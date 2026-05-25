package com.bios.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun dosed_intake_keys_carry_dose_units() {
        // Unlike tobacco_use / cannabis_use (EVENT marker), the #136 keys
        // store the dose in `value` so the pharmacokinetic engine can
        // integrate them. Each key's unit is the dose's native unit.
        assertEquals(MetricDomain.INTAKE, MetricType.CAFFEINE_INTAKE.domain)
        assertEquals(MetricUnit.MILLIGRAMS, MetricType.CAFFEINE_INTAKE.unit)
        assertEquals("mg", MetricUnit.MILLIGRAMS.symbol)

        assertEquals(MetricDomain.INTAKE, MetricType.ALCOHOL_INTAKE.domain)
        assertEquals(MetricUnit.GRAMS, MetricType.ALCOHOL_INTAKE.unit)
        assertEquals("g", MetricUnit.GRAMS.symbol)

        assertEquals(MetricDomain.INTAKE, MetricType.MEDICATION_INTAKE.domain)
        assertEquals(MetricUnit.MILLIGRAMS, MetricType.MEDICATION_INTAKE.unit)
    }

    @Test
    fun dosed_intake_keys_do_not_allow_manual_entry() {
        // Doses come from the producing companion (W2F FuelLog for caffeine,
        // a future medications companion for medication_intake) — not the
        // Bios bedside manual-reading surface. Keep the picker focused on
        // clinical vitals.
        for (t in setOf(
            MetricType.CAFFEINE_INTAKE,
            MetricType.ALCOHOL_INTAKE,
            MetricType.MEDICATION_INTAKE,
        )) {
            assertEquals(
                false, t.allowsManualEntry,
                "${t.key} must not opt in to manual entry — companion is the producer",
            )
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
    fun lf_hf_ratio_is_cardiovascular_score() {
        assertEquals(MetricDomain.CARDIOVASCULAR, MetricType.LF_HF_RATIO.domain)
        assertEquals(MetricUnit.SCORE, MetricType.LF_HF_RATIO.unit)
    }

    @Test
    fun cycle_phase_is_womens_health_category() {
        assertEquals(MetricDomain.WOMENS_HEALTH, MetricType.CYCLE_PHASE.domain)
        assertEquals(MetricUnit.CATEGORY, MetricType.CYCLE_PHASE.unit)
    }

    @Test
    fun menstruation_onset_is_womens_health_event() {
        assertEquals(MetricDomain.WOMENS_HEALTH, MetricType.MENSTRUATION_ONSET.domain)
        assertEquals(MetricUnit.EVENT, MetricType.MENSTRUATION_ONSET.unit)
    }

    @Test
    fun cycle_day_is_womens_health_count() {
        assertEquals(MetricDomain.WOMENS_HEALTH, MetricType.CYCLE_DAY.domain)
        assertEquals(MetricUnit.COUNT, MetricType.CYCLE_DAY.unit)
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
    fun epigenetic_age_clocks_are_biomarkers() {
        // GrimAge, PhenoAge, Horvath report biological age in years.
        for (t in setOf(
            MetricType.EPIGENETIC_AGE_GRIM,
            MetricType.EPIGENETIC_AGE_PHENO,
            MetricType.EPIGENETIC_AGE_HORVATH,
        )) {
            assertEquals(MetricDomain.BIOMARKER, t.domain)
            assertEquals(MetricUnit.YEARS, t.unit)
        }
        // DunedinPACE is years-of-aging-per-year, a dimensionless ratio.
        assertEquals(MetricDomain.BIOMARKER, MetricType.EPIGENETIC_AGE_DUNEDIN_PACE.domain)
        assertEquals(MetricUnit.SCORE, MetricType.EPIGENETIC_AGE_DUNEDIN_PACE.unit)
    }

    @Test
    fun years_unit_symbol_is_yr() {
        assertEquals("yr", MetricUnit.YEARS.symbol)
    }

    @Test
    fun exercise_session_is_activity_event() {
        // Composite event: parent row is a MetricReading, structured fields
        // (modality, start/end utc, avg HR, RPE) live in event_payloads.
        // ACTIVITY domain alongside steps/active_calories/active_minutes;
        // EVENT unit matches the marker semantics (value = 1.0).
        assertEquals(MetricDomain.ACTIVITY, MetricType.EXERCISE_SESSION.domain)
        assertEquals(MetricUnit.EVENT, MetricType.EXERCISE_SESSION.unit)
    }

    @Test
    fun pain_score_is_neurological_score() {
        assertEquals(MetricDomain.NEUROLOGICAL, MetricType.PAIN_SCORE.domain)
        assertEquals(MetricUnit.SCORE, MetricType.PAIN_SCORE.unit)
        assertTrue(
            MetricType.PAIN_SCORE.allowsManualEntry,
            "PAIN_SCORE must allow manual entry — it's the canonical triage vital",
        )
    }

    @Test
    fun consciousness_level_is_neurological_score() {
        // GCS canonical scale, 3–15 total. AVPU input is an entry-time shortcut
        // mapped lossless into GCS — see MetricType.kt header doc.
        assertEquals(MetricDomain.NEUROLOGICAL, MetricType.CONSCIOUSNESS_LEVEL.domain)
        assertEquals(MetricUnit.SCORE, MetricType.CONSCIOUSNESS_LEVEL.unit)
        assertTrue(
            MetricType.CONSCIOUSNESS_LEVEL.allowsManualEntry,
            "CONSCIOUSNESS_LEVEL must allow manual entry — clinicians and " +
                "owners both produce it from the bedside",
        )
    }

    @Test
    fun oxygen_flow_rate_is_respiratory_liters_per_min() {
        assertEquals(MetricDomain.RESPIRATORY, MetricType.OXYGEN_FLOW_RATE.domain)
        assertEquals(MetricUnit.LITERS_PER_MIN, MetricType.OXYGEN_FLOW_RATE.unit)
        assertEquals("L/min", MetricUnit.LITERS_PER_MIN.symbol)
        assertTrue(
            MetricType.OXYGEN_FLOW_RATE.allowsManualEntry,
            "OXYGEN_FLOW_RATE must allow manual entry — it's recorded at " +
                "the bedside, not by a wearable",
        )
    }

    @Test
    fun allows_manual_entry_defaults_false_for_pure_sensor_keys() {
        // Spot-check: streaming sensor / derived signals must never opt in
        // to the manual-reading surface, or the picker fills with noise.
        // HRV_LF_POWER / HRV_HF_POWER stay non-manual (PSD outputs of an IBI
        // series), but HRV itself opts in — phone-PPG apps like HRV4Training
        // give the owner a number to type. See manual_vital assertions.
        val notManual = listOf(
            MetricType.HRV_LF_POWER, MetricType.HRV_HF_POWER,
            MetricType.PARASYMPATHETIC_TONE, MetricType.STRESS_SCORE,
            MetricType.LF_HF_RATIO, MetricType.VO2_MAX,
            MetricType.SKIN_TEMPERATURE_DEVIATION,
            MetricType.SLEEP_STAGE, MetricType.SLEEP_EFFICIENCY,
            MetricType.STEPS, MetricType.ACTIVE_CALORIES,
            MetricType.AMBIENT_LIGHT, MetricType.AIR_PM25,
            MetricType.TYPING_CADENCE, MetricType.MOOD_DRIFT_SCORE,
            MetricType.TOBACCO_USE, MetricType.FALL_EVENT,
        )
        for (t in notManual) {
            assertEquals(
                false, t.allowsManualEntry,
                "${t.key} must not opt in to manual entry",
            )
        }
    }

    @Test
    fun allows_manual_entry_true_for_clinical_vitals() {
        val manual = listOf(
            MetricType.HEART_RATE, MetricType.RESTING_HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.BLOOD_PRESSURE_SYSTOLIC, MetricType.BLOOD_PRESSURE_DIASTOLIC,
            MetricType.BLOOD_OXYGEN, MetricType.RESPIRATORY_RATE,
            MetricType.SKIN_TEMPERATURE,
            MetricType.PAIN_SCORE, MetricType.CONSCIOUSNESS_LEVEL,
            MetricType.OXYGEN_FLOW_RATE,
        )
        for (t in manual) {
            assertTrue(
                t.allowsManualEntry,
                "${t.key} must opt in to manual entry — owner can be the source",
            )
        }
    }

    @Test
    fun every_biomarker_allows_manual_entry() {
        // Biomarkers have no streaming source; manual entry is the only path
        // for owners without a FHIR feed.
        for (t in MetricType.entries.filter { it.domain == MetricDomain.BIOMARKER }) {
            assertTrue(
                t.allowsManualEntry,
                "${t.key} (biomarker) must allow manual entry",
            )
        }
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

    // -- Wave-1 biomarker expansion (BLUEPRINT_PROTOCOL_AUDIT §3.1) --

    @Test
    fun wave1_biomarkers_use_canonical_per_assay_units() {
        // Each new MetricType pairs with the unit its lab report uses by
        // convention; locking the pair here prevents drift if the enum is
        // reordered or a unit is renamed.
        val expected = mapOf(
            MetricType.LIPOPROTEIN_A to MetricUnit.NMOL_PER_L,
            MetricType.HOMOCYSTEINE to MetricUnit.UMOL_PER_L,
            MetricType.BUN to MetricUnit.MG_PER_DL,
            MetricType.CALCIUM_SERUM to MetricUnit.MG_PER_DL,
            MetricType.CARBON_DIOXIDE to MetricUnit.MEQ_PER_L,
            MetricType.CHLORIDE to MetricUnit.MEQ_PER_L,
            MetricType.PHOSPHATE to MetricUnit.MG_PER_DL,
            MetricType.SODIUM to MetricUnit.MEQ_PER_L,
            MetricType.POTASSIUM to MetricUnit.MEQ_PER_L,
            MetricType.URIC_ACID to MetricUnit.MG_PER_DL,
            MetricType.ALBUMIN to MetricUnit.G_PER_DL,
            MetricType.ALKALINE_PHOSPHATASE to MetricUnit.U_PER_L,
            MetricType.BILIRUBIN_TOTAL to MetricUnit.MG_PER_DL,
            MetricType.TOTAL_PROTEIN to MetricUnit.G_PER_DL,
            MetricType.AMYLASE to MetricUnit.U_PER_L,
            MetricType.LIPASE to MetricUnit.U_PER_L,
            MetricType.MCV to MetricUnit.FEMTOLITERS,
            MetricType.MCH to MetricUnit.PICOGRAMS,
            MetricType.MCHC to MetricUnit.G_PER_DL,
            MetricType.RDW to MetricUnit.PERCENT,
            MetricType.MPV to MetricUnit.FEMTOLITERS,
            MetricType.NEUTROPHILS_PCT to MetricUnit.PERCENT,
            MetricType.LYMPHOCYTES_PCT to MetricUnit.PERCENT,
            MetricType.MONOCYTES_PCT to MetricUnit.PERCENT,
            MetricType.EOSINOPHILS_PCT to MetricUnit.PERCENT,
            MetricType.BASOPHILS_PCT to MetricUnit.PERCENT,
            MetricType.IRON_SERUM to MetricUnit.UG_PER_DL,
            MetricType.IRON_SATURATION_PCT to MetricUnit.PERCENT,
            MetricType.TIBC to MetricUnit.UG_PER_DL,
            MetricType.FSH to MetricUnit.MIU_PER_ML,
            MetricType.LH to MetricUnit.MIU_PER_ML,
            MetricType.SHBG to MetricUnit.NMOL_PER_L,
            MetricType.AMH to MetricUnit.NG_PER_ML,
            MetricType.TESTOSTERONE_FREE to MetricUnit.PG_PER_ML,
            MetricType.PROLACTIN to MetricUnit.NG_PER_ML,
            MetricType.DHEA_SULFATE to MetricUnit.UG_PER_DL,
        )
        for ((type, unit) in expected) {
            assertEquals(MetricDomain.BIOMARKER, type.domain, "${type.key} must be a BIOMARKER")
            assertEquals(unit, type.unit, "${type.key} must report in ${unit.name}")
            assertTrue(type.allowsManualEntry, "${type.key} (biomarker) must allow manual entry")
        }
    }

    @Test
    fun wave1_new_units_carry_expected_symbols() {
        assertEquals("nmol/L", MetricUnit.NMOL_PER_L.symbol)
        assertEquals("µmol/L", MetricUnit.UMOL_PER_L.symbol)
        assertEquals("mEq/L", MetricUnit.MEQ_PER_L.symbol)
        assertEquals("fL", MetricUnit.FEMTOLITERS.symbol)
        assertEquals("pg", MetricUnit.PICOGRAMS.symbol)
        assertEquals("mIU/mL", MetricUnit.MIU_PER_ML.symbol)
    }

    // -- Wave-2 biomarker expansion (BLUEPRINT_PROTOCOL_AUDIT §3.2) --

    @Test
    fun wave2_biomarkers_use_canonical_per_assay_units() {
        val expected = mapOf(
            MetricType.THYROID_PEROXIDASE_AB to MetricUnit.IU_PER_ML,
            MetricType.THYROGLOBULIN_AB to MetricUnit.IU_PER_ML,
            MetricType.PSA_TOTAL to MetricUnit.NG_PER_ML,
            MetricType.PSA_FREE to MetricUnit.NG_PER_ML,
            MetricType.TELOMERE_LENGTH to MetricUnit.SCORE,
            MetricType.CORONARY_CALCIUM_SCORE to MetricUnit.SCORE,
            MetricType.BONE_DENSITY_T_SCORE to MetricUnit.SCORE,
            MetricType.PTAU_217 to MetricUnit.PG_PER_ML,
            MetricType.VITAMIN_K2 to MetricUnit.NG_PER_ML,
            MetricType.VITAMIN_A_RETINOL to MetricUnit.UG_PER_DL,
            MetricType.VITAMIN_E_ALPHA_TOCOPHEROL to MetricUnit.MG_PER_L,
        )
        for ((type, unit) in expected) {
            assertEquals(MetricDomain.BIOMARKER, type.domain, "${type.key} must be a BIOMARKER")
            assertEquals(unit, type.unit, "${type.key} must report in ${unit.name}")
            assertTrue(type.allowsManualEntry, "${type.key} (biomarker) must allow manual entry")
        }
    }

    @Test
    fun wave2_new_unit_carries_expected_symbol() {
        assertEquals("IU/mL", MetricUnit.IU_PER_ML.symbol)
    }

    @Test
    fun neurology_urgent_primitives_are_neurological_events() {
        // Audit §3.2 #24 — SEIZURE_EVENT and THUNDERCLAP_HEADACHE_SUSPECTED
        // are owner-logged event-shape primitives that drive URGENT
        // neurology patterns. Same shape as FAST_STROKE_SUSPECTED, FALL_EVENT.
        for (t in setOf(MetricType.SEIZURE_EVENT, MetricType.THUNDERCLAP_HEADACHE_SUSPECTED)) {
            assertEquals(MetricDomain.NEUROLOGICAL, t.domain)
            assertEquals(MetricUnit.EVENT, t.unit)
            assertTrue(t.allowsManualEntry, "${t.key} must allow manual entry — owner is the only producer")
            assertEquals(t, MetricType.fromKey(t.key))
        }
    }

    @Test
    fun augmentation_index_ppg_is_cardiovascular_percent() {
        // Blueprint audit §3.1 #8 — PPG-derived augmentation index. Lives on
        // the cardiovascular side (computed from camera-PPG morphology), not
        // BIOMARKER. Manual entry stays false; the value is always
        // PpgSignalProcessor-derived, never owner-typed.
        assertEquals(MetricDomain.CARDIOVASCULAR, MetricType.AUGMENTATION_INDEX_PPG.domain)
        assertEquals(MetricUnit.PERCENT, MetricType.AUGMENTATION_INDEX_PPG.unit)
        assertEquals(false, MetricType.AUGMENTATION_INDEX_PPG.allowsManualEntry)
        assertEquals(
            MetricType.AUGMENTATION_INDEX_PPG,
            MetricType.fromKey("augmentation_index_ppg"),
        )
    }

    // -- ECG strip presence (#188, audit gap §2.8) --

    @Test
    fun ecg_strip_available_is_cardiovascular_boolean() {
        // ECG strips are not scalar measurements — the waveform lives
        // in the `ecg_strips` table. This MetricType key is just the
        // presence indicator the pattern engine joins against.
        assertEquals(MetricDomain.CARDIOVASCULAR, MetricType.ECG_STRIP_AVAILABLE.domain)
        assertEquals(MetricUnit.BOOLEAN, MetricType.ECG_STRIP_AVAILABLE.unit)
        // Booleans aren't manually entered — the strip is the artefact;
        // owners import the strip itself rather than the presence flag.
        assertEquals(false, MetricType.ECG_STRIP_AVAILABLE.allowsManualEntry)
        // Sibling-consistency check: every cardiovascular key has
        // resolvable round-trip semantics through fromKey.
        assertEquals(MetricType.ECG_STRIP_AVAILABLE,
            MetricType.fromKey("ecg_strip_available"))
    }
}
