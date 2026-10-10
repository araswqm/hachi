package com.araswqm.hachi.ui.settings

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hachi.core.LocaleController
import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.ai.provider.ModelRole
import com.araswqm.hachi.data.ai.provider.ProviderId
import com.araswqm.hachi.data.db.entity.PersonaEntity
import com.araswqm.hachi.data.repo.PersonaRepository
import com.araswqm.hachi.data.settings.AppLanguage
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
    /** §7.2: kok dokunsal geri bildirim anahtari ([LocalHapticsEnabled]). */
    val hapticsEnabled: Boolean = true,
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
            hapticsEnabled = current.hapticsEnabled,
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
    // Saglayicilar (§6)
    // ------------------------------------------------------------------

    /** Bir saglayicinin anahtarini yazar; bos deger anahtari siler. */
    fun setProviderApiKey(id: ProviderId, value: String) {
        viewModelScope.launch { settingsRepository.setProviderApiKey(id, value) }
    }

    fun setProviderEnabled(id: ProviderId, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setProviderEnabled(id, enabled) }
    }

    fun setProviderBaseUrl(id: ProviderId, value: String) {
        viewModelScope.launch { settingsRepository.setProviderBaseUrl(id, value) }
    }

    fun setProviderModel(id: ProviderId, role: ModelRole, value: String) {
        viewModelScope.launch { settingsRepository.setProviderModel(id, role, value) }
    }

    /** Zincirde sirayi degistirir (yukari = -1, asagi = +1). Sinirlarda yok sayilir. */
    fun moveProvider(id: ProviderId, delta: Int) {
        val order = settings.value.providerOrder.toMutableList()
        val from = order.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (to == from) return
        order.add(to, order.removeAt(from))
        viewModelScope.launch { settingsRepository.setProviderOrder(order) }
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

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticsEnabled(enabled) }
    }

    /**
     * §5: arayuz dilini aninda uygular. Once framework'e bildirilir (aktivite
     * yeniden kurulur ve metinler yeni dilde gelir), sonra kalici kaydedilir.
     */
    fun setLanguage(language: AppLanguage) {
        LocaleController.apply(context, language.tag)
        viewModelScope.launch { settingsRepository.setLanguage(language) }
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
