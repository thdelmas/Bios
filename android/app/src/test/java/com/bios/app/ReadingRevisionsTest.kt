package com.bios.app

import com.bios.app.ingest.ReadingRevisions
import com.bios.app.model.MetricReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/specs/alert-detail.md — criterion 8: a re-delivered day-value settles once seen unchanged across two syncs. */
class ReadingRevisionsTest {

    private val t0 = 1_700_000_000_000L
    private fun rhr(value: Double) = MetricReading(
        metricType = "resting_heart_rate", value = value, timestamp = t0, sourceId = "coros", confidence = 2,
    )

    @Test
    fun `first sighting is provisional`() {
        val row = ReadingRevisions.apply(rhr(69.0), existing = null, now = t0)
        assertTrue(row.provisional)
        assertEquals(1, row.seenCount)
        assertEquals(t0, row.firstSeenAt)
    }

    @Test
    fun `second sighting within the same sync gap stays provisional`() {
        val first = ReadingRevisions.apply(rhr(69.0), null, t0)
        val second = ReadingRevisions.apply(rhr(69.0), first, t0 + 15 * 60_000L)
        assertTrue(second.provisional)
        assertEquals(2, second.seenCount)
    }

    @Test
    fun `seen unchanged across two syncs far enough apart settles`() {
        val first = ReadingRevisions.apply(rhr(69.0), null, t0)
        val later = ReadingRevisions.apply(rhr(69.0), first, t0 + ReadingRevisions.SETTLE_GAP_MILLIS)
        assertFalse(later.provisional)
        assertEquals(first.id, later.id)
        assertEquals(first.createdAt, later.createdAt)
    }

    @Test
    fun `a revised value restarts the count`() {
        val first = ReadingRevisions.apply(rhr(69.0), null, t0)
        val revised = ReadingRevisions.apply(rhr(63.0), first, t0 + ReadingRevisions.SETTLE_GAP_MILLIS)
        assertTrue(revised.provisional)
        assertEquals(1, revised.seenCount)
        assertEquals(63.0, revised.value, 0.0)
        assertEquals(t0 + ReadingRevisions.SETTLE_GAP_MILLIS, revised.firstSeenAt)
    }
}
