package com.bios.app

import com.bios.app.alerts.UrgentEscalationGate
import com.bios.app.model.AlertTier
import com.bios.app.model.InterventionLevel
import com.bios.app.physiology.PhysiologyState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [UrgentEscalationGate] (#184). The gate is the single
 * source of truth for whether URGENT-tier alerts should
 * auto-escalate to emergency contacts. It is consulted by
 * [com.bios.app.alerts.AlertManager.sendNotification] today and
 * will be consulted by the URGENT-escalation worker introduced
 * in PR #215 (cross-PR contract).
 *
 * Manifesto contract: the gate fires only on owner-declared state.
 * A regression that lets the gate close on an inferred condition
 * would violate "no hidden agendas" / "owner is final".
 */
class UrgentEscalationGateTest {

    // -- HOSPICE_MODE suppresses --

    @Test
    fun urgent_with_hospice_mode_is_suppressed() {
        // The audit's primary case: cancer patient in home hospice,
        // wearable detects bradycardia, Bios must NOT call EMS.
        assertTrue(
            UrgentEscalationGate.shouldSuppress(
                tier = AlertTier.URGENT,
                state = PhysiologyState.HOSPICE_MODE,
                interventionLevel = InterventionLevel.UNSPECIFIED,
            )
        )
    }

    @Test
    fun urgent_with_hospice_mode_suppresses_regardless_of_intervention_level() {
        // Hospice mode is independent of GoalsOfCare — the owner may
        // have toggled hospice mode without filling out the
        // intervention-level dropdown. Either path closes the gate.
        for (level in InterventionLevel.entries) {
            assertTrue(
                "Hospice mode should suppress regardless of level=$level",
                UrgentEscalationGate.shouldSuppress(
                    tier = AlertTier.URGENT,
                    state = PhysiologyState.HOSPICE_MODE,
                    interventionLevel = level,
                )
            )
        }
    }

    // -- COMFORT_ONLY suppresses --

    @Test
    fun urgent_with_comfort_only_intervention_level_is_suppressed() {
        // Symmetric path: the owner declared COMFORT_ONLY in goals of
        // care without flipping the dedicated hospice-mode toggle.
        // Same effect.
        assertTrue(
            UrgentEscalationGate.shouldSuppress(
                tier = AlertTier.URGENT,
                state = PhysiologyState.STANDARD,
                interventionLevel = InterventionLevel.COMFORT_ONLY,
            )
        )
    }

    @Test
    fun urgent_with_full_or_selective_intervention_does_not_suppress() {
        // FULL and SELECTIVE both leave the URGENT pathway open —
        // the owner has not declared comfort-only care.
        for (level in listOf(InterventionLevel.FULL, InterventionLevel.SELECTIVE)) {
            assertFalse(
                "Level=$level should not suppress",
                UrgentEscalationGate.shouldSuppress(
                    tier = AlertTier.URGENT,
                    state = PhysiologyState.STANDARD,
                    interventionLevel = level,
                )
            )
        }
    }

    @Test
    fun urgent_with_unspecified_intervention_does_not_suppress() {
        // Owner hasn't declared anything → default escalation
        // behaviour applies. This is the load-bearing case for
        // owners who never visit the goals-of-care screen.
        assertFalse(
            UrgentEscalationGate.shouldSuppress(
                tier = AlertTier.URGENT,
                state = PhysiologyState.STANDARD,
                interventionLevel = InterventionLevel.UNSPECIFIED,
            )
        )
    }

    // -- Non-URGENT tiers are never gated --

    @Test
    fun notice_and_advisory_tiers_are_never_suppressed_by_the_gate() {
        // The gate is scoped to URGENT only. Lower-tier alerts are
        // not auto-escalating to begin with, so the gate has no
        // business touching them.
        for (tier in listOf(AlertTier.OBSERVATION, AlertTier.NOTICE, AlertTier.ADVISORY)) {
            assertFalse(
                "Tier=$tier should not be gated even in hospice mode",
                UrgentEscalationGate.shouldSuppress(
                    tier = tier,
                    state = PhysiologyState.HOSPICE_MODE,
                    interventionLevel = InterventionLevel.COMFORT_ONLY,
                )
            )
        }
    }

    // -- Cross-state regressions --

    @Test
    fun urgent_in_standard_state_with_default_goals_is_not_suppressed() {
        // The "happy path" — no goals of care declared, no hospice
        // mode. URGENT alerts must auto-escalate as before. A
        // regression here would silence URGENT alerts for owners
        // who never opted in.
        assertFalse(
            UrgentEscalationGate.shouldSuppress(
                tier = AlertTier.URGENT,
                state = PhysiologyState.STANDARD,
                interventionLevel = InterventionLevel.UNSPECIFIED,
            )
        )
    }

    @Test
    fun urgent_in_pregnancy_does_not_suppress() {
        // Pregnancy is a physiology state that gates *patterns* (not
        // escalation). The escalation gate must remain open in
        // pregnancy / postpartum / athlete / paediatric / frailty —
        // the audit's hospice scope does not extend to those states.
        for (state in listOf(
            PhysiologyState.PREGNANCY_T1,
            PhysiologyState.PREGNANCY_T2,
            PhysiologyState.PREGNANCY_T3,
            PhysiologyState.POSTPARTUM,
            PhysiologyState.ATHLETE_HIGH_FITNESS,
            PhysiologyState.FRAILTY_FLAG,
            PhysiologyState.PAEDIATRIC,
        )) {
            assertFalse(
                "State=$state should not suppress with default goals",
                UrgentEscalationGate.shouldSuppress(
                    tier = AlertTier.URGENT,
                    state = state,
                    interventionLevel = InterventionLevel.UNSPECIFIED,
                )
            )
        }
    }
}
