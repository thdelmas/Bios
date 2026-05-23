package com.bios.app.screening

/**
 * Ob/Gyn society cadences beyond the USPSTF baseline (#191). Closes the
 * gap flagged in [docs/audits/OBGYN_POV.md §2.12–2.14]. Two entries:
 *
 *  - **ACS mammography** — the American Cancer Society publishes an
 *    earlier start age (45) than USPSTF (40 since 2024) and an earlier
 *    annual cadence (45–54 annual, ≥55 biennial). The USPSTF catalog
 *    already lists the 40-start / biennial entry; we surface this as an
 *    *additional* society-perspective row so the owner can see both
 *    perspectives explicitly. The cadence engine doesn't reconcile
 *    society disagreement — the owner reads both and applies the one
 *    their provider recommends.
 *
 *  - **ACOG postmenopausal DEXA** — biennial DEXA in postmenopausal
 *    women with osteoporosis risk factors, distinct from the USPSTF
 *    age-65 floor.
 *
 * **Source.** ACS Breast Cancer Screening Guideline (2015, reaffirmed
 * 2023); ACOG Practice Bulletin No. 222 (Osteoporosis Prevention,
 * Screening, and Diagnosis).
 *
 * **Manifesto guard.** No society-disagreement nudge from Bios. We list
 * both and let the owner choose.
 */
internal object ObGynSocietyCatalog {

    val entries: List<ScreeningCatalogEntry> = listOf(
        // ACS — annual mammography 45–54, biennial 55+. The catalog
        // models the annual-window start age (45) so the entry surfaces
        // for owners who follow the ACS schedule. Entry coexists with
        // the USPSTF 40-start / biennial row.
        ScreeningCatalogEntry(
            key = "acs_mammogram",
            displayName = "Breast cancer — ACS mammogram (alternate start)",
            minAge = 45, maxAge = 54, cadenceMonths = 12,
            applicability = Applicability.PRESENTS_AS_FEMALE,
            citation = "ACS Breast Cancer Screening Guideline (2015, reaffirmed 2023) — annual mammography 45–54. USPSTF 2024 recommends biennial from 40; both are reasonable evidence-based schedules and the owner picks with their provider.",
        ),
        // ACOG — postmenopausal DEXA. Practice Bulletin 222 — DEXA at
        // menopause when risk factors are present (low BMI, prior
        // fracture, glucocorticoid use, etc.), biennial cadence
        // thereafter when stable. The age floor is approximated to 50
        // (typical menopause onset); the engine's risk gate doesn't
        // attempt to detect menopause directly — pull-side surface only.
        ScreeningCatalogEntry(
            key = "acog_postmenopausal_dexa",
            displayName = "Osteoporosis — ACOG postmenopausal DEXA",
            minAge = 50, maxAge = 64, cadenceMonths = 24,
            applicability = Applicability.PRESENTS_AS_FEMALE,
            citation = "ACOG Practice Bulletin No. 222 — Osteoporosis Prevention, Screening, and Diagnosis. Biennial DEXA in postmenopausal women with risk factors before the USPSTF age-65 floor.",
        ),
    )
}
