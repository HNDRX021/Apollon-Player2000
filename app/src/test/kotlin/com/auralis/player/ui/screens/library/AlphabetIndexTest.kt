package com.auralis.player.ui.screens.library

import com.auralis.player.settings.AlbumSort
import com.auralis.player.settings.ArtistSort
import com.auralis.player.settings.SongSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphabetIndexTest {

    @Test
    fun lettersAreUppercasedAndAccentsAreStripped() {
        assertEquals('A', indexLetter("abba"))
        assertEquals('E', indexLetter("\u00c9dith Piaf"))
        assertEquals('O', indexLetter("\u00f6zdemir"))
        assertEquals('B', indexLetter("  Beck"))
    }

    @Test
    fun everythingElseFilesUnderTheHashBucket() {
        assertEquals(OTHER_LETTER, indexLetter("2Pac"))
        assertEquals(OTHER_LETTER, indexLetter("(What's the Story)"))
        assertEquals(OTHER_LETTER, indexLetter("\u0622\u0647\u0646\u06af"))
        assertEquals(OTHER_LETTER, indexLetter(""))
        assertEquals(OTHER_LETTER, indexLetter("   "))
        assertEquals(OTHER_LETTER, indexLetter(null))
    }

    @Test
    fun indexRemembersFirstPositionPerLetterAndSkipsMissingOnes() {
        val index = buildAlphabetIndex(listOf("Abba", "Adele", "Beck", "Cher", "Chic", "9 to 5")) { it }
        assertNotNull(index)
        index!!
        assertEquals(0, index.firstPositionOf('A'))
        assertEquals(2, index.firstPositionOf('B'))
        assertEquals(3, index.firstPositionOf('C'))
        assertEquals(5, index.firstPositionOf(OTHER_LETTER))
        assertNull(index.firstPositionOf('D'))
        assertTrue(index.hasLetter('C'))
        assertFalse(index.hasLetter('Z'))
    }

    @Test
    fun activeLetterFollowsTheFirstVisiblePosition() {
        val index = buildAlphabetIndex(listOf("Abba", "Beck", "Cher")) { it }!!
        assertEquals('A', index.letterAt(0))
        assertEquals('C', index.letterAt(2))
        // Out-of-range positions are clamped rather than crashing while a list is changing.
        assertEquals('C', index.letterAt(99))
        assertEquals('A', index.letterAt(-1))
    }

    @Test
    fun emptyListHasNoIndex() {
        assertNull(buildAlphabetIndex(emptyList<String>()) { it })
    }

    @Test
    fun groupsKeepOneLetterPerRowWhenThereIsRoomAndMergeWhenThereIsNot() {
        assertEquals(IndexLetters.size, indexGroups(40).size)
        assertTrue(indexGroups(40).all { it.size == 1 })

        val squeezed = indexGroups(10)
        assertTrue(squeezed.size <= 10)
        // Every letter still belongs to exactly one row, in order.
        assertEquals(IndexLetters, squeezed.flatten())

        assertTrue(indexGroups(2).isEmpty())
    }

    @Test
    fun aMergedRowJumpsToTheFirstLetterItActuallyHas() {
        val index = buildAlphabetIndex(listOf("Abba", "Cher", "Dido")) { it }!!
        assertEquals('C', index.firstAvailableIn(listOf('B', 'C', 'D')))
        assertNull(index.firstAvailableIn(listOf('X', 'Y', 'Z')))
    }

    @Test
    fun indexOnlyExistsForAlphabeticalSorts() {
        assertNotNull(songIndexText(SongSort.TITLE))
        assertNotNull(songIndexText(SongSort.ARTIST))
        assertNotNull(songIndexText(SongSort.ALBUM))
        assertNull(songIndexText(SongSort.YEAR))
        assertNull(songIndexText(SongSort.DATE_ADDED))
        assertNull(songIndexText(SongSort.DURATION))

        assertNotNull(albumIndexText(AlbumSort.TITLE) { null })
        assertNotNull(albumIndexText(AlbumSort.ARTIST) { null })
        assertNull(albumIndexText(AlbumSort.YEAR) { null })
        assertNull(albumIndexText(AlbumSort.RECENT) { null })
        assertNull(albumIndexText(AlbumSort.FAVORITES) { null })

        assertNotNull(artistIndexText(ArtistSort.NAME))
        assertNull(artistIndexText(ArtistSort.YEAR))
        assertNull(artistIndexText(ArtistSort.FAVORITES))
    }
}
