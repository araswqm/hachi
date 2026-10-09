package com.araswqm.hatir.data.proactive

import com.araswqm.hatir.core.IoDispatcher
import com.araswqm.hatir.core.Tuning
import com.araswqm.hatir.data.ai.AiMessage
import com.araswqm.hatir.data.ai.AiUsage
import com.araswqm.hatir.data.ai.DeepSeekClient
import com.araswqm.hatir.data.ai.MissingApiKeyException
import com.araswqm.hatir.data.ai.Placeholders
import com.araswqm.hatir.data.ai.PromptBuilder
import com.araswqm.hatir.data.db.dao.CharacterDao
import com.araswqm.hatir.data.db.dao.FollowupDao
import com.araswqm.hatir.data.db.dao.MemoryStateDao
import com.araswqm.hatir.data.db.dao.MessageDao
import com.araswqm.hatir.data.db.dao.ProactiveLogDao
import com.araswqm.hatir.data.db.entity.CharacterEntity
import com.araswqm.hatir.data.db.entity.FollowupEntity
import com.araswqm.hatir.data.db.entity.MessageEntity
import com.araswqm.hatir.data.db.entity.ProactiveLogEntity
import com.araswqm.hatir.data.memory.ChatState
import com.araswqm.hatir.data.memory.IntimacyEngine
import com.araswqm.hatir.data.repo.MemoryRepository
import com.araswqm.hatir.data.repo.PersonaRepository
import com.araswqm.hatir.data.settings.SettingsRepository
import com.araswqm.hatir.notify.Notifications
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Kendiliğinden mesaj sistemi (stages 5).
 *
 * Iki asamali:
 *  1. **Kapi (gate)** — tamamen yerel, ucretsiz on eleme. Seviye, sessizlik,
 *     rahatsiz etme penceresi (DND), gunluk limitler, "dogal kapandi" beklemesi
 *     ve yanitlanmayan mesaj geri-cekilmesi burada uygulanir. Gecmeyen her
 *     karakter icin [ProactiveLogEntity]'ye `stage="gate"` yazilir ve LLM'e
 *     hic gidilmez.
 *  2. **Karar (decision)** — kapiyi gecenler icin tek bir JSON modu cagrisi.
 *     Model "yazmali miyim?" sorusunu ve yazarsa ne yazacagini + bir sonraki
 *     kontrol suresini uretir.
 *
 * Bu sinif [com.araswqm.hatir.work.ProactiveWorker] tarafindan cagrilir.
 */
@Singleton
class ProactiveRepository @Inject constructor(
    private val characterDao: CharacterDao,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryStateDao,
    private val followupDao: FollowupDao,
    private val logDao: ProactiveLogDao,
    private val memoryRepository: MemoryRepository,
    private val personaRepository: PersonaRepository,
    private val settings: SettingsRepository,
    private val client: DeepSeekClient,
    private val promptBuilder: PromptBuilder,
    private val intimacy: IntimacyEngine,
    private val notifications: Notifications,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** Vadesi gelmis tum karakterleri kontrol eder. Worker bunu cagirir. */
    suspend fun runCheck(now: Long = System.currentTimeMillis()) {
        val due = withContext(io) { characterDao.getDueForCheck(now) }
        due.forEach { character ->
            runCatching { checkOne(character, now) }
        }
    }

    // ------------------------------------------------------------------
    // Tek karakter
    // ------------------------------------------------------------------

    private suspend fun checkOne(character: CharacterEntity, now: Long) {
        val state = withContext(io) { memoryDao.get(character.id) }
        val score = state?.let {
            intimacy.effectiveScore(it.intimacyScore, it.lastDecayAt, now)
        } ?: 0.0
        val level = intimacy.level(score)

        val dueFollowups = withContext(io) { followupDao.getDue(character.id, now) }
        val hasDueFollowup = dueFollowups.isNotEmpty()

        val appSettings = withContext(io) { settings.currentSettings() }
        val blocked = gate(
            character = character,
            chatStateRaw = state?.chatState.orEmpty(),
            level = level,
            hasDueFollowup = hasDueFollowup,
            now = now,
            dndStartHour = appSettings.dndStartHour,
            dndEndHour = appSettings.dndEndHour,
        )
        if (blocked != null) {
            log(
                characterId = character.id,
                now = now,
                stage = ProactiveLogEntity.STAGE_GATE,
                result = ProactiveLogEntity.RESULT_BLOCKED,
                rule = blocked.first,
                reason = blocked.second,
            )
            // Engellenen karakteri hemen tekrar denememek icin ileri bir zamanla.
            val retryAt = now + TimeUnit.MINUTES.toMillis(Tuning.PROACTIVE_CHECK_INTERVAL_MINUTES)
            withContext(io) { characterDao.setNextCheckAt(character.id, retryAt) }
            return
        }

        decide(character, dueFollowups.firstOrNull(), now)
    }

    /**
     * Yerel on eleme. Gecildiyse `null`, engellendiyse `(rule, reason)` doner.
     * Sirasi onemli: ucuz ve kesin kurallar once.
     */
    private fun gate(
        character: CharacterEntity,
        chatStateRaw: String,
        level: String,
        hasDueFollowup: Boolean,
        now: Long,
        dndStartHour: Int,
        dndEndHour: Int,
    ): Pair<String, String>? {
        // 1) Seviye
        if (levelRank(level) < levelRank(Tuning.PROACTIVE_MIN_LEVEL)) {
            return "level" to "Seviye '$level', gereken '${Tuning.PROACTIVE_MIN_LEVEL}'."
        }

        // 2) Sessizlik: cok uzun suredir konusulmuyorsa artik yazma.
        val lastAt = character.lastMessageAt ?: character.createdAt
        val silenceDays = (now - lastAt).toDouble() / TimeUnit.DAYS.toMillis(1)
        if (silenceDays > Tuning.PROACTIVE_MAX_SILENCE_DAYS) {
            return "silence" to "%.1f gundur konusulmuyor.".format(silenceDays)
        }

        // 3) Rahatsiz etme penceresi (cihaz saat diliminde).
        if (inDndWindow(now, dndStartHour, dndEndHour)) {
            return "dnd" to "Rahatsiz etme penceresi."
        }

        // 4) Gunluk limitler (once karakter-basi, sonra toplam).
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).toString()
        val usedToday = if (character.dailyProactiveDate == today) character.dailyProactiveCount else 0
        if (usedToday >= Tuning.PROACTIVE_DAILY_LIMIT_PER_CHARACTER) {
            return "daily_limit_character" to "Bugun bu karakter icin limit doldu."
        }

        // 5) "Dogal kapandi" beklemesi (vadesi gelmis takip gecersiz kilar).
        val chatState = ChatState.decode(chatStateRaw)
        if (chatState.closed && !hasDueFollowup) {
            val sinceLast = (now - lastAt).toDouble() / TimeUnit.HOURS.toMillis(1)
            if (sinceLast < Tuning.PROACTIVE_CLOSED_CHAT_WAIT_HOURS) {
                return "closed_wait" to "Sohbet kapandi; %.1f saat once.".format(sinceLast)
            }
        }

        // 6) Yanitlanmayan mesaj geri-cekilmesi (katlanarak).
        if (character.lastProactiveUnanswered && character.lastProactiveAt != null) {
            val waitHours = backoffHours(character)
            val elapsed = (now - character.lastProactiveAt).toDouble() / TimeUnit.HOURS.toMillis(1)
            if (elapsed < waitHours) {
                return "backoff" to "Yanitlanmadi; %.1f/%.1f saat.".format(elapsed, waitHours)
            }
        }

        return null
    }

    // ------------------------------------------------------------------
    // LLM karari
    // ------------------------------------------------------------------

    private suspend fun decide(character: CharacterEntity, dueFollowup: FollowupEntity?, now: Long) {
        val settingsNow = withContext(io) { settings.currentSettings() }
        val memory = withContext(io) { memoryRepository.read(character.id) }
        val recent = withContext(io) {
            messageDao.getLast(character.id, Tuning.RECENT_MESSAGES_IN_PROMPT).reversed()
        }
        val userName = withContext(io) { personaRepository.getSelected()?.name }

        val result = try {
            client.complete(
                model = settingsNow.modelDecision,
                messages = buildDecisionPrompt(character, userName, memory, recent, dueFollowup, now),
                temperature = Tuning.DECISION_TEMPERATURE,
                maxTokens = Tuning.DECISION_MAX_TOKENS,
                jsonMode = true,
            )
        } catch (e: MissingApiKeyException) {
            log(
                characterId = character.id,
                now = now,
                stage = ProactiveLogEntity.STAGE_DECISION,
                result = ProactiveLogEntity.RESULT_ERROR,
                reason = "API anahtari yok.",
            )
            return
        } catch (e: Throwable) {
            log(
                characterId = character.id,
                now = now,
                stage = ProactiveLogEntity.STAGE_DECISION,
                result = ProactiveLogEntity.RESULT_ERROR,
                reason = e.message ?: "bilinmeyen hata",
            )
            withContext(io) { characterDao.setNextCheckAt(character.id, now + hour(Tuning.PROACTIVE_BACKOFF_BASE_HOURS)) }
            return
        }

        val decision = runCatching {
            json.decodeFromString<DecisionResponse>(cleanJson(result.text))
        }.getOrNull()

        val usage = result.usage

        if (decision == null || !decision.send || decision.message.isBlank()) {
            val nextHours = clampNextCheck(decision?.nextCheckHours)
            log(
                characterId = character.id,
                now = now,
                stage = ProactiveLogEntity.STAGE_DECISION,
                result = ProactiveLogEntity.RESULT_SILENT,
                reason = decision?.reason.orEmpty(),
                nextCheckHours = nextHours,
                promptTokens = usage?.promptTokens,
                completionTokens = usage?.completionTokens,
                cacheHitTokens = usage?.cacheHitTokens,
                cacheMissTokens = usage?.cacheMissTokens,
            )
            withContext(io) { characterDao.setNextCheckAt(character.id, now + hour(nextHours)) }
            return
        }

        send(character, userName, decision, dueFollowup, now, result.usage)
    }

    private suspend fun send(
        character: CharacterEntity,
        userName: String?,
        decision: DecisionResponse,
        dueFollowup: FollowupEntity?,
        now: Long,
        usage: AiUsage?,
    ) {
        val text = Placeholders.resolve(decision.message.trim(), character.name, userName)
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).toString()

        withContext(io) {
            messageDao.insert(
                MessageEntity(
                    characterId = character.id,
                    role = MessageEntity.ROLE_ASSISTANT,
                    content = text,
                    variantsJson = json.encodeToString(listOf(text)),
                    variantIndex = 0,
                    createdAt = now,
                    isUnread = true,
                    isProactive = true,
                ),
            )
            characterDao.touchLastMessage(character.id, now)

            val usedToday = if (character.dailyProactiveDate == today) character.dailyProactiveCount else 0
            // Yanitlanmamis her yeni mesajda bekleme katlanarak buyur.
            val nextBackoff = if (character.lastProactiveUnanswered) {
                (character.proactiveBackoffHours.coerceAtLeast(Tuning.PROACTIVE_BACKOFF_BASE_HOURS) *
                    Tuning.PROACTIVE_BACKOFF_MULTIPLIER)
                    .coerceAtMost(Tuning.PROACTIVE_BACKOFF_MAX_HOURS)
            } else {
                Tuning.PROACTIVE_BACKOFF_BASE_HOURS
            }

            characterDao.upsert(
                character.copy(
                    lastProactiveAt = now,
                    lastProactiveUnanswered = true,
                    proactiveBackoffHours = nextBackoff,
                    dailyProactiveCount = usedToday + 1,
                    dailyProactiveDate = today,
                    nextCheckAt = now + hour(nextBackoff),
                    unreadCount = character.unreadCount + 1,
                    updatedAt = now,
                ),
            )

            dueFollowup?.let { followupDao.markDone(it.id, now) }

            log(
                characterId = character.id,
                now = now,
                stage = ProactiveLogEntity.STAGE_DECISION,
                result = ProactiveLogEntity.RESULT_SENT,
                reason = decision.reason,
                message = text,
                promptTokens = usage?.promptTokens,
                completionTokens = usage?.completionTokens,
                cacheHitTokens = usage?.cacheHitTokens,
                cacheMissTokens = usage?.cacheMissTokens,
            )
        }

        notifications.showMessage(character.id, character.name, text)
    }

    // ------------------------------------------------------------------
    // Prompt
    // ------------------------------------------------------------------

    private fun buildDecisionPrompt(
        character: CharacterEntity,
        userName: String?,
        memory: String,
        recent: List<MessageEntity>,
        dueFollowup: FollowupEntity?,
        now: Long,
    ): List<AiMessage> {
        fun r(text: String) = Placeholders.resolve(text, character.name, userName)
        val system = buildString {
            appendLine("# GÖREV")
            appendLine(
                "Sen ${character.name} adlı karakterin kendiliğinden mesaj kararını veriyorsun. " +
                    "Kullanıcı şu an ekranda değil; ona kendiliğinden bir mesaj yazıp yazmayacağına karar veriyorsun.",
            )
            appendLine()
            appendLine("# KURALLAR")
            appendLine("- Türkçe düşün ve yaz.")
            appendLine("- Yalnızca verilen bağlama dayan; olmayan bir şeyi uydurma.")
            appendLine("- Boş/ıvır-zıvır mesaj yazma. Yazacaksan kısa, samimi ve konuşmayı ilerleten bir şey yaz.")
            appendLine("- Devam eden bir konu yoksa veya söyleyecek anlamlı bir şey yoksa \"send\": false ver.")
            appendLine("- Yanıtı SADECE geçerli bir JSON nesnesi olarak ver.")
            appendLine()
            appendLine(promptBuilder.characterBlock(character, userName))
        }.trim()

        val user = buildString {
            if (memory.isNotBlank()) {
                appendLine("## İlişki / uzun vadeli hafıza")
                appendLine(r(memory))
                appendLine()
            }
            appendLine("## Son mesajlar")
            if (recent.isEmpty()) {
                appendLine("(henüz mesaj yok)")
            } else {
                recent.forEach { m ->
                    val who = if (m.role == MessageEntity.ROLE_USER) "Kullanıcı" else character.name
                    appendLine("$who: ${r(m.content)}")
                }
            }
            appendLine()
            if (dueFollowup != null) {
                appendLine("## Vadesi gelmiş takip konusu")
                appendLine("- ${r(dueFollowup.text)}")
                appendLine()
            }
            appendLine("Şu an: ${Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())}")
            appendLine()
            appendLine("## İstenen JSON biçimi")
            appendLine(
                """
                {
                  "send": true,
                  "message": "kullanıcıya yazılacak Türkçe mesaj (send false ise boş)",
                  "reason": "kararının çok kısa gerekçesi",
                  "next_check_hours": 4.0
                }
                """.trimIndent(),
            )
        }.trim()

        return listOf(AiMessage.system(system), AiMessage.user(user))
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    private suspend fun log(
        characterId: String,
        now: Long,
        stage: String,
        result: String,
        rule: String = "",
        reason: String = "",
        message: String? = null,
        nextCheckHours: Double? = null,
        promptTokens: Int? = null,
        completionTokens: Int? = null,
        cacheHitTokens: Int? = null,
        cacheMissTokens: Int? = null,
    ) {
        withContext(io) {
            logDao.insert(
                ProactiveLogEntity(
                    characterId = characterId,
                    timestamp = now,
                    stage = stage,
                    result = result,
                    rule = rule,
                    reason = reason.take(Tuning.PROACTIVE_LOG_REASON_MAX_CHARS),
                    message = message,
                    nextCheckHours = nextCheckHours,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    cacheHitTokens = cacheHitTokens,
                    cacheMissTokens = cacheMissTokens,
                ),
            )
        }
    }

    /** Yanitlanmayan mesajdan dolayi birikmis bekleme (saat). */
    private fun backoffHours(character: CharacterEntity): Double =
        character.proactiveBackoffHours
            .takeIf { it > 0.0 }
            ?.coerceIn(Tuning.PROACTIVE_BACKOFF_BASE_HOURS, Tuning.PROACTIVE_BACKOFF_MAX_HOURS)
            ?: Tuning.PROACTIVE_BACKOFF_BASE_HOURS

    private fun clampNextCheck(value: Double?): Double {
        val v = value?.takeIf { it > 0.0 } ?: Tuning.PROACTIVE_CHECK_INTERVAL_MINUTES / 60.0
        return v.coerceIn(Tuning.PROACTIVE_NEXT_CHECK_MIN_HOURS, Tuning.PROACTIVE_NEXT_CHECK_MAX_HOURS)
    }

    private fun hour(hours: Double): Long = (hours * TimeUnit.HOURS.toMillis(1)).toLong()

    private fun inDndWindow(now: Long, startHour: Int, endHour: Int): Boolean {
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        return if (startHour <= endHour) {
            hour in startHour until endHour
        } else {
            // Gece yarisini saran pencere (or. 23 -> 9).
            hour >= startHour || hour < endHour
        }
    }

    /** "Yabancı" < "Tanıdık" < "Arkadaş" < "Yakın". */
    private fun levelRank(level: String): Int = when (level) {
        "Yakın" -> 3
        "Arkadaş" -> 2
        "Tanıdık" -> 1
        else -> 0
    }

    private fun cleanJson(raw: String): String {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }

    /** Karar cagrisinin JSON yaniti. */
    @Serializable
    private data class DecisionResponse(
        val send: Boolean = false,
        val message: String = "",
        val reason: String = "",
        @SerialName("next_check_hours") val nextCheckHours: Double = 0.0,
    )
}
