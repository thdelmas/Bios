package com.bios.app

import com.bios.app.engine.CycleStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CycleStatsTest {

    private val day = 86_400_000L

    @Test
    fun `lengths between consecutive onsets oldest first`() {
        val onsets = listOf(0L, 28 * day, 58 * day) // 28d then 30d
        assertEquals(listOf(28, 30), CycleStats.cycleLengthsDays(onsets))
    }

    @Test
    fun `fewer than two onsets yield no lengths`() {
        assertEquals(emptyList<Int>(), CycleStats.cycleLengthsDays(emptyList()))
        assertEquals(emptyList<Int>(), CycleStats.cycleLengthsDays(listOf(5 * day)))
    }

    @Test
    fun `same-day duplicate onsets collapse`() {
        val onsets = listOf(0L, 3_600_000L, 28 * day)
        assertEquals(listOf(28), CycleStats.cycleLengthsDays(onsets))
    }

    @Test
    fun `unsorted input is bucketed ascending`() {
        val onsets = listOf(28 * day, 0L)
        assertEquals(listOf(28), CycleStats.cycleLengthsDays(onsets))
    }

    @Test
    fun `median odd and even counts`() {
        assertEquals(28.0, CycleStats.median(listOf(30, 28, 27))!!, 1e-9)
        assertEquals(28.5, CycleStats.median(listOf(27, 30, 28, 29))!!, 1e-9)
        assertNull(CycleStats.median(emptyList()))
    }

    @Test
    fun `cycle day anchors on most recent onset on or before`() {
        val onsets = listOf(0L, 28 * day)
        assertEquals(1, CycleStats.cycleDayFor(0L, onsets))
        assertEquals(28, CycleStats.cycleDayFor(27, onsets))
        assertEquals(1, CycleStats.cycleDayFor(28, onsets))
        assertEquals(5, CycleStats.cycleDayFor(32, onsets))
    }

    @Test
    fun `cycle day is null before any onset`() {
        assertNull(CycleStats.cycleDayFor(10, listOf(20 * day)))
        assertNull(CycleStats.cycleDayFor(10, emptyList()))
    }
}
