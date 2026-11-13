package com.auralis.player.data.scanner

import android.content.Context
import android.database.Cursor
import android.provider.DocumentsContract
import androidx.core.database.getStringOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import javax.inject.Inject

/** A single file found while walking a folder tree. */
data class DiscoveredDocument(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
)

/**
 * Walks a SAF folder tree via [DocumentsContract] queries rather than
 * [androidx.documentfile.provider.DocumentFile.listFiles], which issues a
 * separate metadata query per child and gets prohibitively slow once a
 * folder has more than a few hundred files. A single query per directory,
 * fetching all needed columns at once, scales far better for a real music
 * library.
 */
class DocumentTreeWalker @Inject constructor(@ApplicationContext private val context: Context) {

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    private data class Child(val documentId: String, val displayName: String, val mimeType: String, val size: Long, val lastModified: Long)

    /**
     * @param treeUri the persisted SAF tree URI for the folder root.
     * @param rootDocumentId the root's own document id (from
     *   [DocumentsContract.getTreeDocumentId]).
     * @param recursive whether to descend into subfolders.
     * @param ignoredNames lower-case folder names to skip (with everything below them).
     * @param respectNoMedia skip a (non-root) folder that contains a `.nomedia` file.
     */
    fun walk(
        treeUri: android.net.Uri,
        rootDocumentId: String,
        recursive: Boolean,
        ignoredNames: Set<String> = emptySet(),
        respectNoMedia: Boolean = true,
        enabledAudioFormats: Set<String> = com.auralis.player.settings.AppSettings.DEFAULT_AUDIO_FORMATS,
    ): Sequence<DiscoveredDocument> = sequence {
        val pending = ArrayDeque<String>()
        pending.addLast(rootDocumentId)

        while (pending.isNotEmpty()) {
            val parentDocumentId = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)

            // Fail the walk on provider errors. The scanner then leaves the
            // database untouched instead of treating an unreadable subtree as
            // deleted files. The whole directory is read first so `.nomedia`
            // can rule it out before any of its files are yielded.
            val children = readChildren(childrenUri)

            val isRoot = parentDocumentId == rootDocumentId
            if (respectNoMedia && !isRoot && children.any { it.displayName == NO_MEDIA_FILE }) continue

            for (child in children) {
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (recursive && child.displayName.lowercase() !in ignoredNames) pending.addLast(child.documentId)
                    continue
                }
                if (!AudioFileTypes.isAudio(child.mimeType, child.displayName, enabledAudioFormats)) continue
                yield(
                    DiscoveredDocument(
                        documentId = child.documentId,
                        displayName = child.displayName,
                        mimeType = child.mimeType,
                        size = child.size,
                        lastModified = child.lastModified,
                    ),
                )
            }
        }
    }

    private fun readChildren(childrenUri: android.net.Uri): List<Child> {
        val cursor: Cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw IllegalStateException("Document provider returned no cursor for $childrenUri")
        return cursor.use {
            val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val result = ArrayList<Child>()
            while (it.moveToNext()) {
                val documentId = it.getString(idCol) ?: continue
                val mimeType = it.getStringOrNull(mimeCol) ?: continue
                val displayName = it.getStringOrNull(nameCol) ?: continue
                result += Child(documentId, displayName, mimeType, it.getLong(sizeCol), it.getLong(modifiedCol))
            }
            result
        }
    }

    private companion object {
        const val NO_MEDIA_FILE = ".nomedia"
    }
}
