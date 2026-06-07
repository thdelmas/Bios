package com.bios.app.screening

import com.bios.app.model.RiskProfile
import com.bios.app.model.ScreeningEntry

/**
 * Owner-supplied demographic context the engine reads. Stored elsewhere
 * (SharedPreferences in the v1 UI) — passing it as a typed value here
 * keeps the engine pure and unit-testable without Android dependencies.
 *
 * Two anatomy gates live here for backwards compatibility during the
 * audit-driven migration to anatomy-direct routing (#209):
 *
 * - [presentsAs] is the legacy binary flag — still consulted when no
 *   [anatomy] profile is set so existing installs keep working.
 * - [anatomy] is the new per-organ profile. When set, it takes precedence
 *   over [presentsAs] and routes per screening key (cervix-yes →
 *   cervical, breast-yes → mammogram, prostate-yes → AAA / PSA). Owners
 *   are no longer asked "are you female-bodied?"; they're asked about
 *   each organ individually, the audit-explicit fix to the binary UI.
 *
 * The engine treats `null` for either field as "owner hasn't said" and
 * renders the affected entries as elective rather than filtering them
 * out.
 */
data class OwnerDemographics(
    val ageYears: Int,
    val presentsAs: Applicability? = null,
    val anatomy: AnatomyProfile? = null,
)

/** Result of evaluating one catalog entry against the owner's history. */
sealed class ScreeningStatus {
    /** Owner is outside the recommended age range (too young or aged-out). */
    data class NotEligible(val reason: String) : ScreeningStatus()
    /** Eligible and no record on file — owner hasn't logged this screening yet. */
    object NoRecord : ScreeningStatus()
    /** Eligible, on record, due within the next 30 days or already past due. */
    data class DueNow(val monthsSinceLast: Int, val monthsOverdue: Int) : ScreeningStatus()
    /** Eligible, on record, current; next due in [monthsUntilDue] months. */
    data class Current(val monthsSinceLast: Int, val monthsUntilDue: Int) : ScreeningStatus()

    /**
     * [CadenceKind.MIN_INTERVAL_SINCE_LAST] only: on record, but the
     * recommended delay since the last occurrence hasn't elapsed yet.
     * Neutral — "recommended again in [monthsUntilEligible] months". This
     * is *not* an overdue state and callers must never render it as one.
     */
    data class WithinInterval(val monthsSinceLast: Int, val monthsUntilEligible: Int) : ScreeningStatus()

    /**
     * [CadenceKind.MIN_INTERVAL_SINCE_LAST] only: on record and the
     * recommended delay has elapsed — eligible to repeat. Still neutral:
     * a recommended delay you can't be "late" for (manifesto no-shame
     * rule). Distinct from [DueNow], which carries overdue urgency.
     */
    data class IntervalElapsed(val monthsSinceLast: Int) : ScreeningStatus()
}

/**
 * Pure cadence-status calculator (#155). Closes audit gap §2.2.
 *
 * For each catalog entry, computes whether the owner is eligible (age
 * range + applicability), whether they have an entry on file, and how
 * the most-recent entry's date compares to the recommended cadence. No
 * I/O, no DB, no clock side effects — `now` is a parameter so the test
 * suite can pin time.
 *
 * Manifesto guard: this engine *never* pushes. Callers must surface
 * results on a pull-side screen the owner navigates into.
 */
object ScreeningCadenceEngine {

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val DAYS_PER_MONTH = 30.4375  // mean Gregorian month
    /**
     * Owners within this many days of (or already past) the recommended
     * cadence anniversary are surfaced as `DueNow` rather than `Current`.
     */
    private const val DUE_NOW_GRACE_DAYS = 30

    /**
     * Evaluate a single catalog entry against the owner's demographics,
     * risk profile, and the latest entry for that screening (or `null`
     * if none on file). When [riskProfile] is null the engine treats
     * every [RiskGate] other than [RiskGate.NONE] as gated-out — the
     * owner hasn't filled the risk-profile screen yet, so we can't
     * surface a hereditary cadence.
     */
    fun evaluate(
        entry: ScreeningCatalogEntry,
        demographics: OwnerDemographics,
        latest: ScreeningEntry?,
        riskProfile: RiskProfile? = null,
        now: Long = System.currentTimeMillis(),
    ): ScreeningStatus {
        // Risk gate — evaluated first so hereditary entries are filtered
        // out before any age check fires. Hides the entry entirely (the
        // UI drops `NotEligible` rows with this reason from the default
        // view) so non-carriers don't see the long syndrome list.
        if (entry.riskGate != RiskGate.NONE && !satisfiesRiskGate(entry.riskGate, riskProfile)) {
            return ScreeningStatus.NotEligible(
                reason = riskGateReason(entry.riskGate),
            )
        }

        // Age gate.
        if (demographics.ageYears < entry.minAge) {
            return ScreeningStatus.NotEligible(
                reason = "Recommended from age ${entry.minAge}",
            )
        }
        if (entry.maxAge != null && demographics.ageYears > entry.maxAge) {
            return ScreeningStatus.NotEligible(
                reason = "Recommended ${entry.minAge}–${entry.maxAge}",
            )
        }

        // Applicability gate. Two routes:
        //
        // 1. Anatomy-direct (#209). When the owner has set an
        //    [AnatomyProfile], we route per screening key — cervix=yes
        //    gates cervical; prostate=yes gates AAA; etc. This is the
        //    audit-explicit replacement for the binary female/male
        //    bucket UI in PreventiveCareScreen.
        // 2. Legacy [presentsAs] fallback for owners on the old UI who
        //    haven't yet visited the demographics dialog after the
        //    anatomy-question redesign. Same semantics as before:
        //    matching presentsAs → eligible, mismatching → not.
        //
        // In both routes, a fully-unset answer is treated as "elective"
        // — the screen shows the entry and lets the owner decide.
        if (entry.applicability != Applicability.UNIVERSAL) {
            val anatomy = demographics.anatomy
            if (anatomy != null) {
                when (anatomy.resolveForKey(entry.key)) {
                    ApplicabilityResolution.NotApplicable ->
                        return ScreeningStatus.NotEligible(
                            reason = "Anatomy-specific recommendation",
                        )
                    ApplicabilityResolution.Match,
                    ApplicabilityResolution.Unset -> {
                        // Fall through to the cadence / record check.
                    }
                }
            } else if (demographics.presentsAs != null &&
                demographics.presentsAs != entry.applicability
            ) {
                return ScreeningStatus.NotEligible(
                    reason = "Anatomy-specific recommendation",
                )
            }
        }

        // No record on file.
        if (latest == null) return ScreeningStatus.NoRecord

        // Cadence arithmetic.
        val daysSinceLast = ((now - latest.performedDate) / MILLIS_PER_DAY).toInt().coerceAtLeast(0)
        val monthsSinceLast = (daysSinceLast / DAYS_PER_MONTH).toInt()
        val cadenceDays = (entry.cadenceMonths * DAYS_PER_MONTH).toInt()
        val daysUntilDue = cadenceDays - daysSinceLast

        // Minimum-interval entries (routine checkups) never read as
        // overdue: before the recommended delay elapses they're
        // WithinInterval, after it they're IntervalElapsed — both neutral.
        if (entry.cadenceKind == CadenceKind.MIN_INTERVAL_SINCE_LAST) {
            return if (daysUntilDue > 0) {
                ScreeningStatus.WithinInterval(
                    monthsSinceLast = monthsSinceLast,
                    monthsUntilEligible = (daysUntilDue / DAYS_PER_MONTH).toInt(),
                )
            } else {
                ScreeningStatus.IntervalElapsed(monthsSinceLast = monthsSinceLast)
            }
        }

        return if (daysUntilDue <= DUE_NOW_GRACE_DAYS) {
            val monthsOverdue = if (daysUntilDue >= 0) 0 else (-daysUntilDue / DAYS_PER_MONTH).toInt()
            ScreeningStatus.DueNow(
                monthsSinceLast = monthsSinceLast,
                monthsOverdue = monthsOverdue,
            )
        } else {
            ScreeningStatus.Current(
                monthsSinceLast = monthsSinceLast,
                monthsUntilDue = (daysUntilDue / DAYS_PER_MONTH).toInt(),
            )
        }
    }

    /**
     * Convenience: evaluate the entire catalog. The caller supplies a
     * lookup function for "latest screening matching this key" so the
     * engine stays pure (no repo dependency). [riskProfile] is optional
     * so legacy callers (USPSTF-only screen) keep working without
     * changes; the extended catalog (#191) passes the real profile so
     * the risk-gate filter fires.
     */
    fun evaluateAll(
        catalog: List<ScreeningCatalogEntry>,
        demographics: OwnerDemographics,
        latestByKey: (String) -> ScreeningEntry?,
        riskProfile: RiskProfile? = null,
        now: Long = System.currentTimeMillis(),
    ): List<Pair<ScreeningCatalogEntry, ScreeningStatus>> {
        return catalog.map { entry ->
            entry to evaluate(entry, demographics, latestByKey(entry.key), riskProfile, now)
        }
    }

    /**
     * Returns `true` when the owner's [RiskProfile] flag matching the
     * gate is set. Treats a null profile as "no flags set" — hereditary
     * entries stay hidden until the owner records their syndrome on the
     * risk-profile screen.
     */
    internal fun satisfiesRiskGate(gate: RiskGate, profile: RiskProfile?): Boolean {
        if (gate == RiskGate.NONE) return true
        if (profile == null) return false
        return when (gate) {
            RiskGate.NONE -> true
            RiskGate.LYNCH_SYNDROME -> profile.lynchSyndrome
            RiskGate.LI_FRAUMENI -> profile.liFraumeni
            RiskGate.FAP -> profile.fapFamilialAdenomatousPolyposis
            RiskGate.COWDEN -> profile.cowdenSyndrome
            RiskGate.PEUTZ_JEGHERS -> profile.peutzJeghersSyndrome
            RiskGate.VHL -> profile.vhlVonHippelLindau
            RiskGate.MEN1 -> profile.men1
            RiskGate.MEN2A -> profile.men2A
            RiskGate.MEN2B -> profile.men2B
            RiskGate.HDGC -> profile.hdgcHereditaryDiffuseGastric
            RiskGate.IHS_DIABETIC_AIAN -> profile.ihsDiabeticAianStatus
            RiskGate.EVER_SMOKER ->
                profile.personalTobaccoYears != null || profile.personalTobaccoPackYears != null
        }
    }

    /**
     * Human-readable explanation for why a risk-gated entry was hidden,
     * surfaced on debug/eligible screens. Owner-facing wording stays
     * neutral — Bios never asserts a diagnosis, only restates the gate.
     */
    private fun riskGateReason(gate: RiskGate): String = when (gate) {
        RiskGate.NONE -> ""
        RiskGate.LYNCH_SYNDROME -> "Requires owner-recorded Lynch syndrome"
        RiskGate.LI_FRAUMENI -> "Requires owner-recorded Li-Fraumeni syndrome"
        RiskGate.FAP -> "Requires owner-recorded familial adenomatous polyposis"
        RiskGate.COWDEN -> "Requires owner-recorded Cowden syndrome"
        RiskGate.PEUTZ_JEGHERS -> "Requires owner-recorded Peutz-Jeghers syndrome"
        RiskGate.VHL -> "Requires owner-recorded von Hippel-Lindau"
        RiskGate.MEN1 -> "Requires owner-recorded MEN1"
        RiskGate.MEN2A -> "Requires owner-recorded MEN2A"
        RiskGate.MEN2B -> "Requires owner-recorded MEN2B"
        RiskGate.HDGC -> "Requires owner-recorded hereditary diffuse gastric cancer"
        RiskGate.IHS_DIABETIC_AIAN -> "Requires owner-recorded diabetic AI/AN status"
        RiskGate.EVER_SMOKER -> "Requires owner-recorded smoking history"
    }
}
