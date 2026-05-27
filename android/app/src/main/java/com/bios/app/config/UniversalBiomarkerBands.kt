package com.bios.app.config

import com.bios.contracts.MetricType

/**
 * Biomarker bands that are clinically identical across every region Bios
 * supports. ADA / WHO / NICE / JSCC / etc. all use the same cut-offs for these
 * assays; SI ↔ US unit conversions are handled by [UnitDisplay], not here.
 * When a future biomarker has region-divergent bands (e.g. JDS historically
 * used different HbA1c bands), the region-specific config can override.
 *
 * Extracted from [RegionConfigProvider] so per-continent region tables can
 * share it without re-importing the full provider.
 */
internal val universalBiomarkerBands: Map<MetricType, BiomarkerBands> = mapOf(
    // hsCRP (mg/L): <1.0 low, 1.0–3.0 moderate, ≥3.0 high
    // (Ridker 2003; Pearson AHA/CDC 2003)
    MetricType.HSCRP to BiomarkerBands(normalCeiling = 1.0, borderlineCeiling = 3.0),
    // Standard-sensitivity CRP (#354), mg/L. Distinct assay from hsCRP —
    // tracks acute inflammation / infection, not low-grade cardiovascular
    // risk. Bands: <10 normal-ish, 10–50 inflammation notice, ≥50 marked
    // inflammation (Pepys & Hirschfield 2003; Sproston & Ashworth 2018).
    MetricType.CRP to BiomarkerBands(
        normalCeiling = 10.0, borderlineCeiling = 50.0,
        concerningDirection = BandDirection.ABOVE,
    ),
    // HbA1c (%): <5.7 normal, 5.7–6.5 prediabetic, ≥6.5 diabetic (ADA 2024)
    MetricType.HBA1C to BiomarkerBands(normalCeiling = 5.7, borderlineCeiling = 6.5),
    // Total cholesterol (mg/dL): <200 desirable, 200–239 borderline, ≥240 high
    // (NCEP ATP III; AHA 2018 guideline)
    MetricType.TOTAL_CHOLESTEROL to BiomarkerBands(
        normalCeiling = 200.0, borderlineCeiling = 240.0,
    ),
    // LDL-C (mg/dL): <100 optimal, 100–160 borderline, ≥160 high (NCEP ATP III)
    MetricType.LDL_CHOLESTEROL to BiomarkerBands(
        normalCeiling = 100.0, borderlineCeiling = 160.0,
    ),
    // HDL-C (mg/dL): <40 concerning low, 40–60 borderline, ≥60 protective
    // — INVERSE direction: low HDL is the clinical concern (NCEP ATP III)
    MetricType.HDL_CHOLESTEROL to BiomarkerBands(
        normalCeiling = 40.0, borderlineCeiling = 60.0,
        concerningDirection = BandDirection.BELOW,
    ),
    // Triglycerides (mg/dL): <150 normal, 150–200 borderline, ≥200 high
    // (NCEP ATP III)
    MetricType.TRIGLYCERIDES to BiomarkerBands(
        normalCeiling = 150.0, borderlineCeiling = 200.0,
    ),
    // ApoB (mg/dL): <90 desirable, 90–120 borderline, ≥120 high
    // (Sniderman et al. 2019; AACC 2023 — particle-count gold standard)
    MetricType.APO_B to BiomarkerBands(
        normalCeiling = 90.0, borderlineCeiling = 120.0,
    ),
    // 25-OH vitamin D (ng/mL): <20 deficient, 20–30 insufficient, ≥30 sufficient
    // — INVERSE direction: low values are the clinical concern
    // (Holick et al. 2011 — Endocrine Society Clinical Practice Guideline)
    MetricType.VITAMIN_D_25OH to BiomarkerBands(
        normalCeiling = 20.0, borderlineCeiling = 30.0,
        concerningDirection = BandDirection.BELOW,
    ),
    // TSH (mIU/L): <0.4 hyperthyroid, 0.4–4.0 normal, 4.0–10.0 subclinical
    // hypothyroid, ≥10.0 overt hypothyroid. (AACE/ATA 2012; ATA 2014)
    MetricType.TSH to BiomarkerBands(
        normalCeiling = 4.0, borderlineCeiling = 10.0,
        concerningDirection = BandDirection.ABOVE,
        lowCeiling = 0.4,
    ),
    // Free T4 (ng/dL) — hypo-focused window. (AACE 2012)
    MetricType.FREE_T4 to BiomarkerBands(
        normalCeiling = 0.8, borderlineCeiling = 1.0,
        concerningDirection = BandDirection.BELOW,
    ),
    // Free T3 (pg/mL) — bidirectional (Ross 2016 / Garber 2012)
    MetricType.FREE_T3 to BiomarkerBands(
        normalCeiling = 4.2, borderlineCeiling = 6.5,
        concerningDirection = BandDirection.ABOVE,
        lowCeiling = 2.3,
    ),
    // -- CBC panel --
    // Hemoglobin (g/dL): WHO anemia thresholds. (WHO 2011)
    MetricType.HEMOGLOBIN to BiomarkerBands(
        normalCeiling = 12.0, borderlineCeiling = 13.5,
        concerningDirection = BandDirection.BELOW,
    ),
    // Hematocrit (%) — combined-sex conservative. (WHO 2011)
    MetricType.HEMATOCRIT to BiomarkerBands(
        normalCeiling = 36.0, borderlineCeiling = 40.0,
        concerningDirection = BandDirection.BELOW,
    ),
    // WBC (giga/L) — bidirectional. (Williams Hematology 10th ed.)
    MetricType.WBC to BiomarkerBands(
        normalCeiling = 11.0, borderlineCeiling = 15.0,
        concerningDirection = BandDirection.ABOVE,
        lowCeiling = 4.5,
    ),
    // RBC (tera/L). (Williams Hematology 10th ed.)
    MetricType.RBC to BiomarkerBands(
        normalCeiling = 4.0, borderlineCeiling = 4.5,
        concerningDirection = BandDirection.BELOW,
    ),
    // Platelets (giga/L). (NCI CTCAE; Williams Hematology 10th ed.)
    MetricType.PLATELETS to BiomarkerBands(
        normalCeiling = 100.0, borderlineCeiling = 150.0,
        concerningDirection = BandDirection.BELOW,
    ),
    // -- Renal / hepatic / insulin-resistance panel --
    // eGFR (mL/min/1.73m²) — KDIGO 2024
    MetricType.EGFR to BiomarkerBands(
        normalCeiling = 60.0, borderlineCeiling = 90.0,
        concerningDirection = BandDirection.BELOW,
    ),
    // Creatinine (mg/dL) — KDIGO 2024
    MetricType.CREATININE to BiomarkerBands(
        normalCeiling = 1.1, borderlineCeiling = 1.3,
        concerningDirection = BandDirection.ABOVE,
    ),
    // ALT (U/L) — AASLD 2017
    MetricType.ALT to BiomarkerBands(
        normalCeiling = 33.0, borderlineCeiling = 50.0,
        concerningDirection = BandDirection.ABOVE,
    ),
    // AST (U/L) — AASLD 2017
    MetricType.AST to BiomarkerBands(
        normalCeiling = 35.0, borderlineCeiling = 50.0,
        concerningDirection = BandDirection.ABOVE,
    ),
    // GGT (U/L) — AASLD 2017
    MetricType.GGT to BiomarkerBands(
        normalCeiling = 40.0, borderlineCeiling = 60.0,
        concerningDirection = BandDirection.ABOVE,
    ),
    // HOMA-IR — Matthews 1985 / Tam 2012
    MetricType.HOMA_IR to BiomarkerBands(
        normalCeiling = 2.0, borderlineCeiling = 2.5,
        concerningDirection = BandDirection.ABOVE,
    ),
    // INR (#352) — bidirectional. Therapeutic windows depend on indication
    // (warfarin: 2.0–3.0 for AFib/VTE, 2.5–3.5 for mechanical valves) and
    // belong to the owner + clinician, not Bios — these bands flag the
    // universal alarms only. Low INR <0.8 is rare and usually a lab artefact;
    // high INR >5.0 is the over-anticoagulation bleed-risk alarm (ACCP 2012,
    // ACC/AHA AFib 2019). Quick / PT / aPTT are reference-range descriptive
    // only (no universal banding without anticoagulant context).
    MetricType.INR to BiomarkerBands(
        normalCeiling = 1.2, borderlineCeiling = 5.0,
        concerningDirection = BandDirection.ABOVE,
        lowCeiling = 0.8,
    ),
    // Absolute lymphocyte count (#353) — BELOW direction. Lymphopenia
    // alarm thresholds: <1.0 ×10³/µL borderline lymphopenia, <0.5 ×10³/µL
    // severe (CTCAE v5.0 grade ≥3; ASCO/IDSA neutropenic-fever workup).
    // For BELOW direction: <normalCeiling = CONCERNING, [normalCeiling,
    // borderlineCeiling) = BORDERLINE, ≥borderlineCeiling = NORMAL.
    MetricType.ABSOLUTE_LYMPHOCYTE_COUNT to BiomarkerBands(
        normalCeiling = 500.0, borderlineCeiling = 1000.0,
        concerningDirection = BandDirection.BELOW,
    ),
    // Absolute eosinophil count (#353) — ABOVE direction. Eosinophilia
    // thresholds: ≥500/µL routine eosinophilia notice, ≥1500/µL is the
    // hypereosinophilia / HES workup trigger (Valent 2012; Klion 2017).
    MetricType.ABSOLUTE_EOSINOPHIL_COUNT to BiomarkerBands(
        normalCeiling = 500.0, borderlineCeiling = 1500.0,
        concerningDirection = BandDirection.ABOVE,
    ),
    // AMC and ABC (#353) — no universal alarm threshold; reference range
    // depends on whether the lab reports as part of a 5-cell or 6-cell
    // differential. Banded as reference-range descriptive only.

    // -- Wave 6 expansion (#339) --
    // Alkaline phosphatase (U/L): adult universal upper ~120 U/L; >250 is
    // the marked cholestatic / Paget tier worth prompt evaluation.
    // (AASLD 2017; Newton 2011 — cholestatic injury workup)
    MetricType.ALKALINE_PHOSPHATASE to BiomarkerBands(
        normalCeiling = 120.0, borderlineCeiling = 250.0,
        concerningDirection = BandDirection.ABOVE,
    ),
    // Lipoprotein(a) (nmol/L): <75 desirable, 75–125 borderline, ≥125 high
    // — independent ASCVD risk. (ESC 2019 dyslipidaemia guideline §6.4;
    // NLA 2019 — Wilson et al.)
    MetricType.LIPOPROTEIN_A to BiomarkerBands(
        normalCeiling = 75.0, borderlineCeiling = 125.0,
        concerningDirection = BandDirection.ABOVE,
    ),
    // D-dimer (ng/mL FEU): <500 negative VTE rule-out; 500–2000 elevated;
    // ≥2000 marked. Age-adjusted rule (age × 10 above 50 y) is a separate
    // localization concern and not modeled here. (Wells 2003; ESC 2019 PE)
    MetricType.D_DIMER to BiomarkerBands(
        normalCeiling = 500.0, borderlineCeiling = 2000.0,
        concerningDirection = BandDirection.ABOVE,
    ),
    // Omega-3 index (%): <4 high cardiovascular risk, 4–8 intermediate,
    // ≥8 protective. INVERSE direction. (Harris & von Schacky 2004)
    MetricType.OMEGA_3_INDEX to BiomarkerBands(
        normalCeiling = 4.0, borderlineCeiling = 8.0,
        concerningDirection = BandDirection.BELOW,
    ),
    // Reverse T3 / leptin / adiponectin (#339): population-dependent
    // reference ranges (sex, age, BMI for leptin; assay-vendor for rT3);
    // descriptive-only display until the data shape settles per issue scope.
)
