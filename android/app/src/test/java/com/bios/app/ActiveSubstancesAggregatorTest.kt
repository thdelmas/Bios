package com.bios.app

import com.bios.app.engine.EliminationKinetics
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.ui.intake.ActiveSubstancesAggregator
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-state tests for the "what's still in your body" aggregator (#138).
 *
 * Exercises empty-state semantics (no recent intake ≠ zero), source
 * provenance plumbing, threshold-crossing readout, and the curve sample
 * shape. The math itself is covered by ConcentrationMathTest; here we
 * only confirm the dashboard layer wires the right inputs and presents
 * them honestly.
 */
class ActiveSubstancesAggregatorTest {

    private val now = 1_700_000_000_000L
    private val hour = 60L * 60L * 1000L

    @Test
    fun `empty intake list yields all known substances as empty cards`() {
        val cards = ActiveSubstancesAggregator.compute(
            intakes = emptyList(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        // Caffeine and alcohol are the two routed substances today; future
        // dose-bearing keys grow this set without changing the empty-state
        // contract.
        val keys = cards.map { it.metricKey }.toSet()
        assertTrue("caffeine card always present", MetricType.CAFFEINE_INTAKE.key in keys)
        assertTrue("alcohol card always present", MetricType.ALCOHOL_INTAKE.key in keys)
        for (c in cards) {
            assertEquals(0.0, c.currentAmountMg, 1e-9)
            assertNull("no last dose when there are no intakes", c.lastDoseTimestampMs)
            assertNull(c.lastDoseLabel)
            assertTrue("no recent doses to display", c.recentDoses.isEmpty())
            assertEquals(false, c.hasRecentIntake)
        }
    }

    @Test
    fun `caffeine card surfaces current amount and last dose source`() {
        val intakes = listOf(
            caffeineIntake(timestampMs = now - 2L * hour, doseMg = 200.0, sourceId = "src-w2f"),
        )
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = mapOf("src-w2f" to "W2F"),
            nowMs = now,
        )
        val caffeine = cards.first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        assertEquals(true, caffeine.hasRecentIntake)
        // 200 mg with t½ = 5 h and 2 h elapsed ⇒ 200 × 0.5^(2/5) ≈ 152 mg.
        // Bateman absorption with 7-min half-life is fully complete by 2 h.
        assertTrue(
            "current amount $caffeine.currentAmountMg should be near 152 mg",
            caffeine.currentAmountMg in 145.0..158.0
        )
        assertEquals("W2F", caffeine.lastDoseLabel)
        assertEquals(now - 2L * hour, caffeine.lastDoseTimestampMs)
    }

    @Test
    fun `older doses outside the window still inform current amount`() {
        // 8 h ago: caffeine t½ 5 h ⇒ ~33 % remaining = ~66 mg from a 200mg
        // dose. The dose itself falls outside the 24h list-recent-doses
        // window only if it's > 24 h old; an 8 h dose is inside.
        val intakes = listOf(
            caffeineIntake(timestampMs = now - 8L * hour, doseMg = 200.0, sourceId = "src"),
        )
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = mapOf("src" to "W2F"),
            nowMs = now,
        )
        val caffeine = cards.first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        assertTrue(
            "8h-old caffeine should still contribute ~60-70mg, got ${caffeine.currentAmountMg}",
            caffeine.currentAmountMg in 55.0..75.0
        )
    }

    @Test
    fun `intakes 2 days old contribute to current concentration but not recent-doses list`() {
        // Engine math reads the full prior history (the aggregator does
        // not window the doseEvents list); only the recent-doses display
        // is windowed.
        val intakes = listOf(
            caffeineIntake(timestampMs = now - 48L * hour, doseMg = 200.0, sourceId = "src"),
            caffeineIntake(timestampMs = now - 1L * hour, doseMg = 100.0, sourceId = "src"),
        )
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = mapOf("src" to "W2F"),
            nowMs = now,
        )
        val caffeine = cards.first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        assertEquals(
            "recent-doses list should only show the 1h dose (24h window)",
            1, caffeine.recentDoses.size,
        )
        assertEquals(now - 1L * hour, caffeine.recentDoses.first().timestampMs)
    }

    @Test
    fun `unknown source falls back to a labelled placeholder`() {
        val intakes = listOf(caffeineIntake(timestampMs = now - hour, doseMg = 100.0, sourceId = "ghost"))
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val caffeine = cards.first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        assertEquals(ActiveSubstancesAggregator.UNKNOWN_SOURCE_LABEL, caffeine.lastDoseLabel)
        assertEquals(
            ActiveSubstancesAggregator.UNKNOWN_SOURCE_LABEL,
            caffeine.recentDoses.first().sourceLabel,
        )
    }

    @Test
    fun `alcohol value is converted from grams to milligrams in the dose-event stream`() {
        // 14 g of ethanol (one US standard drink) recorded as ALCOHOL_INTAKE.
        // F = 0.9 ⇒ 12 600 mg on-board after absorption; zero-order rate
        // ≈ 117 mg/min ⇒ after 60 min: 12600 − 7000 = 5600 mg.
        val intakes = listOf(
            alcoholIntake(timestampMs = now - 60L * 60L * 1000L, doseG = 14.0, sourceId = "src"),
        )
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val alcohol = cards.first { it.metricKey == MetricType.ALCOHOL_INTAKE.key }
        assertEquals(EliminationKinetics.ZERO_ORDER, alcohol.pk.kinetics)
        assertTrue(
            "alcohol on-board after 1h should be ~5600 mg, got ${alcohol.currentAmountMg}",
            alcohol.currentAmountMg in 5_400.0..5_800.0
        )
        // Display side: 14 g entered → 14000 mg in the dose-event stream.
        assertEquals(14_000.0, alcohol.recentDoses.first().doseMg, 1.0)
    }

    @Test
    fun `threshold override beats the substance default`() {
        // Dose at now-1h so the Bateman absorption phase is complete and
        // current amount is well above both thresholds; otherwise both
        // crossings land at 0 (already-below) and the comparison is
        // meaningless.
        val intakes = listOf(caffeineIntake(timestampMs = now - hour, doseMg = 200.0, sourceId = "src"))
        val withDefault = ActiveSubstancesAggregator.compute(
            intakes = intakes, sourceLabels = emptyMap(), nowMs = now,
        ).first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        // Caffeine default threshold is 50 mg.
        assertEquals(50.0, withDefault.thresholdMg!!, 1e-9)

        val withOverride = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = emptyMap(),
            nowMs = now,
            thresholdOverridesMg = mapOf(MetricType.CAFFEINE_INTAKE.key to 25.0),
        ).first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        assertEquals(25.0, withOverride.thresholdMg!!, 1e-9)
        // Time-until-below: tighter threshold ⇒ longer wait.
        assertTrue(
            "tighter threshold should push the crossing later " +
                "(default=${withDefault.timeUntilBelowThresholdMs}, " +
                "override=${withOverride.timeUntilBelowThresholdMs})",
            (withOverride.timeUntilBelowThresholdMs ?: 0L) >
                (withDefault.timeUntilBelowThresholdMs ?: 0L)
        )
    }

    @Test
    fun `curve points span the configured window`() {
        val intakes = listOf(caffeineIntake(timestampMs = now - hour, doseMg = 100.0, sourceId = "src"))
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val caffeine = cards.first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        // 24 h / 5 min step ⇒ 289 samples (inclusive of both endpoints).
        assertEquals(289, caffeine.curve.size)
        // First point at window start, last at now.
        assertEquals(now - ActiveSubstancesAggregator.DEFAULT_WINDOW_MS, caffeine.curve.first().first)
        assertEquals(now, caffeine.curve.last().first)
    }

    @Test
    fun `unknown metric_type readings do not pollute substance cards`() {
        // A glucose row landing in the intake list (e.g. caller didn't
        // pre-filter) must be silently ignored — the aggregator routes by
        // metric_type so it can't accidentally treat blood_glucose as a
        // caffeine dose.
        val intakes = listOf(
            MetricReading(
                metricType = MetricType.BLOOD_GLUCOSE.key,
                value = 90.0,
                timestamp = now - hour,
                sourceId = "cgm",
                confidence = ConfidenceTier.HIGH.level,
            ),
        )
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        for (c in cards) {
            assertEquals(0.0, c.currentAmountMg, 1e-9)
            assertNull(c.lastDoseTimestampMs)
        }
    }

    @Test
    fun `card list contains the documented substances and nothing else`() {
        // Pins the shipping surface so new substances are an opt-in
        // dashboard expansion, not a silent UI surprise. Both directions:
        // the two substances are present, and no others sneak in (e.g.
        // tobacco/cannabis remain absent until dose-bearing keys ship).
        val cards = ActiveSubstancesAggregator.compute(
            intakes = emptyList(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val keys = cards.map { it.metricKey }.toSet()
        assertEquals(
            setOf(MetricType.CAFFEINE_INTAKE.key, MetricType.ALCOHOL_INTAKE.key),
            keys,
        )
    }

    @Test
    fun `time until below returns zero when already under threshold`() {
        // No intake at all → current amount is zero, which is below any
        // positive threshold. The dashboard should render "already below"
        // (timeUntilBelow = 0), not "no crossing in 10 days".
        val cards = ActiveSubstancesAggregator.compute(
            intakes = emptyList(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val caffeine = cards.first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        assertNotNull(caffeine.thresholdMg)
        assertEquals(0L, caffeine.timeUntilBelowThresholdMs)
    }

    // ---- helpers ----

    private fun caffeineIntake(timestampMs: Long, doseMg: Double, sourceId: String): MetricReading =
        MetricReading(
            metricType = MetricType.CAFFEINE_INTAKE.key,
            value = doseMg,
            timestamp = timestampMs,
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
        )

    private fun alcoholIntake(timestampMs: Long, doseG: Double, sourceId: String): MetricReading =
        MetricReading(
            metricType = MetricType.ALCOHOL_INTAKE.key,
            value = doseG,
            timestamp = timestampMs,
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
        )
}
