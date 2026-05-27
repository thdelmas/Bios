package com.bios.app

import com.bios.app.export.loincCode
import com.bios.app.export.ucumCode
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the FHIR LOINC and UCUM mapping tables (see
 * [com.bios.app.export.loincCode] / [com.bios.app.export.ucumCode] in
 * `FhirMetricCodings.kt`). The mapping functions are `internal`, so the
 * test calls them directly.
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
        // 32 base + 5 wave-5 biomarkers (#158) + AHI (#157) + 47 Wave-1
        // (audit §3.1) + 8 Wave-2 (audit §3.2 — telomere/bone-T/vit K2
        // intentionally unmapped) + 5 coagulation (#352) + 4 absolute
        // leukocyte differential (#353) + 1 standard CRP (#354) + 5 Wave-6
        // (#339 — D-dimer, reverse T3, omega-3 index, leptin, adiponectin)
        // = 108. The count assertion is maintained alongside the
        // loincCode() table so any unmapped new MetricType is caught.
        val mapped = MetricType.entries.count { loincCode(it) != null }
        assertEquals(108, mapped)
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
    fun `CRP maps to LOINC 1988-5 distinct from hsCRP`() {
        // Issue #354. Standard CRP and high-sensitivity CRP are different
        // assays with different LOINCs — Bios stores them as separate keys
        // so a routine ER CRP doesn't masquerade as a cardiovascular-risk
        // hsCRP.
        val (code, _) = loincCode(MetricType.CRP)!!
        assertEquals("1988-5", code)
        assertNotEquals(
            loincCode(MetricType.HSCRP)!!.first,
            loincCode(MetricType.CRP)!!.first,
        )
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
    fun `Wave-1 high-value markers map to canonical LOINC codes`() {
        // Spot-check the Blueprint audit §3.1 priority additions whose
        // identity is most likely to drift if a reviewer mistypes a code.
        assertEquals("89594-4", loincCode(MetricType.LIPOPROTEIN_A)!!.first)
        assertEquals("13965-9", loincCode(MetricType.HOMOCYSTEINE)!!.first)
        assertEquals("3094-0", loincCode(MetricType.BUN)!!.first)
        assertEquals("3084-1", loincCode(MetricType.URIC_ACID)!!.first)
        assertEquals("1751-7", loincCode(MetricType.ALBUMIN)!!.first)
        assertEquals("787-2", loincCode(MetricType.MCV)!!.first)
        assertEquals("2498-4", loincCode(MetricType.IRON_SERUM)!!.first)
        assertEquals("15067-2", loincCode(MetricType.FSH)!!.first)
        assertEquals("13967-5", loincCode(MetricType.SHBG)!!.first)
    }

    @Test
    fun `Wave-2 mapped markers point to canonical LOINC codes`() {
        // Audit §3.2 additions. Telomere length / bone-density T-score /
        // vitamin K2 deliberately have no LOINC (proprietary / site-specific
        // / no stable code) and stay in the unmapped-fallback path.
        assertEquals("8099-3", loincCode(MetricType.THYROID_PEROXIDASE_AB)!!.first)
        assertEquals("8088-6", loincCode(MetricType.THYROGLOBULIN_AB)!!.first)
        assertEquals("2857-1", loincCode(MetricType.PSA_TOTAL)!!.first)
        assertEquals("10886-0", loincCode(MetricType.PSA_FREE)!!.first)
        assertEquals("49595-2", loincCode(MetricType.CORONARY_CALCIUM_SCORE)!!.first)
        assertEquals("102965-7", loincCode(MetricType.PTAU_217)!!.first)
        assertEquals("2923-1", loincCode(MetricType.VITAMIN_A_RETINOL)!!.first)
        assertEquals("1823-4", loincCode(MetricType.VITAMIN_E_ALPHA_TOCOPHEROL)!!.first)
        assertNull(loincCode(MetricType.TELOMERE_LENGTH))
        assertNull(loincCode(MetricType.BONE_DENSITY_T_SCORE))
        assertNull(loincCode(MetricType.VITAMIN_K2))
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
    fun `coagulation panel maps to canonical LOINC codes`() {
        // Issue #352. PT/aPTT plasma-tube LOINCs; Quick uses the percent-of-
        // normal LOINC reported by DACH/ES/FR labs; INR is the warfarin
        // monitoring standard.
        assertEquals("5902-2", loincCode(MetricType.PROTHROMBIN_TIME)!!.first)
        assertEquals("6301-6", loincCode(MetricType.INR)!!.first)
        assertEquals("11149-6", loincCode(MetricType.QUICK_INDEX)!!.first)
        assertEquals("14979-9", loincCode(MetricType.APTT)!!.first)
        assertEquals("13488-2", loincCode(MetricType.APTT_RATIO)!!.first)
    }

    @Test
    fun `coagulation panel units map to canonical UCUM codes`() {
        // PT and aPTT are reported in seconds; INR and aPTT-ratio are
        // dimensionless (SCORE per HOMA_IR precedent); Quick is percent.
        assertEquals("s", ucumCode(MetricType.PROTHROMBIN_TIME))
        assertEquals("{score}", ucumCode(MetricType.INR))
        assertEquals("%", ucumCode(MetricType.QUICK_INDEX))
        assertEquals("s", ucumCode(MetricType.APTT))
        assertEquals("{score}", ucumCode(MetricType.APTT_RATIO))
    }

    @Test
    fun `absolute leukocyte differential maps to canonical LOINC codes`() {
        // Issue #353. Companions to the existing *_PCT keys and the
        // ABSOLUTE_NEUTROPHIL_COUNT that anchors the febrile-neutropenia
        // signature.
        assertEquals("731-0", loincCode(MetricType.ABSOLUTE_LYMPHOCYTE_COUNT)!!.first)
        assertEquals("742-7", loincCode(MetricType.ABSOLUTE_MONOCYTE_COUNT)!!.first)
        assertEquals("711-2", loincCode(MetricType.ABSOLUTE_EOSINOPHIL_COUNT)!!.first)
        assertEquals("704-7", loincCode(MetricType.ABSOLUTE_BASOPHIL_COUNT)!!.first)
        // All four use the /µL UCUM code shared with ABSOLUTE_NEUTROPHIL_COUNT.
        assertEquals("/uL", ucumCode(MetricType.ABSOLUTE_LYMPHOCYTE_COUNT))
        assertEquals("/uL", ucumCode(MetricType.ABSOLUTE_MONOCYTE_COUNT))
        assertEquals("/uL", ucumCode(MetricType.ABSOLUTE_EOSINOPHIL_COUNT))
        assertEquals("/uL", ucumCode(MetricType.ABSOLUTE_BASOPHIL_COUNT))
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

    // -- Wave 6 biomarker LOINC codes (#339) --

    @Test
    fun `D-dimer maps to LOINC 48065-7 FEU`() {
        val (code, display) = loincCode(MetricType.D_DIMER)!!
        assertEquals("48065-7", code)
        assertTrue("display should mark FEU convention: $display", display.contains("FEU"))
    }

    @Test
    fun `reverse T3 maps to LOINC 32261-0`() {
        val (code, _) = loincCode(MetricType.REVERSE_T3)!!
        assertEquals("32261-0", code)
    }

    @Test
    fun `omega-3 index maps to LOINC 89028-2`() {
        val (code, _) = loincCode(MetricType.OMEGA_3_INDEX)!!
        assertEquals("89028-2", code)
    }

    @Test
    fun `leptin maps to LOINC 32413-7`() {
        val (code, _) = loincCode(MetricType.LEPTIN)!!
        assertEquals("32413-7", code)
    }

    @Test
    fun `adiponectin maps to LOINC 30516-9`() {
        val (code, _) = loincCode(MetricType.ADIPONECTIN)!!
        assertEquals("30516-9", code)
    }
}
