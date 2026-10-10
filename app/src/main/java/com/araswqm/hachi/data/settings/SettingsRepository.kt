package com.araswqm.hachi.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.araswqm.hachi.core.IoDispatcher
import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.ai.provider.ModelRole
import com.araswqm.hachi.data.ai.provider.ProviderId
import com.araswqm.hachi.data.ai.provider.defaultModel
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
    val dndStartHour: Int = Tuning.DND_START_HOUR,
    val dndEndHour: Int = Tuning.DND_END_HOUR,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Night Owl: saf OLED siyahi arka plan + gece icin ayarli kontrast. */
    val nightOwl: Boolean = false,
    /** Akis sirasinda kelime basina hafif dokunsal geri bildirim (§7.2). */
    val hapticsEnabled: Boolean = true,
    /** Bildirim izni bir kez soruldu mu (§4); tekrar tekrar sorulmasin diye. */
    val notifPermissionAsked: Boolean = false,
    /** Arayuz dili (§5); null = sistem dilini izle. */
    val languageTag: String? = null,
    /** §6: saglayicilarin deneme sirasi (kullanici yukari/asagi tasiyabilir). */
    val providerOrder: List<ProviderId> = ProviderId.DEFAULT_ORDER,
    /** §6: her saglayicinin ekranda gosterilen ayar satiri (sir haric). */
    val providers: List<ProviderView> = emptyList(),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** §5: desteklenen arayuz dilleri. `tag` null ise sistem dili izlenir. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    TURKISH("tr"),
    SPANISH("es"),
    PORTUGUESE("pt"),
    INDONESIAN("in");

    companion object {
        /** Kayitli dil etiketini enum'a cevirir; taninmayan/`null` -> [SYSTEM]. */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && it.tag == tag } ?: SYSTEM
    }
}

/**
 * §6 sohbet/ozet/karar cagrisi icin cozulmus saglayici: anahtar desifre
 * edilmis haldedir ve yalnizca zincire giren saglayicilarda uretilir.
 */
data class ProviderConfig(
    val id: ProviderId,
    val apiKey: String,
    val baseUrl: String,
    val modelChat: String,
    val modelSummary: String,
    val modelDecision: String,
) {
    /** Cozulmus model; bos/ayarlanmamis alan saglayicinin varsayilanina duser. */
    fun modelFor(role: ModelRole): String = when (role) {
        ModelRole.CHAT -> modelChat
        ModelRole.SUMMARY -> modelSummary
        ModelRole.DECISION -> modelDecision
    }
}

/** §6 ayar ekrani satiri: sir icermez, yalnizca "anahtar var mi" bilgisi. */
data class ProviderView(
    val id: ProviderId,
    val enabled: Boolean,
    val hasKey: Boolean,
    val baseUrl: String,
    val modelChat: String,
    val modelSummary: String,
    val modelDecision: String,
)

/**
 * Ayarlarin tek kaynagi: DataStore (tercihler) + Keystore (sir).
 *
 * Saglayici API anahtarlari DataStore'a **sifreli** yazilir; `settings.preferences_pb`
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
        val order = orderOf(prefs)
        AppSettings(
            dndStartHour = prefs[KEY_DND_START] ?: Tuning.DND_START_HOUR,
            dndEndHour = prefs[KEY_DND_END] ?: Tuning.DND_END_HOUR,
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            nightOwl = prefs[KEY_NIGHT_OWL] ?: false,
            hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
            notifPermissionAsked = prefs[KEY_NOTIF_ASKED] ?: false,
            languageTag = prefs[KEY_LANGUAGE],
            providerOrder = order,
            providers = order.map { viewOf(prefs, it) },
        )
    }

    /** Anlik ayar goruntusu; arka plan isleri ve prompt kurucu icin. */
    suspend fun currentSettings(): AppSettings = withContext(io) { settings.first() }

    /**
     * §6 zinciri: kullanicinin sirasiyla, yalnizca **etkin VE anahtari olan**
     * saglayicilar. Hicbiri yoksa bos liste doner ([MissingApiKeyException]
     * karari cagiricida).
     */
    suspend fun providerChain(): List<ProviderConfig> = withContext(io) {
        val prefs = store.data.first()
        orderOf(prefs).mapNotNull { id ->
            if (prefs[kEnabled(id)] == false) return@mapNotNull null
            val key = prefs[kApiKey(id)]?.let { crypto.decrypt(it) }?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            ProviderConfig(
                id = id,
                apiKey = key,
                baseUrl = prefs[kBaseUrl(id)]?.takeIf { it.isNotBlank() } ?: id.defaultBaseUrl,
                modelChat = modelOf(prefs, id, ModelRole.CHAT),
                modelSummary = modelOf(prefs, id, ModelRole.SUMMARY),
                modelDecision = modelOf(prefs, id, ModelRole.DECISION),
            )
        }
    }

    // ------------------------------------------------------------------
    // §6 saglayici ayarlari
    // ------------------------------------------------------------------

    suspend fun setProviderEnabled(id: ProviderId, enabled: Boolean) = withContext(io) {
        store.edit { it[kEnabled(id)] = enabled }
    }

    suspend fun setProviderApiKey(id: ProviderId, value: String) = withContext(io) {
        store.edit { prefs ->
            val trimmed = value.trim()
            if (trimmed.isEmpty()) prefs.remove(kApiKey(id))
            else prefs[kApiKey(id)] = crypto.encrypt(trimmed)
        }
    }

    suspend fun setProviderBaseUrl(id: ProviderId, value: String) = withContext(io) {
        store.edit { it[kBaseUrl(id)] = value.trim().ifBlank { id.defaultBaseUrl } }
    }

    suspend fun setProviderModel(id: ProviderId, role: ModelRole, value: String) = withContext(io) {
        store.edit { it[kModel(id, role)] = value.trim().ifBlank { id.defaultModel(role) } }
    }

    /** Siralamayi kaydeder; bilinmeyen/tanimsiz saglayicilar sona eklenir. */
    suspend fun setProviderOrder(order: List<ProviderId>) = withContext(io) {
        val normalized = (order.distinct() + ProviderId.entries.filterNot { it in order })
        store.edit { it[KEY_PROVIDER_ORDER] = normalized.joinToString(",") { p -> p.name } }
    }

    // ------------------------------------------------------------------
    // Diger ayarlar
    // ------------------------------------------------------------------

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

    suspend fun setNightOwl(enabled: Boolean) = withContext(io) {
        store.edit { it[KEY_NIGHT_OWL] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) = withContext(io) {
        store.edit { it[KEY_HAPTICS] = enabled }
    }

    suspend fun setNotifPermissionAsked(asked: Boolean) = withContext(io) {
        store.edit { it[KEY_NOTIF_ASKED] = asked }
    }

    /** §5: arayuz dilini kaydeder; null = sistem. */
    suspend fun setLanguage(language: AppLanguage) = withContext(io) {
        store.edit { prefs ->
            val tag = language.tag
            if (tag == null) prefs.remove(KEY_LANGUAGE) else prefs[KEY_LANGUAGE] = tag
        }
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    private fun viewOf(prefs: Preferences, id: ProviderId): ProviderView = ProviderView(
        id = id,
        enabled = prefs[kEnabled(id)] ?: true,
        hasKey = prefs[kApiKey(id)]?.let { crypto.decrypt(it) }?.isNotBlank() == true,
        baseUrl = prefs[kBaseUrl(id)]?.takeIf { it.isNotBlank() } ?: id.defaultBaseUrl,
        modelChat = modelOf(prefs, id, ModelRole.CHAT),
        modelSummary = modelOf(prefs, id, ModelRole.SUMMARY),
        modelDecision = modelOf(prefs, id, ModelRole.DECISION),
    )

    private fun modelOf(prefs: Preferences, id: ProviderId, role: ModelRole): String =
        prefs[kModel(id, role)]?.takeIf { it.isNotBlank() } ?: id.defaultModel(role)

    /** Kayitli siralama; yeni eklenen saglayicilar sona eklenir. */
    private fun orderOf(prefs: Preferences): List<ProviderId> {
        val parsed = prefs[KEY_PROVIDER_ORDER].orEmpty()
            .split(',')
            .mapNotNull { ProviderId.fromNameOrNull(it) }
            .distinct()
        return parsed + ProviderId.entries.filterNot { it in parsed }
    }

    private fun keyName(id: ProviderId): String = id.name.lowercase()

    private fun kApiKey(id: ProviderId) = stringPreferencesKey("provider_${keyName(id)}_api_key_enc")

    private fun kBaseUrl(id: ProviderId) = stringPreferencesKey("provider_${keyName(id)}_base_url")

    private fun kEnabled(id: ProviderId) = booleanPreferencesKey("provider_${keyName(id)}_enabled")

    private fun kModel(id: ProviderId, role: ModelRole) =
        stringPreferencesKey("provider_${keyName(id)}_model_${role.name.lowercase()}")

    private companion object {
        val KEY_DND_START = intPreferencesKey("dnd_start_hour")
        val KEY_DND_END = intPreferencesKey("dnd_end_hour")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_NIGHT_OWL = booleanPreferencesKey("night_owl")
        val KEY_HAPTICS = stringPreferencesKey("haptics_enabled")
        val KEY_NOTIF_ASKED = booleanPreferencesKey("notif_permission_asked")
        val KEY_LANGUAGE = stringPreferencesKey("language_tag")
        val KEY_PROVIDER_ORDER = stringPreferencesKey("provider_order")
    }
}

/** Dosya adi `backup_rules.xml` ile ayni olmali: ayarlar yedeklenmez. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)
