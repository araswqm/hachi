package com.araswqm.hatir.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.araswqm.hatir.R
import com.araswqm.hatir.data.db.entity.CharacterEntity
import com.araswqm.hatir.data.db.entity.FollowupEntity
import com.araswqm.hatir.ui.common.CharacterAvatar
import com.araswqm.hatir.ui.common.MarkdownText
import com.araswqm.hatir.ui.common.fullDate
import com.araswqm.hatir.ui.common.shareFile
import java.io.File

private const val TAB_MEMORY = 0
private const val TAB_FOLLOWUPS = 1
private const val TAB_CARD = 2

/**
 * Karakter detay ekrani. Yakinlik gostergesi BILEREK yoktur; yalnizca hafiza,
 * takip konulari, kart bilgileri ve kendiliğinden mesaj anahtari gorunur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: CharacterDetailViewModel = hiltViewModel(),
) {
    val character by viewModel.character.collectAsStateWithLifecycle()
    val followups by viewModel.followups.collectAsStateWithLifecycle()
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val loadingMemory by viewModel.loadingMemory.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exported by viewModel.exported.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val filesDir = context.filesDir
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(TAB_MEMORY) }
    var confirmDelete by remember { mutableStateOf(false) }

    val messageText = message?.let { stringResource(it.resId) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(exported) {
        exported?.let {
            shareFile(context, it.file, it.mimeType)
            viewModel.consumeExport()
        }
    }

    LaunchedEffect(deleted) { if (deleted) onDeleted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = character?.name?.ifBlank { null }
                            ?: stringResource(R.string.detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    character?.let { current ->
                        IconButton(onClick = { onEdit(current.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                            )
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val current = character
        if (current == null) {
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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(current, filesDir)

            ProactiveRow(
                enabled = current.proactiveEnabled,
                onToggle = viewModel::setProactiveEnabled,
            )

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == TAB_MEMORY,
                    onClick = { tab = TAB_MEMORY },
                    label = { Text(stringResource(R.string.detail_tab_memory)) },
                )
                FilterChip(
                    selected = tab == TAB_FOLLOWUPS,
                    onClick = { tab = TAB_FOLLOWUPS },
                    label = { Text(stringResource(R.string.detail_tab_followups)) },
                )
                FilterChip(
                    selected = tab == TAB_CARD,
                    onClick = { tab = TAB_CARD },
                    label = { Text(stringResource(R.string.detail_tab_card)) },
                )
            }

            when (tab) {
                TAB_MEMORY -> MemoryTab(
                    memory = memory,
                    loading = loadingMemory,
                    onSave = viewModel::saveMemory,
                )

                TAB_FOLLOWUPS -> FollowupsTab(
                    followups = followups,
                    onDelete = viewModel::deleteFollowup,
                )

                else -> CardTab(character = current, onExport = viewModel::exportCard)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        val name = character?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.chats_delete_character_title)) },
            text = { Text(stringResource(R.string.chats_delete_character_body, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteCharacter()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun Header(character: CharacterEntity, filesDir: File) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CharacterAvatar(
            file = character.avatarPath?.let { File(filesDir, it) },
            name = character.name,
            size = 72.dp,
            contentDescription = stringResource(R.string.cd_character_avatar),
            cacheKey = character.updatedAt.toString(),
        )
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = character.name.ifBlank { stringResource(R.string.detail_title) },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(sourceLabel(character.sourceSpec)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.detail_created_at,
                    fullDate(character.createdAt),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun sourceLabel(sourceSpec: String): Int = when (sourceSpec) {
    CharacterEntity.SOURCE_V1 -> R.string.detail_source_v1
    CharacterEntity.SOURCE_V2 -> R.string.detail_source_v2
    CharacterEntity.SOURCE_V3 -> R.string.detail_source_v3
    else -> R.string.detail_source_manual
}

@Composable
private fun ProactiveRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.detail_proactive_toggle),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.detail_proactive_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun MemoryTab(memory: String, loading: Boolean, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.detail_memory_title),
            style = MaterialTheme.typography.titleMedium,
        )

        when {
            loading -> Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            memory.isBlank() -> Text(
                text = stringResource(R.string.detail_memory_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Card(modifier = Modifier.fillMaxWidth()) {
                MarkdownText(
                    text = memory,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        OutlinedButton(
            onClick = {
                draft = memory
                editing = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.detail_memory_edit))
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.detail_memory_edit)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 320.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.detail_memory_lock_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editing = false
                        onSave(draft)
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun FollowupsTab(followups: List<FollowupEntity>, onDelete: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.detail_followups_title),
            style = MaterialTheme.typography.titleMedium,
        )

        if (followups.isEmpty()) {
            Text(
                text = stringResource(R.string.detail_followups_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        followups.forEach { followup ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = followup.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val detail = when {
                            followup.done -> stringResource(R.string.detail_followups_done)
                            followup.dueAt != null -> stringResource(
                                R.string.detail_followups_due,
                                fullDate(followup.dueAt),
                            )

                            else -> ""
                        }
                        if (detail.isNotEmpty()) {
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onDelete(followup.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardTab(character: CharacterEntity, onExport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoBlock(stringResource(R.string.editor_field_description), character.description)
        InfoBlock(stringResource(R.string.editor_field_personality), character.personality)
        InfoBlock(stringResource(R.string.editor_field_scenario), character.scenario)
        InfoBlock(stringResource(R.string.editor_field_first_message), character.firstMessage)
        InfoBlock(stringResource(R.string.editor_field_example_dialogs), character.exampleDialogs)

        OutlinedButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.editor_export_card))
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (value.isBlank()) {
            Text(
                text = stringResource(R.string.detail_card_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            MarkdownText(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
