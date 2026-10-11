package com.araswqm.hachi.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.araswqm.hachi.BuildConfig
import com.araswqm.hachi.R
import com.araswqm.hachi.data.ai.provider.ModelRole
import com.araswqm.hachi.data.settings.AppLanguage
import com.araswqm.hachi.data.settings.ProviderView
import com.araswqm.hachi.data.settings.ThemeMode

/**
 * Ayarlar: API anahtari / sunucu / modeller, persona, kendiliğinden mesaj
 * penceresi, gorunum.
 *
 * API anahtari hicbir zaman geri gosterilmez; alan yalnizca yazmak icindir ve
 * kayitli olup olmadigi "Kayıtlı" / "Ayarlanmadı" metniyle bildirilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPersonas: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val persona by viewModel.persona.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ----------------------------------------------------------
            // Saglayicilar (§6)
            // ----------------------------------------------------------
            SectionHeader(stringResource(R.string.settings_section_providers))

            Text(
                text = stringResource(R.string.settings_providers_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val orderedProviders = settings.providerOrder.mapNotNull { id ->
                settings.providers.firstOrNull { it.id == id }
            }
            orderedProviders.forEachIndexed { index, providerView ->
                ProviderCard(
                    view = providerView,
                    canMoveUp = index > 0,
                    canMoveDown = index < orderedProviders.lastIndex,
                    onEnabled = { viewModel.setProviderEnabled(providerView.id, it) },
                    onKey = { viewModel.setProviderApiKey(providerView.id, it) },
                    onBaseUrl = { viewModel.setProviderBaseUrl(providerView.id, it) },
                    onModel = { role, value ->
                        viewModel.setProviderModel(providerView.id, role, value)
                    },
                    onMoveUp = { viewModel.moveProvider(providerView.id, -1) },
                    onMoveDown = { viewModel.moveProvider(providerView.id, +1) },
                )
            }

            SectionDivider()

            // ----------------------------------------------------------
            // Persona
            // ----------------------------------------------------------
            SectionHeader(stringResource(R.string.settings_section_persona))

            val personaName = persona?.name.orEmpty()
            val personaAbout = persona?.about.orEmpty()

            CommitTextField(
                label = stringResource(R.string.settings_persona_name),
                value = personaName,
                onCommit = { viewModel.savePersona(it, personaAbout) },
            )

            CommitTextField(
                label = stringResource(R.string.settings_persona_about),
                value = personaAbout,
                singleLine = false,
                minLines = 3,
                onCommit = { viewModel.savePersona(personaName, it) },
            )

            TextButton(onClick = onOpenPersonas) {
                Text(stringResource(R.string.settings_personas_manage))
            }

            SectionDivider()

            // ----------------------------------------------------------
            // Kendiliğinden mesajlar
            // ----------------------------------------------------------
            SectionHeader(stringResource(R.string.settings_section_proactive))

            Text(
                text = stringResource(R.string.settings_dnd_title),
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HourField(
                    label = stringResource(R.string.settings_dnd_start),
                    value = settings.dndStartHour,
                    onCommit = { viewModel.setDnd(it, settings.dndEndHour) },
                    modifier = Modifier.weight(1f),
                )
                HourField(
                    label = stringResource(R.string.settings_dnd_end),
                    value = settings.dndEndHour,
                    onCommit = { viewModel.setDnd(settings.dndStartHour, it) },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(R.string.settings_battery_title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_battery_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { context.requestIgnoreBatteryOptimizations() }) {
                Text(stringResource(R.string.settings_battery_action))
            }

            SectionDivider()

            // ----------------------------------------------------------
            // Dil (§5)
            // ----------------------------------------------------------
            SectionHeader(stringResource(R.string.settings_section_language))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val currentLanguage = AppLanguage.fromTag(settings.languageTag)
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = currentLanguage == language,
                        onClick = { viewModel.setLanguage(language) },
                        label = { Text(language.displayName()) },
                    )
                }
            }

            SectionDivider()

            // ----------------------------------------------------------
            // Gorunum
            // ----------------------------------------------------------
            SectionHeader(stringResource(R.string.settings_section_appearance))

            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(stringResource(mode.labelRes())) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_dynamic_color),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            // Night Owl: baslik **her dilde** sabit "Night Owl"; yalnizca
            // aciklama yerellesir (Adim 3'te 5 dile cevrilecek).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Night Owl",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_night_owl_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.nightOwl,
                    onCheckedChange = viewModel::setNightOwl,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_haptics),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_haptics_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled,
                )
            }

            // ----------------------------------------------------------
            // Surum
            // ----------------------------------------------------------
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ----------------------------------------------------------------------
// Kucuk parcalar
// ----------------------------------------------------------------------

/**
 * §6: tek saglayici karti. Etkin degilken yalnizca baslik + anahtar gorunur;
 * etkinken sunucu ve model alanlari da acilir. Sira oklari zinciri belirler.
 */
@Composable
private fun ProviderCard(
    view: ProviderView,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEnabled: (Boolean) -> Unit,
    onKey: (String) -> Unit,
    onBaseUrl: (String) -> Unit,
    onModel: (ModelRole, String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(view.id.displayNameRes),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (!view.hasKey) {
                        Text(
                            text = stringResource(R.string.settings_provider_needs_key),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.settings_provider_move_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.settings_provider_move_down),
                    )
                }
                Switch(checked = view.enabled, onCheckedChange = onEnabled)
            }

            var keyInput by remember(view.id, view.hasKey) { mutableStateOf("") }
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text(stringResource(R.string.settings_provider_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    when {
                        keyInput.isNotBlank() -> IconButton(
                            onClick = {
                                onKey(keyInput)
                                keyInput = ""
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.action_save),
                            )
                        }

                        view.hasKey -> IconButton(onClick = { onKey("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                },
                supportingText = {
                    Text(
                        stringResource(
                            if (view.hasKey) R.string.settings_api_key_saved
                            else R.string.settings_api_key_none,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            CommitTextField(
                label = stringResource(R.string.settings_provider_base_url),
                value = view.baseUrl,
                onCommit = onBaseUrl,
            )
            CommitTextField(
                label = stringResource(R.string.settings_provider_model_chat),
                value = view.modelChat,
                onCommit = { onModel(ModelRole.CHAT, it) },
            )
            CommitTextField(
                label = stringResource(R.string.settings_provider_model_summary),
                value = view.modelSummary,
                onCommit = { onModel(ModelRole.SUMMARY, it) },
            )
            CommitTextField(
                label = stringResource(R.string.settings_provider_model_decision),
                value = view.modelDecision,
                onCommit = { onModel(ModelRole.DECISION, it) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
}

/**
 * Odak kaybedildiginde degeri disari bildiren metin alani.
 *
 * Her tus vurusunda DataStore'a yazmamak icin yerel durum tutulur; [value]
 * disaridan degistiginde (baska bir ekrandan guncelleme) alan kendini yeniler.
 */
@Composable
private fun CommitTextField(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var text by remember(value) { mutableStateOf(value) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (!focusState.isFocused && text != value) onCommit(text)
            },
    )
}

/** 0-23 arasi saat alani; gecersiz giris yok sayilir. */
@Composable
private fun HourField(
    label: String,
    value: Int,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    OutlinedTextField(
        value = text,
        onValueChange = { input -> text = input.filter { it.isDigit() }.take(2) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        modifier = modifier.onFocusChanged { focusState ->
            if (!focusState.isFocused) {
                val parsed = text.toIntOrNull()?.coerceIn(0, 23)
                if (parsed != null && parsed != value) onCommit(parsed) else text = value.toString()
            }
        },
    )
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

/**
 * §5: dil secici etiketi. "Sistem" yerellesir; diller **kendi adiyla** yazilir
 * (ceviri yapilmaz — kullanicinin kendi dilini bulmasi icin).
 */
@Composable
private fun AppLanguage.displayName(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
    AppLanguage.ENGLISH -> "English"
    AppLanguage.TURKISH -> "Türkçe"
    AppLanguage.SPANISH -> "Español"
    AppLanguage.PORTUGUESE -> "Português"
    AppLanguage.INDONESIAN -> "Bahasa Indonesia"
}

/**
 * Pil optimizasyonundan muafiyet istegi. Zamanlanmis islerin Doze tarafindan
 * tamamen durdurulmamasi icin gerekli; kullanici reddedebilir.
 */
@SuppressLint("BatteryLife")
private fun Context.requestIgnoreBatteryOptimizations() {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.fromParts("package", packageName, null))
    runCatching { startActivity(intent) }
}
