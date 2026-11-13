package com.auralis.player.ui.screens.edit

import com.auralis.player.domain.model.ArtworkEdit
import com.auralis.player.domain.model.Edit
import com.auralis.player.domain.model.TagChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditFileModelsTest {

    @Test
    fun untouchedFieldIsNotChangedAndShowsNoError() {
        val state = FieldState(original = "1999", text = "1999")
        assertFalse(state.changed)
        assertNull(validateField(EditField.Year, state))
    }

    @Test
    fun mixedFieldOnlyCountsAsChangedOnceReplacementTyped() {
        assertFalse(FieldState(mixed = true).changed)
        assertTrue(FieldState(text = "Rock", mixed = true).changed)
    }

    @Test
    fun yearMustBeFourDigitsAndNumbersPositive() {
        assertNotNull(validateField(EditField.Year, FieldState("2000", "20")))
        assertNull(validateField(EditField.Year, FieldState("2000", "2001")))
        assertNull(validateField(EditField.Year, FieldState("2000", "")))
        assertNotNull(validateField(EditField.TrackNumber, FieldState("1", "0")))
        assertNull(validateField(EditField.DiscNumber, FieldState("1", "2")))
    }

    @Test
    fun titleCannotBeCleared() {
        assertNotNull(validateField(EditField.Title, FieldState("Song", "  ")))
    }

    @Test
    fun tagChangesWithNothingSetAreEmpty() {
        assertTrue(TagChanges().isEmpty)
        assertFalse(TagChanges(genre = Edit.Change("Jazz")).isEmpty)
        assertFalse(TagChanges(artwork = ArtworkEdit.Remove).isEmpty)
    }

    @Test
    fun blankTextTrimsToNullValue() {
        assertNull(FieldState("a", "   ").value)
        assertEquals("x", FieldState("a", " x ").value)
    }
}
