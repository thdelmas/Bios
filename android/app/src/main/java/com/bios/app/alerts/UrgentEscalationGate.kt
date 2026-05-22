package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.InterventionLevel
import com.bios.app.physiology.PhysiologyState

/**
 * Pure-function gate that decides whether the URGENT-tier
 * escalation pathway should be short-circuited (#184). Extracted
 * from [AlertManager] so callers — the manager itself today, the
 * future emergency-contact escalation worker added by #215
 * tomorrow — can apply the same decision without depending on
 * Android Context.
 *
 * The gate fires when **either**:
 *  - the owner's [PhysiologyState] is [PhysiologyState.HOSPICE_MODE], OR
 *  - the owner's declared [InterventionLevel] is
 *    [InterventionLevel.COMFORT_ONLY].
 *
 * Both conditions are owner-set, never inferred. The manifesto
 * stance ("the owner is final", "silence is a feature at end of
 * life") puts the gate here so the system honors what the owner
 * has already declared.
 *
 * Side effects of the gate (called out for the cross-PR coordination
 * with #215):
 *  - [AlertManager.sendNotification] silences the URGENT notification
 *    channel and emits on the low-importance NOTICE channel.
 *  - The URGENT-tier escalation worker added by #215 must consult
 *    [shouldSuppress] (or its derived `isUrgentEscalationSuppressed`
 *    accessor on [AlertManager]) before scheduling SMS-to-emergency-
 *    contact or tap-to-call work.
 *  - The owner can still see the alert in-app and act on it manually
 *    — the alert is suppressed *from auto-escalation*, not deleted.
 */
object UrgentEscalationGate {

    /**
     * Decide whether escalation should be suppressed for an alert
     * with the given tier, in the owner's current [state], given
     * their declared [interventionLevel].
     *
     * Non-URGENT tiers (NOTICE, ADVISORY, OBSERVATION) are returned
     * `false` unconditionally — the gate is scoped to URGENT.
     */
    fun shouldSuppress(
        tier: AlertTier,
        state: PhysiologyState,
        interventionLevel: InterventionLevel,
    ): Boolean {
        if (tier != AlertTier.URGENT) return false
        if (state == PhysiologyState.HOSPICE_MODE) return true
        if (interventionLevel == InterventionLevel.COMFORT_ONLY) return true
        return false
    }
}
