package com.auralis.player.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.auralis.player.R

sealed class Destination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    data object Home : Destination("home", R.string.nav_home, Icons.Filled.Home)
    data object Library : Destination("library", R.string.nav_library, Icons.Filled.LibraryMusic)
    data object Search : Destination("search", R.string.nav_search, Icons.Filled.Search)
    data object Playlists : Destination("playlists", R.string.nav_playlists, Icons.AutoMirrored.Filled.PlaylistPlay)
    data object Settings : Destination("settings", R.string.nav_settings, Icons.Filled.Settings)

    companion object {
        val bottomBarItems = listOf(Home, Library, Search, Playlists, Settings)
    }
}
