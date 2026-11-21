package com.bios.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bios.app.alerts.SignalSnapshot
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.AnomalyDetector
import com.bios.app.model.AlertTier
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.PersonalBaseline
import com.bios.app.model.ReadingKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one alert Bios raises: watched metrics outside the owner's usual range,
 * stated in units, with no cause and no advice attached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AnomalyDetectorTest {

    private lateinit var db: BiosDatabase
    private var now = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val sensor = "wearable"
    private val manual = "manual"

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BiosDatabase::class.java).allowMainThreadQueries().build()
        db.dataSourceDao().insert(
            DataSource(id = sensor, sourceType = "health_connect", sensorType = "WEARABLE", readingKind = ReadingKind.SENSOR.name)
        )
        db.dataSourceDao().insert(
            DataSource(id = manual, sourceType = "manual", sensorType = "MANUAL", readingKind = ReadingKind.SELF_REPORTED.name)
        )
        baseline("resting_heart_rate", mean = 56.5, sd = 2.6, p5 = 53.0, p95 = 60.0)
        baseline("heart_rate_variability", mean = 60.0, sd = 8.0, p5 = 47.0, p95 = 73.0)
        baseline("respiratory_rate", mean = 14.0, sd = 1.0, p5 = 12.4, p95 = 15.6)
        baseline("steps", mean = 8000.0, sd = 2000.0, p5 = 4700.0, p95 = 11300.0)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun baseline(metric: String, mean: Double, sd: Double, p5: Double, p95: Double) =
        db.personalBaselineDao().upsert(PersonalBaseline(metricType = metric, mean = mean, stdDev = sd, p5 = p5, p95 = p95))

    private suspend fun reading(metric: String, value: Double, hoursAgo: Int = 2, source: String = sensor) =
        db.metricReadingDao().insert(
            MetricReading(
                metricType = metric, value = value, timestamp = now - hoursAgo * hour,
                sourceId = source, confidence = ConfidenceTier.MEDIUM.level,
            )
        )

    private fun detector() = AnomalyDetector(db, clock = { now })

    /** RHR +3.7 σ, HRV −2.5 σ, respiratory rate +2 σ. */
    private suspend fun threeMetricsOut() {
        reading("resting_heart_rate", 66.0)
        reading("heart_rate_variability", 40.0)
        reading("respiratory_rate", 16.0)
    }

    @Test
    fun `three metrics outside the usual range raise one alert stated in units`() = runBlocking {
        threeMetricsOut()
        reading("steps", 8100.0)

        val alert = detector().runDetection().single()

        assertEquals(AnomalyDetector.ML_PATTERN_ID, alert.patternId)
        assertEquals("Outside your usual range", alert.title)
        assertNull(alert.suggestedAction)
        assertTrue(
            alert.explanation,
            alert.explanation.startsWith("Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm."),
        )
        assertTrue(alert.explanation, alert.explanation.contains("below your usual 47–73"))
        assertEquals(0.75, alert.combinedScore, 1e-6)
        assertEquals(AlertTier.NOTICE.level, alert.severity)
        assertTrue(alert.metricTypes.contains("\"resting_heart_rate\""))
        assertFalse("in-range metric is not listed as deviating", alert.metricTypes.contains("\"steps\""))
        assertEquals(listOf(alert), db.anomalyDao().fetchAll())
    }

    @Test
    fun `the alert stores every watched signal as measured, in range or not`() = runBlocking {
        threeMetricsOut()
        reading("steps", 8100.0)

        val snapshot = SignalSnapshot.decode(detector().runDetection().single().signalSnapshot)

        assertEquals(
            setOf("resting_heart_rate", "heart_rate_variability", "respiratory_rate", "steps"),
            snapshot.map { it.metricKey }.toSet(),
        )
        val rhr = snapshot.single { it.metricKey == "resting_heart_rate" }
        assertEquals(66.0, rhr.value, 1e-9)
        assertEquals(53.0, rhr.usualLow, 1e-9)
        assertEquals(60.0, rhr.usualHigh, 1e-9)
        assertEquals(1, rhr.readingCount)
    }

    @Test
    fun `the alert text names no cause and gives no advice`() = runBlocking {
        threeMetricsOut()
        val alert = detector().runDetection().single()
        val text = "${alert.title} ${alert.explanation}".lowercase()
        for (word in listOf("symptom", "illness", "infection", "doctor", "care", "health", "review", "may ", "could")) {
            assertFalse("'$word' in: $text", text.contains(word))
        }
    }

    @Test
    fun `two metrics outside the range raise nothing`() = runBlocking {
        reading("resting_heart_rate", 66.0)
        reading("heart_rate_variability", 40.0)
        assertTrue(detector().runDetection().isEmpty())
        assertTrue(db.anomalyDao().fetchAll().isEmpty())
    }

    @Test
    fun `four metrics with one beyond three sigma is a warning`() = runBlocking {
        threeMetricsOut()
        reading("steps", 1000.0)
        val alert = detector().runDetection().single()
        assertEquals(0.9, alert.combinedScore, 1e-6)
        assertEquals(AlertTier.WARNING.level, alert.severity)
    }

    @Test
    fun `self-reported readings do not count`() = runBlocking {
        reading("resting_heart_rate", 66.0)
        reading("heart_rate_variability", 40.0)
        reading("respiratory_rate", 16.0, source = manual)
        assertTrue(detector().runDetection().isEmpty())
    }

    @Test
    fun `readings older than 24 hours do not count`() = runBlocking {
        reading("resting_heart_rate", 66.0)
        reading("heart_rate_variability", 40.0)
        reading("respiratory_rate", 16.0, hoursAgo = 30)
        assertTrue(detector().runDetection().isEmpty())
    }

    @Test
    fun `a metric without a baseline is not scored`() = runBlocking {
        reading("resting_heart_rate", 66.0)
        reading("heart_rate_variability", 40.0)
        reading("blood_oxygen", 80.0)
        assertTrue(detector().runDetection().isEmpty())
    }

    @Test
    fun `no second alert within 24 hours, even on new readings`() = runBlocking {
        threeMetricsOut()
        assertEquals(1, detector().runDetection().size)
        now += 6 * hour
        reading("resting_heart_rate", 70.0, hoursAgo = 1)
        assertTrue(detector().runDetection().isEmpty())
        assertEquals(1, db.anomalyDao().fetchAll().size)
    }

    @Test
    fun `readings an earlier alert was raised on are not alerted again`() = runBlocking {
        threeMetricsOut()
        // Isolate the evidence check from the cool-down: the earlier alert carries
        // another identifier, shares a metric, and is dated after the newest reading.
        db.anomalyDao().insert(
            com.bios.app.model.Anomaly(
                detectedAt = now, metricTypes = "[\"resting_heart_rate\"]", deviationScores = "{}",
                combinedScore = 1.0, patternId = "earlier", severity = 1, title = "t", explanation = "e",
            )
        )
        assertTrue(detector().runDetection().isEmpty())
    }

    @Test
    fun `a new alert is raised after the cool-down when new readings arrive`() = runBlocking {
        threeMetricsOut()
        assertEquals(1, detector().runDetection().size)
        now += 26 * hour
        threeMetricsOut()
        assertEquals(1, detector().runDetection().size)
        assertEquals(2, db.anomalyDao().fetchAll().size)
    }
}
