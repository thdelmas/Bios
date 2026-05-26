package com.bios.app

import com.bios.app.data.BiosDatabaseMigrationsExtras
import com.bios.app.model.*
import com.bios.app.ui.timeline.TimelineItem
import org.junit.Assert.*
import org.junit.Test

class HealthEventModelTest {

    // --- HealthEvent v33 migration shape (#357) ---

    @Test
    fun `MIGRATION_32_33 covers the right schema versions`() {
        val m = BiosDatabaseMigrationsExtras.MIGRATION_32_33
        assertEquals(32, m.startVersion)
        assertEquals(33, m.endVersion)
    }

    // --- HealthEventType ---

    @Test
    fun `all health event types have labels`() {
        for (type in HealthEventType.entries) {
            assertTrue(type.label.isNotBlank())
        }
    }

    @Test
    fun `health event types include expected entries`() {
        val names = HealthEventType.entries.map { it.name }
        assertTrue(names.contains("SYMPTOM"))
        assertTrue(names.contains("HYPOTHESIS"))
        assertTrue(names.contains("DOCTOR_VISIT"))
        assertTrue(names.contains("DIAGNOSIS"))
        assertTrue(names.contains("TREATMENT"))
        assertTrue(names.contains("NOTE"))
    }

    // --- HealthEventStatus ---

    @Test
    fun `health event statuses include expected entries`() {
        val names = HealthEventStatus.entries.map { it.name }
        assertTrue(names.contains("OPEN"))
        assertTrue(names.contains("RESOLVED"))
        assertTrue(names.contains("DISMISSED"))
    }

    // --- HealthEvent defaults ---

    @Test
    fun `health event defaults to OPEN status`() {
        val event = HealthEvent(type = "SYMPTOM", title = "Metallic taste")
        assertEquals(HealthEventStatus.OPEN.name, event.status)
    }

    @Test
    fun `health event optional fields default to null`() {
        val event = HealthEvent(type = "SYMPTOM", title = "Test")
        assertNull(event.description)
        assertNull(event.anomalyId)
        assertNull(event.parentEventId)
        // Clinical coding (#357) is opt-in metadata.
        assertNull(event.codeSystem)
        assertNull(event.code)
        assertNull(event.codeDisplay)
    }

    @Test
    fun `health event with coded diagnosis preserves all three coding fields`() {
        // Issue #357. Spanish "Cefalea tensional" → ICD-10 G44.2.
        // The title stays the canonical display in the owner's language;
        // the code travels as opt-in structured metadata.
        val event = HealthEvent(
            type = "DIAGNOSIS",
            title = "Cefalea tensional",
            codeSystem = "ICD-10",
            code = "G44.2",
            codeDisplay = "Tension-type headache",
        )
        assertEquals("Cefalea tensional", event.title)
        assertEquals("ICD-10", event.codeSystem)
        assertEquals("G44.2", event.code)
        assertEquals("Tension-type headache", event.codeDisplay)
    }

    @Test
    fun `health event id is generated`() {
        val event = HealthEvent(type = "SYMPTOM", title = "Test")
        assertTrue(event.id.isNotBlank())
    }

    @Test
    fun `health event timestamps are set`() {
        val before = System.currentTimeMillis()
        val event = HealthEvent(type = "SYMPTOM", title = "Test")
        val after = System.currentTimeMillis()
        assertTrue(event.createdAt in before..after)
        assertTrue(event.updatedAt in before..after)
    }

    // --- ActionItem defaults ---

    @Test
    fun `action item defaults to not completed`() {
        val item = ActionItem(healthEventId = "abc", description = "Buy mouthwash")
        assertFalse(item.completed)
        assertNull(item.completedAt)
    }

    @Test
    fun `action item dueAt defaults to null`() {
        val item = ActionItem(healthEventId = "abc", description = "Test")
        assertNull(item.dueAt)
    }

    @Test
    fun `action item id is generated`() {
        val item = ActionItem(healthEventId = "abc", description = "Test")
        assertTrue(item.id.isNotBlank())
    }

    // --- Timeline merging ---

    @Test
    fun `timeline items sort by timestamp descending`() {
        val anomaly = Anomaly(
            detectedAt = 1000L,
            metricTypes = "[]", deviationScores = "{}",
            combinedScore = 1.0, severity = 1,
            title = "Alert", explanation = "Test"
        )
        val event = HealthEvent(
            type = "SYMPTOM", title = "Symptom",
            createdAt = 2000L, updatedAt = 2000L
        )

        val items = listOf(
            TimelineItem.AlertItem(anomaly),
            TimelineItem.EventItem(event)
        ).sortedByDescending { it.timestamp }

        assertEquals(2000L, items[0].timestamp)
        assertEquals(1000L, items[1].timestamp)
        assertTrue(items[0] is TimelineItem.EventItem)
        assertTrue(items[1] is TimelineItem.AlertItem)
    }

    @Test
    fun `timeline items have unique ids`() {
        val anomaly = Anomaly(
            metricTypes = "[]", deviationScores = "{}",
            combinedScore = 1.0, severity = 1,
            title = "Alert", explanation = "Test"
        )
        val event = HealthEvent(type = "SYMPTOM", title = "Symptom")

        val items = listOf(
            TimelineItem.AlertItem(anomaly),
            TimelineItem.EventItem(event)
        )
        val ids = items.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `empty timeline items list is valid`() {
        val items = emptyList<TimelineItem>()
        assertEquals(0, items.size)
    }
}
