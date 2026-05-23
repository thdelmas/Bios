package com.bios.app

import com.bios.app.engine.SeizureDetector
import com.bios.app.engine.SeizureEventFactory
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.SeizureEventFields
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pure-state tests for [SeizureDetector] covering the #269 acceptance
 * cases: a 30 s, 4-Hz rhythmic burst with a concurrent HR spike must
 * fire a wearable-inferred SEIZURE_EVENT with the right durationSec;
 * the same rhythmic burst without an HR spike must not fire
 * (specificity). Additional cases pin down sustain duration, sample-rate
 * floor, baseline-coverage gate, and the [SeizureEventFactory] payload
 * shape.
 */
class SeizureDetectorTest {

    private val sampleRateHz = 50.0
    private val samplesPerSecond = sampleRateHz.toInt()
    private val startMs = 1_000_000_000_000L

    /**
     * Synthesise a rhythmic accelerometer-norm series at [frequencyHz]
     * for [durationSec] seconds. Amplitude 4 m/s² mimics the clonic-
     * phase shake-magnitude range reported in Beniczky 2013.
     */
    private fun rhythmicSeries(
        frequencyHz: Double,
        durationSec: Int,
        amplitude: Double = 4.0,
    ): List<Double> {
        val total = durationSec * samplesPerSecond
        return List(total) { i ->
            val t = i.toDouble() / sampleRateHz
            amplitude * sin(2.0 * PI * frequencyHz * t)
        }
    }

    /** Quiet baseline — low-amplitude broadband noise (resting wrist). */
    private fun quietSeries(durationSec: Int): List<Double> {
        val total = durationSec * samplesPerSecond
        // Deterministic pseudo-noise so the test is reproducible.
        return List(total) { i -> 0.05 * sin(0.7 * i) + 0.03 * sin(1.3 * i) }
    }

    /** Build an HR series at [bpm] over a window starting at [fromMs] for [durationSec]. */
    private fun hrFlat(fromMs: Long, durationSec: Int, bpm: Double, hzSampling: Double = 1.0): List<SeizureDetector.HrSample> {
        val count = (durationSec * hzSampling).toInt()
        val intervalMs = (1000.0 / hzSampling).toLong()
        return List(count) { i ->
            SeizureDetector.HrSample(fromMs + i * intervalMs, bpm)
        }
    }

    @Test
    fun rhythmic_4hz_30s_with_hr_spike_fires_detection() {
        val accel = rhythmicSeries(frequencyHz = 4.0, durationSec = 30)
        val baseline = hrFlat(startMs - 30_000L, durationSec = 30, bpm = 70.0)
        val event = hrFlat(startMs, durationSec = 30, bpm = 110.0)
        val detections = SeizureDetector.analyse(
            accelNormMagnitudes = accel,
            startTimestampMs = startMs,
            sampleRateHz = sampleRateHz,
            hrSeries = baseline + event,
        )
        assertEquals(1, detections.size)
        val d = detections.first()
        assertEquals(startMs, d.startTimestampMs)
        assertEquals(30, d.durationSec)
        assertEquals(110.0, d.medianHrBpm, 1e-9)
        assertEquals(70.0, d.baselineHrBpm, 1e-9)
    }

    @Test
    fun rhythmic_4hz_30s_without_hr_spike_does_not_fire() {
        val accel = rhythmicSeries(frequencyHz = 4.0, durationSec = 30)
        val baseline = hrFlat(startMs - 30_000L, durationSec = 30, bpm = 70.0)
        // Teeth-brushing-like rhythmic motion: HR barely budges.
        val event = hrFlat(startMs, durationSec = 30, bpm = 75.0)
        val detections = SeizureDetector.analyse(
            accelNormMagnitudes = accel,
            startTimestampMs = startMs,
            sampleRateHz = sampleRateHz,
            hrSeries = baseline + event,
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun short_rhythmic_burst_below_10s_does_not_fire_even_with_hr_spike() {
        // 6 s of rhythmic motion (below MIN_SUSTAINED_SEC) padded with quiet seconds.
        val accel = rhythmicSeries(4.0, 6) + quietSeries(20)
        val baseline = hrFlat(startMs - 30_000L, 30, 70.0)
        val event = hrFlat(startMs, 26, 120.0)
        val detections = SeizureDetector.analyse(
            accelNormMagnitudes = accel,
            startTimestampMs = startMs,
            sampleRateHz = sampleRateHz,
            hrSeries = baseline + event,
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun out_of_band_rhythm_at_1hz_does_not_fire() {
        // Walking-like cadence (≈1 Hz, below the 2 Hz low edge).
        val accel = rhythmicSeries(frequencyHz = 1.0, durationSec = 30)
        val baseline = hrFlat(startMs - 30_000L, 30, 70.0)
        // Even with an HR rise the rhythmic-band gate must reject this.
        val event = hrFlat(startMs, 30, 120.0)
        val detections = SeizureDetector.analyse(
            accelNormMagnitudes = accel,
            startTimestampMs = startMs,
            sampleRateHz = sampleRateHz,
            hrSeries = baseline + event,
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun quiet_low_amplitude_signal_does_not_fire() {
        val accel = quietSeries(durationSec = 30)
        val baseline = hrFlat(startMs - 30_000L, 30, 70.0)
        val event = hrFlat(startMs, 30, 120.0)
        val detections = SeizureDetector.analyse(
            accelNormMagnitudes = accel,
            startTimestampMs = startMs,
            sampleRateHz = sampleRateHz,
            hrSeries = baseline + event,
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun missing_baseline_coverage_returns_empty() {
        val accel = rhythmicSeries(4.0, 30)
        // Only the event window has HR; pre-event baseline is missing.
        val event = hrFlat(startMs, 30, 110.0)
        val detections = SeizureDetector.analyse(
            accelNormMagnitudes = accel,
            startTimestampMs = startMs,
            sampleRateHz = sampleRateHz,
            hrSeries = event,
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun sample_rate_below_floor_returns_empty() {
        val tinyRate = 4.0
        val accel = List(120) { 0.0 }
        val detections = SeizureDetector.analyse(
            accelNormMagnitudes = accel,
            startTimestampMs = startMs,
            sampleRateHz = tinyRate,
            hrSeries = emptyList(),
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun series_shorter_than_minimum_sustain_returns_empty() {
        // Only 5 s of samples — below MIN_SUSTAINED_SEC.
        val accel = rhythmicSeries(4.0, 5)
        val baseline = hrFlat(startMs - 30_000L, 30, 70.0)
        val event = hrFlat(startMs, 5, 110.0)
        val detections = SeizureDetector.analyse(
            accelNormMagnitudes = accel,
            startTimestampMs = startMs,
            sampleRateHz = sampleRateHz,
            hrSeries = baseline + event,
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun factory_writes_seizure_event_with_wearable_inferred_payload() {
        val detection = SeizureDetector.Detection(
            startTimestampMs = startMs,
            durationSec = 30,
            medianHrBpm = 115.0,
            baselineHrBpm = 70.0,
        )
        val event = SeizureEventFactory.fromWearableDetection(
            detection = detection,
            sourceId = "phone_accelerometer",
            now = startMs + 60_000L,
        )
        assertEquals(MetricType.SEIZURE_EVENT.key, event.reading.metricType)
        assertEquals(1.0, event.reading.value, 1e-9)
        assertEquals(30, event.reading.durationSec)
        assertEquals(startMs, event.reading.timestamp)
        assertEquals(ConfidenceTier.LOW.level, event.reading.confidence)
        assertEquals("phone_accelerometer", event.reading.sourceId)

        val source = event.payload.first { it.fieldKey == SeizureEventFields.DETECTION_SOURCE }
        assertEquals(
            SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED,
            source.stringValue,
        )
        val medianHr = event.payload.first { it.fieldKey == SeizureEventFields.MEDIAN_HR_BPM }
        assertEquals(115.0, medianHr.doubleValue!!, 1e-9)
        val baselineHr = event.payload.first { it.fieldKey == SeizureEventFields.BASELINE_HR_BPM }
        assertEquals(70.0, baselineHr.doubleValue!!, 1e-9)
        // Every payload row points back at the parent reading.
        assertTrue(event.payload.all { it.readingId == event.reading.id })
    }

    @Test
    fun detection_durationSec_reflects_window_length_not_full_input() {
        // 12 s rhythmic burst sandwiched between quiet seconds. The
        // detection should report durationSec = 12, not the full input
        // length (24 s).
        val accel = quietSeries(6) + rhythmicSeries(4.0, 12) + quietSeries(6)
        val baseline = hrFlat(startMs - 30_000L, 30, 70.0)
        val event = hrFlat(startMs + 6_000L, 12, 110.0)
        val detections = SeizureDetector.analyse(
            accelNormMagnitudes = accel,
            startTimestampMs = startMs,
            sampleRateHz = sampleRateHz,
            hrSeries = baseline + event,
        )
        assertEquals(1, detections.size)
        val d = detections.first()
        assertEquals(12, d.durationSec)
        assertEquals(startMs + 6_000L, d.startTimestampMs)
    }
}
