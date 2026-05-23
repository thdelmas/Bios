package com.bios.app.screening

/**
 * IHS / PAHO regional cadences (#191). Closes the Indigenous Americas
 * audit gap (§2.4) — the only entry the audit specifically named is the
 * IHS Diabetes Standards of Care HbA1c cadence for diabetic American
 * Indian / Alaska Native owners.
 *
 *  - **IHS HbA1c twice-yearly** — Indian Health Service Division of
 *    Diabetes Treatment & Prevention Standards of Care call for HbA1c
 *    every 6 months in diabetic AI/AN patients (vs. annual or 3-yearly
 *    in non-diabetic primary-care populations), reflecting AI/AN T2DM
 *    prevalence ~2–3× the US baseline. Gated on
 *    [RiskGate.IHS_DIABETIC_AIAN] — the owner self-declares their AI/AN
 *    status + diabetic status via the risk-profile screen; Bios never
 *    infers ethnicity.
 *
 * Broader PAHO regional cadences (Chagas serology in endemic-region
 * residents, etc.) are intentionally out of scope for this PR — they
 * need a region-residence field on the demographics surface that
 * doesn't exist yet. Tracked as a follow-up.
 *
 * **Source.** IHS Division of Diabetes Treatment and Prevention,
 * Diabetes Standards of Care (most recent: 2024 release).
 */
internal object IhsPahoCatalog {

    val entries: List<ScreeningCatalogEntry> = listOf(
        ScreeningCatalogEntry(
            key = "ihs_hba1c_diabetic_aian",
            displayName = "Diabetes monitoring — IHS twice-yearly HbA1c",
            minAge = 18, maxAge = null, cadenceMonths = 6,
            applicability = Applicability.UNIVERSAL,
            citation = "IHS Division of Diabetes Treatment & Prevention — Standards of Care: HbA1c every 6 months in diabetic American Indian / Alaska Native patients (T2DM prevalence ~2–3× baseline).",
            riskGate = RiskGate.IHS_DIABETIC_AIAN,
        ),
    )
}
