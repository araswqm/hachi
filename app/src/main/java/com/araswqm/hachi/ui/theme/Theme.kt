package com.araswqm.hachi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainer = LightSurfaceContainer,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainer = DarkSurfaceContainer,
)

/** Night Owl: saf siyah (`#000000`) yuzeyler; dinamik renk kullanilmaz. */
private val NightOwlColors = darkColorScheme(
    primary = NightOwlPrimary,
    onPrimary = NightOwlOnPrimary,
    primaryContainer = NightOwlPrimaryContainer,
    onPrimaryContainer = NightOwlOnPrimaryContainer,
    secondary = NightOwlSecondary,
    onSecondary = NightOwlOnSecondary,
    secondaryContainer = NightOwlSecondaryContainer,
    onSecondaryContainer = NightOwlOnSecondaryContainer,
    tertiary = NightOwlTertiary,
    onTertiary = NightOwlOnTertiary,
    tertiaryContainer = NightOwlTertiaryContainer,
    onTertiaryContainer = NightOwlOnTertiaryContainer,
    error = NightOwlError,
    onError = NightOwlOnError,
    errorContainer = NightOwlErrorContainer,
    onErrorContainer = NightOwlOnErrorContainer,
    background = NightOwlBackground,
    onBackground = NightOwlOnBackground,
    surface = NightOwlSurface,
    onSurface = NightOwlOnSurface,
    surfaceVariant = NightOwlSurfaceVariant,
    onSurfaceVariant = NightOwlOnSurfaceVariant,
    outline = NightOwlOutline,
    outlineVariant = NightOwlOutlineVariant,
    surfaceContainer = NightOwlSurfaceContainer,
)

/**
 * Uygulama temasi.
 *
 * @param darkTheme Koyu tema mi? Cagiran taraf, ayarlardaki tema tercihinden
 *   (Sistem / Açık / Koyu) cozer. Varsayilan sistem temasidir.
 * @param dynamicColor Dinamik renk (duvar kagidi renkleri). minSdk 33 oldugu icin
 *   her zaman desteklenir; yalnizca kullanici kapatirsa false olur.
 * @param nightOwl Night Owl modu. Acikken **her zaman** koyu + saf OLED siyahi
 *   [NightOwlColors] kullanilir; dinamik renk ve tema tercihi yok sayilir.
 */
@Composable
fun HachiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    nightOwl: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        nightOwl -> NightOwlColors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HachiTypography,
        content = content,
    )
}
