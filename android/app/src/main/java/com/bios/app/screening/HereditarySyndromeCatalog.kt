package com.bios.app.screening

/**
 * Hereditary cancer-syndrome surveillance cadences (#191). Closes the
 * gap flagged in [docs/audits/ONCOLOGY_POV.md §1] and
 * [docs/audits/MEDICAL_PROFESSIONAL_POV.md §2 Gap #2] — the original
 * cadence engine shipped a single first-degree-BRCA flag and nothing
 * else for hereditary surveillance.
 *
 * **Source.** NCCN Clinical Practice Guidelines in Oncology (Genetic /
 * Familial High-Risk Assessment series), ATA Medullary Thyroid Cancer
 * Management Guidelines (MEN2A/2B prophylactic thyroidectomy timing),
 * and the VHL Alliance Active Surveillance Guidelines. The values
 * captured here are conservative, owner-pull-side renderings — the
 * cadence engine is a calculator, not a substitute for genetics
 * counselling. Owner-asserted syndrome flags gate every entry via
 * [RiskGate].
 *
 * **Selection.** Six-to-ten highest-impact protocols per the issue
 * brief; we picked the canonical ten the issue named (Lynch, Li-
 * Fraumeni, FAP, Cowden, Peutz-Jeghers, VHL, MEN1, MEN2A, MEN2B, HDGC)
 * with two Lynch-specific sub-entries (colonoscopy + endometrial) so
 * the two organ systems track independently.
 */
internal object HereditarySyndromeCatalog {

    val entries: List<ScreeningCatalogEntry> = listOf(
        // Lynch syndrome (HNPCC). NCCN Genetic/Familial High-Risk
        // Assessment: Colorectal — colonoscopy every 1–2 years starting
        // age 20–25 (or 2–5 years before the earliest case in the
        // family, whichever is earlier). We pin to age 20 floor and the
        // 18-month midpoint cadence; the screen renders the citation so
        // the owner sees the underlying range.
        ScreeningCatalogEntry(
            key = "lynch_colonoscopy",
            displayName = "Lynch — colonoscopy",
            minAge = 20, maxAge = null, cadenceMonths = 18,
            applicability = Applicability.UNIVERSAL,
            citation = "NCCN Genetic/Familial High-Risk Assessment: Colorectal — Lynch syndrome surveillance, colonoscopy every 1–2 years from age 20–25",
            riskGate = RiskGate.LYNCH_SYNDROME,
        ),
        // Lynch — endometrial sampling. NCCN Uterine Neoplasms +
        // Genetic/Familial High-Risk: Colorectal — annual endometrial
        // biopsy starting 30–35 in Lynch carriers with intact uterus.
        ScreeningCatalogEntry(
            key = "lynch_endometrial",
            displayName = "Lynch — endometrial biopsy",
            minAge = 30, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.PRESENTS_AS_FEMALE,
            citation = "NCCN Uterine Neoplasms / Genetic-Familial High-Risk: Colorectal — annual endometrial sampling from age 30–35 in Lynch syndrome",
            riskGate = RiskGate.LYNCH_SYNDROME,
        ),
        // Li-Fraumeni. NCCN Li-Fraumeni Syndrome management + the
        // Toronto protocol — annual whole-body MRI (and brain MRI)
        // from diagnosis; breast MRI annually 20–75.
        ScreeningCatalogEntry(
            key = "li_fraumeni_wb_mri",
            displayName = "Li-Fraumeni — whole-body MRI",
            minAge = 18, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "NCCN / Toronto Protocol — Li-Fraumeni syndrome surveillance, annual whole-body MRI from diagnosis",
            riskGate = RiskGate.LI_FRAUMENI,
        ),
        // FAP. NCCN Genetic/Familial High-Risk: Colorectal — annual
        // colonoscopy from age 10–15 in classical FAP; we pin the floor
        // to 10 and cadence to 12 months. (Post-colectomy surveillance
        // is left to clinician-side workflow.)
        ScreeningCatalogEntry(
            key = "fap_colonoscopy",
            displayName = "FAP — annual colonoscopy",
            minAge = 10, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "NCCN Genetic/Familial High-Risk: Colorectal — Familial Adenomatous Polyposis surveillance, annual colonoscopy from age 10–15",
            riskGate = RiskGate.FAP,
        ),
        // Cowden syndrome (PTEN hamartoma tumour syndrome). NCCN
        // Genetic/Familial High-Risk: Breast / Ovarian / Pancreatic +
        // PTEN management — annual breast MRI from age 30, annual
        // thyroid ultrasound from diagnosis.
        ScreeningCatalogEntry(
            key = "cowden_breast_mri",
            displayName = "Cowden — breast MRI",
            minAge = 30, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.PRESENTS_AS_FEMALE,
            citation = "NCCN PTEN Hamartoma Tumor Syndrome management — annual breast MRI from age 30 in Cowden syndrome",
            riskGate = RiskGate.COWDEN,
        ),
        ScreeningCatalogEntry(
            key = "cowden_thyroid_us",
            displayName = "Cowden — thyroid ultrasound",
            minAge = 18, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "NCCN PTEN Hamartoma Tumor Syndrome management — annual thyroid ultrasound in Cowden syndrome",
            riskGate = RiskGate.COWDEN,
        ),
        // Peutz-Jeghers syndrome. NCCN Genetic/Familial High-Risk:
        // Colorectal (Peutz-Jeghers) — small-bowel imaging (MR
        // enterography or capsule) every 2–3 years from age 8–10. We
        // pin age floor 8 and cadence to the 30-month midpoint.
        ScreeningCatalogEntry(
            key = "peutz_jeghers_small_bowel",
            displayName = "Peutz-Jeghers — small-bowel imaging",
            minAge = 8, maxAge = null, cadenceMonths = 30,
            applicability = Applicability.UNIVERSAL,
            citation = "NCCN Genetic/Familial High-Risk: Colorectal — Peutz-Jeghers syndrome, small-bowel imaging every 2–3 years from age 8–10",
            riskGate = RiskGate.PEUTZ_JEGHERS,
        ),
        // VHL. VHL Alliance Active Surveillance Guidelines — annual
        // ophthalmology + abdominal MRI from age 16 (retinal exam
        // earlier, but the catalog floor here pins the imaging arm).
        ScreeningCatalogEntry(
            key = "vhl_abdominal_mri",
            displayName = "VHL — abdominal MRI",
            minAge = 16, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "VHL Alliance Active Surveillance Guidelines — annual abdominal MRI from age 16 in von Hippel-Lindau",
            riskGate = RiskGate.VHL,
        ),
        // MEN1. NCCN Neuroendocrine and Adrenal Tumors — annual
        // biochemical surveillance (prolactin, ionised calcium, fasting
        // gastrin) from age 5–10. We pin a clinically usable adult
        // floor of 16 for the unsupervised pull-side surface; younger
        // carriers should be seen by a paediatric endocrinologist.
        ScreeningCatalogEntry(
            key = "men1_biochem",
            displayName = "MEN1 — annual biochemical panel",
            minAge = 16, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "NCCN Neuroendocrine and Adrenal Tumors — MEN1 surveillance, annual prolactin / calcium / fasting gastrin",
            riskGate = RiskGate.MEN1,
        ),
        // MEN2A. ATA Medullary Thyroid Cancer Management Guidelines —
        // annual calcitonin + plasma metanephrines after the
        // prophylactic thyroidectomy already-recommended in early
        // childhood; we represent the post-thyroidectomy adult
        // surveillance arm here.
        ScreeningCatalogEntry(
            key = "men2a_surveillance",
            displayName = "MEN2A — calcitonin / metanephrines",
            minAge = 16, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "ATA Medullary Thyroid Cancer Management Guidelines — MEN2A annual calcitonin + metanephrines surveillance",
            riskGate = RiskGate.MEN2A,
        ),
        // MEN2B. ATA Medullary Thyroid Cancer Management Guidelines —
        // same adult-surveillance shape as 2A but the underlying
        // prophylactic thyroidectomy happens in the first year of life,
        // not at age 5.
        ScreeningCatalogEntry(
            key = "men2b_surveillance",
            displayName = "MEN2B — calcitonin / metanephrines",
            minAge = 16, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "ATA Medullary Thyroid Cancer Management Guidelines — MEN2B annual calcitonin + metanephrines surveillance",
            riskGate = RiskGate.MEN2B,
        ),
        // HDGC (CDH1+). NCCN Gastric Cancer — when prophylactic total
        // gastrectomy is declined, annual EGD with random biopsies.
        ScreeningCatalogEntry(
            key = "hdgc_egd",
            displayName = "HDGC (CDH1+) — annual EGD",
            minAge = 18, maxAge = null, cadenceMonths = 12,
            applicability = Applicability.UNIVERSAL,
            citation = "NCCN Gastric Cancer — Hereditary Diffuse Gastric Cancer (CDH1+) surveillance, annual EGD when prophylactic gastrectomy declined",
            riskGate = RiskGate.HDGC,
        ),
    )
}
