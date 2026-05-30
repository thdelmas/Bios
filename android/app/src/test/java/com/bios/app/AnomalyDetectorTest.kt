package com.bios.app

import com.bios.app.engine.applySeverityFloor
import com.bios.app.engine.buildAnomalyExplanation
import com.bios.app.engine.classifySeverity
import com.bios.app.engine.filterDurationAtLeast
import com.bios.app.engine.median
import com.bios.app.model.AlertTier
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AnomalyDetector's pure logic. These call the real top-level
 * functions ([classifySeverity], [buildAnomalyExplanation],
 * [applySeverityFloor], [median]) directly, so a regression in any of them is
 * caught here rather than hidden behind a test-local copy.
 */
class AnomalyDetectorTest {

    // --- classifySeverity tests ---

    @Test
    fun `high combined score triggers advisory`() {
        val tier = classifySeverity(activeSignals = 2, combinedScore = 3.5, totalRules = 6)
        assertEquals(AlertTier.ADVISORY, tier)
    }

    @Test
    fun `high signal ratio triggers advisory`() {
        val tier = classifySeverity(activeSignals = 5, combinedScore = 1.0, totalRules = 6)
        assertEquals(AlertTier.ADVISORY, tier)
    }

    @Test
    fun `medium combined score triggers notice`() {
        val tier = classifySeverity(activeSignals = 2, combinedScore = 2.5, totalRules = 6)
        assertEquals(AlertTier.NOTICE, tier)
    }

    @Test
    fun `medium signal ratio triggers notice`() {
        val tier = classifySeverity(activeSignals = 4, combinedScore = 1.0, totalRules = 6)
        assertEquals(AlertTier.NOTICE, tier)
    }

    @Test
    fun `low score and low ratio triggers observation`() {
        val tier = classifySeverity(activeSignals = 1, combinedScore = 1.5, totalRules = 6)
        assertEquals(AlertTier.OBSERVATION, tier)
    }

    @Test
    fun `boundary combined score 3 is notice not advisory`() {
        val tier = classifySeverity(activeSignals = 1, combinedScore = 3.0, totalRules = 6)
        assertEquals(AlertTier.NOTICE, tier)
    }

    @Test
    fun `boundary combined score 2 is observation not notice`() {
        val tier = classifySeverity(activeSignals = 1, combinedScore = 2.0, totalRules = 6)
        assertEquals(AlertTier.OBSERVATION, tier)
    }

    // --- buildExplanation tests ---

    @Test
    fun `explanation includes deviations sorted by magnitude`() {
        val deviations = mapOf(
            MetricType.RESTING_HEART_RATE to 2.0,
            MetricType.HEART_RATE_VARIABILITY to -3.0,
            MetricType.SKIN_TEMPERATURE_DEVIATION to 1.5
        )

        val explanation = buildAnomalyExplanation("Pattern explanation.", deviations)

        // HRV (|3.0|) should come first, then RHR (|2.0|), then temp (|1.5|)
        val hrvIdx = explanation.indexOf("heart rate variability")
        val rhrIdx = explanation.indexOf("resting heart rate")
        val tempIdx = explanation.indexOf("skin temperature deviation")

        assertTrue("HRV should appear before RHR", hrvIdx < rhrIdx)
        assertTrue("RHR should appear before temp", rhrIdx < tempIdx)
    }

    @Test
    fun `explanation shows above for positive z-scores`() {
        val deviations = mapOf(MetricType.RESTING_HEART_RATE to 2.5)
        val explanation = buildAnomalyExplanation("Test.", deviations)

        assertTrue(explanation.contains("above your personal baseline"))
    }

    @Test
    fun `explanation shows below for negative z-scores`() {
        val deviations = mapOf(MetricType.HEART_RATE_VARIABILITY to -2.0)
        val explanation = buildAnomalyExplanation("Test.", deviations)

        assertTrue(explanation.contains("below your personal baseline"))
    }

    @Test
    fun `explanation ends with pattern explanation`() {
        val deviations = mapOf(MetricType.HEART_RATE to 1.5)
        val patternExplanation = "This is the pattern explanation."
        val explanation = buildAnomalyExplanation(patternExplanation, deviations)

        assertTrue(explanation.endsWith(patternExplanation))
    }

    // --- AlertTier tests ---

    @Test
    fun `alert tier ordering`() {
        assertTrue(AlertTier.OBSERVATION < AlertTier.NOTICE)
        assertTrue(AlertTier.NOTICE < AlertTier.ADVISORY)
        assertTrue(AlertTier.ADVISORY < AlertTier.URGENT)
    }

    @Test
    fun `alert tier fromLevel roundtrips`() {
        for (tier in AlertTier.entries) {
            assertEquals(tier, AlertTier.fromLevel(tier.level))
        }
    }

    @Test
    fun `alert tier fromLevel unknown returns observation`() {
        assertEquals(AlertTier.OBSERVATION, AlertTier.fromLevel(99))
    }

    // --- severityFloor escalation tests ---
    //
    // Exercises the real engine applySeverityFloor used by
    // AnomalyDetector.evaluatePattern: when a pattern declares a severityFloor,
    // the emitted severity is the higher of the classifier's output and the
    // floor. This is the mechanism that makes AlertTier.URGENT reachable for
    // emergency vital-sign patterns (EmergencyVitalPatterns) without affecting
    // trend patterns whose floor is null.

    @Test
    fun `severityFloor escalates classifier output when floor is higher`() {
        // An emergency pattern that fires with a low signal-count (single rule)
        // would otherwise be classified as OBSERVATION; URGENT floor wins.
        assertEquals(
            AlertTier.URGENT,
            applySeverityFloor(classified = AlertTier.OBSERVATION, floor = AlertTier.URGENT)
        )
    }

    @Test
    fun `severityFloor does not downgrade classifier output when classifier is higher`() {
        // Hypothetical: a multi-signal pattern with a NOTICE floor that the
        // classifier scored as ADVISORY — keep ADVISORY.
        assertEquals(
            AlertTier.ADVISORY,
            applySeverityFloor(classified = AlertTier.ADVISORY, floor = AlertTier.NOTICE)
        )
    }

    @Test
    fun `severityFloor null leaves classifier output untouched`() {
        // Trend patterns (the default) leave severityFloor null.
        assertEquals(
            AlertTier.NOTICE,
            applySeverityFloor(classified = AlertTier.NOTICE, floor = null)
        )
    }

    @Test
    fun `severityFloor equal to classifier output is a no-op`() {
        assertEquals(
            AlertTier.URGENT,
            applySeverityFloor(classified = AlertTier.URGENT, floor = AlertTier.URGENT)
        )
    }

    // --- median (multi-reading absolute window) tests ---
    //
    // Exercises the real engine median() used by AnomalyDetector's absolute-
    // rule evaluation. The hypertension_emerging pattern depends on it: the
    // white-coat-robust check across multiple home BP readings uses the
    // median, not the average, so a single outlier reading doesn't pull the
    // signal.

    @Test
    fun `median of odd-count list returns middle element`() {
        assertEquals(125.0, median(listOf(118.0, 125.0, 142.0)), 1e-9)
    }

    @Test
    fun `median of even-count list averages the two middle elements`() {
        assertEquals(126.0, median(listOf(118.0, 124.0, 128.0, 142.0)), 1e-9)
    }

    @Test
    fun `median is robust to a single white-coat outlier`() {
        // Four home readings around 122 systolic, one office white-coat at
        // 168. The mean (131.4) crosses the ACC-AHA 130 stage-1 cutoff
        // because of the outlier; the median (122) does not.
        val readings = listOf(120.0, 122.0, 125.0, 168.0, 122.0)
        val mean = readings.average()
        val med = median(readings)
        assertTrue("Mean would mis-fire the stage-1 cutoff: $mean", mean >= 130.0)
        assertTrue("Median correctly stays below the cutoff: $med", med < 130.0)
    }

    @Test
    fun `median crosses cutoff only when most readings cross`() {
        // Three readings above 130, two below — median is above the cutoff
        // (legitimate sustained elevation).
        val readings = listOf(132.0, 138.0, 145.0, 128.0, 124.0)
        assertTrue("Median should be at or above 130", median(readings) >= 130.0)
    }

    // --- durationAtLeastSec filter (#268) ---
    //
    // Exercises the real engine filterDurationAtLeast used by
    // AnomalyDetector.fetchAbsoluteWindowValues (this helper only projects the
    // surviving rows to their values, as the production fetch does). The
    // status_epilepticus_convulsive pattern relies on this branch to honour the
    // ILAE 2015 t1 = 300 s convulsive-SE definition: a 200 s SEIZURE_EVENT must
    // drop out, a 320 s event must pass through.

    private fun filterByDurationAtLeast(
        rows: List<MetricReading>,
        minDurationSec: Int,
    ): List<Double> = filterDurationAtLeast(rows, minDurationSec).map { it.value }

    @Test
    fun `duration filter drops a sub-300s seizure event`() {
        val rows = listOf(seizureEvent(durationSec = 200))
        assertTrue(
            "200 s seizure must not pass the 300 s gate",
            filterByDurationAtLeast(rows, 300).isEmpty(),
        )
    }

    @Test
    fun `duration filter passes a 320s seizure event`() {
        val rows = listOf(seizureEvent(durationSec = 320))
        val passed = filterByDurationAtLeast(rows, 300)
        assertEquals(
            "320 s seizure must pass the 300 s gate",
            1,
            passed.size,
        )
    }

    @Test
    fun `duration filter splits a mixed cohort at the cutoff`() {
        // Three brief events + one prolonged event. The cluster pattern
        // (no filter) sees four; the status-epilepticus pattern (filter at
        // 300 s) sees one — exactly the discrimination the audit asked for.
        val rows = listOf(
            seizureEvent(durationSec = 60),
            seizureEvent(durationSec = 90),
            seizureEvent(durationSec = 120),
            seizureEvent(durationSec = 360),
        )
        assertEquals(4, rows.size)
        assertEquals(1, filterByDurationAtLeast(rows, 300).size)
    }

    @Test
    fun `duration filter treats null durationSec as zero`() {
        // Some adapters may emit SEIZURE_EVENT without a duration (e.g. an
        // owner who logs "I had a seizure" without recording the length).
        // These must not silently pass a duration-aware gate.
        val rows = listOf(seizureEvent(durationSec = null))
        assertTrue(filterByDurationAtLeast(rows, 300).isEmpty())
    }

    private fun seizureEvent(durationSec: Int?): MetricReading = MetricReading(
        metricType = MetricType.SEIZURE_EVENT.key,
        value = 1.0,
        timestamp = 1_700_000_000_000L,
        durationSec = durationSec,
        sourceId = "owner-self-report",
        confidence = ConfidenceTier.MEDIUM.level,
    )
}
