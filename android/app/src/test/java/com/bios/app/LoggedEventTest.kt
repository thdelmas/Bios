package com.bios.app

import com.bios.app.model.LoggedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the LoggedEvent entity shape so the schema sketch in
 * docs/SELF_REPORTED_DATA_HOME.md decision 2 doesn't drift.
 * Field renames here are breaking changes — companions wiring up an event
 * writer compile against this class via the Room-generated DAO.
 */
class LoggedEventTest {

    @Test
    fun `id defaults to a UUID`() {
        val event = LoggedEvent(
            eventType = "tobacco_use",
            timestamp = 1_700_000_000_000L,
            sourceId = "src-1",
        )
        assertNotNull(event.id)
        // UUID v4 string length is 36 chars (8-4-4-4-12 plus 4 dashes).
        assertEquals(36, event.id.length)
    }

    @Test
    fun `optional fields are null by default`() {
        val event = LoggedEvent(
            eventType = "tobacco_use",
            timestamp = 1L,
            sourceId = "src-1",
        )
        assertNull(event.severity)
        assertNull(event.durationMs)
        assertNull(event.value)
        assertNull(event.packageName)
        assertNull(event.note)
    }

    @Test
    fun `createdAt defaults to roughly now`() {
        val before = System.currentTimeMillis()
        val event = LoggedEvent(
            eventType = "fall_event",
            timestamp = 1L,
            sourceId = "src-1",
        )
        val after = System.currentTimeMillis()
        assertTrue(event.createdAt in before..after)
    }

    @Test
    fun `severity and duration carry through when provided`() {
        // Smokeless-style intake event: severity 0-3, durationMs for the
        // smoking session, note for the owner's own recall.
        val event = LoggedEvent(
            eventType = "tobacco_use",
            timestamp = 1_700_000_000_000L,
            severity = 2,
            durationMs = 90_000L,
            sourceId = "companion_smokeless",
            packageName = "com.smokless.smokeless",
            note = "after dinner",
        )
        assertEquals(2, event.severity)
        assertEquals(90_000L, event.durationMs)
        assertEquals("com.smokless.smokeless", event.packageName)
        assertEquals("after dinner", event.note)
    }
}
