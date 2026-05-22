# Oncology Audit — Bios as a Patient-Side Cancer-Prevention and Survivorship Observation Feed

**Scope:** Bios's clinical reach as a longitudinal cancer-relevant observation layer, evaluated from the perspective of a board-certified medical oncologist with subspecialty exposure to radiation oncology, surgical oncology, paediatric haematology-oncology, and palliative cancer care. The catalogue entry that anchors this audit is [MEDICAL_SPECIALTIES_WORLDWIDE.md §1.8](MEDICAL_SPECIALTIES_WORLDWIDE.md) ("Cancer-focused — medical oncology, radiation oncology, surgical oncology, paediatric haematology-oncology, gynaecologic oncology, neuro-oncology").
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Western biomedical oncology, NCCN / USPSTF / ASCO / ESMO / IARC / CTCAE guideline-anchored. Not a regulatory or 510(k) audit. Not a digital-pathology or radiomic-imaging audit — those live downstream of the cancer-care system Bios sits beside. Written so a practising oncologist with subspecialty literacy can decide whether Bios is useful as patient-side context during prevention, active treatment, and survivorship.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [RiskProfile.kt](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt). Skimmed first: [MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md) (primary-care audit) and [CARDIOLOGY_POV.md](CARDIOLOGY_POV.md) — this audit reframes the primary-care Gap #2 (screening cadence), Gap #3 (immunisation), and Gap #6 (family history) through the oncology lens and does not re-litigate items closed in either prior audit.

---

## Executive summary

Cancer is the second-leading cause of death globally (IARC GLOBOCAN 2022: ~20 million new cases and ~9.7 million cancer deaths in 2022; projected to ~35 million new cases in 2050). The single highest-leverage population-level oncology intervention available today is **age-stratified screening cadence** — mammography, colonoscopy/FIT, cervical Pap/HPV, low-dose lung CT for eligible smokers, and (controversially) PSA. The next-highest-leverage interventions are **tertiary prevention** through modifiable lifestyle exposure (tobacco, alcohol, UV, obesity, HPV/HBV vaccination — IARC has classified these as Group 1 carcinogens or proven aetiologic agents), and **active-treatment toxicity surveillance** (chemotherapy myelosuppression, immunotherapy irAEs, anthracycline cardiotoxicity, radiation dermatitis/pneumonitis/cystitis). Bios's structural posture — passive observation, manifesto-bounded silence, on-device computation, pull-side owner-asked surfaces — is **unusually well-aligned with the cancer-prevention mission**. The reason is that oncology prevention is fundamentally a cadence and risk-stratification problem, not a real-time alerting problem; "the instrument the owner reads to decide" is exactly the right register.

That said, the cancer lens surfaces a set of gaps a generalist would not flag. Ordered by population-level oncology impact:

1. **No age- and risk-stratified screening cadence engine.** This is the primary-care audit's Gap #2 reframed and intensified. USPSTF, ACS, NCCN, and ESMO all centre cancer prevention on cadence-driven screening: mammography (USPSTF 2024: biennial 40–74; ACS: annual 45–54 then biennial), colonoscopy or FIT (USPSTF 2021: 45–75; recently lowered from 50 in response to early-onset CRC rise), cervical Pap/HPV (21–65, USPSTF 2018), low-dose lung CT (50–80 with ≥20 pack-years and quit <15 y ago, USPSTF 2021), PSA (controversial, USPSTF 2018 grade-C 55–69 shared-decision), DEXA (postmenopausal women and ≥65 men with risk factors). Bios has [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) infrastructure ready for region-anchored guideline cadence and [RiskProfile.kt](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt) carrying `firstDegreeBreastOvarianCancer` / `firstDegreeColorectalCancer` / `firstDegreeMelanoma` plus `personalTobaccoPackYears` — every input the lung-CT and early-CRC cadence shifts need. The cadence engine itself is the missing piece, and it is the single most consequential oncology-relevant addition Bios could ship.
2. **Hereditary cancer syndromes have no RiskContext beyond breast/ovarian/colorectal flags.** BRCA1/2 (lifetime breast cancer risk 55–72 %, ovarian 39–44 %; mastectomy + salpingo-oophorectomy decisions; NCCN Genetic/Familial High-Risk Assessment Breast/Ovarian/Pancreatic v2.2025), Lynch syndrome (lifetime CRC risk 15–80 % depending on the mismatch-repair gene; annual colonoscopy from 20–25; NCCN Genetic/Familial v3.2025), Li-Fraumeni (TP53; "Toronto protocol" annual WB-MRI + breast MRI from age 20), familial adenomatous polyposis (APC; CRC risk approaching 100 % by 40 without colectomy), Cowden, Peutz-Jeghers, von Hippel-Lindau, MEN1/MEN2, hereditary diffuse gastric cancer (CDH1) — these populations have *radically* different screening cadences than the general population and are exactly the cases where a missed cadence prompt causes the highest harm. Bios has no syndrome-level entity, only first-degree-relative booleans.
3. **No surface for treatment-related cardiotoxicity surveillance.** Anthracyclines (doxorubicin, epirubicin: cumulative-dose-dependent dilated cardiomyopathy, ESC 2022 Cardio-Oncology Guideline), trastuzumab (HER2-targeted: reversible LVEF decline, ~7–28 % incidence; serial LVEF + GLS monitoring per ASCO 2017 cardio-oncology guideline), TKIs (sunitinib, sorafenib: hypertension + LVEF decline + QT prolongation), immune checkpoint inhibitors (rare but high-mortality fulminant myocarditis, 0.3–1.1 % incidence per JAMA Oncol 2018), CAR-T (cytokine release syndrome with tachycardia, hypotension). Bios already ingests RHR, HRV, BP, and biomarker labs including troponin (... not yet — see Gap #11 below). The cardio-oncology surveillance window is structurally identical to the heart-failure-decompensation window the cardiology audit flagged as Gap #4; an oncology-aware adaptation is plausible inside the existing architecture.
4. **Chemotherapy toxicity convergence is unrealised despite the substrate being present.** Neutropenic fever (CTCAE v5 grade 3+: ANC <500 with temp ≥38.3 °C — oncology emergency; STAR remote-symptom-monitoring trial, Basch JAMA 2017 / JCO 2022, showed median 5-month overall-survival benefit with wearable-adjacent monitoring); mucositis (weight loss + reduced oral intake); chemo-induced diarrhoea (electrolyte derangement, dehydration, tachycardia); chemo-induced nausea (activity drop, sleep fragmentation). The Bios condition-pattern engine's multi-signal convergence ([infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) is the canonical example: RHR + HRV + skin-temp + RR + sleep + activity, `minActiveSignals = 3`) is *exactly* the shape STAR-arm monitoring needed. There is no oncology-specific pattern that fires on the neutropenic-fever convergence, and there is no `is_on_chemotherapy` physiology state that would tighten thresholds during the cycle-day 7–14 nadir window.
5. **Immune-related adverse events (irAEs) are unrecognised.** Pneumonitis (1–10 % with PD-1/PD-L1 monotherapy, higher with combinations; RR + SpO2 trajectory), colitis (diarrhoea + electrolyte + weight loss), hepatitis (ALT/AST/GGT — Bios has these in [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) under BIOMARKER), thyroiditis (TSH shift, often biphasic hyperthyroid → hypothyroid — Bios already has the [hyperthyroidSignature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) and [hypothyroidSignature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) patterns), myocarditis (HR + troponin — see Gap #11), hypophysitis (cortisol — Bios has [CORTISOL](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) as a BIOMARKER), adrenalitis. The ASCO 2021 / ESMO 2022 / NCCN Management of Immunotherapy-Related Toxicities guidelines describe a multi-organ surveillance pattern that is research-active in wearable form (Cohen 2024 — PD-1 toxicity from wearable signals). Bios has substantially more of the substrate than it realises.
6. **No surveillance-after-treatment cadence.** NCCN follow-up schedules per cancer type are highly specific (e.g., breast cancer NCCN v4.2025: H&P + mammography annually, no routine bloodwork or imaging in asymptomatic; colorectal NCCN v3.2025: CEA q3–6 months × 2 y then q6 months × 3 y, CT chest/abdomen/pelvis annually × 5 y, surveillance colonoscopy at 1 y then per findings; lung NCCN v6.2025: CT chest q6 months × 2 y then annually × 3 y). Tumour markers — CEA (CRC), CA-125 (ovarian, with the explicit ASCO caveat that asymptomatic CA-125 surveillance does not improve overall survival per Rustin MRC OV05 2010), CA 19-9 (pancreatic, hepatobiliary), PSA (prostate), HCG + AFP (germ-cell), CA 15-3 / CA 27-29 (breast surveillance — also not OS-improving in asymptomatic) — are exactly the kind of slow-rolling labs the [BIOMARKER](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) domain was built for. None are wired.
7. **No survivorship surface.** Five-year, ten-year, lifetime late-effects surveillance is a structurally distinct phase from active treatment. Childhood-cancer survivors carry markedly elevated risk of second malignancies, anthracycline-related cardiomyopathy, premature ovarian insufficiency, neurocognitive sequelae, and metabolic syndrome (Children's Oncology Group Long-Term Follow-Up Guidelines v6.0, 2023). Adult survivors carry elevated cardiovascular, second-cancer, and psychosocial risk decades out (ASCO Survivorship Care Planning, IOM 2006 "From Cancer Patient to Cancer Survivor"). Bios has no notion of "this owner is N years post-curative-intent treatment" and no cadence overlay for the survivorship-specific late-effects monitoring that NCCN and COG codify.
8. **Cancer-related fatigue is one of the most prevalent and under-measured symptoms in oncology, and Bios is structurally well-positioned to measure it.** FACT-F / EORTC QLQ-C30 / Brief Fatigue Inventory are patient-reported; wearable activity + sleep + HRV is the strongest *objective* correlate (Bower 2014 — NCCN-cited; Berger NCCN Cancer-Related Fatigue v2.2025). CTCAE v5 grades fatigue 1–4. Bios already ingests [STEPS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [ACTIVE_MINUTES](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), sleep duration and efficiency, and HRV — every input needed. No fatigue-trajectory surface exists.
9. **Cachexia and sarcopenia surveillance is structurally trivial and not done.** Cancer cachexia is a multifactorial syndrome of involuntary weight loss with muscle wasting; ≥5 % weight loss over 6 months or BMI <20 with any weight loss meets the Fearon 2011 consensus criteria. It is prognostic (Martin 2015 — cachexia predicts shortened survival across tumour sites) and partially treatable (megestrol, anamorelin — Temel 2016 ROMANA trials; nutritional support; resistance exercise). Bios has [BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) and [LEAN_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) (Withings ingest), plus activity. A cachexia-trajectory pattern is one signal rule away.
10. **No cancer-pain trajectory surface.** Pain is the most prevalent symptom in advanced cancer (40–60 % overall, 60–90 % in advanced disease per ESMO 2018). Opioid posology, breakthrough pain, neuropathic pain (chemo-induced peripheral neuropathy, post-radiation plexopathy), and the WHO three-step analgesic ladder all rely on serial pain assessment. Bios has [PAIN_SCORE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) (0–10 NRS) under NEUROLOGICAL — present, manual-entry-enabled, but no oncology-specific trajectory pattern that distinguishes baseline cancer pain from breakthrough or rapidly escalating pain.
11. **Troponin and NT-proBNP are absent from the biomarker panel.** Both are first-line cardio-oncology surveillance labs (ESC 2022 Cardio-Oncology Guideline §6.2: baseline + serial troponin during anthracycline / trastuzumab / ICI therapy; NT-proBNP for HF risk stratification). Bios's biomarker panel ([MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) BIOMARKER domain) covers HbA1c, hsCRP, lipid panel, ApoB, vitamin D, TSH/T4/T3, CBC, eGFR, creatinine, ALT/AST/GGT, HOMA-IR, ferritin, B12, folate, magnesium, cortisol, sex hormones, IGF-1, epigenetic clocks — but no cardiac troponin (hs-cTnI, hs-cTnT) and no NT-proBNP. Cross-references the cardiology audit's Gap #4 (HF decompensation).
12. **No radiation-therapy toxicity trajectory tracking.** A 5–7-week radiation course produces predictable acute toxicities by anatomic site: head-and-neck (mucositis, dysphagia, xerostomia, weight loss — frequently requiring feeding-tube placement at week 3–5); thoracic (oesophagitis at week 2–3, radiation pneumonitis at weeks 6–12 post-completion with RR + SpO2 trajectory); pelvic (cystitis, proctitis, diarrhoea); breast (dermatitis CTCAE grade 1–3); CNS (fatigue, cerebral oedema). CTCAE v5 grading is the canonical scale. Bios has the substrate (weight, SpO2, RR, pain, fatigue proxies) and no anatomic-site-aware overlay.
13. **No cancer-prevention framing for the lifestyle substrate already present.** IARC Monograph evidence: tobacco (Group 1, 16+ cancer sites — lung, oral, oesophageal, gastric, hepatocellular, pancreatic, renal, bladder, cervical, AML), alcohol (Group 1, 7+ sites — oral, oesophageal, hepatocellular, breast, colorectal, laryngeal), processed meat (Group 1, colorectal), red meat (Group 2A), obesity (IARC Working Group 2016: 13+ cancers — oesophageal adenocarcinoma, gastric cardia, colorectal, hepatocellular, gallbladder, pancreatic, post-menopausal breast, endometrial, ovarian, renal, meningioma, thyroid, multiple myeloma), UV radiation (Group 1, melanoma + NMSC), HPV (Group 1, cervical + oropharyngeal + anogenital), HBV/HCV (Group 1, hepatocellular). Bios already has the [Smokeless companion](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt) (tobacco events), [ALCOHOL_INTAKE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), and physical-activity metrics, plus phone GPS that could (with owner consent) integrate UV-index data. None of these surfaces is framed in cancer-prevention terms. The smoking-cessation `cessation_recovery_pattern` flags cardiovascular recovery, never cancer-risk decline (which is the larger and slower benefit — relative lung-cancer risk halves at 10 y post-cessation per US Surgeon General 2014).
14. **HPV and HBV vaccination status are absent, which directly determines downstream cancer screening cadence.** Cross-references the primary-care audit's Gap #3 (immunisation domain). HPV vaccination shifts cervical-screening recommendations (ASCCP / WHO 2020 cervical-cancer-elimination strategy), and a fully HPV-vaccinated cohort approaching screening age is now reaching the population where the cadence question is live. HBV vaccination shifts hepatocellular surveillance intensity. Bios has no immunisation domain.
15. **Cancer-related venous thromboembolism is unaddressed.** Trousseau syndrome (migratory superficial thrombophlebitis as a paraneoplastic phenomenon); Khorana score for chemotherapy-associated VTE risk stratification; pancreatic, gastric, lung, and brain tumours carry 4–7× general-population VTE risk; NCCN Cancer-Associated VTE Disease v3.2025 recommends primary prophylaxis in selected ambulatory chemotherapy patients. Wearable VTE detection is research-stage (HR + SpO2 + activity asymmetry — Klok 2021 review) but the convergence shape fits Bios's engine.
16. **End-of-life data autonomy is unaddressed, and the manifesto is the most relevant document Bios has on this.** When an owner dies, what happens to their health data is a question the cancer-care population will face with above-average frequency. Bios's reproductive-database isolation precedent (separate SQLCipher key, independent wipe), its burner mode and dead-man's-switch integration via LETHE, and its "Erasure by design" principle ([ROADMAP.md → Non-negotiable principles](../ROADMAP.md)) are all structurally appropriate for end-of-life data autonomy. There is currently no posthumous-data-disposition surface, no advance-directive integration, and no oncology-aware end-of-life data-handoff workflow. Cross-references the Ob/Gyn and African-traditional audits' threat-model framing.
17. **Clinical-trial integration is unaddressed despite cancer being the most trial-intensive specialty.** ~5 % of US adult cancer patients enrol on a therapeutic clinical trial; the percentage is markedly higher in paediatrics (~50 % for ALL). Decentralised clinical trials are the active research frontier (FDA Draft Guidance on Decentralised Clinical Trials, May 2023; ASCO/Friends DCT guidance 2022). Bios's privacy posture (on-device computation, owner-controlled FHIR export, owner-controlled sharing) is structurally favourable for owner-controlled trial participation in a way most consumer health products are not. Cross-references the FHIR-export surface already shipped.
18. **Paediatric oncology is structurally out of scope under the current adult-baseline physiology assumptions.** Childhood ALL, neuroblastoma, CNS tumours, Wilms', osteosarcoma, Ewing sarcoma, hepatoblastoma, retinoblastoma — the paediatric haem-onc population has age-banded vital ranges (heart rate 100–160 bpm in infants, dropping with age), age-banded chemotherapy toxicity profiles, and a survivorship horizon measured in decades. The primary-care audit's Gap #7 (demographic gating: pregnancy, paediatrics, frailty, athletes) is partly closed via [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) but the paediatric_band_x states need fuller threshold overlays before Bios can be a credible paediatric-survivor instrument. Cross-references the paediatrics audit.

The remaining items in this audit are observations on lower-impact gaps, manifesto-tension points (especially around end-of-life and hospice), and on strengths worth preserving as oncology scope grows.

---

## 1. What Bios already does well, viewed from the oncology bench

| Quality | Evidence | Why an oncologist cares |
|---|---|---|
| **The "instrument, not coach" posture is structurally correct for cancer care, and most starkly so at end of life** | [MANIFESTO.md](../../MANIFESTO.md) Principle 7 ("Bios is the instrument; the owner reads it"); [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) bans "you should / streak / daily goal / level up" with CI enforcement | Productivity-coach wellness apps are tone-deaf in oncology and actively distressing in hospice. The contrast is most visible during end-of-life care: a Fitbit suggesting "you missed your step goal" on a dying patient is the canonical case for why Bios's posture is the right one. NCCN palliative-care guidelines explicitly recommend against intervention frameworks that frame the patient as failing to perform health behaviours. |
| **Personal baseline is the unit of comparison for trend patterns** | [SignalRule.isAbsolute](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) splits trend-relative from absolute-cutoff evaluation; [BaselineEngine](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt) computes 14-day rolling baselines per metric | A neutropenic patient on cycle-day 10 has a different "normal" than the same patient pre-treatment. A frail elderly patient on palliative chemotherapy has a different baseline than an athlete. A childhood cancer survivor with anthracycline cardiomyopathy has a different RHR baseline than the population. Bios captures these distinctions natively, where most consumer wearables apply population thresholds and false-fire constantly in the oncology population. |
| **Multi-signal convergence is the right shape for chemotherapy-toxicity surveillance** | [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) requires `minActiveSignals = 3` across RHR + HRV + skin-temp + RR + sleep + activity, citing Mishra 2020 and Quer 2021 | This is *exactly* the shape the STAR trial (Basch JAMA 2017; JCO 2022) needed and didn't have. Median 5-month OS benefit from wearable-adjacent symptom monitoring. The infrastructure for neutropenic-fever convergence is already built; the gap is the oncology-specific pattern card, not the engine. |
| **Biomarker import via FHIR is shipped, with band-classified clinical interpretation** | [FhirImporter](../../android/app/src/main/java/com/bios/app/export/FhirImporter.kt) per the [ROADMAP.md Phase 8.6](../ROADMAP.md); [universalBiomarkerBands](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) classifies hsCRP, HbA1c, full lipid panel, vit D, thyroid panel, CBC, eGFR, creatinine, ALT/AST/GGT, HOMA-IR | Cancer surveillance is fundamentally a serial-lab problem. An oncology owner can already import CBC (myelosuppression tracking), comprehensive metabolic panel (renal/hepatic toxicity), and CRP (inflammation/recurrence proxy). Adding tumour markers and troponin is incremental on this surface, not foundational. |
| **CBC panel is fully wired with anemia pattern recognition** | [anemiaSignature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) — Hb <12 + (Hct <36 OR RBC <4.0 OR sustained RHR↑ OR active-minutes↓), WHO 2011 + Williams Hematology | Chemo-induced anemia, treatment-related myelosuppression, and cancer-of-unknown-primary workup all start with CBC. The existing anemia pattern is the right shape; adding a neutropenia pattern (ANC <500 or <1000 with [WBC](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) corroborator) and a thrombocytopenia pattern (Plt <100, [PLATELETS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt)) is incremental. |
| **Risk profile already carries first-degree cancer history for breast/ovarian, colorectal, and melanoma** | [RiskProfile.kt](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt) — `firstDegreeBreastOvarianCancer`, `firstDegreeColorectalCancer`, `firstDegreeMelanoma`, plus `personalTobaccoPackYears` and `personalCancerHistory` (free text) | Three of the four highest-yield family-history inputs for screening-cadence modification are already captured. The lung-cancer-screening pack-years input is present. The substrate to drive a cadence engine is mostly there; the engine itself is not. |
| **Region-aware regulatory disclaimer and clinical-threshold infrastructure** | [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) carries six region configs (US, GB, EU, CA, AU, JP) with region-anchored hypertension cutoffs, glucose thresholds, regulatory-body alert disclaimers | USPSTF / NHS / EU / Health Canada / TGA / Japan-MHLW screening-cadence guidelines diverge meaningfully (USPSTF: biennial mammography 40–74; NHS: triennial 50–70 transitioning to 47–73; EU: variable per country; Japan: biennial 40+). The region config layer is the right place for region-specific cadence rules. |
| **Reproductive database isolation is a useful precedent for end-of-life data autonomy** | [ReproductiveDatabase](../../android/app/src/main/java/com/bios/app/data/) — separate SQLCipher key, independent wipe, FHIR exporter skips WOMENS_HEALTH by default, priority destruction on duress PIN | The post-Dobbs threat model and the end-of-life threat model are structurally similar: both involve sensitive data that the owner may want to render inaccessible to specific future readers. Bios has built the precedent infrastructure; an analogous end-of-life-data-disposition surface is conceivable within the same architecture. |
| **The "treat the not-yet-ill" posture is structurally the right mission for cancer prevention** | [MANIFESTO.md](../../MANIFESTO.md) — "Modern medicine is reactive. We wait until something breaks, then scramble to fix it" | Cancer prevention is fundamentally an instrument-the-owner-reads-before-symptoms problem. Once a cancer is symptomatic, screening has already failed; the population-level wins are upstream. Bios's posture is structurally aligned with where the largest oncology population-impact lever is. |

These are not parity wins — these are areas where Bios is **meaningfully ahead** of the consumer-wearable category as an oncologist reads it, and where the structural foundation for oncology-specific surfaces is already laid.

---

## 2. Oncology-specific gaps, ordered by population-level impact

### 2.1 No screening cadence engine — the single highest-leverage population-level oncology gap

This is the primary-care audit's Gap #2 reframed through the oncology lens, and intensified by the population-level impact arithmetic. Cancer screening accounts for the single largest fraction of oncology-attributable life-years saved at the population level — Welch & Black NEJM 2010 review estimates that organised screening programs (mammography, cervical, colorectal) account for a meaningful fraction of the 27 % cancer-mortality decline in the US between 1991 and 2020 (Siegel CA 2024).

Concretely, Bios has no answer to the question an oncology-aware primary-care physician asks at every annual visit:

- "Is this 47-year-old woman due for her first mammogram (USPSTF 2024: yes, biennial 40–74) or her next one if she's been screened?"
- "Is this 50-year-old male overdue for colonoscopy (USPSTF 2021: 45–75) or FIT?"
- "Has this 32-year-old woman with `firstDegreeBreastOvarianCancer = true` been referred for BRCA1/2 testing and is she in the enhanced-surveillance protocol (annual MRI + mammography from age 25–30, NCCN Genetic/Familial Breast/Ovarian v2.2025)?"
- "Is this 56-year-old former smoker with `personalTobaccoPackYears = 30`, `personalTobaccoQuitDate` 8 years ago eligible for annual low-dose lung CT (USPSTF 2021: 50–80, ≥20 pack-years, quit <15 y)?"
- "Has this 65-year-old man had a PSA conversation in the last 2 years (USPSTF 2018 grade-C shared decision, 55–69)?"
- "Cervical screening — is HPV co-test current per ASCCP 2020 (age 21–29 cytology q3y; 30–65 HPV primary q5y or co-test q5y)?"
- "DEXA cadence for this 67-year-old woman (USPSTF 2018: at least once at 65; repeat per FRAX risk)?"

These are exactly the surfaces that elevate primary oncology prevention from reactive ("I felt a lump") to scheduled ("I'm due for this; let me book it"). They are also a near-perfect fit for the Bios manifesto: **pull-side**, owner-controlled, never pushed unsolicited, and entirely localisation-driven (USPSTF for US, NHS for GB, KCDC for KR, Tokutei Kenshin for JP).

**Recommendation:** a `ScreeningSchedule` surface, owner-navigated, on the Settings → Privacy-Tier side that:

1. Owner inputs (or imports from FHIR `Procedure` / `ImmunizationRecommendation`) what screenings they have already had and when. Free-form date entries acceptable; FHIR Procedure imports preferred where the source is an EHR.
2. The [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) layer carries the recommended cadence per jurisdiction's guideline (USPSTF / NHS / KCDC / Tokutei Kenshin / EU country-specific).
3. The cadence renderer reads [RiskProfile](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt) — `firstDegreeBreastOvarianCancer`, `firstDegreeColorectalCancer`, `firstDegreeMelanoma`, `personalTobaccoPackYears`, `personalTobaccoQuitDate`, `personalCancerHistory` — and *modifies* the cadence per the relevant NCCN guideline (e.g., first-degree CRC at <50 y shifts CRC screening start from 45 → 40, or 10 y before the youngest affected relative, per NCCN Colorectal v3.2025; first-degree breast cancer at <50 shifts mammography start from 40 → 30 or 10 y before the youngest affected relative, per NCCN Breast Screening and Diagnosis v3.2025).
4. The pull-side surface renders "your last mammogram was 26 months ago — the [USPSTF / NHS / KCDC] cadence for someone with your family history is 12 months. Discuss with your provider."
5. Nothing is pushed. Silence is still a feature; this is owner-initiated.

This is roadmap-scale work, not a one-day change. It is also the single most defensible oncology-relevant feature Bios could ship, and it operationalises the manifesto's "treat the not-yet-ill" posture in the population where that posture has the largest demonstrable impact.

### 2.2 Hereditary cancer syndromes need a richer RiskContext

[RiskProfile](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt) currently captures *first-degree-relative* booleans, which is the right starting point but the wrong stopping point for hereditary cancer syndromes. The clinical reality: a patient who *is* a BRCA1 carrier (not merely a first-degree relative of one) has lifetime breast-cancer risk 55–72 % (Kuchenbaecker JAMA 2017) and lifetime ovarian-cancer risk 39–44 %; their surveillance cadence is annual breast MRI + mammography from age 25–30, plus risk-reducing salpingo-oophorectomy by age 35–40 (NCCN Genetic/Familial Breast/Ovarian/Pancreatic v2.2025). This is *qualitatively* different from a first-degree-relative cadence shift.

The syndromes that matter for cadence-modification logic:

| Syndrome | Gene(s) | Cancer-risk modification | Surveillance cadence shift |
|---|---|---|---|
| **HBOC** | BRCA1, BRCA2, PALB2, ATM, CHEK2 | Breast 55–72 %, ovarian 39–44 % (BRCA1); breast 45–69 %, ovarian 11–18 % (BRCA2) | Annual MRI + mammography from 25–30; RRSO by 35–40 (BRCA1) or 40–45 (BRCA2) |
| **Lynch** | MLH1, MSH2, MSH6, PMS2, EPCAM | CRC 15–80 %, endometrial 15–60 %, ovarian, gastric, urinary tract, biliary, brain (Lynch is the most heterogeneous-risk syndrome) | Colonoscopy annually from 20–25 (MLH1/MSH2) or 30–35 (MSH6/PMS2); endometrial sampling discussion; upper endoscopy q3–5y |
| **Li-Fraumeni** | TP53 | Sarcoma, breast, brain, adrenal, leukaemia; ~50 % cumulative cancer risk by 30 in females, lifetime ~70 % | "Toronto protocol": annual whole-body MRI + brain MRI + breast MRI from age 20 (women) |
| **FAP** | APC | CRC ~100 % by age 40 untreated; duodenal, desmoid, thyroid | Sigmoidoscopy/colonoscopy annually from 10–15; prophylactic colectomy at adenoma burden threshold |
| **Cowden** | PTEN | Breast, thyroid, endometrial, renal | Annual breast MRI + mammography from 30–35; thyroid US annually from diagnosis |
| **Peutz-Jeghers** | STK11 | GI, breast, gynaecologic, pancreatic | Annual breast MRI + mammography from 30; capsule endoscopy q2–3y; MRCP for pancreatic |
| **VHL** | VHL | RCC, haemangioblastoma, pheochromocytoma, pancreatic | Annual abdominal MRI; annual eye exam; biennial brain MRI from age 11 |
| **MEN1/MEN2** | MEN1; RET | Parathyroid, pituitary, pancreatic NET (MEN1); MTC, pheo (MEN2) | Prophylactic thyroidectomy in MEN2A by age 5; annual biochemical screening |
| **HDGC** | CDH1 | Diffuse gastric, lobular breast | Prophylactic gastrectomy discussion at 20–30; annual breast MRI |

Recommendation: extend [RiskProfile](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt) with an optional `hereditarySyndrome` field (enum + free-text rationale) and an optional `geneticTestingDate`. The screening-cadence engine in §2.1 reads it. No alert is ever pushed — the surface is pull-side. The carrier population is small in absolute terms but is the population where missed cadence prompts cause the largest harm.

### 2.3 Treatment-related cardiotoxicity surveillance

The ESC 2022 Cardio-Oncology Guideline (Lyon et al.) and the ASCO 2017 cardio-oncology guideline (Armenian) define a structured surveillance cadence for cardiotoxic agents:

- **Anthracyclines (doxorubicin, epirubicin, daunorubicin, idarubicin)** — cumulative-dose-dependent dilated cardiomyopathy. Baseline LVEF + GLS + troponin; serial troponin during treatment; LVEF + GLS at cumulative dose 250 mg/m² doxorubicin-equivalent, then at completion, then at 6 and 12 months. Pediatric long-term cardiomyopathy risk extends decades (COG LTFU Guidelines v6).
- **Trastuzumab and other HER2-targeted (pertuzumab, T-DM1, T-DXd)** — reversible LVEF decline 7–28 %. LVEF q3 months during treatment.
- **Tyrosine kinase inhibitors (sunitinib, sorafenib, axitinib, lenvatinib, regorafenib)** — hypertension (extremely common; class-effect), LVEF decline, QT prolongation. BP weekly initially.
- **Immune checkpoint inhibitors (PD-1/PD-L1/CTLA-4)** — rare but high-mortality fulminant myocarditis (0.3–1.1 %); typically within 6 weeks of initiation; mortality 27–46 % when fulminant. ECG + troponin at baseline; troponin during cycles 1–3; high index of suspicion for any new cardiac symptom.
- **5-FU / capecitabine** — coronary vasospasm.
- **Cisplatin / oxaliplatin** — accelerated atherosclerosis.
- **CDK4/6 inhibitors (palbociclib, ribociclib)** — QT prolongation (ribociclib particularly).
- **Bruton tyrosine kinase inhibitors (ibrutinib)** — atrial fibrillation (incidence ~10 %, much higher than population).
- **CAR-T cell therapy** — cytokine release syndrome with tachycardia, hypotension.

Bios's existing wearable substrate (RHR, HRV, SpO2, BP, weight) directly supports the cardio-oncology surveillance window. The cardiology audit's [Gap #4 (HF decompensation)](CARDIOLOGY_POV.md) identifies the same multi-signal trajectory; an oncology adaptation would:

1. Add a `cardiotoxicity_surveillance` physiology state (analogous to the existing [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) entries) that the owner enables when on cardiotoxic therapy, with the agent class as a sidecar field.
2. Adjust the trend-pattern thresholds during that state — a 1.5σ RHR rise on cycle day 7 of doxorubicin is a different event from a 1.5σ rise in a healthy 30-year-old.
3. Add troponin and NT-proBNP to the [BIOMARKER](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) panel (Gap #11) and wire them into the surveillance pattern.

The reach is real: anthracycline-based chemotherapy is given to a substantial fraction of patients with curative-intent breast cancer, lymphoma, sarcoma, and acute leukaemia; ICI therapy now spans melanoma, NSCLC, RCC, urothelial, HCC, oesophageal/gastric, MSI-H tumours of any site, and triple-negative breast cancer. The population on cardiotoxic regimens at any moment in the US is in the low millions.

### 2.4 Chemotherapy toxicity convergence — the STAR-trial-shaped opportunity

Basch et al. JAMA 2017 randomised 766 patients with advanced solid tumours to electronic PRO symptom monitoring vs. usual care; the monitoring arm showed 31 % more chemo at completion, 7 % more 1-year quality-adjusted survival, and a *5-month median overall-survival benefit* (Basch JAMA Oncol 2022 — long-term follow-up). The result has been replicated (Denis JAMA 2019 in lung cancer). The mechanism is straightforward: catch toxicity early enough to dose-adjust or supportive-care-intervene before the patient ends up in the ED or hospitalised.

The STAR symptom set was patient-reported (12 symptoms; weekly). Bios's structural opportunity is to add the *objective* layer: the wearable signals that correlate with the symptoms STAR caught, plus the biomarker import surface for the labs that anchor the response. The CTCAE v5 grading anchors the symptom-to-severity mapping:

| Toxicity | CTCAE-relevant signals Bios can converge | Owner action (manifesto-aligned) |
|---|---|---|
| **Febrile neutropenia** (CTCAE: ANC <1000 + temp ≥38.3 °C single or ≥38.0 °C sustained ≥1 h) | Skin-temp + RHR + WBC + HRV; absolute SpO2 if pneumonia developing | URGENT-tier oncology emergency — instruct to call oncology nurse line / ED immediately |
| **Chemo-induced diarrhoea** (CTCAE grade 3: ≥7 stools/d above baseline, IV fluids ≥24 h) | Owner-annotated stool frequency + weight loss + tachycardia (volume depletion) + active-minutes drop | Hydration, loperamide, call oncology if grade 3+ |
| **Mucositis** (CTCAE grade 3: severe pain interfering with oral intake) | Weight loss + reduced active-minutes + sleep disruption from pain | Saline rinses, magic mouthwash, call oncology for grade 3+ — feeding-tube discussion |
| **Nausea/vomiting** (CTCAE grade 3: hospitalisation indicated) | Weight loss + activity drop + sleep fragmentation + tachycardia | 5-HT3 + NK1 + dexamethasone per MASCC/ESMO 2023 antiemetic guidelines |
| **Hand-foot syndrome** (capecitabine, doxil) | Owner-annotated; activity drop from foot involvement | Dose reduction, urea cream |

**Recommendation:** an `on_active_chemotherapy` physiology state that tightens convergence thresholds during the cycle-day 7–14 nadir window, and an oncology-specific `chemotherapy_toxicity_convergence` pattern card that synthesises the existing infection-onset substrate with weight loss + GI-toxicity self-reports. This is one of the highest-evidence-base wearable-monitoring use cases in all of medicine; the literature is essentially asking for what Bios has built.

### 2.5 Immune-related adverse events (irAEs)

PD-1, PD-L1, and CTLA-4 inhibitor toxicity is a multi-organ surveillance problem. The ASCO 2021 / ESMO 2022 / NCCN Management of Immunotherapy-Related Toxicities (v1.2025) frame it as: any new symptom is irAE until proven otherwise, with onset variable from days to >1 year after initiation.

| irAE | Frequency (monotherapy) | Wearable / biomarker substrate present in Bios |
|---|---|---|
| Dermatitis | 30–40 % | Owner-annotation; not patternable from sensors |
| Colitis | 8–22 % | Diarrhoea self-report + weight loss + electrolyte derangement |
| Hepatitis | 5–10 % | [ALT](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [AST](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [GGT](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) — patternable |
| Pneumonitis | 1–10 % | RR + SpO2 trajectory — patternable, cross-references [SleepApneaPattern](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt) substrate |
| Thyroiditis | 5–10 % | [TSH](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [FREE_T4](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [FREE_T3](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) — already patternable via [hyperthyroidSignature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) and [hypothyroidSignature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt); biphasic course needs different pattern |
| Hypophysitis | 1–6 % | [CORTISOL](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) — patternable |
| Myocarditis | 0.3–1.1 % (fulminant; mortality 27–46 %) | RHR + (troponin — see Gap #11) |
| Nephritis | 1–4 % | [CREATININE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [EGFR](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) — patternable |
| Type 1 diabetes (de novo) | <1 % but fulminant | [BLOOD_GLUCOSE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [GLUCOSE_CV](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) — patternable |

Bios has the substrate for 6 of 9 patternable irAEs already. The missing piece is the *oncology context* — Bios doesn't know the owner is on ICI therapy and therefore cannot weight the relevant patterns higher during the irAE-vulnerable window. The same `on_active_immunotherapy` physiology state from Gap #4 would close this.

### 2.6 CAR-T cell therapy

Out of immediate scope during the inpatient CRS-monitoring phase (which is ICU-grade continuous monitoring), but **post-discharge surveillance is Bios-territory**. CAR-T patients are typically monitored for delayed neurotoxicity (ICANS) for ≥4 weeks post-infusion; longer-term concerns include prolonged cytopenias, hypogammaglobulinaemia, infection risk, and (lisocabtagene/axicabtagene/tisagenlecleucel-specific) cytokine-related late effects. The post-discharge surveillance window is structurally the same shape as the chemotherapy-nadir window in Gap #4.

### 2.7 Cancer-related fatigue

CRF affects 80–90 % of patients during active cancer treatment and 25–30 % of survivors years out (NCCN Cancer-Related Fatigue v2.2025). FACT-F, EORTC QLQ-C30 fatigue subscale, and Brief Fatigue Inventory are patient-reported standards. Wearable activity + sleep + HRV is the strongest *objective* correlate (Bower 2014 — meta-analysis, NCCN-cited; Heckler 2016).

Bios already ingests every input needed:

- [STEPS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) (daily activity)
- [ACTIVE_MINUTES](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) (moderate-to-vigorous physical activity)
- [SLEEP_DURATION](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) and [SLEEP_EFFICIENCY](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt)
- [HEART_RATE_VARIABILITY](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) (autonomic tone)
- [PAIN_SCORE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) (pain-fatigue coupling)
- [MOOD_DRIFT_SCORE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) (W2F companion — depression-fatigue coupling)

The existing [recoveryDeficit](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) pattern is structurally close to what an oncology-aware CRF trajectory needs, but uses an athlete-recovery framing in its explanation text rather than a CRF framing. A dedicated `cancer_related_fatigue_trajectory` pattern with CTCAE-anchored grading thresholds, gated to the `on_active_chemotherapy` or `survivorship_window` physiology state, would close this.

### 2.8 Cachexia / sarcopenia trajectory

Fearon 2011 consensus criteria: ≥5 % weight loss over 6 months, OR BMI <20 kg/m² with any weight loss, OR sarcopenia (appendicular skeletal muscle index <7.26 kg/m² men or <5.45 women) with >2 % weight loss. Cachexia is present in ~50 % of advanced cancer patients and is the direct cause of death in ~20 % (Argilés 2014). It is prognostic across tumour sites (Martin Cancer 2015) and partially treatable: megestrol acetate, anamorelin (ROMANA-1/2 — Temel Lancet Oncol 2016), nutritional support, resistance exercise (Solheim 2017 MENAC trial).

Bios has [BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) and [LEAN_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) via Withings BIA, plus activity. A `cachexia_trajectory` pattern:

```
BODY_MASS    BELOW (over 180-day window, ≥5% decline)   required
LEAN_MASS    BELOW                                       supporting
ACTIVE_MINUTES BELOW                                     supporting
minActiveSignals = 2
```

…is one signal-rule entry away from existing. The supplementary [GRIP_STRENGTH] metric (sarcopenia gold standard; not currently in [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt)) would strengthen the pattern but is not a precondition.

### 2.9 Cancer pain trajectory

Pain is the most prevalent symptom in advanced cancer (40–60 % overall, 60–90 % advanced disease per ESMO 2018 cancer-pain clinical practice guideline). Bios has [PAIN_SCORE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) (0–10 NRS, manual-entry-enabled, NEUROLOGICAL domain) — present but with no oncology-specific trajectory pattern.

What an oncology pain trajectory would surface:

1. Baseline pain level for the owner (already implicit via personal baseline).
2. Breakthrough pain frequency (BTP — Davies 2009 definition: transient exacerbation despite controlled background pain). Currently a [BTP_EVENT] metric does not exist.
3. Escalation trajectory — pain rising over 7-day window above personal baseline.
4. Opioid-context overlay: if owner has annotated opioid medication in [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt), pain escalation is more concerning (suggests tolerance, disease progression, or under-treated pain) and the explanation text can say so.

Manifesto-aligned: pain-trajectory pattern is the kind of thing the owner takes to their palliative-care team. It is not a diagnosis. It is the instrument the patient and oncologist read together.

### 2.10 Radiation therapy toxicity trajectory

A 5–7 week radiation course produces predictable site-specific acute toxicities. Currently no Bios pattern recognises a radiation course at all. The substrate is mostly present:

| Anatomic site | Acute toxicity | Bios signals available | Pattern shape |
|---|---|---|---|
| Head & neck | Mucositis, dysphagia, xerostomia, weight loss → feeding tube | Weight loss + pain + reduced active-minutes | Trajectory over 35–49 days; weight loss most actionable |
| Thoracic | Oesophagitis (wk 2–3), pneumonitis (wk 6–12 post-completion) | Weight loss + pain (oesophagitis) + RR + SpO2 (pneumonitis) | Site-specific overlay |
| Pelvic | Cystitis, proctitis, diarrhoea | Owner-annotated GI; tachycardia (volume depletion) | Owner-self-report dominant |
| Breast | Dermatitis CTCAE grade 1–3 | Owner-annotated; not patternable from sensors | Owner-tracked photo journal (out of scope) |
| CNS | Fatigue, cerebral oedema | Activity drop + sleep disruption + cognitive (W2F PVT) | Adjacent to [recoveryDeficit](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) |

An `on_radiation_therapy` physiology state with an anatomic-site sidecar, plus a site-aware toxicity-trajectory pattern, is the right shape. The text would be CTCAE-anchored.

### 2.11 Tumour markers and cardio-oncology biomarkers

The [BIOMARKER](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) domain is the right home for tumour-marker import. The clinically meaningful set:

| Marker | Cancer | LOINC | Surveillance role |
|---|---|---|---|
| CEA | Colorectal (also: gastric, pancreatic, lung adenocarcinoma) | 2039-6 | NCCN CRC v3.2025: q3–6 mo × 2 y, then q6 mo × 3 y post-curative resection |
| CA-125 | Ovarian, peritoneal | 10334-1 | NCCN Ovarian v2.2025: every visit during surveillance — *with* the ASCO caveat (Rustin Lancet 2010 MRC OV05: surveillance does not improve OS in asymptomatic) |
| CA 19-9 | Pancreatic, biliary | 24108-3 | NCCN Pancreatic Adenocarcinoma v2.2025: q3 mo × 2 y, then q6 mo × 3 y |
| PSA | Prostate | 2857-1 | NCCN Prostate Cancer v3.2025: q6–12 mo post-RT, more frequent post-RP |
| HCG (β-hCG) | Germ-cell (testicular, gestational trophoblastic) | 19080-1 | NCCN Testicular v2.2025: monthly during surveillance × 1 y |
| AFP | Hepatocellular, germ-cell | 1834-1 | Same as HCG for germ-cell; q6 mo HCC surveillance per AASLD 2023 |
| CA 15-3 / CA 27-29 | Breast | 6875-9 / 17842-6 | ASCO 2018: not recommended for routine asymptomatic surveillance; use for known metastatic |
| LDH | Lymphoma, germ-cell, melanoma (Stage IV) | 14804-9 | Prognostic; not screening |
| Calcitonin, CEA | Medullary thyroid | 28333-0 | NCCN Thyroid v3.2025: q3–6 mo post-thyroidectomy |
| Thyroglobulin | Differentiated thyroid (post-thyroidectomy) | 9714-7 | ATA 2015 surveillance |

Cardio-oncology biomarkers:

| Marker | Role | LOINC |
|---|---|---|
| **hs-cTnI / hs-cTnT (high-sensitivity troponin)** | Cardiotoxicity surveillance during anthracycline / trastuzumab / ICI; ICI-myocarditis screening | 89579-7 / 89577-1 |
| **NT-proBNP** | HF risk during cardiotoxic therapy; survivorship cardiomyopathy surveillance | 33762-6 |
| **BNP** | Alternative to NT-proBNP | 30934-4 |

Adding these is incremental on the existing [Phase 8.6 biomarker import](../ROADMAP.md) infrastructure — new MetricType entries, LOINC mapping in [FhirExporter](../../android/app/src/main/java/com/bios/app/export/FhirExporter.kt), and (for the tumour markers) the explicit ASCO/NCCN caveat that **asymptomatic-surveillance use of tumour markers does not improve overall survival** in several cancer types (Rustin Lancet 2010 for CA-125; the ASCO 2018 Breast Cancer Surveillance recommendations against CA 15-3/CA 27-29 in asymptomatic patients). Bios's manifesto register is the right one for surfacing the data without surfacing the false-survival-benefit framing.

### 2.12 Surveillance after curative-intent treatment

NCCN follow-up schedules are highly cancer-specific. A `cancer_surveillance_window` physiology state with `cancer_type` + `treatment_completion_date` sidecars would let Bios:

1. Render the NCCN-anchored surveillance cadence (CT/MRI imaging at appropriate intervals; tumour markers at appropriate intervals; H&P; site-specific surveillance like surveillance colonoscopy at 1 y then per findings).
2. Score tumour-marker readings against pre-treatment baseline and prior surveillance readings, not just against population reference ranges. (A CEA of 4 ng/mL is "normal" by reference range but concerning if the post-resection baseline was 1.2.)
3. Trigger a cadence-overdue notice on the pull side if a scheduled surveillance interval has elapsed without a recorded reading.

This sits at the intersection of the screening-cadence engine (§2.1) and the tumour-marker import (§2.11) and would be the most operationally useful single feature for survivorship-care-plan owners.

### 2.13 Survivorship and late effects

The IOM 2006 report "From Cancer Patient to Cancer Survivor: Lost in Transition" established the survivorship-care-plan framework. Late effects extend decades and differ by cancer type and treatment:

- **Childhood cancer survivors** (Children's Oncology Group LTFU Guidelines v6.0, 2023): elevated risk of second malignancies, anthracycline cardiomyopathy, radiation-induced cardiomyopathy + valvular disease + accelerated CAD (chest RT), premature ovarian insufficiency, hypothyroidism (neck RT, MIBG), neurocognitive sequelae (cranial RT, methotrexate), metabolic syndrome, infertility, secondary leukaemias (alkylators, topoisomerase-II inhibitors), pulmonary fibrosis (bleomycin, busulfan, chest RT).
- **Adult breast cancer survivors**: anthracycline + trastuzumab cardiomyopathy, aromatase-inhibitor-related bone loss + arthralgia, tamoxifen-related VTE + endometrial cancer, lymphedema.
- **Adult lymphoma survivors (Hodgkin especially)**: secondary breast cancer (chest RT), secondary leukaemia, cardiovascular disease, hypothyroidism.
- **Prostate cancer survivors**: ADT-related bone loss + metabolic syndrome + cardiovascular disease.

A survivorship-window overlay would adjust the cardiovascular, metabolic, and endocrine pattern weights to reflect the population's elevated late-effect risk, and would render the NCCN/COG-anchored late-effects surveillance cadence on the pull side.

### 2.14 Cancer-prevention framing for the lifestyle substrate

Bios has every lifestyle input needed for cancer-prevention messaging. The framing is missing.

IARC Group 1 carcinogens that Bios touches:

- **Tobacco** (16+ cancer sites) — [Smokeless companion](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt) tracks `TOBACCO_USE` and `TOBACCO_CRAVING`. The [cessation_recovery_pattern](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt) flags cardiovascular recovery. **It does not flag cancer-risk decline**, which is the larger and slower benefit (US Surgeon General 2014: lung-cancer relative risk halves at ~10 y post-cessation; oral/oesophageal at ~5–10 y).
- **Alcohol** (Group 1: oral, oesophageal, hepatocellular, breast, colorectal, laryngeal) — [ALCOHOL_INTAKE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) exists, dosed in grams ethanol. No cancer-context surface.
- **Obesity** (IARC Working Group 2016: 13+ cancers) — [BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) tracked. No cancer-context surface.
- **Physical inactivity** (IARC: colorectal, breast, endometrial) — [STEPS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [ACTIVE_MINUTES](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt). No cancer-context surface.
- **UV** (Group 1: melanoma, NMSC) — phone GPS + public UV-index API integration is technically straightforward; no current surface.
- **HPV / HBV** (Group 1 for cervical / hepatocellular respectively) — vaccination status absent (cross-references the primary-care audit's Gap #3).

Recommendation: **a cancer-prevention pull-side surface** that, for each modifiable exposure the owner is tracking, renders the IARC evidence + the relative-risk-decline trajectory available from cessation/reduction. This is *information*, not nudging — manifesto-aligned. The framing question: "what does the literature say about cancer risk for owners with my exposure pattern?" The answer is pull-side, owner-asked, and never pushed.

### 2.15 Smoking cessation as the highest-leverage cancer-prevention behaviour

Tobacco cessation is the single most consequential modifiable cancer-prevention behaviour available. Bios's Smokeless companion already tracks events and has a cessation-recovery pattern. The extension is a cancer-specific cessation-progress surface: "your lung-cancer relative risk has dropped by approximately X % over Y years post-cessation per the Surgeon General 2014 trajectory." Pull-side; no nudging; information-only.

### 2.16 UV exposure surface

Phone GPS + public UV-index API (OpenUV, EPA UV Index) + owner-entered Fitzpatrick skin type → UV exposure trajectory + melanoma-prevention information. Pull-side. No alert pushed. The screening-cadence engine (§2.1) can then increase skin-screening cadence for high-UV-exposure owners with `firstDegreeMelanoma = true` (already in [RiskProfile.kt](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt)).

### 2.17 Alcohol consumption as cancer risk

IARC Group 1 evidence for oral, oesophageal, hepatocellular, female breast, colorectal, and laryngeal cancer is unambiguous. The dose-response is linear without threshold for breast cancer (Bagnardi BMJ 2015 meta-analysis). [ALCOHOL_INTAKE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) is present and dosed in grams ethanol — the right unit. The cancer-context surface should render the relative-risk trajectory for the owner's intake pattern. Pull-side.

### 2.18 Body weight and cancer risk

IARC Working Group 2016 established sufficient evidence for 13 cancers linked to excess body fat. [BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) is tracked. The cancer-context surface is the same shape as Gap #17.

### 2.19 Cancer-related venous thromboembolism

Pancreatic, gastric, lung, and brain tumours carry 4–7× general-population VTE risk. Khorana score for ambulatory-chemotherapy VTE risk stratification. NCCN Cancer-Associated VTE Disease v3.2025 recommends primary prophylaxis (apixaban or rivaroxaban) in selected high-Khorana-score patients. Wearable VTE detection is research-stage but the convergence shape (HR + SpO2 + leg pain self-report + activity asymmetry) fits Bios's engine. Cross-references the haematology population that overlaps with oncology.

### 2.20 Hospice and end-of-life data autonomy — the most important manifesto-relevant gap

When an owner enters hospice or dies, what happens to their health data is a question the cancer-care population faces with above-average frequency. The manifesto and Bios's existing infrastructure are *the most aligned of any consumer health product I have audited* for this question, and yet there is no explicit surface.

Relevant existing infrastructure:

- [Reproductive database isolation](../../android/app/src/main/java/com/bios/app/data/) — proves Bios can carve out a domain with separate keying and independent destruction.
- Burner mode and dead-man's-switch via LETHE integration — proves Bios can destroy data on trigger.
- "Erasure by design" — [ROADMAP.md → Non-negotiable principles](../ROADMAP.md) #5: "Every data store destroyable in <1 second via key destruction."

What is missing:

- **Advance directive integration** — owner specifies disposition of Bios data at end of life (destroy, transfer to a designated executor, share with a designated provider, leave intact for family).
- **Hospice physiology state** — analogous to the existing [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) entries. In hospice, the goal of monitoring shifts from "catch the not-yet-ill" to "comfort and symptom-burden assessment." Many trend patterns become irrelevant. Pain trajectory, dyspnoea (RR), and symptom-burden tracking (ESAS — Edmonton Symptom Assessment System) become the relevant signals. **The contrast between a productivity-coach wellness app and Bios is most stark here.** A step-goal notification on a dying patient is exactly the kind of harm the manifesto exists to prevent.
- **Posthumous data disposition surface** — once an owner is verified deceased (Virgil dead-man's-switch is the proximal mechanism on the technical side; legal verification is harder), execute the owner's pre-specified disposition.

This is roadmap-scale work and a manifesto-extension question, not a one-day change. It is also the area where Bios's existing architecture is most differentiated from the consumer-wearable category, and where the cancer-care population has the most concentrated need.

Cross-references the Ob/Gyn audit's post-Dobbs threat-model framing (sensitive-data isolation precedent) and the African-traditional audit's threat-model framing (data-sovereignty in vulnerable populations).

### 2.21 Clinical-trial integration

~5 % of US adult cancer patients enrol on a therapeutic clinical trial; paediatric enrolment is ~50 % for ALL. Decentralised clinical trials are the FDA-acknowledged future (Draft Guidance May 2023). Bios's privacy posture (on-device computation, owner-controlled FHIR export, no third-party data brokering by design) is structurally favourable for owner-controlled trial participation in a way most consumer health products are not.

The shape: an owner enrolled in a trial selects which Bios data streams to share with the trial sponsor, on what cadence, with what de-identification. The existing [FHIR exporter](../../android/app/src/main/java/com/bios/app/export/FhirExporter.kt) is the substrate; a trial-specific export profile + consent UI is the addition. This is *not* something Bios should push toward — but it is something Bios should make easy for owners who choose it. Cross-references the doctor-in-the-loop sharing surface already shipped.

### 2.22 Paediatric oncology

Childhood ALL (5-year survival now ~90 %), neuroblastoma, CNS tumours (Wilms', medulloblastoma, ATRT, DIPG), osteosarcoma, Ewing sarcoma, hepatoblastoma, retinoblastoma — the paediatric haem-onc population has age-banded vital ranges, age-banded chemotherapy toxicity profiles, and a survivorship horizon measured in decades.

The primary-care audit's [Gap #7 (demographic gating)](MEDICAL_PROFESSIONAL_POV.md) flagged paediatrics as needing dedicated `PAEDIATRIC_BAND_x` physiology states; the [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) infrastructure exists but the paediatric overlays need:

1. Age-stratified vital ranges (HR ranges per PALS / Pediatric Advanced Life Support: 100–160 bpm infants, 70–110 bpm school-age, 60–100 bpm adolescents).
2. Paediatric SpO2 thresholds (which differ from adult).
3. Paediatric chemotherapy-toxicity profiles (especially neutropenic-fever workup, which has paediatric-oncology-specific thresholds).
4. COG LTFU Guidelines v6 cadence overlays for paediatric survivors transitioning to adult care.

Cross-references the paediatrics audit (when it exists).

---

## 3. Manifesto / clinical-ethics tension points in oncology specifically

These are *not* gaps — they are places where the manifesto's principles and standard oncology practice produce genuinely different answers, and Bios should be aware of which it chose.

### 3.1 "Never evaluate the person" vs. cancer-risk communication

The manifesto's "Bios is the instrument; the owner reads it" posture is the right one for oncology — see the §1 table on why end-of-life makes the case starkest. The friction point: a 56-year-old male owner with `personalTobaccoPackYears = 35`, `personalTobaccoQuitDate = 4 years ago`, and a `firstDegreeColorectalCancer = true` is at materially elevated lifetime cancer risk; a primary-care visit would say so quantitatively. Bios will not, by design.

The screening-cadence engine (§2.1), the hereditary-syndrome RiskContext extension (§2.2), and the cancer-prevention pull-side surface (§2.14) are the *manifesto-aligned ways* to close part of this gap: pull-side, owner-asked, never pushed. The owner can ask "what does the literature say about my cancer risk?" on a screen they navigate into, and the answer can be specific and quantitative. That is the framing that respects both the manifesto and the clinical reality.

### 3.2 "Silence is a feature" vs. neutropenic fever

Silence is correct for trend-based notices. It is *incorrect* for ANC <500 with skin-temp ≥38.3 °C. The existing URGENT-tier path ([EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt)) is the right mechanism. The chemotherapy-toxicity convergence pattern (§2.4) needs to claim the same URGENT severity floor when its activation criteria are met, with text that names "neutropenic fever is an oncology emergency."

### 3.3 "Tumour markers do not improve survival in asymptomatic surveillance" vs. owner agency

The ASCO and NCCN evidence is clear: CA-125 surveillance does not improve OS in asymptomatic ovarian cancer survivors (Rustin Lancet 2010 MRC OV05); CA 15-3 / CA 27-29 are not recommended for asymptomatic breast cancer surveillance (ASCO 2018). But owners who have had cancer often *want* to see the numbers. The manifesto's posture — Bios surfaces what the owner imports, and "never withholds information you look for" — means Bios should accept the imports and render them. The manifesto-aligned addition is to surface the ASCO/NCCN context alongside the value, not to hide the value.

### 3.4 "Erasure by design" vs. survivorship records

Survivorship care plans are decades-long records. A 5-year-old patient cured of ALL has a 70-year survivorship horizon. The "Every data store destroyable in <1 second via key destruction" principle is correct as a safety property; the survivorship-care-plan use case is a place where the owner *wants* persistence. Bios's existing key management already supports owner-controlled retention; the survivorship surface should make persistence a deliberate, owner-affirmed choice with a default that respects the manifesto.

### 3.5 "Free to all" + "no Play Services" + clinical-grade cancer care

Bios's commitment to no-subscription-gating, no-Play-Services, and on-device computation is laudable and rare. The friction: cancer screening is delivered by health systems, not by apps. Bios cannot order the colonoscopy, schedule the LDCT, or refer to a genetic counsellor. It can only prompt the conversation.

The screening-cadence engine (§2.1) is the right boundary: Bios tells the owner what's recommended for their demographics and what their family-history modifiers do to the cadence; the owner takes that to whichever care system they have access to. This is the manifesto-aligned ceiling, and it operationalises the manifesto's "treat the not-yet-ill" posture in the population where that posture has the largest demonstrable population-level impact.

---

## 4. What I would recommend, prioritised

**Tier A — population-impact-defining; the case for shipping these is the cancer-mortality arithmetic**

1. **Screening-cadence engine** (§2.1), driven by [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) + [RiskProfile](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt). Pull-side, owner-controlled, never pushed. USPSTF/ACS/NCCN/ESMO-anchored cadence per region. This is the single highest-leverage oncology-relevant feature Bios could ship and operationalises the manifesto.
2. **Hereditary cancer syndrome extension to RiskProfile** (§2.2). Optional `hereditarySyndrome` enum + `geneticTestingDate`. Feeds the cadence engine. Small population, highest harm-prevention leverage per owner.
3. **Cancer-prevention pull-side framing for the lifestyle substrate already present** (§§2.13–2.18). IARC-anchored relative-risk trajectory information for tobacco, alcohol, obesity, inactivity, UV exposure. Pull-side. No nudging. The substrate is built; only the framing is new.

**Tier B — active-treatment surveillance; the case is the STAR-trial mortality arithmetic**

4. **Chemotherapy toxicity convergence pattern** (§2.4) gated to an `on_active_chemotherapy` physiology state. Reuses the [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) convergence machinery. Reaches URGENT severity for neutropenic-fever criteria.
5. **Troponin and NT-proBNP added to BIOMARKER** (§2.11). Wires cardio-oncology surveillance into the existing biomarker-pattern engine.
6. **Cardiotoxicity surveillance pattern** (§2.3) gated to an `on_cardiotoxic_therapy` physiology state with agent class. Pairs with the cardiology audit's HF-decompensation gap.
7. **irAE multi-organ patterns** (§2.5) gated to an `on_active_immunotherapy` state. Reuses existing thyroid, hepatic, renal, glucose-CV patterns; adds pneumonitis and (when myocarditis pattern lands) cardiac.
8. **Tumour-marker import** in the [BIOMARKER](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) domain (§2.11): CEA, CA-125, CA 19-9, PSA, HCG, AFP, CA 15-3/CA 27-29, calcitonin, thyroglobulin, LDH. Each with the LOINC mapping. ASCO/NCCN surveillance context surfaced alongside the value where the asymptomatic-OS-benefit evidence is negative.

**Tier C — survivorship and palliative; the case is the population horizon and the manifesto-alignment**

9. **Cancer surveillance window physiology state** (§2.12). `cancer_type` + `treatment_completion_date` sidecars. Renders NCCN follow-up cadence on the pull side; scores tumour-marker readings against pre-treatment baseline.
10. **Cancer-related fatigue trajectory pattern** (§2.7) reusing existing recovery-deficit substrate, gated to `on_active_chemotherapy` or `survivorship_window` state, with CTCAE grading.
11. **Cachexia trajectory pattern** (§2.8). One signal-rule entry away from existing.
12. **Cancer pain trajectory pattern** (§2.9). Uses existing [PAIN_SCORE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt); pairs with medication context for opioid escalation.
13. **Radiation therapy toxicity overlay** (§2.10) with anatomic-site sidecar.
14. **Hospice physiology state** (§2.20) that suppresses trend patterns inappropriate for end-of-life care and surfaces ESAS symptom-burden tracking.
15. **Survivorship overlay** (§2.13) for childhood and adult survivors; late-effects-aware cadence; COG LTFU v6 anchored for paediatric.
16. **End-of-life data disposition surface** (§2.20). Advance-directive integration; posthumous disposition; the manifesto extension this requires is the most consequential governance question Bios will face.

**Tier D — exploratory; ship only if the foundation is solid and the evidence base matures**

17. **Cancer-VTE convergence pattern** (§2.19). Research-stage; revisit when the wearable-VTE literature is more mature.
18. **Decentralised-trial export profile** (§2.21). Only ship when an owner explicitly asks for it; do not pre-build infrastructure that pulls Bios toward a trial-sponsor data-flow posture.
19. **Paediatric oncology physiology overlays** (§2.22). Coordinate with the paediatrics audit; do not ship a half-built paediatric vital-range model.

**Do not adopt**

- **Push-side cancer-risk score** ("you are at high lifetime risk for cancer X"). The clinical answer and the manifesto answer converge here: risk stratification belongs on the pull side, surfaced when the owner asks. A pushed risk score would be the cancer-care equivalent of the productivity-coach health-score the manifesto exists to refuse.
- **Tumour-marker alerts with implied diagnostic certainty**. A rising CEA in a CRC survivor needs an oncologist, not a Bios alert claiming recurrence. Surface the data; reference the ASCO/NCCN context; suggest the conversation.
- **Direct integration with cancer-genetic-testing companies** (e.g., 23andMe BRCA1/2 panel). The data is valuable but the import path raises consent and re-identification questions the manifesto would need to address explicitly. Owner-mediated FHIR import is the safer path.
- **Marketing toward cancer patients specifically**. The instrument-not-coach posture means Bios should be useful to the cancer-care population, but should never position itself as a cancer-management product. The clinical, regulatory, and emotional weight of cancer care is not something a non-cleared instrument should claim to share.

---

## 5. Summary line for the project

> Cancer is the second-leading cause of death globally; cancer-screening cadence and tertiary-prevention exposure modification are the highest-leverage population-level interventions available, and Bios's *"treat the not-yet-ill," pull-side, instrument-not-coach* posture is structurally the most aligned of any consumer health product I have audited for the cancer-prevention mission. To realise that alignment, Bios needs (a) a USPSTF/ACS/NCCN-anchored, region-aware, family-history-modified **screening-cadence engine**, (b) a hereditary-cancer-syndrome extension to RiskProfile, (c) IARC-anchored cancer-prevention framing for the lifestyle substrate already present, (d) an `on_active_chemotherapy` physiology state that turns the existing multi-signal convergence engine into a STAR-trial-shaped toxicity-surveillance feed, (e) troponin + NT-proBNP + tumour markers added to the BIOMARKER panel, and (f) a survivorship and hospice posture that respects the unique manifesto-relevant gravity of end-of-life cancer-data autonomy. None of these violate the manifesto; all of them are within the existing architecture; the case for each is the cancer-mortality arithmetic.
