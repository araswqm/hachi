package com.araswqm.hachi.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tarih/saat bicimlendirme yardimcilari.
 *
 * Uygulama yalnizca Turkce oldugu icin buradaki kisa metinler `strings.xml`
 * yerine dogrudan kodda tutuluyor; sayi + birim birlesimi ceviri kaynagi
 * gerektirmiyor (Turkce'de cogul eki yok).
 */
private val TURKISH: Locale = Locale.forLanguageTag("tr")
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", TURKISH)
private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", TURKISH)
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", TURKISH)

private fun localDate(epochMillis: Long) =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

/** Sohbet listesindeki "son mesaj" etiketi: `şimdi`, `12 dk`, `3 sa`, `dün`, `5 gün`, `12 Eki`. */
fun relativeTime(
    epochMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    if (epochMillis == null) return ""
    val diff = nowMillis - epochMillis
    if (diff < 0L) return "şimdi"

    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L

    return when {
        minutes < 1L -> "şimdi"
        minutes < 60L -> "$minutes dk"
        hours < 24L -> "$hours sa"
        days < 7L -> "$days gün"
        else -> DAY_MONTH.format(localDate(epochMillis))
    }
}

/** Tam tarih: `12 Ekim 2026`. */
fun fullDate(epochMillis: Long): String = FULL_DATE.format(localDate(epochMillis))

/** Saat: `21:47`. */
fun clockTime(epochMillis: Long): String =
    CLOCK.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
