package com.bios.app

import com.bios.app.ui.components.metricMatchesQuery
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pin the owner-vocabulary searches a typed user expects to "just work".
 * Each row is `(query, expected metric)`; if a regression breaks any of
 * these, the search bar silently fails to surface the metric and the owner
 * thinks Bios doesn't track it.
 */
class MetricSearchTest {

    @Test
    fun owner_typed_queries_resolve_to_canonical_metrics() {
        val cases = listOf(
            "weight" to MetricType.BODY_MASS,
            "Weight" to MetricType.BODY_MASS,
            "poids" to MetricType.BODY_MASS,
            "bmi" to MetricType.BMI_KG_PER_M2,
            "imc" to MetricType.BMI_KG_PER_M2,
            "hrv" to MetricType.HEART_RATE_VARIABILITY,
            "rmssd" to MetricType.HEART_RATE_VARIABILITY,
            "bp" to MetricType.BLOOD_PRESSURE_SYSTOLIC,
            "tas" to MetricType.BLOOD_PRESSURE_SYSTOLIC,
            "tad" to MetricType.BLOOD_PRESSURE_DIASTOLIC,
            "spo2" to MetricType.BLOOD_OXYGEN,
            "saturation" to MetricType.BLOOD_OXYGEN,
            "fc" to MetricType.HEART_RATE,
            "pulse" to MetricType.HEART_RATE,
            "rhr" to MetricType.RESTING_HEART_RATE,
            "fat" to MetricType.BODY_FAT_PCT,
            "lean mass" to MetricType.LEAN_BODY_MASS_KG,
            "a1c" to MetricType.HBA1C,
            "crp" to MetricType.HSCRP,
            "apob" to MetricType.APO_B,
            "ldl" to MetricType.LDL_CHOLESTEROL,
            "hdl" to MetricType.HDL_CHOLESTEROL,
        )
        for ((query, expected) in cases) {
            assertTrue(
                "search '$query' must find ${expected.key}",
                metricMatchesQuery(expected, query)
            )
        }
    }

    @Test
    fun empty_query_matches_nothing() {
        for (m in MetricType.entries) {
            assertEquals(false, metricMatchesQuery(m, ""))
            assertEquals(false, metricMatchesQuery(m, "   "))
        }
    }

    @Test
    fun substring_in_readable_name_still_matches() {
        // Synonym map is additive, not a replacement — the existing
        // readableName/key fallthrough must keep working.
        assertTrue(metricMatchesQuery(MetricType.HEART_RATE, "heart"))
        assertTrue(metricMatchesQuery(MetricType.HEART_RATE_VARIABILITY, "variability"))
        assertTrue(metricMatchesQuery(MetricType.SKIN_TEMPERATURE, "skin"))
    }
}
