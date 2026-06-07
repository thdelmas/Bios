package com.bios.app.screening

/**
 * Routine periodic medical checkups — the recurring clinical *encounters*
 * that aren't disease-specific screenings (those live in
 * [ScreeningCatalog.uspstf]) or vaccines (those live in the immunisations
 * screen). The cadence screen's own doc comment named "eye, dental" as
 * universal examples long before any entry existed; this catalog closes
 * that gap.
 *
 * Two timing semantics, both surfaced by [ScreeningCadenceEngine]:
 *
 *  - **[CadenceKind.RECURRING]** — a target interval that reads "due" /
 *    "overdue" once passed (e.g. the periodic wellness visit).
 *  - **[CadenceKind.MIN_INTERVAL_SINCE_LAST]** — a *recommended delay
 *    since the last occurrence*; doing it sooner adds little and being
 *    "late" is not a failure, so it never renders as overdue (dental
 *    recall, eye exam, skin / hearing checks). This is the manifesto's
 *    no-shame rule expressed in the data model.
 *
 * **Honest sourcing.** Unlike the USPSTF screening set, several routine
 * checkups have no A/B grade — the periodic physical and average-risk
 * skin exam are explicitly *not* USPSTF-recommended. Each entry's
 * citation states the actual evidence posture so the owner reads cadence
 * as professional-society convention, not a Bios diagnosis. Bios points;
 * the clinic reads.
 *
 * Region-agnostic by design, like the rest of the catalog — a future
 * RegionConfig hook can substitute local recall intervals (NHS dental
 * NICE recall, etc.) without touching the engine.
 */
internal object CheckupCatalog {

    val entries: List<ScreeningCatalogEntry> = listOf(
        ScreeningCatalogEntry(
            key = "periodic_health_exam",
            displayName = "Periodic health exam (wellness visit)",
            minAge = 18, maxAge = null, cadenceMonths = 36,
            applicability = Applicability.UNIVERSAL,
            citation = "No USPSTF A/B grade for the annual physical (the routine yearly exam in " +
                "asymptomatic adults shows no mortality benefit). Framed here as a periodic check-in to " +
                "keep a clinician relationship live; a 3-yr default for younger adults is a common " +
                "primary-care convention, not a mandate. Your provider sets the real interval.",
            cadenceKind = CadenceKind.RECURRING,
        ),
        ScreeningCatalogEntry(
            key = "blood_pressure_check",
            displayName = "Blood pressure check",
            minAge = 18, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "USPSTF 2021 (A recommendation) — BP screening in adults ≥18. Annual for adults " +
                "≥40 or at higher risk; every 3–5 yr for 18–39 with normal BP. The 12-mo cadence here is " +
                "the higher-risk pace; an in-range reading just means the recommended re-check is further out.",
            cadenceKind = CadenceKind.RECURRING,
        ),
        ScreeningCatalogEntry(
            key = "dental_checkup",
            displayName = "Dental exam + cleaning",
            minAge = 18, maxAge = null, cadenceMonths = 6,
            applicability = Applicability.UNIVERSAL,
            citation = "ADA: recall interval set individually by risk; ~6 mo is the common default for " +
                "average-risk adults (NICE recall can extend to 24 mo for low-risk). A recommended delay " +
                "since your last visit, not a deadline.",
            cadenceKind = CadenceKind.MIN_INTERVAL_SINCE_LAST,
        ),
        ScreeningCatalogEntry(
            key = "eye_exam",
            displayName = "Comprehensive eye exam",
            minAge = 18, maxAge = null, cadenceMonths = 24,
            applicability = Applicability.UNIVERSAL,
            citation = "AAO / AOA: comprehensive eye exam every 1–2 yr for low-risk adults (annual from " +
                "~65). New or changing vision symptoms route to an eye-care professional regardless of " +
                "where this interval sits — Bios has no vision-acuity metric.",
            cadenceKind = CadenceKind.MIN_INTERVAL_SINCE_LAST,
        ),
        ScreeningCatalogEntry(
            key = "skin_exam",
            displayName = "Full-body skin check",
            minAge = 18, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "USPSTF 2023: I-statement — insufficient evidence to recommend routine whole-body " +
                "skin exams in average-risk asymptomatic adults. Surfaced as an elective annual recall; " +
                "higher-risk owners (personal/family melanoma history) follow a dermatologist's shorter " +
                "interval. A new or changing lesion routes to a clinician now, not at the next recall.",
            cadenceKind = CadenceKind.MIN_INTERVAL_SINCE_LAST,
        ),
        ScreeningCatalogEntry(
            key = "hearing_test",
            displayName = "Hearing check (audiometry)",
            minAge = 50, maxAge = null, cadenceMonths = 36,
            applicability = Applicability.UNIVERSAL,
            citation = "USPSTF 2021: I-statement — insufficient evidence to screen for hearing loss in " +
                "asymptomatic older adults; ASHA suggests periodic audiometry from ~50. Surfaced as a " +
                "recommended delay since your last test, never overdue. Noticeable hearing change routes " +
                "to a clinician regardless.",
            cadenceKind = CadenceKind.MIN_INTERVAL_SINCE_LAST,
        ),
    )
}
