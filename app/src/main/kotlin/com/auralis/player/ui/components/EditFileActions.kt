package com.auralis.player.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.Song

/**
 * How a three-dot menu opens the "Edit File" editor. Provided once at the top of the app
 * (see AuralisNavGraph), so every song and album menu can offer "Edit File" without each screen
 * having to pass anything down. Null (no provider) means the menu simply has no Edit File entry.
 */
@Immutable
class EditFileActions(
    val editSong: (Song) -> Unit,
    val editAlbum: (Album) -> Unit,
)

val LocalEditFileActions = staticCompositionLocalOf<EditFileActions?> { null }
