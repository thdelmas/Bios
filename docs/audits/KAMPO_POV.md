# Kampo Specialist Audit — Bios as a Sho-Candidate Reading Layer

**Scope:** Bios's usefulness in the consulting room of a Japan Society for Oriental Medicine (JSOM, 日本東洋医学会) board-certified Kampo specialist who is also a licensed MD. The reader is the most biomedically integrated of any East Asian medicine audit on this codebase: they read CRP, HbA1c and an abdominal palpation in the same visit, prescribe both an SGLT2 inhibitor and 大柴胡湯, and bill both through the same Japanese National Health Insurance (NHI) claim. This audit asks whether Bios's signals — already biomedically legible — can also be read as inputs to Sho (証) differentiation.
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Kampo (漢方) — Sho-based pattern identification (証), the Japanese empiricist Koho-ha (古方派) school's tight pattern→formula mapping, abdominal diagnosis (腹診, fukushin), the kyo-jitsu (虚実) / ki-ketsu-sui (気血水) / kan-netsu (寒熱) diagnostic axes, and the 148 NHI-covered Tsumura / Kotaro / Kracie formula extracts. Not an integrative-medicine audit and not a re-skin of the [TCM audit](./TCM_POV.md) — written specifically for the MD-Kampo dual-licensed reader who works inside the Japanese health system, where Kampo is mainstream medicine, not alternative.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [Wave5BiomarkerPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/Wave5BiomarkerPatterns.kt), [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [MedicationAnnotationRepo.kt](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt). Cross-read for distinction from the parallel East Asian audit: [TCM_POV.md](./TCM_POV.md).

This audit does **not** ask Bios to become a Kampo application, and it does not ask the codebase to abandon its biomedical idiom — for the Kampo reader, that idiom is the same one they use during the first half of the patient interview. What it asks is whether Bios's existing signal clusters could be exposed in a Sho-aware vocabulary alongside their existing labels, so the same patient handing the same FHIR bundle to a clinic in Setagaya gets read both ways.

---

## Executive summary

Of the three East Asian audits on this codebase, this is the one where the gap between Bios's vocabulary and the reader's is *narrowest*. The Kampo specialist trained at Kitasato, Kinki, or Toyama is not a parallel-track practitioner asked to translate from a foreign idiom — they prescribed atorvastatin this morning and 防風通聖散 this afternoon, and the same NHI claim covered both. Bios's biomarker layer ([BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [Wave5BiomarkerPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/Wave5BiomarkerPatterns.kt)) is *directly* usable; the FHIR Observation import is fully usable; the 148 NHI-covered Kampo extracts already sit on Japanese formularies the reader uses daily. What is missing is the second-half vocabulary — Sho identification, fukushin findings, the kyo-jitsu/ki-ketsu-sui axes — and a place in the data model to hold them.

The reader's distinctive contribution to East Asian medicine is **腹診 (fukushin)** — abdominal palpation patterns codified by Yoshimasu Tōdō (吉益東洞, 1702–1773) and refined into the contemporary JSOM curriculum. The classical findings — 心下痞硬 (shinka hikō, epigastric tightness), 胸脇苦満 (kyōkyō kuman, hypochondrial fullness), 小腹急結 (shōfuku kyūketsu, lower-abdominal acute knot), 腹直筋拘急 (fukuchokkin kōkyū, taut recti), 振水音 (shinsuion, succussion splash), 臍下不仁 (saika funin, subumbilical weakness) — are *the* operational basis for selecting between formulas that share otherwise-similar indications. Bios has no surface anywhere for any of these. This is the single most uniquely-Japanese gap in the codebase, and the audit calls it out first.

Ordered by impact through a Kampo specialist's lens:

1. **No place to record a fukushin finding.** This is the central diagnostic move that distinguishes Japanese Kampo from both modern Chinese TCM and Korean Hanui-hak, and Bios has zero representation. The owner cannot palpate their own 胸脇苦満; the practitioner records it in clinic. Bios's MedicationAnnotation surface ([MedicationAnnotationRepo.kt](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt)) shows the architectural pattern is in place — a parallel `FukushinFinding` annotation, owner-loggable from a clinic visit summary or practitioner-shared QR, would carry the six classical findings forward in the timeline the reader already trusts. Without this, no Bios export is fully readable to a Kampo specialist; with it, the FHIR Observation bundle plus a sidecar fukushin record is the most complete patient handoff the reader could reasonably ask for from a smartphone.
2. **The Sho is the unit of clinical reasoning, and Bios's condition patterns sit one layer below it.** The 33+ patterns in [ConditionPatterns.all](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L115-L124) — `cardiovascular_stress`, `recovery_deficit`, `chronic_inflammation`, `metabolic_drift`, `mental_health_correlate`, `cardiorespiratoryDeconditioning` — describe *signal clusters that resolve into a Sho* once the practitioner adds the four examinations (望聞問切, bō-bun-mon-setsu — looking, listening/smelling, asking, palpating). The clusters are not Sho themselves, but they are the *closest analog* of any West-coast software to the Sho's structure: convergent multi-signal pattern recognition, evaluated against an owner-specific baseline. A pull-side Sho-candidate annotation on each active condition pattern — listing the two or three Sho the cluster is most consistent with (e.g. `chronic_inflammation` ⇒ 瘀血 oketsu / 気滞 kitai / 湿熱 shitsunetsu candidates) — would be a thin layer that gives the reader a starting hypothesis to test in clinic. This is the single highest-leverage Sho-aware change.
3. **The kyo-jitsu (虚実) axis is unrepresented, and it is the first sorting move a Kampo specialist makes.** Japanese clinical Kampo, post-Koho-ha, runs on a smaller set of diagnostic axes than full Chinese zang-fu: kyo (虚, deficiency) vs. jitsu (実, excess) is the dominant polarity, refined by ki-ketsu-sui (気血水) and kan-netsu (寒熱). Bios has zero representation of any of these axes — no enum, no annotation, no derived view. The HRV depression + reduced active minutes signature read as `recovery_deficit` in [ConditionPatterns.kt:339-363](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L339-L363) is, in clinic, the substrate for a kyo Sho. The HRV depression + elevated RHR + elevated BP signature ([HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) + cardiovascular cluster) is the substrate for a jitsu Sho. Without these axes the reader has to do the translation in their head every time.
4. **148 NHI-covered formulas, zero recognition in the medication-annotation surface.** Bios's [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt) accepts free-text drug names so the AnomalyDetector can de-noise patterns when the owner is on a beta-blocker, statin, or SSRI. A Japanese owner on 半夏厚朴湯 (hange koboku tō) for plum-pit-throat sensation, or 加味逍遥散 (kami shōyō san) for premenstrual irritability, or 抑肝散 (yokukan san) for senile agitation, is on a *finite, well-catalogued formulary* — the 148 NHI extracts have Tsumura numbers (e.g. ツムラ24 = kami shōyō san, ツムラ54 = yokukan san) and analogous Kotaro / Kracie / Sanwa codes. Recognising these in the existing annotation surface, surfacing them with their target Sho and the expected directional effect on the wearable signals (yokukan san → HRV recovery and sleep stabilisation in dementia-spectrum agitation; daiōkanzōtō → bowel-frequency increase that may transiently raise activity counts), would do for Kampo formulas exactly what the existing annotation surface does for beta-blockers — let the practitioner read the patient's wearable record without the prescribed extract producing spurious alerts.
5. **Bios is positioned almost perfectly for the Japanese demographic context, but doesn't yet know it.** Japan has the world's oldest population (28% over 65, 10% over 80), the highest per-capita smartphone-with-LTE penetration in the OECD, NHI coverage that includes both biomedicine and Kampo, and a regulatory environment that treats Kampo extracts as pharmaceutical products with quality control by the PMDA. The frailty / geriatric gap already flagged in the [primary-care audit §2.7](./MEDICAL_PROFESSIONAL_POV.md#27-demographic-gating-pregnancy-paediatrics-frailty-athletes) and acknowledged in [PhysiologyState.FRAILTY_FLAG](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L37) is precisely the population Kampo geriatrics specialises in: yokukan san for BPSD, 補中益気湯 (hochū ekki tō) for post-stroke fatigue, 麻子仁丸 (mashinin gan) for elderly constipation. A Bios deployment that landed in Japan with the frailty patterns properly tuned and a Kampo-formula annotation surface would be more useful in a Tokyo outpatient clinic than in most of the markets Bios was originally designed for.
6. **No abdominal-symptom self-report keys, despite Kampo gastroenterology being a major Kampo sub-specialty.** Bios already has adjacent metabolic signals (blood glucose via CGM, weight, sleep fragmentation as a post-prandial fatigue proxy, [biomarker_nafld_signature](../../android/app/src/main/java/com/bios/app/alerts/Wave5BiomarkerPatterns.kt#L97) for hepatic enzymes). It has no surface for the symptoms that anchor Kampo GI diagnosis: epigastric discomfort severity, post-prandial fullness, regurgitation, abdominal bloating, stool frequency / consistency (Bristol scale would suffice), gas, borborygmus. A modest self-report key set in METABOLIC or a new GI domain — owner-loggable, never inferred — would let the existing patterns become anchors for 半夏瀉心湯-class (hange shashin tō, epigastric tightness with diarrhoea), 大建中湯-class (daikenchūtō, post-operative ileus, evidence-supported in NEJM-tier trials), 六君子湯-class (rikkunshi tō, functional dyspepsia, ROME-IV-compatible) Sho readings. This is the domain where Kampo has the strongest contemporary evidence base in mainstream Japanese medicine, and where Bios has the most existing adjacency.
7. **The PPG waveform is recorded and discarded, blind to myakushin.** [PpgSignalProcessor.kt:120-152](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L120-L152) computes peak amplitude, peak-amplitude CoV, RR series, RR CoV — and returns only the RR series after rejection-checking. Japanese Kampo myakushin (脈診) is less elaborated than Chinese 脉诊 — the standard Kampo curriculum teaches roughly six to eight pulse qualities (浮 fu / 沈 chin / 数 saku / 遅 chi / 虚 kyo / 実 jitsu / 滑 katsu / 弦 gen) rather than the 28 classical Chinese qualities — but the kyo / jitsu pulse distinction is *directly* connected to the kyo-jitsu Sho axis (gap §3). The same morphology features that the TCM audit flagged as latent (rise-time, amplitude CoV, asymmetry) carry the kyo / jitsu pulse signal that the Kampo reader uses, and the Japanese vocabulary's smaller set is easier to map cleanly than the Chinese one.
8. **`mental_health_correlate` is half-aligned with Kampo psychosomatic medicine, but the formula targets are invisible.** Kampo psychosomatic medicine (心身医学) is a recognised sub-specialty with a substantial Japanese MD-Kampo literature on 加味逍遥散 for menopausal mood drift, 抑肝散 for irritability with sleep disturbance, 半夏厚朴湯 for somatic anxiety with throat sensation, 桂枝加竜骨牡蛎湯 (keishi-ka-ryūkotsu-borei tō) for autonomic dysregulation with insomnia, 甘麦大棗湯 (kanbaku taisō tō) for affective lability and uncontrollable weeping. The signals Bios already aggregates in [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) — sleep architecture decline, HRV depression, activity drop, plus the W2F enrichers (typing cadence, circadian phase shift, mood drift score) — are exactly the substrate a Kampo psychosomatic clinic uses to confirm or refute these prescriptions over a 4–8 week trial. The data is on the bus; the formula-recognition layer is not.
9. **Bios already does kanpō-friendly things, quietly.** The 14-day personal baseline is the operational form of "each person carries their own normal," which Yoshimasu Tōdō and the entire Koho-ha tradition would have recognised as 平人 (heijin, the balanced state for *this* person, not a population). The convergence rule (`minActiveSignals = 3` for infection onset, `required = true` on biomarker gates) mirrors the Sho's reliance on multiple corroborating findings before a formula is selected. The "silence is a feature" posture maps directly onto the Japanese clinical reserve characteristic of the senior Kampo clinician — speak only when something is clear, never fill space. These are not parity wins; they are the orientation a Kampo specialist will recognise as competent on first read of the codebase.
10. **Acupuncture, anma, shiatsu and judo-seifuku are correctly out of scope, and worth naming as such.** The Japanese system separates 鍼灸 (hari-kyū), あんま指圧マッサージ (anma-massage-shiatsu) and 柔道整復 (judo-seifuku) into independently licensed paramedical professions (hari-kyū-shi, anma-massage-shiatsu-shi, judo-seifuku-shi). An MD-Kampo specialist almost never performs these. Bios should not implement acupoint surfaces, meridian palpation logs, or moxa-tracking; the reader the audit is written for does not need them, and the practitioners who do need them work in a different licensure stream that operates outside Bios's scope. This is a boundary worth naming, not a gap.

The rest of this audit walks each gap with file references and recommends only changes that are layerings over the existing architecture.

---

## 1. What Bios already does well, viewed through a Kampo lens

| Kampo principle | How Bios already embodies it | Evidence |
|---|---|---|
| **平人 — the balanced state is person-specific** | The 14-day rolling personal baseline replaces population norms. Yoshimasu Tōdō's clinical methodology — *Ruijuhō* (類聚方) — was empirical and individual: the same symptom in two people called for different formulas because the bodies were different. Bios's `BaselineEngine` instantiates that posture in software, without saying so. | Rolling baseline machinery in `BaselineEngine`; biomarker patterns combine personal z-scores with absolute clinical cutoffs ([BiomarkerConditionPatterns.kt:46-52](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L46-L52)) |
| **Convergence over single findings — 諸症相合 (shoshō sōgō)** | A single sign rarely confirms a Sho; the Kampo specialist looks for the convergence across tongue, pulse, abdomen, and complaint. Bios's `minActiveSignals = 3` for infection onset and `required = true` for biomarker gates is the same posture in a different vocabulary. | [ConditionPatterns.kt:145](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L145); [BiomarkerConditionPatterns.kt:46-52](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L46-L52) |
| **病証一致 — disease-and-pattern accord** (Koho-ha shorthand) | The Koho-ha doctrine was that the same病 (byō, disease) does not require the same治 (chi, treatment) without the same 証 (shō, pattern). Bios's biomarker gate logic — same LDL cutoff applies, but the patient's HRV trend and resting HR are read alongside — operationalises a softened version of this: the lab is necessary but not sufficient, the wearable trend disambiguates. The reader will find this orientation familiar. | [dyslipidemiaSignature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L121-L160); [inflammationSignature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L41-L72) |
| **方証相対 — formula-pattern correspondence** | The Koho-ha tightening: one Sho, one formula. Bios doesn't yet do this, but the medication-annotation surface ([MedicationAnnotationRepo.kt](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt)) is structured exactly the way a Kampo formulary would be carried — name + start date + end date + dose. Adding a TKM extract code to the same surface is a layering, not a rebuild. | [MedicationAnnotation](../../android/app/src/main/java/com/bios/app/model/MedicationAnnotation.kt); reader of meds in alert explanations ([AnomalyDetector.kt:377-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L377-L378)) |
| **判断は患者と医師の手に — evaluation belongs to the patient and the physician, not the instrument** | The manifesto's Principle 7 ("instrument, not coach") is the same posture the senior Kampo clinician takes: the instrument (pulse, abdomen, wearable) reports; the discernment (Sho identification, formula choice) is reserved for the clinician working with the patient. The CI-gated banlist in [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) is the strongest non-judgement guarantee the reader will have encountered in a consumer health product. | [AlertContentPolicy.kt:51-83](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L51-L83); manifesto Principle 7 |
| **治未病 — preventive medicine as the priority of the superior physician** | Imported from the *Su Wen* via the Japanese classical curriculum and central to JSOM-track preventive medicine (which is also a billable NHI category for *未病* state assessment in Japanese clinical practice). The entire Bios detection pipeline is built on this premise — Mishra/Quer/Smarr pre-symptomatic detection cited in [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) is the 21st-century operationalisation. | [ConditionPatterns.kt:127-156](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156); manifesto Principle 1 |
| **婦人科 Kampo gynaecology and the menstrual cycle** | The reproductive database is isolated, encrypted with its own key, and the `menstrualCycleAnomaly` pattern uses BBT — the same anchor the gynaecological Kampo formulas (当帰芍薬散 tōki shakuyaku san, 桂枝茯苓丸 keishi bukuryō gan, 加味逍遙散 kami shōyō san — the so-called "婦人三方", three women's formulas) are dosed against. The data shape is correct; the formula-correspondence layer is the missing piece. | [menstrualCycleAnomaly](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498); ReproductiveDatabase isolation |
| **FHIR Observation as the clinical handoff artefact** | Japanese NHI claims, hospital EMRs (NEC MegaOakHR, Fujitsu HOPE, IBM HospInfo) and PMDA-aligned reporting all converge on FHIR R4. Bios exports LOINC-coded Observations for 12 mapped metric types and ingests 16 biomarker keys. A patient in Osaka can hand a Bios FHIR bundle to a Kampo specialist at Osaka City University Hospital and the bundle is *natively readable* — no translation layer, no proprietary export, no screenshot. This is rare. | FHIR R4 bidirectional support; biomarker ingestion path in [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) |
| **Multi-signal autonomic + behavioural read for psychosomatic patterns** | The signals Bios uses in [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) — sleep architecture, HRV, activity, typing cadence, circadian phase shift, mood drift — are exactly the substrate a Kampo psychosomatic clinic would want to track during a 4–8 week formula trial. The pattern doesn't name 抑肝散 or 加味逍遙散 as candidates, but the data it accumulates is what the practitioner would ask for. | [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) |

The orientation is recognisable. What the reader will want is for the *vocabulary* to catch up to the *posture*.

---

## 2. Diagnostic gaps, ordered by impact

### 2.1 Fukushin (腹診) — no surface, the most uniquely-Japanese gap

This is the load-bearing finding of the audit and the one most distinctive to Kampo specifically — neither Chinese TCM (where abdominal palpation exists but is secondary to pulse and tongue) nor Korean Hanui-hak (where Sasang constitutional medicine takes a different organising frame) elevates fukushin to the diagnostic centrality the Japanese tradition does. The Koho-ha school under Yoshimasu Tōdō made the abdomen the dominant single finding; modern JSOM clinical training carries this forward; the standard examination form used in most Japanese Kampo outpatient clinics has a stylised abdominal diagram on which the practitioner marks the findings.

The classical fukushin findings, with their formula correspondences as taught in the Kitasato / Toyama / Kinki curricula:

| Finding (japanese) | Pronunciation | Location and quality | Lead formula candidates |
|---|---|---|---|
| 心下痞硬 | shinka hikō | Epigastric resistance and tightness on palpation | 半夏瀉心湯, 黄連湯, 大柴胡湯 |
| 胸脇苦満 | kyōkyō kuman | Costal-margin fullness, resistance under the rib edge | 小柴胡湯, 大柴胡湯, 柴胡桂枝湯, 柴胡加竜骨牡蛎湯 |
| 小腹急結 | shōfuku kyūketsu | Tender knot in the left lower quadrant, near the sigmoid | 桃核承気湯, 桂枝茯苓丸 |
| 腹直筋拘急 | fukuchokkin kōkyū | Taut, palpable rectus abdominis bands | 小建中湯, 桂枝加芍薬湯, 四逆散 |
| 振水音 | shinsuion | Audible succussion splash over the epigastrium on light tap | 茯苓飲, 苓桂朮甘湯, 半夏白朮天麻湯 |
| 臍下不仁 | saika funin | Subumbilical softness, weakness, lack of resistance | 八味地黄丸, 牛車腎気丸, 六味丸 |
| 臍上悸 | saijō ki | Palpable pulsation above the umbilicus | 苓桂朮甘湯, 桂枝加竜骨牡蛎湯 |
| 瘀血の腹証 | oketsu no fukushō | Lower-abdominal tenderness, often with tongue darkness | 桂枝茯苓丸, 桃核承気湯, 通導散 |

Bios has *no* representation of any of these. There is no entity in the data model that would hold "the practitioner noted 胸脇苦満 on 2026-04-12, mild on the right." The closest architectural cousin is the [MedicationAnnotation](../../android/app/src/main/java/com/bios/app/model/MedicationAnnotation.kt) — owner-loggable, free-text or coded, with start/end dates and a notes field — and that pattern is exactly what a `FukushinFinding` annotation should clone.

**What a Kampo specialist would want:**

1. A `FukushinFinding` entity mirroring `MedicationAnnotation`'s shape:
   - `findingCode` — enum over the 8–12 standardised findings above (extensible)
   - `severity` — none / mild / moderate / marked (the standard four-grade Japanese clinical convention)
   - `laterality` — none / left / right / bilateral (relevant for 胸脇苦満 and 小腹急結 in particular)
   - `notedAt` — the clinic visit date
   - `notedBy` — free text for the clinic / practitioner name (NEVER auto-shared)
   - `notes` — practitioner free text
2. An owner-loggable entry surface that supports two flows:
   - Manual entry by the owner from a clinic visit summary
   - QR import from a future practitioner-side tool — the JSOM has been discussing standardised electronic Kampo records since 2021, and Bios should be ready to import them when they exist
3. **Bios never infers fukushin.** A camera-based abdominal-photo classifier or a smartphone-accelerometer-based abdominal-resistance estimator would be both technically implausible and a "Bios evaluating the person" surface the manifesto prohibits. The data flow is one-way: clinic → owner → Bios storage → readable on next clinic visit.
4. A pull-side Sho-candidate view that reads the most recent fukushin finding alongside the active condition patterns — e.g. "the most recent fukushin recorded 胸脇苦満 (moderate, right) on 2026-04-12; the current `cardiovascular_stress` pattern overlaps with柴胡加竜骨牡蛎湯's typical fukushin profile" — *without ever recommending the formula*, just surfacing the convergence for the practitioner to evaluate.

**Manifesto check:** owner-controlled storage, pull-side rendering, never auto-inferred, never push-notified. The fukushin record is information the *owner* brings to *the next visit*; Bios is the substrate, not the diagnostician.

**Cost:** one Room entity, one DAO, one minimal screen, one annotation enum. Architecturally indistinguishable from the existing medication-annotation infrastructure. The single highest-leverage change in this audit.

### 2.2 Sho (証) candidate readings — exposing what Bios already half-computes

The Sho is, in the contemporary JSOM curriculum, the unit at which a Kampo prescription is justified. A Sho is not a diagnosis (病, byō) and not a symptom (症, shō without the radical); it is a *pattern that crosses the four examinations and resolves into a specific formula*. The Koho-ha school's empiricism was the position that Sho identification could be tightened: same Sho ⇒ same formula, regardless of speculative theoretical framing. Modern Kampo practice in Japan inherits this orientation — there is less Wuxing (五行) reasoning and less zang-fu elaboration than in Chinese TCM, and tighter formula-pattern mapping.

Bios's `ConditionPattern` is *structurally* the closest analog any West-coast software has to a Sho candidate. It clusters multi-system signals, requires convergence, evaluates against a personal baseline, and resolves into a *specific named pattern with a documented set of references* — and the patterns Bios already authors are not far in shape from the Sho a Kampo specialist would draw from the same signals:

| Bios pattern | Likely Sho candidates (Kampo idiom) | Lead formula candidates |
|---|---|---|
| [chronic_inflammation](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L310-L336) | 瘀血 (oketsu, blood stasis); 気滞 (kitai, ki stagnation); 湿熱 (shitsunetsu, damp-heat) | 桂枝茯苓丸, 加味逍遙散, 防風通聖散 |
| [recovery_deficit](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L339-L363) | 気虚 (kikyo, ki deficiency); 血虚 (kekkyo, blood deficiency); 陽虚 (yōkyo, yang deficiency) | 補中益気湯, 十全大補湯, 人参養栄湯 |
| [cardiovascular_stress](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L187-L208) (jitsu presentation) | 肝陽上亢 (kanyō jōkō, ascendant liver yang) — in Kampo idiom usually rendered as 胸脇苦満 + 心下痞硬 | 大柴胡湯, 柴胡加竜骨牡蛎湯 |
| [cardiovascular_stress](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L187-L208) (kyo presentation) | 心虚 (shinkyo, heart deficiency) with 不眠 (insomnia) | 桂枝加竜骨牡蛎湯, 帰脾湯 |
| [metabolic_drift](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L242-L271) | 湿熱 (shitsunetsu); 痰湿 (tanshitsu, phlegm-damp); 瘀血 (oketsu) | 防風通聖散, 大柴胡湯, 防已黄耆湯 |
| [mental_health_correlate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) (irritable / jitsu) | 肝鬱気滞 (kanutsu kitai) | 加味逍遙散, 抑肝散, 四逆散 |
| [mental_health_correlate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) (depleted / kyo) | 心脾両虚 (shinpi ryōkyo); 気血両虚 (kiketsu ryōkyo) | 帰脾湯, 加味帰脾湯, 十全大補湯 |
| [menstrualCycleAnomaly](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498) | 血虚 (kekkyo); 瘀血 (oketsu); 寒証 (kanshō) | 当帰芍薬散, 桂枝茯苓丸, 温経湯 |
| [biomarker_inflammation_signature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L41-L72) (elevated hsCRP + autonomic shifts) | 瘀血 (oketsu); 湿熱 (shitsunetsu) | 桂枝茯苓丸, 通導散, 黄連解毒湯 |
| [biomarker_prediabetes_signature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L80-L111) | 痰湿 (tanshitsu); 湿熱 (shitsunetsu) | 防風通聖散, 大柴胡湯 |
| [SleepApneaPattern.all](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt#L37) | 痰湿 + 瘀血 overlay | 半夏厚朴湯, 二陳湯 (CPAP / weight loss remain primary, formula is adjunct) |

The mapping above is illustrative, not exhaustive, and the Kampo specialist will sharpen it against their own training school (Kitasato vs. Toyama vs. Kinki teach subtly different formula-Sho correspondences). The structural point is that **a `ShoCandidate` annotation set on each `ConditionPattern`, surfaced through a pull-side Kampo lens, is a thin layer over the existing pattern library**. It does not require Bios to redesign anything — it adds a sidecar to data already computed.

**What a Kampo specialist would want:**

1. A `ShoCandidate` data class:
   ```
   data class ShoCandidate(
       val shoCode: String,         // e.g. "OKETSU", "KIKYO"
       val shoNameJa: String,       // e.g. "瘀血", "気虚"
       val confidence: Double,      // 0..1, derived from how many signals support it
       val supportingSignals: List<MetricType>,
       val formulaCandidates: List<KampoFormulaCode>
   )
   ```
2. An optional `shoCandidates: List<ShoCandidate>` field on `ConditionPattern`, populated at pattern definition time by an authored mapping (not inferred by ML).
3. A pull-side Kampo-lens view that, when the owner opens it, lists active patterns alongside their Sho candidates and formula candidates — explicitly framed as "for discussion with your Kampo specialist," never as a recommendation.
4. **The push-side never surfaces Sho candidates.** Per manifesto Principle 7 and the AlertContentPolicy, evaluative content stays on the pull side.

**Cost:** one data class, one optional field, one screen, one authored mapping table. Same shape as the existing biomarker → condition-pattern wiring.

### 2.3 Kyo-jitsu (虚実) axis — the primary sorting move, unrepresented

Modern Japanese Kampo, after the Koho-ha simplification, runs on a smaller set of axes than full Chinese eight-principle differentiation:

- **Kyo / jitsu (虚 / 実)** — deficiency vs. excess. The first sorting move. A jitsu patient receives draining / clearing formulas (瀉剤, shazai); a kyo patient receives tonifying formulas (補剤, hozai). Misreading the axis is the single most common Kampo prescribing error.
- **Ki / ketsu / sui (気 / 血 / 水)** — the three substances. Disturbance is localised to one or more: ki-tai (ki stagnation), ki-kyo (ki deficiency), ki-gyaku (ki counter-flow); oketsu (blood stasis), kekkyo (blood deficiency); suidoku (water poison / fluid stagnation).
- **Kan-netsu (寒 / 熱)** — cold vs. heat. Often read together with kyo / jitsu: kyo-kan (deficiency-cold) and jitsu-netsu (excess-heat) are common combinations; jitsu-kan and kyo-netsu also occur.
- **Hyō-ri (表 / 裏)** — exterior vs. interior. Inherited from the Shang Han Lun lineage but used more selectively than in TCM; matters most in acute illness staging.

Bios represents none of these. Searching [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) and [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt) finds no enum, no annotation, no field whose semantics correspond to any of these axes.

The mapping the audit would propose is intentionally sparser than the TCM eight-principles mapping, because Japanese Kampo *is* sparser:

| Axis | Bios signal candidates |
|---|---|
| Kyo / jitsu | HRV trend (jitsu trends sympathetic; kyo trends dampened parasympathetic without compensation), active minutes (jitsu owners tolerate load; kyo owners decline rapidly), pulse amplitude (latent in PPG — see §2.7), post-prandial recovery slope |
| Ki (気) | HRV LF/HF balance ([HRV_LF_POWER](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L43), [HRV_HF_POWER](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L44) are already on the bus), typing cadence drift, mood drift score, owner-reported sighing / chest tightness / plum-pit-throat (the standard 気滞 self-reports) |
| Ketsu (血) | Hemoglobin / hematocrit / RBC (anemia pattern already covers kekkyo), nail-bed colour and tongue darkness (owner self-report), menstrual flow regularity (already on the bus for tracking owners) |
| Sui (水) | Lower-extremity oedema (owner self-report), weight fluctuation pattern (already on the bus), 振水音 fukushin finding (§2.1), dizziness on standing (orthostatic BP overlap), urine frequency (owner self-report) |
| Kan / netsu | Skin temperature deviation (already on the bus), owner subjective cold/hot preference (new self-report) |

**What a Kampo specialist would want:**

1. A `KampoAxisReading` data class summarising the kyo-jitsu and ki-ketsu-sui state derivable from the last 7 days of wearable signals plus the most recent owner self-reports, surfaced only on the pull-side Kampo lens.
2. Two new owner-self-report MetricType keys in a new `SELF_REPORT_KAMPO` cluster: cold/hot preference (5-point), bowel/urine pattern (free-text), oedema location (multi-select).
3. The axis readings flow into the Sho candidate scoring from §2.2 — Sho candidates whose typical axis profile contradicts the current reading are downweighted.

**Manifesto check:** Bios stores; the practitioner reads; never push, never evaluation. Owner-controlled self-reports use the existing `SELF_REPORTED` ReadingKind machinery from [Enums.kt:41-45](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L40-L45).

### 2.4 NHI-covered formulary — recognition layer absent

Japan has 148 Kampo extract formulations covered by NHI (the so-called 医療用漢方製剤, *iryōyō kanpō seizai*, "medical-use Kampo preparations"). Each carries a Tsumura number (the dominant brand, e.g. ツムラ24 = 加味逍遙散), and analogous Kotaro, Kracie, and Sanwa codes for the same formula. The PMDA regulates them as pharmaceuticals; the supply chain is industrial; the QA is GMP-grade. From the reader's perspective these are **drugs**, indistinguishable in workflow from any other prescribed medication.

Bios's [MedicationAnnotation](../../android/app/src/main/java/com/bios/app/model/MedicationAnnotation.kt) entity accepts free-text drug names. An owner in Japan on 抑肝散 can record "yokukan san" or "ツムラ54" today. What the system does *not* do is recognise the entry as a Kampo formula, surface its target Sho, or anticipate the expected directional changes on the wearable signals.

The de-noising logic in [AnomalyDetector.kt:377-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L377-L378) is the right place for this. The same false-positive-suppression that prevents a beta-blocker user's bradycardia from reading as hypothyroid signature would suppress:

| Kampo formula | Common indication | Expected wearable direction | Pattern that would false-fire without recognition |
|---|---|---|---|
| ツムラ54 抑肝散 (yokukan san) | BPSD, irritability with sleep disturbance | HRV recovery, sleep stabilisation, activity baseline drift down (sedation in some elderly) | `recovery_deficit` (the HRV recovery direction is correct, but the activity drop might read as deconditioning) |
| ツムラ24 加味逍遙散 (kami shōyō san) | Menopausal autonomic instability | Sleep architecture improvement, HRV stabilisation, mood drift normalisation | `mental_health_correlate` (signal *normalisation* is the desired outcome, not an anomaly) |
| ツムラ41 補中益気湯 (hochū ekki tō) | Post-illness / post-operative fatigue, immune support | Activity tolerance up, HRV recovery | `recovery_deficit` resolution; possibly false `overtraining` signal if owner suddenly increases activity |
| ツムラ100 大建中湯 (daikenchūtō) | Post-op ileus, elderly constipation, abdominal distension | Bowel-frequency increase, transient activity uptick, possibly transient sleep fragmentation | None directly, but the activity uptick could confound recovery readings |
| ツムラ8 大柴胡湯 (daisaiko tō) | Obesity with epigastric tightness, jitsu metabolic-syndrome presentation | Weight trend down, glucose variability down, BP trend down | This *is* the desired pattern — the issue is recognising the formula context so the *rate* of change isn't flagged |
| ツムラ43 六君子湯 (rikkunshi tō) | Functional dyspepsia | Post-prandial fullness self-reports decline; possibly activity uptick | Future GI pattern (§2.6) without recognition |
| ツムラ16 半夏厚朴湯 (hange koboku tō) | Plum-pit-throat sensation, somatic anxiety | HRV recovery, sleep latency improvement, typing cadence stabilisation | `mental_health_correlate` reading the *recovery* as anomalous drift |
| ツムラ7 八味地黄丸 (hachimi jiō gan) | Lower-back ache, polyuria, elderly kidney-yang deficiency | Nocturnal awakening reduction, possibly resting HR drift up (slight) | `recovery_deficit` improvement |
| ツムラ107 牛車腎気丸 (goshajinkitsu gan) | Diabetic neuropathy, elderly cold limbs | Activity tolerance improvement, possibly mild glucose change | Future neuropathy / pain patterns |

**What a Kampo specialist would want:**

1. A `KampoFormulary` lookup keyed by Tsumura number (and aliased to Kotaro / Kracie / Sanwa codes and the formula name in kanji/romaji) — a JSON resource at most a few KB, fully ship-able with the app.
2. Recognition logic in the medication-annotation flow: when an owner enters "抑肝散" or "yokukan san" or "ツムラ54", the system silently tags it as `KampoFormulary["TJ-054"]` and stores both the original text and the canonical reference.
3. The de-noising suppression path in [AnomalyDetector.kt:377-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L377-L378) reads the formula's expected directional effect set and either annotates the alert explanation ("ツムラ54 抑肝散 is annotated as current; sleep stabilisation and HRV recovery are the expected directional effects") or suppresses the alert when the deviation is consistent with the formula's expected effect.
4. **Bios never recommends a formula.** Recognition is for de-noising and for the pull-side Kampo lens; prescription decisions stay with the JSOM-certified specialist.

**Manifesto check:** identical posture to the existing biomedical medication-annotation flow. No new architecture; one lookup table and a small recognition pass.

### 2.5 Japanese demographic and regulatory context — Bios is positioned for it

The Kampo audit is, in part, a market audit. Japan is the natural launching market for Bios for reasons that have not been called out elsewhere in the audit corpus:

- **Population age structure.** 28% of the population is 65+, 10% is 80+. The frailty / geriatric gap acknowledged in [PhysiologyState.FRAILTY_FLAG](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L37) is more centrally important in Japan than in any other OECD market. Kampo geriatrics (老年医学) is a recognised JSOM sub-specialty with substantial RCT-level evidence (yokukan san for BPSD has multiple positive multi-centre Japanese trials; daikenchūtō for post-operative ileus is in the NEJM / J Gastroenterol literature).
- **Smartphone penetration and infrastructure.** OECD top-quartile mobile broadband, near-universal smartphone use across 65+ cohort, the highest per-capita app spend in Asia. Bios's "no Google Play Services, runs on any AOSP" stance is an asset, not a liability — Japanese regulators view degoogled / sovereign Android stacks favourably in healthcare contexts.
- **NHI coverage of both biomedical and Kampo modalities.** The 148-formula Kampo formulary is reimbursed at the same NHI rates as a biomedical drug. A patient handing Bios data to their MD-Kampo specialist is not asking the practitioner to step outside their reimbursement model — both reads are billable in the same visit.
- **PMDA regulatory orientation.** The Pharmaceuticals and Medical Devices Agency has a "Software as a Medical Device" framework (J-SaMD, updated 2024) that treats observation-only, non-diagnostic software substantially more leniently than software that issues diagnostic or therapeutic recommendations. Bios's "instrument, not coach" posture is *almost exactly* the J-SaMD safe-harbour shape. A Japan-targeted regulatory pathway would be considerably less burdensome than the FDA 510(k) route the codebase implicitly benchmarks against in [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt).
- **EMR and FHIR maturity.** Japanese hospital EMRs are converging on FHIR R4 through the厚生労働省 (MHLW) Standard Storage Architecture initiative. Bios's existing FHIR export is more interoperable with a Japanese tertiary hospital today than with most US health systems.
- **Kampo as mainstream medicine.** Over 90% of Japanese MDs prescribe Kampo at least occasionally; ~30% prescribe weekly. This is not an integrative-medicine niche. A Kampo-aware Bios feature is mainstream-medicine feature in this market.

**What this implies for the roadmap:**

1. A Japan-flavoured build configuration — Japanese localisation, kanji-aware text rendering (Japanese-specific font fallback), JSH 2019 BP thresholds in [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), MHLW disclaimer text, Kampo lens enabled by default — would be a *small* deviation from the existing region-config pattern.
2. The frailty pattern set, currently a flagged gap, should be the *primary* pattern set in a Japan-targeted build, not an afterthought.
3. The Kampo lens (§2.1–§2.4) is more justifiable in a Japan-targeted build than in any other regional flavour. It is also harmless to ship globally as an opt-in.
4. The PMDA J-SaMD pathway should be evaluated as a primary regulatory route, not a secondary one.

This is roadmap input, not an architecture gap. Calling it out because no other audit on the codebase has, and because the reader of this audit lives in this market.

### 2.6 Kampo gastroenterology — strong sub-specialty, missing self-report substrate

Of the Kampo sub-specialties, gastroenterology has the strongest contemporary Western-evidence base. Daikenchūtō (大建中湯, ツムラ100) has multiple positive Japanese multi-centre RCTs for post-operative ileus prevention and is routinely prescribed in surgical wards. Rikkunshi tō (六君子湯, ツムラ43) has Japanese trial evidence for functional dyspepsia, including ghrelin-pathway mechanism work. Hange shashin tō (半夏瀉心湯, ツムラ14) is studied for irinotecan-induced diarrhoea. Mashinin gan (麻子仁丸, ツムラ126) is the standard elderly-constipation formula. The Japanese Society of Gastroenterology now includes Kampo recommendations in its functional GI guidelines.

Bios has *strong adjacent signals*: glucose variability via CGM, weight, sleep fragmentation (a post-prandial fatigue proxy), hepatic enzymes via [biomarker_nafld_signature](../../android/app/src/main/java/com/bios/app/alerts/Wave5BiomarkerPatterns.kt#L97), insulin resistance via [biomarker_insulin_resistance_signature](../../android/app/src/main/java/com/bios/app/alerts/Wave5BiomarkerPatterns.kt#L153). What it lacks is the *symptom* layer that anchors Kampo GI Sho identification:

- Epigastric discomfort severity (0–10)
- Post-prandial fullness severity (0–10)
- Reflux / regurgitation frequency (events/day)
- Abdominal bloating severity (0–10)
- Stool frequency (events/day)
- Stool consistency (Bristol scale 1–7)
- Borborygmus frequency (subjective: rare / occasional / frequent)
- Gas / flatulence (subjective)
- Abdominal pain location + severity (free text + 0–10)

**What a Kampo specialist would want:**

1. A `GI_SYMPTOM` self-report key cluster under a new `MetricDomain.GASTROINTESTINAL` (or layered into `METABOLIC` if a new domain is judged too heavy).
2. A daily / per-event journal surface — owner-initiated, never prompted (silence is a feature).
3. A Sho-candidate view that reads GI symptoms alongside metabolic biomarkers and surfaces formula candidates: epigastric tightness + 振水音 + post-prandial fullness ⇒ rikkunshi tō / hange shashin tō candidate; cold lower abdomen + constipation + elderly ⇒ daikenchūtō / mashinin gan candidate; sigmoid tenderness + dark menstrual flow ⇒ keishi bukuryō gan / tōkaku jōki tō candidate.

**Manifesto check:** owner self-report, owner-reviewed; no inferential GI diagnosis; no nutritional coaching.

This is the Kampo gap with the most *evidence-supported adjacency* — the gastroenterology evidence is in mainstream Japanese journals, and the wearable signals exist. The missing piece is the symptom journal, and the existing journal architecture supports it.

### 2.7 Myakushin (脈診) — fewer pulse qualities than Chinese 脉诊, easier to surface

Japanese Kampo pulse diagnosis (脈診, myakushin) is a deliberate Koho-ha simplification of the 28-quality Chinese taxonomy. The standard Kampo curriculum teaches around six to eight pulse qualities, organised primarily along the kyo-jitsu axis:

| Quality | Pronunciation | Description | Axis association |
|---|---|---|---|
| 浮 / 沈 | fu / chin | Floating (felt at light touch) vs. sinking (felt only with pressure) | Exterior vs. interior |
| 数 / 遅 | saku / chi | Rapid (>90 bpm) vs. slow (<60 bpm) | Heat vs. cold |
| 虚 / 実 | kyo / jitsu | Soft, weak, dampened vs. firm, strong, well-resisting | Deficiency vs. excess — the primary axis |
| 滑 | katsu | Slippery, smooth, rounded | Phlegm / pregnancy / dampness |
| 弦 | gen | Taut, wiry, like a guitar string | Liver-qi stagnation, hypertension |
| 細 | sai | Thin, fine | Blood deficiency, depletion |

The set is small enough that a sober mapping from PPG waveform morphology is more tractable than the full Chinese mapping the [TCM audit §2.2](./TCM_POV.md#22-pulse-diagnosis-脉诊--bios-discards-what-it-already-measures) discusses. Bios already computes the underlying features in [PpgSignalProcessor.kt:120-152](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L120-L152) and discards them after rejection-checking:

| Kampo pulse quality | PPG-derivable feature | Already computed? |
|---|---|---|
| 数 / 遅 (saku / chi) | Direct rate | Yes |
| 虚 (kyo) | Low peak amplitude relative to baseline variance | Peak amplitude computed |
| 実 (jitsu) | High peak amplitude, narrow RR CoV, consistent rise time | All computed |
| 滑 (katsu) | Smooth waveform, normal-to-elevated rate, low RR CoV | Smoothness *measured* but not surfaced |
| 弦 (gen) | Sharp upstroke, fast rise time, narrow peak | Rise-time derivable but not stored |
| 細 (sai) | Low peak amplitude, low amplitude variance | Both computed |
| 浮 / 沈 (fu / chin) | Not extractable from optical PPG (requires pressure modulation) | Not extractable |

**Recommendation:** identical in shape to the TCM audit's pulse recommendation but with a smaller mapping table:

1. Preserve waveform morphology features in `PpgResult` (peak-amplitude trimmed mean, peak-amplitude CoV, rise-time mean, rise-time CoV, decay-asymmetry index).
2. A pull-side "pulse character" view that, when the owner opens it under the Kampo lens, lists the closest-matching of the six surfaceable Japanese pulse qualities and explicitly notes that kyo / jitsu pulse character feeds the Sho-axis reading in §2.3.
3. Optical PPG never substitutes for the practitioner's wrist palpation. The Kampo specialist palpates 寸関尺 in three positions at three depths; Bios reports one feature vector from one fingertip. The pull-side view should say so explicitly.

**Cost:** the same data-structure change as for the TCM audit, plus a smaller mapping table.

### 2.8 Kampo psychosomatic medicine — `mental_health_correlate` half-aligned

Kampo psychosomatic medicine (心身医学的漢方治療) is a recognised sub-specialty with its own JSOM teaching tradition (Kobe University, Toho University, Kitasato). The formulas most commonly used:

| Formula | Common indication | Sho profile |
|---|---|---|
| 加味逍遙散 (kami shōyō san, ツムラ24) | Menopausal mood drift, premenstrual irritability, mild depression with autonomic instability | Kyo + kanutsu (liver-qi stagnation) + ki-ketsu deficiency overlap |
| 抑肝散 (yokukan san, ツムラ54) | BPSD, irritability with sleep disturbance, paediatric night-terrors, postpartum agitation | Kanutsu + ki-counter-flow |
| 半夏厚朴湯 (hange koboku tō, ツムラ16) | Plum-pit-throat sensation, somatic anxiety, globus | Kitai + suidoku |
| 桂枝加竜骨牡蛎湯 (keishi-ka-ryūkotsu-borei tō, ツムラ26) | Autonomic dysregulation, insomnia, palpitations, post-traumatic-style anxiety | Kyo, kan, with floating yang |
| 甘麦大棗湯 (kanbaku taisō tō, ツムラ72) | Affective lability, uncontrollable weeping, childhood emotional dysregulation | Kyo, dryness |
| 帰脾湯 (kihi tō, ツムラ65) | Depletion-type depression with insomnia, palpitations, post-illness fatigue | Shinpi ryōkyo (heart-spleen dual deficiency) |
| 柴胡加竜骨牡蛎湯 (saiko-ka-ryūkotsu-borei tō, ツムラ12) | Jitsu-type anxiety with hypertension, irritability, kyōkyō kuman | Jitsu, kanutsu + floating yang |

Bios's [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) is *the* pattern best positioned to anchor Kampo psychosomatic Sho candidates: it integrates sleep architecture, HRV, activity, typing cadence, circadian phase shift, and mood drift. What it lacks is the formula-correspondence layer and the kyo / jitsu pre-sorting that the Kampo psychosomatic clinic uses to decide between (e.g.) 加味逍遙散 (kyo, depleted) and 柴胡加竜骨牡蛎湯 (jitsu, irritable) on otherwise-similar symptom profiles.

**What a Kampo specialist would want:**

1. The Sho-candidate view from §2.2 applied to `mental_health_correlate`, with kyo / jitsu sub-clustering from §2.3.
2. The formula-recognition layer from §2.4 reading active prescriptions and tracking the expected directional changes on the same signals (sleep stabilisation under yokukan san; HRV recovery under hange koboku tō; mood-drift normalisation under kami shōyō san).
3. A pull-side per-formula trial-review screen — when an owner is annotated as on a Kampo psychosomatic formula, the screen shows the wearable signals from the 4–8 weeks before the formula started and the same signals since, framed as data only ("HRV 7-day median: 38 ms pre-start, 47 ms past 4 weeks; sleep latency: 38 min pre-start, 22 min past 4 weeks"). Never an evaluation — the Kampo specialist evaluates, the clinic visit interprets.

**Cost:** the screen is a new view over data that already exists. Sho-candidate and formula-recognition layers are §2.2 and §2.4.

### 2.9 Kampo geriatrics — Japan's demographic + Bios's existing frailty gap

[PhysiologyState.FRAILTY_FLAG](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L37) acknowledges the frailty population as a class for which the rolling personal baseline produces different signals; the primary-care audit §2.7 flagged it as an underserved population. In Japan, this is *the* largest patient cohort, and Kampo geriatrics is the sub-specialty with the most active prescribing.

The frailty-relevant formulas:

| Formula | Indication | Wearable signal expectation |
|---|---|---|
| 補中益気湯 (hochū ekki tō, ツムラ41) | Post-illness fatigue, immune support, sarcopenia | Activity tolerance up over 4–8 weeks |
| 十全大補湯 (jūzentaihō tō, ツムラ48) | Anemia + fatigue + post-chemotherapy recovery | Hemoglobin trend up, activity recovery, HRV recovery |
| 人参養栄湯 (ninjin'yōei tō, ツムラ108) | Cachexia, sarcopenia, frail elderly with cognitive decline | Activity + appetite up, cognitive probe stabilisation |
| 牛車腎気丸 (goshajinkitsu gan, ツムラ107) | Diabetic neuropathy, elderly cold limbs, lumbago | Activity tolerance, possibly reduced nocturia |
| 八味地黄丸 (hachimi jiō gan, ツムラ7) | Lower-back ache, polyuria, mild diabetic complications | Nocturnal awakenings down, BP stabilisation |
| 麻子仁丸 (mashinin gan, ツムラ126) | Elderly habitual constipation | Bowel frequency up (with §2.6 GI surface) |
| 抑肝散 (yokukan san, ツムラ54) | BPSD, dementia-spectrum agitation, REM sleep behaviour disorder | Sleep stabilisation, agitation event frequency down |

The audit's recommendation here is to *combine* the existing frailty PhysiologyState handling with the Kampo formula recognition (§2.4): when an owner is in FRAILTY_FLAG state and is annotated as on yokukan san, the alert thresholds for the relevant patterns (notably `recovery_deficit` and sleep patterns) should be tuned to the formula's typical effect window, not the standard adult baseline. This is a layering — `excludedStates` already exists on `ConditionPattern`; an analogous `formulaModifiers: Map<KampoFormulaCode, ThresholdShift>` would be the symmetric construct.

### 2.10 Sho-axis self-report and 問診 (monshin) digital intake

The Japanese Kampo consultation traditionally opens with monshin — structured questioning across cold/hot preference, sweating pattern, thirst, appetite, bowel, urine, sleep, menstruation. Standardised monshin forms exist (the Terasawa monshin chart and the JSOM-affiliated Tsutani / Takegoshi versions) and many Japanese Kampo clinics distribute them as paper intake forms.

Bios is well-positioned to surface a digital monshin: a one-time intake the owner completes on first install (or before a clinic visit), revisable, owner-controlled, never push-prompted. The fields are stable and finite; the data shape matches the existing `SELF_REPORTED` ReadingKind in [Enums.kt:41-45](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L40-L45).

The monshin items map onto the kyo-jitsu and ki-ketsu-sui axes (§2.3) and feed the Sho-candidate scoring (§2.2). A standardised export of the completed monshin alongside the FHIR Observation bundle would let the Kampo specialist read the patient's wearable timeline *and* their own self-described diathesis on the first visit — substantially compressing the diagnostic timeline.

**Cost:** a one-time form, JSON-defined, with the question set drawn from the JSOM-affiliated monshin standards. Owner-controlled storage, exportable as PDF or as a sidecar to the FHIR bundle.

### 2.11 What this audit does not recommend Bios implement

The reader the audit is written for is an MD-Kampo dual-licensed specialist. They do not practice acupuncture, anma, shiatsu, or judo-seifuku — those are independently licensed paramedical professions (hari-kyū-shi, anma-shiatsu-shi, judo-seifuku-shi) that operate outside the MD scope. Bios should not implement:

1. **Acupoint or meridian palpation surfaces.** Out of MD-Kampo scope; the practitioners who use these are on a different licensure track and have different software needs.
2. **Moxa (お灸, okyū) tracking.** Same boundary.
3. **Anma / shiatsu / massage session logging.** Same boundary.
4. **Judo-seifuku bone-setting records.** Same boundary; this is an osteopathic-adjacent paramedical profession with its own clinic-management software ecosystem in Japan.
5. **Automated Sho diagnosis.** The Sho is the unit of *clinical* reasoning, and the Koho-ha empiricism that defines modern Japanese Kampo holds the Sho as the practitioner's discernment. Bios may surface candidates; it must never resolve a Sho.
6. **Formula recommendation engine.** Even on the pull side. Formula choice is the most senior part of Kampo practice, requires the fukushin the software does not have, and is the practitioner's job under PMDA-aligned reasoning.
7. **A "Kampo wellness score" composite.** The same anti-pattern the [DATA_MODEL.md](../DATA_MODEL.md) guard against epigenetic-clock composites refuses, applied to Sho: no `KampoBalanceIndex`, no `gokyo-rokufu` numeric composite.
8. **Tongue-image classifier.** The [TCM audit §2.3](./TCM_POV.md#23-tongue-diagnosis-舌诊--capture-surface-absent) makes the same call. In the Japanese curriculum tongue diagnosis (舌診, zesshin) is secondary to fukushin and pulse; the practitioner sees the tongue in clinic. A tongue-photo journal in an isolated SQLCipher database is *defensible* if Bios chooses to surface it, but it is lower priority for the Kampo reader than fukushin recording (§2.1).

These eight negatives are the load-bearing constraints on the recommendations.

---

## 3. Where Bios and Kampo principles agree

### 3.1 "Instrument, not coach" ≈ 平脈辨証 / 平腹辨証 — the instrument reports, the practitioner discerns

The Koho-ha methodology of Yoshimasu Tōdō was explicitly empirical: *方証相対* (hō-shō-sōtai, formula-pattern correspondence) was tightened *because* speculative theoretical framing was held to obscure the clinical observation. The practitioner is the discerning agent; the wrist, the abdomen, the complaint are *what is read*. Bios's principle 7 ("Bios is the instrument; the owner reads it") is the same posture. A pull-side Sho-candidate view that lists candidates without resolving them is the correct shape; a push-side Sho diagnosis would violate both the manifesto and the Koho-ha posture.

### 3.2 "Silence is a feature" ≈ 大医精誠 (Sun Simiao via Kampo's classical inheritance)

The Japanese Kampo curriculum carries Sun Simiao's *On the Absolute Sincerity of Great Physicians* as part of the classical literature, in addition to the *Shang Han Lun* and the *Jin Gui Yao Lue* that anchor the Koho-ha formulary. The senior Kampo clinician is characterised by clinical reserve — speaking when something is clear, refusing to fill space. The [AlertContentPolicy](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) banlist is the software analogue.

### 3.3 "Personal baseline over population norm" ≈ 平人 / 個別化医療

The classical concept of 平人 (heijin) is *the balanced state for this individual*, against which deviation is judged. Modern Japanese clinical medicine has, partly under Kampo influence, embraced 個別化医療 (kobetsuka iryō, personalised medicine) as a watchword. Bios's 14-day rolling baseline is the operational form. The reader will recognise this as the orientation they apply at the bedside.

### 3.4 "Free to all" ≈ 国民皆保険 + manifesto Principle 3

Japan's 国民皆保険制度 (kokumin kai-hoken seido, universal health coverage) is the structural commitment to medicine as a right rather than a commodity, including Kampo on the same coverage terms as biomedical drugs. Manifesto Principle 3 (full health intelligence for everyone, never gated by payment) is the same commitment in software. A contribution-popup that gated features would violate the alignment as severely as it would violate the manifesto; the alignment is not coincidental.

### 3.5 治未病 — preventive medicine as the priority of the superior physician

The full passage from *Su Wen* Chapter 2 is part of the standard Kampo curriculum's classical readings:

> 是故聖人不治已病治未病、不治已亂治未亂、此之謂也。

The Japanese reading (yomi) renders it: *seijin wa sude ni byō naru o chisezu, imada byō narazaru o chi su*. The senior physician treats the not-yet-disease. The Bios detection pipeline is built on this premise; the manifesto's principle 1 (prevention over reaction) is its modern restatement. The Kampo reader will recognise the orientation immediately — Mishra/Quer/Smarr pre-symptomatic detection is the 21st-century instrument of 治未病.

---

## 4. Prioritised recommendations

**Tier A — high signal, small engineering cost, manifesto-compatible, ship-able as a Japan-flavour build first**

1. **`FukushinFinding` annotation entity + minimal entry screen.** (§2.1) The single most uniquely-Japanese gap; architecturally identical to the existing `MedicationAnnotation` infrastructure. One Room entity, one DAO, one screen, one enum. Closes the gap that prevents Bios from being a fully readable Kampo handoff artefact.
2. **`KampoFormulary` recognition layer over the existing medication-annotation surface.** (§2.4) Tsumura-coded lookup table + recognition pass + de-noising annotation in alert explanations. JSON resource; no architectural change.
3. **Sho-candidate sidecar on `ConditionPattern` + pull-side Kampo lens.** (§2.2) One data class, one optional field on `ConditionPattern`, one authored mapping table from existing patterns to Sho / formula candidates, one pull-side view. Push-side never surfaces Sho candidates.
4. **Preserve PPG waveform morphology features in `PpgResult`.** (§2.7) Identical to the TCM audit's recommendation and shared with it. Smaller Japanese pulse-quality mapping table; same data-class change.

**Tier B — modest engineering, opens the Kampo lens**

5. **Kyo-jitsu / ki-ketsu-sui axis derivation view.** (§2.3) Two new owner self-report keys (cold/hot preference, oedema), a `KampoAxisReading` data class summarising the axis state from existing wearables + new self-reports, pull-side only.
6. **GI symptom self-report keys.** (§2.6) New `MetricDomain.GASTROINTESTINAL` (or extension of `METABOLIC`) with Bristol scale, post-prandial fullness, regurgitation, bloating. Owner-initiated journal surface.
7. **Digital monshin intake form.** (§2.10) JSON-defined structured intake using the JSOM-affiliated monshin standards, owner-revisable, exportable as PDF sidecar to the FHIR bundle.
8. **Kampo formula trial-review screen.** (§2.8) Pull-side per-formula screen showing pre-start vs. post-start wearable signal trends for owners on annotated Kampo prescriptions. Data only, never evaluation.

**Tier C — substantial body of work, but high value in the Japanese market**

9. **Japan-flavour build configuration.** (§2.5) JSH 2019 BP thresholds in [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), MHLW disclaimer text, Japanese localisation, Kampo lens enabled by default, frailty patterns primary rather than peripheral.
10. **Frailty + Kampo-geriatric formula-modifier extension on `ConditionPattern`.** (§2.9) A symmetric construct to `excludedStates`: `formulaModifiers: Map<KampoFormulaCode, ThresholdShift>` that tunes pattern thresholds when an owner is in FRAILTY_FLAG state and annotated as on a Kampo geriatric formula.
11. **PMDA J-SaMD regulatory-pathway evaluation.** (§2.5) Roadmap item, not engineering. The Japanese SaMD framework is more accommodating to observation-only software than the FDA 510(k) route; worth scoping properly.

**Tier D — defensible-to-defer boundary**

12. **Tongue-photo journal in a separate SQLCipher database.** Same as [TCM audit Tier D](./TCM_POV.md#5-prioritised-recommendations). Lower priority for the Kampo reader than fukushin recording, since the Japanese curriculum places fukushin above zesshin.

**Do not adopt**

- Automated Sho diagnosis.
- Automated fukushin inference from smartphone accelerometer / camera. (Owner-logged from clinic visits only.)
- Composite "Kampo balance score."
- Push-notification alerts from the Kampo lens.
- Herbal-formula recommendation engine.
- Acupoint, meridian, anma, shiatsu, judo-seifuku surfaces (out of MD-Kampo scope).

---

## 5. Summary line for the project

> Bios is the *closest* a piece of consumer health software has come to the orientation a Japanese Kampo specialist holds at the bedside — personal-baseline empiricism, multi-signal convergence, silence as a clinical virtue, refusal to evaluate the person. The biomedical idiom is *not* foreign to the reader: it is the same idiom they use during the first half of the consultation, and Bios's FHIR Observation bundle is more interoperable with a Japanese tertiary hospital than with most US health systems. The gaps are in the *second half* of the consultation — no fukushin record, no Sho candidate surface, no kyo-jitsu axis, no recognition of the 148 NHI-covered Kampo extracts in the medication-annotation flow. None of these require a redesign; all of them are layerings over architecture that already exists, sized like the existing `MedicationAnnotation` and `PhysiologyState` machinery. The reader's distinctive contribution to East Asian medicine — fukushin — is the load-bearing single gap, and it is also architecturally the smallest. The most uniquely-Japanese feature Bios could ship is also the easiest one.
