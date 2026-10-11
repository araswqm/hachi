package com.araswqm.hachi.ui.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hachi.R
import com.araswqm.hachi.data.db.dao.ChatDao
import com.araswqm.hachi.data.db.entity.CharacterEntity
import com.araswqm.hachi.data.db.entity.ChatEntity
import com.araswqm.hachi.data.db.entity.PersonaEntity
import com.araswqm.hachi.data.repo.CharacterRepository
import com.araswqm.hachi.data.repo.PersonaRepository
import com.araswqm.hachi.ui.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Kullaniciya gosterilecek mesaj; metin kaynagi ekran tarafindan cozulur. */
data class DetailMessage(
    @StringRes val resId: Int,
    val ok: Boolean,
)

/** Paylasima hazir disa aktarma dosyasi. */
data class ExportedCard(val file: File, val mimeType: String)

/**
 * Karakter detay ekrani: kart bilgileri, persona secimi, kendiliğinden mesaj
 * anahtari ve silme.
 *
 * Yakinlik puani BILEREK gosterilmez; yalnizca gizli hata ayiklama ekraninda
 * (Stage 4) gorunur. Hafiza ve takip konulari bu ekrandan KALDIRILDI.
 */
@HiltViewModel
class CharacterDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val characterRepository: CharacterRepository,
    private val personaRepository: PersonaRepository,
    private val chatDao: ChatDao,
) : ViewModel() {

    val characterId: String =
        savedStateHandle.get<String>(Routes.ARG_CHARACTER_ID).orEmpty()

    /** Sohbet ekranindan girildiyse o sohbet; ana sayfadan girildiyse null (§8). */
    val chatId: String? =
        savedStateHandle.get<String>(Routes.ARG_CHAT_ID)?.takeIf { it.isNotBlank() }

    val character: StateFlow<CharacterEntity?> = characterRepository.observeById(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cagirilan sohbet (varsa); persona secimi ona ozel uygulanir. */
    val chat: StateFlow<ChatEntity?> =
        if (chatId == null) {
            MutableStateFlow(null).asStateFlow()
        } else {
            chatDao.observeById(chatId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

    /** Sohbet-basina persona secimi icin butun personalar (§3). */
    val personas: StateFlow<List<PersonaEntity>> = personaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<DetailMessage?>(null)
    val message: StateFlow<DetailMessage?> = _message.asStateFlow()

    private val _exported = MutableStateFlow<ExportedCard?>(null)
    val exported: StateFlow<ExportedCard?> = _exported.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun setProactiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = characterRepository.getById(characterId) ?: return@launch
            characterRepository.save(current.copy(proactiveEnabled = enabled))
        }
    }

    /**
     * Persona atar. Bir sohbet baglaminda cagrildiysa yalniz o sohbete
     * (`ChatEntity.personaId`), aksi halde karakterin genel ayarina yazilir (§3, §8).
     * `null` → varsayilan personaya doner.
     */
    fun setPersona(personaId: String?) {
        viewModelScope.launch {
            val activeChatId = chatId
            if (activeChatId != null) {
                chatDao.setPersona(activeChatId, personaId, System.currentTimeMillis())
            } else {
                val current = characterRepository.getById(characterId) ?: return@launch
                characterRepository.save(current.copy(personaId = personaId))
            }
        }
    }

    /** Karti `cache/exports/` altina yazar; paylasma isini ekran yapar. */
    fun exportCard() {
        viewModelScope.launch {
            val file = characterRepository.writeExportFile(characterId)
            if (file == null) {
                _message.value = DetailMessage(R.string.editor_export_failed, ok = false)
                return@launch
            }
            val mime = if (file.extension.equals("png", ignoreCase = true)) {
                "image/png"
            } else {
                "application/json"
            }
            _exported.value = ExportedCard(file, mime)
        }
    }

    fun consumeExport() {
        _exported.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun deleteCharacter() {
        viewModelScope.launch {
            characterRepository.deleteCharacter(characterId)
            _deleted.value = true
        }
    }
}
