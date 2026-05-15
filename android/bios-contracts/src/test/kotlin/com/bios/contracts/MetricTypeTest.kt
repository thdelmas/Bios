package com.bios.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Guards the inter-app contract surface — every key here is a public API
 * consumed by companions. Breakage shows up here before it ships.
 */
class MetricTypeTest {

    @Test
    fun fromKey_roundTrips_every_entry() {
        for (type in MetricType.entries) {
            assertEquals(type, MetricType.fromKey(type.key))
        }
    }

    @Test
    fun fromKey_returns_null_for_unknown_keys() {
        assertNull(MetricType.fromKey("not_a_real_metric"))
    }

    @Test
    fun substance_use_keys_are_intake_events() {
        val intakeEvents = setOf(
            MetricType.TOBACCO_USE, MetricType.TOBACCO_CRAVING,
            MetricType.CANNABIS_USE, MetricType.CANNABIS_CRAVING,
        )
        for (t in intakeEvents) {
            assertEquals(MetricDomain.INTAKE, t.domain)
            assertEquals(MetricUnit.EVENT, t.unit)
        }
    }

    @Test
    fun safety_keys_are_safety_events() {
        val safetyEvents = setOf(
            MetricType.FALL_EVENT, MetricType.NEAR_MISS_FALL, MetricType.CHECK_IN_MISS,
        )
        for (t in safetyEvents) {
            assertEquals(MetricDomain.SAFETY, t.domain)
            assertEquals(MetricUnit.EVENT, t.unit)
        }
    }

    @Test
    fun parasympathetic_tone_is_cardiovascular_score() {
        assertEquals(MetricDomain.CARDIOVASCULAR, MetricType.PARASYMPATHETIC_TONE.domain)
        assertEquals(MetricUnit.SCORE, MetricType.PARASYMPATHETIC_TONE.unit)
    }

    @Test
    fun stress_score_is_cardiovascular_score() {
        assertEquals(MetricDomain.CARDIOVASCULAR, MetricType.STRESS_SCORE.domain)
        assertEquals(MetricUnit.SCORE, MetricType.STRESS_SCORE.unit)
    }

    @Test
    fun cycle_phase_is_womens_health_category() {
        assertEquals(MetricDomain.WOMENS_HEALTH, MetricType.CYCLE_PHASE.domain)
        assertEquals(MetricUnit.CATEGORY, MetricType.CYCLE_PHASE.unit)
    }

    @Test
    fun hba1c_is_biomarker_percent() {
        assertEquals(MetricDomain.BIOMARKER, MetricType.HBA1C.domain)
        assertEquals(MetricUnit.PERCENT, MetricType.HBA1C.unit)
    }

    @Test
    fun hscrp_is_biomarker_mg_per_l() {
        assertEquals(MetricDomain.BIOMARKER, MetricType.HSCRP.domain)
        assertEquals(MetricUnit.MG_PER_L, MetricType.HSCRP.unit)
        assertEquals("mg/L", MetricUnit.MG_PER_L.symbol)
    }

    @Test
    fun lipid_panel_and_apob_are_biomarker_mg_per_dl() {
        val lipids = setOf(
            MetricType.TOTAL_CHOLESTEROL, MetricType.LDL_CHOLESTEROL,
            MetricType.HDL_CHOLESTEROL, MetricType.TRIGLYCERIDES,
            MetricType.APO_B,
        )
        for (t in lipids) {
            assertEquals(MetricDomain.BIOMARKER, t.domain)
            assertEquals(MetricUnit.MG_PER_DL, t.unit)
        }
    }

    @Test
    fun vitamin_d_25oh_is_biomarker_ng_per_ml() {
        assertEquals(MetricDomain.BIOMARKER, MetricType.VITAMIN_D_25OH.domain)
        assertEquals(MetricUnit.NG_PER_ML, MetricType.VITAMIN_D_25OH.unit)
        assertEquals("ng/mL", MetricUnit.NG_PER_ML.symbol)
    }

    @Test
    fun thyroid_panel_uses_canonical_per_assay_units() {
        // Each assay reports in a different unit by clinical convention —
        // collapsing them into one unit would force misleading conversions.
        assertEquals(MetricDomain.BIOMARKER, MetricType.TSH.domain)
        assertEquals(MetricUnit.MIU_PER_L, MetricType.TSH.unit)
        assertEquals("mIU/L", MetricUnit.MIU_PER_L.symbol)

        assertEquals(MetricDomain.BIOMARKER, MetricType.FREE_T4.domain)
        assertEquals(MetricUnit.NG_PER_DL, MetricType.FREE_T4.unit)
        assertEquals("ng/dL", MetricUnit.NG_PER_DL.symbol)

        assertEquals(MetricDomain.BIOMARKER, MetricType.FREE_T3.domain)
        assertEquals(MetricUnit.PG_PER_ML, MetricType.FREE_T3.unit)
        assertEquals("pg/mL", MetricUnit.PG_PER_ML.symbol)
    }

    @Test
    fun cbc_panel_uses_canonical_per_assay_units() {
        assertEquals(MetricDomain.BIOMARKER, MetricType.HEMOGLOBIN.domain)
        assertEquals(MetricUnit.G_PER_DL, MetricType.HEMOGLOBIN.unit)
        assertEquals("g/dL", MetricUnit.G_PER_DL.symbol)

        assertEquals(MetricDomain.BIOMARKER, MetricType.HEMATOCRIT.domain)
        assertEquals(MetricUnit.PERCENT, MetricType.HEMATOCRIT.unit)

        // WBC and platelets share the giga-per-litre cell-count unit.
        for (t in setOf(MetricType.WBC, MetricType.PLATELETS)) {
            assertEquals(MetricDomain.BIOMARKER, t.domain)
            assertEquals(MetricUnit.GIGA_PER_L, t.unit)
        }

        assertEquals(MetricDomain.BIOMARKER, MetricType.RBC.domain)
        assertEquals(MetricUnit.TERA_PER_L, MetricType.RBC.unit)
    }

    @Test
    fun sleep_latency_is_sleep_seconds() {
        assertEquals(MetricDomain.SLEEP, MetricType.SLEEP_LATENCY.domain)
        assertEquals(MetricUnit.SECONDS, MetricType.SLEEP_LATENCY.unit)
    }

    @Test
    fun sleep_efficiency_is_sleep_percent() {
        assertEquals(MetricDomain.SLEEP, MetricType.SLEEP_EFFICIENCY.domain)
        assertEquals(MetricUnit.PERCENT, MetricType.SLEEP_EFFICIENCY.unit)
    }

    @Test
    fun sleep_fragmentation_is_sleep_count() {
        assertEquals(MetricDomain.SLEEP, MetricType.SLEEP_FRAGMENTATION_INDEX.domain)
        assertEquals(MetricUnit.COUNT, MetricType.SLEEP_FRAGMENTATION_INDEX.unit)
    }

    @Test
    fun body_fat_pct_is_metabolic_percent() {
        assertEquals(MetricDomain.METABOLIC, MetricType.BODY_FAT_PCT.domain)
        assertEquals(MetricUnit.PERCENT, MetricType.BODY_FAT_PCT.unit)
    }

    @Test
    fun ambient_light_is_environment_lux() {
        assertEquals(MetricDomain.ENVIRONMENT, MetricType.AMBIENT_LIGHT.domain)
        assertEquals(MetricUnit.LUX, MetricType.AMBIENT_LIGHT.unit)
        assertEquals("lx", MetricUnit.LUX.symbol)
    }

    @Test
    fun health_contract_authority_is_stable() {
        assertEquals("com.bios.app.health", BiosHealthContract.AUTHORITY)
    }

    @Test
    fun permission_names_match_manifest() {
        assertEquals("com.bios.app.permission.READ_HEALTH", BiosPermissions.READ_HEALTH)
        assertEquals("com.bios.app.permission.WRITE_COMPANION", BiosPermissions.WRITE_COMPANION)
    }

    @Test
    fun reserved_intent_actions_are_present() {
        assertNotNull(BiosIntentActions.ACTION_SUGGEST_BAND)
        assertNotNull(BiosIntentActions.ACTION_REQUEST_STOP)
    }
}
