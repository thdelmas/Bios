package com.bios.app

import com.bios.app.ui.support.MonthlyAskScheduler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyAskSchedulerTest {

    private val cadence = MonthlyAskScheduler.CADENCE_MILLIS
    private val now = 1_700_000_000_000L

    @Test
    fun `unseeded state never fires`() {
        assertFalse(MonthlyAskScheduler.shouldShowPure(now, lastShownMillis = 0L))
        assertFalse(MonthlyAskScheduler.shouldShowPure(now, lastShownMillis = -1L))
    }

    @Test
    fun `before cadence does not fire`() {
        assertFalse(MonthlyAskScheduler.shouldShowPure(now, now - 1L))
        assertFalse(MonthlyAskScheduler.shouldShowPure(now, now - cadence + 1))
    }

    @Test
    fun `at or after cadence fires`() {
        assertTrue(MonthlyAskScheduler.shouldShowPure(now, now - cadence))
        assertTrue(MonthlyAskScheduler.shouldShowPure(now, now - cadence - 1))
        assertTrue(MonthlyAskScheduler.shouldShowPure(now, now - 365L * 24 * 3600 * 1000))
    }

    @Test
    fun `cadence is thirty days`() {
        val thirtyDaysMillis = 30L * 24 * 3600 * 1000
        assertTrue(MonthlyAskScheduler.CADENCE_MILLIS == thirtyDaysMillis)
    }
}
