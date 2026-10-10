package com.araswqm.hachi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.araswqm.hachi.core.Tuning

/**
 * Dokunsal geri bildirim acik mi (§7.2). Deger `MainActivity`'de ayarlardan
 * okunup tum agaca verilir; varsayilan "acik" (ayar anahtarinin varsayilaniyla
 * ayni). Nadiren degistigi icin `static` varyant yeterli.
 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Hafif dokunsal geri bildirim uretir (§7.2).
 *
 * Akis sirasinda SSE deltalari cok sik geldigi icin iki esik uygulanir:
 * eklenen parca en az [Tuning.HAPTICS_MIN_CHARS] karakter olmali ve iki tik
 * arasinda en az [Tuning.HAPTICS_THROTTLE_MILLIS] ms gecmeli. Bu esikler
 * olmadan motor surekli titrer.
 *
 * `LocalHapticFeedback` **VIBRATE izni GEREKTIRMEZ**.
 */
class Haptics internal constructor(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {
    /** Son tikin zamani (ms) ve o an biriken akis metninin uzunlugu. */
    private var lastTickAt = 0L
    private var lastLength = 0

    /**
     * Akis icin: [accumulated] o ana kadar biriken yanit metnidir. Onceki
     * cagridan bu yana eklenen parca kisaysa ya da iki tik cok yakinsa sessiz
     * kalir. Metin sifirlanirsa (yeni uretim) kendiliginden toparlanir.
     */
    fun tick(accumulated: String) {
        val added = accumulated.length - lastLength
        lastLength = accumulated.length
        if (!enabled || added <= 0) return

        val fragment = accumulated.takeLast(added)
        if (fragment.count { !it.isWhitespace() } < Tuning.HAPTICS_MIN_CHARS) return

        val now = System.currentTimeMillis()
        if (now - lastTickAt < Tuning.HAPTICS_THROTTLE_MILLIS) return
        lastTickAt = now
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** Buton / uzun basma gibi tek seferlik eylemler icin belirgin tik. */
    fun tap() {
        if (!enabled) return
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/** Ekran omrune bagli, ayar anahtarini izleyen [Haptics] ornegi. */
@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}
