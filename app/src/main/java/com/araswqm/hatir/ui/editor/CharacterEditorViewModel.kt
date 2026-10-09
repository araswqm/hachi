package com.araswqm.hatir.ui.editor

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hatir.R
import com.araswqm.hatir.core.IoDispatcher
import com.araswqm.hatir.core.Tuning
import com.araswqm.hatir.data.card.CharacterCard
import com.araswqm.hatir.data.db.entity.CharacterEntity
import com.araswqm.hatir.data.repo.CharacterRepository
import com.araswqm.hatir.ui.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Duzenleyicinin gorunur durumu.
 *
 * [base] duzenlenmeyen alanlari (system_prompt, karakter kitabi, etiketler,
 * olusturma tarihi...) tasiyan orijinal kayittir; Kaydet yalnizca form
 * alanlarini bunun uzerine kopyalar. Boylece ice aktarilan bir kartin
 * dokunulmayan alanlari kaybolmaz.
 */
data class EditorState(
    val base: CharacterEntity,
    val isNew: Boolean,
    val loading: Boolean,
    val name: String = base.name,
    val description: String = base.description,
    val personality: String = base.personality,
    val scenario: String = base.scenario,
    val firstMessage: String = base.firstMessage,
    val exampleDialogs: String = base.exampleDialogs,
    val temperature: Float = base.temperature,
    val maxTokens: Int = base.maxTokens,

    /** Ekranda gosterilecek avatar yolu; yok/temizlendiyse null. */
    val avatarPath: String? = base.avatarPath,

    val nameError: Boolean = false,
)

/** Kullaniciya gosterilecek kisa mesaj; metin kaynagi ekran tarafindan cozulur. */
data class EditorMessage(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList(),
    val ok: Boolean,
)

/**
 * Karakter olusturma/duzenleme + kart ice aktarma.
 *
 * Yeni karakter icin [CharacterRepository.newCharacter] ile **kararli** bir id
 * uretilir; satir ancak Kaydet ile veritabanina girer. Bu yuzden kaydedilmemis
 * yeni bir karakter icin avatar dosyasi yazilmaz, ortada dosya kalmaz.
 */
@HiltViewModel
class CharacterEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val characterRepository: CharacterRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val requestedId: String? = savedStateHandle.get<String>(Routes.ARG_CHARACTER_ID)

    private val _state = MutableStateFlow(
        EditorState(
            base = characterRepository.newCharacter(),
            isNew = true,
            loading = true,
        ),
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** Henuz kaydedilmemis avatar baytlari (bellekten onizlenir). */
    private val _pendingAvatar = MutableStateFlow<ByteArray?>(null)
    val pendingAvatar: StateFlow<ByteArray?> = _pendingAvatar.asStateFlow()

    /** Kart ice aktarma sonucu; gosterildikten sonra [consumeMessage] ile silinir. */
    private val _message = MutableStateFlow<EditorMessage?>(null)
    val message: StateFlow<EditorMessage?> = _message.asStateFlow()

    /** Kaydetme tamamlaninca dolar; ekran bunu yakalayip geri doner. */
    private val _savedId = MutableStateFlow<String?>(null)
    val savedId: StateFlow<String?> = _savedId.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = requestedId?.let { characterRepository.getById(it) }
            val base = existing ?: characterRepository.newCharacter()
            _state.value = EditorState(
                base = base,
                isNew = existing == null,
                loading = false,
                avatarPath = existing?.avatarPath?.takeIf { File(context.filesDir, it).isFile },
            )
        }
    }

    // ------------------------------------------------------------------
    // Form alanlari
    // ------------------------------------------------------------------

    fun setName(value: String) = update { it.copy(name = value, nameError = false) }

    fun setDescription(value: String) = update { it.copy(description = value) }

    fun setPersonality(value: String) = update { it.copy(personality = value) }

    fun setScenario(value: String) = update { it.copy(scenario = value) }

    fun setFirstMessage(value: String) = update { it.copy(firstMessage = value) }

    fun setExampleDialogs(value: String) = update { it.copy(exampleDialogs = value) }

    fun setTemperature(value: Float) = update {
        it.copy(temperature = value.coerceIn(Tuning.MIN_TEMPERATURE, Tuning.MAX_TEMPERATURE))
    }

    fun setMaxTokens(value: Int) = update {
        it.copy(maxTokens = value.coerceIn(Tuning.MIN_MAX_TOKENS, Tuning.MAX_MAX_TOKENS))
    }

    // ------------------------------------------------------------------
    // Avatar
    // ------------------------------------------------------------------

    /** Galeriden secilen resim; henuz diske yazilmaz, onizleme icin tutulur. */
    fun setPendingAvatar(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        _pendingAvatar.value = bytes
        update { it.copy(avatarPath = null) }
    }

    /** Mevcut avatarı kaldirir; degisiklik Kaydet ile kalici olur. */
    fun clearAvatar() {
        _pendingAvatar.value = null
        update { it.copy(avatarPath = null) }
    }

    /** Galeriden secilen resmi okur; diske yazilmaz, yalnizca onizlemede tutulur. */
    fun pickAvatarFromUri(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(io) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                _message.value = EditorMessage(R.string.editor_import_read_error, ok = false)
                return@launch
            }
            setPendingAvatar(bytes)
        }
    }

    // ------------------------------------------------------------------
    // Kart ice aktarma
    // ------------------------------------------------------------------

    /**
     * `content://` URI'sinden kart okur. PNG ise icindeki `ccv3`/`chara`
     * chunk'i, degilse JSON olarak yorumlanir. Alanlar forma doldurulur;
     * karakter Kaydet'e basilmadan olusturulmaz.
     */
    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(io) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                _message.value = EditorMessage(R.string.editor_import_read_error, ok = false)
                return@launch
            }

            if (isPng(bytes)) {
                val parsed = characterRepository.parseCardPng(bytes)
                if (parsed == null) {
                    _message.value = EditorMessage(R.string.editor_import_no_png_card, ok = false)
                } else {
                    applyCard(parsed, avatarPng = bytes, fallbackName = fileNameOf(uri))
                }
            } else {
                val parsed = characterRepository.parseCardJson(bytes.toString(Charsets.UTF_8))
                if (parsed == null) {
                    _message.value = EditorMessage(R.string.editor_import_bad_json, ok = false)
                } else {
                    applyCard(parsed, avatarPng = null, fallbackName = fileNameOf(uri))
                }
            }
        }
    }

    private fun applyCard(card: CharacterCard, avatarPng: ByteArray?, fallbackName: String?) {
        val current = _state.value
        val named = if (card.name.isBlank() && !fallbackName.isNullOrBlank()) {
            card.copy(name = fallbackName)
        } else {
            card
        }
        // Kartin dokunulmayan alanlarini tasiyan yeni bir temel uret; PNG kart
        // avatar getirmediyse mevcut avatarı koru.
        val base = named.toEntity(
            id = current.base.id,
            avatarPath = current.avatarPath,
            now = current.base.createdAt,
        )
        _pendingAvatar.value = avatarPng
        _state.value = EditorState(
            base = base,
            isNew = current.isNew,
            loading = false,
            avatarPath = if (avatarPng != null) null else current.avatarPath,
        )
        _message.value = EditorMessage(
            R.string.editor_import_success,
            args = listOf(named.name),
            ok = true,
        )
    }

    // ------------------------------------------------------------------
    // Kaydetme
    // ------------------------------------------------------------------

    fun save() {
        val current = _state.value
        if (current.name.isBlank()) {
            update { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            val edited = current.base.copy(
                name = current.name.trim(),
                description = current.description.trim(),
                personality = current.personality.trim(),
                scenario = current.scenario.trim(),
                firstMessage = current.firstMessage.trim(),
                exampleDialogs = current.exampleDialogs.trim(),
                temperature = current.temperature
                    .coerceIn(Tuning.MIN_TEMPERATURE, Tuning.MAX_TEMPERATURE),
                maxTokens = current.maxTokens
                    .coerceIn(Tuning.MIN_MAX_TOKENS, Tuning.MAX_MAX_TOKENS),
                avatarPath = current.avatarPath,
            )
            characterRepository.save(edited)

            val pending = _pendingAvatar.value
            when {
                pending != null -> characterRepository.setAvatar(edited.id, pending)
                current.avatarPath == null -> characterRepository.clearAvatar(edited.id)
            }

            _savedId.value = edited.id
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private inline fun update(transform: (EditorState) -> EditorState) {
        _state.value = transform(_state.value)
    }

    private fun fileNameOf(uri: Uri): String? = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringBeforeLast('.')

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= PNG_SIGNATURE.size &&
            PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
