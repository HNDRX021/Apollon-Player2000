package com.auralis.player.ui.screens.library

import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.Artist
import com.auralis.player.domain.model.Song
import com.auralis.player.domain.util.releaseSortKey
import com.auralis.player.settings.AlbumSort
import com.auralis.player.settings.ArtistSort
import com.auralis.player.settings.SongSort
import java.text.Collator
import java.util.Locale

/**
 * Pure sorting and genre filtering for the Library tabs (no Android or Compose dependencies,
 * so it is unit-testable). Rules shared by every category:
 *  - text sorts use the locale's [Collator] (accents, Persian) and put blank/missing values last;
 *  - "release year" is chronological, OLDEST FIRST, and items with no date go last;
 *  - ties fall back to the item's name so the order is always deterministic.
 */

/** Genre-filter value for items whose metadata has no genre. `null` means "All Genres". */
const val UNKNOWN_GENRE = "\u0000unknown-genre"

// --- Comparators -------------------------------------------------------------

private fun textComparator(collator: Collator = Collator.getInstance()): Comparator<String?> =
    Comparator { a, b ->
        val x = a?.trim().orEmpty()
        val y = b?.trim().orEmpty()
        when {
            x.isEmpty() && y.isEmpty() -> 0
            x.isEmpty() -> 1
            y.isEmpty() -> -1
            else -> collator.compare(x, y)
        }
    }

/** ISO-like keys ("2017" < "2017-07" < "2017-07-27") ascending, null last. */
private val releaseKeyComparator: Comparator<String?> = Comparator { a, b ->
    when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        else -> a.compareTo(b)
    }
}

private fun yearKey(year: Int?): String? =
    year?.takeIf { it in 1000..2999 }?.let { "%04d".format(Locale.ROOT, it) }

// --- Sorting -----------------------------------------------------------------

fun sortSongs(songs: List<Song>, sort: SongSort, albums: List<Album>): List<Song> {
    val text = textComparator()
    val byTitle = Comparator<Song> { a, b -> text.compare(a.title, b.title) }
    val byArtist = Comparator<Song> { a, b -> text.compare(a.artistName, b.artistName) }
    return when (sort) {
        SongSort.TITLE -> songs.sortedWith(byTitle)
        SongSort.ARTIST -> songs.sortedWith(byArtist.then(byTitle))
        SongSort.ALBUM -> songs.sortedWith(
            Comparator<Song> { a, b -> text.compare(a.albumTitle, b.albumTitle) }
                .then(compareBy<Song> { it.discNumber })
                .then(compareBy<Song, Int?>(nullsLast<Int>()) { it.trackNumber })
                .then(byTitle),
        )
        SongSort.DATE_ADDED -> songs.sortedByDescending { it.dateAdded }
        SongSort.DURATION -> songs.sortedBy { it.durationMs }
        SongSort.YEAR -> {
            // The album's full release date is more precise (and keeps an album together) than a track's year tag.
            val albumKeys = albums.associate { it.id to it.releaseSortKey }
            fun key(song: Song): String? = song.albumId?.let(albumKeys::get) ?: yearKey(song.year)
            songs.sortedWith(
                Comparator<Song> { a, b -> releaseKeyComparator.compare(key(a), key(b)) }
                    .then(byArtist)
                    .then(byTitle),
            )
        }
    }
}

fun sortAlbums(albums: List<Album>, sort: AlbumSort, artistNameOf: (Album) -> String?): List<Album> {
    val text = textComparator()
    val byTitle = Comparator<Album> { a, b -> text.compare(a.title, b.title) }
    val byArtist = Comparator<Album> { a, b -> text.compare(artistNameOf(a), artistNameOf(b)) }
    val byRelease = Comparator<Album> { a, b -> releaseKeyComparator.compare(a.releaseSortKey, b.releaseSortKey) }
    return when (sort) {
        AlbumSort.TITLE -> albums.sortedWith(byTitle)
        AlbumSort.ARTIST -> albums.sortedWith(byArtist.then(byRelease).then(byTitle))
        AlbumSort.YEAR -> albums.sortedWith(byRelease.then(byArtist).then(byTitle))
        AlbumSort.RECENT -> albums.sortedByDescending { it.dateAdded }
        AlbumSort.FAVORITES -> albums.sortedWith(compareByDescending<Album> { it.isFavorite }.then(byTitle))
    }
}

fun sortArtists(artists: List<Artist>, sort: ArtistSort, albums: List<Album>): List<Artist> {
    val text = textComparator()
    val byName = Comparator<Artist> { a, b -> text.compare(a.name, b.name) }
    return when (sort) {
        ArtistSort.NAME -> artists.sortedWith(byName)
        ArtistSort.FAVORITES -> artists.sortedWith(compareByDescending<Artist> { it.isFavorite }.then(byName))
        ArtistSort.YEAR -> {
            // An artist's "release year" is the date of their earliest album.
            val earliest: Map<Long, String?> = albums
                .filter { it.albumArtistId != null }
                .groupBy { it.albumArtistId!! }
                .mapValues { (_, list) -> list.mapNotNull { it.releaseSortKey }.minOrNull() }
            artists.sortedWith(
                Comparator<Artist> { a, b -> releaseKeyComparator.compare(earliest[a.id], earliest[b.id]) }.then(byName),
            )
        }
    }
}

// --- Genres ------------------------------------------------------------------

/** Case/whitespace-insensitive identity of a genre, or null when the tag is missing/blank. */
fun genreKey(name: String?): String? = name?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)

/** key -> the first spelling seen, used as the label in the genre menu. */
fun genreDisplayNames(songs: List<Song>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for (song in songs) {
        val key = genreKey(song.genreName) ?: continue
        result.getOrPut(key) { song.genreName!!.trim() }
    }
    return result
}

fun songGenreSet(song: Song): Set<String> = setOfNotNull(genreKey(song.genreName))

/** albumId -> genre keys of the songs on that album. */
fun albumGenreMap(songs: List<Song>): Map<Long, Set<String>> {
    val result = HashMap<Long, MutableSet<String>>()
    for (song in songs) {
        val albumId = song.albumId ?: continue
        val key = genreKey(song.genreName) ?: continue
        result.getOrPut(albumId) { mutableSetOf() }.add(key)
    }
    return result
}

/** artistId -> genre keys of their songs, counting both the track artist and the album artist. */
fun artistGenreMap(songs: List<Song>, albums: List<Album>): Map<Long, Set<String>> {
    val albumArtist = albums.associate { it.id to it.albumArtistId }
    val result = HashMap<Long, MutableSet<String>>()
    for (song in songs) {
        val key = genreKey(song.genreName) ?: continue
        song.artistId?.let { result.getOrPut(it) { mutableSetOf() }.add(key) }
        song.albumId?.let { albumArtist[it] }?.let { result.getOrPut(it) { mutableSetOf() }.add(key) }
    }
    return result
}

/** What the genre menu offers for one category. */
data class GenreChoices(val names: List<String>, val hasUnknown: Boolean) {
    fun accepts(selected: String?): Boolean = when (selected) {
        null -> true
        UNKNOWN_GENRE -> hasUnknown
        else -> names.any { genreKey(it) == genreKey(selected) }
    }
}

fun genreChoices(itemGenres: Collection<Set<String>>, display: Map<String, String>): GenreChoices {
    val collator = Collator.getInstance()
    val keys = itemGenres.flatMapTo(LinkedHashSet()) { it }
    val names = keys.mapNotNull(display::get).sortedWith { a, b -> collator.compare(a, b) }
    return GenreChoices(names, hasUnknown = itemGenres.any { it.isEmpty() })
}

fun matchesGenre(itemGenres: Set<String>, selected: String?): Boolean = when (selected) {
    null -> true
    UNKNOWN_GENRE -> itemGenres.isEmpty()
    else -> genreKey(selected) in itemGenres
}

fun filterSongsByGenre(songs: List<Song>, selected: String?): List<Song> =
    if (selected == null) songs else songs.filter { matchesGenre(songGenreSet(it), selected) }

fun filterAlbumsByGenre(albums: List<Album>, genres: Map<Long, Set<String>>, selected: String?): List<Album> =
    if (selected == null) albums else albums.filter { matchesGenre(genres[it.id].orEmpty(), selected) }

fun filterArtistsByGenre(artists: List<Artist>, genres: Map<Long, Set<String>>, selected: String?): List<Artist> =
    if (selected == null) artists else artists.filter { matchesGenre(genres[it.id].orEmpty(), selected) }
