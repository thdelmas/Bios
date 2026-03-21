package com.bios.app

import com.bios.app.ingest.SyncWorker
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SyncWorker constants and retention logic.
 */
class SyncWorkerTest {

    @Test
    fun `MINIMUM_DATA_DAYS is 7`() {
        assertEquals(7, SyncWorker.MINIMUM_DATA_DAYS)
    }

    @Test
    fun `RETENTION_DAYS is 90`() {
        assertEquals(90, SyncWorker.RETENTION_DAYS)
    }

    @Test
    fun `retention window in millis is correct`() {
        val retentionMillis = SyncWorker.RETENTION_DAYS.toLong() * 24 * 3600 * 1000
        val expectedMillis = 90L * 24 * 3600 * 1000 // ~7,776,000,000 ms
        assertEquals(expectedMillis, retentionMillis)
    }

    @Test
    fun `retention cutoff is in the past`() {
        val now = System.currentTimeMillis()
        val retentionMillis = SyncWorker.RETENTION_DAYS.toLong() * 24 * 3600 * 1000
        val cutoff = now - retentionMillis
        assertTrue(cutoff < now)
        assertTrue(cutoff > 0)
    }

    @Test
    fun `data age below minimum skips detection`() {
        val dataAgeDays = 5
        val shouldRunDetection = dataAgeDays >= SyncWorker.MINIMUM_DATA_DAYS
        assertFalse(shouldRunDetection)
    }

    @Test
    fun `data age at minimum runs detection`() {
        val dataAgeDays = 7
        val shouldRunDetection = dataAgeDays >= SyncWorker.MINIMUM_DATA_DAYS
        assertTrue(shouldRunDetection)
    }

    @Test
    fun `data age above minimum runs detection`() {
        val dataAgeDays = 30
        val shouldRunDetection = dataAgeDays >= SyncWorker.MINIMUM_DATA_DAYS
        assertTrue(shouldRunDetection)
    }

    @Test
    fun `work name is stable`() {
        assertEquals("bios_sync", SyncWorker.WORK_NAME)
    }

    @Test
    fun `MAX_RETRIES is 3`() {
        assertEquals(3, SyncWorker.MAX_RETRIES)
    }

    @Test
    fun `STALE_THRESHOLD_HOURS is 2`() {
        assertEquals(2, SyncWorker.STALE_THRESHOLD_HOURS)
    }

    @Test
    fun `stale threshold in millis is correct`() {
        val staleMillis = SyncWorker.STALE_THRESHOLD_HOURS * 3600 * 1000L
        assertEquals(7_200_000L, staleMillis)
    }
}
