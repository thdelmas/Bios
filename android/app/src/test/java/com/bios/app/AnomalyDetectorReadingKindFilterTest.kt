package com.bios.app

import com.bios.app.engine.isReproductiveMetric
import com.bios.app.engine.readingKindFilterFor
import com.bios.app.model.ReadingKind
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the SENSOR-only filter to non-EVENT metrics. Regression guard for the
 * mistake in PR #25 where every fetch hardcoded SENSOR, which silently zeroed
 * out the tobacco_use / fall_event / check_in_miss signal rules in
 * CompanionConditionPatterns — those events are written by SELF_REPORTED or
 * DERIVED companion sources, so filtering to SENSOR returned no rows.
 */
class AnomalyDetectorReadingKindFilterTest {

    @Test
    fun `EVENT-unit metrics are not SENSOR-filtered`() {
        // Sweep every EVENT-unit MetricType the contract defines — every
        // companion-written event surface must read through to its rows.
        val eventMetrics = MetricType.entries.filter { it.unit == MetricUnit.EVENT }
        assertEquals(
            "If this drops to 0, MetricUnit.EVENT is no longer load-bearing — re-check the contract.",
            true, eventMetrics.isNotEmpty()
        )
        for (metric in eventMetrics) {
            assertNull(
                "${metric.key} is an EVENT — readingKindFilterFor must return null to admit companion writes",
                readingKindFilterFor(metric)
            )
        }
    }

    @Test
    fun `cardiovascular and other sensor metrics resolve to SENSOR`() {
        val sensorMetrics = listOf(
            MetricType.HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.RESTING_HEART_RATE,
            MetricType.BLOOD_OXYGEN,
            MetricType.RESPIRATORY_RATE,
            MetricType.SKIN_TEMPERATURE,
            MetricType.SLEEP_DURATION,
        )
        for (metric in sensorMetrics) {
            assertEquals(
                "${metric.key} is sensor data — must filter to SENSOR to keep baselines uncorrupted",
                ReadingKind.SENSOR.name,
                readingKindFilterFor(metric)
            )
        }
    }

    @Test
    fun `WOMENS_HEALTH metrics are not SENSOR-filtered`() {
        // Reproductive readings live in the isolated ReproductiveDatabase and
        // are SELF_REPORTED by design — the manual BBT entry surface is the
        // only writer. Filtering to SENSOR would zero every row.
        val reproductiveMetrics =
            MetricType.entries.filter { it.domain == MetricDomain.WOMENS_HEALTH }
        assertTrue(
            "MetricDomain.WOMENS_HEALTH should have at least one metric (BBT, CYCLE_PHASE, etc.)",
            reproductiveMetrics.isNotEmpty()
        )
        for (metric in reproductiveMetrics) {
            assertNull(
                "${metric.key} is WOMENS_HEALTH — readingKindFilterFor must return null so the SELF_REPORTED BBT rows in ReproductiveDatabase resolve",
                readingKindFilterFor(metric)
            )
        }
    }

    @Test
    fun `isReproductiveMetric tracks WOMENS_HEALTH domain`() {
        // Every WOMENS_HEALTH metric routes to the reproductive DB; nothing
        // else does. Pinning the predicate to the domain (not a hand-rolled
        // key allowlist) means new reproductive keys auto-route correctly.
        for (metric in MetricType.entries) {
            val expected = metric.domain == MetricDomain.WOMENS_HEALTH
            assertEquals(
                "${metric.key}: isReproductiveMetric should match WOMENS_HEALTH domain membership",
                expected,
                isReproductiveMetric(metric)
            )
        }

        // Spot-check the keys that exist today so a future domain rename
        // doesn't silently break routing.
        assertTrue(isReproductiveMetric(MetricType.BASAL_BODY_TEMPERATURE))
        assertTrue(isReproductiveMetric(MetricType.CYCLE_PHASE))
        assertTrue(isReproductiveMetric(MetricType.CYCLE_DAY))
        assertTrue(isReproductiveMetric(MetricType.MENSTRUATION_ONSET))
        assertFalse(isReproductiveMetric(MetricType.HEART_RATE))
        assertFalse(isReproductiveMetric(MetricType.SLEEP_DURATION))
    }
}
