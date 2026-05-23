package com.bios.app.screening

import com.bios.app.model.ImmunizationRecord
import com.bios.app.ui.immunisations.DoseRecommendation
import com.bios.app.ui.immunisations.PaediatricVaccineSchedule
import com.bios.app.ui.immunisations.VaccineSchedule

/**
 * Status of a single paediatric vaccine dose for a single child (#191).
 * Pull-side rendering — surfaced on the cadence screen the owner
 * navigates into, never pushed.
 */
sealed class DoseStatus {
    /** Dose recorded — owner already has this dose in [ImmunizationRecord]. */
    data class Recorded(val occurrenceDate: Long) : DoseStatus()
    /** Child hasn't reached the minimum age for this dose yet. */
    object NotYetEligible : DoseStatus()
    /** Child is in the dose's recommended age band and has no record. */
    object DueNow : DoseStatus()
    /** Child has passed the recommended max age for this dose. */
    data class Overdue(val monthsOverdue: Int) : DoseStatus()
}

/**
 * Pure cadence calculator for the paediatric ACIP schedule (#191).
 * Closes the paediatrics-audit gap §2.6. Mirrors the adult cadence
 * engine's design: no I/O, no DB, `now` is a parameter so tests can
 * pin time.
 *
 * The owner's records for a given child arrive as a list of
 * [ImmunizationRecord] (filtered to this child elsewhere — Bios is
 * one-owner per device, so for the typical paediatric use the
 * "child" is the owner themselves). The engine matches by CVX code +
 * dose number; free-text-only records (no CVX) are ignored for
 * cadence computation because the dose-number anchor is required.
 */
object PaediatricVaccineCadenceEngine {

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val DAYS_PER_MONTH = 30.4375

    /**
     * Returns the dose-status list for one [VaccineSchedule] in the
     * order doses appear in the schedule.
     *
     * @param childBirthDate epoch millis of the child's date of birth
     * @param now epoch millis of "evaluation time"
     */
    fun evaluate(
        schedule: VaccineSchedule,
        childBirthDate: Long,
        records: List<ImmunizationRecord>,
        now: Long = System.currentTimeMillis(),
    ): List<Pair<DoseRecommendation, DoseStatus>> {
        val matching = records
            .filter { it.cvxCode != null && it.cvxCode == schedule.cvxCode }
            .sortedBy { it.occurrenceDate }
        val ageMonths = monthsBetween(childBirthDate, now)
        return schedule.doses.map { dose ->
            val recorded = matching.firstOrNull { it.doseNumber == dose.doseNumber }
            val status = when {
                recorded != null -> DoseStatus.Recorded(recorded.occurrenceDate)
                ageMonths < dose.minAgeMonths -> DoseStatus.NotYetEligible
                dose.maxAgeMonths != null && ageMonths > dose.maxAgeMonths ->
                    DoseStatus.Overdue(monthsOverdue = ageMonths - dose.maxAgeMonths)
                else -> DoseStatus.DueNow
            }
            dose to status
        }
    }

    /**
     * Convenience wrapper that evaluates every schedule in
     * [PaediatricVaccineSchedule.entries].
     */
    fun evaluateAll(
        childBirthDate: Long,
        records: List<ImmunizationRecord>,
        now: Long = System.currentTimeMillis(),
    ): List<Triple<VaccineSchedule, DoseRecommendation, DoseStatus>> =
        PaediatricVaccineSchedule.entries.flatMap { schedule ->
            evaluate(schedule, childBirthDate, records, now).map { (dose, status) ->
                Triple(schedule, dose, status)
            }
        }

    /** Whole-month age difference. Truncates toward zero. */
    internal fun monthsBetween(earlier: Long, later: Long): Int {
        if (later <= earlier) return 0
        val days = (later - earlier) / MILLIS_PER_DAY
        return (days / DAYS_PER_MONTH).toInt()
    }
}
