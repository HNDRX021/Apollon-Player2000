package com.auralis.player.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccentColorsTest {

    @Test
    fun parsesSixDigitHexWithOrWithoutHash() {
        assertEquals(0xFF7C5CFCL, parseHexColor("7C5CFC"))
        assertEquals(0xFF7C5CFCL, parseHexColor("#7c5cfc"))
        assertEquals(0xFF000000L, parseHexColor("000000"))
        assertEquals(0xFFFFFFFFL, parseHexColor("  FFFFFF "))
    }

    @Test
    fun rejectsAnythingThatIsNotExactlySixHexDigits() {
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("7C5CF"))
        assertNull(parseHexColor("7C5CFCC"))
        assertNull(parseHexColor("GGGGGG"))
        assertNull(parseHexColor("#12 456"))
    }

    @Test
    fun formatsAsUpperCaseSixDigitsIgnoringAlpha() {
        assertEquals("7C5CFC", formatHexColor(0xFF7C5CFCL))
        assertEquals("00000F", formatHexColor(0xFF00000FL))
        assertEquals("E53935", formatHexColor(0x80E53935L))
    }

    @Test
    fun formatAndParseRoundTrip() {
        val color = 0xFF03A9F4L
        assertEquals(color, parseHexColor(formatHexColor(color)))
    }

    @Test
    fun opaqueAccentDropsTransparency() {
        assertEquals(0xFF123456L, opaqueAccent(0x00123456L))
        assertEquals(0xFF123456L, opaqueAccent(0x80123456L))
    }

    @Test
    fun sanitizingKeepsOnlySixUpperCaseHexDigits() {
        assertEquals("7C5CFC", sanitizeHexInput("#7c-5cfc99"))
        assertEquals("", sanitizeHexInput("xyz!"))
    }
}
