package com.auralis.player.domain.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrackIdentityTest {
    @Test
    fun unchangedDocumentSkipsContentLookup() = runBlocking {
        val verdict = TrackIdentity.evaluate(
            candidate = TrackFingerprint("doc", 120, 10),
            findByDocumentId = { ExistingTrack(7, TrackFingerprint("doc", 120, 10)) },
            findByContentHash = { error("unchanged tracks must not hash lookup") },
        )

        assertEquals(DuplicateVerdict.SkipUnchanged(7), verdict)
    }

    @Test
    fun matchingCachedDuplicateHashSkipsImport() = runBlocking {
        var imported = false
        val verdict = TrackIdentity.evaluate(
            candidate = TrackFingerprint("copy", 120, 10, "same-hash"),
            findByDocumentId = { null },
            findByContentHash = { hash -> ExistingTrack(7, TrackFingerprint("original", 120, 9, hash)) },
        )

        if (verdict == DuplicateVerdict.Import) imported = true
        assertEquals(DuplicateVerdict.SkipDuplicateContent(7), verdict)
        assertFalse(imported)
    }
}
