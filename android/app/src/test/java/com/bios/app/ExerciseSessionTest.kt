package com.bios.app

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.EventPayloadField
import com.bios.app.model.ExerciseModality
import com.bios.app.model.ExerciseSession
import com.bios.app.model.ExerciseSessionFields
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the EXERCISE_SESSION composite shape — parent MetricReading plus
 * a payload list keyed by field. Adapters wire to these names; renames
 * are breaking changes.
 */
class ExerciseSessionTest {

    @Test
    fun modality_constants_are_the_documented_buckets() {
        // The stable string values documented in docs/DATA_MODEL.md.
        // Pattern engine and downstream readers compile against these.
        assertEquals("CARDIO", ExerciseModality.CARDIO)
        assertEquals("STRENGTH", ExerciseModality.STRENGTH)
        assertEquals("INTERVAL", ExerciseModality.INTERVAL)
        assertEquals("MOBILITY", ExerciseModality.MOBILITY)
        assertEquals("OTHER", ExerciseModality.OTHER)
    }

    @Test
    fun field_keys_match_data_model_vocabulary() {
        assertEquals("modality", ExerciseSessionFields.MODALITY)
        assertEquals("start_utc", ExerciseSessionFields.START_UTC)
        assertEquals("end_utc", ExerciseSessionFields.END_UTC)
        assertEquals("avg_hr_bpm", ExerciseSessionFields.AVG_HR_BPM)
        assertEquals("rpe", ExerciseSessionFields.RPE)
    }

    @Test
    fun a_session_carries_the_parent_reading_and_payload_together() {
        val reading = MetricReading(
            metricType = MetricType.EXERCISE_SESSION.key,
            value = 1.0,
            timestamp = 1_700_000_000_000L,
            durationSec = 1800,
            sourceId = "src-1",
            confidence = ConfidenceTier.MEDIUM.level,
        )
        val payload = listOf(
            EventPayloadField(
                readingId = reading.id,
                fieldKey = ExerciseSessionFields.MODALITY,
                stringValue = ExerciseModality.CARDIO,
            ),
            EventPayloadField(
                readingId = reading.id,
                fieldKey = ExerciseSessionFields.START_UTC,
                longValue = 1_700_000_000_000L,
            ),
            EventPayloadField(
                readingId = reading.id,
                fieldKey = ExerciseSessionFields.END_UTC,
                longValue = 1_700_000_000_000L + 1_800_000L,
            ),
        )
        val session = ExerciseSession(reading = reading, payload = payload)

        assertEquals(MetricType.EXERCISE_SESSION.key, session.reading.metricType)
        assertEquals(1.0, session.reading.value, 0.0)
        assertEquals(1800, session.reading.durationSec)
        assertEquals(3, session.payload.size)

        val modalityField = session.payload.first { it.fieldKey == ExerciseSessionFields.MODALITY }
        assertEquals(ExerciseModality.CARDIO, modalityField.stringValue)
        assertNull(modalityField.doubleValue)
        assertNull(modalityField.longValue)

        val startField = session.payload.first { it.fieldKey == ExerciseSessionFields.START_UTC }
        assertNotNull(startField.longValue)
        assertNull(startField.stringValue)
    }
}
