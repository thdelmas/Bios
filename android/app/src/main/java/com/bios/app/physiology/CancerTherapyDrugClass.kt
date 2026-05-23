package com.bios.app.physiology

/**
 * Owner-declared cancer-therapy drug class (#201, audit gap from
 * ONCOLOGY_POV §2.3-2.5 + CARDIOLOGY_POV §2.3).
 *
 * Cancer-therapy toxicity patterns (anthracycline cardiotoxicity, ICI
 * pneumonitis, etc.) are drug-class specific. Anthracyclines cause LV
 * dysfunction; trastuzumab causes a different cardiotoxicity profile;
 * ICIs (PD-1/PD-L1) cause immune-related adverse events (pneumonitis,
 * colitis, thyroiditis, myocarditis); TKIs cause vascular and metabolic
 * effects; CAR-T causes CRS in the early recovery window.
 *
 * The owner picks the class that matches their regimen alongside the
 * cancer-treatment [PhysiologyState]. Bios never infers drug class from
 * any signal — the owner is the source. [NONE] means "treatment state
 * declared but drug class not specified"; the corresponding patterns
 * stay silent until both axes are set.
 *
 * The drug-class set is a closed enum (no free-text drug name) because
 * the pattern engine evaluates membership, not pharmacovigilance — the
 * class is what determines which signals matter. A future companion
 * (medications) can hold the per-drug detail; this enum is the
 * minimum surface the cardio-oncology patterns need.
 */
enum class CancerTherapyDrugClass(val displayName: String) {
    /** No drug class declared. Treatment-state patterns stay silent. */
    NONE("Not specified"),

    /**
     * Anthracyclines (doxorubicin, daunorubicin, epirubicin, idarubicin).
     * Dose-dependent LV dysfunction; AHA 2018 + ASCO 2017 cardio-oncology
     * guidelines anchor LVEF surveillance to cumulative dose.
     */
    ANTHRACYCLINE("Anthracycline (doxorubicin / epirubicin / daunorubicin)"),

    /**
     * HER2-targeted therapy (trastuzumab, pertuzumab, T-DM1, T-DXd).
     * Reversible cardiotoxicity profile distinct from anthracyclines;
     * ASCO 2017 + ESMO cardio-oncology recommend periodic LVEF.
     */
    TRASTUZUMAB("HER2-targeted (trastuzumab / pertuzumab / T-DM1 / T-DXd)"),

    /**
     * Immune-checkpoint inhibitor — PD-1 (nivolumab, pembrolizumab,
     * cemiplimab, dostarlimab) and PD-L1 (atezolizumab, durvalumab,
     * avelumab). irAE risk profile per ESMO 2022 + ASCO 2021 irAE
     * management guidelines.
     */
    ICI_PD1_PDL1("Immune-checkpoint inhibitor (PD-1 / PD-L1)"),

    /**
     * Tyrosine-kinase inhibitor — VEGFR-class (sunitinib, sorafenib,
     * pazopanib, axitinib, cabozantinib, regorafenib). Hypertension,
     * QT prolongation, hand-foot syndrome, and thyroid effects per
     * ESC 2022 cardio-oncology.
     */
    TKI("Tyrosine-kinase inhibitor (sunitinib / sorafenib / VEGFR-class)"),

    /**
     * CAR-T cell therapy (axicabtagene, tisagenlecleucel, brexucabtagene,
     * lisocabtagene, idecabtagene). CRS + ICANS risk in the first 14-28
     * days post-infusion; cardiotoxicity overlay per ASCO 2024.
     */
    CAR_T("CAR-T cell therapy");
}
