package com.araswqm.hatir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.araswqm.hatir.ui.HatirRoot
import com.araswqm.hatir.ui.settings.SettingsViewModel
import com.araswqm.hatir.ui.theme.HatirTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tek etkinlik (single-activity) mimarisi; tum ekranlar Compose ile cizilir.
 *
 * Android 16 uyumu: [enableEdgeToEdge] ile sistem cubuklarinin altina tasiyoruz,
 * ekranlar `WindowInsets` ile kendi dolgusunu yonetiyor. Geri tusu,
 * `android:enableOnBackInvokedCallback="true"` sayesinde predictive back
 * API'sini kullanir; ekran ici geri davranisi `BackHandler` ile verilir.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val theme by settingsViewModel.themeState.collectAsStateWithLifecycle()

            HatirTheme(
                darkTheme = theme.darkTheme,
                dynamicColor = theme.dynamicColor,
            ) {
                HatirRoot()
            }
        }
    }
}
