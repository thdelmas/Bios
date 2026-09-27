package com.bios.app.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bios.app.data.BiosDatabase
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.ReadingKind
import com.bios.contracts.MetricType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ABSENT rules need a control: a metric that was never recorded is not
 * "absent for 72 h". Found by the Mishra 2020 replay (2026-09-27), where
 * `cessation_recovery_pattern` fired on wearable-only participants who
 * had no tobacco companion at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AbsentRuleGateTest {

    private lateinit var db: BiosDatabase
    private val now = 1_700_000_000_000L
    private val day = 24L * 3600 * 1000
    private val sourceId = "wearable"

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BiosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.dataSourceDao().insert(
            DataSource(id = sourceId, sourceType = "health_connect", sensorType = "WEARABLE", readingKind = ReadingKind.SENSOR.name)
        )
        // 14 daily RHR values; the last three days dip well below the 14-day baseline
        // (mean ≈ 57.7, sd ≈ 4.2 → last-72 h mean 50 ⇒ z ≈ −1.8 < −1.0, the pattern's RHR rule).
        val values = listOf(50.0, 50.0, 50.0, 58.0, 60.0, 62.0, 58.0, 60.0, 62.0, 58.0, 60.0, 62.0, 58.0, 60.0)
        db.metricReadingDao().insertAll(values.mapIndexed { i, v ->
            MetricReading(
                metricType = MetricType.RESTING_HEART_RATE.key,
                value = v,
                timestamp = now - i * day - 3600_000,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level,
            )
        })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun runEngine(): Set<String?> {
        BaselineEngine(db, clock = { now }).computeBaseline(MetricType.RESTING_HEART_RATE)
        val detector = AnomalyDetector(db = db, mlModel = null, acuteWindowDetector = null, clock = { now })
        return detector.runDetection().map { it.patternId }.toSet()
    }

    @Test
    fun `cessation pattern stays silent when tobacco was never recorded`() = runBlocking {
        val fired = runEngine()
        assertFalse("ABSENT rule must not activate on a metric with no history: $fired", "cessation_recovery_pattern" in fired)
    }

    @Test
    fun `cessation pattern fires when tobacco was recorded before the window and is now absent`() = runBlocking {
        db.metricReadingDao().insert(
            MetricReading(
                metricType = MetricType.TOBACCO_USE.key,
                value = 1.0,
                timestamp = now - 10 * day,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level,
            )
        )
        val fired = runEngine()
        assertTrue("prior tobacco + 72 h absence + RHR dip should fire: $fired", "cessation_recovery_pattern" in fired)
    }

    @Test
    fun `tobacco older than the lookback does not count as prior presence`() = runBlocking {
        db.metricReadingDao().insert(
            MetricReading(
                metricType = MetricType.TOBACCO_USE.key,
                value = 1.0,
                timestamp = now - (AnomalyDetector.ABSENT_PRIOR_LOOKBACK_HOURS + 72 + 24) * 3600_000,
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level,
            )
        )
        val fired = runEngine()
        assertFalse("tobacco outside the 90-day lookback is not recent presence: $fired", "cessation_recovery_pattern" in fired)
    }
}
