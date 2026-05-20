package com.bios.app

import com.bios.app.engine.EliminationKinetics
import com.bios.app.engine.PharmacokineticsReference
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrity checks on the shipped substance table (#136).
 *
 * The reference data is slow-rolling but every entry powers concentration
 * math the owner sees. These tests catch the failure modes that show up
 * silently — a FIRST_ORDER substance missing a half-life, a metric key
 * pointing at a substance the table no longer knows, a bioavailability
 * outside [0, 1], etc.
 */
class PharmacokineticsReferenceTest {

    @Test
    fun every_substance_has_a_citation() {
        for (pk in PharmacokineticsReference.all.values) {
            assertTrue(
                "substance ${pk.substanceKey} must carry a non-empty citation",
                pk.source.isNotBlank()
            )
        }
    }

    @Test
    fun first_order_substances_have_a_positive_half_life() {
        val firstOrder = PharmacokineticsReference.all.values
            .filter { it.kinetics == EliminationKinetics.FIRST_ORDER }
        assertTrue("expected ≥ 1 FIRST_ORDER substance shipped", firstOrder.isNotEmpty())
        for (pk in firstOrder) {
            assertTrue(
                "FIRST_ORDER substance ${pk.substanceKey} must have halfLifeMinutes > 0",
                pk.halfLifeMinutes > 0.0
            )
        }
    }

    @Test
    fun zero_order_substances_have_a_positive_elimination_rate() {
        val zeroOrder = PharmacokineticsReference.all.values
            .filter { it.kinetics == EliminationKinetics.ZERO_ORDER }
        assertTrue("expected ≥ 1 ZERO_ORDER substance shipped (alcohol)", zeroOrder.isNotEmpty())
        for (pk in zeroOrder) {
            assertTrue(
                "ZERO_ORDER substance ${pk.substanceKey} must have a positive elimination rate",
                pk.zeroOrderRateMgPerMin > 0.0
            )
        }
    }

    @Test
    fun bioavailability_is_a_fraction() {
        for (pk in PharmacokineticsReference.all.values) {
            assertTrue(
                "bioavailability ${pk.bioavailability} for ${pk.substanceKey} must be in (0, 1]",
                pk.bioavailability > 0.0 && pk.bioavailability <= 1.0
            )
        }
    }

    @Test
    fun absorption_half_life_is_non_negative() {
        for (pk in PharmacokineticsReference.all.values) {
            assertTrue(
                "absorption half-life for ${pk.substanceKey} must be ≥ 0",
                pk.absorptionHalfLifeMinutes >= 0.0
            )
        }
    }

    @Test
    fun caffeine_intake_maps_to_a_known_substance() {
        val substanceKey = PharmacokineticsReference.substanceKeyForMetric(
            MetricType.CAFFEINE_INTAKE.key
        )
        assertNotNull("caffeine_intake must map to a substance key", substanceKey)
        assertNotNull(
            "caffeine substance must be in the table",
            PharmacokineticsReference.all[substanceKey!!]
        )
    }

    @Test
    fun alcohol_intake_maps_to_the_zero_order_substance() {
        val substanceKey = PharmacokineticsReference.substanceKeyForMetric(
            MetricType.ALCOHOL_INTAKE.key
        )
        assertNotNull(substanceKey)
        val pk = PharmacokineticsReference.all[substanceKey!!]
        assertNotNull(pk)
        assertEquals(EliminationKinetics.ZERO_ORDER, pk!!.kinetics)
    }

    @Test
    fun medication_intake_has_no_default_substance() {
        // Generic medication_intake requires per-event substance_key in
        // event_payloads; the static mapping must not silently route it
        // to a single drug.
        assertNull(
            PharmacokineticsReference.substanceKeyForMetric(MetricType.MEDICATION_INTAKE.key)
        )
    }
}
