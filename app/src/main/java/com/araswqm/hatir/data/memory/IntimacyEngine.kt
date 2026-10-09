package com.araswqm.hatir.data.memory

import com.araswqm.hatir.core.Tuning
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Gizli yakinlik (intimacy) puani.
 *
 * Puan = OZET_AGIRLIGI * (son pencerenin ham puan ortalamasi)
 *      + SES_AGIRLIGI * (hacim bileseni, 0–100)
 *
 * Ham puan ozetleyicinin urettigi 0–100 degeridir. Hacim bileseni, konusmanin
 * gercekten yogun olup olmadigini olcer; uzun ama mesafeli sohbetlerin yuksek
 * puan almamasi icin yalnizca %20 etkiler.
 *
 * **Puan hicbir normal ekranda gosterilmez.** Yalnizca gizli hata ayiklama
 * ekrani ve kendiliğinden mesaj kapisi (gate) bu sinifi kullanir.
 */
@Singleton
class IntimacyEngine @Inject constructor() {

    /** Ham gecmisi + hacmi agirlikli ortalamayla nihai puana cevirir. */
    fun compose(rawHistory: List<Int>, messagesInWindow: Int, streakDays: Int): Double {
        val window = rawHistory.takeLast(Tuning.INTIMACY_MOVING_AVERAGE_WINDOW)
        val rawAverage = if (window.isEmpty()) {
            0.0
        } else {
            window.sum().toDouble() / window.size
        }
        val volume = volumeComponent(messagesInWindow, streakDays)
        return Tuning.INTIMACY_SUMMARY_WEIGHT * rawAverage + Tuning.INTIMACY_VOLUME_WEIGHT * volume
    }

    /** Hacim: mesaj yogunlugu ve ardisik gun seridinin ortalamasi (0–100). */
    private fun volumeComponent(messages: Int, streakDays: Int): Double {
        val messageFraction = (messages.toDouble() / Tuning.INTIMACY_VOLUME_FULL_MESSAGES)
            .coerceIn(0.0, 1.0)
        val streakFraction = (streakDays.toDouble() / Tuning.INTIMACY_STREAK_FULL_DAYS)
            .coerceIn(0.0, 1.0)
        return 100.0 * ((messageFraction + streakFraction) / 2.0)
    }

    /** Konusma olmayan her gun icin puan dusulur; taban 0'dir. */
    fun effectiveScore(storedScore: Double, lastDecayAt: Long, now: Long): Double {
        if (lastDecayAt <= 0L || now <= lastDecayAt) return max(0.0, storedScore)
        val days = TimeUnit.MILLISECONDS.toDays(now - lastDecayAt).toDouble() +
            (TimeUnit.MILLISECONDS.toHours(now - lastDecayAt) % 24) / 24.0
        return max(0.0, storedScore - days * Tuning.INTIMACY_DAILY_DECAY)
    }

    /** Seviye adi; esikler [Tuning]'den gelir. Ust sinir harictir. */
    fun level(score: Double): String = when {
        score < Tuning.LEVEL_FAMILIAR_MIN -> "Yabancı"
        score < Tuning.LEVEL_FRIEND_MIN -> "Tanıdık"
        score < Tuning.LEVEL_CLOSE_MIN -> "Arkadaş"
        else -> "Yakın"
    }

    /** 0–100 araligina kirpilmis guvenli ham puan. */
    fun clampRaw(value: Double): Int =
        min(Tuning.INTIMACY_RAW_MAX, max(Tuning.INTIMACY_RAW_MIN, value)).toInt()
}
