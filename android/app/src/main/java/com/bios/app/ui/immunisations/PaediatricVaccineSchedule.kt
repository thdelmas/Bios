package com.bios.app.ui.immunisations

/**
 * Age-banded paediatric vaccine schedule (#191). Closes the paediatric
 * audit gap (§2.6) — the existing [VaccineCatalog] honestly deferred
 * paediatric vaccines, so the cadence engine could only answer "is this
 * adult vaccine current?" and never "is this 6-month-old due for their
 * second DTaP dose?".
 *
 * **Anchor.** CDC ACIP Child & Adolescent Immunization Schedule, 2024
 * release (the WHO EPI schedule overlaps substantially but pins
 * different intervals for HepB birth + measles; the schedule shape here
 * is the US ACIP path because that's the one the existing VaccineCatalog
 * CVX codes line up against). Owners outside the US can manually shift
 * dose-due dates via the entry screen.
 *
 * **What this is not.** This is a pull-side cadence reference, not a
 * vaccination reminder system. The owner records their child's doses;
 * Bios computes which doses remain and renders the due band. No push,
 * no notifications — same manifesto guard as every other cadence
 * surface.
 *
 * **Why a separate file.** `VaccineCatalog.kt` is owner-facing adult
 * picker data (20 rows). Pediatric schedules use a different shape
 * (age-band + dose number + interval) and would push the catalog file
 * past the 500-line limit if combined.
 */
data class VaccineSchedule(
    /** Display name (matches a [VaccineCatalogEntry.displayName] where possible). */
    val displayName: String,
    /** Optional CDC CVX code (matches an existing entry when one exists). */
    val cvxCode: String?,
    /** Series-of-doses entries, in dose order. */
    val doses: List<DoseRecommendation>,
    /** Source / guideline citation. */
    val citation: String,
)

/**
 * One dose within a multi-dose paediatric series. The cadence engine
 * computes "due now" by comparing the child's age (in months) to
 * [minAgeMonths] / [maxAgeMonths] and checking whether the matching
 * [doseNumber] has been recorded.
 */
data class DoseRecommendation(
    /** Dose number within the series, starting at 1. */
    val doseNumber: Int,
    /** Earliest age (months) the dose can be given. */
    val minAgeMonths: Int,
    /**
     * Recommended upper age (months) for the dose — beyond this the
     * dose is "overdue" rather than "due now." Null means "no upper
     * bound" (lifetime catch-up).
     */
    val maxAgeMonths: Int?,
    /**
     * Minimum interval (months) from the previous dose. Used when the
     * child started the series late and the previous dose anchors when
     * the next dose can be given.
     */
    val minIntervalFromPriorMonths: Int? = null,
)

/**
 * Static ACIP / WHO EPI paediatric schedule the cadence engine reads.
 * Each entry pins the canonical US ACIP path for the named vaccine.
 *
 * The selection covers the routine birth-through-age-12 schedule the
 * audit named (birth HepB; 2/4/6mo DTaP/IPV/PCV/RV/Hib; 12mo MMR/VAR/
 * HepA; 4–6y boosters; 11–12y HPV/Tdap/MenACWY). Adolescent boosters
 * after age 12 (MenACWY-2, MenB shared-decision) are out of scope for
 * this PR per the issue's "6–10 highest-impact" framing.
 */
object PaediatricVaccineSchedule {

    /** Months in a year — used by helper math, exposed for tests. */
    const val MONTHS_PER_YEAR = 12

    val entries: List<VaccineSchedule> = listOf(
        VaccineSchedule(
            displayName = "Hepatitis B (paediatric)",
            cvxCode = "08",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 0, maxAgeMonths = 1),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 1, maxAgeMonths = 4, minIntervalFromPriorMonths = 1),
                DoseRecommendation(doseNumber = 3, minAgeMonths = 6, maxAgeMonths = 18, minIntervalFromPriorMonths = 2),
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — HepB birth dose, 1–2mo, 6–18mo",
        ),
        VaccineSchedule(
            displayName = "DTaP (paediatric series)",
            cvxCode = "20",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 2, maxAgeMonths = 3),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 4, maxAgeMonths = 5, minIntervalFromPriorMonths = 1),
                DoseRecommendation(doseNumber = 3, minAgeMonths = 6, maxAgeMonths = 7, minIntervalFromPriorMonths = 1),
                DoseRecommendation(doseNumber = 4, minAgeMonths = 15, maxAgeMonths = 18, minIntervalFromPriorMonths = 6),
                DoseRecommendation(doseNumber = 5, minAgeMonths = 4 * MONTHS_PER_YEAR, maxAgeMonths = 6 * MONTHS_PER_YEAR, minIntervalFromPriorMonths = 6),
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — DTaP 2/4/6mo + 15–18mo + 4–6y",
        ),
        VaccineSchedule(
            displayName = "IPV (inactivated polio, paediatric)",
            cvxCode = "10",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 2, maxAgeMonths = 3),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 4, maxAgeMonths = 5, minIntervalFromPriorMonths = 1),
                DoseRecommendation(doseNumber = 3, minAgeMonths = 6, maxAgeMonths = 18, minIntervalFromPriorMonths = 1),
                DoseRecommendation(doseNumber = 4, minAgeMonths = 4 * MONTHS_PER_YEAR, maxAgeMonths = 6 * MONTHS_PER_YEAR, minIntervalFromPriorMonths = 6),
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — IPV 2/4/6–18mo + 4–6y",
        ),
        VaccineSchedule(
            displayName = "PCV13 / PCV15 (paediatric pneumococcal)",
            cvxCode = "133",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 2, maxAgeMonths = 3),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 4, maxAgeMonths = 5, minIntervalFromPriorMonths = 1),
                DoseRecommendation(doseNumber = 3, minAgeMonths = 6, maxAgeMonths = 7, minIntervalFromPriorMonths = 1),
                DoseRecommendation(doseNumber = 4, minAgeMonths = 12, maxAgeMonths = 15, minIntervalFromPriorMonths = 2),
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — PCV 2/4/6/12–15mo",
        ),
        VaccineSchedule(
            displayName = "Rotavirus (RV, paediatric)",
            cvxCode = "116",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 2, maxAgeMonths = 3),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 4, maxAgeMonths = 5, minIntervalFromPriorMonths = 1),
                // RV third dose (RotaTeq only) is optional; we model the
                // two-dose Rotarix path here and let the owner record a
                // third entry manually if their child got RotaTeq.
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — Rotavirus 2/4mo (Rotarix 2-dose path)",
        ),
        VaccineSchedule(
            displayName = "Hib (Haemophilus influenzae type b)",
            cvxCode = "48",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 2, maxAgeMonths = 3),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 4, maxAgeMonths = 5, minIntervalFromPriorMonths = 1),
                DoseRecommendation(doseNumber = 3, minAgeMonths = 6, maxAgeMonths = 7, minIntervalFromPriorMonths = 1),
                DoseRecommendation(doseNumber = 4, minAgeMonths = 12, maxAgeMonths = 15, minIntervalFromPriorMonths = 2),
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — Hib 2/4/6/12–15mo (PRP-T schedule)",
        ),
        VaccineSchedule(
            displayName = "MMR (measles, mumps, rubella)",
            cvxCode = "03",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 12, maxAgeMonths = 15),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 4 * MONTHS_PER_YEAR, maxAgeMonths = 6 * MONTHS_PER_YEAR, minIntervalFromPriorMonths = 1),
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — MMR 12–15mo + 4–6y",
        ),
        VaccineSchedule(
            displayName = "Varicella (chickenpox, paediatric)",
            cvxCode = "21",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 12, maxAgeMonths = 15),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 4 * MONTHS_PER_YEAR, maxAgeMonths = 6 * MONTHS_PER_YEAR, minIntervalFromPriorMonths = 3),
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — Varicella 12–15mo + 4–6y",
        ),
        VaccineSchedule(
            displayName = "Hepatitis A (paediatric)",
            cvxCode = "85",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 12, maxAgeMonths = 23),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 18, maxAgeMonths = 35, minIntervalFromPriorMonths = 6),
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — HepA 12–23mo + ≥6mo later",
        ),
        VaccineSchedule(
            displayName = "HPV (paediatric / adolescent 9-valent)",
            cvxCode = "165",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 11 * MONTHS_PER_YEAR, maxAgeMonths = 13 * MONTHS_PER_YEAR),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 11 * MONTHS_PER_YEAR + 5, maxAgeMonths = 13 * MONTHS_PER_YEAR + 12, minIntervalFromPriorMonths = 5),
                // ACIP: 2-dose series when initiated <15y; 3-dose
                // series when initiated ≥15y. Model the 2-dose path
                // because the entry's age band starts at 11.
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — HPV 11–12y (2-dose series, dose 2 at 6–12mo)",
        ),
        VaccineSchedule(
            displayName = "Tdap (adolescent booster)",
            cvxCode = "115",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 11 * MONTHS_PER_YEAR, maxAgeMonths = 12 * MONTHS_PER_YEAR),
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — Tdap booster 11–12y",
        ),
        VaccineSchedule(
            displayName = "MenACWY (meningococcal, adolescent)",
            cvxCode = "147",
            doses = listOf(
                DoseRecommendation(doseNumber = 1, minAgeMonths = 11 * MONTHS_PER_YEAR, maxAgeMonths = 12 * MONTHS_PER_YEAR),
                DoseRecommendation(doseNumber = 2, minAgeMonths = 16 * MONTHS_PER_YEAR, maxAgeMonths = 18 * MONTHS_PER_YEAR, minIntervalFromPriorMonths = 36),
            ),
            citation = "CDC ACIP Child & Adolescent Immunization Schedule, 2024 — MenACWY 11–12y + booster 16y",
        ),
    )

    /**
     * Indexed-by-CVX accessor. Returns null when the CVX isn't in the
     * paediatric schedule (e.g. adult-only vaccines).
     */
    fun byCvx(cvxCode: String): VaccineSchedule? =
        entries.firstOrNull { it.cvxCode == cvxCode }
}
