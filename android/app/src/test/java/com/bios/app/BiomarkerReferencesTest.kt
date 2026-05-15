package com.bios.app

import com.bios.app.alerts.BiomarkerReferences
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the direct-vs-proxy plumbing on [BiomarkerReferences].
 *
 * After §8.6 shipped HSCRP and HBA1C as canonical lab keys, the inflammation
 * and metabolic-health references should know about their direct readings —
 * so the longevity screen surfaces "Direct lab reading" status instead of
 * only describing the wearable proxy. These tests pin that wiring.
 */
class BiomarkerReferencesTest {

    @Test
    fun hsCRP_reference_points_at_HSCRP_as_its_direct_metric() {
        assertEquals(MetricType.HSCRP, BiomarkerReferences.inflammation.directMetric)
    }

    @Test
    fun hba1c_reference_points_at_HBA1C_as_its_direct_metric() {
        assertEquals(MetricType.HBA1C, BiomarkerReferences.metabolicHealth.directMetric)
    }

    @Test
    fun references_without_a_canonical_lab_key_leave_directMetric_null() {
        // VO2 max, arterial stiffness, cortisol, body composition, sleep —
        // none of these have a Bios biomarker key today.
        val withoutDirect = setOf(
            BiomarkerReferences.cardiorespFitness,
            BiomarkerReferences.arterialHealth,
            BiomarkerReferences.stressLoad,
            BiomarkerReferences.bodyComposition,
            BiomarkerReferences.sleepArchitecture,
        )
        for (ref in withoutDirect) {
            assertNull("${ref.id} should have no directMetric until a lab key is shipped", ref.directMetric)
        }
    }

    @Test
    fun forMetric_returns_the_reference_when_queried_by_direct_metric() {
        val refs = BiomarkerReferences.forMetric(MetricType.HSCRP)
        assertTrue(
            "HSCRP should resolve at least to the inflammation reference",
            BiomarkerReferences.inflammation in refs
        )
    }

    @Test
    fun forMetric_still_returns_references_by_proxy_metric() {
        // RESTING_HEART_RATE proxies multiple biomarkers; the lookup should
        // continue to find every reference that lists it as a proxy.
        val refs = BiomarkerReferences.forMetric(MetricType.RESTING_HEART_RATE)
        assertTrue(BiomarkerReferences.inflammation in refs)
        assertTrue(BiomarkerReferences.metabolicHealth in refs)
        assertTrue(BiomarkerReferences.cardiorespFitness in refs)
    }
}
