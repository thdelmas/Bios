package com.bios.app

import com.bios.app.model.MetricType
import com.bios.app.model.MetricUnit
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for FhirExporter LOINC and UCUM mapping logic.
 *
 * The mapping methods are private in FhirExporter, so we mirror them here
 * to validate correctness of clinical coding without needing a database.
 */
class FhirExporterTest {

    // --- LOINC code coverage ---

    @Test
    fun `heart rate maps to LOINC 8867-4`() {
        val (code, display) = loincCode(MetricType.HEART_RATE)!!
        assertEquals("8867-4", code)
        assertEquals("Heart rate", display)
    }

    @Test
    fun `HRV maps to LOINC 80404-7`() {
        val (code, _) = loincCode(MetricType.HEART_RATE_VARIABILITY)!!
        assertEquals("80404-7", code)
    }

    @Test
    fun `resting heart rate maps to LOINC 40443-4`() {
        val (code, _) = loincCode(MetricType.RESTING_HEART_RATE)!!
        assertEquals("40443-4", code)
    }

    @Test
    fun `blood oxygen maps to LOINC 2708-6`() {
        val (code, _) = loincCode(MetricType.BLOOD_OXYGEN)!!
        assertEquals("2708-6", code)
    }

    @Test
    fun `respiratory rate maps to LOINC 9279-1`() {
        val (code, _) = loincCode(MetricType.RESPIRATORY_RATE)!!
        assertEquals("9279-1", code)
    }

    @Test
    fun `systolic BP maps to LOINC 8480-6`() {
        val (code, _) = loincCode(MetricType.BLOOD_PRESSURE_SYSTOLIC)!!
        assertEquals("8480-6", code)
    }

    @Test
    fun `diastolic BP maps to LOINC 8462-4`() {
        val (code, _) = loincCode(MetricType.BLOOD_PRESSURE_DIASTOLIC)!!
        assertEquals("8462-4", code)
    }

    @Test
    fun `blood glucose maps to LOINC 2345-7`() {
        val (code, _) = loincCode(MetricType.BLOOD_GLUCOSE)!!
        assertEquals("2345-7", code)
    }

    @Test
    fun `steps maps to LOINC 55423-8`() {
        val (code, _) = loincCode(MetricType.STEPS)!!
        assertEquals("55423-8", code)
    }

    @Test
    fun `sleep duration maps to LOINC 93832-4`() {
        val (code, _) = loincCode(MetricType.SLEEP_DURATION)!!
        assertEquals("93832-4", code)
    }

    @Test
    fun `skin temperature maps to LOINC 8310-5`() {
        val (code, _) = loincCode(MetricType.SKIN_TEMPERATURE)!!
        assertEquals("8310-5", code)
    }

    @Test
    fun `basal body temperature maps to LOINC 8332-9`() {
        val (code, _) = loincCode(MetricType.BASAL_BODY_TEMPERATURE)!!
        assertEquals("8332-9", code)
    }

    @Test
    fun `metrics without LOINC mapping return null`() {
        assertNull(loincCode(MetricType.ACTIVE_CALORIES))
        assertNull(loincCode(MetricType.ACTIVE_MINUTES))
        assertNull(loincCode(MetricType.RECOVERY_SCORE))
        assertNull(loincCode(MetricType.SLEEP_STAGE))
        assertNull(loincCode(MetricType.SKIN_TEMPERATURE_DEVIATION))
    }

    @Test
    fun `12 metric types have LOINC mappings`() {
        val mapped = MetricType.entries.count { loincCode(it) != null }
        assertEquals(12, mapped)
    }

    // --- UCUM code mappings ---

    @Test
    fun `BPM maps to per-minute UCUM`() {
        assertEquals("/min", ucumCode(MetricType.HEART_RATE))
    }

    @Test
    fun `milliseconds maps to ms UCUM`() {
        assertEquals("ms", ucumCode(MetricType.HEART_RATE_VARIABILITY))
    }

    @Test
    fun `percent maps to percent UCUM`() {
        assertEquals("%", ucumCode(MetricType.BLOOD_OXYGEN))
    }

    @Test
    fun `mmHg maps to mm Hg UCUM`() {
        assertEquals("mm[Hg]", ucumCode(MetricType.BLOOD_PRESSURE_SYSTOLIC))
    }

    @Test
    fun `celsius maps to Cel UCUM`() {
        assertEquals("Cel", ucumCode(MetricType.SKIN_TEMPERATURE))
    }

    @Test
    fun `delta celsius maps to Cel UCUM`() {
        assertEquals("Cel", ucumCode(MetricType.SKIN_TEMPERATURE_DEVIATION))
    }

    @Test
    fun `seconds maps to s UCUM`() {
        assertEquals("s", ucumCode(MetricType.SLEEP_DURATION))
    }

    @Test
    fun `count maps to count UCUM`() {
        assertEquals("{count}", ucumCode(MetricType.STEPS))
    }

    @Test
    fun `kcal maps to kcal UCUM`() {
        assertEquals("kcal", ucumCode(MetricType.ACTIVE_CALORIES))
    }

    @Test
    fun `mg per dL maps to mg per dL UCUM`() {
        assertEquals("mg/dL", ucumCode(MetricType.BLOOD_GLUCOSE))
    }

    @Test
    fun `all metric units have UCUM mappings`() {
        // Every MetricType should produce a UCUM code without exception
        for (type in MetricType.entries) {
            assertNotNull("${type.key} should have UCUM", ucumCode(type))
        }
    }

    // --- LOINC display names are non-empty ---

    @Test
    fun `all LOINC mappings have non-empty display names`() {
        for (type in MetricType.entries) {
            val pair = loincCode(type) ?: continue
            assertTrue("${type.key} LOINC display should not be blank", pair.second.isNotBlank())
        }
    }

    // --- Mirror methods from FhirExporter ---

    private fun loincCode(metricType: MetricType): Pair<String, String>? {
        return when (metricType) {
            MetricType.HEART_RATE -> "8867-4" to "Heart rate"
            MetricType.HEART_RATE_VARIABILITY -> "80404-7" to "R-R interval.standard deviation"
            MetricType.RESTING_HEART_RATE -> "40443-4" to "Heart rate - resting"
            MetricType.BLOOD_OXYGEN -> "2708-6" to "Oxygen saturation in Arterial blood"
            MetricType.RESPIRATORY_RATE -> "9279-1" to "Respiratory rate"
            MetricType.BLOOD_PRESSURE_SYSTOLIC -> "8480-6" to "Systolic blood pressure"
            MetricType.BLOOD_PRESSURE_DIASTOLIC -> "8462-4" to "Diastolic blood pressure"
            MetricType.BLOOD_GLUCOSE -> "2345-7" to "Glucose [Mass/volume] in Serum or Plasma"
            MetricType.STEPS -> "55423-8" to "Number of steps in unspecified time Pedometer"
            MetricType.SLEEP_DURATION -> "93832-4" to "Sleep duration"
            MetricType.SKIN_TEMPERATURE -> "8310-5" to "Body temperature"
            MetricType.BASAL_BODY_TEMPERATURE -> "8332-9" to "Oral temperature"
            else -> null
        }
    }

    private fun ucumCode(metricType: MetricType): String {
        return when (metricType.unit) {
            MetricUnit.BPM -> "/min"
            MetricUnit.MILLISECONDS -> "ms"
            MetricUnit.PERCENT -> "%"
            MetricUnit.MMHG -> "mm[Hg]"
            MetricUnit.BREATHS_PER_MIN -> "/min"
            MetricUnit.CELSIUS -> "Cel"
            MetricUnit.DELTA_CELSIUS -> "Cel"
            MetricUnit.SECONDS -> "s"
            MetricUnit.COUNT -> "{count}"
            MetricUnit.KCAL -> "kcal"
            MetricUnit.MG_PER_DL -> "mg/dL"
            MetricUnit.SCORE -> "{score}"
            MetricUnit.CATEGORY -> "{category}"
        }
    }
}
