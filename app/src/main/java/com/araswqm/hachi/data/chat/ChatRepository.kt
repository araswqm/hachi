package com.araswqm.hachi.data.chat

import com.araswqm.hachi.core.IoDispatcher
import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.ai.AiStreamEvent
import com.araswqm.hachi.data.ai.AiUsage
import com.araswqm.hachi.data.ai.DeepSeekClient
import com.araswqm.hachi.data.ai.MissingApiKeyException
import com.araswqm.hachi.data.ai.Placeholders
import com.araswqm.hachi.data.ai.PromptBuilder
import com.araswqm.hachi.data.db.dao.CharacterDao
import com.araswqm.hachi.data.db.dao.MessageDao
import com.araswqm.hachi.data.db.entity.CharacterEntity
import com.araswqm.hachi.data.db.entity.MessageEntity
import com.araswqm.hachi.data.repo.MemoryRepository
import com.araswqm.hachi.data.repo.PersonaRepository
import com.araswqm.hachi.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Sohbet uretim akisinin olaylari. */
sealed interface GenEvent {
    /** Uretim basladi; arayuz "yaziyor" gostergesini acar. */
    data object Started : GenEvent

    /** Yeni metin parcasi (artim). */
    data class Delta(val text: String) : GenEvent

    /** Uretim bitti ve mesaj veritabanina yazildi. */
    data class Completed(val messageId: Long) : GenEvent

    /** Uretim basarisiz. [needsApiKey] true ise arayuz Ayarlar'a yonlendirir. */
    data class Failed(val message: String, val needsApiKey: Boolean) : GenEvent

    /** Kullanici durdurdu; kismi metin (varsa) kaydedildi. */
    data object Cancelled : GenEvent
}

/**
 * Sohbet verisi + uretim orkestrasyonu.
 *
 * **Tasarim notu — neden onceden bos mesaj satiri acilmiyor:** Onceki hatali
 * yaklasimda uretim baslamadan veritabanina bos bir asistan mesaji ekleniyordu;
 * bu, gecikme sirasinda bos balon yanip sonmesine yol aciyordu. Burada metin
 * once bellekte birikir, yalnizca uretim bitince (ya da durdurma/hata aninda
 * kismi metinle) satir yazilir.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val characterDao: CharacterDao,
    private val personaRepository: PersonaRepository,
    private val memoryRepository: MemoryRepository,
    private val settings: SettingsRepository,
    private val client: DeepSeekClient,
    private val promptBuilder: PromptBuilder,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeMessages(characterId: String): Flow<List<MessageEntity>> =
        messageDao.observeForCharacter(characterId)

    // ------------------------------------------------------------------
    // Kullanici eylemleri
    // ------------------------------------------------------------------

    /** Kullanici mesajini ekler ve karakter listesindeki son mesaj zamanini gunceller. */
    suspend fun appendUserMessage(characterId: String, text: String): Long = withContext(io) {
        val now = System.currentTimeMillis()
        val character = characterDao.getById(characterId)
        val body = Placeholders.resolve(
            text = text.trim(),
            characterName = character?.name.orEmpty(),
            userName = personaRepository.getSelected()?.name,
        )
        val id = messageDao.insert(
            MessageEntity(
                characterId = characterId,
                role = MessageEntity.ROLE_USER,
                content = body,
                variantsJson = encodeVariants(listOf(body)),
                createdAt = now,
            ),
        )
        characterDao.touchLastMessage(characterId, now)
        // Kullanici yanit verdi: kendiliğinden mesaj geri-cekilmesini sifirla.
        character?.let { c ->
            if (c.lastProactiveUnanswered || c.proactiveBackoffHours > 0.0) {
                characterDao.upsert(
                    c.copy(lastProactiveUnanswered = false, proactiveBackoffHours = 0.0),
                )
            }
        }
        id
    }

    /** Ilk mesaj: sohbet bossa karakterin `first_mes` metnini yerlestirir. */
    suspend fun ensureGreeting(character: CharacterEntity) = withContext(io) {
        if (messageDao.count(character.id) > 0) return@withContext
        if (character.firstMessage.isBlank()) return@withContext
        val now = System.currentTimeMillis()
        val body = Placeholders.resolve(
            text = character.firstMessage.trim(),
            characterName = character.name,
            userName = personaRepository.getSelected()?.name,
        )
        messageDao.insert(
            MessageEntity(
                characterId = character.id,
                role = MessageEntity.ROLE_ASSISTANT,
                content = body,
                variantsJson = encodeVariants(listOf(body)),
                createdAt = now,
            ),
        )
        characterDao.touchLastMessage(character.id, now)
    }

    suspend fun editMessage(messageId: Long, text: String) = withContext(io) {
        val message = messageDao.getById(messageId) ?: return@withContext
        val trimmed = text.trim()
        val variants = decodeVariants(message.variantsJson).toMutableList()
        if (variants.isEmpty()) variants.add(trimmed)
        else variants[message.variantIndex.coerceIn(0, variants.lastIndex)] = trimmed
        messageDao.update(
            message.copy(
                content = trimmed,
                variantsJson = encodeVariants(variants),
                isEdited = message.role == MessageEntity.ROLE_USER || message.isEdited,
            ),
        )
    }

    suspend fun deleteMessage(messageId: Long) = withContext(io) {
        val message = messageDao.getById(messageId) ?: return@withContext
        messageDao.deleteById(messageId)
        val latest = messageDao.getLatest(message.characterId)
        characterDao.touchLastMessage(message.characterId, latest?.createdAt ?: 0L)
    }

    /** Yeniden uretme varyantlari arasinda gecis. */
    suspend fun setVariant(messageId: Long, index: Int) = withContext(io) {
        val message = messageDao.getById(messageId) ?: return@withContext
        val variants = decodeVariants(message.variantsJson)
        if (variants.isEmpty()) return@withContext
        val safe = index.coerceIn(0, variants.lastIndex)
        messageDao.update(
            message.copy(content = variants[safe], variantIndex = safe),
        )
    }

    suspend fun markRead(characterId: String) = withContext(io) {
        messageDao.markAllRead(characterId)
        characterDao.setUnreadCount(characterId, 0)
    }

    // ------------------------------------------------------------------
    // Uretim
    // ------------------------------------------------------------------

    /**
     * Yanit uretir. [regenerate] true ise son asistan mesajini **uzerine yazar**
     * (eski metin varyant olarak saklanir); false ise sona yeni mesaj ekler.
     *
     * Akis iptal edilirse kismi metin `NonCancellable` blokta kaydedilir ve
     * iptal yeniden firlatilir (Flow kurali: iptal sessizce yutulmaz).
     */
    fun generateReply(characterId: String, regenerate: Boolean): Flow<GenEvent> = flow {
        val character = withContext(io) { characterDao.getById(characterId) }
        if (character == null) {
            emit(GenEvent.Failed("Karakter bulunamadı.", needsApiKey = false))
            return@flow
        }

        val target = withContext(io) {
            val last = messageDao.getLatest(characterId)
            if (regenerate && last?.role == MessageEntity.ROLE_ASSISTANT) last else null
        }

        val prompt = withContext(io) {
            val recent = messageDao
                .getLast(characterId, Tuning.RECENT_MESSAGES_IN_PROMPT + 1)
                .reversed()
                .filter { it.id != target?.id }
            val persona = personaRepository.getOrCreateSelected()
            val memory = memoryRepository.read(characterId)
            promptBuilder.build(
                character = character,
                persona = persona,
                memory = memory,
                history = recent,
                nowMillis = System.currentTimeMillis(),
            )
        }

        val model = withContext(io) { settings.currentSettings().modelChat }
        val buffer = StringBuilder()
        var usage: AiUsage? = null

        emit(GenEvent.Started)
        try {
            client.stream(
                model = model,
                messages = prompt,
                temperature = character.temperature,
                maxTokens = character.maxTokens,
            ).collect { event ->
                when (event) {
                    is AiStreamEvent.Delta -> {
                        buffer.append(event.text)
                        emit(GenEvent.Delta(event.text))
                    }
                    is AiStreamEvent.Usage -> usage = event.usage
                    AiStreamEvent.Done -> Unit
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                persistPartial(characterId, target, buffer.toString(), usage, isError = false)
            }
            emit(GenEvent.Cancelled)
            throw e
        } catch (e: MissingApiKeyException) {
            emit(GenEvent.Failed("DeepSeek API anahtarı ayarlanmamış.", needsApiKey = true))
            return@flow
        } catch (e: Throwable) {
            val partial = buffer.toString()
            if (partial.isNotBlank()) {
                withContext(NonCancellable) {
                    persistPartial(characterId, target, partial, usage, isError = true)
                }
            }
            emit(GenEvent.Failed(e.message ?: "Bilinmeyen hata.", needsApiKey = false))
            return@flow
        }

        val id = withContext(io) { persistPartial(characterId, target, buffer.toString(), usage, isError = false) }
        emit(GenEvent.Completed(id ?: -1L))
    }

    /**
     * Biriken metni kalici hale getirir. [target] null ise yeni satir ekler,
     * aksi halde o satiri guncelleyip eski metni varyant olarak saklar.
     */
    private suspend fun persistPartial(
        characterId: String,
        target: MessageEntity?,
        text: String,
        usage: AiUsage?,
        isError: Boolean,
    ): Long? {
        val now = System.currentTimeMillis()
        if (target == null) {
            if (text.isBlank()) return null
            val id = messageDao.insert(
                MessageEntity(
                    characterId = characterId,
                    role = MessageEntity.ROLE_ASSISTANT,
                    content = text,
                    variantsJson = encodeVariants(listOf(text)),
                    createdAt = now,
                    isError = isError,
                    promptTokens = usage?.promptTokens,
                    completionTokens = usage?.completionTokens,
                    cacheHitTokens = usage?.cacheHitTokens,
                    cacheMissTokens = usage?.cacheMissTokens,
                ),
            )
            characterDao.touchLastMessage(characterId, now)
            return id
        }

        val variants = decodeVariants(target.variantsJson).toMutableList()
        // Ilk varyant eski metindir; yenisi sona eklenir ve secilir.
        if (variants.isEmpty()) variants.add(target.content)
        variants.add(text)
        messageDao.update(
            target.copy(
                content = text,
                variantsJson = encodeVariants(variants),
                variantIndex = variants.lastIndex,
                isError = isError,
                completionTokens = usage?.completionTokens ?: target.completionTokens,
                cacheHitTokens = usage?.cacheHitTokens ?: target.cacheHitTokens,
                cacheMissTokens = usage?.cacheMissTokens ?: target.cacheMissTokens,
            ),
        )
        return target.id
    }

    // ------------------------------------------------------------------
    // Varyant JSON yardimcilari
    // ------------------------------------------------------------------

    private fun decodeVariants(raw: String): List<String> =
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    private fun encodeVariants(values: List<String>): String =
        runCatching { json.encodeToString(values) }.getOrDefault("[]")
}
