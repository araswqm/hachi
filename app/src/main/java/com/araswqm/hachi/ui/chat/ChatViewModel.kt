package com.araswqm.hachi.ui.chat

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hachi.R
import com.araswqm.hachi.core.AppFiles
import com.araswqm.hachi.core.ApplicationScope
import com.araswqm.hachi.data.ai.MissingApiKeyException
import com.araswqm.hachi.data.ai.Placeholders
import com.araswqm.hachi.data.ai.provider.ProviderId
import com.araswqm.hachi.data.chat.ChatRepository
import com.araswqm.hachi.data.chat.GenEvent
import com.araswqm.hachi.data.db.dao.ChatDao
import com.araswqm.hachi.data.db.entity.CharacterEntity
import com.araswqm.hachi.data.db.entity.ChatEntity
import com.araswqm.hachi.data.db.entity.MessageEntity
import com.araswqm.hachi.data.db.entity.PersonaEntity
import com.araswqm.hachi.data.memory.MemorySummarizer
import com.araswqm.hachi.data.repo.CharacterRepository
import com.araswqm.hachi.data.repo.PersonaRepository
import com.araswqm.hachi.ui.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Tek bir mesajin arayuz modeli. */
data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val createdAt: Long,
    val isProactive: Boolean,
    val isError: Boolean,
    val isEdited: Boolean,
    val variantIndex: Int,
    val variantCount: Int,
) {
    val hasVariants: Boolean get() = variantCount > 1
}

/**
 * Gosterilecek bir hata. [id] her yeni hatada artar; arayuz ayni metin tekrar
 * gelse bile snackbar'i yeniden gosterebilsin diye (LaunchedEffect anahtari).
 * [resId] doluysa metin kaynaktan cozulur (yerellestirilebilir), aksi halde
 * ham [message] gosterilir. [resArgResIds] doluysa `%1$s`-tarzi yer tutucular
 * bu kaynaklarin cozulmus metinleriyle doldurulur (§6 saglayici gecisi).
 */
data class ChatError(
    val message: String,
    val needsApiKey: Boolean,
    val id: Long,
    @StringRes val resId: Int? = null,
    @StringRes val resArgResIds: List<Int> = emptyList(),
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val error: ChatError? = null,
    val input: String = "",
    val assistantBusy: Boolean = false,
    /** Karakterin sohbetleri (sohbet degistirme sayfasi icin). */
    val chats: List<ChatEntity> = emptyList(),
    /** Acik olan sohbet; henuz cozulmemisse `null`. */
    val currentChatId: String? = null,
) {
    /** Son mesaj asistana aitse (ve uretim yoksa) "yeniden uret" sunulur. */
    val canRegenerate: Boolean get() = !isGenerating && messages.lastOrNull()?.fromUser == false

    /** Taslak asistani yalnizca uretim ve asistan mesgul degilken calisir. */
    val canUseAssistant: Boolean get() = !isGenerating && !assistantBusy
}

/**
 * Sohbet ekrani.
 *
 * §8: bir karakterin birden fazla sohbeti olabilir; mesaj ve hafiza sohbet
 * bazlidir. Aktif sohbet [chatId] akisinda tutulur ve tum veri akislari ona
 * gore yeniden kurulur. Gelen `chatId` parametresi (varsa) acilista o sohbeti
 * secer; yoksa karakterin son sohbeti, o da yoksa yeni bir sohbet acilir.
 *
 * Uretim [ChatRepository.generateReply] akisindan gelir; metin once
 * [streamingText]'te birikir, veritabanina yalnizca akis bittiğinde/durdugunda
 * yazilir. Bu yuzden listede "hayalet" bos balon olusmaz.
 *
 * Ozetleme [ApplicationScope]'ta calisir: kullanici ekrandan ciksa bile
 * tamamlanir (kapanis worker'i zaten ayrica telafi eder).
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val characters: CharacterRepository,
    private val chat: ChatRepository,
    private val chatDao: ChatDao,
    private val personas: PersonaRepository,
    private val summarizer: MemorySummarizer,
    @ApplicationScope private val appScope: CoroutineScope,
    private val json: Json,
) : ViewModel() {

    val characterId: String = savedStateHandle.get<String>(Routes.ARG_CHARACTER_ID).orEmpty()

    private val generating = MutableStateFlow(false)
    private val streaming = MutableStateFlow("")
    private val errorState = MutableStateFlow<ChatError?>(null)
    private var errorSeq = 0L

    /** Aktif sohbet; cozulene kadar `null`. */
    private val currentChatIdState = MutableStateFlow<String?>(null)

    /** Disari acilan sohbet akisi (arayuz basligi/secimi bunu izler). */
    val chatId: StateFlow<String?> = currentChatIdState.asStateFlow()

    val character: StateFlow<CharacterEntity?> = characters
        .observeById(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Karakterin sohbetleri; en son konusulan ustte. */
    private val chatsFlow: Flow<List<ChatEntity>> =
        chatDao.observeForCharacter(characterId)

    /**
     * Aktif sohbette gecerli persona (§3/§8). Once sohbetin `personaId`'si,
     * sonra karakterin `personaId`'si, yoksa genel varsayilan persona. Kullanici
     * balonlarinin avatari ve mesaj metnindeki `{{user}}` buradan cozulur.
     */
    private val personaFlow: Flow<PersonaEntity?> = combine(
        characters.observeById(characterId),
        currentChatIdState,
    ) { character, activeChatId ->
        character to activeChatId
    }.flatMapLatest { (character, activeChatId) ->
        val chatPersonaId: Flow<String?> = if (activeChatId != null) {
            chatDao.observeById(activeChatId).map { it?.personaId }
        } else {
            flowOf(null)
        }
        combine(chatPersonaId, flowOf(character?.personaId)) { chatPid, characterPid ->
            chatPid ?: characterPid
        }
    }.flatMapLatest { personaId ->
        if (personaId != null) personas.observeById(personaId) else personas.observeSelected()
    }

    val persona: StateFlow<PersonaEntity?> = personaFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messages = combine(
        currentChatIdState.flatMapLatest { activeChatId ->
            if (activeChatId == null) flowOf(emptyList()) else chat.observeMessages(activeChatId)
        },
        characters.observeById(characterId),
        personaFlow,
    ) { rows, character, persona ->
        val characterName = character?.name.orEmpty()
        val userName = persona?.name
        rows.map { it.toUi(characterName, userName) }
    }

    private val draft = MutableStateFlow("")
    private val assistantBusy = MutableStateFlow(false)

    /** Uretimle ilgili uc akisi tek bir demette toplar (combine argüman siniri). */
    private val generationState = combine(generating, streaming, errorState) { g, s, e ->
        Triple(g, s, e)
    }

    /** Mesaj listesi + sohbet listesi + aktif sohbet kimligi tek demette. */
    private val listState = combine(messages, chatsFlow, currentChatIdState) { list, chats, activeChatId ->
        Triple(list, chats, activeChatId)
    }

    val uiState: StateFlow<ChatUiState> =
        combine(listState, generationState, draft, assistantBusy) { list, gen, input, busy ->
            ChatUiState(
                messages = list.first,
                isGenerating = gen.first,
                streamingText = gen.second,
                error = gen.third,
                input = input,
                assistantBusy = busy,
                chats = list.second,
                currentChatId = list.third,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            if (characterId.isBlank()) return@launch
            val requested = savedStateHandle.get<String>(Routes.ARG_CHAT_ID)?.takeIf { it.isNotBlank() }
            val target = requested ?: chatDao.getLatestForCharacter(characterId)?.id
            if (target != null) {
                activateChat(target)
            } else {
                createChatInternal()
            }
        }
    }

    // ------------------------------------------------------------------
    // Sohbet secimi
    // ------------------------------------------------------------------

    /** Baska bir sohbete gecer; devam eden uretimi iptal eder. */
    fun switchChat(id: String) {
        if (id == currentChatIdState.value) return
        clearInFlight()
        viewModelScope.launch { activateChat(id) }
    }

    /** Bu karakter icin yeni bir sohbet acar ve ona gecer. */
    fun createChat() {
        if (characterId.isBlank()) return
        clearInFlight()
        viewModelScope.launch { createChatInternal() }
    }

    /** Aktif sohbeti yukler: karsilama mesajini yazar ve okundu isaretler. */
    private suspend fun activateChat(id: String) {
        val entity = chatDao.getById(id) ?: return
        currentChatIdState.value = entity.id
        val character = characters.getById(characterId) ?: return
        chat.ensureGreeting(entity, character)
        chat.markRead(entity.id, characterId)
    }

    /** Yeni sohbet satiri olusturur, karsilama mesajini yazar ve ona gecer. */
    private suspend fun createChatInternal() {
        val character = characters.getById(characterId) ?: return
        val now = System.currentTimeMillis()
        val entity = ChatEntity(
            id = AppFiles.newId(),
            characterId = characterId,
            createdAt = now,
            updatedAt = now,
        )
        chatDao.insert(entity)
        chat.ensureGreeting(entity, character)
        currentChatIdState.value = entity.id
    }

    /** Uretim/hata durumunu sifirlar (sohbet degisiminde cagrilir). */
    private fun clearInFlight() {
        generationJob?.cancel()
        generating.value = false
        streaming.value = ""
        errorState.value = null
    }

    // ------------------------------------------------------------------
    // Kullanici eylemleri
    // ------------------------------------------------------------------

    /** Giris kutusundaki metni gunceller (metin artik ViewModel'de yasar). */
    fun onInputChange(text: String) {
        draft.value = text
    }

    fun send() {
        val activeChatId = currentChatIdState.value ?: return
        val trimmed = draft.value.trim()
        if (trimmed.isEmpty() || generating.value) return
        draft.value = ""
        viewModelScope.launch {
            chat.appendUserMessage(activeChatId, characterId, trimmed)
            launchGeneration(regenerate = false)
        }
    }

    // ------------------------------------------------------------------
    // Taslak asistani (Astroid, §7.1)
    // ------------------------------------------------------------------

    /** Eldeki taslagi LLM ile zenginlestirir; sonuc giris kutusuna yazilir. */
    fun enhanceDraft() {
        val activeChatId = currentChatIdState.value ?: return
        val current = draft.value.trim()
        if (current.isEmpty() || assistantBusy.value || generating.value) return
        runAssistant { chat.enhanceDraft(activeChatId, characterId, current) }
    }

    /** Karakterin son mesajina uygun bir kullanici yaniti onerir. */
    fun writeForMe() {
        val activeChatId = currentChatIdState.value ?: return
        if (assistantBusy.value || generating.value) return
        runAssistant { chat.suggestReply(activeChatId, characterId) }
    }

    /** Taslak cagrisini calistirir; mesguliyet bayragini ve hatayi yonetir. */
    private fun runAssistant(block: suspend () -> String?) {
        if (characterId.isBlank()) return
        viewModelScope.launch {
            assistantBusy.value = true
            errorState.value = null
            try {
                val result = block()
                if (result.isNullOrBlank()) {
                    reportErrorRes(R.string.assistant_failed, needsApiKey = false)
                } else {
                    draft.value = result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                reportErrorRes(
                    R.string.assistant_failed,
                    needsApiKey = e is MissingApiKeyException,
                )
            } finally {
                assistantBusy.value = false
            }
        }
    }

    fun regenerate() {
        if (generating.value || currentChatIdState.value == null) return
        launchGeneration(regenerate = true)
    }

    fun stop() {
        generationJob?.cancel()
    }

    fun editMessage(messageId: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { chat.editMessage(messageId, trimmed) }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { chat.deleteMessage(messageId) }
    }

    fun setVariant(messageId: Long, index: Int) {
        viewModelScope.launch { chat.setVariant(messageId, index) }
    }

    fun dismissError() {
        errorState.value = null
    }

    // ------------------------------------------------------------------
    // Ic isleyis
    // ------------------------------------------------------------------

    private fun launchGeneration(regenerate: Boolean) {
        val activeChatId = currentChatIdState.value ?: return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            generating.value = true
            streaming.value = ""
            errorState.value = null
            try {
                chat.generateReply(activeChatId, characterId, regenerate).collect { event ->
                    when (event) {
                        GenEvent.Started -> Unit

                        is GenEvent.Delta -> streaming.value += event.text

                        is GenEvent.Completed -> {
                            // Akis bitti; metin artik veritabaninda. Ozetleme
                            // esigi asildiysa kalici hafizayi guncelle.
                            maybeSummarize()
                        }

                        is GenEvent.Failed -> reportError(event.message, event.needsApiKey)

                        is GenEvent.ProviderSwitched ->
                            reportProviderSwitch(event.from, event.to)

                        GenEvent.Cancelled -> Unit
                    }
                }
            } catch (e: CancellationException) {
                // Kullanici durdurdu ya da ekran kapandi: kismi metin
                // ChatRepository tarafinda NonCancellable blokta kaydedildi.
                throw e
            } catch (e: Throwable) {
                reportError(e.message ?: "", needsApiKey = false)
            } finally {
                generating.value = false
                streaming.value = ""
            }
        }
    }

    private fun reportError(message: String, needsApiKey: Boolean) {
        errorSeq += 1
        errorState.value = ChatError(message = message, needsApiKey = needsApiKey, id = errorSeq)
    }

    /** Metni kaynaktan cozulecek (yerellestirilebilir) bir hata bildirir. */
    private fun reportErrorRes(@StringRes resId: Int, needsApiKey: Boolean) {
        errorSeq += 1
        errorState.value = ChatError(
            message = "",
            needsApiKey = needsApiKey,
            id = errorSeq,
            resId = resId,
        )
    }

    /**
     * §6: zincir bir sonraki saglayiciya gectiginde bilgilendirir. Uretim
     * durmaz — yalnizca snackbar gosterilir (kalici DB satiri yazilmaz).
     */
    private fun reportProviderSwitch(from: ProviderId, to: ProviderId) {
        errorSeq += 1
        errorState.value = ChatError(
            message = "",
            needsApiKey = false,
            id = errorSeq,
            resId = R.string.chat_provider_switched,
            resArgResIds = listOf(from.displayNameRes, to.displayNameRes),
        )
    }

    private fun maybeSummarize() {
        val activeChatId = currentChatIdState.value ?: return
        if (characterId.isBlank()) return
        appScope.launch {
            runCatching {
                if (summarizer.shouldSummarize(activeChatId)) {
                    summarizer.summarize(activeChatId, characterId, force = false)
                }
            }
        }
    }

    private fun MessageEntity.toUi(characterName: String, userName: String?): ChatMessage = ChatMessage(
        id = id,
        fromUser = role == MessageEntity.ROLE_USER,
        text = Placeholders.resolve(content, characterName, userName),
        createdAt = createdAt,
        isProactive = isProactive,
        isError = isError,
        isEdited = isEdited,
        variantIndex = variantIndex,
        variantCount = countVariants(variantsJson),
    )

    private fun countVariants(raw: String): Int {
        if (raw.isBlank()) return 1
        val parsed = runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        return parsed.size.coerceAtLeast(1)
    }
}
