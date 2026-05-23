package com.bios.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Frozen snapshot of every `MetricType.key` string that has shipped in a
 * released `bios-contracts` artifact. Out-of-tree companions (W2F, Smokeless,
 * Virgil, Fil) pin a contracts version and assume these keys keep resolving.
 *
 * **How to update this file.**
 *  - Adding a key: append the new string. That is the only valid edit when
 *    growing the contract.
 *  - Deprecating a key: keep the entry here AND keep the enum constant —
 *    annotate the enum with `@Deprecated`. `fromKey()` keeps resolving it,
 *    so this test still passes.
 *  - Removing a key (the entry actually disappears from the enum): do not
 *    silence this test by trimming the snapshot. Removing a key forces a
 *    paired release of every companion and is a major-version break of the
 *    contracts artifact. Coordinate with companion maintainers first, then
 *    bump the contracts major version, then edit this snapshot.
 *
 * See docs/CONSUMER_API.md → "Contracts forward/backward compatibility".
 */
class MetricTypeKeysSnapshotTest {

    private val shippedKeys: Set<String> = setOf(
        // Cardiovascular
        "heart_rate",
        "heart_rate_variability",
        "parasympathetic_tone",
        "stress_score",
        "lf_hf_ratio",
        "hrv_lf_power",
        "hrv_hf_power",
        "vo2_max",
        "resting_heart_rate",
        "blood_pressure_systolic",
        "blood_pressure_diastolic",
        "blood_oxygen",
        // PPG-derived AFib screen (#180) + pulse-wave morphology (#181)
        "irregular_rhythm_burden",
        // Single-lead ECG strip presence (#188)
        "ecg_strip_available",
        "ppg_peak_amplitude_mean",
        "ppg_peak_amplitude_cov",
        "ppg_rise_time_mean",
        "ppg_rise_time_cov",
        "ppg_decay_asymmetry_index",
        "ppg_dichrotic_notch_position",
        // Respiratory
        "respiratory_rate",
        "oxygen_flow_rate",
        // Sleep apnea passthrough (#157)
        "sleep_apnea_event",
        "ahi",
        // Asthma surveillance (#200) — owner-measured PEF + FEV1
        "peak_expiratory_flow_lmin",
        "forced_expiratory_volume_1_liters",
        // Neurological (manual-capture clinical signs)
        "pain_score",
        "consciousness_level",
        // Temperature
        "skin_temperature",
        "skin_temperature_deviation",
        // Sleep
        "sleep_stage",
        "sleep_duration",
        "sleep_latency",
        "sleep_efficiency",
        "sleep_fragmentation_index",
        "wake_after_sleep_onset",
        "sleep_score",
        "sleep_regularity",
        "circadian_phase_shift",
        // Activity
        "steps",
        "active_calories",
        "active_minutes",
        "exercise_session",
        // Metabolic
        "blood_glucose",
        "glucose_cv",
        "glucose_mage",
        "glucose_time_in_range",
        "glucose_peak_24h",
        "body_mass",
        "body_fat_pct",
        "lean_mass",
        "body_water_pct",
        "bone_mass",
        // Recovery
        "recovery_score",
        // Women's Health
        "basal_body_temperature",
        "cycle_phase",
        "menstruation_onset",
        "cycle_day",
        // Environment
        "ambient_light",
        "air_pm25",
        "air_voc",
        "air_co2",
        // Environment expansion (#200) — humidity + ambient temp for asthma
        // trigger context, heat-index, damp/dry pathogen overlays
        "ambient_humidity_pct",
        "ambient_temperature_c",
        // Environmental context (#197) — elevation + daylight hours
        "elevation_m",
        "daylight_hours",
        // Biomarkers
        "hba1c",
        "hscrp",
        "total_cholesterol",
        "ldl_cholesterol",
        "hdl_cholesterol",
        "triglycerides",
        "apo_b",
        "vitamin_d_25oh",
        "tsh",
        "free_t4",
        "free_t3",
        "hemoglobin",
        "hematocrit",
        "wbc",
        "rbc",
        "platelets",
        // Biomarker glycemic-extended / iron / endocrine / micronutrient (#24)
        "fasting_glucose",
        "fasting_insulin",
        "homa_ir",
        "ferritin",
        "testosterone_total",
        "estradiol",
        "cortisol",
        "igf_1",
        "vitamin_b12",
        "folate",
        "magnesium",
        // Renal / hepatic panel (#158)
        "egfr",
        "creatinine",
        "alt",
        "ast",
        "ggt",
        // Cardio-oncology biomarkers (#201)
        "troponin_ng_per_l",
        "nt_pro_bnp_pg_per_ml",
        "absolute_neutrophil_count",
        // Epigenetic age clocks
        "epigenetic_age_dunedin_pace",
        "epigenetic_age_grim",
        "epigenetic_age_pheno",
        "epigenetic_age_horvath",
        // Companion-written (W2F)
        "typing_cadence",
        "mood_drift_score",
        // Companion-written (Smokeless)
        "tobacco_use",
        "tobacco_craving",
        "cannabis_use",
        "cannabis_craving",
        // Dosed-intake (#136 — pharmacokinetic engine substrate)
        "caffeine_intake",
        "alcohol_intake",
        "medication_intake",
        // Companion-written (Virgil)
        "fall_event",
        "near_miss_fall",
        "check_in_miss",
        // Reserved active-test result
        "reaction_time_ms",
        // Neurology owner-PRO (#207)
        "headache_intensity_nrs",
        "migraine_attack_event",
        "fast_stroke_suspected",
    )

    @Test
    fun every_shipped_key_still_resolves() {
        for (key in shippedKeys) {
            assertNotNull(
                MetricType.fromKey(key),
                "Shipped key '$key' no longer resolves. Removing a key from " +
                    "MetricType breaks every companion pinned to an earlier " +
                    "bios-contracts version. Mark the entry @Deprecated " +
                    "instead, or bump the contracts major version and " +
                    "coordinate paired companion releases.",
            )
        }
    }

    @Test
    fun snapshot_covers_every_current_entry() {
        // Catches the reverse failure: a new key was added to the enum but
        // the snapshot was not updated, so future removals of that key
        // would silently slip past the guard above.
        val current = MetricType.entries.map { it.key }.toSet()
        val missingFromSnapshot = current - shippedKeys
        assertEquals(
            emptySet<String>(),
            missingFromSnapshot,
            "New MetricType keys are not listed in the snapshot. Append " +
                "them to MetricTypeKeysSnapshotTest.shippedKeys so future " +
                "removals are caught.",
        )
    }
}
