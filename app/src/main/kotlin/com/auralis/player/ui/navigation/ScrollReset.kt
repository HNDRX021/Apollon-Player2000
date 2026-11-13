package com.auralis.player.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Per-section "go back to the top" signal. Tapping a bottom-bar icon calls [request] for that
 * section's route; the section's screen reacts via [ScrollResetEffect]. A counter (not an event)
 * is used so the request is not lost when the screen is not composed yet: a section being
 * restored from the saved back stack still sees that its counter moved while it was away.
 */
class ScrollResetSignals {
    private val tokens = mutableStateMapOf<String, Int>()
    fun token(route: String): Int = tokens[route] ?: 0
    fun request(route: String) { tokens[route] = token(route) + 1 }
}

val LocalScrollResetSignals = staticCompositionLocalOf { ScrollResetSignals() }

/**
 * Runs [reset] (scroll every list of this section to item 0) whenever the bottom-bar icon for
 * [route] was tapped since this screen last handled it. Coming back from a detail page with the
 * back button does not count, so scroll positions are still preserved there.
 */
@Composable
fun ScrollResetEffect(route: String, reset: suspend () -> Unit) {
    val token = LocalScrollResetSignals.current.token(route)
    var handled by rememberSaveable { mutableIntStateOf(token) }
    val latestReset by rememberUpdatedState(reset)
    LaunchedEffect(token) {
        if (token != handled) {
            handled = token
            latestReset()
        }
    }
}
