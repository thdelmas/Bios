package com.bios.app.ui.biomarkers

import com.bios.contracts.MetricType

/**
 * One row group on the biomarker dashboard (#137). A panel is the
 * smallest unit of clinical co-meaning — labs that are typically drawn
 * together, that the owner reads as one story. Splitting the dashboard
 * by panel matches how lab reports themselves are organised.
 *
 * The list of panels is the dashboard's shipping contract. Adding a
 * panel here is a deliberate dashboard expansion; per-metric tiles are
 * driven by [BiomarkerPanel.metrics] so adding a [MetricType] to an
 * existing panel needs no UI work beyond its reference range.
 *
 * Manifesto: no panel composes a *"health score"* across its metrics —
 * each tile renders on its own and the owner reads the picture.
 */
data class BiomarkerPanel(
    val id: String,
    val title: String,
    val metrics: List<MetricType>,
)

object BiomarkerPanels {

    val cardiometabolic = BiomarkerPanel(
        id = "cardiometabolic",
        title = "Cardiometabolic",
        metrics = listOf(
            MetricType.TOTAL_CHOLESTEROL,
            MetricType.LDL_CHOLESTEROL,
            MetricType.HDL_CHOLESTEROL,
            MetricType.TRIGLYCERIDES,
            MetricType.APO_B,
            MetricType.LIPOPROTEIN_A,
            MetricType.HOMOCYSTEINE,
            MetricType.CORONARY_CALCIUM_SCORE,
        ),
    )

    val glycemic = BiomarkerPanel(
        id = "glycemic",
        title = "Glycemic",
        metrics = listOf(
            MetricType.HBA1C,
            MetricType.FASTING_GLUCOSE,
            MetricType.FASTING_INSULIN,
            MetricType.HOMA_IR,
        ),
    )

    val inflammation = BiomarkerPanel(
        id = "inflammation",
        title = "Inflammation & Iron",
        metrics = listOf(
            MetricType.HSCRP,
            MetricType.FERRITIN,
            MetricType.IRON_SERUM,
            MetricType.IRON_SATURATION_PCT,
            MetricType.TIBC,
        ),
    )

    val thyroid = BiomarkerPanel(
        id = "thyroid",
        title = "Thyroid",
        metrics = listOf(
            MetricType.TSH,
            MetricType.FREE_T4,
            MetricType.FREE_T3,
            MetricType.THYROID_PEROXIDASE_AB,
            MetricType.THYROGLOBULIN_AB,
        ),
    )

    val vitamins = BiomarkerPanel(
        id = "vitamins",
        title = "Vitamins & Minerals",
        metrics = listOf(
            MetricType.VITAMIN_D_25OH,
            MetricType.VITAMIN_B12,
            MetricType.FOLATE,
            MetricType.MAGNESIUM,
            MetricType.VITAMIN_K2,
            MetricType.VITAMIN_A_RETINOL,
            MetricType.VITAMIN_E_ALPHA_TOCOPHEROL,
        ),
    )

    val hematology = BiomarkerPanel(
        id = "hematology",
        title = "Hematology",
        metrics = listOf(
            MetricType.HEMOGLOBIN,
            MetricType.HEMATOCRIT,
            MetricType.WBC,
            MetricType.RBC,
            MetricType.PLATELETS,
            MetricType.MCV,
            MetricType.MCH,
            MetricType.MCHC,
            MetricType.RDW,
            MetricType.MPV,
            MetricType.NEUTROPHILS_PCT,
            MetricType.LYMPHOCYTES_PCT,
            MetricType.MONOCYTES_PCT,
            MetricType.EOSINOPHILS_PCT,
            MetricType.BASOPHILS_PCT,
        ),
    )

    val renal = BiomarkerPanel(
        id = "renal",
        title = "Renal",
        metrics = listOf(
            MetricType.EGFR,
            MetricType.CREATININE,
            MetricType.BUN,
            MetricType.URIC_ACID,
        ),
    )

    /**
     * Electrolytes + minerals (Blueprint audit §1.7 + §1.15). Sodium /
     * potassium / chloride / total CO2 are the four basic-metabolic-panel
     * electrolytes; calcium + phosphate ride along because both are
     * commonly reported on the same CMP and share the same clinical context
     * (parathyroid, renal, bone).
     */
    val electrolytes = BiomarkerPanel(
        id = "electrolytes",
        title = "Electrolytes & Minerals",
        metrics = listOf(
            MetricType.SODIUM,
            MetricType.POTASSIUM,
            MetricType.CHLORIDE,
            MetricType.CARBON_DIOXIDE,
            MetricType.CALCIUM_SERUM,
            MetricType.PHOSPHATE,
        ),
    )

    val hepatic = BiomarkerPanel(
        id = "hepatic",
        title = "Hepatic & Pancreatic",
        metrics = listOf(
            MetricType.ALT,
            MetricType.AST,
            MetricType.GGT,
            MetricType.ALBUMIN,
            MetricType.ALKALINE_PHOSPHATASE,
            MetricType.BILIRUBIN_TOTAL,
            MetricType.TOTAL_PROTEIN,
            MetricType.AMYLASE,
            MetricType.LIPASE,
        ),
    )

    val endocrine = BiomarkerPanel(
        id = "endocrine",
        title = "Endocrine",
        metrics = listOf(
            MetricType.TESTOSTERONE_TOTAL,
            MetricType.ESTRADIOL,
            MetricType.CORTISOL,
            MetricType.IGF_1,
        ),
    )

    /**
     * Reproductive endocrine axis (Blueprint audit §1.9 + §1.12). FSH/LH/
     * SHBG/AMH/free testosterone/prolactin/DHEA-S — sex- and (for women)
     * cycle-phase-dependent reference ranges, so the dashboard renders
     * the value without a coloured cutoff and the owner reads the lab's
     * own range supplied via the FHIR import.
     */
    val reproductiveEndocrine = BiomarkerPanel(
        id = "reproductive_endocrine",
        title = "Reproductive Endocrine",
        metrics = listOf(
            MetricType.FSH,
            MetricType.LH,
            MetricType.SHBG,
            MetricType.AMH,
            MetricType.TESTOSTERONE_FREE,
            MetricType.PROLACTIN,
            MetricType.DHEA_SULFATE,
        ),
    )

    val epigenetic = BiomarkerPanel(
        id = "epigenetic",
        title = "Aging Biomarkers",
        metrics = listOf(
            MetricType.EPIGENETIC_AGE_DUNEDIN_PACE,
            MetricType.EPIGENETIC_AGE_GRIM,
            MetricType.EPIGENETIC_AGE_PHENO,
            MetricType.EPIGENETIC_AGE_HORVATH,
            MetricType.TELOMERE_LENGTH,
        ),
    )

    /**
     * Men's prostate screening (Wave-2, audit §3.2). PSA Total is the
     * routine men-50+ screen; PSA Free / Total ratio refines borderline
     * elevated total PSA values.
     */
    val prostate = BiomarkerPanel(
        id = "prostate",
        title = "Prostate Screening",
        metrics = listOf(
            MetricType.PSA_TOTAL,
            MetricType.PSA_FREE,
        ),
    )

    /**
     * Skeletal health (Wave-2, audit §3.2). DEXA T-score is the single-
     * number osteoporosis screen; anatomical site rides in event_payloads.
     */
    val skeletal = BiomarkerPanel(
        id = "skeletal",
        title = "Bone Health",
        metrics = listOf(
            MetricType.BONE_DENSITY_T_SCORE,
        ),
    )

    /**
     * Plasma neurology (Wave-2, audit §3.2). pTau-217 is the emerging
     * Alzheimer's blood biomarker; single-tile panel today, will fill
     * out as additional neuro biomarkers ship (Aβ42/40 ratio, NfL, GFAP).
     */
    val neurology = BiomarkerPanel(
        id = "neurology",
        title = "Neurology",
        metrics = listOf(
            MetricType.PTAU_217,
        ),
    )

    /**
     * Cardio-oncology panel (#201). NT-proBNP and high-sensitivity troponin
     * are the AHA 2018 / ASCO 2017 cardio-oncology cardiac biomarkers used
     * to surveil for anthracycline / trastuzumab cardiotoxicity. ANC anchors
     * the IDSA 2010 febrile-neutropenia screen. The panel is dashboard-
     * visible to every owner because the assays are also used outside
     * cancer therapy (NT-proBNP for HF rule-out; troponin for ACS; ANC for
     * any cytopenia work-up).
     */
    val cardioOncology = BiomarkerPanel(
        id = "cardio_oncology",
        title = "Cardio-Oncology",
        metrics = listOf(
            MetricType.NT_PRO_BNP_PG_PER_ML,
            MetricType.TROPONIN_NG_PER_L,
            MetricType.ABSOLUTE_NEUTROPHIL_COUNT,
        ),
    )

    /** Render order on the dashboard — order matches the typical lab-
     *  report layout (lipids first, then metabolic, then inflammation). */
    val all: List<BiomarkerPanel> = listOf(
        cardiometabolic,
        glycemic,
        inflammation,
        thyroid,
        vitamins,
        hematology,
        electrolytes,
        renal,
        hepatic,
        endocrine,
        reproductiveEndocrine,
        prostate,
        skeletal,
        neurology,
        cardioOncology,
        epigenetic,
    )
}
