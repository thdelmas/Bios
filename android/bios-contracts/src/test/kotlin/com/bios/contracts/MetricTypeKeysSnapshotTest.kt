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
        // Respiratory
        "respiratory_rate",
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
        // Companion-written (Virgil)
        "fall_event",
        "near_miss_fall",
        "check_in_miss",
        // Reserved active-test result
        "reaction_time_ms",
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
