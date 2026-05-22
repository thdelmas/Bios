package com.bios.app

import com.bios.app.alerts.AcuteWindowPatterns
import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the four acute-window URGENT patterns (#190,
 * EMERGENCY_CRITICAL_CARE_POV §2.3 / §2.4 / §2.9 / §2.10).
 *
 *  - anaphylaxis_screen — Sampson 2006 NIAID/FAAN criteria
 *  - opioid_respiratory_depression_screen — Nandakumar 2019 SAFE
 *  - dka_screen — Kitabchi 2009 + Wolfsdorf 2022 ISPAD
 *  - hypotensive_shock_screen — Singer 2016 Sepsis-3 + Cecconi 2014 ESICM
 *
 * Tests cover: registration, severityFloor URGENT, AlertContentPolicy
 * compliance (no judgment language on these URGENT messages), citation
 * hygiene, and physiology-state gating.
 */
class AcuteWindowPatternsTest {

    // -- registration --

    @Test
    fun all_four_patterns_register_in_global_all_list() {
        assertTrue(AcuteWindowPatterns.anaphylaxisScreen in ConditionPatterns.all)
        assertTrue(AcuteWindowPatterns.opioidRespiratoryDepressionScreen in ConditionPatterns.all)
        assertTrue(AcuteWindowPatterns.dkaScreen in ConditionPatterns.all)
        assertTrue(AcuteWindowPatterns.hypotensiveShockScreen in ConditionPatterns.all)
    }

    @Test
    fun every_acute_pattern_declares_urgent_severity_floor() {
        for (pattern in AcuteWindowPatterns.all) {
            assertEquals(
                "${pattern.id} must escalate to URGENT — acute events are not trend warnings",
                AlertTier.URGENT,
                pattern.severityFloor,
            )
        }
    }

    @Test
    fun every_rule_carries_a_literature_citation() {
        for (pattern in AcuteWindowPatterns.all) {
            for (rule in pattern.signalRules) {
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} must be LITERATURE-sourced",
                    ThresholdSource.LITERATURE,
                    rule.source,
                )
                assertTrue(
                    "${pattern.id}/${rule.metricType.key} must ship a citation",
                    rule.citation.isNotBlank(),
                )
            }
        }
    }

    // -- AlertContentPolicy strictness (issue requirement #4) --

    @Test
    fun every_acute_pattern_is_content_policy_compliant() {
        for (pattern in AcuteWindowPatterns.all) {
            val violations = AlertContentPolicy.validate(pattern)
            assertTrue(
                "${pattern.id} content-policy violations: " +
                    violations.joinToString("; ") {
                        "${it.field}:'${it.prohibitedPhrase}'@${it.context}"
                    },
                violations.isEmpty(),
            )
        }
    }

    @Test
    fun no_acute_pattern_uses_judgment_or_diagnostic_assertion_language() {
        // The audit framing requires data-statement framing for URGENT
        // messages, never "you're in shock" / "you may be overdosing".
        val banned = listOf(
            "you're in shock",
            "you are in shock",
            "you may be overdosing",
            "you are overdosing",
            "you're overdosing",
            "you have anaphylaxis",
            "you have dka",
            "you have ketoacidosis",
            "you have an overdose",
        )
        for (pattern in AcuteWindowPatterns.all) {
            val text = listOf(
                pattern.title,
                pattern.explanation,
                pattern.suggestedAction ?: "",
                pattern.earlyDetection,
            ).joinToString(" ").lowercase()
            for (phrase in banned) {
                assertFalse(
                    "${pattern.id} contains banned judgment / diagnostic phrase '$phrase'",
                    text.contains(phrase),
                )
            }
        }
    }

    // -- anaphylaxis_screen --

    @Test
    fun anaphylaxis_screen_uses_hr_and_spo2_signals_with_sustained_floor() {
        val pattern = AcuteWindowPatterns.anaphylaxisScreen
        assertEquals(2, pattern.signalRules.size)
        val hr = pattern.signalRules.single { it.metricType == MetricType.HEART_RATE }
        assertEquals(DeviationDirection.ABOVE, hr.direction)
        assertEquals(120.0, hr.absoluteAbove!!, 1e-9)
        assertEquals(2, hr.absoluteMinReadings)
        val spo2 = pattern.signalRules.single { it.metricType == MetricType.BLOOD_OXYGEN }
        assertEquals(DeviationDirection.BELOW, spo2.direction)
        assertEquals(92.0, spo2.absoluteBelow!!, 1e-9)
        assertEquals(2, spo2.absoluteMinReadings)
    }

    @Test
    fun anaphylaxis_screen_cites_sampson_2006() {
        val refs = AcuteWindowPatterns.anaphylaxisScreen.references.joinToString("\n")
        assertTrue("Sampson 2006 NIAID/FAAN citation missing", refs.contains("Sampson"))
    }

    // -- opioid_respiratory_depression_screen --

    @Test
    fun opioid_screen_uses_rr_le_8_and_spo2_le_88_required() {
        val pattern = AcuteWindowPatterns.opioidRespiratoryDepressionScreen
        val rr = pattern.signalRules.single { it.metricType == MetricType.RESPIRATORY_RATE }
        assertEquals(DeviationDirection.BELOW, rr.direction)
        assertEquals(8.0, rr.absoluteBelow!!, 1e-9)
        assertTrue("RR rule must be required", rr.required)
        val spo2 = pattern.signalRules.single { it.metricType == MetricType.BLOOD_OXYGEN }
        assertEquals(88.0, spo2.absoluteBelow!!, 1e-9)
        assertTrue("SpO2 rule must be required", spo2.required)
    }

    @Test
    fun opioid_screen_cites_nandakumar_2019_safe_study() {
        val refs = AcuteWindowPatterns.opioidRespiratoryDepressionScreen.references
            .joinToString("\n")
        assertTrue("Nandakumar 2019 SAFE citation missing", refs.contains("Nandakumar"))
        assertTrue("Sci Transl Med citation missing", refs.contains("Sci Transl Med"))
    }

    @Test
    fun opioid_screen_text_is_not_a_drug_use_detector_message() {
        // The audit explicitly states: NOT a "you're using drugs" detector
        // — it is a respiratory-depression detector with multiple causes.
        val explanation = AcuteWindowPatterns.opioidRespiratoryDepressionScreen.explanation
        val lower = explanation.lowercase()
        // Must NOT assert opioid-use as the cause.
        assertFalse(
            "Explanation should not assert opioid use as the cause",
            lower.contains("you are using opioids") ||
                lower.contains("you used opioids"),
        )
        // Must name multiple possible causes (per the audit's framing).
        assertTrue(
            "Explanation should name multiple possible causes (sedative, sleep apnea, COPD…)",
            lower.contains("opioid") &&
                (lower.contains("sedative") || lower.contains("sleep apnea") || lower.contains("copd")),
        )
    }

    @Test
    fun opioid_screen_action_uses_data_statement_framing() {
        val action = AcuteWindowPatterns.opioidRespiratoryDepressionScreen
            .suggestedAction
        assertNotNull(action)
        val lower = action!!.lowercase()
        assertTrue(
            "Action should reference respiratory depression and immediate medical assessment",
            lower.contains("respiratory") && lower.contains("medical"),
        )
    }

    // -- dka_screen --

    @Test
    fun dka_screen_uses_glucose_ge_250_required_and_hr_corroborator() {
        val pattern = AcuteWindowPatterns.dkaScreen
        val glucose = pattern.signalRules.single { it.metricType == MetricType.BLOOD_GLUCOSE }
        assertEquals(DeviationDirection.ABOVE, glucose.direction)
        assertEquals(250.0, glucose.absoluteAbove!!, 1e-9)
        assertTrue(glucose.required)
        val hr = pattern.signalRules.single { it.metricType == MetricType.HEART_RATE }
        assertEquals(100.0, hr.absoluteAbove!!, 1e-9)
    }

    @Test
    fun dka_screen_excludes_paediatric_until_ispad_threshold_lands() {
        // ISPAD 2022 paediatric DKA threshold is ≥200 mg/dL — the adult
        // 250 mg/dL cutoff under-triggers in children. Conservative gate:
        // suppress until the paediatric variant ships.
        assertTrue(
            "Paediatric must be excluded from the adult DKA pattern",
            PhysiologyState.PAEDIATRIC in AcuteWindowPatterns.dkaScreen.excludedStates,
        )
    }

    @Test
    fun dka_screen_cites_kitabchi_and_ispad() {
        val refs = AcuteWindowPatterns.dkaScreen.references.joinToString("\n")
        assertTrue("Kitabchi 2009 ADA hyperglycemic crises citation missing",
            refs.contains("Kitabchi"))
        assertTrue("Wolfsdorf 2022 ISPAD citation missing",
            refs.contains("Wolfsdorf"))
    }

    // -- hypotensive_shock_screen --

    @Test
    fun shock_screen_uses_sbp_le_90_required_and_hr_gt_100_required() {
        val pattern = AcuteWindowPatterns.hypotensiveShockScreen
        val sbp = pattern.signalRules.single {
            it.metricType == MetricType.BLOOD_PRESSURE_SYSTOLIC
        }
        assertEquals(DeviationDirection.BELOW, sbp.direction)
        assertEquals(90.0, sbp.absoluteBelow!!, 1e-9)
        assertTrue(sbp.required)
        val hr = pattern.signalRules.single { it.metricType == MetricType.HEART_RATE }
        assertEquals(100.0, hr.absoluteAbove!!, 1e-9)
        assertTrue(hr.required)
    }

    @Test
    fun shock_screen_cites_sepsis3_and_esicm() {
        val refs = AcuteWindowPatterns.hypotensiveShockScreen.references
            .joinToString("\n")
        assertTrue("Singer 2016 Sepsis-3 citation missing", refs.contains("Singer M"))
        assertTrue("Cecconi 2014 ESICM citation missing", refs.contains("Cecconi"))
    }

    @Test
    fun shock_screen_uses_circulatory_compromise_framing_not_shock_diagnosis() {
        val title = AcuteWindowPatterns.hypotensiveShockScreen.title.lowercase()
        val action = AcuteWindowPatterns.hypotensiveShockScreen.suggestedAction!!
            .lowercase()
        // Title and action must reference circulatory compromise (data
        // observation), not assert shock as a diagnosis.
        assertTrue(
            "Title should use circulatory-compromise framing, not 'you are in shock'",
            title.contains("circulatory compromise") || title.contains("blood pressure"),
        )
        assertTrue(
            "Action should describe the BP/HR observation and refer to medical assessment",
            action.contains("circulatory compromise") || action.contains("medical"),
        )
        assertFalse(
            "Action must not assert 'you are in shock' as diagnosis",
            action.contains("you are in shock") || action.contains("you're in shock"),
        )
    }

    // -- citation hygiene across the four patterns --

    @Test
    fun every_acute_pattern_carries_action_with_immediate_medical_referral() {
        for (pattern in AcuteWindowPatterns.all) {
            val action = pattern.suggestedAction
            assertNotNull("${pattern.id} must ship a suggestedAction", action)
            val lower = action!!.lowercase()
            assertTrue(
                "${pattern.id} action should reference immediate medical assessment / emergency",
                lower.contains("immediate medical") ||
                    lower.contains("emergency") ||
                    lower.contains("seek immediate"),
            )
        }
    }

    @Test
    fun default_window_minutes_is_ten_per_audit_recommendation() {
        // Audit §2.4 anaphylaxis recommendation is the 5-min event with
        // a 5-min lookback margin; 10-minute window captures both ends.
        assertEquals(10, AcuteWindowPatterns.DEFAULT_WINDOW_MINUTES)
    }
}
