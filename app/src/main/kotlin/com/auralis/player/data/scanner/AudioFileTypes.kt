package com.auralis.player.data.scanner

/**
 * Mime types the scanner treats as audio. Audio MIME types cover the vast
 * majority of files, but some containers (Matroska audio, certain OGG
 * variants) surface under an application MIME type from SAF's
 * `DocumentsContract`, hence the explicit extras below.
 */
object AudioFileTypes {

    val supportedExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "3gp")

    private val extraMimeTypes = setOf(
        "application/ogg",
        "application/x-flac",
    )

    fun isAudio(mimeType: String?): Boolean {
        if (mimeType == null) return false
        return mimeType.startsWith("audio/") || mimeType in extraMimeTypes
    }

    fun isAudio(mimeType: String?, displayName: String, enabledExtensions: Set<String>): Boolean {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        if (extension in supportedExtensions) return extension in enabledExtensions
        return isAudio(mimeType)
    }
}
