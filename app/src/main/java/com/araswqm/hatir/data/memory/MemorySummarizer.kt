package com.araswqm.hatir.data.memory

import com.araswqm.hatir.core.IoDispatcher
import com.araswqm.hatir.core.Tuning
import com.araswqm.hatir.data.ai.AiMessage
import com.araswqm.hatir.data.ai.DeepSeekClient
import com.araswqm.hatir.data.ai.PromptBuilder
import com.araswqm.hatir.data.db.dao.CharacterDao
import com.araswqm.hatir.data.db.dao.FollowupDao
import com.araswqm.hatir.data.db.dao.MemoryStateDao
import com.araswqm.hatir.data.db.dao.MessageDao
import com.araswqm.hatir.data.db.entity.FollowupEntity
import com.araswqm.hatir.data.db.entity.MemoryStateEntity
import com.araswqm.hatir.data.db.entity.MessageEntity
import com.araswqm.hatir.data.repo.MemoryRepository
import com.araswqm.hatir.data.settings.SettingsRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Ozetleyicinin urettigi JSON yaniti. */
@Serializable
private data class SummaryResponse(
    val ozet: String = "",
    val samimiyet: Double = 0.0,
    @SerialName("sohbet_durumu") val sohbetDurumu: String = "",
    @SerialName("karakter_duygusu") val karakterDuygusu: String = "",
    @SerialName("sohbet_kapandi") val sohbetKapandi: Boolean = false,
    @SerialName("takip_konulari") val takipKonulari: List<FollowupItem> = emptyList(),
)

@Serializable
private data class FollowupItem(
    val metin: String = "",
    @SerialName("gun_sonra") val gunSonra: Double = 0.0,
)

/**
 * `memory.md` ozetleyici.
 *
 * Ne zaman calisir:
 *  - `SUMMARY_EVERY_MESSAGES` (10) mesaj birikince (sohbet akisindan),
 *  - uygulama arka plana gecerken ([com.araswqm.hatir.work.SummarizeWorker]).
 *
 * Karakter basina bir `Mutex` vardir; ayni karakter icin iki ozetleme asla
 * ust uste binmez. Ham yakinlik puanlari [MemoryStateEntity] icinde birikir;
 * nihai puan [IntimacyEngine] tarafindan hesaplanir.
 */
@Singleton
class MemorySummarizer @Inject constructor(
    private val messageDao: MessageDao,
    private val characterDao: CharacterDao,
    private val memoryDao: MemoryStateDao,
    private val followupDao: FollowupDao,
    private val memoryRepository: MemoryRepository,
    private val settings: SettingsRepository,
    private val client: DeepSeekClient,
    private val promptBuilder: PromptBuilder,
    private val intimacy: IntimacyEngine,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(characterId: String): Mutex = locks.getOrPut(characterId) { Mutex() }

    /** Ozetlenmeyi bekleyen yeterli mesaj var mi? */
    suspend fun shouldSummarize(characterId: String): Boolean = withContext(io) {
        val state = memoryDao.get(characterId)
        val lastId = state?.lastSummarizedMessageId ?: 0L
        messageDao.countAfter(characterId, lastId) >= Tuning.SUMMARY_EVERY_MESSAGES
    }

    /**
     * Bekleyen mesajlari ozetler. [force] true ise mesaj sayisi esigi yok sayilir
     * (uygulama kapanisi). Basarili ozetlemede true doner.
     */
    suspend fun summarize(characterId: String, force: Boolean = false): Boolean {
        val lock = lockFor(characterId)
        if (lock.isLocked && !force) return false
        return lock.withLock { runSummary(characterId, force) }
    }

    private suspend fun runSummary(characterId: String, force: Boolean): Boolean {
        val character = withContext(io) { characterDao.getById(characterId) } ?: return false
        val state = withContext(io) { memoryDao.get(characterId) } ?: MemoryStateEntity(characterId = characterId)
        val pending = withContext(io) {
            messageDao.getAfter(characterId, state.lastSummarizedMessageId, Tuning.MEMORY_PENDING_MESSAGES_LIMIT)
        }
        if (pending.isEmpty()) return false
        if (!force && pending.size < Tuning.SUMMARY_EVERY_MESSAGES) return false

        val existing = withContext(io) { memoryRepository.read(characterId) }
        val model = withContext(io) { settings.currentSettings().modelSummary }

        val response = try {
            val result = client.complete(
                model = model,
                messages = buildMessages(character.name, existing, pending),
                temperature = Tuning.SUMMARY_TEMPERATURE,
                maxTokens = Tuning.SUMMARY_MAX_TOKENS,
                jsonMode = true,
            )
            json.decodeFromString<SummaryResponse>(cleanJson(result.text))
        } catch (e: Throwable) {
            return false
        }

        if (response.ozet.isBlank() && response.samimiyet <= 0.0) return false

        val now = System.currentTimeMillis()
        val startIndex = state.summarizedMessageCount + 1
        val endIndex = state.summarizedMessageCount + pending.size
        val title = "Bölüm ${state.sectionCount + 1} (mesaj $startIndex–$endIndex)"

        val document = MemoryDocument.parse(existing)
            .let { doc ->
                if (response.ozet.isNotBlank()) doc.appending(title, response.ozet) else doc
            }
            .let { doc -> doc.copy(relationship = relationshipBlock(response)) }
            .compressed()

        withContext(io) {
            memoryRepository.write(characterId, document.toText())

            val rawHistory = decodeInts(state.intimacyRawHistoryJson) +
                intimacy.clampRaw(response.samimiyet)
            val window = rawHistory.takeLast(Tuning.INTIMACY_MOVING_AVERAGE_WINDOW)
            val messageCount = messageDao.count(characterId)
            val streakDays = streakDays(pending)
            val score = intimacy.compose(window, messageCount, streakDays)

            memoryDao.upsert(
                state.copy(
                    lastSummarizedMessageId = pending.last().id,
                    summarizedMessageCount = state.summarizedMessageCount + pending.size,
                    sectionCount = document.sections.size,
                    intimacyScore = score,
                    intimacyRawHistoryJson = json.encodeToString(
                        rawHistory.takeLast(
                            Tuning.INTIMACY_MOVING_AVERAGE_WINDOW * Tuning.MEMORY_RAW_HISTORY_RETENTION_MULTIPLIER,
                        ),
                    ),
                    lastDecayAt = now,
                    chatState = ChatState(response.sohbetKapandi, response.sohbetDurumu).encode(),
                    characterFeeling = response.karakterDuygusu.take(Tuning.MEMORY_CHARACTER_FEELING_MAX_CHARS),
                    updatedAt = now,
                ),
            )

            response.takipKonulari
                .filter { it.metin.isNotBlank() }
                .forEach { item ->
                    val dueAt = if (item.gunSonra > 0.0) {
                        now + (item.gunSonra * TimeUnit.HOURS.toMillis(Tuning.HOURS_PER_DAY)).toLong()
                    } else {
                        null
                    }
                    followupDao.insert(
                        FollowupEntity(
                            characterId = characterId,
                            text = item.metin.trim().take(Tuning.MEMORY_FOLLOWUP_TEXT_MAX_CHARS),
                            dueAt = dueAt,
                            createdAt = now,
                        ),
                    )
                }
        }
        return true
    }

    // ------------------------------------------------------------------
    // Prompt
    // ------------------------------------------------------------------

    private fun buildMessages(
        characterName: String,
        existing: String,
        pending: List<MessageEntity>,
    ): List<AiMessage> {
        val system = buildString {
            appendLine("# GÖREV")
            appendLine(
                "$characterName adlı karakter ile kullanıcı arasındaki sohbetin yeni bölümünü, " +
                    "karakterin kalıcı hafızasına eklenecek bir nota dönüştürüyorsun.",
            )
            appendLine()
            appendLine("# KURALLAR")
            appendLine("- Türkçe yaz.")
            appendLine("- Yalnızca verilen mesajlara dayan; olmayan bir şeyi uydurma.")
            appendLine("- Yanıtı SADECE geçerli bir JSON nesnesi olarak ver; açıklama ekleme.")
            appendLine("- 'samimiyet' alanı 0–100 arası bir tam sayıdır: 0 = tamamen yabancı, 100 = derin yakınlık.")
        }.trim()

        val user = buildString {
            appendLine("## Mevcut hafıza (varsa)")
            appendLine(existing.ifBlank { "(boş)" })
            appendLine()
            appendLine("## Özetlenecek yeni mesajlar")
            pending.forEach { message ->
                val who = if (message.role == MessageEntity.ROLE_USER) "Kullanıcı" else characterName
                appendLine("$who: ${message.content}")
            }
            appendLine()
            appendLine("## İstenen JSON biçimi")
            appendLine(
                """
                {
                  "ozet": "bu bölümün Türkçe, madde madde özeti",
                  "samimiyet": 55,
                  "sohbet_durumu": "konuşmanın şu an nerede kaldığı, çok kısa",
                  "karakter_duygusu": "$characterName karakterin kullanıcıya dair hissi, çok kısa",
                  "sohbet_kapandi": true,
                  "takip_konulari": [{"metin": "ileride sorulacak konu", "gun_sonra": 2.5}]
                }
                """.trimIndent(),
            )
        }.trim()

        return listOf(AiMessage.system(system), AiMessage.user(user))
    }

    private fun relationshipBlock(response: SummaryResponse): String = buildString {
        appendLine("## İlişki Durumu")
        val raw = intimacy.clampRaw(response.samimiyet)
        appendLine("- Samimiyet: $raw/100 (${intimacy.level(raw.toDouble())})")
        if (response.sohbetDurumu.isNotBlank()) {
            appendLine("- Sohbet durumu: ${response.sohbetDurumu.trim()}")
        }
        if (response.karakterDuygusu.isNotBlank()) {
            appendLine("- ${response.karakterDuygusu.trim()}")
        }
    }.trim()

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    /** Modele bazen JSON'u ```json ... ``` icinde sarmalayabilir; temizler. */
    private fun cleanJson(raw: String): String {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }

    private fun decodeInts(raw: String): List<Int> =
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<Int>>(raw) }.getOrDefault(emptyList())

    /** Ardisik gun serisi: ozetlenen mesajlarin farkli gun sayisi (ust sinirli). */
    private fun streakDays(messages: List<MessageEntity>): Int {
        if (messages.isEmpty()) return 0
        val days = messages
            .map { TimeUnit.MILLISECONDS.toDays(it.createdAt) }
            .distinct()
            .sorted()
        var streak = 1
        var best = 1
        for (i in 1 until days.size) {
            if (days[i] == days[i - 1] + 1) streak++ else streak = 1
            if (streak > best) best = streak
        }
        return best
    }
}
