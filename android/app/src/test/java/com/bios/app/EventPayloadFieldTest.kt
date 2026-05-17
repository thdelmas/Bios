package com.bios.app

import com.bios.app.model.EventPayloadField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the EventPayloadField entity shape so the composite-event sidecar
 * doesn't drift. Field renames here are breaking changes — Bios's own
 * pattern engines and (eventually) companion readers compile against this
 * class via the Room-generated DAO and the BiosHealthProvider /payload
 * cursor columns.
 */
class EventPayloadFieldTest {

    @Test
    fun `single-field payload exposes the typed value and nulls for the others`() {
        val stringField = EventPayloadField(
            readingId = "reading-1",
            fieldKey = "modality",
            stringValue = "CARDIO",
        )
        assertEquals("CARDIO", stringField.stringValue)
        assertNull(stringField.doubleValue)
        assertNull(stringField.longValue)

        val doubleField = EventPayloadField(
            readingId = "reading-1",
            fieldKey = "avg_hr_bpm",
            doubleValue = 138.0,
        )
        assertNull(doubleField.stringValue)
        assertEquals(138.0, doubleField.doubleValue!!, 0.0)
        assertNull(doubleField.longValue)

        val longField = EventPayloadField(
            readingId = "reading-1",
            fieldKey = "end_utc",
            longValue = 1_700_000_900_000L,
        )
        assertNull(longField.stringValue)
        assertNull(longField.doubleValue)
        assertEquals(1_700_000_900_000L, longField.longValue)
    }

    @Test
    fun `optional fields default to null`() {
        val field = EventPayloadField(readingId = "reading-1", fieldKey = "rpe")
        assertNull(field.stringValue)
        assertNull(field.doubleValue)
        assertNull(field.longValue)
    }

    @Test
    fun `composite key is (readingId, fieldKey)`() {
        // The primary key shape is asserted by Room's compile-time check on
        // the entity; this test exercises that two fields under the same
        // reading can coexist as distinct data-class instances.
        val a = EventPayloadField(readingId = "reading-1", fieldKey = "modality",
            stringValue = "CARDIO")
        val b = EventPayloadField(readingId = "reading-1", fieldKey = "avg_hr_bpm",
            doubleValue = 138.0)
        assertEquals("reading-1", a.readingId)
        assertEquals("reading-1", b.readingId)
        assertEquals("modality", a.fieldKey)
        assertEquals("avg_hr_bpm", b.fieldKey)
    }
}
