package com.araswqm.hachi.ui.chats

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.araswqm.hachi.R
import com.araswqm.hachi.data.db.entity.CharacterEntity
import com.araswqm.hachi.ui.common.CharacterAvatar
import com.araswqm.hachi.ui.common.relativeTime
import com.araswqm.hachi.ui.common.rememberHaptics
import java.io.File

/**
 * Sohbetler listesi. Yakinlik gostergesi BILEREK yok; yalnizca okunmamis
 * mesaj rozeti, avatar ve son aktivite zamani gorunur.
 *
 * §4: ilk girişte bildirim izni BIR KEZ istenir (`AppSettings.notifPermissionAsked`
 * bayragi ile); bildirimler acikken pil optimizasyonu icin kapatilabilir bir
 * kart gosterilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    onOpenChat: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onEditCharacter: (String) -> Unit,
    onNewCharacter: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<CharacterEntity?>(null) }
    var batteryCardDismissed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptics = rememberHaptics()

    // §4: sistem izin penceresi. Sonuc ne olursa olsun "soruldu" isaretlenir;
    // kullanici reddederse bir daha rahatsiz edilmez.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { },
    )
    LaunchedEffect(Unit) {
        if (!settings.notifPermissionAsked) {
            viewModel.markNotifPermissionAsked()
            if (!context.hasNotificationPermission()) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val showBatteryCard = !batteryCardDismissed &&
        context.hasNotificationPermission() &&
        !context.isIgnoringBatteryOptimizations()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(R.drawable.hachi_home_logo),
                        contentDescription = stringResource(R.string.chats_title),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(26.dp),
                    )
                },
                actions = {
                    IconButton(onClick = { haptics.tap(); onOpenSettings() }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.nav_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { haptics.tap(); onNewCharacter() }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.chats_new_character),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showBatteryCard) {
                BatteryOptimizationCard(
                    onDismiss = { batteryCardDismissed = true },
                    onRequest = {
                        batteryCardDismissed = true
                        context.requestIgnoreBatteryOptimizations()
                    },
                )
            }

            if (characters.isEmpty()) {
                EmptyChats(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(characters, key = { it.id }) { character ->
                        CharacterRow(
                            character = character,
                            onClick = { haptics.tap(); onOpenChat(character.id) },
                            onEdit = { haptics.tap(); onEditCharacter(character.id) },
                            onDetails = { haptics.tap(); onOpenDetails(character.id) },
                            onDelete = { haptics.tap(); pendingDelete = character },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.chats_delete_character_title)) },
            text = { Text(stringResource(R.string.chats_delete_character_body, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCharacter(target.id)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * §4: pil optimizasyonu muafiyeti icin kapatilabilir M3 karti. Vandeville
 * islerin (kendiliğinden mesajlar) Doze tarafindan durdurulmamasi icin onerilir.
 */
@Composable
private fun BatteryOptimizationCard(
    onDismiss: () -> Unit,
    onRequest: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_battery_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_battery_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRequest) {
                    Text(stringResource(R.string.settings_battery_action))
                }
            }
        }
    }
}

@Composable
private fun CharacterRow(
    character: CharacterEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val filesDir = LocalContext.current.filesDir

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = {
                    if (character.unreadCount > 0) {
                        Badge {
                            Text(
                                text = if (character.unreadCount > 99) "99+"
                                else character.unreadCount.toString(),
                            )
                        }
                    }
                },
            ) {
                CharacterAvatar(
                    file = character.avatarPath?.let { File(filesDir, it) },
                    name = character.name,
                    size = 52.dp,
                    contentDescription = stringResource(R.string.cd_character_avatar),
                    cacheKey = character.updatedAt.toString(),
                )
            }

            Spacer(Modifier.padding(horizontal = 6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name.ifBlank { stringResource(R.string.editor_title_new) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = character.description.trim()
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val stamp = relativeTime(character.lastMessageAt)
            if (stamp.isNotEmpty()) {
                Text(
                    text = stamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_details)) },
                        onClick = {
                            menuOpen = false
                            onDetails()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChats(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chats_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chats_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ----------------------------------------------------------------------
// Izin yardimcilari
// ----------------------------------------------------------------------

/** POST_NOTIFICATIONS verilmis mi? (minSdk 33 → izin her zaman vardir.) */
private fun Context.hasNotificationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** Uygulama pil optimizasyonundan muaf mi? */
private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return power.isIgnoringBatteryOptimizations(packageName)
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
