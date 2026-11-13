package com.auralis.player.data.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileTypesTest {
    @Test fun enabledKnownFormatsRespectUserSelectionEvenWithGenericAudioMime() {
        assertTrue(AudioFileTypes.isAudio("audio/mpeg", "track.mp3", setOf("mp3")))
        assertFalse(AudioFileTypes.isAudio("audio/flac", "track.flac", setOf("mp3")))
    }

    @Test fun unknownExtensionsContinueToUseProviderMimeType() {
        assertTrue(AudioFileTypes.isAudio("audio/x-custom", "track.custom", emptySet()))
        assertFalse(AudioFileTypes.isAudio("application/octet-stream", "notes.txt", AudioFileTypes.supportedExtensions))
    }
}
