package com.araswqm.hatir.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hatir.core.ApplicationScope
import com.araswqm.hatir.data.ai.Placeholders
import com.araswqm.hatir.data.chat.ChatRepository
import com.araswqm.hatir.data.chat.GenEvent
import com.araswqm.hatir.data.db.entity.CharacterEntity
import com.araswqm.hatir.data.db.entity.MessageEntity
import com.araswqm.hatir.data.memory.MemorySummarizer
import com.araswqm.hatir.data.repo.CharacterRepository
import com.araswqm.hatir.data.repo.PersonaRepository
import com.araswqm.hatir.ui.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 */
data class ChatError(val message: String, val needsApiKey: Boolean, val id: Long)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val error: ChatError? = null,
) {
    /** Son mesaj asistana aitse (ve uretim yoksa) "yeniden uret" sunulur. */
    val canRegenerate: Boolean get() = !isGenerating && messages.lastOrNull()?.fromUser == false
}

/**
 * Sohbet ekrani.
 *
 * Uretim [ChatRepository.generateReply] akisindan gelir; metin once
 * [streamingText]'te birikir, veritabanina yalnizca akis bittiğinde/durduğunda
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

    val character: StateFlow<CharacterEntity?> = characters
        .observeById(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messages = combine(
        chat.observeMessages(characterId),
        characters.observeById(characterId),
        personas.observeSelected(),
    ) { rows, character, persona ->
        val characterName = character?.name.orEmpty()
        val userName = persona?.name
        rows.map { it.toUi(characterName, userName) }
    }

    val uiState: StateFlow<ChatUiState> =
        combine(messages, generating, streaming, errorState) { list, isGenerating, text, error ->
            ChatUiState(
                messages = list,
                isGenerating = isGenerating,
                streamingText = text,
                error = error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            if (characterId.isBlank()) return@launch
            characters.getById(characterId)?.let { chat.ensureGreeting(it) }
            chat.markRead(characterId)
        }
    }

    // ------------------------------------------------------------------
    // Kullanici eylemleri
    // ------------------------------------------------------------------

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || generating.value) return
        viewModelScope.launch {
            chat.appendUserMessage(characterId, trimmed)
            launchGeneration(regenerate = false)
        }
    }

    fun regenerate() {
        if (generating.value || characterId.isBlank()) return
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
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            generating.value = true
            streaming.value = ""
            errorState.value = null
            try {
                chat.generateReply(characterId, regenerate).collect { event ->
                    when (event) {
                        GenEvent.Started -> Unit

                        is GenEvent.Delta -> streaming.value += event.text

                        is GenEvent.Completed -> {
                            // Akis bitti; metin artik veritabaninda. Ozetleme
                            // esigi asildiysa kalici hafizayi guncelle.
                            maybeSummarize()
                        }

                        is GenEvent.Failed -> reportError(event.message, event.needsApiKey)

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

    private fun maybeSummarize() {
        if (characterId.isBlank()) return
        appScope.launch {
            runCatching {
                if (summarizer.shouldSummarize(characterId)) {
                    summarizer.summarize(characterId, force = false)
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
