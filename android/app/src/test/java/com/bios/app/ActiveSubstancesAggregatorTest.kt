package com.bios.app

import com.bios.app.engine.EliminationKinetics
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.ui.intake.ActiveSubstancesAggregator
import com.bios.app.ui.intake.ActiveSubstancesAggregator.DisplayMode
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-state tests for the "what's still in your body" aggregator (#138).
 *
 * Exercises both display modes — DOSED tiles (caffeine, alcohol) with
 * concentration math, and EVENT_ONLY tiles (tobacco, cannabis) with the
 * event log only. Pins the shipping substance set so future expansions
 * land as deliberate edits.
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
        val keys = cards.map { it.metricKey }.toSet()
        assertTrue("caffeine card always present", MetricType.CAFFEINE_INTAKE.key in keys)
        assertTrue("alcohol card always present", MetricType.ALCOHOL_INTAKE.key in keys)
        assertTrue("tobacco event card always present", MetricType.TOBACCO_USE.key in keys)
        assertTrue("cannabis event card always present", MetricType.CANNABIS_USE.key in keys)
        for (c in cards) {
            assertNull("no last dose when there are no intakes", c.lastDoseTimestampMs)
            assertNull(c.lastDoseLabel)
            assertTrue("no recent doses to display", c.recentDoses.isEmpty())
            assertEquals(false, c.hasRecentIntake)
            when (c.displayMode) {
                DisplayMode.DOSED -> assertEquals(0.0, c.currentAmountMg!!, 1e-9)
                DisplayMode.EVENT_ONLY -> {
                    assertNull("event-only cards carry no current-amount", c.currentAmountMg)
                    assertNull("event-only cards carry no PK profile", c.pk)
                    assertTrue("event-only cards carry no curve", c.curve.isEmpty())
                    assertNull(c.thresholdMg)
                }
            }
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
        assertEquals(DisplayMode.DOSED, caffeine.displayMode)
        assertEquals(true, caffeine.hasRecentIntake)
        assertTrue(
            "current amount ${caffeine.currentAmountMg} should be near 152 mg",
            caffeine.currentAmountMg!! in 145.0..158.0
        )
        assertEquals("W2F", caffeine.lastDoseLabel)
        assertEquals(now - 2L * hour, caffeine.lastDoseTimestampMs)
    }

    @Test
    fun `older doses outside the window still inform current amount`() {
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
            caffeine.currentAmountMg!! in 55.0..75.0
        )
    }

    @Test
    fun `intakes 2 days old contribute to current concentration but not recent-doses list`() {
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
        val intakes = listOf(
            alcoholIntake(timestampMs = now - hour, doseG = 14.0, sourceId = "src"),
        )
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val alcohol = cards.first { it.metricKey == MetricType.ALCOHOL_INTAKE.key }
        assertEquals(EliminationKinetics.ZERO_ORDER, alcohol.pk!!.kinetics)
        assertTrue(
            "alcohol on-board after 1h should be ~5600 mg, got ${alcohol.currentAmountMg}",
            alcohol.currentAmountMg!! in 5_400.0..5_800.0
        )
        // Display side: 14 g entered → 14000 mg in the dose-event stream.
        assertEquals(14_000.0, alcohol.recentDoses.first().doseMg!!, 1.0)
    }

    @Test
    fun `threshold override beats the substance default`() {
        val intakes = listOf(caffeineIntake(timestampMs = now - hour, doseMg = 200.0, sourceId = "src"))
        val withDefault = ActiveSubstancesAggregator.compute(
            intakes = intakes, sourceLabels = emptyMap(), nowMs = now,
        ).first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        assertEquals(50.0, withDefault.thresholdMg!!, 1e-9)

        val withOverride = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = emptyMap(),
            nowMs = now,
            thresholdOverridesMg = mapOf(MetricType.CAFFEINE_INTAKE.key to 25.0),
        ).first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        assertEquals(25.0, withOverride.thresholdMg!!, 1e-9)
        assertTrue(
            "tighter threshold should push the crossing later " +
                "(default=${withDefault.timeUntilBelowThresholdMs}, " +
                "override=${withOverride.timeUntilBelowThresholdMs})",
            (withOverride.timeUntilBelowThresholdMs ?: 0L) >
                (withDefault.timeUntilBelowThresholdMs ?: 0L)
        )
    }

    @Test
    fun `curve points span the configured window for dosed substances`() {
        val intakes = listOf(caffeineIntake(timestampMs = now - hour, doseMg = 100.0, sourceId = "src"))
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val caffeine = cards.first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        assertEquals(289, caffeine.curve.size)
        assertEquals(now - ActiveSubstancesAggregator.DEFAULT_WINDOW_MS, caffeine.curve.first().first)
        assertEquals(now, caffeine.curve.last().first)
    }

    @Test
    fun `unknown metric_type readings do not pollute substance cards`() {
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
            assertNull(c.lastDoseTimestampMs)
            assertTrue(c.recentDoses.isEmpty())
            if (c.displayMode == DisplayMode.DOSED) {
                assertEquals(0.0, c.currentAmountMg!!, 1e-9)
            }
        }
    }

    @Test
    fun `card list pins the shipping substance set`() {
        // Adding a substance is a deliberate dashboard expansion. The test
        // catches drift on the EVENT_ONLY side too — Smokeless event keys
        // must keep rendering until a dose-bearing companion key replaces
        // them.
        val cards = ActiveSubstancesAggregator.compute(
            intakes = emptyList(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val keys = cards.map { it.metricKey }.toSet()
        assertEquals(
            setOf(
                MetricType.CAFFEINE_INTAKE.key,
                MetricType.ALCOHOL_INTAKE.key,
                MetricType.TOBACCO_USE.key,
                MetricType.CANNABIS_USE.key,
            ),
            keys,
        )
    }

    @Test
    fun `time until below returns zero when dosed card has no intake`() {
        // Zero amount is below any positive threshold → the card renders
        // "already below" instead of "no crossing".
        val cards = ActiveSubstancesAggregator.compute(
            intakes = emptyList(),
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val caffeine = cards.first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        assertNotNull(caffeine.thresholdMg)
        assertEquals(0L, caffeine.timeUntilBelowThresholdMs)
    }

    // ---- event-only tiles (tobacco / cannabis) ----

    @Test
    fun `cannabis_use event surfaces on the cannabis event-only tile`() {
        // The user-reported bug: a Smokeless-logged cannabis_use event must
        // appear on the dashboard. Engine math is skipped (no dose) but
        // the event log is the whole point.
        val intakes = listOf(
            eventOnly(
                metricKey = MetricType.CANNABIS_USE.key,
                timestampMs = now - 30L * 60L * 1000L,
                sourceId = "smokeless",
            ),
        )
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = mapOf("smokeless" to "Smokeless"),
            nowMs = now,
        )
        val cannabis = cards.first { it.metricKey == MetricType.CANNABIS_USE.key }
        assertEquals(DisplayMode.EVENT_ONLY, cannabis.displayMode)
        assertEquals(true, cannabis.hasRecentIntake)
        assertEquals(now - 30L * 60L * 1000L, cannabis.lastDoseTimestampMs)
        assertEquals("Smokeless", cannabis.lastDoseLabel)
        assertEquals(1, cannabis.recentDoses.size)
        assertNull("event-only dose has no mg recorded", cannabis.recentDoses.first().doseMg)
        // No concentration math because there's nothing to integrate.
        assertNull(cannabis.currentAmountMg)
        assertNull(cannabis.pk)
        assertNull(cannabis.thresholdMg)
        assertTrue(cannabis.curve.isEmpty())
    }

    @Test
    fun `tobacco_use events accumulate as multiple recent events`() {
        val intakes = listOf(
            eventOnly(MetricType.TOBACCO_USE.key, now - 4L * hour, "smokeless"),
            eventOnly(MetricType.TOBACCO_USE.key, now - 2L * hour, "smokeless"),
            eventOnly(MetricType.TOBACCO_USE.key, now - 30L * 60L * 1000L, "smokeless"),
        )
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = mapOf("smokeless" to "Smokeless"),
            nowMs = now,
        )
        val tobacco = cards.first { it.metricKey == MetricType.TOBACCO_USE.key }
        assertEquals(3, tobacco.recentDoses.size)
        // Latest event timestamp wins for last-dose.
        assertEquals(now - 30L * 60L * 1000L, tobacco.lastDoseTimestampMs)
    }

    @Test
    fun `event-only intakes older than the window are excluded from recent list`() {
        val intakes = listOf(
            eventOnly(MetricType.CANNABIS_USE.key, now - 48L * hour, "smokeless"),
            eventOnly(MetricType.CANNABIS_USE.key, now - hour, "smokeless"),
        )
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = mapOf("smokeless" to "Smokeless"),
            nowMs = now,
        )
        val cannabis = cards.first { it.metricKey == MetricType.CANNABIS_USE.key }
        assertEquals(
            "24h window should drop the 48h-old event from recent-doses",
            1, cannabis.recentDoses.size,
        )
        // But last-dose still tracks the most recent event inside the window.
        assertEquals(now - hour, cannabis.lastDoseTimestampMs)
    }

    @Test
    fun `cannabis events do not pollute caffeine or alcohol tiles`() {
        val intakes = listOf(eventOnly(MetricType.CANNABIS_USE.key, now - hour, "smokeless"))
        val cards = ActiveSubstancesAggregator.compute(
            intakes = intakes,
            sourceLabels = emptyMap(),
            nowMs = now,
        )
        val caffeine = cards.first { it.metricKey == MetricType.CAFFEINE_INTAKE.key }
        val alcohol = cards.first { it.metricKey == MetricType.ALCOHOL_INTAKE.key }
        assertEquals(0.0, caffeine.currentAmountMg!!, 1e-9)
        assertEquals(0.0, alcohol.currentAmountMg!!, 1e-9)
        assertTrue(caffeine.recentDoses.isEmpty())
        assertTrue(alcohol.recentDoses.isEmpty())
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

    private fun eventOnly(metricKey: String, timestampMs: Long, sourceId: String): MetricReading =
        MetricReading(
            metricType = metricKey,
            value = 1.0, // event marker
            timestamp = timestampMs,
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level,
        )
}
