package com.bios.app.ui.vessel

import com.bios.contracts.MetricType

/**
 * Read-only descriptor for one Vessel Watch dial.
 *
 * A dial reads existing [MetricType] keys and stands alone. There is
 * deliberately no numeric field here that could be summed across dials into a
 * composite "vessel score" — that is forbidden by MANIFESTO §7 and the
 * DATA_MODEL no-derived-score rule. Each dial carries only what it reads
 * ([primaryKey] / [secondaryKey]), why it may be empty ([availability]), and
 * the honest fill / honest-gap copy ([note]) transcribed from
 * docs/subjects/owner/VESSEL_WATCH.md.
 */
data class VesselDial(
    val label: String,
    val availability: DialAvailability,
    val note: String,
    val primaryKey: MetricType? = null,
    /** Second key shown alongside the first, e.g. diastolic rendered sys/dia. */
    val secondaryKey: MetricType? = null,
)

/**
 * Why a dial may have no value yet — drives the explicit-gap copy. The watch
 * never hides or fakes a missing dial; it names what would fill it. Acute
 * out-of-range signals are not modelled here at all: they live in the
 * Section-0 boundary, which routes to a clinician rather than to a dial.
 */
enum class DialAvailability {
    /** A sensor or sync produces this; empty means waiting for a first reading. */
    INSTRUMENTED,

    /** Derived on demand from a one-off capture (camera PPG). */
    DERIVED_ON_DEMAND,

    /** The owner enters it (cuff reading, lab draw, epigenetic clock). */
    NEEDS_ENTRY,

    /** Not yet instrumented at all — the honest agency-edge gap. */
    PLANNED,
}

/** One labelled group of dials (a section A–F of the spec). */
data class VesselGroup(
    val title: String,
    val blurb: String,
    val dials: List<VesselDial>,
)

/**
 * The curated dial catalog — groups A–F, in the order and grouping of
 * docs/subjects/owner/VESSEL_WATCH.md. Every key referenced here is a real
 * [MetricType] constant, so a renamed/removed key breaks compilation rather
 * than silently dropping a dial.
 *
 * Cluster dials with no single canonical key (lab vascular risk, cognitive
 * throughput) carry a null [primaryKey] and render as an explicit gap that
 * points at where the value would come from — they are named, not hidden.
 */
val vesselWatchGroups: List<VesselGroup> = listOf(
    VesselGroup(
        title = "A · Vascular substrate",
        blurb = "The circuit the acute symptoms sit on, and tobacco's footprint.",
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
                label = "Arterial stiffness",
                availability = DialAvailability.DERIVED_ON_DEMAND,
                note = "Run a camera PPG — vascular-aging readout, tobacco-sensitive.",
                primaryKey = MetricType.AUGMENTATION_INDEX_PPG,
            ),
            VesselDial(
                label = "Lab vascular risk",
                availability = DialAvailability.NEEDS_ENTRY,
                note = "ApoB, Lp(a), homocysteine — annual draw, enter via Lab values.",
            ),
        ),
    ),
    VesselGroup(
        title = "B · Metabolic substrate",
        blurb = "Fuel handling under the vascular circuit.",
        dials = listOf(
            VesselDial(
                label = "Glucose",
                availability = DialAvailability.NEEDS_ENTRY,
                note = "Manual / lab. HbA1c + fasting insulin enter via Lab values.",
                primaryKey = MetricType.BLOOD_GLUCOSE,
            ),
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
        title = "C · Recovery & sleep",
        blurb = "Cognition and repair run on this — the chronic side of harder-to-think.",
        dials = listOf(
            VesselDial(
                label = "Sleep duration",
                availability = DialAvailability.INSTRUMENTED,
                note = "Wearable or manual.",
                primaryKey = MetricType.SLEEP_DURATION,
            ),
            VesselDial(
                label = "Recovery",
                availability = DialAvailability.INSTRUMENTED,
                note = "Wearable recovery readout.",
                primaryKey = MetricType.RECOVERY_SCORE,
            ),
            VesselDial(
                label = "Overnight respiratory rate",
                availability = DialAvailability.INSTRUMENTED,
                note = "Captured nightly.",
                primaryKey = MetricType.RESPIRATORY_RATE,
            ),
        ),
    ),
    VesselGroup(
        title = "D · Aging",
        blurb = "The named dial. Each clock stands alone — never composed into one age.",
        dials = listOf(
            VesselDial(
                label = "Pace of aging (DunedinPACE)",
                availability = DialAvailability.NEEDS_ENTRY,
                note = "Enter at next epigenetic test.",
                primaryKey = MetricType.EPIGENETIC_AGE_DUNEDIN_PACE,
            ),
            VesselDial(
                label = "Biological age (GrimAge)",
                availability = DialAvailability.NEEDS_ENTRY,
                note = "Standalone clock — displayed alone, never averaged.",
                primaryKey = MetricType.EPIGENETIC_AGE_GRIM,
            ),
            VesselDial(
                label = "Biological age (PhenoAge)",
                availability = DialAvailability.NEEDS_ENTRY,
                note = "Standalone clock — displayed alone, never averaged.",
                primaryKey = MetricType.EPIGENETIC_AGE_PHENO,
            ),
            VesselDial(
                label = "Biological age (Horvath)",
                availability = DialAvailability.NEEDS_ENTRY,
                note = "Standalone clock — displayed alone, never averaged.",
                primaryKey = MetricType.EPIGENETIC_AGE_HORVATH,
            ),
            VesselDial(
                label = "VO₂max",
                availability = DialAvailability.INSTRUMENTED,
                note = "Strongest functional-capacity / longevity correlate.",
                primaryKey = MetricType.VO2_MAX,
            ),
        ),
    ),
    VesselGroup(
        title = "E · Neuro-motor reserve",
        blurb = "The agency edge — and, honestly, the least instrumented today. " +
            "The watch names this gap rather than hiding it behind the dials that are live.",
        dials = listOf(
            VesselDial(
                label = "Gait symmetry",
                availability = DialAvailability.PLANNED,
                note = "Needs an accelerometer-posture pipeline — not yet instrumented.",
            ),
            VesselDial(
                label = "Tremor",
                availability = DialAvailability.PLANNED,
                note = "Planned — fine-motor reserve readout.",
            ),
            VesselDial(
                label = "Cognitive throughput",
                availability = DialAvailability.PLANNED,
                note = "SDMT / keystroke dynamics via Fil — planned.",
            ),
        ),
    ),
    VesselGroup(
        title = "F · Substance load",
        blurb = "The degrading input — surfaced to correlate against vascular and " +
            "recovery dials, never to score or shame. Upstream data, not a moral line.",
        dials = listOf(
            VesselDial(
                label = "Tobacco, cannabis, caffeine, alcohol",
                availability = DialAvailability.NEEDS_ENTRY,
                note = "Concentration + event log live under Active Substances.",
            ),
        ),
    ),
)
