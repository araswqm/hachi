package com.araswqm.hachi.ui.personas

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hachi.core.IoDispatcher
import com.araswqm.hachi.data.db.entity.PersonaEntity
import com.araswqm.hachi.data.repo.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persona yonetimi (§3): ekle / duzenle / sil / varsayilan sec / avatar.
 *
 * Sohbet-basina persona atamasi burada degil, karakter detay ekranindadir;
 * burasi yalnizca kullanicinin kimlik listesini yonetir.
 */
@HiltViewModel
class PersonasViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personas: PersonaRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val items: StateFlow<List<PersonaEntity>> = personas.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add() {
        viewModelScope.launch { personas.create() }
    }

    fun save(id: String, name: String, about: String) {
        viewModelScope.launch {
            val current = personas.getById(id) ?: return@launch
            personas.save(current.copy(name = name.trim(), about = about.trim()))
        }
    }

    /** Varsayilan personayi degistirir (ayni anda tek secili kalir). */
    fun makeDefault(id: String) {
        viewModelScope.launch { personas.setSelected(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { personas.delete(id) }
    }

    fun pickAvatar(id: String, uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(io) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes != null && bytes.isNotEmpty()) personas.setAvatar(id, bytes)
        }
    }

    fun clearAvatar(id: String) {
        viewModelScope.launch { personas.clearAvatar(id) }
    }
}
