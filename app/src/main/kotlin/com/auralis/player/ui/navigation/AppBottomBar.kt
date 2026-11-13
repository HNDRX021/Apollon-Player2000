package com.auralis.player.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/** Height and shape shared by the bottom navigation bar and the mini player above it. */
internal val BottomPanelHeight = 72.dp
private val BottomNavigationHeight = 68.dp
internal val BottomPanelShape = RoundedCornerShape(30.dp)

/**
 * The floating rounded panel used for both the bottom bar and the mini player, so the two always
 * have the same width, corner radius, border and shadow. The mini player passes its own colours
 * (a gradient drawn inside the panel, with [containerColor] transparent).
 */
@Composable
internal fun BottomPanelSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    tonalElevation: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = BottomPanelShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, borderColor),
        content = content,
    )
}

@Composable
fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val scrollResets = LocalScrollResetSignals.current

    BottomPanelSurface(
        modifier = Modifier.navigationBarsPadding().padding(vertical = 8.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
        contentColor = Color.White,
        borderColor = Color.White.copy(alpha = 0.24f),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier
                .height(BottomNavigationHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.12f),
                        0.5f to Color.White.copy(alpha = 0.055f),
                        1f to Color.White.copy(alpha = 0.025f),
                    ),
                ),
        ) {
            Destination.bottomBarItems.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        // Always ask the section to go back to the top, including when its icon is
                        // already the active one.
                        scrollResets.request(destination.route)
                        navController.openSection(destination)
                    },
                    icon = {
                        Icon(
                            destination.icon,
                            contentDescription = stringResource(destination.labelRes),
                            modifier = Modifier.size(23.dp),
                        )
                    },
                    label = null,
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Opens the root screen of a bottom-bar section. Tapping the section you are already on does not
 * navigate at all (no remount, playback untouched); the scroll reset handles it. Switching section
 * keeps the usual save/restore of each section's state, but lands on the section root instead of a
 * detail page that was open there earlier.
 */
internal fun NavHostController.openSection(destination: Destination) {
    val route = destination.route
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    // Restoring a section also restores a detail page that was opened on top of it; an icon tap
    // should show the section itself.
    if (currentDestination?.route != route) popBackStack(route, inclusive = false)
}
