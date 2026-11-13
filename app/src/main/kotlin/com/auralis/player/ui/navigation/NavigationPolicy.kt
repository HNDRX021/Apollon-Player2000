package com.auralis.player.ui.navigation

import androidx.navigation.NavHostController

internal sealed interface NavigationAction {
    data object NoOp : NavigationAction
    data object PopToTarget : NavigationAction
    data object Navigate : NavigationAction
}

/** Chooses whether to keep the current entry, return to an existing entry, or add a new one. */
internal fun decideNavigation(targetExists: Boolean, isCurrentTarget: Boolean): NavigationAction = when {
    !targetExists -> NavigationAction.Navigate
    isCurrentTarget -> NavigationAction.NoOp
    else -> NavigationAction.PopToTarget
}

/** Reuses exact route instances, including arguments, to avoid duplicate detail/player history. */
internal fun NavHostController.navigateSmart(route: String) {
    val targetEntry = runCatching { getBackStackEntry(route) }.getOrNull()
    when (decideNavigation(targetEntry != null, targetEntry?.id == currentBackStackEntry?.id)) {
        NavigationAction.NoOp -> Unit
        NavigationAction.PopToTarget -> {
            if (!popBackStack(route, inclusive = false)) navigate(route) { launchSingleTop = true }
        }
        NavigationAction.Navigate -> navigate(route) { launchSingleTop = true }
    }
}
