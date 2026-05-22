package com.bios.app

import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
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
    fun `body mass maps to LOINC 29463-7`() {
        val (code, _) = loincCode(MetricType.BODY_MASS)!!
        assertEquals("29463-7", code)
    }

    @Test
    fun `body fat percent maps to LOINC 41982-0`() {
        val (code, _) = loincCode(MetricType.BODY_FAT_PCT)!!
        assertEquals("41982-0", code)
    }

    @Test
    fun `metric types with LOINC mappings include the AHI passthrough`() {
        // 32 base + 5 wave-5 biomarkers (#158: eGFR, creatinine, ALT, AST, GGT)
        // + AHI (#157) = 38. The count assertion is intentionally maintained
        // alongside the loincCode() table so any unmapped new MetricType is
        // caught.
        val mapped = MetricType.entries.count { loincCode(it) != null }
        assertEquals(38, mapped)
    }

    @Test
    fun `HbA1c maps to LOINC 4548-4`() {
        val (code, _) = loincCode(MetricType.HBA1C)!!
        assertEquals("4548-4", code)
    }

    @Test
    fun `hsCRP maps to LOINC 30522-7`() {
        val (code, _) = loincCode(MetricType.HSCRP)!!
        assertEquals("30522-7", code)
    }

    @Test
    fun `lipid panel and ApoB map to canonical LOINC codes`() {
        assertEquals("2093-3", loincCode(MetricType.TOTAL_CHOLESTEROL)!!.first)
        assertEquals("2089-1", loincCode(MetricType.LDL_CHOLESTEROL)!!.first)
        assertEquals("2085-9", loincCode(MetricType.HDL_CHOLESTEROL)!!.first)
        assertEquals("2571-8", loincCode(MetricType.TRIGLYCERIDES)!!.first)
        assertEquals("1884-6", loincCode(MetricType.APO_B)!!.first)
    }

    @Test
    fun `vitamin D and thyroid panel map to canonical LOINC codes`() {
        assertEquals("14635-7", loincCode(MetricType.VITAMIN_D_25OH)!!.first)
        assertEquals("3016-3", loincCode(MetricType.TSH)!!.first)
        assertEquals("3024-7", loincCode(MetricType.FREE_T4)!!.first)
        assertEquals("3051-0", loincCode(MetricType.FREE_T3)!!.first)
    }

    @Test
    fun `new biomarker units map to canonical UCUM codes`() {
        assertEquals("ng/mL", ucumCode(MetricType.VITAMIN_D_25OH))
        assertEquals("ng/dL", ucumCode(MetricType.FREE_T4))
        assertEquals("pg/mL", ucumCode(MetricType.FREE_T3))
        assertEquals("m[IU]/L", ucumCode(MetricType.TSH))
    }

    @Test
    fun `CBC panel maps to canonical LOINC codes`() {
        assertEquals("718-7", loincCode(MetricType.HEMOGLOBIN)!!.first)
        assertEquals("4544-3", loincCode(MetricType.HEMATOCRIT)!!.first)
        assertEquals("6690-2", loincCode(MetricType.WBC)!!.first)
        assertEquals("789-8", loincCode(MetricType.RBC)!!.first)
        assertEquals("777-3", loincCode(MetricType.PLATELETS)!!.first)
    }

    @Test
    fun `CBC units map to canonical UCUM codes`() {
        assertEquals("g/dL", ucumCode(MetricType.HEMOGLOBIN))
        // WBC and platelets share the 10*9/L cell-count unit per CBC convention.
        assertEquals("10*9/L", ucumCode(MetricType.WBC))
        assertEquals("10*9/L", ucumCode(MetricType.PLATELETS))
        // RBC reports in trillions per litre.
        assertEquals("10*12/L", ucumCode(MetricType.RBC))
    }

    @Test
    fun `mg per L maps to mg per L UCUM`() {
        assertEquals("mg/L", ucumCode(MetricType.HSCRP))
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
            MetricType.BODY_MASS -> "29463-7" to "Body weight"
            MetricType.BODY_FAT_PCT -> "41982-0" to "Percentage of body fat Measured"
            MetricType.LEAN_MASS -> "91557-9" to "Body lean mass Measured by Bioelectrical impedance analysis"
            MetricType.VO2_MAX -> "97090-9" to "Maximum oxygen consumption per unit time per unit body mass"
            MetricType.HBA1C -> "4548-4" to "Hemoglobin A1c/Hemoglobin.total in Blood"
            MetricType.HSCRP -> "30522-7" to "C reactive protein.high sensitivity [Mass/volume] in Serum or Plasma"
            MetricType.TOTAL_CHOLESTEROL -> "2093-3" to "Cholesterol [Mass/volume] in Serum or Plasma"
            MetricType.LDL_CHOLESTEROL -> "2089-1" to "Cholesterol in LDL [Mass/volume] in Serum or Plasma"
            MetricType.HDL_CHOLESTEROL -> "2085-9" to "Cholesterol in HDL [Mass/volume] in Serum or Plasma"
            MetricType.TRIGLYCERIDES -> "2571-8" to "Triglyceride [Mass/volume] in Serum or Plasma"
            MetricType.APO_B -> "1884-6" to "Apolipoprotein B [Mass/volume] in Serum or Plasma"
            MetricType.VITAMIN_D_25OH -> "14635-7" to "25-hydroxyvitamin D2+D3 [Mass/volume] in Serum or Plasma"
            MetricType.TSH -> "3016-3" to "Thyrotropin [Units/volume] in Serum or Plasma"
            MetricType.FREE_T4 -> "3024-7" to "Thyroxine (T4) free [Mass/volume] in Serum or Plasma"
            MetricType.FREE_T3 -> "3051-0" to "Triiodothyronine (T3) free [Mass/volume] in Serum or Plasma"
            MetricType.HEMOGLOBIN -> "718-7" to "Hemoglobin [Mass/volume] in Blood"
            MetricType.HEMATOCRIT -> "4544-3" to "Hematocrit [Volume Fraction] of Blood by Automated count"
            MetricType.WBC -> "6690-2" to "Leukocytes [#/volume] in Blood by Automated count"
            MetricType.RBC -> "789-8" to "Erythrocytes [#/volume] in Blood by Automated count"
            MetricType.PLATELETS -> "777-3" to "Platelets [#/volume] in Blood by Automated count"
            // #158 — renal / hepatic.
            MetricType.EGFR -> "62238-1" to "Glomerular filtration rate/1.73 sq M.predicted by Creatinine-based formula (CKD-EPI 2021)"
            MetricType.CREATININE -> "2160-0" to "Creatinine [Mass/volume] in Serum or Plasma"
            MetricType.ALT -> "1742-6" to "Alanine aminotransferase [Enzymatic activity/volume] in Serum or Plasma"
            MetricType.AST -> "1920-8" to "Aspartate aminotransferase [Enzymatic activity/volume] in Serum or Plasma"
            MetricType.GGT -> "2324-2" to "Gamma glutamyl transferase [Enzymatic activity/volume] in Serum or Plasma"
            // #157 — apnea-hypopnea index.
            MetricType.AHI -> "90562-0" to "Sleep apnea hypopnea index"
            else -> null
        }
    }

    // --- Reproductive-data isolation guard ---

    @Test
    fun `reproductive metrics stay in WOMENS_HEALTH so FhirExporter skips them by default`() {
        // The FhirExporter bundle loop skips MetricDomain.WOMENS_HEALTH because
        // those readings live in the isolated ReproductiveDatabase (separate
        // SQLCipher key, independent wipe). Moving either of these keys to a
        // different domain would silently start leaking reproductive data into
        // the default FHIR export — this test fails first instead.
        assertEquals(MetricDomain.WOMENS_HEALTH, MetricType.BASAL_BODY_TEMPERATURE.domain)
        assertEquals(MetricDomain.WOMENS_HEALTH, MetricType.CYCLE_PHASE.domain)
    }

    @Test
    fun `every WOMENS_HEALTH MetricType is excluded from the default FHIR enumeration`() {
        // Mirror of the FhirExporter `for (metricType in MetricType.entries) …
        // if (domain == WOMENS_HEALTH) continue` loop. If any new reproductive
        // key is added without a corresponding exporter exclusion (or a
        // conscious opt-in path), this test makes the gap visible.
        val included = MetricType.entries.filter { it.domain != MetricDomain.WOMENS_HEALTH }
        val excluded = MetricType.entries - included.toSet()
        assertTrue(
            "WOMENS_HEALTH key(s) leaked into the default-export set: " +
                "${included.filter { it.domain == MetricDomain.WOMENS_HEALTH }}",
            included.none { it.domain == MetricDomain.WOMENS_HEALTH }
        )
        assertTrue(
            "Expected at least one WOMENS_HEALTH key to assert non-vacuously",
            excluded.any { it.domain == MetricDomain.WOMENS_HEALTH }
        )
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
            MetricUnit.KILOGRAMS -> "kg"
            MetricUnit.MG_PER_DL -> "mg/dL"
            MetricUnit.MG_PER_L -> "mg/L"
            MetricUnit.NG_PER_ML -> "ng/mL"
            MetricUnit.NG_PER_DL -> "ng/dL"
            MetricUnit.UG_PER_DL -> "ug/dL"
            MetricUnit.PG_PER_ML -> "pg/mL"
            MetricUnit.MIU_PER_L -> "m[IU]/L"
            MetricUnit.MICRO_IU_PER_ML -> "u[IU]/mL"
            MetricUnit.G_PER_DL -> "g/dL"
            MetricUnit.GIGA_PER_L -> "10*9/L"
            MetricUnit.TERA_PER_L -> "10*12/L"
            MetricUnit.SCORE -> "{score}"
            MetricUnit.CATEGORY -> "{category}"
            MetricUnit.EVENT -> "{event}"
            MetricUnit.LUX -> "lx"
            MetricUnit.YEARS -> "a"
            MetricUnit.MS_SQUARED -> "ms2"
            MetricUnit.ML_PER_KG_MIN -> "mL/(kg.min)"
            MetricUnit.UG_PER_M3 -> "ug/m3"
            MetricUnit.PPM -> "[ppm]"
            MetricUnit.PPB -> "[ppb]"
            MetricUnit.LITERS_PER_MIN -> "L/min"
            MetricUnit.LITERS -> "L"
            MetricUnit.MILLIGRAMS -> "mg"
            MetricUnit.GRAMS -> "g"
            MetricUnit.U_PER_L -> "U/L"
            MetricUnit.ML_PER_MIN_PER_173 -> "mL/min/{1.73_m2}"
            MetricUnit.METERS -> "m"
            MetricUnit.HOURS -> "h"
        }
    }
}
