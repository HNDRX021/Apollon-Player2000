package com.auralis.player.data.lyrics

import android.net.Uri
import com.auralis.player.domain.model.Song
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

sealed interface LyricsLookupResult {
    data class Found(val lyrics: String) : LyricsLookupResult
    data object NotFound : LyricsLookupResult
    data object MissingArtist : LyricsLookupResult
    data object Failed : LyricsLookupResult
}

/** User-triggered LRCLIB lookup. Only track metadata is sent; audio files are never uploaded. */
@ViewModelScoped
class LyricsLookup @Inject constructor() {

    suspend fun find(song: Song): LyricsLookupResult = withContext(Dispatchers.IO) {
        val artist = song.artistName?.trim().orEmpty()
        if (artist.isEmpty() || song.title.isBlank()) return@withContext LyricsLookupResult.MissingArtist

        val requestUri = Uri.parse("https://lrclib.net/api/get").buildUpon()
            .appendQueryParameter("track_name", song.title)
            .appendQueryParameter("artist_name", artist)
            .apply {
                song.albumTitle?.takeIf(String::isNotBlank)?.let { appendQueryParameter("album_name", it) }
                (song.durationMs / 1_000L).takeIf { it in 1L..3_600L }?.let {
                    appendQueryParameter("duration", it.toString())
                }
            }
            .build()

        var connection: HttpURLConnection? = null
        try {
            val activeConnection = (URL(requestUri.toString()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "ApollonPlayer/0.6.0 (Android)")
            }
            connection = activeConnection

            when (activeConnection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> LyricsLookupResult.NotFound
                in 200..299 -> {
                    val response = activeConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(response)
                    val synced = json.optString("syncedLyrics").trim()
                    val plain = json.optString("plainLyrics").trim()
                    val lyrics = synced.ifBlank { plain }
                    if (lyrics.isBlank()) LyricsLookupResult.NotFound else LyricsLookupResult.Found(lyrics)
                }
                else -> LyricsLookupResult.Failed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            LyricsLookupResult.Failed
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
