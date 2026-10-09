package com.araswqm.hatir.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.araswqm.hatir.core.IoDispatcher
import com.araswqm.hatir.core.Tuning
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Kullanicinin uygulama geneli ayarlari (sirri olmayan kismi). */
data class AppSettings(
    val baseUrl: String = Tuning.DEFAULT_BASE_URL,
    val modelChat: String = Tuning.DEFAULT_MODEL_CHAT,
    val modelSummary: String = Tuning.DEFAULT_MODEL_SUMMARY,
    val modelDecision: String = Tuning.DEFAULT_MODEL_DECISION,
    val dndStartHour: Int = Tuning.DND_START_HOUR,
    val dndEndHour: Int = Tuning.DND_END_HOUR,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Ayarlarin tek kaynagi: DataStore (tercihler) + Keystore (sir).
 *
 * API anahtari DataStore'a **sifreli** yazilir; `settings.preferences_pb`
 * dosyasi `data_extraction_rules.xml` / `backup_rules.xml` ile yedeklemeden
 * haric tutulur. Cozulemeyen anahtar (ornegin yedekten donen cihaz) yok
 * sayilir; kullanici yeniden girer.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: SecretCrypto,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val store: DataStore<Preferences> = context.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[KEY_BASE_URL] ?: Tuning.DEFAULT_BASE_URL,
            modelChat = prefs[KEY_MODEL_CHAT] ?: Tuning.DEFAULT_MODEL_CHAT,
            modelSummary = prefs[KEY_MODEL_SUMMARY] ?: Tuning.DEFAULT_MODEL_SUMMARY,
            modelDecision = prefs[KEY_MODEL_DECISION] ?: Tuning.DEFAULT_MODEL_DECISION,
            dndStartHour = prefs[KEY_DND_START] ?: Tuning.DND_START_HOUR,
            dndEndHour = prefs[KEY_DND_END] ?: Tuning.DND_END_HOUR,
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
        )
    }

    /** Cozulmus API anahtari; ayarlanmamissa null. */
    val apiKey: Flow<String?> = store.data.map { prefs ->
        prefs[KEY_API_KEY]?.let { encrypted ->
            crypto.decrypt(encrypted)?.takeIf { it.isNotBlank() }
        }
    }

    val hasApiKey: Flow<Boolean> = apiKey.map { !it.isNullOrBlank() }

    suspend fun currentApiKey(): String? = withContext(io) { apiKey.first() }

    /** Anlik ayar goruntusu; arka plan isleri ve prompt kurucu icin. */
    suspend fun currentSettings(): AppSettings = withContext(io) { settings.first() }

    suspend fun setApiKey(value: String) = withContext(io) {
        store.edit { prefs ->
            val trimmed = value.trim()
            if (trimmed.isEmpty()) prefs.remove(KEY_API_KEY)
            else prefs[KEY_API_KEY] = crypto.encrypt(trimmed)
        }
    }

    suspend fun setBaseUrl(value: String) = withContext(io) {
        store.edit { it[KEY_BASE_URL] = value.trim().ifBlank { Tuning.DEFAULT_BASE_URL } }
    }

    suspend fun setModelChat(value: String) = withContext(io) {
        store.edit { it[KEY_MODEL_CHAT] = value.ifBlank { Tuning.DEFAULT_MODEL_CHAT } }
    }

    suspend fun setModelSummary(value: String) = withContext(io) {
        store.edit { it[KEY_MODEL_SUMMARY] = value.ifBlank { Tuning.DEFAULT_MODEL_SUMMARY } }
    }

    suspend fun setModelDecision(value: String) = withContext(io) {
        store.edit { it[KEY_MODEL_DECISION] = value.ifBlank { Tuning.DEFAULT_MODEL_DECISION } }
    }

    suspend fun setDnd(startHour: Int, endHour: Int) = withContext(io) {
        store.edit {
            it[KEY_DND_START] = startHour.coerceIn(0, 23)
            it[KEY_DND_END] = endHour.coerceIn(0, 23)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = withContext(io) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) = withContext(io) {
        store.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val KEY_API_KEY = stringPreferencesKey("deepseek_api_key_enc")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_MODEL_CHAT = stringPreferencesKey("model_chat")
        val KEY_MODEL_SUMMARY = stringPreferencesKey("model_summary")
        val KEY_MODEL_DECISION = stringPreferencesKey("model_decision")
        val KEY_DND_START = intPreferencesKey("dnd_start_hour")
        val KEY_DND_END = intPreferencesKey("dnd_end_hour")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}

/** Dosya adi `backup_rules.xml` ile ayni olmali: ayarlar yedeklenmez. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)
