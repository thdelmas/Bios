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
 * screen over. The same holds for the lab-backed screenings: a FHIR lab
 * import (#396) or a manual biomarker entry for HbA1c, a lipid component, or
 * Lp(a) *is* the corresponding screening, drawn on the reading's date. The
 * UI merges the most-recent derived date with any manual
 * [com.bios.app.model.ScreeningEntry]; most-recent wins.
 *
 * Only screenings a single reading can actually satisfy are linked. Imaging
 * / cytology / endoscopy screenings (mammogram, cervical, colorectal, DEXA)
 * and the composite WHO cardiovascular-risk assessment have no one metric
 * that stands in for "the test was done", so they stay owner-entered.
 *
 * A lipid panel is satisfied by *any* of its component analytes — labs vary
 * in which they report — so the merge in `PreventiveCareData` takes the
 * most-recent across the listed keys.
 *
 * Pure data, no Android dependency — the lookup + merge live in
 * `PreventiveCareData` so the engine stays a pure function. Closes #400;
 * lab-backed links extend it.
 */
object CheckupHealthDataLink {

    /** screening key → metric keys whose latest reading satisfies it. */
    val metricKeysByScreeningKey: Map<String, List<String>> = mapOf(
        "blood_pressure_check" to listOf(
            MetricType.BLOOD_PRESSURE_SYSTOLIC.key,
            MetricType.BLOOD_PRESSURE_DIASTOLIC.key,
        ),
        "hba1c" to listOf(
            MetricType.HBA1C.key,
        ),
        "lipid_panel" to listOf(
            MetricType.TOTAL_CHOLESTEROL.key,
            MetricType.LDL_CHOLESTEROL.key,
            MetricType.HDL_CHOLESTEROL.key,
            MetricType.TRIGLYCERIDES.key,
        ),
        "lpa_one_time" to listOf(
            MetricType.LIPOPROTEIN_A.key,
        ),
    )
}
