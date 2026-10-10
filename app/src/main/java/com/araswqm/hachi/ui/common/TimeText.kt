package com.araswqm.hachi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.araswqm.hachi.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tarih/saat bicimlendirme yardimcilari.
 *
 * §5 (coklu dil) sonrasi kisa metinler (`now`, `12 min`) `strings.xml`'den
 * gelir; tarih desenleri ise cihazin **gecerli** diline gore bicimlenir.
 */
private val DAY_MONTH: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val FULL_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
private val CLOCK: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun localDate(epochMillis: Long) =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * Sohbet listesindeki "son mesaj" etiketi: `now`, `12 min`, `3h`, `5d`, `Oct 12`.
 * Composable'dir cunku kisa birimler yerellestirilmis kaynaktan gelir.
 */
@Composable
fun relativeTime(
    epochMillis: Long?,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    if (epochMillis == null) return ""
    val diff = nowMillis - epochMillis
    if (diff < 0L) return stringResource(R.string.time_now)

    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L

    return when {
        minutes < 1L -> stringResource(R.string.time_now)
        minutes < 60L -> stringResource(R.string.time_minutes, minutes)
        hours < 24L -> stringResource(R.string.time_hours, hours)
        days < 7L -> stringResource(R.string.time_days, days)
        else -> DAY_MONTH.format(localDate(epochMillis))
    }
}

/** Tam tarih: `12 Ekim 2026` (cihaz diline gore). */
fun fullDate(epochMillis: Long): String = FULL_DATE.format(localDate(epochMillis))

/** Saat: `21:47`. */
fun clockTime(epochMillis: Long): String =
    CLOCK.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
