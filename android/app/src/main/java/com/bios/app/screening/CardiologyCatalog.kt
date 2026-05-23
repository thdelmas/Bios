package com.bios.app.screening

/**
 * Cardiology one-time / society-recommended screens (#191). Closes the
 * Cardiology audit gap flagged in the issue brief.
 *
 *  - **Lp(a) once-in-lifetime** — AHA / EAS 2022 consensus statements
 *    recommend a single lifetime Lp(a) measurement in every adult, with
 *    no follow-up testing required if the value is normal (Lp(a) is
 *    >90% genetically determined and stable across adulthood). The
 *    cadence engine models this as `cadenceMonths = Int.MAX_VALUE`,
 *    matching the existing AAA one-time pattern.
 *
 * The USPSTF AAA one-time entry already lives in [ScreeningCatalog.uspstf]
 * (now gated on the ever-smoker flag via [RiskGate.EVER_SMOKER]); we
 * don't duplicate it here.
 *
 * **Source.** AHA Scientific Statement on Lipoprotein(a) (2022); EAS
 * Consensus Statement on Lipoprotein(a) in Atherosclerotic CVD Risk
 * (2022).
 */
internal object CardiologyCatalog {

    val entries: List<ScreeningCatalogEntry> = listOf(
        ScreeningCatalogEntry(
            key = "lpa_one_time",
            displayName = "Lipoprotein(a) — one-time lifetime measurement",
            minAge = 18, maxAge = null, cadenceMonths = Int.MAX_VALUE,
            applicability = Applicability.UNIVERSAL,
            citation = "AHA Scientific Statement on Lipoprotein(a) (2022) + EAS Consensus Statement (2022) — once-in-lifetime Lp(a) measurement in every adult; >90% genetic, stable across adulthood, no follow-up needed if normal.",
        ),
    )
}
