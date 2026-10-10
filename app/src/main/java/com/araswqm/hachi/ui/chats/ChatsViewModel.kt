package com.araswqm.hachi.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hachi.data.db.entity.CharacterEntity
import com.araswqm.hachi.data.repo.CharacterRepository
import com.araswqm.hachi.data.settings.AppSettings
import com.araswqm.hachi.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Sohbetler listesi: karakterler, silme ve ilk giristeki izin istemleri (§4). */
@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val characters: StateFlow<List<CharacterEntity>> = characterRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §4: bildirim izni isteminin daha once yapilip yapilmadigini tasir. */
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /**
     * Karakteri siler. Veritabani kayitlari `ON DELETE CASCADE` ile birlikte
     * gider; avatar ve `memory/<id>.md` dosyalari da repository tarafindan
     * temizlenir.
     */
    fun deleteCharacter(characterId: String) {
        viewModelScope.launch { characterRepository.deleteCharacter(characterId) }
    }

    /**
     * §4: sistem izin penceresi yalnizca BIR kez gosterilir. Kullanici izni
     * reddetse de tekrar sormayiz; gerekirse Ayarlar'dan acilir.
     */
    fun markNotifPermissionAsked() {
        viewModelScope.launch { settingsRepository.setNotifPermissionAsked(true) }
    }
}
