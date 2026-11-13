package com.auralis.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.auralis.player.settings.AppSettings
import com.auralis.player.settings.ThemeMode

@Composable
fun AuralisTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val isDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val accent = settings.accentColor
    // Custom accents are adjusted per theme so buttons, switches, sliders and active icons stay
    // visible on both the light and the dark backgrounds; presets that already read well are untouched.
    val backgrounds = if (isDark) {
        listOf(DarkBackground, DarkSurface, DarkSurfaceVariant)
    } else {
        listOf(LightBackground, LightSurface, LightSurfaceVariant)
    }
    val primary = AccentPalette.readablePrimary(accent, backgrounds)
    val base = primary.toArgb().toLong() and 0xFFFFFFFFL
    val primaryContainer = AccentPalette.primaryContainer(base, isDark)
    val secondary = AccentPalette.secondary(base, isDark)
    val onPrimary = AccentPalette.onColorFor(primary)
    val onPrimaryContainer = AccentPalette.onColorFor(primaryContainer)
    val onSecondary = AccentPalette.onColorFor(secondary)

    val context = LocalContext.current
    val colorScheme = if (settings.dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = DarkOutline,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = LightOutline,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuralisTypography,
        shapes = AuralisShapes,
        content = content,
    )
}
