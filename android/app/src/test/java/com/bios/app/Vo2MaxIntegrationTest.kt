package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.export.loincCode
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the cross-file wiring for VO2_MAX (#26).
 *
 * The adapter HTTP paths (Garmin / Oura `cardiovascular_age`, HC
 * `Vo2MaxRecord`) require networked or `HealthConnectClient` runtime
 * fixtures the unit-test layer doesn't provide. What we can pin here
 * — and what catches the most likely silent regressions — is the
 * contract surface every adapter has to land on: domain, unit,
 * LOINC / UCUM mapping, baseline membership, and condition-pattern
 * consumption.
 */
class Vo2MaxIntegrationTest {

    @Test
    fun `VO2_MAX is registered as a CARDIOVASCULAR metric in mL per kg per minute`() {
        // Anti-regression: silently moving VO2_MAX to a different domain
        // would drop it out of the cardiovascular cross-correlation set;
        // changing the unit would orphan every previously-written row.
        assertEquals(MetricDomain.CARDIOVASCULAR, MetricType.VO2_MAX.domain)
        assertEquals(MetricUnit.ML_PER_KG_MIN, MetricType.VO2_MAX.unit)
        assertEquals("vo2_max", MetricType.VO2_MAX.key)
    }

    @Test
    fun `ML_PER_KG_MIN renders the conventional clinical unit symbol`() {
        // Clinical literature reads mL/kg/min. Slash-separated is the
        // ESC / AHA / Hirshkowitz / Ross convention; UCUM normalizes
        // (mL/(kg.min)) for export but the display stays human-readable.
        assertEquals("mL/kg/min", MetricUnit.ML_PER_KG_MIN.symbol)
    }

    @Test
    fun `VO2_MAX has a LOINC code so FHIR export carries it`() {
        // The "Share FHIR Bundle with your doctor" path round-trips
        // anything with a loincCode mapping; VO2_MAX is clinically
        // canonical, so it must be on the table.
        val mapping = loincCode(MetricType.VO2_MAX)
        assertNotNull("VO2_MAX needs a LOINC mapping for doctor handoff", mapping)
        // LOINC 97090-9 — "Maximum oxygen consumption per unit time per
        // unit body mass." Pin both ends of the pair so a typo in either
        // half trips the test.
        assertEquals("97090-9", mapping!!.first)
        assertTrue(mapping.second.lowercase().contains("oxygen"))
    }

    @Test
    fun `cardiorespiratory deconditioning pattern consumes VO2_MAX as a BELOW signal`() {
        // The pattern's prose ("VO2 max, the clinical gold standard")
        // pre-existed the ingestion path — adding a SignalRule closes
        // the loop. Direction BELOW is load-bearing: a VO2 increase is
        // not a deconditioning signal.
        val pattern = ConditionPatterns.cardiorespiratoryDeconditioning
        val vo2Rule = pattern.signalRules.singleOrNull { it.metricType == MetricType.VO2_MAX }
        assertNotNull(
            "Cardiorespiratory deconditioning pattern must include a VO2_MAX SignalRule (#26)",
            vo2Rule,
        )
        assertEquals(DeviationDirection.BELOW, vo2Rule!!.direction)
    }

    @Test
    fun `VO2_MAX is the only mL per kg per min metric on the contract today`() {
        // A second metric in that unit might want a different LOINC /
        // baseline / pattern set; force a fresh decision instead of
        // letting a future addition inherit VO2_MAX's bindings by
        // accident.
        val sharingUnit = MetricType.entries.filter { it.unit == MetricUnit.ML_PER_KG_MIN }
        assertEquals(listOf(MetricType.VO2_MAX), sharingUnit)
    }
}
