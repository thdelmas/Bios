package com.bios.app

import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.engine.CircadianEngine
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Exercises the orchestration around [CircadianCalculator]: SLEEP_DURATION rows
 * are read off the bus, grouped by local wake-date, fed to the calculator, and
 * emitted as a CIRCADIAN_PHASE_SHIFT reading.
 *
 * Math parity with W2F lives in [CircadianCalculatorTest]; this file covers the
 * Bios-specific wiring: per-night grouping, multi-adapter confidence tie-break,
 * the "insufficient history" silent path, and idempotent re-emission.
 */
class CircadianEngineTest {

    private val utc = ZoneOffset.UTC

    @Test
    fun `no SLEEP_DURATION rows returns null`() = runBlocking {
        val dao = FakeMetricReadingDao(rows = emptyList())
        val engine = CircadianEngine(dao, zoneId = utc)
        assertNull(engine.derive(now = "2026-04-10T12:00:00Z".asInstant()))
    }

    @Test
    fun `fewer than four nights returns null`() = runBlocking {
        // The calculator needs 3 history nights + today; anything less is silent
        // by design rather than producing a noisy zero-history phase shift.
        val rows = (1..3).map { d ->
            sleepRow(wake = "2026-04-%02dT07:00:00Z".format(d).asInstant(), inBedHours = 7.5)
        }
        val dao = FakeMetricReadingDao(rows = rows)
        val engine = CircadianEngine(dao, zoneId = utc)
        assertNull(engine.derive(now = "2026-04-04T12:00:00Z".asInstant()))
    }

    @Test
    fun `consistent schedule emits a near-zero phase shift`() = runBlocking {
        // 7 nights of identical onset/duration — the math should agree the
        // phase didn't shift.
        val rows = (1..7).map { d ->
            sleepRow(wake = "2026-04-%02dT06:30:00Z".format(d).asInstant(), inBedHours = 7.5)
        }
        val dao = FakeMetricReadingDao(rows = rows)
        val engine = CircadianEngine(dao, zoneId = utc)
        val emitted = engine.derive(now = "2026-04-07T12:00:00Z".asInstant())
        assertNotNull(emitted)
        assertEquals(MetricType.CIRCADIAN_PHASE_SHIFT.key, emitted!!.metricType)
        assertEquals(0.0, emitted.value, 0.5)
    }

    @Test
    fun `later bedtime emits a negative phase shift`() = runBlocking {
        // Six baseline nights with a 23:00 onset, then a much later onset on
        // night seven — that's a phase delay, depression signal.
        val baseline = (1..6).map { d ->
            sleepRow(wake = "2026-04-%02dT06:00:00Z".format(d).asInstant(), inBedHours = 7.0)
        }
        val lateNight = sleepRow(
            wake = "2026-04-07T09:00:00Z".asInstant(),
            inBedHours = 7.0,
        )
        val dao = FakeMetricReadingDao(rows = baseline + lateNight)
        val engine = CircadianEngine(dao, zoneId = utc)
        val emitted = engine.derive(now = "2026-04-07T12:00:00Z".asInstant())
        assertNotNull(emitted)
        assertTrue(
            "Phase delay should produce a negative shift, got ${emitted!!.value}",
            emitted.value < 0.0,
        )
    }

    @Test
    fun `re-emission for the same wake instant is skipped`() = runBlocking {
        // Calling derive twice on identical inputs should produce a reading then
        // null — the second call sees the prior wake timestamp in the DAO and
        // bails. Prevents the once-per-sync invocation from stacking duplicates.
        val rows = (1..7).map { d ->
            sleepRow(wake = "2026-04-%02dT06:30:00Z".format(d).asInstant(), inBedHours = 7.5)
        }
        val dao = FakeMetricReadingDao(rows = rows)
        val engine = CircadianEngine(dao, zoneId = utc)

        val first = engine.derive(now = "2026-04-07T12:00:00Z".asInstant())
        assertNotNull(first)
        dao.recordPhaseShift(first!!.timestamp)

        val second = engine.derive(now = "2026-04-07T13:00:00Z".asInstant())
        assertNull("Second derive should skip — already wrote this night", second)
    }

    @Test
    fun `multi-adapter same night picks highest confidence`() = runBlocking {
        // Two adapters cover night 7 — a watch (HIGH) and a manual entry (LOW).
        // The watch row should provide the source attribution and confidence
        // for the emitted CIRCADIAN_PHASE_SHIFT reading.
        val baseline = (1..6).map { d ->
            sleepRow(wake = "2026-04-%02dT06:00:00Z".format(d).asInstant(), inBedHours = 7.0)
        }
        val manual = sleepRow(
            wake = "2026-04-07T08:00:00Z".asInstant(),
            inBedHours = 7.0,
            sourceId = "manual",
            confidence = ConfidenceTier.LOW.level,
        )
        val watch = sleepRow(
            wake = "2026-04-07T06:00:00Z".asInstant(),
            inBedHours = 7.0,
            sourceId = "watch",
            confidence = ConfidenceTier.HIGH.level,
        )
        val dao = FakeMetricReadingDao(rows = baseline + manual + watch)
        val engine = CircadianEngine(dao, zoneId = utc)
        val emitted = engine.derive(now = "2026-04-07T12:00:00Z".asInstant())
        assertNotNull(emitted)
        assertEquals("watch", emitted!!.sourceId)
        assertEquals(ConfidenceTier.HIGH.level, emitted.confidence)
    }

    // ---- Pure grouping ----

    @Test
    fun `toDailySummaries skips rows missing durationSec`() {
        val rows = listOf(
            sleepRow(wake = "2026-04-01T06:00:00Z".asInstant(), inBedHours = 7.0),
            // No durationSec → can't reconstruct onset, must be skipped
            MetricReading(
                metricType = MetricType.SLEEP_DURATION.key,
                value = 25_200.0,
                timestamp = "2026-04-02T06:00:00Z".asInstant().toEpochMilli(),
                durationSec = null,
                sourceId = "x",
                confidence = ConfidenceTier.MEDIUM.level,
            ),
        )
        val summaries = CircadianEngine.toDailySummaries(rows, utc)
        assertEquals(1, summaries.size)
        assertEquals(LocalDate.of(2026, 4, 1), summaries[0].date)
    }

    @Test
    fun `toDailySummaries computes onset hour as wake minus in-bed time`() {
        // Wake 06:30 UTC, in bed for 7.5h → onset at 23:00 the previous day.
        val rows = listOf(
            sleepRow(wake = "2026-04-01T06:30:00Z".asInstant(), inBedHours = 7.5),
        )
        val summaries = CircadianEngine.toDailySummaries(rows, utc)
        assertEquals(1, summaries.size)
        assertEquals(23.0, summaries[0].summary.sleepOnsetHour!!, 0.01)
        assertEquals(7.5, summaries[0].summary.sleepHours!!, 0.01)
    }

    // ---- Helpers ----

    private fun sleepRow(
        wake: java.time.Instant,
        inBedHours: Double,
        sourceId: String = "source1",
        confidence: Int = ConfidenceTier.MEDIUM.level,
    ): MetricReading {
        val inBedSec = (inBedHours * 3600).toInt()
        return MetricReading(
            metricType = MetricType.SLEEP_DURATION.key,
            // value is "asleep seconds"; for the engine's onset math we only
            // need durationSec (in-bed) and timestamp (wake). Set value = inBed
            // for simplicity — it only affects sleepHours, which is fine.
            value = inBedSec.toDouble(),
            timestamp = wake.toEpochMilli(),
            durationSec = inBedSec,
            sourceId = sourceId,
            confidence = confidence,
        )
    }

    private fun String.asInstant(): java.time.Instant = java.time.Instant.parse(this)
}

/**
 * Minimal in-memory MetricReadingDao for [CircadianEngineTest]. Implements the
 * two methods CircadianEngine actually calls (`fetch` and `lastTimestampFor`);
 * the rest are not exercised here and throw if hit.
 */
private class FakeMetricReadingDao(
    private val rows: List<MetricReading>,
) : MetricReadingDao {

    private var lastPhaseShiftTimestamp: Long? = null

    fun recordPhaseShift(timestamp: Long) {
        lastPhaseShiftTimestamp = timestamp
    }

    override suspend fun fetch(metricType: String, startMillis: Long, endMillis: Long): List<MetricReading> =
        rows.filter {
            it.metricType == metricType && it.timestamp in startMillis..endMillis
        }

    override suspend fun lastTimestampFor(metricType: String): Long? =
        if (metricType == MetricType.CIRCADIAN_PHASE_SHIFT.key) lastPhaseShiftTimestamp else null

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
    override suspend fun deleteById(readingId: String): Int = notUsed()
    override suspend fun deleteAll(): Unit = notUsed()

    private fun <T> notUsed(): T = error("FakeMetricReadingDao: method not exercised by CircadianEngineTest")
}
