package com.araswqm.hatir.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.araswqm.hatir.ui.chats.ChatsListScreen
import com.araswqm.hatir.ui.detail.CharacterDetailScreen
import com.araswqm.hatir.ui.editor.CharacterEditorScreen
import com.araswqm.hatir.ui.settings.SettingsScreen

/** Tum ekranlari birbirine baglayan gezinme grafigi. */
@Composable
fun HatirRoot(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.CHATS) {

        composable(Routes.CHATS) {
            ChatsListScreen(
                onOpenCharacter = { navController.navigate(Routes.detail(it)) },
                onNewCharacter = { navController.navigate(Routes.editor()) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
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
