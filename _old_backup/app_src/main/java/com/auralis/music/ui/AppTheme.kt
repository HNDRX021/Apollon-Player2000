package com.auralis.music.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD7FF4F),
    onPrimary = Color(0xFF13150A),
    background = Color(0xFF08090A),
    onBackground = Color(0xFFF5F5F2),
    surface = Color(0xFF111315),
    surfaceContainer = Color(0xFF17191B),
    onSurface = Color(0xFFF5F5F2),
    onSurfaceVariant = Color(0xFFA4A8A7)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3D5D00),
    onPrimary = Color.White,
    background = Color(0xFFF7F7F2),
    onBackground = Color(0xFF171814),
    surface = Color.White,
    surfaceContainer = Color(0xFFEFEFE9),
    onSurface = Color(0xFF171814),
    onSurfaceVariant = Color(0xFF5F625B)
)

@Immutable
data class AppUiTheme(
    val accent: Color,
    val animations: Boolean,
    val dark: Boolean
)

@Composable
fun AuralisTheme(
    darkTheme: Boolean,
    accent: Color,
    animations: Boolean,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkScheme else LightScheme
    val scheme = remember(base, accent) { base.copy(primary = accent) }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content
    )
}

fun accentFromHex(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFFD7FF4F))
