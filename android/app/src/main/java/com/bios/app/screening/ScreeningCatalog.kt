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
 * Risk-profile gate that decides whether a catalog entry is surfaced to
 * the owner at all. The cadence engine evaluates this against the
 * owner's [com.bios.app.model.RiskProfile] — entries gated on a
 * hereditary syndrome only render when the owner has self-declared the
 * matching flag. Pure data-driven enum so the catalog stays serializable
 * and the engine doesn't need to capture closures.
 *
 * Manifesto guard: the owner is the one who flips these flags from the
 * risk-profile screen. Bios never infers a syndrome — the owner enters
 * what they know, the engine honours it.
 */
enum class RiskGate {
    /** No risk gate — surface to everyone in the age / applicability band. */
    NONE,
    /** Only surface when [com.bios.app.model.RiskProfile.lynchSyndrome] is set. */
    LYNCH_SYNDROME,
    /** Only surface when [com.bios.app.model.RiskProfile.liFraumeni] is set. */
    LI_FRAUMENI,
    /** Only surface when [com.bios.app.model.RiskProfile.fapFamilialAdenomatousPolyposis] is set. */
    FAP,
    /** Only surface when [com.bios.app.model.RiskProfile.cowdenSyndrome] is set. */
    COWDEN,
    /** Only surface when [com.bios.app.model.RiskProfile.peutzJeghersSyndrome] is set. */
    PEUTZ_JEGHERS,
    /** Only surface when [com.bios.app.model.RiskProfile.vhlVonHippelLindau] is set. */
    VHL,
    /** Only surface when [com.bios.app.model.RiskProfile.men1] is set. */
    MEN1,
    /** Only surface when [com.bios.app.model.RiskProfile.men2A] is set. */
    MEN2A,
    /** Only surface when [com.bios.app.model.RiskProfile.men2B] is set. */
    MEN2B,
    /** Only surface when [com.bios.app.model.RiskProfile.hdgcHereditaryDiffuseGastric] is set. */
    HDGC,
    /**
     * Only surface when the owner is an IHS-served diabetic American
     * Indian / Alaska Native (per IHS Diabetes Standards of Care: HbA1c
     * twice yearly given T2DM prevalence ~2–3× baseline). Requires
     * [com.bios.app.model.RiskProfile.ihsDiabeticAianStatus].
     */
    IHS_DIABETIC_AIAN,
    /**
     * Only surface when the owner has any first-degree-relative pack-year
     * smoking history recorded — the USPSTF AAA one-time ultrasound is
     * scoped to men 65–75 who have ever smoked. The engine treats
     * [com.bios.app.model.RiskProfile.personalTobaccoYears] != null or
     * `personalTobaccoPackYears` != null as "ever smoked".
     */
    EVER_SMOKER,
}

/**
 * How a catalog entry's recommended interval should be read.
 *
 * The distinction matters for owner-facing framing, not just arithmetic
 * (audit follow-up: periodic checkups). Disease screenings recur on a
 * target cadence and read "overdue" once you pass it; routine checkups
 * are often phrased as a *minimum recommended delay since the last one*,
 * where doing it sooner adds little and being "late" is not a failure.
 *
 * Manifesto guard: the no-shame rule means a delay you can't fail must
 * never render in the error / overdue treatment. The engine honours that
 * by returning distinct statuses per kind.
 */
enum class CadenceKind {
    /**
     * Recurring periodic screening. `cadenceMonths` is a *target* — once
     * the owner passes it (plus the grace window) the entry reads as due
     * / overdue. The default so every pre-existing entry is unchanged.
     */
    RECURRING,

    /**
     * Recommended *delay since the last occurrence*. `cadenceMonths` is a
     * minimum recommended interval, not a target to chase. Before it
     * elapses the entry reads "recommended again after …"; once it does,
     * "eligible again" — neutral either way, never overdue. Used for
     * routine checkups (dental recall, eye exam, periodic wellness visit)
     * whose interval is advice, not a clock you fail.
     */
    MIN_INTERVAL_SINCE_LAST,
}

/**
 * One screening recommendation. Pinned to the USPSTF adult schedule v1
 * (#155, audit gap §2.2), with hereditary-syndrome / cardiology
 * one-time / Ob/Gyn-society / IHS extensions added in #191.
 *
 * Region-specific overrides (NHS Health Check, Tokutei Kenshin, KCDC,
 * BowelScreen AU) are tracked as a follow-up — the data shape here is
 * region-agnostic so a future RegionConfig hook can substitute alternate
 * catalogs without touching the engine.
 *
 * **Threshold sourcing.** USPSTF A/B recommendations cited per-entry;
 * hereditary cadences cite NCCN; vaccine-overlap and one-time entries
 * cite the matching society guideline (AHA/EAS Lp(a), ACOG DEXA, ACS
 * mammogram, IHS Diabetes Standards).
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
    /**
     * Owner-asserted risk gate. `RiskGate.NONE` means "everyone in the
     * age / applicability band"; any other value means the cadence
     * engine only surfaces this entry when the matching
     * [com.bios.app.model.RiskProfile] flag is set.
     */
    val riskGate: RiskGate = RiskGate.NONE,
    /**
     * How [cadenceMonths] should be interpreted. Defaults to
     * [CadenceKind.RECURRING] so every existing entry keeps its current
     * due/overdue behaviour; routine checkups opt into
     * [CadenceKind.MIN_INTERVAL_SINCE_LAST].
     */
    val cadenceKind: CadenceKind = CadenceKind.RECURRING,
)

/**
 * Static catalog of adult screening recommendations the cadence engine
 * iterates over. USPSTF anchors the base set; #191 layers in
 * hereditary-syndrome (NCCN), Ob/Gyn-society (ACOG / ACS),
 * cardiology-one-time (AHA-EAS Lp(a)), and IHS regional cadences. The
 * sub-lists stay separate so test fixtures can pin them individually,
 * and the engine reads [combined] for end-to-end evaluation.
 *
 * Region-localised catalogs (NHS, Tokutei Kenshin, KCDC, BowelScreen AU)
 * are still tracked as a follow-up via a future RegionConfig hook.
 */
object ScreeningCatalog {

    /** USPSTF adult schedule. */
    val uspstf: List<ScreeningCatalogEntry> = listOf(
        ScreeningCatalogEntry(
            key = "colorectal_cancer",
            displayName = "Colorectal cancer (colonoscopy or FIT)",
            minAge = 45, maxAge = 75, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "USPSTF 2021 (A recommendation) — colorectal cancer screening 45–75; annual " +
                "FIT or 10-yr colonoscopy. The 12-mo cadence here is the annual-FIT pace. Bios doesn't " +
                "know which modality you used, so if you had a colonoscopy record its date and read the " +
                "next-due as the 10-yr interval your provider set — the displayed cadence assumes FIT.",
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
            riskGate = RiskGate.EVER_SMOKER,
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

    /**
     * Hereditary cancer-syndrome surveillance cadences (#191). NCCN /
     * NCI / VHL Alliance / ATA sources. Entries surface only when the
     * owner has self-declared the matching flag on the risk-profile
     * screen. Bios never infers — the owner enters what they know.
     */
    val hereditary: List<ScreeningCatalogEntry> = HereditarySyndromeCatalog.entries

    /**
     * Ob/Gyn society cadences (#191) beyond USPSTF — ACS mammography
     * starting age 45 (a known divergence from USPSTF 40), ACOG
     * postmenopausal DEXA recurrence cadence. Documented per-entry.
     */
    val obGynSociety: List<ScreeningCatalogEntry> = ObGynSocietyCatalog.entries

    /**
     * Cardiology one-time / society-recommended screens (#191) —
     * AHA/EAS 2022 once-in-lifetime Lp(a). AAA is already in [uspstf]
     * under the ever-smoker gate; we don't duplicate it here.
     */
    val cardiology: List<ScreeningCatalogEntry> = CardiologyCatalog.entries

    /**
     * IHS / PAHO regional cadences (#191) — IHS Diabetes Standards
     * twice-yearly HbA1c for diabetic American Indian / Alaska Native
     * owners (T2DM prevalence ~2–3× baseline; Indigenous Americas
     * audit §2.4 named this).
     */
    val ihsPaho: List<ScreeningCatalogEntry> = IhsPahoCatalog.entries

    /**
     * Routine periodic medical checkups (periodic-checkup follow-up) —
     * the recurring clinical encounters that aren't disease-specific
     * screenings or vaccines: periodic wellness visit, BP check, dental,
     * eye, skin, hearing. Several use
     * [CadenceKind.MIN_INTERVAL_SINCE_LAST] so they read as a recommended
     * delay, never as overdue.
     */
    val checkups: List<ScreeningCatalogEntry> = CheckupCatalog.entries

    /**
     * Everything the engine should evaluate in the default cadence
     * screen. The UI iterates this list; each entry's [riskGate] +
     * [applicability] + age-band decide whether it actually renders for
     * the active owner.
     */
    val combined: List<ScreeningCatalogEntry> =
        uspstf + hereditary + obGynSociety + cardiology + ihsPaho + checkups
}
