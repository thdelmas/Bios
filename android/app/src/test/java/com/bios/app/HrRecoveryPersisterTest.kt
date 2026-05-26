package com.bios.app

import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.engine.HrRecoveryPersister
import com.bios.app.model.EventPayloadField
import com.bios.app.model.ExerciseSession
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wiring tests for [HrRecoveryPersister] — the producer that turns
 * landed [ExerciseSession]s into HR_RECOVERY_1MIN/2MIN MetricReadings.
 *
 * Pure-math behaviour for HRR1/HRR2 deltas is already covered by
 * [HrRecoveryComputerTest]; these tests exercise the DAO interaction:
 * query window, write-back, and idempotency under re-ingest.
 */
class HrRecoveryPersisterTest {

    private val sourceId = "test-source"
    private val sessionStart = 1_700_000_000_000L
    private val sessionDurationSec = 30 * 60
    private val sessionEnd = sessionStart + sessionDurationSec * 1000L

    @Test
    fun happy_path_writes_hrr1_and_hrr2_rows() = runBlocking {
        // Healthy recovery shape: peak 165 during session, drops to 130 at
        // +60 s (HRR1 = 35), 115 at +120 s (HRR2 = 50).
        val hrReadings = listOf(
            hr(sessionStart + 5 * 60_000L, 140.0),
            hr(sessionStart + 10 * 60_000L, 155.0),
            hr(sessionStart + 15 * 60_000L, 160.0),
            hr(sessionStart + 20 * 60_000L, 165.0),
            hr(sessionStart + 25 * 60_000L, 162.0),
            hr(sessionEnd, 158.0),
            hr(sessionEnd + 60_000L, 130.0),
            hr(sessionEnd + 120_000L, 115.0),
        )
        val dao = FakeReadingDao(hrReadings)
        val rows = HrRecoveryPersister.persistFor(listOf(session()), dao)

        assertEquals("HRR1 + HRR2 should both be emitted", 2, rows.size)
        val byType = rows.associateBy { it.metricType }
        assertEquals(35.0, byType[MetricType.HR_RECOVERY_1MIN.key]!!.value, 1e-9)
        assertEquals(50.0, byType[MetricType.HR_RECOVERY_2MIN.key]!!.value, 1e-9)
        assertEquals(
            "HRR rows must inherit the session's sourceId",
            sourceId,
            rows.first().sourceId,
        )
        assertEquals(
            "HRR rows are anchored to the session end timestamp",
            sessionEnd,
            rows.first().timestamp,
        )
        assertEquals("DAO insert should be called exactly once", 1, dao.insertAllCallCount)
    }

    @Test
    fun too_few_session_samples_writes_nothing() = runBlocking {
        // Only two HR samples land inside the session — below
        // HrRecoveryComputer.MIN_SESSION_READINGS. Persister must skip
        // silently rather than writing partial / unreliable HRR rows.
        val hrReadings = listOf(
            hr(sessionStart + 5 * 60_000L, 140.0),
            hr(sessionStart + 10 * 60_000L, 150.0),
            hr(sessionEnd + 60_000L, 120.0),
            hr(sessionEnd + 120_000L, 110.0),
        )
        val dao = FakeReadingDao(hrReadings)
        val rows = HrRecoveryPersister.persistFor(listOf(session()), dao)

        assertTrue("No HRR rows when session is too short", rows.isEmpty())
        assertEquals("DAO should not be written to", 0, dao.insertAllCallCount)
    }

    @Test
    fun rerunning_on_same_session_is_idempotent() = runBlocking {
        // Same session ingested twice (e.g. HC re-fetch after a retry).
        // Stable id keying + Room REPLACE means the readings table ends
        // up with the same rows, not duplicates.
        val hrReadings = listOf(
            hr(sessionStart + 5 * 60_000L, 140.0),
            hr(sessionStart + 10 * 60_000L, 155.0),
            hr(sessionStart + 15 * 60_000L, 160.0),
            hr(sessionStart + 20 * 60_000L, 165.0),
            hr(sessionStart + 25 * 60_000L, 162.0),
            hr(sessionEnd + 60_000L, 130.0),
            hr(sessionEnd + 120_000L, 115.0),
        )
        val dao = FakeReadingDao(hrReadings)
        val first = HrRecoveryPersister.persistFor(listOf(session()), dao)
        val second = HrRecoveryPersister.persistFor(listOf(session()), dao)

        assertEquals(
            "Re-running must produce the same ids (REPLACE-friendly)",
            first.map { it.id }.toSet(),
            second.map { it.id }.toSet(),
        )
        assertEquals(2, first.size)
        assertEquals(2, second.size)
    }

    // ---- Helpers ----

    private fun hr(timestamp: Long, value: Double): MetricReading = MetricReading(
        metricType = MetricType.HEART_RATE.key,
        value = value,
        timestamp = timestamp,
        sourceId = sourceId,
        confidence = ConfidenceTier.LOW.level,
    )

    private fun session(): ExerciseSession = ExerciseSession(
        reading = MetricReading(
            metricType = MetricType.EXERCISE_SESSION.key,
            value = 1.0,
            timestamp = sessionStart,
            durationSec = sessionDurationSec,
            sourceId = sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
        ),
        payload = emptyList<EventPayloadField>(),
    )
}

/**
 * Minimal in-memory [MetricReadingDao] for [HrRecoveryPersisterTest].
 * Implements only the methods the persister exercises (`fetch`,
 * `insertAll`); other DAO methods throw if hit so test drift is loud.
 */
private class FakeReadingDao(
    private val seed: List<MetricReading>,
) : MetricReadingDao {

    val inserted = mutableListOf<MetricReading>()
    var insertAllCallCount: Int = 0
        private set

    override suspend fun fetch(
        metricType: String,
        startMillis: Long,
        endMillis: Long,
    ): List<MetricReading> = seed.filter {
        it.metricType == metricType && it.timestamp in startMillis..endMillis
    }

    override suspend fun insertAll(readings: List<MetricReading>) {
        insertAllCallCount++
        inserted += readings
    }

    override suspend fun insert(reading: MetricReading): Unit = notUsed()
    override suspend fun fetchValues(metricType: String, startMillis: Long, endMillis: Long, readingKind: String?): List<Double> = notUsed()
    override suspend fun fetchLatest(metricType: String, limit: Int): List<MetricReading> = notUsed()
    override suspend fun count(metricType: String): Int = notUsed()
    override suspend fun countAll(): Int = notUsed()
    override suspend fun countInRange(metricType: String, startMillis: Long, endMillis: Long, readingKind: String?): Int = notUsed()
    override suspend fun fetchBucketedMeans(metricType: String, startMillis: Long, endMillis: Long, bucketMillis: Long, readingKind: String?): List<Double> = notUsed()
    override suspend fun lastTimestampFor(metricType: String): Long? = notUsed()
    override suspend fun oldestTimestamp(): Long? = notUsed()
    override suspend fun statusSummary(since24h: Long): List<MetricReadingDao.MetricStatusRow> = notUsed()
    override suspend fun sourceFreshness(): List<MetricReadingDao.SourceFreshnessRow> = notUsed()
    override suspend fun metricTypeCounts(): List<MetricReadingDao.MetricTypeCountRow> = notUsed()
    override suspend fun sourceCountsForMetric(metricType: String): List<MetricReadingDao.SourceCountRow> = notUsed()
    override suspend fun fetchCreatedAfter(sinceMillis: Long): List<MetricReading> = notUsed()
    override suspend fun deleteBefore(beforeMillis: Long): Int = notUsed()
    override suspend fun deleteById(readingId: String): Int = notUsed()
    override suspend fun deleteAll(): Unit = notUsed()

    private fun <T> notUsed(): T = error("FakeReadingDao: method not exercised by HrRecoveryPersisterTest")
}
