package com.araswqm.hachi.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.araswqm.hachi.R
import com.araswqm.hachi.data.ai.Placeholders
import com.araswqm.hachi.data.db.entity.ChatEntity
import com.araswqm.hachi.ui.common.CharacterAvatar
import com.araswqm.hachi.ui.common.MarkdownText
import com.araswqm.hachi.ui.common.relativeTime
import com.araswqm.hachi.ui.common.rememberHaptics
import java.io.File
import java.util.Date
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
    onOpenDetails: (characterId: String, chatId: String?) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val character by viewModel.character.collectAsStateWithLifecycle()
    val persona by viewModel.persona.collectAsStateWithLifecycle()
    // Aktif sohbet kimligi (§8): sohbet degistirici alt sayfada secili satiri
    // isaretlemek icin. `StateFlow<String?>` — lifecycle-guvenli toplanir.
    val chatIdValue by viewModel.chatId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    // §7.2: ayar anahtarini izleyen dokunsal geri bildirim.
    val haptics = rememberHaptics()

    var chatSheetOpen by remember { mutableStateOf(false) }
    var assistantOpen by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }

    val characterName = character?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.debug_deleted_character)

    // §8.2: balon kenarlarindaki avatarlar. Karakter solda, persona sagda;
    // ikisi de yuvarlatilmis kare ([BUBBLE_AVATAR_SHAPE]).
    val characterAvatar = AvatarData(
        file = character?.avatarPath?.let { File(context.filesDir, it) },
        name = characterName,
        cacheKey = character?.updatedAt?.toString(),
    )
    val personaAvatar = AvatarData(
        file = persona?.avatarPath?.let { File(context.filesDir, it) },
        name = persona?.name?.takeIf { it.isNotBlank() } ?: Placeholders.DEFAULT_USER_NAME,
        cacheKey = persona?.updatedAt?.toString(),
    )

    // Hata: snackbar + (anahtar yoksa) Ayarlar kisayolu.
    LaunchedEffect(uiState.error?.id) {
        val error = uiState.error ?: return@LaunchedEffect
        val base = when {
            // Yerellestirilebilir hata: metin kaynaktan cozulur. Parametreli
            // kaynaklarda (or. "X yanit vermedi, Y'ye gecildi") argumanlar da
            // kaynaktan cozulur ki diller arasi sira farki sorun cikarmasin.
            error.resId != null -> if (error.resArgResIds.isEmpty()) {
                context.getString(error.resId)
            } else {
                context.getString(
                    error.resId,
                    *error.resArgResIds.map { context.getString(it) }.toTypedArray(),
                )
            }
            error.needsApiKey -> context.getString(R.string.chat_error_no_api_key)
            else -> context.getString(R.string.chat_error_generic)
        }
        val detail = error.message.trim()
        val text = if (error.resId == null && !error.needsApiKey && detail.isNotBlank()) {
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

    // §7.2: akis sirasinda cok hafif dokunsal tik. Esikler `Haptics.tick`
    // icinde uygulanir (parca uzunlugu + iki tik arasi sure).
    LaunchedEffect(uiState.streamingText) {
        if (uiState.streamingText.isNotBlank()) haptics.tick(uiState.streamingText)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            haptics.tap()
                            onOpenDetails(viewModel.characterId, chatIdValue)
                        },
                    ) {
                        CharacterAvatar(
                            file = characterAvatar.file,
                            name = characterAvatar.name,
                            size = 32.dp,
                            contentDescription = stringResource(R.string.cd_character_avatar),
                            cacheKey = characterAvatar.cacheKey,
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
                    IconButton(onClick = { haptics.tap(); onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { haptics.tap(); chatSheetOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chat_switch),
                            contentDescription = stringResource(R.string.chats_switch_cd),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (chatSheetOpen) {
            ChatSwitcherSheet(
                chats = uiState.chats,
                currentChatId = chatIdValue,
                onSelect = { chatSheetOpen = false; viewModel.switchChat(it) },
                onCreate = { chatSheetOpen = false; viewModel.createChat() },
                onDismiss = { chatSheetOpen = false },
            )
        }
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
                                avatar = if (message.fromUser) personaAvatar else characterAvatar,
                                showRegenerate = uiState.canRegenerate &&
                                    message.id == uiState.messages.lastOrNull()?.id,
                                onLongPress = { haptics.tap(); actionTarget = message },
                                onRegenerate = viewModel::regenerate,
                                onVariant = { index -> viewModel.setVariant(message.id, index) },
                            )
                        }
                        if (uiState.isGenerating) {
                            item(key = STREAMING_KEY) {
                                StreamingBubble(
                                    text = uiState.streamingText,
                                    waitingLabel = stringResource(R.string.chat_typing, characterName),
                                    avatar = characterAvatar,
                                )
                            }
                        }
                    }
                }
            }

            Composer(
                value = uiState.input,
                onValueChange = viewModel::onInputChange,
                isGenerating = uiState.isGenerating,
                assistantBusy = uiState.assistantBusy,
                canUseAssistant = uiState.canUseAssistant,
                onAssistant = { assistantOpen = true },
                onSend = viewModel::send,
                onStop = viewModel::stop,
            )
        }
    }

    // §7.1 Astroid: taslak asistani menusu (uygulamanin ilk alt sayfasi).
    if (assistantOpen) {
        AssistantSheet(
            busy = uiState.assistantBusy,
            hasDraft = uiState.input.isNotBlank(),
            canUse = uiState.canUseAssistant,
            onEnhance = {
                assistantOpen = false
                viewModel.enhanceDraft()
            },
            onWrite = {
                assistantOpen = false
                viewModel.writeForMe()
            },
            onDismiss = { assistantOpen = false },
        )
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

/** Sohbet balonundaki kucuk avatarlarin olcusu ve boslugu (§8.2). */
private val AVATAR_SIZE = 30.dp
private val AVATAR_SPACING = 6.dp

/**
 * Balon kenarindaki avatarlar (§8.2): karakter de persona da hafif
 * yuvarlatilmis kare. Yalnizca ust bar avatari daire kalir.
 */
private val BUBBLE_AVATAR_SHAPE = RoundedCornerShape(10.dp)

/**
 * Balondaki avatar icin gereken veriler; karakter ve persona avatarlari ayni
 * bilesenle ([CharacterAvatar]) cizilir, yalnizca sekil ve adlar degisir.
 */
private data class AvatarData(
    val file: File?,
    val name: String,
    val cacheKey: String?,
)

/** Balonun gradyaninin taban rengi yuzeye ne kadar yaklassin (§8.3). */
private const val BUBBLE_GRADIENT_SHIFT = 0.12f

@Composable
private fun MessageAvatar(avatar: AvatarData, shape: Shape) {
    CharacterAvatar(
        file = avatar.file,
        name = avatar.name,
        size = AVATAR_SIZE,
        cacheKey = avatar.cacheKey,
        shape = shape,
    )
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    avatar: AvatarData,
    showRegenerate: Boolean,
    onLongPress: () -> Unit,
    onRegenerate: () -> Unit,
    onVariant: (Int) -> Unit,
) {
    val fromUser = message.fromUser
    val shape = if (fromUser) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }
    val container = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        fromUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        fromUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            // §8.2: karakter avatari balonun SOLUNDA (yuvarlatilmis kare). Avatar
            // her zaman balonun alt kenarina hizalanir; varyant/yeniden uret
            // kontrolleri ayri satirda oldugu icin bunu asagi cekmez.
            if (!fromUser) {
                MessageAvatar(avatar = avatar, shape = BUBBLE_AVATAR_SHAPE)
                Spacer(Modifier.width(AVATAR_SPACING))
            }

            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
            ) {
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
            }

            // §8.2: persona avatarı balonun SAGINDA (yuvarlatilmis kare).
            if (fromUser) {
                Spacer(Modifier.width(AVATAR_SPACING))
                MessageAvatar(avatar = avatar, shape = BUBBLE_AVATAR_SHAPE)
            }
        }

        // Kontroller balonun ALTINDA, kendi satirinda. Boylece avatar (balonun
        // yanindaki) balonun alt kenarinda kalir, kontrolun dibine inmez.
        if (message.hasVariants || showRegenerate) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (fromUser) 0.dp else AVATAR_SIZE + AVATAR_SPACING,
                    ),
                horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VariantSwitcher(
                    index = message.variantIndex,
                    count = message.variantCount,
                    onSelect = onVariant,
                    onRegenerate = if (!fromUser && showRegenerate) onRegenerate else null,
                    alignEnd = fromUser,
                )
            }
        }
    }
}

@Composable
private fun Bubble(
    message: ChatMessage,
    container: Color,
    content: Color,
    shape: Shape,
    onLongPress: () -> Unit,
) {
    val currentLongPress by rememberUpdatedState(onLongPress)

    Column(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .shadow(elevation = 1.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        container,
                        lerp(container, MaterialTheme.colorScheme.surface, BUBBLE_GRADIENT_SHIFT),
                    ),
                ),
            )
            .pointerInputLongPress(message.id) { currentLongPress() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        MarkdownText(
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
                    text = stringResource(R.string.chat_edited),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f),
                )
            }
            if (message.hasVariants) {
                Text(
                    text = stringResource(
                        R.string.chat_variant_position,
                        message.variantIndex + 1,
                        message.variantCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String, waitingLabel: String, avatar: AvatarData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        MessageAvatar(avatar = avatar, shape = BUBBLE_AVATAR_SHAPE)
        Spacer(Modifier.width(AVATAR_SPACING))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 300.dp)
                .shadow(
                    elevation = 1.dp,
                    shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                    clip = false,
                )
                .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondaryContainer,
                            lerp(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.surface,
                                BUBBLE_GRADIENT_SHIFT,
                            ),
                        ),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (text.isBlank()) {
                Text(
                    text = waitingLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    MarkdownText(
                        text = text,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "▍",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun VariantSwitcher(
    index: Int,
    count: Int,
    onSelect: (Int) -> Unit,
    onRegenerate: (() -> Unit)?,
    alignEnd: Boolean,
) {
    val haptics = rememberHaptics()
    Row(
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count > 1) {
            IconButton(onClick = { haptics.tap(); onSelect(index - 1) }, enabled = index > 0) {
                Text("‹", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "${index + 1}/$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = { haptics.tap(); onSelect(index + 1) },
                enabled = index < count - 1,
            ) {
                Text("›", style = MaterialTheme.typography.titleMedium)
            }
        }
        // Sinirsiz yeniden uretme: her basista yeni bir varyant eklenir.
        if (onRegenerate != null) {
            IconButton(onClick = { haptics.tap(); onRegenerate() }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.chat_regenerate),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
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
    assistantBusy: Boolean,
    canUseAssistant: Boolean,
    onAssistant: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val haptics = rememberHaptics()
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // §7.1: Astroid taslak asistani — giris kutusunun SOLUNDA.
            if (assistantBusy) {
                Box(
                    modifier = Modifier.size(AVATAR_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                IconButton(onClick = { haptics.tap(); onAssistant() }, enabled = canUseAssistant) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sparkle),
                        contentDescription = stringResource(R.string.cd_assistant),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

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
                onClick = {
                    haptics.tap()
                    if (isGenerating) onStop() else onSend()
                },
                enabled = isGenerating || value.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp),
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

/**
 * §7.1: taslak asistani alt sayfasi. Yaklasik olarak yarim yukseklikte acilir;
 * iki islev sunar — eldeki taslagi zenginlestirme ve son mesaja uygun yanit
 * yazma. Taslak bosken "gelistir" devre disidir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantSheet(
    busy: Boolean,
    hasDraft: Boolean,
    canUse: Boolean,
    onEnhance: () -> Unit,
    onWrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.assistant_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            AssistantOption(
                title = stringResource(R.string.assistant_enhance),
                description = stringResource(
                    if (hasDraft) R.string.assistant_enhance_desc
                    else R.string.assistant_empty_draft,
                ),
                enabled = canUse && hasDraft,
                onClick = onEnhance,
            )

            AssistantOption(
                title = stringResource(R.string.assistant_write),
                description = stringResource(R.string.assistant_write_desc),
                enabled = canUse,
                onClick = onWrite,
            )
        }
    }
}

@Composable
private fun AssistantOption(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        supportingContent = {
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_sparkle),
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = { haptics.tap(); onClick() }),
    )
}

/**
 * §8: ayni karakterin birden fazla sohbeti olabilir; bu alt sayfa aralarinda
 * gecisi saglar. Satirlar cagrildigi sirada gosterilir; secili sohbet tik ile
 * isaretlenir. Basligi bos olan sohbetlerde son (ya da olusturulma) tarihi
 * baslik yerine kullanilir — boylece "Yeni sohbet" satirlari ayirt edilebilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSwitcherSheet(
    chats: List<ChatEntity>,
    currentChatId: String?,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    // Orta uzunlukta tarih formati cihazin gecerli diline gore cozulur.
    val dateFormat = remember(context) { DateFormat.getMediumDateFormat(context) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_switch_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            // Yeni sohbet: her zaman en ustte, tek dokunusla olusturulur.
            ListItem(
                headlineContent = { Text(stringResource(R.string.chat_switch_new)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier.clickable { haptics.tap(); onCreate() },
            )

            HorizontalDivider()

            LazyColumn {
                items(chats, key = { it.id }) { chat ->
                    val stamp = chat.lastMessageAt ?: chat.createdAt
                    val label = chat.title.takeIf { it.isNotBlank() }
                        ?: dateFormat.format(Date(stamp))
                    ListItem(
                        headlineContent = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            val relative = relativeTime(stamp)
                            if (relative.isNotEmpty()) {
                                Text(
                                    text = relative,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        trailingContent = {
                            if (chat.id == currentChatId) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.chats_switch_cd),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.clickable { haptics.tap(); onSelect(chat.id) },
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
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_actions_title)) },
        text = {
            Column {
                TextButton(
                    onClick = { haptics.tap(); onCopy() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_copy))
                }
                if (message.fromUser) {
                    TextButton(
                        onClick = { haptics.tap(); onEdit() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.chat_edit_message))
                    }
                }
                if (canRegenerate) {
                    TextButton(
                        onClick = { haptics.tap(); onRegenerate() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.chat_regenerate))
                    }
                }
                HorizontalDivider()
                TextButton(
                    onClick = { haptics.tap(); onDelete() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
    manager.setPrimaryClip(ClipData.newPlainText("hachi", text))
}
