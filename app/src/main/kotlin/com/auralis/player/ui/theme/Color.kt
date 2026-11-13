package com.auralis.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// --- Dark-first base palette (the app's primary identity) -----------------
val DarkBackground = Color(0xFF0E0E12)
val DarkSurface = Color(0xFF17171C)
val DarkSurfaceVariant = Color(0xFF201F27)
val DarkOnBackground = Color(0xFFEDEDF2)
val DarkOnSurfaceVariant = Color(0xFFB6B4C0)
val DarkOutline = Color(0xFF39373F)

// --- Light counterpart ------------------------------------------------------
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFF6F5F9)
val LightSurfaceVariant = Color(0xFFE9E7EF)
val LightOnBackground = Color(0xFF1B1B1F)
val LightOnSurfaceVariant = Color(0xFF48464E)
val LightOutline = Color(0xFFC9C6D0)

/**
 * Derives a small family of related colors from the user's single chosen
 * accent color via HSL lightness adjustments, rather than pulling in an
 * unverified seed-color/dynamic-color library. The base dark/light
 * palettes above stay fixed; only primary/secondary come from this.
 */
object AccentPalette {

    /** Contrast the accent must keep against the page and card backgrounds (WCAG 1.4.11, UI components). */
    private const val MIN_ACCENT_CONTRAST = 3.0
    private val DarkInk = Color(0xFF1B1B1F)
    private val LightInk = Color(0xFFFFFFFF)

    fun primary(accentArgb: Long): Color = Color(accentArgb)

    /**
     * The accent as it is used for buttons, switches, sliders, active icons and selected states. A custom
     * colour that would nearly vanish into the [backgrounds] it sits on (a dark navy on the dark theme, a
     * pale yellow on the light one) is nudged lighter or darker, keeping its hue, until it reaches 3:1
     * contrast. Colours that already read well are returned untouched.
     */
    fun readablePrimary(accentArgb: Long, backgrounds: List<Color>): Color {
        val accent = accentArgb.toInt() or 0xFF000000.toInt()
        val backgroundArgbs = backgrounds.map { it.toArgb() }
        fun contrast(argb: Int) = backgroundArgbs.minOf { ColorUtils.calculateContrast(argb, it) }
        if (contrast(accent) >= MIN_ACCENT_CONTRAST) return Color(accent)

        // Move away from the background: lighter on dark themes, darker on light ones.
        val towardLight = backgrounds.map { ColorUtils.calculateLuminance(it.toArgb()) }.average() < 0.5
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        var best = accent
        repeat(50) {
            hsl[2] = (hsl[2] + if (towardLight) 0.02f else -0.02f).coerceIn(0f, 1f)
            best = ColorUtils.HSLToColor(hsl)
            if (contrast(best) >= MIN_ACCENT_CONTRAST) return Color(best)
        }
        return Color(best)
    }

    fun primaryContainer(accentArgb: Long, isDark: Boolean): Color =
        adjustLightness(accentArgb, if (isDark) -0.22f else 0.32f)

    fun secondary(accentArgb: Long, isDark: Boolean): Color =
        adjustLightness(accentArgb, if (isDark) 0.12f else -0.08f)

    fun onColorFor(backgroundArgb: Long): Color {
        val luminance = ColorUtils.calculateLuminance(backgroundArgb.toInt())
        return if (luminance > 0.5) Color(0xFF1B1B1F) else Color(0xFFFFFFFF)
    }

    /** Dark or white text/icons, whichever has more contrast on [background] (not a fixed luminance cut-off). */
    fun onColorFor(background: Color): Color {
        val argb = background.toArgb() or 0xFF000000.toInt()
        val onLight = ColorUtils.calculateContrast(DarkInk.toArgb(), argb)
        val onDark = ColorUtils.calculateContrast(LightInk.toArgb(), argb)
        return if (onLight >= onDark) DarkInk else LightInk
    }

    private fun adjustLightness(argb: Long, delta: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb.toInt(), hsl)
        hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}
