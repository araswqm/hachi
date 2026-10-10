package com.araswqm.hachi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.araswqm.hachi.core.LocaleController
import com.araswqm.hachi.data.settings.SettingsRepository
import com.araswqm.hachi.notify.Notifications
import com.araswqm.hachi.ui.HachiRoot
import com.araswqm.hachi.ui.common.LocalHapticsEnabled
import com.araswqm.hachi.ui.settings.SettingsViewModel
import com.araswqm.hachi.ui.theme.HachiTheme
import com.araswqm.hachi.work.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Tek etkinlik (single-activity) mimarisi; tum ekranlar Compose ile cizilir.
 *
 * Android 16 uyumu: [enableEdgeToEdge] ile sistem cubuklarinin altina tasiyoruz,
 * ekranlar `WindowInsets` ile kendi dolgusunu yonetiyor. Geri tusu,
 * `android:enableOnBackInvokedCallback="true"` sayesinde predictive back
 * API'sini kullanir; ekran ici geri davranisi `BackHandler` ile verilir.
 *
 * **Bildirim yonlendirmesi:** Bir bildirime dokunuldugunda niyet
 * [Notifications.EXTRA_CHARACTER_ID] tasir. Deger [pendingChat] akisina birakilir;
 * [HachiRoot] grafik hazir olduktan sonra ilgili sohbeti acar ve
 * `onPendingChatHandled` ile tuketir. `singleTop` + [onNewIntent] sayesinde
 * etkinlik zaten acikken gelen bildirim de yonlendirilir.
 *
 * **Kapanis ozetlemesi:** Uygulama arka plana gecerken birikmis mesajlar
 * [WorkScheduler.scheduleSummarize] ile kalici hafizaya yazilir.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var workScheduler: WorkScheduler

    @Inject lateinit var settingsRepository: SettingsRepository

    /** Bildirimden gelen karakter kimligi; [HachiRoot] tukettiğinde null'lanir. */
    private val pendingChat = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // §5: kayitli arayuz dili tercihini framework'e uygula. Ayni deger icin
        // cagri etkisizdir (aktivite yeniden kurulmaz); yalnizca gercekten farkli
        // oldugunda (or. yedekten yeni kurulum) bir kez yeniden kurulur.
        lifecycleScope.launch {
            LocaleController.apply(this@MainActivity, settingsRepository.currentSettings().languageTag)
        }
        enableEdgeToEdge()
        pendingChat.value = intent.characterIdExtra()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val theme by settingsViewModel.themeState.collectAsStateWithLifecycle()
            val pendingChatId by pendingChat.collectAsStateWithLifecycle()

            HachiTheme(
                darkTheme = theme.darkTheme,
                dynamicColor = theme.dynamicColor,
                nightOwl = theme.nightOwl,
            ) {
                // §7.2: dokunsal geri bildirim anahtari tum agaca buradan iner;
                // ekranlar `rememberHaptics()` ile okur.
                CompositionLocalProvider(LocalHapticsEnabled provides theme.hapticsEnabled) {
                    HachiRoot(
                        pendingChatId = pendingChatId,
                        onPendingChatHandled = { pendingChat.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChat.value = intent.characterIdExtra()
    }

    override fun onStop() {
        // Birikmis mesajlari (esik beklemeden) ozetle; ag yoksa is bekler.
        workScheduler.scheduleSummarize(force = true)
        super.onStop()
    }

    /**
     * Bildirim niyetinden karakter kimligini okur. [Intent.getIntExtra] gibi
     * varsayilan bir deger yoktur; anahtar yoksa null doner.
     */
    private fun Intent?.characterIdExtra(): String? =
        this?.getStringExtra(Notifications.EXTRA_CHARACTER_ID)?.takeIf { it.isNotBlank() }
}
