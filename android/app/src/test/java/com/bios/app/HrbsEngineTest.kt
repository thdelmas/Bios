package com.bios.app

import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.engine.HrbsEngine
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * Tests the orchestration around the HRBS derivation (#309): the engine
 * picks the most-recent SLEEP_DURATION row, scopes HR samples to the
 * pre-sleep window, computes the median, and emits one HRBS reading at
 * the derived bedtime.
 */
class HrbsEngineTest {

    private val utc = ZoneOffset.UTC

    @Test
    fun `no SLEEP_DURATION rows returns null`() = runBlocking {
        val dao = FakeHrbsDao(rows = emptyList())
        val engine = HrbsEngine(dao, zoneId = utc)
        assertNull(engine.derive(now = "2026-04-10T12:00:00Z".asInstant()))
    }

    @Test
    fun `too few HR samples in window returns null`() = runBlocking {
        // Bedtime = 22:30; pre-sleep window = 22:15..22:30. Only 3 samples
        // present — below MIN_HR_SAMPLES_REQUIRED=5, so the engine stays silent.
        val wake = "2026-04-10T06:00:00Z".asInstant()
        val rows = mutableListOf(sleepRow(wake = wake, inBedHours = 7.5))
        rows += listOf(60.0, 62.0, 61.0).mapIndexed { i, bpm ->
            hrSample("2026-04-09T22:%02d:00Z".format(20 + i * 3).asInstant(), bpm)
        }
        val dao = FakeHrbsDao(rows = rows)
        assertNull(HrbsEngine(dao, zoneId = utc).derive(now = "2026-04-10T08:00:00Z".asInstant()))
    }

    @Test
    fun `enough HR samples emits the median bpm at bedtime`() = runBlocking {
        val wake = "2026-04-10T06:00:00Z".asInstant()
        val sleep = sleepRow(wake = wake, inBedHours = 7.5, sourceId = "watch", confidence = ConfidenceTier.HIGH.level)
        val bpms = listOf(58.0, 60.0, 62.0, 64.0, 66.0, 68.0, 70.0)
        val samples = bpms.mapIndexed { i, bpm ->
            // 7 samples spread across the 15-minute pre-sleep window 22:15-22:30.
            hrSample("2026-04-09T22:%02d:00Z".format(17 + i * 2).asInstant(), bpm)
        }
        val dao = FakeHrbsDao(rows = listOf(sleep) + samples)

        val emitted = HrbsEngine(dao, zoneId = utc).derive(now = "2026-04-10T08:00:00Z".asInstant())

        assertNotNull(emitted)
        assertEquals(MetricType.HEART_RATE_BEFORE_SLEEP.key, emitted!!.metricType)
        // Median of 58,60,62,64,66,68,70 = 64.
        assertEquals(64.0, emitted.value, 1e-9)
        // Bedtime = wake (06:00) − 7.5h = 22:30 previous day.
        assertEquals("2026-04-09T22:30:00Z".asInstant().toEpochMilli(), emitted.timestamp)
        assertEquals("watch", emitted.sourceId)
        assertEquals(ConfidenceTier.HIGH.level, emitted.confidence)
    }

    @Test
    fun `HR samples outside the pre-sleep window are ignored`() = runBlocking {
        val wake = "2026-04-10T06:00:00Z".asInstant()
        val sleep = sleepRow(wake = wake, inBedHours = 7.5)
        // 5 in-window low samples (avg ~60), plus 5 out-of-window high samples
        // (90s) hours earlier. Median must come only from the in-window set.
        val inWindow = listOf(58.0, 59.0, 60.0, 61.0, 62.0).mapIndexed { i, bpm ->
            hrSample("2026-04-09T22:%02d:00Z".format(20 + i * 2).asInstant(), bpm)
        }
        val outOfWindow = listOf(90.0, 92.0, 88.0, 94.0, 89.0).mapIndexed { i, bpm ->
            hrSample("2026-04-09T18:%02d:00Z".format(i).asInstant(), bpm)
        }
        val dao = FakeHrbsDao(rows = listOf(sleep) + inWindow + outOfWindow)

        val emitted = HrbsEngine(dao, zoneId = utc).derive(now = "2026-04-10T08:00:00Z".asInstant())
        assertNotNull(emitted)
        assertEquals(60.0, emitted!!.value, 1e-9)
    }

    @Test
    fun `re-emission for the same night is suppressed`() = runBlocking {
        val wake = "2026-04-10T06:00:00Z".asInstant()
        val sleep = sleepRow(wake = wake, inBedHours = 7.5)
        val samples = (0..6).map {
            hrSample("2026-04-09T22:%02d:00Z".format(17 + it * 2).asInstant(), 60.0 + it)
        }
        val dao = FakeHrbsDao(rows = listOf(sleep) + samples)
        // Pretend HRBS was already emitted at bedtime — engine must stay silent.
        dao.recordLastHrbs("2026-04-09T22:30:00Z".asInstant().toEpochMilli())

        assertNull(HrbsEngine(dao, zoneId = utc).derive(now = "2026-04-10T08:00:00Z".asInstant()))
    }

    @Test
    fun `highest-confidence sleep row wins when same night has multiple adapters`() = runBlocking {
        val wake = "2026-04-10T06:00:00Z".asInstant()
        // Two sleep rows for the same night: a manual entry (LOW) and a
        // watch session (HIGH). The HRBS reading must inherit watch confidence.
        val manualSleep = sleepRow(wake = wake, inBedHours = 7.5, sourceId = "manual", confidence = ConfidenceTier.LOW.level)
        val watchSleep = sleepRow(wake = wake, inBedHours = 7.5, sourceId = "watch", confidence = ConfidenceTier.HIGH.level)
        val samples = (0..6).map {
            hrSample("2026-04-09T22:%02d:00Z".format(17 + it * 2).asInstant(), 60.0 + it)
        }
        val dao = FakeHrbsDao(rows = listOf(manualSleep, watchSleep) + samples)

        val emitted = HrbsEngine(dao, zoneId = utc).derive(now = "2026-04-10T08:00:00Z".asInstant())
        assertNotNull(emitted)
        assertEquals("watch", emitted!!.sourceId)
        assertEquals(ConfidenceTier.HIGH.level, emitted.confidence)
    }

    @Test
    fun `medianOf is correct for odd and even sample counts`() {
        // Odd: middle element.
        assertEquals(60.0, HrbsEngine.medianOf(listOf(58.0, 60.0, 62.0)), 1e-9)
        // Even: mean of the two middle elements.
        assertEquals(61.0, HrbsEngine.medianOf(listOf(58.0, 60.0, 62.0, 64.0)), 1e-9)
        // Out-of-order input.
        assertEquals(60.0, HrbsEngine.medianOf(listOf(62.0, 58.0, 60.0)), 1e-9)
    }

    // -- helpers --

    private fun sleepRow(
        wake: Instant,
        inBedHours: Double,
        sourceId: String = "source1",
        confidence: Int = ConfidenceTier.MEDIUM.level,
    ): MetricReading {
        val inBedSec = (inBedHours * 3600).toInt()
        return MetricReading(
            metricType = MetricType.SLEEP_DURATION.key,
            value = inBedSec.toDouble(),
            timestamp = wake.toEpochMilli(),
            durationSec = inBedSec,
            sourceId = sourceId,
            confidence = confidence,
        )
    }

    private fun hrSample(
        at: Instant, bpm: Double, sourceId: String = "watch",
    ): MetricReading = MetricReading(
        metricType = MetricType.HEART_RATE.key,
        value = bpm,
        timestamp = at.toEpochMilli(),
        sourceId = sourceId,
        confidence = ConfidenceTier.MEDIUM.level,
    )

    private fun String.asInstant(): Instant = Instant.parse(this)
}

/**
 * Minimal in-memory MetricReadingDao for [HrbsEngineTest]. Implements only
 * the `fetch` and `lastTimestampFor` paths the engine exercises.
 */
private class FakeHrbsDao(
    private val rows: List<MetricReading>,
) : MetricReadingDao {

    private var lastHrbsTimestamp: Long? = null

    fun recordLastHrbs(timestamp: Long) {
        lastHrbsTimestamp = timestamp
    }

    override suspend fun fetch(metricType: String, startMillis: Long, endMillis: Long): List<MetricReading> =
        rows.filter {
            it.metricType == metricType && it.timestamp in startMillis..endMillis
        }

    override suspend fun lastTimestampFor(metricType: String): Long? =
        if (metricType == MetricType.HEART_RATE_BEFORE_SLEEP.key) lastHrbsTimestamp else null

    override suspend fun insertAll(readings: List<MetricReading>): Unit = notUsed()
    override suspend fun insert(reading: MetricReading): Unit = notUsed()
    override suspend fun fetchValues(metricType: String, startMillis: Long, endMillis: Long, readingKind: String?): List<Double> = notUsed()
    override suspend fun fetchLatest(metricType: String, limit: Int): List<MetricReading> = notUsed()
    override suspend fun count(metricType: String): Int = notUsed()
    override suspend fun countAll(): Int = notUsed()
    override suspend fun countInRange(metricType: String, startMillis: Long, endMillis: Long, readingKind: String?): Int = notUsed()
    override suspend fun fetchBucketedMeans(metricType: String, startMillis: Long, endMillis: Long, bucketMillis: Long, readingKind: String?): List<Double> = notUsed()
    override suspend fun oldestTimestamp(): Long? = notUsed()
    override suspend fun statusSummary(since24h: Long): List<MetricReadingDao.MetricStatusRow> = notUsed()
    override suspend fun sourceFreshness(): List<MetricReadingDao.SourceFreshnessRow> = notUsed()
    override suspend fun metricTypeCounts(): List<MetricReadingDao.MetricTypeCountRow> = notUsed()
    override suspend fun sourceCountsForMetric(metricType: String): List<MetricReadingDao.SourceCountRow> = notUsed()
    override suspend fun fetchCreatedAfter(sinceMillis: Long): List<MetricReading> = notUsed()
    override suspend fun deleteBefore(beforeMillis: Long): Int = notUsed()
    override suspend fun deleteAfter(afterMillis: Long): Int = notUsed()
    override suspend fun deleteById(readingId: String): Int = notUsed()
    override suspend fun deleteAll(): Unit = notUsed()

    private fun <T> notUsed(): T = error("FakeHrbsDao: method not exercised by HrbsEngineTest")
}
