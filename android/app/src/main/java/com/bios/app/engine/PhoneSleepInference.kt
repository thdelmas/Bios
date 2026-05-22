package com.bios.app.engine

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.SleepStage
import com.bios.contracts.MetricType
import kotlin.math.max
import kotlin.math.min

/**
 * Rule-based sleep inference from phone-only signals (issue #134).
 *
 * Inputs are five universally-available phone sensors fused over a nightly
 * window: screen-off state, charging state, ambient-light lux, and
 * accelerometer magnitude variance per sample bucket. The algorithm is
 * intentionally simple — no ML, no per-owner training — because the goal
 * is trend math, not stage-accurate clinical sleep scoring. Published
 * accuracy of comparable phone-only apps is ~30 min RMSE vs actigraphy
 * for total sleep time, which is good enough for Bios's baseline math.
 *
 * The pure logic lives here (a JVM-only object) so it can be exercised by
 * unit tests without an Android device. The Android-bound collection
 * lifecycle lives in [com.bios.app.ingest.PhoneSleepAdapter].
 *
 * Confidence: [ConfidenceTier.MEDIUM] when both charging and dark-environment
 * boosters confirm, otherwise [ConfidenceTier.LOW] — the screen-off-only
 * signal alone is no better than W2F's existing derivation.
 */
object PhoneSleepInference {

    /** One phone-state observation, typically aggregated over ~1 minute. */
    data class Sample(
        val timestamp: Long,
        val screenOff: Boolean,
        val charging: Boolean,
        val ambientLightLux: Float?,
        val accelMagnitudeVar: Float?,
    )

    /**
     * Run the inference. Returns at most one [MetricType.SLEEP_DURATION]
     * reading plus optional [MetricType.SLEEP_STAGE] rows for LIGHT / AWAKE
     * segments inside the sleep window. Empty when no viable window is found.
     */
    fun infer(samples: List<Sample>, sourceId: String): List<MetricReading> {
        if (samples.size < 2) return emptyList()
        val sorted = samples.sortedBy { it.timestamp }
        val window = longestScreenOffStretch(sorted) ?: return emptyList()
        val (startIdx, endIdx) = window
        val windowMs = sorted[endIdx].timestamp - sorted[startIdx].timestamp
        if (windowMs < MIN_SLEEP_WINDOW_MS) return emptyList()

        val segments = segmentByActivity(sorted, startIdx, endIdx)
        val awakeMs = segments.filter { it.stage == SleepStage.AWAKE }.sumOf { it.durationMs }
        val sleepMs = max(0L, windowMs - awakeMs)
        if (sleepMs < MIN_SLEEP_DURATION_MS) return emptyList()

        val confidence = confidenceFor(sorted, startIdx, endIdx)
        val midpoint = sorted[startIdx].timestamp + windowMs / 2L
        val sleepSeconds = sleepMs / 1000L

        val readings = mutableListOf<MetricReading>()
        readings += MetricReading(
            metricType = MetricType.SLEEP_DURATION.key,
            value = sleepSeconds.toDouble(),
            timestamp = midpoint,
            durationSec = sleepSeconds.toInt(),
            sourceId = sourceId,
            confidence = confidence.level
        )
        // Emit per-segment stage rows so the dashboard can render the
        // AWAKE interruption pattern. DEEP / REM are deliberately omitted
        // — phone-only cannot discriminate them.
        for (seg in segments) {
            readings += MetricReading(
                metricType = MetricType.SLEEP_STAGE.key,
                value = seg.stage.value.toDouble(),
                timestamp = seg.startMs,
                durationSec = (seg.durationMs / 1000L).toInt(),
                sourceId = sourceId,
                confidence = confidence.level
            )
        }
        return readings
    }

    private data class Segment(val stage: SleepStage, val startMs: Long, val durationMs: Long)

    /**
     * Walk the [start, end] index range and merge consecutive samples that
     * share the same coarse activity label (AWAKE if accel variance is
     * above [ACTIVITY_THRESHOLD], LIGHT otherwise) into contiguous segments.
     */
    private fun segmentByActivity(
        samples: List<Sample>,
        startIdx: Int,
        endIdx: Int
    ): List<Segment> {
        val out = mutableListOf<Segment>()
        var segStart = samples[startIdx].timestamp
        var currentStage = stageAt(samples[startIdx])
        for (i in (startIdx + 1)..endIdx) {
            val stage = stageAt(samples[i])
            if (stage != currentStage) {
                out += Segment(currentStage, segStart, samples[i].timestamp - segStart)
                segStart = samples[i].timestamp
                currentStage = stage
            }
        }
        out += Segment(currentStage, segStart, samples[endIdx].timestamp - segStart)
        // Collapse very brief AWAKE flickers (< AWAKE_BOUT_MIN_MS) back into
        // the surrounding sleep — single-minute movement bouts during sleep
        // are normal posture shifts, not wake events.
        return mergeBriefAwake(out)
    }

    private fun mergeBriefAwake(segments: List<Segment>): List<Segment> {
        if (segments.size <= 1) return segments
        val out = mutableListOf<Segment>()
        for (seg in segments) {
            val last = out.lastOrNull()
            val tooBrief = seg.stage == SleepStage.AWAKE && seg.durationMs < AWAKE_BOUT_MIN_MS
            if (tooBrief && last != null && last.stage == SleepStage.LIGHT) {
                out[out.lastIndex] = last.copy(durationMs = last.durationMs + seg.durationMs)
            } else if (last != null && last.stage == seg.stage) {
                out[out.lastIndex] = last.copy(durationMs = last.durationMs + seg.durationMs)
            } else {
                out += seg
            }
        }
        return out
    }

    private fun stageAt(sample: Sample): SleepStage {
        val variance = sample.accelMagnitudeVar ?: 0f
        return if (variance > ACTIVITY_THRESHOLD) SleepStage.AWAKE else SleepStage.LIGHT
    }

    /**
     * Per-sample "owner is likely quiet / not actively using the device"
     * predicate. Display-off is the primary signal; when it reads "on" we
     * fall back to a strict three-way fusion to cover phones whose display
     * state is unreliable (always-on-display devices report STATE_ON even
     * during sleep on some OEMs):
     *
     *   - phone is stationary (low accelerometer variance)
     *   - room is dark
     *   - device is charging
     *
     * Requiring all three of the fallback signals keeps "watching a movie
     * in a dark room" from being misclassified as sleep — that scenario
     * fails the stationary check the moment the owner adjusts position.
     * "Phone on nightstand showing AOD" passes all three.
     */
    internal fun isQuietSample(s: Sample): Boolean {
        if (s.screenOff) return true
        val lowMotion = (s.accelMagnitudeVar ?: Float.MAX_VALUE) < ACTIVITY_THRESHOLD
        val dark = (s.ambientLightLux ?: Float.MAX_VALUE) < DARK_LUX_THRESHOLD
        return s.charging && lowMotion && dark
    }

    /**
     * Walk the samples and return the index range of the longest contiguous
     * stretch where [isQuietSample] holds, **tolerating brief activity
     * flickers** shorter than [SCREEN_ON_FLICKER_MS]. A notification, AOD
     * wake, or "lift to wake" event mid-sleep would otherwise split a clean
     * overnight stretch in two; treating those single-sample flickers as
     * part of the surrounding sleep matches owner intent.
     *
     * Returns null when no quiet stretch exists at all.
     */
    private fun longestScreenOffStretch(samples: List<Sample>): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestLen = 0L
        var runStart = -1
        var i = 0
        while (i < samples.size) {
            if (isQuietSample(samples[i])) {
                if (runStart == -1) runStart = i
                if (i == samples.lastIndex) {
                    val len = samples[i].timestamp - samples[runStart].timestamp
                    if (len > bestLen) { best = runStart to i; bestLen = len }
                }
                i++
            } else if (runStart != -1) {
                // Look ahead: how long is this active gap, and does the
                // quiet stretch resume after? If the gap is brief, treat it
                // as a flicker and keep the run going.
                val gapStart = i
                while (i < samples.size && !isQuietSample(samples[i])) i++
                val gapEndExclusive = i
                val gapDurMs = if (gapEndExclusive < samples.size) {
                    samples[gapEndExclusive].timestamp - samples[gapStart].timestamp
                } else {
                    Long.MAX_VALUE
                }
                val resumes = gapEndExclusive < samples.size
                if (resumes && gapDurMs < SCREEN_ON_FLICKER_MS) {
                    // Brief flicker, sleep resumes → keep the run alive.
                    continue
                }
                // Real active segment ended the stretch.
                val runEnd = gapStart - 1
                val len = samples[runEnd].timestamp - samples[runStart].timestamp
                if (len > bestLen) { best = runStart to runEnd; bestLen = len }
                runStart = -1
            } else {
                i++
            }
        }
        return best
    }

    /**
     * Public helper: returns the duration in milliseconds of the longest
     * (flicker-tolerant) screen-off stretch in the given samples, or 0 when
     * no stretch exists. Used by the dashboard diagnostic so the owner can
     * see how close the buffer is to crossing the 4h floor.
     */
    fun longestScreenOffMs(samples: List<Sample>): Long {
        if (samples.size < 2) return 0L
        val sorted = samples.sortedBy { it.timestamp }
        val range = longestScreenOffStretch(sorted) ?: return 0L
        return sorted[range.second].timestamp - sorted[range.first].timestamp
    }

    /** Per-signal breakdown of the buffer so owners can diagnose which
     *  signal is keeping the inference from firing. */
    data class SignalBreakdown(
        val total: Int,
        val screenInactive: Int,
        val lowMotion: Int,
        val dark: Int,
        val charging: Int,
        val quiet: Int,
    )

    fun signalBreakdown(samples: List<Sample>): SignalBreakdown {
        val total = samples.size
        val screenInactive = samples.count { it.screenOff }
        val lowMotion = samples.count {
            (it.accelMagnitudeVar ?: Float.MAX_VALUE) < ACTIVITY_THRESHOLD
        }
        val dark = samples.count {
            (it.ambientLightLux ?: Float.MAX_VALUE) < DARK_LUX_THRESHOLD
        }
        val charging = samples.count { it.charging }
        val quiet = samples.count { isQuietSample(it) }
        return SignalBreakdown(
            total = total,
            screenInactive = screenInactive,
            lowMotion = lowMotion,
            dark = dark,
            charging = charging,
            quiet = quiet,
        )
    }

    /**
     * Two booster signals confirm "this was actual sleep, not a long
     * powered-off phone in a drawer":
     *
     *  - charging plugged in for > [BOOSTER_FRACTION] of the window
     *  - ambient light reading dark (lux < [DARK_LUX_THRESHOLD]) for >
     *    [BOOSTER_FRACTION] of the window (ignoring null samples)
     *
     * Both confirm → MEDIUM. Otherwise the screen-off signal alone is no
     * stronger than W2F's existing derivation → LOW.
     */
    private fun confidenceFor(samples: List<Sample>, startIdx: Int, endIdx: Int): ConfidenceTier {
        val total = endIdx - startIdx + 1
        if (total <= 0) return ConfidenceTier.LOW
        val charging = (startIdx..endIdx).count { samples[it].charging }
        val lit = (startIdx..endIdx).mapNotNull { samples[it].ambientLightLux }
        val chargingFrac = charging.toDouble() / total
        val darkFrac = if (lit.isEmpty()) 0.0
            else lit.count { it < DARK_LUX_THRESHOLD }.toDouble() / lit.size
        return if (chargingFrac > BOOSTER_FRACTION && darkFrac > BOOSTER_FRACTION) {
            ConfidenceTier.MEDIUM
        } else {
            ConfidenceTier.LOW
        }
    }

    // 4h minimum screen-off stretch — anything shorter is more likely a long
    // meeting or movie than a sleep period.
    internal const val MIN_SLEEP_WINDOW_MS: Long = 4L * 60L * 60L * 1000L
    // After subtracting AWAKE bouts, demand at least 1h of actual sleep
    // before publishing anything. Floors out clearly-bad windows.
    internal const val MIN_SLEEP_DURATION_MS: Long = 1L * 60L * 60L * 1000L
    // Brief movement bouts (< 5 min) are posture shifts, not wake events.
    internal const val AWAKE_BOUT_MIN_MS: Long = 5L * 60L * 1000L
    // Single notification waking the screen, AOD, or a "lift to wake" event
    // shouldn't bisect an otherwise clean overnight stretch. ~20 min covers
    // one missed sample at the 15-min cadence plus a small buffer for clock
    // drift; longer gaps imply real wake activity.
    internal const val SCREEN_ON_FLICKER_MS: Long = 20L * 60L * 1000L
    // Accel-magnitude variance above this counts a sample as AWAKE. Hand-
    // tuned for ~5 Hz sampled accelerometer; the variance unit is (m/s^2)^2.
    internal const val ACTIVITY_THRESHOLD: Float = 0.5f
    // Ambient-light threshold below which a room reads as "dark." Dim
    // bedroom nightlight = ~5 lux; phone screen at the lowest brightness
    // pointing at the sensor easily clears 50.
    internal const val DARK_LUX_THRESHOLD: Float = 5f
    // Majority confirmation — both boosters need to hold over the window.
    private const val BOOSTER_FRACTION: Double = 0.5
}
