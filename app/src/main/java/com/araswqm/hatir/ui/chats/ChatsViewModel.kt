package com.araswqm.hatir.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hatir.data.db.entity.CharacterEntity
import com.araswqm.hatir.data.repo.CharacterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Sohbetler listesi: karakterler ve silme. */
@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val characterRepository: CharacterRepository,
) : ViewModel() {

    val characters: StateFlow<List<CharacterEntity>> = characterRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Karakteri siler. Veritabani kayitlari `ON DELETE CASCADE` ile birlikte
     * gider; avatar ve `memory/<id>.md` dosyalari da repository tarafindan
     * temizlenir.
     */
    fun deleteCharacter(characterId: String) {
        viewModelScope.launch { characterRepository.deleteCharacter(characterId) }
    }
}
