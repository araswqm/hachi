package com.araswqm.hachi.data.memory

import com.araswqm.hachi.core.IoDispatcher
import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.ai.AiMessage
import com.araswqm.hachi.data.ai.ENGLISH_OUTPUT_ONLY
import com.araswqm.hachi.data.ai.PromptBuilder
import com.araswqm.hachi.data.ai.provider.LlmRouter
import com.araswqm.hachi.data.ai.provider.ModelRole
import com.araswqm.hachi.data.db.dao.CharacterDao
import com.araswqm.hachi.data.db.dao.FollowupDao
import com.araswqm.hachi.data.db.dao.MemoryStateDao
import com.araswqm.hachi.data.db.dao.MessageDao
import com.araswqm.hachi.data.db.entity.FollowupEntity
import com.araswqm.hachi.data.db.entity.MemoryStateEntity
import com.araswqm.hachi.data.db.entity.MessageEntity
import com.araswqm.hachi.data.repo.MemoryRepository
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

/**
 * Ozetleyicinin urettigi JSON yaniti.
 *
 * Alan adlari Ingilizcedir (§2.2): hafiza verisi daima Ingilizce tutulur,
 * arayuz dili ne olursa olsun.
 */
@Serializable
private data class SummaryResponse(
    val summary: String = "",
    val intimacy: Double = 0.0,
    @SerialName("chat_state") val chatState: String = "",
    @SerialName("character_feeling") val characterFeeling: String = "",
    @SerialName("chat_closed") val chatClosed: Boolean = false,
    val followups: List<FollowupItem> = emptyList(),
)

@Serializable
private data class FollowupItem(
    val text: String = "",
    @SerialName("in_days") val inDays: Double = 0.0,
)

/**
 * `memory.md` ozetleyici (§8: hafiza sohbet bazlidir).
 *
 * Ne zaman calisir:
 *  - `SUMMARY_EVERY_MESSAGES` (10) mesaj birikince (sohbet akisindan),
 *  - uygulama arka plana gecerken ([com.araswqm.hachi.work.SummarizeWorker]).
 *
 * Sohbet basina bir `Mutex` vardir; ayni sohbet icin iki ozetleme asla
 * ust uste binmez. Ham yakinlik puanlari [MemoryStateEntity] icinde birikir;
 * nihai puan [IntimacyEngine] tarafindan hesaplanir. `characterId` yalnizca
 * takip konulari (`followups`, karakter bazli) ve iliskili aramalar icin tutulur.
 */
@Singleton
class MemorySummarizer @Inject constructor(
    private val messageDao: MessageDao,
    private val characterDao: CharacterDao,
    private val memoryDao: MemoryStateDao,
    private val followupDao: FollowupDao,
    private val memoryRepository: MemoryRepository,
    private val router: LlmRouter,
    private val promptBuilder: PromptBuilder,
    private val intimacy: IntimacyEngine,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(chatId: String): Mutex = locks.getOrPut(chatId) { Mutex() }

    /** Ozetlenmeyi bekleyen yeterli mesaj var mi? */
    suspend fun shouldSummarize(chatId: String): Boolean = withContext(io) {
        val state = memoryDao.get(chatId)
        val lastId = state?.lastSummarizedMessageId ?: 0L
        messageDao.countAfter(chatId, lastId) >= Tuning.SUMMARY_EVERY_MESSAGES
    }

    /**
     * Bekleyen mesajlari ozetler. [force] true ise mesaj sayisi esigi yok sayilir
     * (uygulama kapanisi). Basarili ozetlemede true doner.
     */
    suspend fun summarize(chatId: String, characterId: String, force: Boolean = false): Boolean {
        val lock = lockFor(chatId)
        if (lock.isLocked && !force) return false
        return lock.withLock { runSummary(chatId, characterId, force) }
    }

    /**
     * §2.1 — silme / varyant degisimi sonrasi hafiza senkronu.
     *
     * [affectedMessageId] silinen ya da icerigi degisen mesajdir. Mesaj zaten
     * ozetlenmis bolgeye aitse (`id <= lastSummarizedMessageId`), hafiza "olmus
     * gibi" kabul edilemez: hafiza BASTAN kurulur — kilitli kullanici notlari
     * (`🔒`) korunur, gerisi sifirlanir ve kalan mesajlar yeniden ozetlenir.
     *
     * Mesaj henuz ozetlenmemisse imlec degismez; cagiran taraf yalnizca satiri
     * silmis olur ve bu yontem false doner.
     */
    suspend fun rebuildFrom(chatId: String, characterId: String, affectedMessageId: Long): Boolean {
        val lock = lockFor(chatId)
        // Kilit tutulurken mutex YENIDEN alinamaz (Kotlin mutex'i yeniden girmez);
        // bu yuzden icte dogrudan runSummary cagrilir.
        return lock.withLock {
            val state = withContext(io) { memoryDao.get(chatId) } ?: return@withLock false
            if (affectedMessageId > state.lastSummarizedMessageId) return@withLock false

            // Hafizayi sifirdan kur: yalnizca kilitli kullanici notlari kalir.
            val existing = withContext(io) { memoryRepository.read(chatId) }
            val doc = MemoryDocument.parse(existing)
            val reset = MemoryDocument.empty().copy(lockedNotes = doc.lockedNotes)

            withContext(io) {
                memoryRepository.save(chatId, reset.toText())
                memoryDao.upsert(
                    state.copy(
                        lastSummarizedMessageId = 0L,
                        summarizedMessageCount = 0,
                        sectionCount = 0,
                        chatState = "",
                        characterFeeling = "",
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }

            val rebuilt = runSummary(chatId, characterId, force = true)
            // Kalan mesaj yoksa bos iskelet birakmak yerine dosyayi sil. Kilitli
            // notlar varsa bunlar gercek iceriktir, korunur (silinmez).
            if (!rebuilt && reset.lockedNotes.isEmpty()) {
                withContext(io) { memoryRepository.delete(chatId) }
            }
            true
        }
    }

    private suspend fun runSummary(chatId: String, characterId: String, force: Boolean): Boolean {
        val character = withContext(io) { characterDao.getById(characterId) } ?: return false
        val state = withContext(io) { memoryDao.get(chatId) }
            ?: MemoryStateEntity(chatId = chatId, characterId = characterId)
        val pending = withContext(io) {
            messageDao.getAfter(chatId, state.lastSummarizedMessageId, Tuning.MEMORY_PENDING_MESSAGES_LIMIT)
        }
        if (pending.isEmpty()) return false
        if (!force && pending.size < Tuning.SUMMARY_EVERY_MESSAGES) return false

        val existing = withContext(io) { memoryRepository.read(chatId) }

        val response = try {
            val result = router.complete(
                role = ModelRole.SUMMARY,
                messages = buildMessages(character.name, existing, pending),
                temperature = Tuning.SUMMARY_TEMPERATURE,
                maxTokens = Tuning.SUMMARY_MAX_TOKENS,
                jsonMode = true,
            )
            json.decodeFromString<SummaryResponse>(cleanJson(result.text))
        } catch (e: Throwable) {
            return false
        }

        if (response.summary.isBlank() && response.intimacy <= 0.0) return false

        val now = System.currentTimeMillis()
        val title = "Session ${state.sectionCount + 1}"

        val document = MemoryDocument.parse(existing)
            .let { doc ->
                if (response.summary.isNotBlank()) doc.appending(title, response.summary) else doc
            }
            .let { doc -> doc.copy(relationship = relationshipBlock(response)) }
            .compressed()
            .let { doc -> shrinkLongTerm(doc) }

        withContext(io) {
            memoryRepository.write(chatId, document.toText())

            val rawHistory = decodeInts(state.intimacyRawHistoryJson) +
                intimacy.clampRaw(response.intimacy)
            val window = rawHistory.takeLast(Tuning.INTIMACY_MOVING_AVERAGE_WINDOW)
            val messageCount = messageDao.count(chatId)
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
                    chatState = ChatState(response.chatClosed, response.chatState).encode(),
                    characterFeeling = response.characterFeeling.take(Tuning.MEMORY_CHARACTER_FEELING_MAX_CHARS),
                    updatedAt = now,
                ),
            )

            response.followups
                .filter { it.text.isNotBlank() }
                .forEach { item ->
                    val dueAt = if (item.inDays > 0.0) {
                        now + (item.inDays * TimeUnit.HOURS.toMillis(Tuning.HOURS_PER_DAY)).toLong()
                    } else {
                        null
                    }
                    followupDao.insert(
                        FollowupEntity(
                            characterId = characterId,
                            text = item.text.trim().take(Tuning.MEMORY_FOLLOWUP_TEXT_MAX_CHARS),
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
            appendLine("# TASK")
            appendLine(
                "You turn the newest chapter of the conversation between the character " +
                    "\"$characterName\" and the user into a note appended to the character's " +
                    "persistent memory.",
            )
            appendLine()
            appendLine("# RULES")
            appendLine("- $ENGLISH_OUTPUT_ONLY")
            appendLine("- Rely only on the messages provided; never invent anything.")
            appendLine("- Return ONLY a valid JSON object; add no explanation.")
            appendLine("- 'intimacy' is an integer from 0 to 100: 0 = complete strangers, 100 = deep closeness.")
        }.trim()

        val user = buildString {
            appendLine("## Existing memory (if any)")
            appendLine(existing.ifBlank { "(empty)" })
            appendLine()
            appendLine("## New messages to summarize")
            pending.forEach { message ->
                val who = if (message.role == MessageEntity.ROLE_USER) "User" else characterName
                appendLine("$who: ${message.content}")
            }
            appendLine()
            appendLine("## Required JSON shape")
            appendLine(
                """
                {
                  "summary": "bullet-point summary of this chapter, in English",
                  "intimacy": 55,
                  "chat_state": "where the conversation currently stands, very short, in English",
                  "character_feeling": "how $characterName feels about the user, very short, in English",
                  "chat_closed": true,
                  "followups": [{"text": "topic to bring up later, in English", "in_days": 2.5}]
                }
                """.trimIndent(),
            )
        }.trim()

        return listOf(AiMessage.system(system), AiMessage.user(user))
    }

    private fun relationshipBlock(response: SummaryResponse): String = buildString {
        appendLine("## Relationship Status")
        val raw = intimacy.clampRaw(response.intimacy)
        appendLine("- Intimacy: $raw/100 (${intimacy.level(raw.toDouble()).label})")
        if (response.chatState.isNotBlank()) {
            appendLine("- Conversation state: ${response.chatState.trim()}")
        }
        if (response.characterFeeling.isNotBlank()) {
            appendLine("- ${response.characterFeeling.trim()}")
        }
    }.trim()

    /**
     * §2.1 sinirsiz hafiza: uzun vadeli blok esigi asinca "sadece birlestir" ile
     * buyumeye devam etmesin. Blok, LLM ile yeniden ozetlenerek sabit-kisa bir
     * cekirdege indirilir. Hata olursa (ag, anahtar yok) blok oldugu gibi kalir;
     * ozetleme zaten basarili sayilir — kayip veri yok, sadece sikistirma atlanir.
     */
    private suspend fun shrinkLongTerm(doc: MemoryDocument): MemoryDocument {
        val longTerm = doc.sections.firstOrNull { it.title == MemoryDocument.LONG_TERM_TITLE }
            ?: return doc
        if (longTerm.estimatedTokens() <= Tuning.MEMORY_LONG_TERM_MAX_TOKENS) return doc

        val shrunk = try {
            router.complete(
                role = ModelRole.SUMMARY,
                messages = listOf(
                    AiMessage.system(
                        "You compress long-term memory notes into one concise, information-dense " +
                            "summary. Keep names, facts, promises, preferences and unresolved " +
                            "threads. Drop filler. $ENGLISH_OUTPUT_ONLY",
                    ),
                    AiMessage.user(longTerm.body),
                ),
                temperature = Tuning.SUMMARY_TEMPERATURE,
                maxTokens = Tuning.MEMORY_LONG_TERM_MAX_TOKENS,
            ).text.trim()
        } catch (e: Throwable) {
            return doc
        }

        if (shrunk.isBlank()) return doc
        return doc.withSection(MemoryDocument.LONG_TERM_TITLE, shrunk)
    }

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
