package com.auralis.player.ui.screens.library

import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.Artist
import com.auralis.player.domain.model.Song
import com.auralis.player.settings.AlbumSort
import com.auralis.player.settings.ArtistSort
import com.auralis.player.settings.SongSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOrderingTest {

    private fun song(
        id: Long,
        title: String,
        artist: String? = null,
        artistId: Long? = null,
        albumId: Long? = null,
        genre: String? = null,
        year: Int? = null,
    ) = Song(
        id = id, documentId = "d$id", folderId = 1L, title = title, artistId = artistId, artistName = artist,
        albumId = albumId, albumTitle = null, genreId = null, composer = null, year = year,
        trackNumber = null, totalTracks = null, discNumber = 1, totalDiscs = null,
        durationMs = 1000L, isFavorite = false, playCount = 0, lastPlayedAt = null, genreName = genre,
    )

    private fun album(id: Long, title: String, artistId: Long? = null, releaseDate: String? = null, year: Int? = null) =
        Album(
            id = id, title = title, albumArtistId = artistId, year = year, artworkUri = null,
            trackCount = 1, discCount = 1, releaseDate = releaseDate,
        )

    private fun artist(id: Long, name: String) = Artist(id = id, name = name, artworkUri = null)

    @Test
    fun songsSortAlphabeticallyAndMissingValuesGoLast() {
        val songs = listOf(song(1, "Charlie"), song(2, "alpha"), song(3, "Bravo"))
        assertEquals(listOf(2L, 3L, 1L), sortSongs(songs, SongSort.TITLE, emptyList()).map { it.id })

        val byArtist = sortSongs(
            listOf(song(1, "x", artist = null), song(2, "y", artist = "Zed"), song(3, "z", artist = "Abe")),
            SongSort.ARTIST, emptyList(),
        )
        assertEquals(listOf(3L, 2L, 1L), byArtist.map { it.id })
    }

    @Test
    fun releaseYearIsOldestFirstWithUnknownLastForEveryCategory() {
        val albums = listOf(
            album(1, "Undated", artistId = 10),
            album(2, "New", artistId = 10, releaseDate = "2020-03-01"),
            album(3, "Old", artistId = 11, year = 1999),
        )
        assertEquals(listOf(3L, 2L, 1L), sortAlbums(albums, AlbumSort.YEAR) { null }.map { it.id })

        // A song follows its album's release date; with no album it falls back to its own year tag.
        val songs = listOf(
            song(1, "no date"),
            song(2, "on new album", albumId = 2),
            song(3, "on old album", albumId = 3),
            song(4, "tag only", year = 2005),
        )
        assertEquals(listOf(3L, 4L, 2L, 1L), sortSongs(songs, SongSort.YEAR, albums).map { it.id })

        // An artist is ordered by their earliest album; artists with no dated album go last.
        val artists = listOf(artist(10, "Ten"), artist(11, "Eleven"), artist(12, "Twelve"))
        assertEquals(listOf(11L, 10L, 12L), sortArtists(artists, ArtistSort.YEAR, albums).map { it.id })
    }

    @Test
    fun albumsSortByArtistThenReleaseThenTitle() {
        val albums = listOf(
            album(1, "B", artistId = 2, year = 2000),
            album(2, "A", artistId = 1, year = 2010),
            album(3, "C", artistId = 1, year = 2005),
            album(4, "D", artistId = null),
        )
        val names = mapOf(1L to "Zed", 2L to "Abe")
        val sorted = sortAlbums(albums, AlbumSort.ARTIST) { it.albumArtistId?.let(names::get) }
        assertEquals(listOf(1L, 3L, 2L, 4L), sorted.map { it.id })
    }

    @Test
    fun genresAreMatchedCaseInsensitivelyAndUnknownIsItsOwnChoice() {
        val songs = listOf(
            song(1, "a", genre = "Rock"),
            song(2, "b", genre = " rock "),
            song(3, "c", genre = "Jazz"),
            song(4, "d", genre = null),
            song(5, "e", genre = "  "),
        )
        val display = genreDisplayNames(songs)
        val choices = genreChoices(songs.map(::songGenreSet), display)
        assertEquals(listOf("Jazz", "Rock"), choices.names)
        assertTrue(choices.hasUnknown)

        assertEquals(listOf(1L, 2L), filterSongsByGenre(songs, "Rock").map { it.id })
        assertEquals(listOf(4L, 5L), filterSongsByGenre(songs, UNKNOWN_GENRE).map { it.id })
        assertEquals(5, filterSongsByGenre(songs, null).size)
    }

    @Test
    fun albumsAndArtistsInheritGenresFromTheirSongs() {
        val songs = listOf(
            song(1, "a", artistId = 10, albumId = 100, genre = "Rock"),
            song(2, "b", artistId = 11, albumId = 100, genre = "Pop"),
            song(3, "c", artistId = 12, albumId = 200, genre = null),
        )
        val albums = listOf(album(100, "One", artistId = 10), album(200, "Two", artistId = 12))
        val albumGenres = albumGenreMap(songs)
        assertEquals(listOf(100L), filterAlbumsByGenre(albums, albumGenres, "Pop").map { it.id })
        assertEquals(listOf(200L), filterAlbumsByGenre(albums, albumGenres, UNKNOWN_GENRE).map { it.id })

        val artists = listOf(artist(10, "A"), artist(11, "B"), artist(12, "C"))
        val artistGenres = artistGenreMap(songs, albums)
        // Artist 10 is the album artist of album 100, so it also gets "Pop" from track 2.
        assertEquals(listOf(10L, 11L), filterArtistsByGenre(artists, artistGenres, "Pop").map { it.id })
        assertEquals(listOf(12L), filterArtistsByGenre(artists, artistGenres, UNKNOWN_GENRE).map { it.id })
    }

    @Test
    fun staleGenreSelectionIsRejected() {
        val choices = GenreChoices(listOf("Rock"), hasUnknown = false)
        assertTrue(choices.accepts(null))
        assertTrue(choices.accepts("rock"))
        assertFalse(choices.accepts("Jazz"))
        assertFalse(choices.accepts(UNKNOWN_GENRE))
    }
}
