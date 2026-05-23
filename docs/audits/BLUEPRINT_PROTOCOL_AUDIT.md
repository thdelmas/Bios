# Blueprint Protocol Audit — Bios vs. Bryan Johnson's Measurement Stack

**Scope:** Coverage audit comparing Bios's current measurement surface against the published Bryan Johnson "Blueprint" longevity protocol, with the goal of identifying which Blueprint measurements Bios already captures, which it could reasonably add, and which are out of scope on manifesto or feasibility grounds.
**Date:** 2026-05-23
**Branch:** `docs/blueprint-protocol-audit`
**Lens:** Coverage matrix, not a clinical evaluation of the Blueprint protocol itself. Bios's philosophy ("instrument, not coach"; on-device only; owner sets goals) intentionally diverges from Blueprint's prescriptive stack — this audit measures *measurement parity*, not protocol agreement.
**Auditor:** Claude (Opus 4.7)

Files reviewed: [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), all adapters in [android/app/src/main/java/com/bios/app/ingest/](../../android/app/src/main/java/com/bios/app/ingest/), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/SELF_REPORTED_DATA_HOME.md](../SELF_REPORTED_DATA_HOME.md), [MANIFESTO.md](../../MANIFESTO.md).

Blueprint sources:
- [blueprint.bryanjohnson.com/pages/biomarkers](https://blueprint.bryanjohnson.com/pages/biomarkers) — itemised lab biomarker panel (fetched 2026-05-23)
- [blueprint.bryanjohnson.com/blogs/news/bryan-johnsons-protocol](https://blueprint.bryanjohnson.com/blogs/news/bryan-johnsons-protocol) — full protocol including imaging, functional tests, wearable cadence (fetched 2026-05-23)

---

## Note on the "219" count

The popular "219 biomarkers" figure circulates from older Blueprint communications. The current canonical Blueprint Biomarkers page states **140 biomarkers at baseline + 86 at follow-up = 226 measurements per year**. Some markers repeat across category groupings (e.g., cortisol counts under Adrenal and Hormone), so any single number is approximate. This audit uses the **226** figure from the live page as the authoritative denominator, and flags where category overlap inflates totals.

---

## Executive summary

Bios covers **~38 of Blueprint's ~140 baseline biomarkers** (~27 %) and **most of the wearable-tier functional measurements** (sleep, HRV, RHR, SpO2, steps, body composition, VO2max, CGM). The four epigenetic age clocks Blueprint relies on (DunedinPACE, GrimAge, PhenoAge, Horvath) are all first-class Bios MetricTypes via manual entry — Bios is at parity here.

The coverage gap is concentrated in three places:

1. **Lab panel breadth.** Blueprint's CBC differential (48 markers), kidney panel (32 markers including BUN, uric acid, phosphate), and micronutrient panel (26 markers including iron studies, omega-3 index, zinc) are mostly unrepresented in [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt). These are pure-additive enum extensions — no engine work, no privacy concerns — and the FHIR importer pathway already exists ([SELF_REPORTED_DATA_HOME.md](../SELF_REPORTED_DATA_HOME.md)).
2. **Imaging and structural measurements.** DEXA scans, full-body MRI, multispectral skin imaging, Visia, coronary CT — Blueprint's imaging surface has no Bios analogue. Bios has [ECG_STRIP_AVAILABLE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) as a precedent for "binary blob with metadata pointer," which is the right shape for imaging reports too.
3. **Specialised assays.** pTAU217 (Alzheimer's blood biomarker), VEGF, telomere length, urinary toxin panel (2,4-D, MEP, MBP, phthalates), heavy metals (lead, mercury). These are research-tier or detoxification-tier measurements that the typical Bios owner won't order; adding them to the enum is cheap but the priority is low.

Notable cases where **Bios is ahead of the Blueprint protocol page** as currently published:
- **PPG waveform morphology** (`PPG_PEAK_AMPLITUDE_MEAN`, `PPG_RISE_TIME_MEAN`, `PPG_DICHROTIC_NOTCH_POSITION`, etc.) — Bios derives arterial-stiffness surrogates on-device from camera PPG. Blueprint pays for an external pulse-wave-velocity device; Bios computes the same family of features from a fingertip on the phone camera. The cardiology audit (#2) flagged these features as under-exposed — the Blueprint comparison reinforces it: they map directly to Blueprint's `CBP`, `CPP`, `AP`, `AIx`, `SEVR` cluster.
- **Glucose variability derivations** (`GLUCOSE_CV`, `GLUCOSE_MAGE`, `GLUCOSE_TIME_IN_RANGE`, `GLUCOSE_PEAK_24H`) — Blueprint exposes CGM raw values; Bios computes the four standard CGM-quality derivatives natively in [GlucoseVariability.kt](../../android/app/src/main/java/com/bios/app/engine/GlucoseVariability.kt).
- **Sleep regularity** as a circular-statistics metric — Bios computes it ([SleepRegularityCalculator.kt](../../android/app/src/main/java/com/bios/app/engine/SleepRegularityCalculator.kt)) over a 14-day rolling window. Blueprint tracks sleep duration and stages but does not publish a regularity score.
- **Personal baseline + anomaly detection.** Bios's [BaselineEngine.kt](../../android/app/src/main/java/com/bios/app/engine/BaselineEngine.kt) + [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt) is a fundamentally different posture from Blueprint's "optimise to 18-year-old percentiles." Blueprint compares to population norms; Bios compares to the owner's own past. These are not competing — they are complementary — but Bios's posture is the one that survives manifesto scrutiny.

---

## 1. Blueprint biomarker panel → Bios coverage matrix

Counts are Blueprint's category totals as listed on [blueprint.bryanjohnson.com/pages/biomarkers](https://blueprint.bryanjohnson.com/pages/biomarkers). Bios coverage cites the [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) enum identifier. "✅" = explicit enum entry; "🟡" = ingestible via FHIR/manual but no first-class enum; "❌" = no path today.

### 1.1 Adrenal (Blueprint: 2 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| Cortisol | ✅ `CORTISOL` | µg/dL, manual entry, FHIR import |
| DHEA Sulfate | ❌ | Common longevity panel marker, low-cost add |

### 1.2 Autoimmune (Blueprint: 4 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| ANA Screen | ❌ | |
| ANA Pattern | ❌ | Categorical (homogeneous/speckled/nucleolar/…) |
| ANA Titer | ❌ | |
| Rheumatoid Factor | ❌ | |

Coverage: 0/4. ANA Pattern is categorical; the others are quantitative. Low priority unless an autoimmune-focused condition pattern is on the roadmap.

### 1.3 Blood / CBC (Blueprint: 48 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| Hemoglobin | ✅ `HEMOGLOBIN` | |
| Hematocrit | ✅ `HEMATOCRIT` | |
| Red Blood Cell Count | ✅ `RBC` | |
| White Blood Cell Count | ✅ `WBC` | |
| Platelet Count | ✅ `PLATELETS` | |
| ABO Group, Rh Type | ❌ | One-time identity attributes, not a longitudinal metric |
| MCV, MCH, MCHC, RDW, MPV | ❌ | Standard CBC indices; easy enum additions |
| Neutrophils (abs + %) | ❌ | Includes Band Neutrophils |
| Lymphocytes (abs + %, Reactive) | ❌ | |
| Monocytes (abs + %) | ❌ | |
| Eosinophils (abs + %) | ❌ | |
| Basophils (abs + %) | ❌ | |
| Blasts, Promyelocytes, Myelocytes, Metamyelocytes, Nucleated RBC | ❌ | Manual differential; only reported when abnormal |

Coverage: 5/48. The five high-level cell counts are present; the differentials (which fire most of the CBC's screening value) are not. **Recommended:** add the 5 indices (MCV, MCH, MCHC, RDW, MPV) and the 5-part differential (NEU%, LYM%, MON%, EOS%, BAS%) — 10 enum entries that close the bulk of the CBC.

### 1.4 Bone (Blueprint: 4 markers)

Blueprint does not itemise on the public page. Likely candidates: bone-specific alkaline phosphatase, osteocalcin, CTX, P1NP.

| Blueprint | Bios | Notes |
|---|---|---|
| Bone turnover markers | ❌ | Specialist endocrinology, low priority |
| Bone mineral density (DEXA-derived) | 🟡 `BONE_MASS` (Withings BMR proxy) | True BMD requires DEXA imaging |

### 1.5 Brain (Blueprint: 6 markers)

Blueprint does not fully itemise. Includes pTAU217 (Alzheimer's plasma biomarker, called out in the protocol page).

| Blueprint | Bios | Notes |
|---|---|---|
| pTAU217 | ❌ | Emerging Alzheimer's screening marker (Quanterix Simoa). Worth adding given Bios's longitudinal posture; ingestible via FHIR today even without enum entry. |
| Other neuro markers (Aβ42/40, NfL, GFAP, etc.) | ❌ | Research-tier |

### 1.6 Cardiovascular (Blueprint: 15 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| Total Cholesterol | ✅ `TOTAL_CHOLESTEROL` | |
| HDL Cholesterol | ✅ `HDL_CHOLESTEROL` | |
| LDL Cholesterol | ✅ `LDL_CHOLESTEROL` | |
| Triglycerides | ✅ `TRIGLYCERIDES` | |
| ApoB | ✅ `APO_B` | Bios was already ahead of most consumer panels |
| hs-CRP | ✅ `HSCRP` | |
| Chol/HDL Ratio | 🟡 Derivable from Total + HDL | No explicit enum; trivially computed |
| Non-HDL Cholesterol | 🟡 Derivable from Total − HDL | No explicit enum |
| Lipoprotein (a) | ❌ | Independent ASCVD risk; cardiology audit (#11) also flagged. **High-value add.** |
| LDL Particle Number (LDL-P) | ❌ | NMR or ion mobility panel; advanced lipidology |
| LDL Pattern (A/B) | ❌ | Categorical |
| LDL Peak Size | ❌ | |
| LDL Small | ❌ | |
| LDL Medium | ❌ | |
| HDL Large | ❌ | |

Coverage: 6/15 explicit, 8/15 if derivable ratios counted. **Lp(a) is the priority single add** — it is the single strongest independent ASCVD risk marker and a routine Blueprint reading.

### 1.7 Electrolyte (Blueprint: 2 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| Sodium | ❌ | Standard CMP marker |
| Potassium | ❌ | Standard CMP marker; clinically important (arrhythmia risk) |

Coverage: 0/2. Both should be added — they are CMP basics and Bios has no other CMP coverage.

### 1.8 Eye (Blueprint: 1 marker)

Blueprint doesn't itemise. Likely intraocular pressure or visual acuity from the annual eye-doctor visit.

| Blueprint | Bios | Notes |
|---|---|---|
| Vision / IOP | ❌ | Manual entry could capture; no first-class enum |

### 1.9 Fertility (Blueprint: 3–4 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| AMH (women only) | ❌ | Ovarian reserve marker |
| FSH | ❌ | |
| LH | ❌ | |
| SHBG | ❌ | |
| Semen analysis (men: motile count, concentration, motility, morphology) | ❌ | Specialist; manual entry only realistic path |

Coverage: 0/4. FSH/LH/SHBG/AMH are routine reproductive endocrinology — worth adding given Bios already has the [ReproductiveDatabase](../../android/app/src/main/java/com/bios/app/data/) isolated table.

### 1.10 Heart (Blueprint: 12 markers)

Largely overlaps with Cardiovascular. The protocol page calls out additional "vascular function" markers:

| Blueprint | Bios | Notes |
|---|---|---|
| Central Blood Pressure (CBP) | 🟡 PPG morphology can approximate | Bios computes the precursor features but does not surface a CBP estimate |
| Central Pulse Pressure (CPP) | 🟡 PPG morphology precursor | Same |
| Augmentation Pressure (AP) | 🟡 PPG morphology precursor | Same |
| Augmentation Index (AIx) | 🟡 PPG morphology precursor | **Highly derivable from existing `PPG_*` MetricTypes** — see cardiology audit #2 |
| SEVR (Subendocardial Viability Ratio) | 🟡 PPG morphology precursor | |
| VEGF | ❌ | Vascular endothelial growth factor; specialty assay |

**Recommended:** add a `PULSE_WAVE_AUGMENTATION_INDEX` derived MetricType computed in [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) from the already-extracted dichrotic notch and peak amplitude features. This is the single highest-leverage addition surfaced by both this audit and the cardiology audit.

### 1.11 Heavy Metals (Blueprint: 2 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| Lead (blood) | ❌ | |
| Mercury (blood) | ❌ | |

Coverage: 0/2. Low population priority; specialty test. Enum additions are cheap if pursued.

### 1.12 Hormone (Blueprint: 13 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| Cortisol | ✅ `CORTISOL` | |
| Estradiol | ✅ `ESTRADIOL` | |
| Testosterone, Total | ✅ `TESTOSTERONE_TOTAL` | |
| TSH | ✅ `TSH` | |
| IGF-1 | ✅ `IGF_1` | Bios includes; not always present in consumer panels |
| DHEA Sulfate | ❌ | See §1.1 |
| FSH | ❌ | See §1.9 |
| LH | ❌ | See §1.9 |
| Leptin | ❌ | Adipokine; metabolic relevance |
| Prolactin | ❌ | |
| SHBG | ❌ | See §1.9 |
| Testosterone, Free | ❌ | Free T is the bioavailable fraction; useful alongside total |
| Thyroglobulin Antibodies | ❌ | See §1.18 thyroid |
| Thyroid Peroxidase Antibodies | ❌ | See §1.18 |

Coverage: 5/13. **Recommended adds (priority order):** Free Testosterone, SHBG, DHEA-S, Prolactin, Leptin.

### 1.13 Immune (Blueprint: 37 markers)

Mostly overlaps with CBC differential (§1.3) and ANA (§1.2). True coverage: 5/37 via the CBC top-line entries.

### 1.14 Inflammation (Blueprint: 17 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| hs-CRP | ✅ `HSCRP` | |
| Homocysteine | ❌ | Cardiovascular / cognitive risk; standard longevity marker |
| Other (IL-6, TNF-α, fibrinogen, etc.) | ❌ | Research-tier; rarely on consumer panels |

Coverage: 1/17 explicit. **Homocysteine is the priority add** — it is a routine Blueprint marker and a standard longevity-panel item, often co-tested with B12 and folate (both already in Bios).

### 1.15 Kidney (Blueprint: 32 markers — counts CMP + urine)

| Blueprint | Bios | Notes |
|---|---|---|
| eGFR | ✅ `EGFR` | KDIGO 2024 anchored |
| Creatinine | ✅ `CREATININE` | |
| Magnesium | ✅ `MAGNESIUM` | |
| BUN (Blood Urea Nitrogen) | ❌ | CMP basic |
| BUN/Creatinine Ratio | 🟡 Derivable | |
| Calcium | ❌ | CMP basic |
| Carbon Dioxide | ❌ | CMP basic |
| Chloride | ❌ | CMP basic |
| Phosphate | ❌ | |
| Sodium, Potassium | ❌ | See §1.7 |
| Uric Acid | ❌ | Gout + cardiovascular risk |
| Urine markers (26 items: glucose, ketones, protein, blood, microscopy) | ❌ | See §1.20 |

Coverage: 3/32. **Recommended:** the 6 missing CMP basics (BUN, Calcium, CO2, Chloride, Phosphate, Uric Acid). Combined with §1.7 (Na, K) this brings Bios to a full Comprehensive Metabolic Panel — a foundational add.

### 1.16 Liver (Blueprint: 13 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| ALT | ✅ `ALT` | |
| AST | ✅ `AST` | |
| GGT | ✅ `GGT` | |
| Albumin | ❌ | CMP / liver panel basic |
| Alkaline Phosphatase | ❌ | |
| Bilirubin, Total | ❌ | |
| Globulin | ❌ | |
| Albumin/Globulin Ratio | 🟡 Derivable | |
| Total Protein | ❌ | |
| Amylase | ❌ | Also a pancreatic marker |
| Lipase | ❌ | Also a pancreatic marker |

Coverage: 3/13. **Recommended:** Albumin, Alkaline Phosphatase, Bilirubin Total, Total Protein, Amylase, Lipase — the rest of a standard liver panel.

### 1.17 Metabolic (Blueprint: 22 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| Glucose | ✅ `BLOOD_GLUCOSE`, `FASTING_GLUCOSE` | |
| HbA1c | ✅ `HBA1C` | |
| Insulin | ✅ `FASTING_INSULIN` | |
| HOMA-IR | ✅ `HOMA_IR` | |
| Homocysteine | ❌ | See §1.14 |
| Methylmalonic Acid (MMA) | ❌ | B12 sufficiency marker |

Coverage: 4/22 explicit. The Glucose Variability Engine adds `GLUCOSE_CV`, `GLUCOSE_MAGE`, `GLUCOSE_TIME_IN_RANGE`, `GLUCOSE_PEAK_24H` — these are **derived metrics Blueprint does not publish**.

### 1.18 Micronutrient (Blueprint: 26 markers)

| Blueprint | Bios | Notes |
|---|---|---|
| Vitamin D (25-OH) | ✅ `VITAMIN_D_25OH` | |
| Vitamin B12 | ✅ `VITAMIN_B12` | |
| Folate | ✅ `FOLATE` | |
| Magnesium | ✅ `MAGNESIUM` | |
| Ferritin | ✅ `FERRITIN` | |
| Iron | ❌ | |
| Iron Saturation % | ❌ | |
| Total Iron Binding Capacity (TIBC) | ❌ | |
| Zinc | ❌ | |
| Omega-3 Total, Omega-6 Total, Omega-6/3 Ratio | ❌ | OmegaQuant-style panel |
| EPA, DHA, DPA | ❌ | |
| Arachidonic Acid, AA/EPA Ratio | ❌ | |
| Linoleic Acid | ❌ | |

Coverage: 5/26. **Iron panel (Iron, Iron Sat %, TIBC)** is the highest-priority add — these are essentials co-ordered with Ferritin. **Omega-3 index** (EPA+DHA in RBC) is a popular longevity marker and worth a single derived MetricType.

### 1.19 Pancreas / Prostate / Thyroid

| Blueprint | Bios | Notes |
|---|---|---|
| Amylase, Lipase | ❌ | Cross-listed with Liver §1.16 |
| Glucose | ✅ | |
| PSA Total (men) | ❌ | Standard men's screening after 50 |
| PSA Free | ❌ | |
| PSA Free % | 🟡 Derivable | |
| TSH | ✅ `TSH` | |
| Free T4 | ✅ `FREE_T4` | |
| Free T3 | ✅ `FREE_T3` | |
| Thyroglobulin Antibodies | ❌ | Hashimoto's screen |
| Thyroid Peroxidase Antibodies | ❌ | Hashimoto's screen |

Thyroid coverage: 3/5. **Adding TPO Ab + Tg Ab** completes the thyroid autoimmunity workup.

### 1.20 Urine (Blueprint: 26 markers)

| Category | Bios |
|---|---|
| All 26 urinalysis markers (color, appearance, pH, specific gravity, glucose, ketones, protein, bilirubin, nitrite, leukocyte esterase, occult blood, microscopy) | ❌ |

Coverage: 0/26. Urinalysis is binary or categorical for most markers and rarely captured longitudinally outside annual physicals. A single `URINALYSIS_RESULT` event-type with payload (per Bios's existing event_payloads pattern) could ingest a full strip result without 26 enum entries.

---

## 2. Imaging, functional, and other Blueprint measurements

Items from the [Blueprint protocol page](https://blueprint.bryanjohnson.com/blogs/news/bryan-johnsons-protocol) that are not on the biomarker lab panel.

### 2.1 Imaging

| Blueprint | Bios | Notes |
|---|---|---|
| Full-body MRI (annual) | ❌ | No imaging ingestion path. Could follow `ECG_STRIP_AVAILABLE` precedent — a binary blob with metadata pointer. Realistically a "report PDF + key findings text" import is more useful than the DICOM. |
| DEXA (bone density + body composition) | 🟡 Partial via `BONE_MASS`, `LEAN_MASS`, `BODY_FAT_PCT` from Withings/Health Connect (bioimpedance, not DEXA) | True DEXA T-score / Z-score has no enum. Worth adding `BONE_DENSITY_T_SCORE` as a manual-entry MetricType. |
| Coronary CT angiography / CAC score | ❌ | `CORONARY_CALCIUM_SCORE` would be a useful single addition — once-per-decade test but a major ASCVD modifier. |
| Visia multispectral skin imaging | ❌ | Out of scope; specialist dermatology |
| Carotid IMT, liver MRI PDFF/MRE | ❌ | Out of scope for typical owners |
| Echocardiogram | ❌ | Imaging report; same path as full-body MRI |

### 2.2 Functional / cardiopulmonary

| Blueprint | Bios | Notes |
|---|---|---|
| VO2 max | ✅ `VO2_MAX` | Health Connect, Oura, Garmin all emit |
| Resting HR | ✅ `RESTING_HEART_RATE` | |
| HRV (RMSSD) | ✅ `HEART_RATE_VARIABILITY` | Plus 5 derived HRV metrics |
| Blood pressure | ✅ `BLOOD_PRESSURE_SYSTOLIC` / `_DIASTOLIC` | |
| Arterial stiffness (CBP, CPP, AP, AIx, SEVR) | 🟡 PPG morphology features present, no derived index | See §1.10 — **closeable inside existing architecture** |
| Pulse wave velocity | 🟡 Derivable from PPG | |
| Grip strength | ❌ | Cannot be measured by phone/wearable; manual entry only |
| Balance / gait | 🟡 `FALL_EVENT`, `NEAR_MISS_FALL` from Virgil companion | Bios captures negative outcomes (falls), not the positive test (timed-up-and-go) |
| 6-minute walk test | ❌ | Could be a manual-entry MetricType (meters walked) |

### 2.3 Sleep

| Blueprint | Bios | Notes |
|---|---|---|
| Sleep duration | ✅ `SLEEP_DURATION` | |
| Sleep stages (light/deep/REM/awake) | ✅ `SLEEP_STAGE` | |
| Sleep latency | ✅ `SLEEP_LATENCY` | |
| Sleep efficiency | ✅ `SLEEP_EFFICIENCY` | |
| Wake after sleep onset | ✅ `WAKE_AFTER_SLEEP_ONSET` | |
| Sleep fragmentation | ✅ `SLEEP_FRAGMENTATION_INDEX` | |
| Sleep score | ✅ `SLEEP_SCORE` | |
| **Sleep regularity** | ✅ `SLEEP_REGULARITY` | **Bios computes; Blueprint does not publish** |
| **Circadian phase shift** | ✅ `CIRCADIAN_PHASE_SHIFT` | **Bios computes; Blueprint does not publish** |
| Eight Sleep mattress data | ❌ | No adapter; not on roadmap |

Bios is fully at parity here and **ahead on two derived metrics** (regularity, phase shift).

### 2.4 Body composition

Full coverage: `BODY_MASS`, `BODY_FAT_PCT`, `LEAN_MASS`, `BODY_WATER_PCT`, `BONE_MASS` — all via Withings + Health Connect.

### 2.5 CGM / glucose

| Blueprint | Bios | Notes |
|---|---|---|
| CGM raw values | ✅ `BLOOD_GLUCOSE` (Dexcom adapter) | |
| Time in range | ✅ `GLUCOSE_TIME_IN_RANGE` | Bios-derived |
| Glucose variability (CV) | ✅ `GLUCOSE_CV` | Bios-derived |
| MAGE | ✅ `GLUCOSE_MAGE` | Bios-derived |
| Peak 24h | ✅ `GLUCOSE_PEAK_24H` | Bios-derived |

Bios is at parity on raw CGM and **ahead on derivatives**.

### 2.6 Environment

| Blueprint | Bios | Notes |
|---|---|---|
| Air quality (PM2.5, VOC, CO2) | ✅ `AIR_PM25`, `AIR_VOC`, `AIR_CO2` via [BleAirQualityAdapter.kt](../../android/app/src/main/java/com/bios/app/ingest/BleAirQualityAdapter.kt) | |
| Ambient temperature, humidity | ✅ `AMBIENT_TEMPERATURE_C`, `AMBIENT_HUMIDITY_PCT` | |
| Ambient light | ✅ `AMBIENT_LIGHT` | |
| Water quality (Simplelab) | ❌ | Manual entry would be the realistic path |
| Toxin panel (2,4-D, MEP, MBP, MEHP, NAPR, HEMA, perchlorate) | ❌ | Specialty urinary panel; very low population priority |

### 2.7 Sensory / organ-specific

| Blueprint | Bios | Notes |
|---|---|---|
| Audiogram | ❌ | Could be a manual-entry MetricType (hearing threshold by frequency) |
| Vision / acuity | ❌ | |
| Dental (plaque, exam findings) | ❌ | |
| Skin mole check | ❌ | |
| Sexual function (NTE) | ❌ | Highly sensitive; if added, must follow reproductive-health isolation pattern |

### 2.8 Epigenetic age / aging

| Blueprint | Bios | Notes |
|---|---|---|
| DunedinPACE | ✅ `EPIGENETIC_AGE_DUNEDIN_PACE` | Manual entry from TruDiagnostic etc. |
| GrimAge | ✅ `EPIGENETIC_AGE_GRIM` | |
| PhenoAge | ✅ `EPIGENETIC_AGE_PHENO` | |
| Horvath | ✅ `EPIGENETIC_AGE_HORVATH` | |
| Telomere length | ❌ | TeloYears, SpectraCell — single MetricType add |
| Telomerase activity | ❌ | Research-tier |

Coverage: 4/6 — **Bios is at parity on the four major epigenetic clocks**, missing only telomere measures.

### 2.9 Microbiome

| Blueprint | Bios | Notes |
|---|---|---|
| 16S / metagenomic gut profile | ❌ | Output is a taxonomic + diversity report, not a single number. Could be ingested as an event with payload (vendor, diversity index, dysbiosis flag). |

---

## 3. Gaps grouped by priority

### 3.1 High-priority adds (do these first)

These close real coverage gaps for low engineering cost and high owner value. All are enum additions to [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) + LOINC mappings in the FHIR importer.

1. **Lipoprotein (a)** — strongest independent ASCVD risk marker; cardiology audit also flagged.
2. **Homocysteine** — standard longevity marker; cross-cuts §1.14 inflammation and §1.17 metabolic.
3. **CMP completion** — BUN, Calcium, CO2, Chloride, Phosphate, Sodium, Potassium, Uric Acid (8 markers) — completes the basic metabolic panel.
4. **Liver panel completion** — Albumin, Alkaline Phosphatase, Bilirubin Total, Total Protein, Amylase, Lipase (6 markers).
5. **CBC indices + differential** — MCV, MCH, MCHC, RDW, MPV + Neutrophils/Lymphocytes/Monocytes/Eosinophils/Basophils % (10 markers).
6. **Iron panel** — Iron, Iron Saturation %, TIBC (3 markers, co-ordered with existing Ferritin).
7. **Reproductive endocrine panel** — FSH, LH, SHBG, AMH, Free Testosterone, Prolactin, DHEA-S (7 markers).
8. **Pulse wave augmentation index** — derived MetricType in [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) from existing PPG features. Cross-referenced by [CARDIOLOGY_POV.md](CARDIOLOGY_POV.md) gap #2.

**Total:** ~36 new MetricType entries + one derived metric. Brings Bios from ~38/140 to ~74/140 baseline-panel coverage (~53 %) with no new ingestion paths required.

### 3.2 Medium-priority adds

- **Thyroid autoimmunity:** TPO Antibodies + Thyroglobulin Antibodies (2 entries).
- **PSA panel:** PSA Total + PSA Free (2 entries) — relevant for men 50+.
- **Telomere length** — single entry, manual import from TeloYears/SpectraCell.
- **Coronary calcium score** — single entry, once-per-decade test, large prognostic value.
- **Bone density T-score** — single entry; DEXA result.
- **pTAU217** — Alzheimer's plasma biomarker, single entry.
- **Vitamin D auxiliaries** — Vitamin K2 (MK-7), Vitamin A, Vitamin E if a fuller fat-soluble panel is desired.

### 3.3 Out of scope / won't-do

- **Blueprint's prescriptive protocol layer** (the "blueprint diet," supplement stack, "Don't Die" community). Bios is an instrument, not a coach. The MetricTypes that *measure* what Blueprint tries to optimise belong in Bios; the optimisation targets and adherence tracking do not.
- **Visia multispectral imaging, full-body MRI raw DICOM, echocardiogram waveforms.** Bios is a phone app; specialised imaging belongs in DICOM viewers and clinic systems. The *report* (text findings, key measurements) can be captured by manual entry or PDF reference.
- **Specialised toxin urinary panels** (2,4-D, MEP, MBP, etc.). Population value is too narrow.
- **Sexual function NTE.** If ever added, must inherit the [ReproductiveDatabase](../../android/app/src/main/java/com/bios/app/data/) isolation pattern (separate encrypted database, separate disclosure surface). Default off.
- **Eight Sleep mattress adapter.** Vendor-locked, niche population, no public API parity.
- **CIED telemetry** (Medtronic CareLink etc.). Cross-referenced by [CARDIOLOGY_POV.md](CARDIOLOGY_POV.md) gap #9 — vendor-locked, no realistic ingestion path.

### 3.4 Where Bios is already ahead of the Blueprint page

Documented so the manifesto framing stays honest: Bios is not a Blueprint clone with gaps; it does several things Blueprint's published protocol does not.

1. **On-device baseline + anomaly detection.** Blueprint optimises against population percentiles. Bios compares against the owner's own past. Both have value; only Bios's posture survives the "evaluation belongs to the owner" manifesto clause.
2. **PPG-derived arterial stiffness substrate.** Blueprint pays for external pulse-wave-velocity devices. Bios extracts the same feature family from camera PPG on a phone.
3. **Glucose variability derivatives.** Blueprint publishes CGM raw values. Bios computes CV / MAGE / TIR / peak-24h natively.
4. **Sleep regularity (circular statistics).** Bios computes; Blueprint does not publish.
5. **Circadian phase shift estimate.** Bios computes from sleep onset + duration; Blueprint does not publish.
6. **Irregular rhythm burden from PPG.** Rolling 7-day measure of AF-suspect windows; cardiology audit identified this as work-in-progress, but it is more than Blueprint exposes.
7. **Mental health observables.** `TYPING_CADENCE`, `MOOD_DRIFT_SCORE`, `CIRCADIAN_PHASE_SHIFT` from W2F integration. Blueprint has no mental-health surface beyond sleep quality.
8. **Safety signals.** `FALL_EVENT`, `NEAR_MISS_FALL`, `CHECK_IN_MISS` from Virgil. Blueprint has none.
9. **Erasure semantics.** Instant key destruction + LETHE burner/dead-man's-switch integration. Blueprint stores data on Blueprint servers indefinitely.

---

## 4. Recommended next steps

1. **Land §3.1 in a single MetricType expansion PR.** 36 enum additions + LOINC mappings + the augmentation-index derivation. Touches [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [FhirImporter.kt](../../android/app/src/main/java/com/bios/app/export/FhirImporter.kt), [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt). No ingestion-path work; no UI work beyond auto-generated manual-entry rows.
2. **Land §3.2 medium-priority adds in a follow-up.** ~12 enum additions; no derivation work.
3. **Decide the imaging-report ingestion question separately.** Coronary calcium score and DEXA T-score are single numbers; full-body MRI is a multi-page report. The right shape is probably: numeric findings as MetricType, full report as an attached blob (following [ECG_STRIP_AVAILABLE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) precedent).
4. **Do not implement Blueprint-style prescriptive targets.** The audit's purpose is measurement parity, not protocol mirroring. The owner sets goals; Bios reports deviations against the owner's own baseline.

---

## 5. Headline numbers

| Surface | Blueprint | Bios coverage |
|---|---|---|
| Baseline lab biomarkers | ~140 | ~38 (27 %) |
| Plus follow-up panel markers | +86 → 226 total | n/a (Bios doesn't distinguish baseline vs follow-up) |
| Wearable functional metrics (sleep, HRV, RHR, SpO2, CGM, body comp, VO2max) | covered | **at parity, ahead on derivations** |
| Epigenetic age clocks | 4 | **4/4 (100 %)** |
| Imaging | DEXA, MRI, CCT, Visia, etc. | none today |
| Bios-only metrics (no Blueprint analogue) | n/a | ~10 (PPG morphology, circadian phase, sleep regularity, mental-health observables, safety events) |

**If §3.1 lands:** Bios biomarker coverage rises from ~27 % to ~53 % of Blueprint's baseline panel, while preserving the manifesto posture of "instrument, not coach."
