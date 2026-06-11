package com.bios.app.screening

/**
 * World Health Organization screening / periodic-check recommendations
 * (maximum-coverage follow-up). Where the rest of the catalog anchors on
 * the USPSTF (a US body), this set carries WHO's globally-scoped guidance
 * so the cadence screen isn't silently US-centric — fitting the catalog's
 * region-agnostic design.
 *
 * Only entries that add *new* coverage live here. WHO recommendations that
 * merely restate something already in the catalog with a different
 * threshold are deliberately omitted to avoid duplicate rows:
 *
 *  - Cervical (WHO: HPV-primary from 30, q5–10y; q3–5y from 25 for women
 *    living with HIV), breast (WHO: 50–69 biennial), colorectal (WHO/IARC:
 *    FIT biennial 50–74), diabetes/HbA1c, lipids, blood-pressure, and
 *    depression are all already represented by their USPSTF/society entries.
 *
 * **Honest sourcing.** WHO frames most of these as setting-dependent (the
 * infectious-disease tests gate on local prevalence) or as a *composite*
 * assessment rather than a single lab. Each citation states that posture so
 * the owner reads cadence as WHO public-health guidance, not a Bios verdict.
 * Bios points; the clinic reads.
 *
 * **Deferred (need infrastructure this PR doesn't add):**
 *  - WHO systematic TB screening — risk-gated to PLHIV, contacts, prisons,
 *    high-burden settings; needs new [RiskProfile] flags + a RegionConfig
 *    prevalence hook before it can surface without false universality.
 *  - Antenatal syphilis / HBsAg / HIV ("at least once per pregnancy") —
 *    belong to the reproductive module's pregnancy timeline, not the adult
 *    cadence screen.
 *  - Tobacco / harmful-alcohol screening — WHO frames these as opportunistic
 *    at every visit with no fixed interval; tobacco load is already the
 *    Smokeless companion's domain (see ECOSYSTEM_BOUNDARIES).
 */
internal object WhoScreeningCatalog {

    val entries: List<ScreeningCatalogEntry> = listOf(
        ScreeningCatalogEntry(
            key = "who_cvd_risk_assessment",
            displayName = "Cardiovascular risk assessment (WHO total-risk)",
            minAge = 40, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "WHO PEN 2020 / HEARTS 2020 — total cardiovascular risk assessment using the " +
                "WHO/ISH risk charts for adults >40 (or at any age with current smoking, raised waist, " +
                "known hypertension or diabetes, or premature-CVD family history). Low-risk reassessment " +
                "~yearly; higher-risk bands sooner, set by your clinician. A composite risk check, not a " +
                "single test.",
            cadenceKind = CadenceKind.RECURRING,
        ),
        ScreeningCatalogEntry(
            key = "hiv_test",
            displayName = "HIV test",
            minAge = 18, maxAge = null, cadenceMonths = Int.MAX_VALUE,
            applicability = Applicability.UNIVERSAL,
            citation = "WHO Consolidated Guidelines on HIV Testing Services (2019; updated 2024) — at " +
                "least one HIV test for adults, with broad general-population testing offered in " +
                "higher-prevalence settings. Higher-risk / key populations retest at least yearly (every " +
                "3–6 mo with ongoing exposure) — record again if so. Self-testing is an accepted option.",
            cadenceKind = CadenceKind.RECURRING,
        ),
        ScreeningCatalogEntry(
            key = "hepatitis_b_test",
            displayName = "Hepatitis B test (HBsAg)",
            minAge = 18, maxAge = null, cadenceMonths = Int.MAX_VALUE,
            applicability = Applicability.UNIVERSAL,
            citation = "WHO Guidelines on Hepatitis B and C Testing (2017) — at least one HBsAg test; " +
                "general-population testing where HBsAg seroprevalence is ≥2%, otherwise focused on " +
                "higher-risk groups. Often offered opportunistically through antenatal, HIV or TB " +
                "services. No repeat needed once negative and risk is low.",
            cadenceKind = CadenceKind.RECURRING,
        ),
        ScreeningCatalogEntry(
            key = "hepatitis_c_test",
            displayName = "Hepatitis C test (anti-HCV)",
            minAge = 18, maxAge = null, cadenceMonths = Int.MAX_VALUE,
            applicability = Applicability.UNIVERSAL,
            citation = "WHO Guidelines on Hepatitis B and C Testing (2017) — a one-time anti-HCV test for " +
                "the general adult population in settings with ≥2% seroprevalence (or by birth cohort), " +
                "and for higher-risk groups regardless of setting. Repeat only with ongoing exposure.",
            cadenceKind = CadenceKind.RECURRING,
        ),
    )
}
