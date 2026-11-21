package com.bios.app.ui.vessel

import com.bios.contracts.MetricType

/**
 * Read-only descriptor for one Vessel Watch dial: the measurement it shows
 * ([primaryKey] / [secondaryKey]), how a value gets there ([availability])
 * and one line saying where it comes from ([note]). A dial has no field that
 * could be summed across dials.
 */
data class VesselDial(
    val label: String,
    val availability: DialAvailability,
    val note: String,
    val primaryKey: MetricType,
    /** Second key shown alongside the first, e.g. diastolic rendered sys/dia. */
    val secondaryKey: MetricType? = null,
)

/** How a dial gets its value; drives the state label of an empty dial. */
enum class DialAvailability {
    /** A sensor or sync produces this; empty means waiting for a first reading. */
    INSTRUMENTED,

    /** Derived on demand from a one-off capture (camera PPG). */
    DERIVED_ON_DEMAND,

    /** The owner enters it (cuff reading, lab result). */
    NEEDS_ENTRY,
}

/** One titled group of dials. */
data class VesselGroup(
    val title: String,
    val dials: List<VesselDial>,
)

/**
 * The dial catalog. Every label is the name of a measurement, and every
 * dial reads a real [MetricType] constant, so a renamed or removed key
 * breaks compilation rather than silently dropping a dial.
 */
val vesselWatchGroups: List<VesselGroup> = listOf(
    VesselGroup(
        title = "Heart and circulation",
        dials = listOf(
            VesselDial(
                label = "Blood pressure",
                availability = DialAvailability.NEEDS_ENTRY,
                note = "Manual cuff entry — Log → Blood pressure.",
                primaryKey = MetricType.BLOOD_PRESSURE_SYSTOLIC,
                secondaryKey = MetricType.BLOOD_PRESSURE_DIASTOLIC,
            ),
            VesselDial(
                label = "Resting heart rate",
                availability = DialAvailability.INSTRUMENTED,
                note = "Wearable → Health Connect.",
                primaryKey = MetricType.RESTING_HEART_RATE,
            ),
            VesselDial(
                label = "HRV (RMSSD)",
                availability = DialAvailability.INSTRUMENTED,
                note = "Captured nightly.",
                primaryKey = MetricType.HEART_RATE_VARIABILITY,
            ),
            VesselDial(
                label = "SpO₂",
                availability = DialAvailability.INSTRUMENTED,
                note = "Captured nightly.",
                primaryKey = MetricType.BLOOD_OXYGEN,
            ),
            VesselDial(
                label = "Augmentation index (camera)",
                availability = DialAvailability.DERIVED_ON_DEMAND,
                note = "Run a camera capture from Log.",
                primaryKey = MetricType.AUGMENTATION_INDEX_PPG,
            ),
        ),
    ),
    VesselGroup(
        title = "Blood lab values",
        dials = listOf(
            labDial("ApoB", MetricType.APO_B),
            labDial("Lp(a)", MetricType.LIPOPROTEIN_A),
            labDial("Homocysteine", MetricType.HOMOCYSTEINE),
            labDial("HbA1c", MetricType.HBA1C),
            VesselDial(
                label = "Glucose",
                availability = DialAvailability.NEEDS_ENTRY,
                note = "Glucose sensor, or enter it yourself.",
                primaryKey = MetricType.BLOOD_GLUCOSE,
            ),
        ),
    ),
    VesselGroup(
        title = "Body composition",
        dials = listOf(
            VesselDial(
                label = "Body mass",
                availability = DialAvailability.INSTRUMENTED,
                note = "Smart scale → Health Connect, or manual.",
                primaryKey = MetricType.BODY_MASS,
            ),
            VesselDial(
                label = "Body fat %",
                availability = DialAvailability.INSTRUMENTED,
                note = "Smart scale → Health Connect, or manual.",
                primaryKey = MetricType.BODY_FAT_PCT,
            ),
        ),
    ),
    VesselGroup(
        title = "Sleep and breathing",
        dials = listOf(
            VesselDial(
                label = "Sleep duration",
                availability = DialAvailability.INSTRUMENTED,
                note = "Wearable or manual.",
                primaryKey = MetricType.SLEEP_DURATION,
            ),
            VesselDial(
                label = "Recovery score (reported by your wearable)",
                availability = DialAvailability.INSTRUMENTED,
                note = "The number your wearable's maker computes; Bios shows it as received.",
                primaryKey = MetricType.RECOVERY_SCORE,
            ),
            VesselDial(
                label = "Overnight respiratory rate",
                availability = DialAvailability.INSTRUMENTED,
                note = "Captured nightly.",
                primaryKey = MetricType.RESPIRATORY_RATE,
            ),
            VesselDial(
                label = "VO₂max",
                availability = DialAvailability.INSTRUMENTED,
                note = "Wearable estimate → Health Connect.",
                primaryKey = MetricType.VO2_MAX,
            ),
        ),
    ),
    VesselGroup(
        title = "Epigenetic test results",
        dials = listOf(
            clockDial("DunedinPACE", MetricType.EPIGENETIC_AGE_DUNEDIN_PACE),
            clockDial("GrimAge", MetricType.EPIGENETIC_AGE_GRIM),
            clockDial("PhenoAge", MetricType.EPIGENETIC_AGE_PHENO),
            clockDial("Horvath clock", MetricType.EPIGENETIC_AGE_HORVATH),
        ),
    ),
)

private fun labDial(label: String, key: MetricType) = VesselDial(
    label = label,
    availability = DialAvailability.NEEDS_ENTRY,
    note = "From a lab report — enter it under Lab values.",
    primaryKey = key,
)

private fun clockDial(label: String, key: MetricType) = VesselDial(
    label = label,
    availability = DialAvailability.NEEDS_ENTRY,
    note = "The number on your test report, as the laboratory gave it.",
    primaryKey = key,
)
