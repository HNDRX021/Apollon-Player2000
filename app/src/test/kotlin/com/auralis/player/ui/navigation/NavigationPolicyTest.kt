package com.auralis.player.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {
    @Test
    fun currentExactDestinationIsNoOp() {
        assertEquals(NavigationAction.NoOp, decideNavigation(targetExists = true, isCurrentTarget = true))
    }

    @Test
    fun earlierExactDestinationIsReused() {
        assertEquals(NavigationAction.PopToTarget, decideNavigation(targetExists = true, isCurrentTarget = false))
    }

    @Test
    fun destinationWithDifferentIdIsNotConsideredTheSame() {
        // The adapter asks NavController for the complete route including its ID argument.
        assertEquals(NavigationAction.Navigate, decideNavigation(targetExists = false, isCurrentTarget = false))
    }

    @Test
    fun newDestinationIsNavigatedTo() {
        assertEquals(NavigationAction.Navigate, decideNavigation(targetExists = false, isCurrentTarget = false))
    }
}
