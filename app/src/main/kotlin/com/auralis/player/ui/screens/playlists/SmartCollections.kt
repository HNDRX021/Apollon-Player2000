package com.auralis.player.ui.screens.playlists

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import com.auralis.player.R

/**
 * The three automatic playlists shown above the user's own: they have a page of their own (the same
 * layout as a playlist page) but no stored tracks. Their songs are always read from the library, so
 * they follow likes and plays live and never hold copies.
 */
enum class SmartCollection(val key: String, val titleRes: Int, val icon: ImageVector) {
    FAVORITES("favorites", R.string.home_favorites, Icons.Default.Favorite),
    RECENTLY_PLAYED("recent", R.string.home_recently_played, Icons.Default.History),
    MOST_PLAYED("most", R.string.home_most_played, Icons.Default.Whatshot),
    ;

    companion object {
        /** The navigation argument name of the route `collection/{kind}`. */
        const val KIND_ARG = "kind"

        fun fromKey(key: String?): SmartCollection? = entries.firstOrNull { it.key == key }
    }
}
