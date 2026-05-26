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
            // #203 biomarker-panel expansion — cardiovascular risk
            // amplifiers and metabolic-hormone axis. Lp(a) is a once-in-
            // a-lifetime inherited atherogenic factor; omega-3 index,
            // leptin, adiponectin are the cardiometabolic-syndrome
            // matrix markers (EAS 2022, Harris 2008, IFM matrix).
            MetricType.LP_A_NMOL_PER_L,
            MetricType.D_DIMER_NG_PER_ML,
            MetricType.OMEGA_3_INDEX_PCT,
            MetricType.LEPTIN_NG_PER_ML,
            MetricType.ADIPONECTIN_MG_PER_L,
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
            // #203 — methylation / inflammation extensions. Homocysteine
            // and urate are functional-medicine markers; TIBC pairs with
            // ferritin to disambiguate iron-deficiency vs. anemia of
            // chronic disease.
            MetricType.HOMOCYSTEINE_UMOL_PER_L,
            MetricType.URIC_ACID_UMOL_PER_L,
            MetricType.TIBC_UMOL_PER_L,
        ),
    )

    val thyroid = BiomarkerPanel(
        id = "thyroid",
        title = "Thyroid",
        metrics = listOf(
            MetricType.TSH,
            MetricType.FREE_T4,
            MetricType.FREE_T3,
            // #203 — autoimmune-thyroid + functional-medicine extensions.
            // TPO Ab is the Hashimoto's screen; reverse T3 is the
            // euthyroid-sick-syndrome marker (IFM matrix, ATA 2014).
            MetricType.REVERSE_T3_NG_PER_DL,
            MetricType.THYROID_PEROXIDASE_AB_KIU_PER_L,
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
        ),
    )

    val renal = BiomarkerPanel(
        id = "renal",
        title = "Renal",
        metrics = listOf(
            MetricType.EGFR,
            MetricType.CREATININE,
        ),
    )

    val hepatic = BiomarkerPanel(
        id = "hepatic",
        title = "Hepatic",
        metrics = listOf(
            MetricType.ALT,
            MetricType.AST,
            MetricType.GGT,
            // #203 hepatic-panel completion — cholestatic + synthetic-
            // function + bilirubin axis (AASLD 2017 / 2021).
            MetricType.ALP_U_PER_L,
            MetricType.TOTAL_BILIRUBIN_UMOL_PER_L,
            MetricType.ALBUMIN_G_PER_L,
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
            // #203 — adrenal reserve + sex-hormone-binding capacity
            // extensions (Endocrine Society 2014 / 2018).
            MetricType.DHEA_S_UMOL_PER_L,
            MetricType.SHBG_NMOL_PER_L,
        ),
    )

    val epigenetic = BiomarkerPanel(
        id = "epigenetic",
        title = "Epigenetic Age",
        metrics = listOf(
            MetricType.EPIGENETIC_AGE_DUNEDIN_PACE,
            MetricType.EPIGENETIC_AGE_GRIM,
            MetricType.EPIGENETIC_AGE_PHENO,
            MetricType.EPIGENETIC_AGE_HORVATH,
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
        renal,
        hepatic,
        endocrine,
        epigenetic,
    )
}
