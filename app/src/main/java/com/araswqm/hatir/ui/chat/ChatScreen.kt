package com.araswqm.hatir.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.araswqm.hatir.R
import com.araswqm.hatir.ui.common.CharacterAvatar
import com.araswqm.hatir.ui.common.relativeTime
import java.io.File
import kotlinx.coroutines.launch

/**
 * Sohbet ekrani.
 *
 * Yakınlık puanı BILEREK gosterilmez (bkz. proje kurallari); yalnizca mesajlar,
 * yaziyor gostergesi ve hata mesajlari var. Kendiliğinden gelen mesajlar
 * "Kendiliğinden yazdı" rozetiyle isaretlenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val character by viewModel.character.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }

    val characterName = character?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.debug_deleted_character)

    // Hata: snackbar + (anahtar yoksa) Ayarlar kisayolu.
    LaunchedEffect(uiState.error?.id) {
        val error = uiState.error ?: return@LaunchedEffect
        val base = if (error.needsApiKey) {
            context.getString(R.string.chat_error_no_api_key)
        } else {
            context.getString(R.string.chat_error_generic)
        }
        val detail = error.message.trim()
        val text = if (!error.needsApiKey && detail.isNotBlank()) {
            "$base\n${context.getString(R.string.chat_error_detail, detail)}"
        } else {
            base
        }
        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = if (error.needsApiKey) {
                context.getString(R.string.chat_open_settings)
            } else {
                null
            },
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) onOpenSettings()
    }

    // Yeni mesaj / akis balonunda en alta kay.
    val scrollTarget = if (uiState.isGenerating) uiState.messages.size else uiState.messages.lastIndex
    LaunchedEffect(scrollTarget) {
        if (scrollTarget >= 0) runCatching { listState.animateScrollToItem(scrollTarget) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CharacterAvatar(
                            file = character?.avatarPath?.let { File(context.filesDir, it) },
                            name = characterName,
                            size = 32.dp,
                            contentDescription = stringResource(R.string.cd_character_avatar),
                            cacheKey = character?.updatedAt?.toString(),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = characterName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.cd_more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_details)) },
                                onClick = {
                                    menuOpen = false
                                    onOpenDetails(viewModel.characterId)
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.messages.isEmpty() && !uiState.isGenerating -> EmptyChat(characterName)

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            MessageRow(
                                message = message,
                                showRegenerate = uiState.canRegenerate &&
                                    message.id == uiState.messages.lastOrNull()?.id,
                                onLongPress = { actionTarget = message },
                                onRegenerate = viewModel::regenerate,
                                onVariant = { index -> viewModel.setVariant(message.id, index) },
                            )
                        }
                        if (uiState.isGenerating) {
                            item(key = STREAMING_KEY) {
                                StreamingBubble(
                                    text = uiState.streamingText,
                                    waitingLabel = stringResource(R.string.chat_typing, characterName),
                                )
                            }
                        }
                    }
                }
            }

            Composer(
                value = input,
                onValueChange = { input = it },
                isGenerating = uiState.isGenerating,
                onSend = {
                    val text = input
                    input = ""
                    viewModel.send(text)
                },
                onStop = viewModel::stop,
            )
        }
    }

    // --- Mesaj eylemleri ---
    actionTarget?.let { target ->
        MessageActionsDialog(
            message = target,
            canRegenerate = uiState.canRegenerate && target.id == uiState.messages.lastOrNull()?.id,
            onDismiss = { actionTarget = null },
            onCopy = {
                context.copyToClipboard(target.text)
                actionTarget = null
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.chat_copied)) }
            },
            onEdit = {
                actionTarget = null
                editTarget = target
            },
            onDelete = {
                actionTarget = null
                deleteTarget = target
            },
            onRegenerate = {
                actionTarget = null
                viewModel.regenerate()
            },
        )
    }

    editTarget?.let { target ->
        EditMessageDialog(
            initial = target.text,
            onDismiss = { editTarget = null },
            onConfirm = { text ->
                viewModel.editMessage(target.id, text)
                editTarget = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessage(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

// ----------------------------------------------------------------------
// Mesaj listesi
// ----------------------------------------------------------------------

private const val STREAMING_KEY = "streaming"

@Composable
private fun MessageRow(
    message: ChatMessage,
    showRegenerate: Boolean,
    onLongPress: () -> Unit,
    onRegenerate: () -> Unit,
    onVariant: (Int) -> Unit,
) {
    val alignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (message.fromUser) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }
    val container = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        message.fromUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        message.fromUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start) {
            if (message.isProactive) {
                Text(
                    text = stringResource(R.string.chat_proactive_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }

            Bubble(
                message = message,
                container = container,
                content = content,
                shape = shape,
                onLongPress = onLongPress,
            )

            if (message.hasVariants) {
                VariantSwitcher(
                    index = message.variantIndex,
                    count = message.variantCount,
                    onSelect = onVariant,
                    alignEnd = message.fromUser,
                )
            } else if (showRegenerate) {
                TextButton(onClick = onRegenerate) {
                    Text(stringResource(R.string.chat_regenerate))
                }
            }
        }
    }
}

@Composable
private fun Bubble(
    message: ChatMessage,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    shape: androidx.compose.ui.graphics.Shape,
    onLongPress: () -> Unit,
) {
    val currentLongPress by rememberUpdatedState(onLongPress)

    Column(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(shape)
            .background(container)
            .pointerInputLongPress(message.id) { currentLongPress() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = relativeTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.7f),
            )
            if (message.isEdited) {
                Text(
                    text = " · düzenlendi",
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f),
                )
            }
            if (message.hasVariants) {
                Text(
                    text = " · ${message.variantIndex + 1}/${message.variantCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String, waitingLabel: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = if (text.isBlank()) waitingLabel else "$text▍",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun VariantSwitcher(
    index: Int,
    count: Int,
    onSelect: (Int) -> Unit,
    alignEnd: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onSelect(index - 1) }, enabled = index > 0) {
            Text("‹", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = "${index + 1}/$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = { onSelect(index + 1) }, enabled = index < count - 1) {
            Text("›", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Uzun basma jesti; cagri her yeniden duzenlemede tazelenir. */
private fun Modifier.pointerInputLongPress(key: Any, onLongPress: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(key) {
            detectTapGestures(onLongPress = { onLongPress() })
        },
    )

// ----------------------------------------------------------------------
// Alt girdi alani
// ----------------------------------------------------------------------

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                maxLines = 6,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(8.dp))

            FilledIconButton(
                onClick = if (isGenerating) onStop else onSend,
                enabled = isGenerating || value.isNotBlank(),
            ) {
                if (isGenerating) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_stop),
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Bos durum + diyaloglar
// ----------------------------------------------------------------------

@Composable
private fun EmptyChat(characterName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chat_empty_state, characterName),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_input_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageActionsDialog(
    message: ChatMessage,
    canRegenerate: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_actions_title)) },
        text = {
            Column {
                TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_copy))
                }
                if (message.fromUser) {
                    TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.chat_edit_message))
                    }
                }
                if (canRegenerate) {
                    TextButton(onClick = onRegenerate, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.chat_regenerate))
                    }
                }
                HorizontalDivider()
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.chat_delete_message),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun EditMessageDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_edit_message)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun Context.copyToClipboard(text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("hatir", text))
}
