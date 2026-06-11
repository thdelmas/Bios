package com.bios.app.screening

import com.bios.contracts.MetricType

/**
 * Maps a checkup / screening key to the metric readings that, when present,
 * already count as that checkup having been performed — so the owner is
 * never asked to re-log data Bios already holds.
 *
 * The canonical case: a blood-pressure reading (manual cuff entry, Withings,
 * an imported ER vital) *is* a blood-pressure check. Without this link the
 * Preventive Care screen would show "No record yet" for `blood_pressure_check`
 * while a fresh systolic/diastolic reading sits in the metric store one
 * screen over. The UI merges the most-recent derived date with any manual
 * [com.bios.app.model.ScreeningEntry]; most-recent wins.
 *
 * Pure data, no Android dependency — the lookup + merge live in
 * `PreventiveCareData` so the engine stays a pure function. Closes #400.
 */
object CheckupHealthDataLink {

    /** screening key → metric keys whose latest reading satisfies it. */
    val metricKeysByScreeningKey: Map<String, List<String>> = mapOf(
        "blood_pressure_check" to listOf(
            MetricType.BLOOD_PRESSURE_SYSTOLIC.key,
            MetricType.BLOOD_PRESSURE_DIASTOLIC.key,
        ),
    )
}
