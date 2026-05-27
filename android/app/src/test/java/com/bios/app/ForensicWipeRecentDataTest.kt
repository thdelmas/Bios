package com.bios.app

import com.bios.app.data.dao.AnomalyDao
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.Anomaly
import com.bios.app.model.MetricReading
import com.bios.app.platform.wipeRecentWindow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for issue #313. Before the fix, `wipeRecentData(days)`
 * called `deleteBefore(now)` and removed virtually every row in the DB —
 * the opposite of its contract. This test pins the corrected behavior:
 * given 30 days of seeded data, `wipeRecentData(7)` removes the newest
 * 7 days and leaves the older 23 untouched.
 *
 * The fakes implement only the methods used by [wipeRecentWindow] —
 * `deleteAfter` on both DAOs — and throw on any other call.
 */
class ForensicWipeRecentDataTest {

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val NOW = 30L * DAY_MS
    }

    @Test
    fun wipeRecentData_7days_removes_newest_7_keeps_oldest_23() = runBlocking {
        val readings = WipeFakeMetricReadingDao()
        val anomalies = WipeFakeAnomalyDao()
        repeat(30) { day ->
            readings.seed(reading(timestamp = day * DAY_MS))
            anomalies.seed(anomaly(detectedAt = day * DAY_MS))
        }

        wipeRecentWindow(days = 7, readings = readings, anomalies = anomalies, nowMillis = NOW)

        assertEquals(23, readings.rows.size)
        assertEquals(23, anomalies.rows.size)
        assertTrue(
            "all surviving readings must be older than the 7-day cutoff",
            readings.rows.all { it.timestamp < 23 * DAY_MS },
        )
        assertTrue(
            "all surviving anomalies must be older than the 7-day cutoff",
            anomalies.rows.all { it.detectedAt < 23 * DAY_MS },
        )
    }

    @Test
    fun wipeRecentData_zero_days_removes_only_the_now_row() = runBlocking {
        val readings = WipeFakeMetricReadingDao()
        val anomalies = WipeFakeAnomalyDao()
        readings.seed(reading(timestamp = NOW - DAY_MS))
        readings.seed(reading(timestamp = NOW))
        anomalies.seed(anomaly(detectedAt = NOW - DAY_MS))
        anomalies.seed(anomaly(detectedAt = NOW))

        wipeRecentWindow(days = 0, readings = readings, anomalies = anomalies, nowMillis = NOW)

        assertEquals(1, readings.rows.size)
        assertEquals(NOW - DAY_MS, readings.rows.single().timestamp)
        assertEquals(1, anomalies.rows.size)
        assertEquals(NOW - DAY_MS, anomalies.rows.single().detectedAt)
    }

    @Test
    fun wipeRecentData_window_larger_than_seed_removes_everything() = runBlocking {
        val readings = WipeFakeMetricReadingDao()
        val anomalies = WipeFakeAnomalyDao()
        repeat(5) { day ->
            readings.seed(reading(timestamp = day * DAY_MS))
            anomalies.seed(anomaly(detectedAt = day * DAY_MS))
        }

        wipeRecentWindow(days = 365, readings = readings, anomalies = anomalies, nowMillis = NOW)

        assertEquals(0, readings.rows.size)
        assertEquals(0, anomalies.rows.size)
    }

    private fun reading(timestamp: Long) = MetricReading(
        metricType = "HEART_RATE",
        value = 72.0,
        timestamp = timestamp,
        sourceId = "test-source",
        confidence = 3,
    )

    private fun anomaly(detectedAt: Long) = Anomaly(
        detectedAt = detectedAt,
        metricTypes = "[\"HEART_RATE\"]",
        deviationScores = "{}",
        combinedScore = 0.0,
        severity = 1,
        title = "t",
        explanation = "e",
    )
}

private class WipeFakeMetricReadingDao : MetricReadingDao {
    val rows = mutableListOf<MetricReading>()
    fun seed(r: MetricReading) {
        rows += r
    }

    override suspend fun deleteAfter(afterMillis: Long): Int {
        val before = rows.size
        rows.removeAll { it.timestamp >= afterMillis }
        return before - rows.size
    }

    override suspend fun insertAll(readings: List<MetricReading>): Unit = notUsed()
    override suspend fun insert(reading: MetricReading): Unit = notUsed()
    override suspend fun fetch(metricType: String, startMillis: Long, endMillis: Long): List<MetricReading> = notUsed()
    override suspend fun fetchValues(
        metricType: String, startMillis: Long, endMillis: Long, readingKind: String?
    ): List<Double> = notUsed()
    override suspend fun fetchLatest(metricType: String, limit: Int): List<MetricReading> = notUsed()
    override suspend fun count(metricType: String): Int = notUsed()
    override suspend fun countAll(): Int = notUsed()
    override suspend fun countInRange(
        metricType: String, startMillis: Long, endMillis: Long, readingKind: String?
    ): Int = notUsed()
    override suspend fun lastTimestampFor(metricType: String): Long? = notUsed()
    override suspend fun fetchBucketedMeans(
        metricType: String, startMillis: Long, endMillis: Long, bucketMillis: Long, readingKind: String?
    ): List<Double> = notUsed()
    override suspend fun oldestTimestamp(): Long? = notUsed()
    override suspend fun sourceFreshness(): List<MetricReadingDao.SourceFreshnessRow> = notUsed()
    override suspend fun statusSummary(since24h: Long): List<MetricReadingDao.MetricStatusRow> = notUsed()
    override suspend fun fetchCreatedAfter(sinceMillis: Long): List<MetricReading> = notUsed()
    override suspend fun metricTypeCounts(): List<MetricReadingDao.MetricTypeCountRow> = notUsed()
    override suspend fun sourceCountsForMetric(metricType: String): List<MetricReadingDao.SourceCountRow> = notUsed()
    override suspend fun deleteBefore(beforeMillis: Long): Int = notUsed()
    override suspend fun deleteById(readingId: String): Int = notUsed()
    override suspend fun deleteAll(): Unit = notUsed()

    private fun <T> notUsed(): T = error("WipeFakeMetricReadingDao: only deleteAfter is exercised")
}

private class WipeFakeAnomalyDao : AnomalyDao {
    val rows = mutableListOf<Anomaly>()
    fun seed(a: Anomaly) {
        rows += a
    }

    override suspend fun deleteAfter(afterMillis: Long): Int {
        val before = rows.size
        rows.removeAll { it.detectedAt >= afterMillis }
        return before - rows.size
    }

    override suspend fun insert(anomaly: Anomaly): Unit = notUsed()
    override suspend fun fetchRecent(limit: Int): List<Anomaly> = notUsed()
    override suspend fun fetchUnacknowledged(): List<Anomaly> = notUsed()
    override suspend fun acknowledge(id: String, now: Long): Unit = notUsed()
    override suspend fun saveFeedback(
        id: String, feedbackAt: Long, feltSick: Boolean?, visitedDoctor: Boolean?,
        diagnosis: String?, symptoms: String?, notes: String?, outcomeAccurate: Boolean?
    ): Unit = notUsed()
    override suspend fun fetchWithFeedback(limit: Int): List<Anomaly> = notUsed()
    override suspend fun fetchCreatedAfter(sinceMillis: Long): List<Anomaly> = notUsed()
    override suspend fun fetchAll(): List<Anomaly> = notUsed()
    override suspend fun deleteAll(): Unit = notUsed()

    private fun <T> notUsed(): T = error("WipeFakeAnomalyDao: only deleteAfter is exercised")
}
