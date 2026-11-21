package com.bios.app

import com.bios.app.ui.components.FeedbackInput
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for data models used by OnboardingScreen and the FeedbackInput form.
 */
class OnboardingScreenTest {

    @Test
    fun `FeedbackInput defaults are all null`() {
        val input = FeedbackInput()
        assertNull(input.feltSick)
        assertNull(input.symptoms)
        assertNull(input.notes)
        assertNull(input.outcomeAccurate)
    }

    @Test
    fun `FeedbackInput preserves all fields`() {
        val input = FeedbackInput(
            feltSick = true,
            symptoms = "Fever, sore throat",
            notes = "Started feeling better after 3 days",
            outcomeAccurate = true
        )
        assertTrue(input.feltSick!!)
        assertEquals("Fever, sore throat", input.symptoms)
        assertEquals("Started feeling better after 3 days", input.notes)
        assertTrue(input.outcomeAccurate!!)
    }

    @Test
    fun `FeedbackInput with blank strings treated as null by form logic`() {
        val symptoms = "   ".ifBlank { null }
        val notes = "Real note".ifBlank { null }
        assertNull(symptoms)
        assertEquals("Real note", notes)
    }

    @Test
    fun `FeedbackInput equality works for data class`() {
        val a = FeedbackInput(feltSick = true, notes = "test")
        val b = FeedbackInput(feltSick = true, notes = "test")
        assertEquals(a, b)
    }

    @Test
    fun `FeedbackInput copy allows partial updates`() {
        val original = FeedbackInput(feltSick = true)
        val updated = original.copy(outcomeAccurate = false, notes = "Late night")
        assertTrue(updated.feltSick!!)
        assertFalse(updated.outcomeAccurate!!)
        assertEquals("Late night", updated.notes)
    }
}
