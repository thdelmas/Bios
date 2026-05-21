package com.bios.app.screening

/**
 * Which biological sex(es) a screening recommendation applies to. Pure
 * domain enum — not a sex/gender model for the owner. Trans / non-binary
 * owners pick whichever recommendation set matches their organ-system
 * presentation; the catalog doesn't impose identity politics on cadence
 * math.
 */
enum class Applicability {
    /** Applies regardless of sex (lipid panel, HbA1c, colorectal, eye, dental). */
    UNIVERSAL,
    /** Applies to owners with the reproductive anatomy in question (mammogram, cervical). */
    PRESENTS_AS_FEMALE,
    /** Applies primarily to male-bodied owners (one-time AAA ever-smoker). */
    PRESENTS_AS_MALE,
}

/**
 * One screening recommendation. Pinned to the USPSTF adult schedule v1
 * (#155, audit gap §2.2). Region-specific overrides (NHS Health Check,
 * Tokutei Kenshin, KCDC, BowelScreen AU) are tracked as a follow-up —
 * the data shape here is region-agnostic so a future RegionConfig hook
 * can substitute alternate catalogs without touching the engine.
 *
 * **Threshold sourcing.** USPSTF A/B recommendations cited per-entry.
 * Risk-adjusted cadences (e.g. colorectal at age 40 with first-degree
 * family history) are explicitly out of scope until #160 (family-history
 * surface) ships.
 */
data class ScreeningCatalogEntry(
    /** Stable key matching `ScreeningEntry.screeningKey`. Renames are breaking. */
    val key: String,
    /** Short display title for the cadence screen. */
    val displayName: String,
    /** Inclusive lower bound of the screening-age range. */
    val minAge: Int,
    /** Inclusive upper bound, or null for "no upper limit". */
    val maxAge: Int?,
    /** Recommended cadence between screenings, in months. */
    val cadenceMonths: Int,
    /** Sex / anatomy applicability. */
    val applicability: Applicability,
    /** Source guideline citation. */
    val citation: String,
)

/**
 * Static catalog of adult screening recommendations the cadence engine
 * iterates over. Currently USPSTF-only; region-localised catalogs land
 * via a future RegionConfig hook (see audit issue body for the
 * NHS / ESC / JSH / KCDC equivalents).
 */
object ScreeningCatalog {

    /** USPSTF adult schedule. */
    val uspstf: List<ScreeningCatalogEntry> = listOf(
        ScreeningCatalogEntry(
            key = "colorectal_cancer",
            displayName = "Colorectal cancer (colonoscopy or FIT)",
            minAge = 45, maxAge = 75, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "USPSTF 2021 (A recommendation) — colorectal cancer screening 45–75; annual FIT or 10-yr colonoscopy. Cadence here is the annual-FIT pace; the engine treats long-interval modalities (colonoscopy) as still-current when within 10 years.",
        ),
        ScreeningCatalogEntry(
            key = "mammogram",
            displayName = "Breast cancer (mammogram)",
            minAge = 40, maxAge = 74, cadenceMonths = 24,
            applicability = Applicability.PRESENTS_AS_FEMALE,
            citation = "USPSTF 2024 (B recommendation) — biennial mammography 40–74",
        ),
        ScreeningCatalogEntry(
            key = "cervical_cancer",
            displayName = "Cervical cancer (HPV / Pap co-test)",
            minAge = 21, maxAge = 65, cadenceMonths = 60,
            applicability = Applicability.PRESENTS_AS_FEMALE,
            citation = "USPSTF 2018 (A recommendation) — 5-yr HPV co-testing 30–65; 3-yr cytology 21–29 (cadence pinned to the lighter-burden HPV-era schedule)",
        ),
        ScreeningCatalogEntry(
            key = "aaa_one_time",
            displayName = "Abdominal aortic aneurysm (one-time ultrasound)",
            minAge = 65, maxAge = 75, cadenceMonths = Int.MAX_VALUE,
            applicability = Applicability.PRESENTS_AS_MALE,
            citation = "USPSTF 2019 (B recommendation) — one-time AAA ultrasound, men 65–75 who have ever smoked",
        ),
        ScreeningCatalogEntry(
            key = "lipid_panel",
            displayName = "Lipid panel (cholesterol)",
            minAge = 40, maxAge = null, cadenceMonths = 60,
            applicability = Applicability.UNIVERSAL,
            citation = "USPSTF 2016 (B recommendation) — statin primary prevention assessment with periodic lipid measurement; 5-yr cadence reflects the standard primary-care interval for stable values",
        ),
        ScreeningCatalogEntry(
            key = "hba1c",
            displayName = "Diabetes screening (HbA1c)",
            minAge = 35, maxAge = 70, cadenceMonths = 36,
            applicability = Applicability.UNIVERSAL,
            citation = "USPSTF 2021 (B recommendation) — prediabetes/T2DM screening 35–70 in overweight/obese adults, 3-yr cadence in normoglycemic owners",
        ),
        ScreeningCatalogEntry(
            key = "dexa_bone_density",
            displayName = "Osteoporosis (DEXA)",
            minAge = 65, maxAge = null, cadenceMonths = 24,
            applicability = Applicability.PRESENTS_AS_FEMALE,
            citation = "USPSTF 2018 (B recommendation) — postmenopausal women ≥65; biennial cadence reflects clinical convention for stable T-scores",
        ),
        ScreeningCatalogEntry(
            key = "depression_screen",
            displayName = "Depression screen (PHQ-2 / PHQ-9)",
            minAge = 18, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "USPSTF 2023 (B recommendation) — annual depression screening in adults",
        ),
    )
}
