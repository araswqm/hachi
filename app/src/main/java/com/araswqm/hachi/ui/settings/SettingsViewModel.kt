package com.araswqm.hachi.ui.settings

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.db.entity.PersonaEntity
import com.araswqm.hachi.data.repo.PersonaRepository
import com.araswqm.hachi.data.settings.AppSettings
import com.araswqm.hachi.data.settings.SettingsRepository
import com.araswqm.hachi.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Tema karari icin gereken degerler; `MainActivity` bunu `HachiTheme`'e verir. */
data class ThemeState(
    val darkTheme: Boolean,
    val dynamicColor: Boolean,
    val nightOwl: Boolean,
)

/**
 * Ayarlar ekrani + uygulama geneli tema.
 *
 * `themeState` aktif olarak toplanir (`SharingStarted.Eagerly`) cunku kok
 * `setContent` blogu tema degerine ilk kareden ihtiyac duyar.
 *
 * Tema tercihi "Sistem" iken cihazin gece modu degisirse aktivite yeniden
 * olusturulmayabilir; bu yuzden sistem degerini `ComponentCallbacks` ile
 * dinleyip akisa ceviriyoruz. Kayit uygulama baglamina yapilir, sizinti olmaz.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val personaRepository: PersonaRepository,
) : ViewModel() {

    private val systemDark = MutableStateFlow(context.isSystemDark())

    private val configurationCallback = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            systemDark.value = newConfig.isNightMode()
        }

        override fun onLowMemory() = Unit
    }

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val hasApiKey: StateFlow<Boolean> = settingsRepository.hasApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val persona: StateFlow<PersonaEntity?> = personaRepository.observeSelected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeState: StateFlow<ThemeState> = combine(settings, systemDark) { current, dark ->
        ThemeState(
            darkTheme = when (current.themeMode) {
                ThemeMode.SYSTEM -> dark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            },
            dynamicColor = current.dynamicColor,
            nightOwl = current.nightOwl,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ThemeState(darkTheme = systemDark.value, dynamicColor = true, nightOwl = false),
    )

    init {
        context.registerComponentCallbacks(configurationCallback)
    }

    // ------------------------------------------------------------------
    // API / model
    // ------------------------------------------------------------------

    fun setApiKey(value: String) {
        viewModelScope.launch { settingsRepository.setApiKey(value) }
    }

    fun setBaseUrl(value: String) {
        viewModelScope.launch { settingsRepository.setBaseUrl(value) }
    }

    fun setModelChat(value: String) {
        viewModelScope.launch { settingsRepository.setModelChat(value) }
    }

    fun setModelSummary(value: String) {
        viewModelScope.launch { settingsRepository.setModelSummary(value) }
    }

    fun setModelDecision(value: String) {
        viewModelScope.launch { settingsRepository.setModelDecision(value) }
    }

    // ------------------------------------------------------------------
    // Persona
    // ------------------------------------------------------------------

    fun savePersona(name: String, about: String) {
        viewModelScope.launch { personaRepository.updateProfile(name.trim(), about.trim()) }
    }

    // ------------------------------------------------------------------
    // Rahatsiz etme (DND)
    // ------------------------------------------------------------------

    fun setDnd(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            settingsRepository.setDnd(
                startHour.coerceIn(0, 23),
                endHour.coerceIn(0, 23),
            )
        }
    }

    // ------------------------------------------------------------------
    // Gorunum
    // ------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setNightOwl(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNightOwl(enabled) }
    }

    override fun onCleared() {
        context.unregisterComponentCallbacks(configurationCallback)
        super.onCleared()
    }

    companion object {
        /** Model adi alani icin onerilen degerler; alan serbest metne de izin verir. */
        val KNOWN_MODELS: List<String> = Tuning.KNOWN_MODELS
    }
}

private fun Configuration.isNightMode(): Boolean =
    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

private fun Context.isSystemDark(): Boolean = resources.configuration.isNightMode()
