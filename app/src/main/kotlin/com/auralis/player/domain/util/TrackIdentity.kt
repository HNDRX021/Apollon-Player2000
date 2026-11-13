package com.auralis.player.domain.util

/**
 * A candidate track discovered on disk during a scan, identified primarily
 * by its SAF [documentId] (stable across rescans of the same file, unlike
 * a raw file path which can shift if a parent folder is renamed).
 */
data class TrackFingerprint(
    val documentId: String,
    val fileSize: Long,
    val lastModified: Long,
    val contentHash: String? = null,
)

/** The matching row already in the database, if any, keyed by documentId. */
data class ExistingTrack(
    val songId: Long,
    val fingerprint: TrackFingerprint,
)

sealed interface DuplicateVerdict {
    /** No existing row for this documentId — insert as new. */
    data object Import : DuplicateVerdict

    /** Existing row, nothing on disk has changed — nothing to do. */
    data class SkipUnchanged(val songId: Long) : DuplicateVerdict

    /** Existing row, but size/lastModified (or content hash) changed. */
    data class UpdateMetadata(val songId: Long) : DuplicateVerdict

    /**
     * Only reachable when deep (content-hash) scanning is enabled: same
     * hash as an existing row under a *different* documentId — e.g. the
     * same file copied into two folders. Import is skipped to avoid a
     * duplicate library entry.
     */
    data class SkipDuplicateContent(val existingSongId: Long) : DuplicateVerdict
}

object TrackIdentity {

    /**
     * Primary key: [TrackFingerprint.documentId]. Secondary check:
     * size + lastModified, to detect an in-place edit (e.g. retagging).
     * Tertiary (optional, opt-in): a content hash, to catch the same file
     * duplicated at a different location — only computed when the user
     * has enabled "Deep duplicate scan" in Settings, since hashing every
     * file is expensive on a large library.
     */
    suspend fun evaluate(
        candidate: TrackFingerprint,
        findByDocumentId: suspend (String) -> ExistingTrack?,
        findByContentHash: suspend (String) -> ExistingTrack?,
    ): DuplicateVerdict {
        val existing = findByDocumentId(candidate.documentId)
        if (existing != null) {
            val unchanged =
                existing.fingerprint.fileSize == candidate.fileSize &&
                    existing.fingerprint.lastModified == candidate.lastModified &&
                    (candidate.contentHash == null || existing.fingerprint.contentHash == candidate.contentHash)
            return if (unchanged) {
                DuplicateVerdict.SkipUnchanged(existing.songId)
            } else {
                DuplicateVerdict.UpdateMetadata(existing.songId)
            }
        }

        val hash = candidate.contentHash
        if (hash != null) {
            val hashMatch = findByContentHash(hash)
            if (hashMatch != null) {
                return DuplicateVerdict.SkipDuplicateContent(hashMatch.songId)
            }
        }

        return DuplicateVerdict.Import
    }
}
