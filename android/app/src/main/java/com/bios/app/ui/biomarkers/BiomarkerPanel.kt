package com.bios.app.ui.biomarkers

import com.bios.contracts.MetricType

/**
 * One row group on the lab values dashboard (#137): labs that are
 * typically drawn together. Splitting the dashboard by panel matches how
 * lab reports themselves are organised.
 *
 * The list of panels is the dashboard's shipping contract. Adding a
 * panel here is a deliberate dashboard expansion; per-metric tiles are
 * driven by [BiomarkerPanel.metrics] so adding a [MetricType] to an
 * existing panel needs no UI work.
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
            MetricType.OMEGA_3_INDEX,
            MetricType.ADIPONECTIN,
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
        title = "C-reactive protein & Iron",
        metrics = listOf(
            MetricType.HSCRP,
            MetricType.CRP,
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
            MetricType.REVERSE_T3,
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
            // Absolute leukocyte differential (#353) — ANC stays on the
            // cardio-oncology panel where it anchors the febrile-neutropenia
            // signature; the four companion counts ride here.
            MetricType.ABSOLUTE_LYMPHOCYTE_COUNT,
            MetricType.ABSOLUTE_MONOCYTE_COUNT,
            MetricType.ABSOLUTE_EOSINOPHIL_COUNT,
            MetricType.ABSOLUTE_BASOPHIL_COUNT,
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
            MetricType.LEPTIN,
        ),
    )

    /** FSH, LH, SHBG, AMH, free testosterone, prolactin, DHEA-S. */
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
        title = "Epigenetic",
        metrics = listOf(
            MetricType.EPIGENETIC_AGE_DUNEDIN_PACE,
            MetricType.EPIGENETIC_AGE_GRIM,
            MetricType.EPIGENETIC_AGE_PHENO,
            MetricType.EPIGENETIC_AGE_HORVATH,
            MetricType.TELOMERE_LENGTH,
        ),
    )

    /** PSA total and free. */
    val prostate = BiomarkerPanel(
        id = "prostate",
        title = "Prostate",
        metrics = listOf(
            MetricType.PSA_TOTAL,
            MetricType.PSA_FREE,
        ),
    )

    /** DEXA T-score; the anatomical site rides in event_payloads. */
    val skeletal = BiomarkerPanel(
        id = "skeletal",
        title = "Bone",
        metrics = listOf(
            MetricType.BONE_DENSITY_T_SCORE,
        ),
    )

    /** Plasma pTau-217; single-tile panel today. */
    val neurology = BiomarkerPanel(
        id = "neurology",
        title = "Neurology",
        metrics = listOf(
            MetricType.PTAU_217,
        ),
    )

    /** NT-proBNP, high-sensitivity troponin and absolute neutrophil count (#201). */
    val cardioOncology = BiomarkerPanel(
        id = "cardio_oncology",
        title = "Cardiac markers & neutrophils",
        metrics = listOf(
            MetricType.NT_PRO_BNP_PG_PER_ML,
            MetricType.TROPONIN_NG_PER_L,
            MetricType.ABSOLUTE_NEUTROPHIL_COUNT,
        ),
    )

    /** PT, INR, aPTT, the Quick index, the aPTT ratio and D-dimer (#352). */
    val coagulation = BiomarkerPanel(
        id = "coagulation",
        title = "Coagulation",
        metrics = listOf(
            MetricType.PROTHROMBIN_TIME,
            MetricType.INR,
            MetricType.QUICK_INDEX,
            MetricType.APTT,
            MetricType.APTT_RATIO,
            MetricType.D_DIMER,
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
        coagulation,
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
