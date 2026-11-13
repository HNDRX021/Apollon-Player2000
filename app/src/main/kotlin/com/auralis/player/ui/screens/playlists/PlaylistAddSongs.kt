package com.auralis.player.ui.screens.playlists

import com.auralis.player.ui.components.GlassToolbarButton

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auralis.player.domain.model.Song
import com.auralis.player.ui.components.ArtworkImage

/**
 * Spotify's "Add to this playlist" page: a full-width panel over the playlist with a search field and
 * the whole library below it. A song gets a "+" button; once it is in the playlist the button becomes a
 * check mark, so it is clear what is already there. Every tap adds straight away (no confirm step).
 *
 * @param playlistSongIds ids of the songs already in the playlist (live, so the check marks update).
 */
@Composable
internal fun AddSongsPanel(
    playlistName: String,
    songs: List<Song>,
    playlistSongIds: Set<Long>,
    onAdd: (Song) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim()
    val visible = remember(songs, needle) {
        val matching = if (needle.isEmpty()) songs else songs.filter { song ->
            song.title.contains(needle, ignoreCase = true) ||
                song.artistName.orEmpty().contains(needle, ignoreCase = true) ||
                song.albumTitle.orEmpty().contains(needle, ignoreCase = true)
        }
        matching.sortedBy { it.title.lowercase() }
    }
    val ink = MaterialTheme.colorScheme.onSurface

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassToolbarButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close") }
            Column(Modifier.weight(1f)) {
                Text("Add songs", style = MaterialTheme.typography.titleLarge)
                Text(
                    playlistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search your library") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    GlassToolbarButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
                }
            } else {
                null
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ink.copy(alpha = 0.08f),
                unfocusedContainerColor = ink.copy(alpha = 0.08f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.size(8.dp))
        when {
            songs.isEmpty() -> PanelMessage("Your library is empty", "Add a music folder from Settings first.")
            visible.isEmpty() -> PanelMessage("No songs found", "Nothing in your library matches “$needle”.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { song ->
                    AddSongRow(song = song, added = song.id in playlistSongIds, onAdd = { onAdd(song) })
                }
            }
        }
    }
}

@Composable
private fun PanelMessage(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddSongRow(song: Song, added: Boolean, onAdd: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val subtitle = listOfNotNull(song.artistName?.takeIf(String::isNotBlank), song.albumTitle?.takeIf(String::isNotBlank))
        .joinToString(" · ").ifBlank { "Unknown artist" }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (added) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = "Add ${song.title}", onClick = onAdd))
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(song.artworkUri, Modifier.size(48.dp), targetSize = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (added) {
            Box(Modifier.size(48.dp).semantics { contentDescription = "Already in this playlist" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = scheme.primary)
            }
        } else {
            GlassToolbarButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add ${song.title} to playlist", tint = scheme.onSurface)
            }
        }
    }
}
