package com.araswqm.hachi.ui.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hachi.R
import com.araswqm.hachi.data.db.dao.FollowupDao
import com.araswqm.hachi.data.db.entity.CharacterEntity
import com.araswqm.hachi.data.db.entity.FollowupEntity
import com.araswqm.hachi.data.repo.CharacterRepository
import com.araswqm.hachi.data.repo.MemoryRepository
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
 * Karakter detay ekrani: hafiza dosyasi, takip konulari, kart bilgileri,
 * kendiliğinden mesaj anahtari ve silme.
 *
 * Yakinlik puani BILEREK gosterilmez; yalnizca gizli hata ayiklama ekraninda
 * (Stage 4) gorunur.
 */
@HiltViewModel
class CharacterDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val characterRepository: CharacterRepository,
    private val memoryRepository: MemoryRepository,
    private val followupDao: FollowupDao,
) : ViewModel() {

    val characterId: String =
        savedStateHandle.get<String>(Routes.ARG_CHARACTER_ID).orEmpty()

    val character: StateFlow<CharacterEntity?> = characterRepository.observeById(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val followups: StateFlow<List<FollowupEntity>> = followupDao.observeAll(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _memory = MutableStateFlow("")
    val memory: StateFlow<String> = _memory.asStateFlow()

    private val _loadingMemory = MutableStateFlow(true)
    val loadingMemory: StateFlow<Boolean> = _loadingMemory.asStateFlow()

    private val _message = MutableStateFlow<DetailMessage?>(null)
    val message: StateFlow<DetailMessage?> = _message.asStateFlow()

    private val _exported = MutableStateFlow<ExportedCard?>(null)
    val exported: StateFlow<ExportedCard?> = _exported.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        reloadMemory()
    }

    fun reloadMemory() {
        viewModelScope.launch {
            _memory.value = memoryRepository.read(characterId)
            _loadingMemory.value = false
        }
    }

    /** Kullanici duzenlemesini diske yazar; tamamen bos metin dosyayi siler. */
    fun saveMemory(content: String) {
        viewModelScope.launch {
            memoryRepository.save(characterId, content)
            _memory.value = memoryRepository.read(characterId)
            _message.value = DetailMessage(R.string.detail_memory_saved, ok = true)
        }
    }

    fun setProactiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = characterRepository.getById(characterId) ?: return@launch
            characterRepository.save(current.copy(proactiveEnabled = enabled))
        }
    }

    fun deleteFollowup(id: Long) {
        viewModelScope.launch { followupDao.deleteById(id) }
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
