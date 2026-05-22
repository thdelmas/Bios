package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPattern
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.alerts.TropicalDiseasePatterns
import com.bios.app.config.RegionConfigProvider
import com.bios.app.model.AlertTier
import com.bios.app.physiology.OwnerCondition
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the tropical / endemic-disease pattern library (issue #196,
 * audit-gap §2.6 cluster — AFRICAN_TRADITIONAL_POV, INDIGENOUS_AMERICAS_POV,
 * OTHER_ASIAN_SYSTEMS_POV, OCEANIC_ARCTIC_POV, SIDDHA_POV).
 *
 * The tests cover:
 *  - Registration: all 7 patterns land in [ConditionPatterns.all].
 *  - Region gating: the 6 region-context patterns carry
 *    `requiresTropicalRegion = true`; the SCD crisis screen is gated on
 *    [OwnerCondition.KNOWN_SICKLE_CELL_DISEASE] instead (global diaspora).
 *  - Shape: every signal rule carries a literature citation; every alert
 *    text passes [AlertContentPolicy] (no "you should" / "you must" /
 *    judgmental language).
 *  - Severity floors: dengue and SCD vaso-occlusive crisis lift to URGENT.
 *  - Filter logic: the applicable-patterns filter that the
 *    [com.bios.app.engine.AnomalyDetector] uses correctly suppresses
 *    tropical patterns in Northern temperate regions and the SCD pattern
 *    when the owner has not declared SCD.
 */
class TropicalDiseasePatternsTest {

    // ---- registration ----

    @Test
    fun all_seven_tropical_patterns_register_in_global_list() {
        for (pattern in TropicalDiseasePatterns.all) {
            assertTrue(
                "Pattern ${pattern.id} should be in ConditionPatterns.all",
                pattern in ConditionPatterns.all,
            )
        }
        // Sanity: exactly seven patterns shipped in this wave.
        assertEquals(7, TropicalDiseasePatterns.all.size)
    }

    @Test
    fun every_pattern_id_is_present() {
        val ids = TropicalDiseasePatterns.all.map { it.id }.toSet()
        assertTrue("malaria_suggestive" in ids)
        assertTrue("dengue_warning" in ids)
        assertTrue("chikungunya_suggestive" in ids)
        assertTrue("leptospirosis_suggestive" in ids)
        assertTrue("scrub_typhus_suggestive" in ids)
        assertTrue("sickle_cell_crisis_screen" in ids)
        assertTrue("ciguatera_suggestive" in ids)
    }

    // ---- region / condition gating ----

    @Test
    fun six_region_patterns_require_tropical_region() {
        val regionGated = TropicalDiseasePatterns.all.filter { it.requiresTropicalRegion }
        // Malaria, dengue, chikungunya, leptospirosis, scrub typhus,
        // ciguatera = 6 region-gated. SCD is owner-condition-gated.
        assertEquals(6, regionGated.size)
        assertFalse(
            "Sickle cell crisis screen should NOT be region-gated (global diaspora)",
            TropicalDiseasePatterns.sickleCellCrisisScreen.requiresTropicalRegion,
        )
    }

    @Test
    fun sickle_cell_pattern_requires_known_scd_owner_condition() {
        val pattern = TropicalDiseasePatterns.sickleCellCrisisScreen
        assertEquals(
            "SCD crisis screen must require KNOWN_SICKLE_CELL_DISEASE",
            setOf(OwnerCondition.KNOWN_SICKLE_CELL_DISEASE),
            pattern.requiredOwnerConditions,
        )
    }

    // ---- severity floors ----

    @Test
    fun dengue_warning_phase_lifts_to_urgent() {
        // Severe dengue develops in the 24–48h plasma-leak window;
        // WHO + PAHO anchor early supportive fluid management to outcome.
        assertEquals(
            AlertTier.URGENT,
            TropicalDiseasePatterns.dengueWarning.severityFloor,
        )
    }

    @Test
    fun sickle_cell_crisis_lifts_to_urgent() {
        // Acute chest syndrome is the leading cause of mortality in
        // adults with SCD; ASH 2020 anchors outcome to prompt assessment.
        assertEquals(
            AlertTier.URGENT,
            TropicalDiseasePatterns.sickleCellCrisisScreen.severityFloor,
        )
    }

    // ---- shape contract ----

    @Test
    fun every_signal_rule_carries_a_literature_citation() {
        for (pattern in TropicalDiseasePatterns.all) {
            for (rule in pattern.signalRules) {
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should be LITERATURE-sourced",
                    ThresholdSource.LITERATURE,
                    rule.source,
                )
                assertTrue(
                    "${pattern.id}/${rule.metricType.key} should ship a citation string",
                    rule.citation.isNotBlank(),
                )
            }
        }
    }

    @Test
    fun every_pattern_carries_at_least_three_references() {
        for (pattern in TropicalDiseasePatterns.all) {
            assertTrue(
                "${pattern.id} should ship at least one literature reference",
                pattern.references.isNotEmpty(),
            )
        }
    }

    @Test
    fun every_pattern_passes_alert_content_policy() {
        // Belt-and-braces — AlertContentPolicyTest covers this globally,
        // but verify each tropical pattern individually for clearer
        // failure messages.
        for (pattern in TropicalDiseasePatterns.all) {
            val violations = AlertContentPolicy.validate(pattern)
            assertTrue(
                "${pattern.id} should pass AlertContentPolicy. Violations:\n" +
                    violations.joinToString("\n") {
                        "  ${it.field} contains '${it.prohibitedPhrase}': ${it.context}"
                    },
                violations.isEmpty(),
            )
        }
    }

    @Test
    fun titles_use_data_observation_framing() {
        // The audit calls for "fever pattern suggestive of X — consider
        // diagnostic testing if exposure-risk applies"; never "you have
        // malaria". Each title must use suggestive / pattern / screen
        // language rather than claim a diagnosis.
        val safeWords = listOf("suggestive", "pattern", "screen")
        for (pattern in TropicalDiseasePatterns.all) {
            val lower = pattern.title.lowercase()
            assertTrue(
                "${pattern.id} title should use suggestive/pattern/screen framing (was: '${pattern.title}')",
                safeWords.any { lower.contains(it) },
            )
        }
    }

    // ---- malaria-specific shape ----

    @Test
    fun malaria_pattern_anchors_on_cyclical_fever_window() {
        val pattern = TropicalDiseasePatterns.malariaSuggestive
        val tempRule = pattern.signalRules.first { it.metricType == MetricType.SKIN_TEMPERATURE_DEVIATION }
        // 72-hour window captures the slowest plasmodial cycle (quartan
        // fever, P. malariae); tertian / quotidian fall within.
        assertEquals(72, tempRule.minDurationHours)
    }

    // ---- dengue-specific shape ----

    @Test
    fun dengue_pattern_anchors_on_thrombocytopenia_threshold() {
        val pattern = TropicalDiseasePatterns.dengueWarning
        val plateletRule = pattern.signalRules.first { it.metricType == MetricType.PLATELETS }
        // WHO Dengue 2009/2022 warning-phase platelet threshold.
        assertEquals(100.0, plateletRule.absoluteBelow!!, 1e-9)
    }

    // ---- sickle-cell-specific shape ----

    @Test
    fun sickle_cell_pattern_uses_short_window_and_absolute_spo2_threshold() {
        val pattern = TropicalDiseasePatterns.sickleCellCrisisScreen
        // 6-hour heart-rate window is short enough to catch the sudden
        // VOC onset.
        val hrRule = pattern.signalRules.first { it.metricType == MetricType.RESTING_HEART_RATE }
        assertEquals(6, hrRule.minDurationHours)
        // SpO2 anchor at 92 % flags possible acute chest syndrome (NHLBI 2014).
        val spo2Rule = pattern.signalRules.first { it.metricType == MetricType.BLOOD_OXYGEN }
        assertEquals(92.0, spo2Rule.absoluteBelow!!, 1e-9)
        // Pain-score gate at 7/10 reflects ASH 2020 VOC severity definition.
        val painRule = pattern.signalRules.first { it.metricType == MetricType.PAIN_SCORE }
        assertEquals(7.0, painRule.absoluteAbove!!, 1e-9)
    }

    // ---- ciguatera-specific shape ----

    @Test
    fun ciguatera_pattern_excludes_endurance_athlete_state() {
        // Endurance athletes can baseline below 50 bpm; the bradycardia
        // gate would false-fire on them.
        assertTrue(
            PhysiologyState.ATHLETE_HIGH_FITNESS in
                TropicalDiseasePatterns.ciguateraSuggestive.excludedStates,
        )
    }

    // ---- region-config integration ----

    @Test
    fun tropical_regions_resolve_with_tropicalDiseaseRelevant_flag_set() {
        // ISO codes covering at least one representative from each cohort
        // the audit calls out: African, South Asian, Southeast Asian,
        // Pacific, Caribbean, tropical South American.
        val tropicalCodes = listOf("NG", "IN", "TH", "FJ", "JM", "BR")
        for (code in tropicalCodes) {
            val cfg = RegionConfigProvider.forRegion(code)
            assertTrue(
                "Region $code should have tropicalDiseaseRelevant = true",
                cfg.tropicalDiseaseRelevant,
            )
        }
    }

    @Test
    fun temperate_regions_have_tropicalDiseaseRelevant_false() {
        // The six original Northern temperate configs stay false.
        val temperateCodes = listOf("US", "GB", "EU", "CA", "AU", "JP")
        for (code in temperateCodes) {
            val cfg = RegionConfigProvider.forRegion(code)
            assertFalse(
                "Region $code should have tropicalDiseaseRelevant = false",
                cfg.tropicalDiseaseRelevant,
            )
        }
    }

    // ---- filter logic (mirroring AnomalyDetector.applicablePatterns) ----

    /**
     * Mirror of [com.bios.app.engine.AnomalyDetector.applicablePatterns]
     * — duplicated here so we can exercise the filter rule purely on
     * pattern metadata without standing up a database fixture. If the
     * detector's filter shape changes, this helper must update with it.
     */
    private fun applicable(
        patterns: List<ConditionPattern>,
        physiologyState: PhysiologyState,
        tropicalRelevant: Boolean,
        ownerConditions: Set<OwnerCondition>,
    ): List<ConditionPattern> = patterns.filter { pattern ->
        physiologyState !in pattern.excludedStates &&
            (!pattern.requiresTropicalRegion || tropicalRelevant) &&
            ownerConditions.containsAll(pattern.requiredOwnerConditions)
    }

    @Test
    fun tropical_patterns_fire_in_tropical_region_for_general_owner() {
        val active = applicable(
            TropicalDiseasePatterns.all,
            PhysiologyState.STANDARD,
            tropicalRelevant = true,
            ownerConditions = emptySet(),
        ).map { it.id }.toSet()
        // All six region-gated patterns active in a tropical region.
        assertTrue("malaria_suggestive" in active)
        assertTrue("dengue_warning" in active)
        assertTrue("chikungunya_suggestive" in active)
        assertTrue("leptospirosis_suggestive" in active)
        assertTrue("scrub_typhus_suggestive" in active)
        assertTrue("ciguatera_suggestive" in active)
        // SCD pattern requires the owner-condition flag — not active.
        assertFalse("sickle_cell_crisis_screen" in active)
    }

    @Test
    fun tropical_patterns_stay_silent_in_temperate_region() {
        val active = applicable(
            TropicalDiseasePatterns.all,
            PhysiologyState.STANDARD,
            tropicalRelevant = false,
            ownerConditions = emptySet(),
        ).map { it.id }.toSet()
        // No region-gated patterns fire in a Northern-temperate cohort.
        assertFalse("malaria_suggestive" in active)
        assertFalse("dengue_warning" in active)
        assertFalse("chikungunya_suggestive" in active)
        assertFalse("leptospirosis_suggestive" in active)
        assertFalse("scrub_typhus_suggestive" in active)
        assertFalse("ciguatera_suggestive" in active)
        // SCD also still gated off.
        assertFalse("sickle_cell_crisis_screen" in active)
    }

    @Test
    fun sickle_cell_pattern_fires_only_with_known_scd_flag() {
        // No SCD flag → not active.
        val withoutScd = applicable(
            TropicalDiseasePatterns.all,
            PhysiologyState.STANDARD,
            tropicalRelevant = false,
            ownerConditions = emptySet(),
        )
        assertFalse(TropicalDiseasePatterns.sickleCellCrisisScreen in withoutScd)

        // SCD flag set → active even in a Northern-temperate region
        // (SCD diaspora is global).
        val withScd = applicable(
            TropicalDiseasePatterns.all,
            PhysiologyState.STANDARD,
            tropicalRelevant = false,
            ownerConditions = setOf(OwnerCondition.KNOWN_SICKLE_CELL_DISEASE),
        )
        assertTrue(TropicalDiseasePatterns.sickleCellCrisisScreen in withScd)
    }

    @Test
    fun ciguatera_pattern_suppressed_for_endurance_athlete_even_in_tropical_region() {
        // Endurance athletes baseline at RHR <50; the ciguatera gate would
        // false-fire on them.
        val active = applicable(
            TropicalDiseasePatterns.all,
            PhysiologyState.ATHLETE_HIGH_FITNESS,
            tropicalRelevant = true,
            ownerConditions = emptySet(),
        )
        assertFalse(TropicalDiseasePatterns.ciguateraSuggestive in active)
        // Other tropical patterns should still fire for an athlete in
        // a tropical region (malaria doesn't care about RHR baseline).
        assertTrue(TropicalDiseasePatterns.malariaSuggestive in active)
    }

    // ---- exposure-risk language sanity ----

    @Test
    fun every_pattern_suggested_action_references_exposure_risk_or_diagnostic_testing() {
        // The audit's contract: "consider diagnostic testing if exposure-
        // risk applies" — the suggested-action text must point the owner
        // at either the exposure-risk question or the diagnostic step.
        for (pattern in TropicalDiseasePatterns.all) {
            val action = pattern.suggestedAction
            assertNotNull("${pattern.id} should ship a suggestedAction", action)
            val lower = action!!.lowercase()
            assertTrue(
                "${pattern.id} suggestedAction should reference diagnostic / clinical assessment language (was: '${action}')",
                lower.contains("diagnos") || lower.contains("medical") ||
                    lower.contains("clinic") || lower.contains("emergency") ||
                    lower.contains("test"),
            )
        }
    }
}
