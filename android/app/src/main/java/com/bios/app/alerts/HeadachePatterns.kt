package com.bios.app.alerts

import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.model.HeadacheLog
import com.bios.app.model.MigraineAttack
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Medication-overuse-headache (MOH) screen, neurology owner-symptom-logging
 * pattern bundle (issue #207, neurology audit §2.6 + §2.5).
 *
 * **IHS ICHD-3 §8.2** defines MOH as "headache present on ≥15 days per
 * month in a patient with a pre-existing primary headache disorder, in
 * association with regular overuse for >3 months of one or more drugs
 * that can be taken for acute and/or symptomatic treatment of headache."
 * The acute-treatment threshold is **≥10 acute-headache-medication days
 * per month for triptans / opioids / combination analgesics / ergots**,
 * or ≥15 days/month for plain simple analgesics. Bios uses the more
 * conservative ≥10 days/month threshold across all acute treatments —
 * the goal is *screening*, not classification, and the owner can read
 * the IHS substrate themselves if they want the simple-vs-combination
 * distinction.
 *
 * Three months of overuse — not one — is what the IHS definition
 * requires. The screen reads the **last 90 days** of acute-treatment
 * days and fires only when *every* one of the most-recent 3 calendar
 * months (counted backward from "today") meets the ≥10-day threshold.
 *
 * **Manifesto framing.** This screen is a **data statement**, never a
 * diagnostic claim. "Acute-headache-treatment days in the last 3 calendar
 * months meet the IHS medication-overuse-headache screening threshold."
 * The owner reads the pattern; the owner decides what to do. Bios never
 * tells the owner "you have MOH" — the language stays strictly factual
 * per [AlertContentPolicy].
 *
 * **Severity.** ADVISORY. MOH is a chronic-pattern condition, not an
 * acute medical event; URGENT escalation would be a category mistake.
 *
 * **No SignalRule shape.** Unlike most patterns in this directory, MOH
 * cannot be evaluated by the standard signal-rule pipeline: the inputs
 * live in [com.bios.app.model.MigraineAttack] and
 * [com.bios.app.model.HeadacheLog] rows (one per attack / headache), not
 * in a continuous metric stream. The pattern definition below is
 * authoritative for the alert *text* (title, explanation,
 * suggestedAction, references) and the [AlertContentPolicy] check;
 * actual evaluation is handled by
 * [MedicationOveruseHeadacheEvaluator.evaluate], which is a separate
 * pure-Kotlin scoring function the alerts / journal worker can call.
 *
 * ## References
 *  - IHS International Classification of Headache Disorders, 3rd edition
 *    (ICHD-3) §8.2 — Medication-overuse headache.
 *  - Diener HC et al. (2016) — Pathophysiology, prevention, and treatment
 *    of medication overuse headache. Lancet Neurology 15(1):85-93.
 *  - Vandenbussche N et al. (2018) — Medication-overuse headache: a
 *    widely recognized entity amidst ongoing debate. J Headache Pain.
 *
 * All text obeys [AlertContentPolicy].
 */
object HeadachePatterns {

    val all by lazy { listOf(medicationOveruseHeadacheScreen) }

    /**
     * IHS ICHD-3 §8.2 MOH screening threshold: acute-headache-medication
     * days per month. ≥10 is the triptan / opioid / combination-analgesic
     * cutoff; plain simple analgesics are ≥15 in the standard. Bios uses
     * the more conservative single threshold of ≥10 across all acute
     * treatments — a screening-not-classification stance.
     */
    const val MOH_DAYS_PER_MONTH_THRESHOLD: Int = 10

    /**
     * IHS ICHD-3 §8.2 requires the overuse pattern across **>3 months**.
     * The evaluator checks the most-recent 3 calendar months — the
     * minimum span the definition admits.
     */
    const val MOH_CONSECUTIVE_MONTHS_REQUIRED: Int = 3

    /**
     * Rolling window the evaluator pulls from the diary repos. 100 days
     * comfortably covers 3 consecutive calendar months in any timezone
     * even when the current date is on the first of the month.
     */
    const val MOH_WINDOW_DAYS: Int = 100

    /**
     * MOH screen pattern. Severity ADVISORY (chronic pattern, not acute
     * event). Manifesto-clean: data-statement framing, no diagnostic
     * claim, no second-person judgment.
     *
     * No SignalRules — see file-level KDoc. The pattern is registered for
     * the alert-text surface and AlertContentPolicy compliance; the
     * actual evaluation is in [MedicationOveruseHeadacheEvaluator].
     */
    val medicationOveruseHeadacheScreen = ConditionPattern(
        id = "medication_overuse_headache_screen",
        title = "Acute-headache-treatment days meet MOH screening threshold",
        category = ConditionCategory.MENTAL_HEALTH,
        signalRules = emptyList(),
        minActiveSignals = 0,
        severityFloor = AlertTier.ADVISORY,
        explanation = "Diary entries show 10 or more days per month of acute headache treatment (triptan, NSAID, paracetamol, ergot, opioid, or combination analgesic) across the most recent 3 calendar months. The IHS International Classification of Headache Disorders, 3rd edition (ICHD-3) §8.2 uses that threshold as the medication-overuse-headache screen. This is a data observation against the published clinical screen — not a medication-overuse-headache diagnosis. The owner is the one who decides whether to discuss this pattern with a neurologist or primary-care clinician.",
        suggestedAction = "If headache frequency has been rising despite acute treatment, a neurology or primary-care visit can review the medication pattern and discuss preventive options. Bring the diary entries — the full record of dates, intensities, and what was taken is the substrate the clinical conversation needs.",
        references = listOf(
            "IHS International Classification of Headache Disorders, 3rd edition (ICHD-3) §8.2 — Medication-overuse headache",
            "Diener HC et al. (2016) — Pathophysiology, prevention, and treatment of medication overuse headache. Lancet Neurology",
            "Vandenbussche N et al. (2018) — Medication-overuse headache: a widely recognized entity amidst ongoing debate. J Headache Pain",
        ),
        earlyDetection = "MOH develops over months: acute treatment gradually shifts from rescue-medication-when-needed to near-daily use as the underlying headache pattern intensifies. The IHS ICHD-3 §8.2 10-or-more acute-treatment-days-per-month threshold for triptans / combination analgesics / opioids / ergots, sustained across 3 consecutive months, is the published screening criterion. The structured diary surface in Bios records every acute treatment the owner logs (whether logged as a migraine attack or a generic headache); the screen runs over a 90-day window and surfaces only when the pattern holds across every one of the most-recent 3 calendar months.",
        prevention = "The headache-medicine literature converges on a few protective patterns: limit acute medication use to no more than 2 days per week for triptans, opioids, and combination analgesics; avoid daily simple-analgesic use across consecutive weeks; treat the underlying primary-headache disorder with a preventive medication when frequency is climbing (a neurologist's call, not Bios's). Consistent sleep and hydration, identified-trigger avoidance (the migraine trigger taxonomy in the diary), and stress management all reduce the upstream attack rate that drives acute-treatment use.",
        healing = "Withdrawal of the overused acute medication is the IHS-recommended first step — usually a 2-8 week transient worsening of headache as the central sensitisation reverses, then improvement to baseline pre-overuse frequency. This is a neurologist-supervised process for triptan / opioid / barbiturate / ergot overuse; primary-care guidance is sufficient for simple-analgesic overuse. A preventive medication started during or after withdrawal reduces the relapse rate. The diary continues to be useful through the withdrawal period — the headache trajectory across weeks is exactly what the neurologist needs to read.",
        risks = "Sustained acute-medication overuse worsens the underlying primary headache: the central pain pathways become more sensitised, attacks become more frequent and harder to abort, and the medication that initially worked stops working. Long-term overuse of combination analgesics, opioids, and barbiturate-containing acute treatments carries its own toxicity (gastrointestinal, hepatic, dependence). The reversibility of MOH on withdrawal is the headache-medicine literature's main reason for surfacing the pattern early — the earlier the screen fires, the shorter the withdrawal course and the lower the relapse risk.",
    )
}

/**
 * Pure-Kotlin MOH evaluator. Counts acute-headache-treatment days per
 * calendar month across [MigraineAttack] and [HeadacheLog] entries, and
 * reports whether the IHS ICHD-3 §8.2 screening criterion is met across
 * the most-recent [HeadachePatterns.MOH_CONSECUTIVE_MONTHS_REQUIRED]
 * calendar months.
 *
 * Decoupled from the database / repos so the math is directly unit-
 * testable. A worker passes in the [MigraineAttack] / [HeadacheLog]
 * lists from the rolling-window fetch and reads back the verdict.
 *
 * **An "acute-headache-treatment day"** is a calendar date (in the
 * supplied [java.time.ZoneId]) on which **at least one** [MigraineAttack] or
 * [HeadacheLog] has a non-null, non-blank [MigraineAttack.medicationTaken]
 * / [HeadacheLog.medicationTaken]. A single date counts as exactly one
 * treatment day regardless of how many separate doses or entries the
 * owner recorded.
 *
 * Calendar months are bounded by the supplied [java.time.ZoneId].
 */
object MedicationOveruseHeadacheEvaluator {

    /**
     * Evaluation result. [meetsScreeningThreshold] is true when every
     * one of the most-recent 3 calendar months has the configured
     * minimum acute-treatment days; [perMonthCounts] is the per-month
     * breakdown the alert explanation surface uses to show the owner
     * exactly what the screen counted.
     */
    data class Verdict(
        val meetsScreeningThreshold: Boolean,
        /**
         * Per-calendar-month treatment-day counts, newest first. Indexed
         * by (year, month) where month is 1..12.
         */
        val perMonthCounts: List<MonthCount>,
    )

    /** Per-calendar-month acute-treatment-day count. */
    data class MonthCount(
        val year: Int,
        val month: Int,
        val treatmentDays: Int,
    )

    /**
     * Evaluate the MOH screen across the given diary records.
     *
     * @param migraineAttacks records to count
     * @param headacheLogs records to count
     * @param nowMillis "now" for picking the most-recent 3 calendar months
     * @param zoneId timezone for calendar-month boundaries (defaults to system default)
     */
    fun evaluate(
        migraineAttacks: List<MigraineAttack>,
        headacheLogs: List<HeadacheLog>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Verdict {
        // Build the set of (year, month, dayOfMonth) tuples with a non-
        // blank medication entry, then count distinct dates per month.
        val treatmentDates = mutableSetOf<TreatmentDate>()

        for (attack in migraineAttacks) {
            if (!attack.medicationTaken.isNullOrBlank()) {
                treatmentDates += treatmentDate(attack.onsetTimestamp, zoneId)
            }
        }
        for (log in headacheLogs) {
            if (!log.medicationTaken.isNullOrBlank()) {
                treatmentDates += treatmentDate(log.timestamp, zoneId)
            }
        }

        val targetMonths = recentCalendarMonths(
            nowMillis = nowMillis,
            zoneId = zoneId,
            count = HeadachePatterns.MOH_CONSECUTIVE_MONTHS_REQUIRED,
        )

        val perMonthCounts = targetMonths.map { (year, month) ->
            MonthCount(
                year = year,
                month = month,
                treatmentDays = treatmentDates.count { it.year == year && it.month == month },
            )
        }

        val meets = perMonthCounts.size == HeadachePatterns.MOH_CONSECUTIVE_MONTHS_REQUIRED &&
            perMonthCounts.all { it.treatmentDays >= HeadachePatterns.MOH_DAYS_PER_MONTH_THRESHOLD }

        return Verdict(meetsScreeningThreshold = meets, perMonthCounts = perMonthCounts)
    }

    private data class TreatmentDate(val year: Int, val month: Int, val day: Int)

    private fun treatmentDate(epochMillis: Long, zoneId: ZoneId): TreatmentDate {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId)
        return TreatmentDate(zdt.year, zdt.monthValue, zdt.dayOfMonth)
    }

    /**
     * The most-recent [count] calendar months ending at [nowMillis],
     * newest first. Returned as (year, month) pairs where month is 1..12.
     */
    private fun recentCalendarMonths(
        nowMillis: Long,
        zoneId: ZoneId,
        count: Int,
    ): List<Pair<Int, Int>> {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        return (0 until count).map { offset ->
            val month = now.minusMonths(offset.toLong())
            month.year to month.monthValue
        }
    }

    /**
     * Build a manifesto-clean alert message body summarising the per-
     * month treatment-day counts. Returned text obeys
     * [AlertContentPolicy] — data statements only, no second-person
     * judgments.
     */
    fun describeVerdict(verdict: Verdict): String {
        if (verdict.perMonthCounts.isEmpty()) return ""
        val monthLine = verdict.perMonthCounts.joinToString("; ") { mc ->
            "${monthLabel(mc.year, mc.month)}: ${mc.treatmentDays} acute-treatment days"
        }
        return "Per-month acute-treatment-day counts (most recent first): $monthLine. IHS ICHD-3 §8.2 screening threshold is ${HeadachePatterns.MOH_DAYS_PER_MONTH_THRESHOLD} days/month across ${HeadachePatterns.MOH_CONSECUTIVE_MONTHS_REQUIRED} consecutive months."
    }

    private fun monthLabel(year: Int, month: Int): String {
        val mm = java.time.Month.of(month).getDisplayName(
            java.time.format.TextStyle.SHORT,
            java.util.Locale.US,
        )
        return "$mm $year"
    }
}
