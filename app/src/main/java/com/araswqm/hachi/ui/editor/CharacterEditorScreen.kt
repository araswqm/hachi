package com.araswqm.hachi.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.araswqm.hachi.R
import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.ui.common.CharacterAvatar
import java.io.File

/**
 * Karakter olusturma/duzenleme ekrani. Kaydet'e basilmadan hicbir sey
 * veritabanina yazilmaz; ice aktarma da yalnizca formu doldurur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditorScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: CharacterEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingAvatar by viewModel.pendingAvatar.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val savedId by viewModel.savedId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val filesDir = LocalContext.current.filesDir

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::pickAvatarFromUri) }

    val cardPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFromUri) }

    val messageText = message?.let { stringResource(it.resId, *it.args.toTypedArray()) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(savedId) { savedId?.let(onSaved) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.editor_title_new
                            else R.string.editor_title_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = !state.loading) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.action_save),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AvatarSection(
                state = state,
                pendingAvatar = pendingAvatar,
                previewFile = state.avatarPath?.let { File(filesDir, it) },
                onPick = { avatarPicker.launch(arrayOf("image/*")) },
                onClear = viewModel::clearAvatar,
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.editor_field_name)) },
                isError = state.nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.nameError) {
                Text(
                    text = stringResource(R.string.editor_name_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text(stringResource(R.string.editor_field_description)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.personality,
                onValueChange = viewModel::setPersonality,
                label = { Text(stringResource(R.string.editor_field_personality)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.scenario,
                onValueChange = viewModel::setScenario,
                label = { Text(stringResource(R.string.editor_field_scenario)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.firstMessage,
                onValueChange = viewModel::setFirstMessage,
                label = { Text(stringResource(R.string.editor_field_first_message)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.exampleDialogs,
                onValueChange = viewModel::setExampleDialogs,
                label = { Text(stringResource(R.string.editor_field_example_dialogs)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            TemperatureField(state = state, onTemperature = viewModel::setTemperature)

            MaxTokensField(state = state, onMaxTokens = viewModel::setMaxTokens)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ImportSection(
                hint = stringResource(R.string.editor_import_hint),
                label = stringResource(R.string.editor_import_card),
                onImport = { cardPicker.launch(arrayOf("*/*")) },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AvatarSection(
    state: EditorState,
    pendingAvatar: ByteArray?,
    previewFile: File?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CharacterAvatar(
            file = previewFile,
            name = state.name.ifBlank { stringResource(R.string.editor_title_new) },
            size = 96.dp,
            bytes = pendingAvatar,
            contentDescription = stringResource(R.string.editor_avatar),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(R.string.editor_avatar),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onPick) {
                Text(stringResource(R.string.editor_avatar_pick))
            }
            if (pendingAvatar != null || previewFile != null) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.editor_avatar_remove))
                }
            }
        }
    }
}

@Composable
private fun TemperatureField(state: EditorState, onTemperature: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.editor_field_temperature),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "%.2f".format(state.temperature),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = state.temperature,
            onValueChange = onTemperature,
            valueRange = Tuning.MIN_TEMPERATURE..Tuning.MAX_TEMPERATURE,
        )
    }
}

@Composable
private fun MaxTokensField(state: EditorState, onMaxTokens: (Int) -> Unit) {
    // Metin, temel kayit degistiginde (ilk yukleme / kart ice aktarma) sifirlanir.
    var text by remember(state.base) { mutableStateOf(state.maxTokens.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(5)
            text = digits
            digits.toIntOrNull()?.let { value ->
                if (value in Tuning.MIN_MAX_TOKENS..Tuning.MAX_MAX_TOKENS) onMaxTokens(value)
            }
        },
        label = { Text(stringResource(R.string.editor_field_max_tokens)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ImportSection(hint: String, label: String, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label)
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
