package com.araswqm.hatir.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.araswqm.hatir.ui.chat.ChatScreen
import com.araswqm.hatir.ui.chats.ChatsListScreen
import com.araswqm.hatir.ui.debug.DebugScreen
import com.araswqm.hatir.ui.detail.CharacterDetailScreen
import com.araswqm.hatir.ui.editor.CharacterEditorScreen
import com.araswqm.hatir.ui.settings.SettingsScreen

/**
 * Tum ekranlari birbirine baglayan gezinme grafigi.
 *
 * [pendingChatId] bildirimden gelir: kullanici bir bildirime dokundugunda
 * MainActivity niyeti okur ve buraya birakir. Grafik hazir olduktan sonra
 * (LaunchedEffect) ilgili sohbete gidilir ve [onPendingChatHandled] ile
 * niyet tuketilir; aksi halde her yeniden kompozisyonda ayni sohbet acilirdi.
 */
@Composable
fun HatirRoot(
    pendingChatId: String? = null,
    onPendingChatHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(pendingChatId) {
        val id = pendingChatId ?: return@LaunchedEffect
        navController.navigate(Routes.chat(id)) { launchSingleTop = true }
        onPendingChatHandled()
    }

    NavHost(navController = navController, startDestination = Routes.CHATS) {

        composable(Routes.CHATS) {
            ChatsListScreen(
                onOpenChat = { navController.navigate(Routes.chat(it)) },
                onOpenDetails = { navController.navigate(Routes.detail(it)) },
                onEditCharacter = { navController.navigate(Routes.editor(it)) },
                onNewCharacter = { navController.navigate(Routes.editor()) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDebug = { navController.navigate(Routes.DEBUG) },
            )
        }

        composable(Routes.DEBUG) {
            DebugScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CHAT_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_CHARACTER_ID) { type = NavType.StringType },
            ),
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onOpenDetails = { navController.navigate(Routes.detail(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.EDITOR_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_CHARACTER_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            CharacterEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(Routes.detail(id)) {
                        popUpTo(Routes.CHATS)
                    }
                },
            )
        }

        composable(
            route = Routes.DETAIL_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_CHARACTER_ID) { type = NavType.StringType },
            ),
        ) {
            CharacterDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.editor(it)) },
                onDeleted = {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.CHATS) { inclusive = true }
                    }
                },
            )
        }
    }
}
