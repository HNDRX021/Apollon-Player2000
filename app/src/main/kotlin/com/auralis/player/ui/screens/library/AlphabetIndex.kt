package com.auralis.player.ui.screens.library

import com.auralis.player.domain.model.Album
import com.auralis.player.domain.model.Artist
import com.auralis.player.domain.model.Song
import com.auralis.player.settings.AlbumSort
import com.auralis.player.settings.ArtistSort
import com.auralis.player.settings.SongSort
import java.text.Normalizer
import kotlin.math.ceil

/**
 * Pure logic behind the Library's A-Z side index (no Android or Compose dependencies, so it is
 * unit-testable).
 *
 * The index only makes sense when the list is ordered alphabetically by the field the letters are
 * taken from, so [songIndexText] / [albumIndexText] / [artistIndexText] return null for sorts that
 * are not alphabetical (release year, date added, duration, favorites first, ...): the Library then
 * hides the index instead of showing letters that do not match the list order.
 */

/** Bucket for names that do not start with A-Z: digits, symbols, other scripts, blank. */
const val OTHER_LETTER = '#'

/** Every letter the index can show, in display order. */
val IndexLetters: List<Char> = ('A'..'Z').toList() + OTHER_LETTER

/** The index letter [text] is filed under: its first letter without accents, or [OTHER_LETTER]. */
fun indexLetter(text: String?): Char {
    val first = text?.trim()?.firstOrNull() ?: return OTHER_LETTER
    // "Édith" files under E and "Ärzte" under A, the same place the locale's collator sorts them.
    val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull() ?: return OTHER_LETTER
    val upper = Character.toUpperCase(base)
    return if (upper in 'A'..'Z') upper else OTHER_LETTER
}

/**
 * The letter of every item of a displayed list, by position. Built once per list so a tap resolves
 * to a list position without scanning, and so the "active" letter can be read from the first
 * visible item.
 */
class AlphabetIndex(private val letterByPosition: List<Char>) {
    private val firstPositionByLetter: Map<Char, Int> = buildMap {
        letterByPosition.forEachIndexed { position, letter -> if (letter !in this) put(letter, position) }
    }

    /** Position of the first item filed under [letter], or null when there is none. */
    fun firstPositionOf(letter: Char): Int? = firstPositionByLetter[letter]

    fun hasLetter(letter: Char): Boolean = letter in firstPositionByLetter

    /** The letter of the item at [position] (clamped into the list), or null for an empty list. */
    fun letterAt(position: Int): Char? =
        if (letterByPosition.isEmpty()) null else letterByPosition[position.coerceIn(0, letterByPosition.lastIndex)]

    /** The first letter of [group] that has at least one item, or null if the whole group is empty. */
    fun firstAvailableIn(group: List<Char>): Char? = group.firstOrNull { hasLetter(it) }
}

/** Builds the index of [items] (already in display order), or null for an empty list. */
fun <T> buildAlphabetIndex(items: List<T>, textOf: (T) -> String?): AlphabetIndex? =
    if (items.isEmpty()) null else AlphabetIndex(items.map { indexLetter(textOf(it)) })

/**
 * Splits [IndexLetters] into consecutive groups so at most [slots] rows are needed. With room for
 * every letter each group is a single letter; on short screens neighbouring letters share a row and
 * a tap on that row jumps to the first of them that exists. Returns an empty list when there is not
 * room for a usable index.
 */
fun indexGroups(slots: Int): List<List<Char>> {
    if (slots >= IndexLetters.size) return IndexLetters.map { listOf(it) }
    if (slots < MIN_INDEX_SLOTS) return emptyList()
    val groupSize = ceil(IndexLetters.size / slots.toDouble()).toInt()
    return IndexLetters.chunked(groupSize)
}

private const val MIN_INDEX_SLOTS = 6

// --- Which field feeds the index for each Library sort ---------------------------------------

fun songIndexText(sort: SongSort): ((Song) -> String?)? = when (sort) {
    SongSort.TITLE -> Song::title
    SongSort.ARTIST -> Song::artistName
    SongSort.ALBUM -> Song::albumTitle
    SongSort.DATE_ADDED, SongSort.DURATION, SongSort.YEAR -> null
}

fun albumIndexText(sort: AlbumSort, artistNameOf: (Album) -> String?): ((Album) -> String?)? = when (sort) {
    AlbumSort.TITLE -> Album::title
    AlbumSort.ARTIST -> artistNameOf
    AlbumSort.YEAR, AlbumSort.RECENT, AlbumSort.FAVORITES -> null
}

fun artistIndexText(sort: ArtistSort): ((Artist) -> String?)? = when (sort) {
    ArtistSort.NAME -> Artist::name
    ArtistSort.YEAR, ArtistSort.FAVORITES -> null
}
