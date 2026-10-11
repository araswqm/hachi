package com.araswqm.hachi.data.chat

import com.araswqm.hachi.core.ApplicationScope
import com.araswqm.hachi.core.IoDispatcher
import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.ai.AiMessage
import com.araswqm.hachi.data.ai.AiStreamEvent
import com.araswqm.hachi.data.ai.AiUsage
import com.araswqm.hachi.data.ai.MissingApiKeyException
import com.araswqm.hachi.data.ai.Placeholders
import com.araswqm.hachi.data.ai.PromptBuilder
import com.araswqm.hachi.data.ai.provider.LlmRouter
import com.araswqm.hachi.data.ai.provider.ModelRole
import com.araswqm.hachi.data.ai.provider.ProviderId
import com.araswqm.hachi.data.db.dao.CharacterDao
import com.araswqm.hachi.data.db.dao.ChatDao
import com.araswqm.hachi.data.db.dao.MessageDao
import com.araswqm.hachi.data.db.entity.CharacterEntity
import com.araswqm.hachi.data.db.entity.ChatEntity
import com.araswqm.hachi.data.db.entity.MessageEntity
import com.araswqm.hachi.data.memory.MemorySummarizer
import com.araswqm.hachi.data.repo.MemoryRepository
import com.araswqm.hachi.data.repo.PersonaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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

    /**
     * §6: ilk saglayici bos elle birakti, zincirdeki bir sonrakine gecildi.
     * Yalnizca bilgilendirmedir; uretim ayni akista devam eder.
     */
    data class ProviderSwitched(val from: ProviderId, val to: ProviderId) : GenEvent
}

/**
 * Sohbet verisi + uretim orkestrasyonu.
 *
 * §8: mesajlar ve hafiza artik sohbet ([ChatEntity]) bazlidir; bu yuzden
 * kullanici eylemleri [chatId] ile calisir. [characterId] yalnizca karakter
 * kartini okumak ve karakter bazli toplamlari (son mesaj zamani, okunmamis
 * sayisi) guncellemek icin gerekir.
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
    private val chatDao: ChatDao,
    private val personaRepository: PersonaRepository,
    private val memoryRepository: MemoryRepository,
    private val router: LlmRouter,
    private val promptBuilder: PromptBuilder,
    private val json: Json,
    private val summarizer: MemorySummarizer,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> =
        messageDao.observeForChat(chatId)

    // ------------------------------------------------------------------
    // Kullanici eylemleri
    // ------------------------------------------------------------------

    /**
     * Kullanici mesajini ekler; hem karakterin hem sohbetin son mesaj zamanini
     * gunceller.
     */
    suspend fun appendUserMessage(chatId: String, characterId: String, text: String): Long =
        withContext(io) {
            val now = System.currentTimeMillis()
            val character = characterDao.getById(characterId)
            val body = Placeholders.resolve(
                text = text.trim(),
                characterName = character?.name.orEmpty(),
                userName = personaRepository.resolve(character, chatDao.getById(chatId))?.name,
            )
            val id = messageDao.insert(
                MessageEntity(
                    characterId = characterId,
                    chatId = chatId,
                    role = MessageEntity.ROLE_USER,
                    content = body,
                    variantsJson = encodeVariants(listOf(body)),
                    createdAt = now,
                ),
            )
            chatDao.touchLastMessage(chatId, now)
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
    suspend fun ensureGreeting(chat: ChatEntity, character: CharacterEntity) = withContext(io) {
        if (messageDao.count(chat.id) > 0) return@withContext
        if (character.firstMessage.isBlank()) return@withContext
        val now = System.currentTimeMillis()
        val body = Placeholders.resolve(
            text = character.firstMessage.trim(),
            characterName = character.name,
            userName = personaRepository.resolve(character, chat)?.name,
        )
        messageDao.insert(
            MessageEntity(
                characterId = character.id,
                chatId = chat.id,
                role = MessageEntity.ROLE_ASSISTANT,
                content = body,
                variantsJson = encodeVariants(listOf(body)),
                createdAt = now,
            ),
        )
        chatDao.touchLastMessage(chat.id, now)
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
        // §8: sohbet ve karakter zaman damgalari birlikte guncellenir.
        val latestAt = messageDao.getLatest(message.chatId)?.createdAt ?: 0L
        chatDao.touchLastMessage(message.chatId, latestAt)
        characterDao.touchLastMessage(message.characterId, latestAt)
        // §2.1: mesaj ozetlenmis bolgeye aitse hafiza ondan arindirilir. Arka
        // planda calisir; kullanici silme islemini beklemez.
        val chatId = message.chatId
        val characterId = message.characterId
        appScope.launch { summarizer.rebuildFrom(chatId, characterId, messageId) }
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
        // §2.1: secilen varyant ozetlenmis bolgeye aitse hafiza yeniden kurulur.
        val chatId = message.chatId
        val characterId = message.characterId
        if (message.role == MessageEntity.ROLE_ASSISTANT) {
            appScope.launch { summarizer.rebuildFrom(chatId, characterId, messageId) }
        }
    }

    suspend fun markRead(chatId: String, characterId: String) = withContext(io) {
        messageDao.markAllRead(chatId)
        chatDao.setUnreadCount(chatId, 0)
        // Karakter rozeti, karakterin butun sohbetlerindeki toplam okunmamistir.
        characterDao.setUnreadCount(characterId, chatDao.unreadForCharacter(characterId))
    }

    // ------------------------------------------------------------------
    // Taslak asistani (Astroid, §7.1)
    // ------------------------------------------------------------------

    /**
     * Kullanicinin yazdigi taslagi zenginlestirir. Tam baglam (karakter karti +
     * persona + hafiza + son mesajlar) kurulur; taslak KULLANICININ agzindan
     * yazilmis bir mesaj oldugu icin talimat son bir kullanici mesaji olarak
     * eklenir ve cikti yine kullanici diliyle uretilir. Hata/bos yanit
     * durumunda `null` doner; cagiran taraf hatayi kullaniciya bildirir.
     */
    suspend fun enhanceDraft(chatId: String, characterId: String, draft: String): String? =
        withContext(io) {
            val character = characterDao.getById(characterId) ?: return@withContext null
            val persona = personaRepository.resolve(character, chatDao.getById(chatId))
                ?: personaRepository.getOrCreateSelected()
            val recent = messageDao
                .getLast(chatId, Tuning.DRAFT_CONTEXT_MESSAGES)
                .reversed()
            val memory = memoryRepository.read(chatId)
            val prompt = promptBuilder.build(
                character = character,
                persona = persona,
                memory = memory,
                history = recent,
                nowMillis = System.currentTimeMillis(),
            )
            val result = router.complete(
                role = ModelRole.CHAT,
                messages = prompt + AiMessage.user(ENHANCE_INSTRUCTION + "\n\n---\n" + draft.trim()),
                temperature = Tuning.DRAFT_TEMPERATURE,
                maxTokens = Tuning.DRAFT_MAX_TOKENS,
            )
            result.text.trim().ifBlank { null }
        }

    /**
     * Karakterin son mesajina uygun, KULLANICININ bir sonraki mesajini onerir.
     * Tam prompt (karakter karti + hafiza + son mesajlar) kurulur ve sonuna
     * "kullanici olarak yaz" talimati eklenir. Hata/bos yanitta `null`.
     */
    suspend fun suggestReply(chatId: String, characterId: String): String? = withContext(io) {
        val character = characterDao.getById(characterId) ?: return@withContext null
        val persona = personaRepository.resolve(character, chatDao.getById(chatId))
            ?: personaRepository.getOrCreateSelected()
        val recent = messageDao
            .getLast(chatId, Tuning.DRAFT_CONTEXT_MESSAGES)
            .reversed()
        val memory = memoryRepository.read(chatId)
        val prompt = promptBuilder.build(
            character = character,
            persona = persona,
            memory = memory,
            history = recent,
            nowMillis = System.currentTimeMillis(),
        )
        val result = router.complete(
            role = ModelRole.CHAT,
            messages = prompt + AiMessage.user(SUGGEST_INSTRUCTION),
            temperature = Tuning.DRAFT_TEMPERATURE,
            maxTokens = Tuning.DRAFT_MAX_TOKENS,
        )
        result.text.trim().ifBlank { null }
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
    fun generateReply(chatId: String, characterId: String, regenerate: Boolean): Flow<GenEvent> = flow {
        val character = withContext(io) { characterDao.getById(characterId) }
        if (character == null) {
            emit(GenEvent.Failed("Karakter bulunamadı.", needsApiKey = false))
            return@flow
        }
        val chat = withContext(io) { chatDao.getById(chatId) }

        val target = withContext(io) {
            val last = messageDao.getLatest(chatId)
            if (regenerate && last?.role == MessageEntity.ROLE_ASSISTANT) last else null
        }

        val prompt = withContext(io) {
            val recent = messageDao
                .getLast(chatId, Tuning.RECENT_MESSAGES_IN_PROMPT + 1)
                .reversed()
                .filter { it.id != target?.id }
            // Sohbete atanmis persona; yoksa karakterin personasi, o da yoksa
            // varsayilan. Hicbiri yoksa bos bir varsayilan olusturulur ki prompt
            // kurucu kullanici adini alabilsin.
            val persona = personaRepository.resolve(character, chat)
                ?: personaRepository.getOrCreateSelected()
            val memory = memoryRepository.read(chatId)
            promptBuilder.build(
                character = character,
                persona = persona,
                memory = memory,
                history = recent,
                nowMillis = System.currentTimeMillis(),
            )
        }

        val buffer = StringBuilder()
        var usage: AiUsage? = null
        // §6 gecis bildirimi: router callback'i ayri bir coroutine'de tetiklenebilir,
        // bu yuzden dogrudan emit etmek yerine kanal uzerinden ana akisa tasinir.
        val switches = Channel<GenEvent.ProviderSwitched>(Channel.UNLIMITED)
        suspend fun drainSwitches() {
            while (true) {
                val switched = switches.tryReceive().getOrNull() ?: break
                emit(switched)
            }
        }

        emit(GenEvent.Started)
        try {
            router.stream(
                role = ModelRole.CHAT,
                messages = prompt,
                temperature = character.temperature,
                maxTokens = character.maxTokens,
                onProviderSwitch = { from, to ->
                    switches.trySend(GenEvent.ProviderSwitched(from, to))
                },
            ).collect { event ->
                drainSwitches()
                when (event) {
                    is AiStreamEvent.Delta -> {
                        buffer.append(event.text)
                        emit(GenEvent.Delta(event.text))
                    }
                    is AiStreamEvent.Usage -> usage = event.usage
                    AiStreamEvent.Done -> Unit
                }
            }
            drainSwitches()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                persistPartial(chatId, characterId, target, buffer.toString(), usage, isError = false)
            }
            emit(GenEvent.Cancelled)
            throw e
        } catch (e: MissingApiKeyException) {
            emit(GenEvent.Failed("", needsApiKey = true))
            return@flow
        } catch (e: Throwable) {
            val partial = buffer.toString()
            if (partial.isNotBlank()) {
                withContext(NonCancellable) {
                    persistPartial(chatId, characterId, target, partial, usage, isError = true)
                }
            }
            emit(GenEvent.Failed(e.message ?: "", needsApiKey = false))
            return@flow
        } finally {
            switches.close()
        }

        val id = withContext(io) {
            persistPartial(chatId, characterId, target, buffer.toString(), usage, isError = false)
        }
        emit(GenEvent.Completed(id ?: -1L))
    }

    /**
     * Biriken metni kalici hale getirir. [target] null ise yeni satir ekler,
     * aksi halde o satiri guncelleyip eski metni varyant olarak saklar.
     */
    private suspend fun persistPartial(
        chatId: String,
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
                    chatId = chatId,
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
            chatDao.touchLastMessage(chatId, now)
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

    private companion object {
        /**
         * Talimatlar Ingilizce yazilir (modeller talimati boyle daha guvenilir
         * izler) ama cikti dili acikca sohbetin diline baglanir; boylece
         * Adım 3'teki "son mesajin diline uy" kuraliyla da tutarli kalir.
         */
        const val ENHANCE_INSTRUCTION =
            "The text below is a draft message that the USER is about to send. " +
                "Rewrite it so it fits the conversation tone and the user's voice: " +
                "clearer, more natural and more in-character. Keep the same intent " +
                "and approximate length. Output ONLY the rewritten message, with no " +
                "quotes, labels or explanations. Write in the same language as the draft."

        const val SUGGEST_INSTRUCTION =
            "Now write the next message that the USER would send in this conversation. " +
                "It must be the user's own message — do NOT reply as the character. " +
                "Keep it natural and consistent with the recent exchange. Output ONLY " +
                "that message, with no quotes, labels or explanations. Write in the " +
                "same language as the recent conversation."
    }
}
