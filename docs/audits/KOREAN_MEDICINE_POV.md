# Korean Medicine Audit — Bios Through the Sasang Lens of a 한의사 (Hanui-sa, KMD)

**Scope:** Bios's clinical reach as an on-device monitoring instrument, evaluated by a Korean Medicine Doctor (한의사 / KMD) trained in the standard six-year curriculum at a Korean Medicine college (한의과대학) with specialty in **Sasang constitutional medicine (사상의학)** — Lee Je-ma's 1894 system codified in the *Donguisusebowon* (동의수세보원, *Longevity and Life Preservation in Eastern Medicine*) and elaborated through the Society of Sasang Constitutional Medicine, the Association of Korean Medicine (대한한의사협회), and Korean National Health Insurance reimbursable practice.
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Korean Medicine (Hanui-hak) — Sasang four-type constitutional diagnosis (사상체질), Saam acupuncture (사암침법), Korean herbology (본초), traditional Korean gynaecology and paediatrics, and the institutional context of parallel KMD / MD licensing in South Korea where KMDs routinely read modern lab panels alongside pulse and facial diagnosis. Not a syncretist audit, and explicitly **not** a transposition of the existing TCM audit — Korean medicine has been a distinct system from Chinese medicine since at least the late Joseon period, and the differences are load-bearing.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [docs/audits/MEDICAL_SPECIALTIES_WORLDWIDE.md](MEDICAL_SPECIALTIES_WORLDWIDE.md) (§7), [docs/audits/TCM_POV.md](TCM_POV.md) (to ensure distinct treatment), [docs/audits/MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [CircadianConditionPattern.kt](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt).

This audit asks one question: **could a 한의사 in a Korean clinic — paid through National Health Insurance, sharing patients with internists and cardiologists from the MD side of the parallel licensure system — use Bios as an adjunct to constitutional-typing-informed practice?** It does not ask Bios to become a Korean medicine application. It asks where, viewed through the Sasang frame and the broader Hanui-hak diagnostic register, Bios's existing signals are usable, where they are mute, and where the architecture leaves room without violating the manifesto.

The TCM audit ([docs/audits/TCM_POV.md](TCM_POV.md)) has already evaluated the codebase against the eight-principles (八纲) / zang-fu (脏腑) / six-pathogens (六淫) frame that Korean medicine shares with the Chinese tradition. **This audit does not repeat that ground.** It concentrates on the four things that make Korean medicine *not* TCM in a rebrand: Sasang constitutional medicine, Saam acupuncture's five-element axis, Korean herbology's distinct geography and ginseng-centric pharmacopoeia, and the institutional reality of parallel KMD-MD licensure that makes FHIR lab import genuinely usable in a Korean clinic.

---

## Executive summary

Bios meets the institutional reality of Korean medicine practice better than it meets any other classical-medicine system audited so far. The reason is structural, not stylistic. South Korea is one of the only jurisdictions in the world where a non-biomedical traditional system has a fully parallel licensing track to MD with comparable training duration (six years), full National Health Insurance coverage for many indications, and a working professional norm of KMDs reading modern lab panels alongside pulse and facial diagnosis. The [Phase 8.6 lab biomarker inbound surface](../ROADMAP.md#86-lab--biomarker-inbound-surface-foundation-shipped) — 30+ FHIR-importable lab values across lipid, thyroid, CBC, renal, hepatic, glycemic-extended, endocrine, micronutrient, and epigenetic panels, all mapped to LOINC — is exactly the surface a KMD already uses in their own clinic-management system. A KMD does not have to choose between Bios's vocabulary and their training; the lab-import path is shared and the diagnostic vocabulary layered atop is each clinician's call.

What is missing is the layer Korean medicine actually contributes that distinguishes it from the broader East Asian tradition: **the four-type constitutional model (사상체질).** Bios has no `Sasang` enum. No `Constitution` selector. No way for a Taeumin (太陰人) owner to mark themselves so that their constitutionally higher BMI does not fire a `cardiorespiratory_deconditioning` pattern, and no way for a Soumin (少陰人) owner to mark themselves so that their constitutionally lower body temperature does not fire a `hypothyroid_signature` proxy on the wearable side. The 14-day rolling personal baseline catches some of this implicitly, as it does for every constitutional system, but **constitution is lifelong and pattern-coverage is categorical**; the rolling baseline cannot substitute for the gating decision of "which patterns are clinically meaningful for this owner."

Ordered by clinical impact in Korean medicine terms, the gaps are:

1. **Sasang constitutional type (사상체질) is unmodelled, and this is the single defining feature of Korean medicine.** Lee Je-ma's 1894 four-type system — Taeyangin (太陽人), Soyangin (少陽人), Taeumin (太陰人), Soumin (少陰人) — is the irreducible diagnostic axis of the Korean tradition. Each type has distinct physiological tendencies (RHR baseline, body-temperature baseline, BMI baseline, digestive function, autonomic tone), distinct disease vulnerabilities, and distinct herbal formularies. Bios has no `Sasang` enum, no constitutional selector in Settings, and no `excludedConstitutions` companion to the existing [excludedStates](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L36) gating field on `ConditionPattern`. This is the most consequential single absence from a Korean-medicine standpoint — far more than the absence of zang-fu pattern names (which is a TCM gap) or the absence of eight-principles axes (also TCM-side). A KMD's first diagnostic question — "what is this owner's 체질?" — has no answer in the schema.

2. **Sasang body-type tendencies that *would* map onto existing Bios signals are not surfaced.** The four constitutions have well-documented physiological tendencies, and Bios already captures the inputs:
   - **Taeumin (태음인 / 太陰人)** — strong digestive absorption, slower metabolism, tendency to higher BMI, prone to metabolic syndrome and respiratory illness. Bios has [BODY_MASS, BODY_FAT_PCT, LEAN_MASS, BODY_WATER_PCT, GLUCOSE_CV, GLUCOSE_MAGE, GLUCOSE_TIME_IN_RANGE, RESPIRATORY_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) — every signal needed to project a Taeumin metabolic-burden view.
   - **Soumin (소음인 / 少陰人)** — weak digestion, tendency to lower core body temperature, lower digestive function, prone to GI complaints and cold-pattern symptoms. Bios has [SKIN_TEMPERATURE, SKIN_TEMPERATURE_DEVIATION, BASAL_BODY_TEMPERATURE, GLUCOSE_CV, RESTING_HEART_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) — every signal needed to flag Soumin cold-pattern drift.
   - **Soyangin (소양인 / 少陽人)** — hot constitution, higher resting HR tendency, sympathetic-leaning autonomics, prone to anxiety, ulcer, hypertension. Bios has [RESTING_HEART_RATE, HEART_RATE_VARIABILITY, LF_HF_RATIO, STRESS_SCORE, PARASYMPATHETIC_TONE, BLOOD_PRESSURE_SYSTOLIC/DIASTOLIC, MOOD_DRIFT_SCORE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) — every signal needed to surface a Soyangin sympathetic-load view.
   - **Taeyangin (태양인 / 太陽人)** — rare (often cited as <0.5 % of the population), distinct pulmonary-pattern presentation with descending-qi pathology. Wearable signature would emphasise respiratory rate variability and posture/orthostatic patterns. Bios's coverage here is thinner but [RESPIRATORY_RATE, BLOOD_PRESSURE_SYSTOLIC/DIASTOLIC, FALL_EVENT](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) are present.

   None of these projections exists in the codebase. The data is on the bus; the constitutional lens is not.

3. **The hot/cold polarity (한열 / 寒熱) that organises Sasang prescription is invisible.** Korean herbal formulations are prescribed against constitutional cold/hot tendency: Soumin formulae are warming (ginger, ginseng, atractylodes, cinnamon), Soyangin formulae are cooling (rehmannia, scrophularia, gardenia). The owner's *current* hot/cold drift, plotted against their *constitutional* hot/cold baseline, is the practitioner's primary axis for adjusting prescription between visits. Bios captures [SKIN_TEMPERATURE_DEVIATION](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L69) — the literal substrate — and never projects it onto this axis. A pull-side "constitutional hot/cold drift today" view would close this gap with essentially no new data collection.

4. **Saam acupuncture (사암침법) — the distinctly Korean five-element acupoint system — has no representation.** This is a different beast from the TCM-side meridian-time / zang-fu point system. Saam acupuncture, attributed to the Buddhist monk Saam Doin (사암도인), works with only five points per meridian and uses five-element generative / control relationships (生剋, 상생/상극) to select tonification (補) or sedation (瀉) point combinations. The clinical workflow involves no diagnostic data Bios collects, but the *follow-up* — owner reports of warmth-after-needling, pulse-quality change at 24h, sleep-quality change at 72h — could plausibly use Bios as the objective complement to the practitioner's between-session palpation memory. No such surface exists.

5. **Korean herbology (본초학) prescription tracking has no place to land.** Korean clinical practice uses a partially-overlapping but distinct pharmacopoeia from Chinese tradition — heavy use of 인삼 (Korean red ginseng / Panax ginseng C.A. Meyer, climate-distinct from Chinese ginseng), 황기 (Astragalus), 당귀 (Korean angelica, *Angelica gigas* — a different species from the Chinese *Angelica sinensis*), and formulations adapted to the Korean peninsula's climate and soil. The existing [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L33-L34) surface accepts free-text and so technically *can* hold a Korean formula name, but the alert-explanation builder does not recognise these strings as formulations targeting particular constitutional patterns (Soyangin-yang-decreasing, Taeumin-qi-tonifying, Soumin-warming) and so cannot annotate the wearable observation with "this formula targets the pattern you're seeing."

6. **Korean tongue and facial diagnosis (망진/체형 진단) for Sasang typing has no capture surface, and this is principled but total.** Sasang constitutional typing in clinic relies on three combined examinations: 용모사기 (facial and bodily appearance — shoulder vs. hip development is the iconic Sasang signature), pulse (맥진), and voice / temperament (성정 / 사기). The body-frame distinction — Taeyangin and Soyangin have stronger upper bodies, Taeumin and Soumin have stronger lower bodies — is read by the practitioner's eye in seconds and is not derivable from wearables. Bios has no image-capture path for any of this, and the codebase's posture on imagery is deliberately conservative (see [ReproductiveDatabase](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L464-L474) isolation logic — the same principled reasoning applies here). The omission is therefore principled rather than oversight, but it does mean Bios cannot self-classify a constitutional type and never should. The constitution selector recommended in §1 must be **owner-set after their KMD has typed them**, never inferred.

7. **The Donguibogam (동의보감) seven-emotion + four-temperament etiology framework is absent.** Korean medicine's distinctive contribution to emotion-as-etiology builds on the Chinese 七情 but adds Sasang temperament correlations: 哀 (sorrow, characteristic of Taeyangin), 怒 (anger, characteristic of Soyangin), 喜 (joy / liveliness, characteristic of Soumin), 樂 (pleasure / steadiness, characteristic of Taeumin). The four temperaments are diagnostic *for the constitution* — the practitioner uses dominant emotional tendency as one input to constitutional typing — and they are *etiological* for each constitution's characteristic disease patterns (Soyangin's anger-driven hypertension and ulcer, Soumin's grief-driven digestive collapse). Bios has [MOOD_DRIFT_SCORE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L216) as a generic mood-drift composite but no four-temperament logging surface and no constitutional-temperament linkage in the alert explanation builder.

8. **The Korean-style pulse refinements (Sasang-specific 맥상) on the PPG waveform are not surfaced.** Sasang pulse diagnosis pays attention to constitution-specific qualities — Taeyangin is read as having a characteristic 부완 (floating-relaxed) quality on the right cubit position, Soyangin a more 활삭 (slippery-rapid), Taeumin a 침완 (deep-relaxed), Soumin a 미세 (faint-thin). [PpgSignalProcessor](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) computes peak amplitude, peak-amplitude CoV, RR CoV, and waveform morphology — the same features the TCM audit (§2.2) noted are discarded after rejection-checking. For a KMD, the discarded features carry constitutional weight: a faint-thin trace on a Soumin patient is *expected* (and a *strong* trace would prompt re-examination of the constitutional classification), where a faint-thin trace on a Soyangin patient is a red flag for sympathetic depletion. The TCM audit's recommendation to preserve these features in `PpgResult` applies with extra force here because the practitioner's *interpretation* of the same waveform is constitution-dependent.

9. **National Health Insurance share-flow has no first-class export shape for KMDs.** South Korea's NHIS reimburses many KMD services, and clinic-to-clinic data sharing happens through the same EMR backbones (mainly EzPlus, EzMR, and similar Korean-language systems) that the MD side uses. Bios's [FHIR R4 export](../ROADMAP.md#84-sleep-derivations-latency--components-shippped) and [FHIR R4 import](../ROADMAP.md#86-lab--biomarker-inbound-surface-foundation-shipped) are the right artefact for the MD-side workflow, but a KMD-side export would naturally want to surface (a) constitutional type, (b) the constitutional-lens views of recent anomalies, and (c) Korean-medicine-vocabulary formulation annotations — none of which fit a standard FHIR `Observation` with LOINC coding. A parallel "Korean Medicine practitioner share" surface (PDF or Korean-language structured text, not FHIR) would be the analogous artefact.

10. **Where Bios *aligns* with Korean medicine practice it does so quietly and well, and deserves naming.** The personal baseline as the unit of comparison ([BaselineEngine](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L146-L166) rolling-window logic) is the operational form of *수증치료* — "adjust treatment to the individual" — and is also the only sane substrate on which constitutional-typing could rest, since each Sasang type's normative ranges differ. The [Phase 8.6 biomarker import + clinical-band classification](../ROADMAP.md#86-lab--biomarker-inbound-surface-foundation-shipped) makes Bios a legitimate adjunct to a KMD-MD collaborative workflow; the manifesto's *"evaluation belongs to the owner"* posture is aligned with the Korean medicine norm that the owner participates actively in 養生 (yang-saeng — life-cultivation) rather than receiving a diagnosis from on high; and the silence-as-feature posture matches the institutional Korean medicine norm that recommendations follow the constitution and the season rather than a fixed cadence.

The rest of this audit walks each gap and aligned strength with concrete file references, then closes with prioritised recommendations.

---

## 1. What Bios already does well, viewed through the Sasang lens

| Korean medicine principle | How Bios already embodies it | Evidence |
|---|---|---|
| **수증치료 (sujeung chiryo) — adjust treatment to the individual constitution** | The 14-day rolling personal baseline replaces population norms. A Soumin owner's constitutionally lower body temperature is *captured* as their baseline (even though Bios does not know it is constitutional). A Soyangin owner's constitutionally higher RHR is similarly captured. The architecture is *ready* for a constitutional layer to sit on top — the baseline does much of the work that constitutional adjustment otherwise would. | [BaselineEngine](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L146-L166) rolling-window logic; biomarker patterns combine personal-baseline z-scores with absolute clinical cutoffs at [BiomarkerConditionPatterns.kt:46-52](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L46-L52) |
| **未病 (mibyeong) — treat the not-yet-illness** | The entire detection pipeline is pre-symptomatic by design. The same classical principle that organises 養生 / 治未病 in the Chinese tradition is foundational in Korean medicine — the *Donguibogam* opens with the *Naegyeong* (內景) chapter on cultivation before disease. The Mishra/Quer/Smarr citations on [infection_onset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) are the 21st-century operationalisation of this. | [infection_onset pattern + citations](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156); manifesto Principle 1 |
| **KMD-MD parallel-licensure interoperability** | The [FHIR R4 import surface](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) and the 30+ LOINC-mapped biomarker keys ([MetricType.kt:146-209](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L146-L209)) match exactly what a KMD already does in clinic — read modern lab panels alongside pulse and facial diagnosis. A KMD does not have to choose between Bios's lab-reading vocabulary and their training; they share. | [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt); LOINC mappings in `FhirExporter` per the Phase 8.6 description |
| **사진 (sajin) — four examinations, convergence reasoning** | Pattern convergence (`minActiveSignals = 3` for infection onset; `required = true` for biomarker gating rules) is exactly the *convergence across multiple examinations* a KMD uses. A single elevated RHR is not a diagnosis; RHR + HRV-drop + skin-temp elevation + sleep-disruption is. The architecture matches the clinical-reasoning shape, even where the vocabulary is biomedical. | [ConditionPatterns.kt:145](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L145); [BiomarkerConditionPatterns.kt:46-52](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L46-L52) |
| **여성과 — distinct gynaecological reasoning** | The reproductive database is isolated with its own SQLCipher key and wipe path, and the [menstrualCycleAnomaly pattern](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498) — BBT, HRV, sleep across cycle phase — operationalises what Korean 한방부인과 (gynaecology) reads as 충임불조 (Chong-Ren channel disharmony) and constitution-specific cycle patterns (Soumin's tendency to amenorrhea-from-cold, Soyangin's tendency to short cycles). The isolation also matches the discretion norm in Korean clinical practice. | [ReproductiveDatabase](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L465-L475) isolation; [menstrualCycleAnomaly](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498) |
| **소아과 — paediatrics as a distinct discipline** | The [PhysiologyState.PAEDIATRIC](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L38) state exists and gates `excludedStates` on adult-only patterns, mirroring the Korean medicine norm that paediatric (한방소아과) practice is its own specialty with its own dose-and-constitution rules. | [PhysiologyState.kt:38](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L38); [ConditionPattern.excludedStates](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L36) |
| **判斷은 본인의 것 — evaluation belongs to the owner** | The [AlertContentPolicy](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) CI-gated banlist on "you should / you need to / streak / level up" is the strongest non-judgement guarantee I have seen in consumer health software. A KMD trained in the classical posture — the practitioner reads, the patient is the agent of their own restoration — recognises this register. The fact that *Sasang* practice in particular emphasises individualisation against constitutional grain rather than against population norm aligns naturally. | [AlertContentPolicy.kt:51-83](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L51-L83); manifesto Principle 7 |
| **순응자연 — accord with nature (and season)** | The [circadianDisruption pattern](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt) reads ambient-light irregularity + sleep degradation as a coherent misalignment signal. A KMD reads the same data as misalignment with the constitution's natural rest-rise pattern (Soyangin's tendency to evening over-activation, Soumin's tendency to early-morning fatigue). The frame is alignable, even if Bios does not yet expose seasonal context. | [CircadianConditionPattern.kt:33-61](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt#L33-L61) |
| **체질별 응급 임계치 — constitution-specific emergency thresholds** | The [EmergencyVitalPatterns](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) hard-cutoff vital-sign patterns (SpO2 ≤85 %, hypoglycemia, extreme bradycardia / tachycardia) escalate to `URGENT` regardless of personal baseline. This is correct: at these thresholds, constitution does not modify the clinical urgency, and the *Donguibogam* tradition is equally clear that life-threatening signs (亡陽 / 亡陰 — yang-collapse / yin-collapse) require immediate intervention regardless of constitution. The pattern shape is constitution-agnostic at exactly the right places. | [EmergencyVitalPatterns.kt:42-80](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L42-L80) |

These are *not* parity wins. These are places where Bios's architecture — written entirely in biomedical idiom — happens to align with the operational structure of a Sasang-informed Korean medicine clinic. A KMD reading the codebase will recognise the orientation even where the vocabulary is unfamiliar.

---

## 2. Diagnostic gaps, ordered by impact in Korean medicine terms

### 2.1 Sasang constitutional type (사상체질) is unmodelled — the load-bearing absence

This is the single defining feature of Korean medicine as distinct from the broader East Asian tradition, and its absence in Bios is therefore the largest single Korean-medicine-side gap. To be concrete about what is missing: there is no `Sasang` enum at `com.bios.app.sasang` or `com.bios.app.physiology`. There is no constitutional selector in Settings. The [ConditionPattern.excludedStates](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L36) field gates patterns by `PhysiologyState` (pregnancy, postpartum, paediatric, athlete, frailty) but has no `excludedConstitutions` companion. The [AnomalyDetector](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L43-L44) filter on `applicablePatterns()` reads only `physiologyState`.

The four constitutions in the canonical Lee Je-ma framework, with their classical Chinese transliterations and the dominant physiological tendencies documented in the Korean Society of Sasang Constitutional Medicine guidelines:

| Constitution | Hanja | Population share* | Dominant tendencies | Bios signals that would calibrate |
|---|---|---|---|---|
| Taeyangin (태양인) | 太陽人 | ~0.03–0.5 % | Strong upper body, weak lower body; ascending qi excess, descending qi deficient; distinct pulmonary-pattern presentations; rare in clinic | RESPIRATORY_RATE variability, posture/orthostatic falls, BP supine/standing |
| Soyangin (소양인) | 少陽人 | ~30 % | Strong spleen / weak kidney; hot constitution; sympathetic-leaning; prone to ulcer, hypertension, anxiety; angry temperament | RESTING_HEART_RATE, HEART_RATE_VARIABILITY, LF_HF_RATIO, STRESS_SCORE, BP, MOOD_DRIFT_SCORE |
| Taeumin (태음인) | 太陰人 | ~50 % | Strong liver / weak lung; cold-and-damp tendency; higher BMI; slower metabolism; prone to metabolic syndrome, respiratory illness; steady temperament | BODY_MASS, BODY_FAT_PCT, LEAN_MASS, GLUCOSE_CV, GLUCOSE_MAGE, GLUCOSE_TIME_IN_RANGE, RESPIRATORY_RATE, ALT/AST/GGT |
| Soumin (소음인) | 少陰人 | ~20 % | Strong kidney / weak spleen; cold constitution; lower core body temperature, weak digestion; prone to GI complaints, fatigue, depression; sorrowful temperament | SKIN_TEMPERATURE, SKIN_TEMPERATURE_DEVIATION, BASAL_BODY_TEMPERATURE, GLUCOSE_CV (post-meal pattern), RESTING_HEART_RATE, MOOD_DRIFT_SCORE |

*Population shares vary across surveys; these are the modal figures from the Korean Society of Sasang Constitutional Medicine and the standardised QSCC-II (Questionnaire for the Sasang Constitution Classification II) validations.

**What a KMD would want, concretely:**

1. A `SasangConstitution` enum at `com.bios.app.sasang` with five values: `UNKNOWN` (default), `TAEYANGIN`, `SOYANGIN`, `TAEUMIN`, `SOUMIN`. The default is `UNKNOWN` and Bios never infers — typing is the KMD's job in clinic.
2. A pull-side Settings surface "Sasang constitutional type (옵션)" with a brief explainer, links to QSCC-II if the owner wants to self-screen between visits, and an explicit note: "If you have been typed by a 한의사, enter the result here. Bios will not change its inferences without your input." Off by default; never auto-detected.
3. An `excludedConstitutions: Set<SasangConstitution> = emptySet()` field on [ConditionPattern](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L8-L37), parallel to the existing `excludedStates`. The [AnomalyDetector.applicablePatterns()](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L43-L44) filter adds a constitution check.
4. Constitution-aware threshold modifiers on at least the patterns most likely to false-fire by constitution: `hypothyroidSignature` (its bradycardia rule fires on Soumin's constitutionally lower RHR), `cardiorespiratoryDeconditioning` (its activity-decline rule fires on Taeumin's lower baseline activity), `cardiovascularStress` (its RHR-elevation rule fires on Soyangin's constitutionally higher RHR).

**Crucially**, the constitution is never *pushed back* to the owner as an evaluation. Bios stores it because the owner told Bios what their KMD said. Bios uses it to filter what it shows and to widen acceptable ranges on the false-fire-prone patterns. Bios never says "you are Soyangin" — that is the practitioner's call, made through 용모사기 + 맥진 + 성정 examination in clinic.

**Manifesto check:** owner-set, pull-side, never inferred, never pushed. Closes the single largest Korean-medicine-side gap without violating any of the seven principles. Cost: an enum, a Settings screen, one field on `ConditionPattern`, four small modifier branches in the patterns most prone to constitutional false-fire.

### 2.2 The Sasang body-type physiological projections are not surfaced

Even before a constitution is explicitly modelled, Bios *already collects* the inputs that distinguish the four constitutions on the wearable side. The pull-side "constitutional projection" view — read the existing 14-day baselines through a constitutional lens — is a view, not new data.

What such a view would surface:

- **Taeumin metabolic-burden view.** [BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L121) trend over 90 days; [BODY_FAT_PCT](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L122) vs. the owner's baseline; [GLUCOSE_TIME_IN_RANGE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L119) vs. baseline; [ALT / AST / GGT](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L198-L200) from FHIR imports. The framing for a Taeumin owner is "your constitution carries metabolic-syndrome susceptibility; here is where your signals sit relative to your own baseline and to the population reference." No moralisation, no nudge — instrument-reading.
- **Soumin cold-pattern view.** [SKIN_TEMPERATURE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L68) and [SKIN_TEMPERATURE_DEVIATION](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L69) — the literal substrate for cold-pattern reading; [BASAL_BODY_TEMPERATURE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L131) trend; [GLUCOSE_CV](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L117) post-meal pattern as a digestive-fire proxy. The framing for a Soumin owner reads cold-trend drift differently from a Taeumin owner with the same numbers.
- **Soyangin sympathetic-load view.** [RESTING_HEART_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L46), [HEART_RATE_VARIABILITY](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L39), [LF_HF_RATIO](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L42), [STRESS_SCORE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L41), [PARASYMPATHETIC_TONE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L40), [BLOOD_PRESSURE_SYSTOLIC](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L47) / [DIASTOLIC](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L48), [MOOD_DRIFT_SCORE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L216). Soyangin owners run constitutionally hot and sympathetic-leaning; the practitioner reads sustained elevation as constitution-aligned dysregulation, where the same numbers on a Soumin owner would be acute strain. This view tells the owner — and tells the KMD on FHIR export — which framing applies.
- **Taeyangin descending-qi view.** Rare enough that the view is documented but rarely used: [RESPIRATORY_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L52) variability across the day, orthostatic patterns in [BLOOD_PRESSURE_SYSTOLIC](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L47), [FALL_EVENT](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L246) frequency over a 90-day window.

**What it would cost:** a `ConstitutionalProjection` data class in the pull-side reporting layer (under `com.bios.app.ui.sasang` or similar), four projection composers, and four Compose screens behind a "Sasang lens" toggle that is only available once the owner has set a constitution under §2.1. No new MetricType keys. No new sensors. No push notifications from this surface — strictly pull-side, owner-asked.

**Manifesto check:** pull-side, owner-asked, never pushed. The view does not generate new push alerts; it reframes data the owner can already see in numerical form. Cost: ~200 lines of Compose UI plus one data class.

### 2.3 The hot/cold polarity (한열, 寒熱) as a continuous diagnostic axis is invisible

Korean herbal practice prescribes against constitutional cold/hot tendency. Soumin formulae — 인삼탕 (Ginseng Decoction), 곽향정기산 (Patchouli Qi-Regulating Powder), 부자이중탕 (Aconite Centre-Regulating Decoction) — are warming and primarily Spleen-tonifying. Soyangin formulae — 형방패독산 (Schizonepeta-Saposhnikovia Toxin-Dispelling Powder), 양격산화탕 (Diaphragm-Cooling Fire-Dispersing Decoction), 육미지황탕 (Six-Flavour Rehmannia Decoction in its Korean variant) — are cooling and primarily Kidney-yin-nourishing. The owner's *current* hot/cold drift, plotted against their *constitutional* hot/cold baseline, is the practitioner's primary axis for between-visit adjustments.

Bios captures the literal substrate at [SKIN_TEMPERATURE_DEVIATION](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L69) (Δ°C from personal baseline) — and never projects it onto this axis. The closest existing surface is the `chronic_inflammation` pattern, which fires on sustained mild skin-temp elevation among other signals, but it reads the elevation as inflammation rather than as a hot/cold drift to interpret against constitution.

**What a KMD would want:**

A pull-side "constitutional hot/cold drift" view that:

1. Renders [SKIN_TEMPERATURE_DEVIATION](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L69) over the last 7 / 30 / 90 days, against the constitutional default direction (Soumin: tends cold, drift up is constitution-leaving; Soyangin: tends hot, drift up is constitution-exacerbating).
2. Couples with the owner's self-reported cold/hot preference (new SELF_REPORTED key: `subjective_thermal_preference`, enum {COLD_AVERSE, NEUTRAL, HEAT_AVERSE}) — the same lightweight self-report key the TCM audit proposed for the eight-principles candidate-reading view, here reused for the Korean hot/cold polarity.
3. Surfaces an annotation: "skin-temp deviation +0.4 °C sustained over 14 days; for your constitution (Soyangin) this trends in the heat-aggravating direction. Discuss with your 한의사." Data first, framing second, professional referral as the action.

**What it would cost:** one new `SELF_REPORTED` key (`subjective_thermal_preference`), a Compose screen, and a small composer that reads the existing [SKIN_TEMPERATURE_DEVIATION](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L69) and [BASAL_BODY_TEMPERATURE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L131) baselines.

**Manifesto check:** owner-set self-report + pull-side rendering. No new push notifications, no new evaluation language.

### 2.4 Saam acupuncture (사암침법) follow-up surface absent

Saam acupuncture is one of the most distinctly Korean contributions to East Asian medicine. Attributed to the 17th-century Buddhist monk Saam Doin, it works with only five points per meridian (one per phase — wood, fire, earth, metal, water) and uses the *Nan Jing* 68th-difficulty five-element generative (生, 상생) and control (剋, 상극) relationships to select tonification (補) or sedation (瀉) point combinations. A representative Saam protocol: for "Liver-deficiency-with-Lung-excess" — tonify Kidney-water-point on the Liver channel (mother-point of the deficient organ) and sedate the Lung-metal-point on the Lung channel (sedate the controlling organ's point that exacerbates).

The diagnostic step Saam relies on — pulse + facial + constitutional context — is not Bios's territory and probably should not be. But the *follow-up*, between sessions, is a place Bios *could* be useful. A Saam treatment is expected to produce specific changes:

- pulse-quality change at 24–72 h (which the [PpgSignalProcessor](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) could measure if waveform morphology were preserved per §2.8);
- thermal-distribution change (Soumin owners receiving warming-Spleen protocols should show [SKIN_TEMPERATURE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L68) shifts);
- sleep-quality change at 72 h ([SLEEP_EFFICIENCY](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L86), [SLEEP_FRAGMENTATION_INDEX](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L87));
- autonomic-tone change at 24–48 h ([HEART_RATE_VARIABILITY](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L39), [PARASYMPATHETIC_TONE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L40)).

**What a KMD would want:**

A lightweight session-marker self-report — `acupuncture_session_event` on `MetricDomain.SELF_REPORTED` — that the owner taps after a clinic visit. The pull-side surface then renders the wearable signals from the 72 h *before* and 72 h *after* the marker, plotted together. Not an outcome score, not an efficacy judgement, not anything the manifesto bans. Just: "here is what your data looked like around your last 침 session," for the owner to bring back to their KMD.

**What it would cost:** one new event-shaped MetricType (`ACUPUNCTURE_SESSION`, `MetricUnit.EVENT`, possibly under a new `MetricDomain.PRACTITIONER_VISIT` or simply `MetricDomain.SELF_REPORTED`), one Compose screen, one composer.

**Manifesto check:** owner-logged, pull-side rendering, no efficacy claim. The same shape as the existing [TOBACCO_USE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L220) / [FALL_EVENT](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L246) discrete events.

### 2.5 Korean herbology — formulation-aware medication annotation

The existing [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L33-L34) surface accepts free-text annotations, and the [AnomalyDetector](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L375-L378) appends an "Annotated current medications" line to alert explanations. This already covers the *storage* of Korean formula names — an owner taking 인삼탕 (Ginseng Decoction) for Soumin-cold-pattern can type "인삼탕" into the medication-annotation field.

What the surface does *not* do: recognise that "인삼탕" is a constitutional formulation targeting a particular pattern, and re-frame the wearable observation accordingly. A Soumin owner on 인삼탕 whose [RESTING_HEART_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L46) drifts upward and [SKIN_TEMPERATURE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L68) trends warm is showing the expected directional response to a warming-Spleen formula. A Soyangin owner on 양격산화탕 whose [HEART_RATE_VARIABILITY](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L39) recovers and skin-temp drifts cool is showing the expected directional response to a cooling-fire-dispersing formula. The alert-explanation builder is currently constitution-blind and formula-blind.

**What a KMD would want:**

A small recognition table — perhaps 30–50 of the most common Korean formulations — that the alert-explanation builder consults. When the annotation matches, the explanation appends "this formula targets [the pattern], the observed direction is [aligned / counter] to the expected response." This is *not* evaluation of the owner; it is annotation of the relationship between the formula and the observation, leaving the owner-KMD conversation to decide whether the response is adequate.

The Korean herbological pharmacopoeia has well-documented constitutional-targeting metadata in the standardised reference works (Donguibogam-derived formularies; KFDA-approved 한의약 standards), so the recognition table is a static asset, not an inference engine.

**What it would cost:** a YAML or JSON asset under `assets/sasang/` with formula → (constitution, pattern, expected direction) entries; a small extension to the [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L33) read path that consults this table; a brief addition to the explanation builder.

**Manifesto check:** annotation only, no evaluation. The "expected direction is [aligned / counter]" framing is data-only — same register as the existing biomarker patterns that say "the lab anchors the pattern; the wearable signals on their own have many possible causes."

### 2.6 Korean tongue and facial diagnosis (망진 / 체형 진단) — principled total absence

Sasang constitutional typing in the clinic relies on three combined examinations: 용모사기 (facial form and body-frame), 맥진 (pulse), and 성정·사기 (voice / temperament). The body-frame distinction is the iconic Sasang signature — Taeyangin and Soyangin have visibly stronger upper-body development; Taeumin and Soumin have stronger lower-body development. A trained KMD reads this in seconds and uses it as the primary anchor for constitutional classification, with pulse and temperament as corroborators.

Bios has no image-capture path for any of this. The codebase's posture on imagery is deliberately conservative (see the [reproductive-database isolation pattern](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L464-L474) — the team has thought through image storage as a threat surface). This is principled.

The omission is nonetheless total. There is no facial-photo journal MetricType, no body-frame measurement key, no image storage path in the journal layer that would accept one, no [FHIR `Media`](https://www.hl7.org/fhir/media.html) resource mapping.

**The honest framing for a KMD reading this audit:**

1. **Bios cannot self-type a Sasang constitution.** The body-frame examination is not derivable from wearables. This is not a defect to remediate. The constitution selector recommended in §2.1 must be owner-set after a KMD has done the in-clinic typing. Bios stores; the practitioner classifies.
2. **If Bios chose to ship a tongue/facial photo journal — and it is defensible to choose not to** — the architectural pattern is already in place. A reproductive-DB-style isolated photo journal (separate SQLCipher key, owner-controlled wipe, no automatic analysis) would be the manifesto-compliant shape, and the threat-model reasoning that justified isolating reproductive data applies equally to facial biometrics. The owner photographs; the photo is encrypted-at-rest; the owner shows it to their 한의사. Bios stores; it does not interpret.
3. **No automated facial-typing CNN.** The same reasoning that the TCM audit applied to tongue analysis applies here with extra force: an automated facial-typing classifier would be a "Bios evaluating the person" surface that the manifesto explicitly prohibits — and would also be a poor instrument, since the KMD's in-person reading integrates 용모 + 맥 + 성정 in a way that no image alone can replicate.

If this is judged out of scope: that is a defensible choice consistent with the manifesto's image posture, and the audit notes it as a deliberate boundary rather than a defect. If Bios ever wants to be a serious 망진 surface for a 한의사's between-visit reading, the reproductive-DB precedent is the architectural template.

### 2.7 The Donguibogam seven-emotion + Sasang four-temperament etiology framework

Korean medicine builds on the Chinese 七情 (seven emotions) framework but adds a distinctive Sasang temperament dimension. The *Donguisusebowon* maps each constitution to a dominant emotion:

| Constitution | Dominant emotion | Hanja | Western analogue | Disease pathway when in excess |
|---|---|---|---|---|
| Taeyangin | sorrow / mourning | 哀 | grief, melancholy | Lung-channel descending-qi failure; pulmonary pattern |
| Soyangin | anger / indignation | 怒 | irritability, frustration | Heart-Spleen heat; hypertension, ulcer, anxiety |
| Taeumin | pleasure / steadiness | 樂 | complacency, indulgence | Liver-channel stagnation; metabolic syndrome |
| Soumin | joy / liveliness | 喜 | excitement, scattered spirit | Spleen-channel depletion; digestive collapse, depression |

A KMD reads the dominant emotion as *diagnostic for the constitution* — temperament is one input to constitutional typing — and as *etiological for that constitution's characteristic diseases* — Soyangin's anger-driven hypertension and Soumin's grief-driven digestive failure are textbook 사상의학.

Bios's mental-health surface is generic: [MOOD_DRIFT_SCORE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L216) is a multivariate composite from W2F's ADA-1/HDA-1 framework. There is no four-temperament logging surface, and no constitutional-temperament linkage in the [mentalHealthCorrelate pattern](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459).

**What a KMD would want:**

A self-report key set in `MetricDomain.MENTAL_HEALTH` for the seven classical emotions (joy 喜, anger 怒, worry 思, grief 悲, fear 恐, fright 驚, sadness 哀) — owner-loggable from the journal surface — and a small composer that, *when constitution is set*, reads persistent emotion-log entries through the constitutional lens. Persistent 怒 in a Soyangin owner alongside elevated [BLOOD_PRESSURE_SYSTOLIC](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L47) and depressed [HEART_RATE_VARIABILITY](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L39) is the textbook constitutional-emotional-physiological cluster a KMD reads as 노상심비 (anger-injuring Heart and Spleen). Persistent 哀 in a Soumin owner with declining [ACTIVE_MINUTES](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L106) and post-meal [GLUCOSE_CV](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L117) elevation is the cluster a KMD reads as 비상비기 (grief-injuring Spleen-qi).

This is also where the seven-emotion model honours the manifesto better than the generic mental-health framing: in Korean medicine the emotion is what the *owner* reports, never what the instrument infers. The owner annotates; Bios stores; the practitioner reads. That is precisely the pull-side, owner-evaluating posture the manifesto requires.

**What it would cost:** seven `SELF_REPORTED` keys in [MENTAL_HEALTH](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L215-L216) domain (one per classical emotion), a journal-entry surface, a small composer in the constitutional-projection view.

### 2.8 Sasang-specific pulse refinements on the PPG waveform

The TCM audit ([§2.2](TCM_POV.md#22-pulse-diagnosis-脉诊--bios-discards-what-it-already-measures)) catalogued the latent pulse-quality features the [PpgSignalProcessor](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) computes and then discards. That argument applies with extra force in the Sasang frame because **a KMD's interpretation of the same waveform is constitution-dependent**.

Concretely: the Sasang-specific qualities ([Korean medicine refinements documented in the Society of Sasang Constitutional Medicine guidelines](https://www.ksomp.or.kr/)) overlay onto the Chinese 28 pulse qualities but with constitutional weighting:

| Constitution | Expected pulse signature | What a counter-signature signals |
|---|---|---|
| Taeyangin | floating-relaxed (부완) on right cubit position | Strong / forceful signature suggests reclassification toward Soyangin |
| Soyangin | slippery-rapid (활삭) — wider waveform, faster rate, smooth upstroke | Thin-faint signature suggests acute sympathetic depletion |
| Taeumin | deep-relaxed (침완) — broad waveform, lower amplitude variation | Wiry-tight signature suggests acute stress superimposed on the constitution |
| Soumin | faint-thin (미세) — narrow waveform, low amplitude | Slippery-rapid signature is a red flag for false-heat (虛熱) — yin-deficiency-with-effulgent-fire-pattern in a constitutionally cold body |

[PpgSignalProcessor.kt:120-142](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L120-L142) computes peak amplitude, peak-amplitude CoV (after trimming), and RR CoV — features that map onto every one of the above qualities. They are computed, used as rejection signals, and discarded at [PpgSignalProcessor.kt:144-152](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L144-L152) when [PpgResult](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L266-L295) returns only `rrIntervalsMs`, `sqiScore`, `rejectionReason`, `peakCount`, `durationSec`.

**What a KMD would want, layered on top of the TCM audit's recommendation:**

1. Preserve the waveform features in `PpgResult` (TCM audit §2.2).
2. *When a Sasang constitution is set*, the pull-side "pulse character" view annotates whether the waveform features are *constitution-aligned* or *counter to constitution* — not a diagnosis, but the practitioner-readable framing.
3. A Soumin owner's *unexpectedly* slippery-rapid pulse (the false-heat signature) gets a stronger note than the same pulse on a Soyangin owner where it is expected. This is exactly the interpretive layer a KMD adds in clinic to the same waveform.

**What it would cost:** preserving the features (TCM audit §2.2 work, ~30 lines in `PpgResult`); a small Sasang-aware annotation in the pull-side pulse view (~50 lines in a Compose screen and one mapping table).

### 2.9 KMD-side FHIR / share-flow shape

South Korea's parallel KMD-MD licensure makes [FHIR R4 export](../ROADMAP.md#current-state-v020) genuinely useful for inter-clinic coordination — but the standard FHIR `Observation` shape, even with LOINC coding, does not carry the constitutional context a KMD would expect to share with another KMD. There is no LOINC code for "Soyangin"; there is no SNOMED code for "이 환자의 사상체질은 소양인입니다."

This is fine for KMD → MD direction (constitutional context is not what an MD needs from the bundle; the lab values and wearable trends are). It is *not* fine for KMD → KMD direction, which is the more common Korean clinic-to-clinic flow.

**What a KMD would want:**

A parallel "한의사 공유" (Korean Medicine practitioner share) export surface that:

1. Renders the constitutional type (when set).
2. Surfaces the constitutional-lens projections (§2.2) — Taeumin metabolic-burden, Soumin cold-pattern, Soyangin sympathetic-load, Taeyangin descending-qi.
3. Includes the hot/cold drift summary (§2.3).
4. Lists the formula annotations and their (constitution, pattern, expected direction) metadata (§2.5).
5. Lists the seven-emotion log entries from the last 30 days (§2.7).
6. Provides Korean-language localisation throughout — the existing [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) handles US / GB / EU / CA / AU / JP per [ROADMAP.md](../ROADMAP.md), and South Korea is a natural addition.

The natural artefact is a Korean-language PDF or a structured text format (not FHIR), suitable for paste into Korean clinic-management systems (EzPlus, EzMR) or attachment to a KakaoTalk message to the referring KMD. FHIR is the right artefact for the MD-side workflow and should stay; this is the parallel artefact for the KMD-side workflow.

**What it would cost:** Korean locale addition to [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) (config-driven per the existing localisation layer); a `SasangShareExporter` parallel to the existing FHIR exporter; a Compose screen under Settings → "공유 / Share."

### 2.10 Where Bios and Korean medicine practice agree, and what that means

These are not gaps — these are convergences worth naming because they constrain what kind of Sasang-aware features make sense.

#### 2.10.1 "Instrument, not coach" ≈ 한의사의 도구 (the practitioner's instrument)

The manifesto's Principle 7 is essentially the classical Korean medicine posture. The instrument (the wearable, the [PpgSignalProcessor](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) waveform, the imported lab panel) reports; the discernment — the constitutional classification, the pattern identification, the formula choice — is the practitioner's, made in clinic over decades. Bios refuses to do the discernment, and is therefore correctly shaped to slot into a Korean medicine consult as a data source the practitioner reads, not as a competing diagnostic engine.

This forecloses one class of features: "Bios tells the owner their Sasang constitution" should never ship. The constitution selector recommended throughout this audit always stops at *owner-set after KMD typing*; it never resolves into a Bios-inferred classification. The manifesto and the classical-medicine posture converge on the same answer.

#### 2.10.2 "Personal baseline over population norm" ≈ 수증치료 (sujeung chiryo)

The principle that the same disease in two different people requires two different treatments is canonical in Korean medicine — *수증치료* is the explicit doctrinal form. Bios's 14-day rolling baseline is the operational form of this. The fact that biomarker patterns combine *personal-baseline z-scores* with *absolute clinical cutoffs* ([BiomarkerConditionPatterns.kt:46-52](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L46-L52)) is a sophisticated reading: the constitutional/baseline interpretation operates alongside the population threshold, neither overriding the other. This is exactly how a KMD reads a contemporary lab panel — the LDL is what the lab says, but how that LDL *means* for *this* owner depends on constitution, age, season, and the rest of the picture.

#### 2.10.3 The institutional reality of parallel KMD-MD licensure makes Bios usable

South Korea is one of the only jurisdictions where Bios's design assumption — that the owner may share data with multiple practitioner types, each reading the same data through a different lens — is institutionally true today. A Korean owner may legitimately consult both an MD endocrinologist and a KMD specialising in 한방내과 about the same set of HbA1c, fasting insulin, and CGM-derived signals. The MD will read against ADA glycemic targets; the KMD will read against Sasang constitutional metabolic-burden expectations (Taeumin metabolic susceptibility, Soyangin heat-driven hyperglycemic pattern, Soumin Spleen-deficient irregular glucose handling). Both readings are reimbursable under NHIS for the right indications. Bios's [FHIR R4 import + export](../ROADMAP.md#86-lab--biomarker-inbound-surface-foundation-shipped) supports the MD-side flow today; §2.9 closes the gap for the KMD-side flow.

This is the strongest single point of alignment between Bios and Korean medicine *as an institution*: Bios's owner-controlled multi-practitioner sharing posture matches the South Korean clinical reality where KMD and MD genuinely coexist as parallel options for the same owner.

#### 2.10.4 "Silence is a feature" ≈ 양생 (yang-saeng — life-cultivation)

The owner-as-agent-of-restoration norm is foundational in Korean medicine — the practitioner advises, but the owner cultivates. The CI-gated banlist on "you should / you need to" at [AlertContentPolicy.kt:51-83](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L51-L83) is the strongest non-judgement guarantee I have seen in consumer health software, and it sits naturally alongside the *Donguibogam* opening posture that the wise person treats their own body with awareness rather than waiting for the practitioner to intervene.

---

## 3. What this audit does *not* recommend

To be explicit about boundaries, since the question of "constitutional medicine in software" attracts a credulous syncretism this audit refuses:

1. **No automated Sasang constitutional typing.** The 용모 + 맥 + 성정 examination is not derivable from wearables. The constitution selector must be owner-set after a KMD has done in-clinic typing, never inferred by Bios.
2. **No automated facial-photo classifier for constitutional inference.** Same reasoning, with the additional manifesto guard against image-based "Bios evaluating the person" surfaces.
3. **No herbal-formula recommendations.** Even in clinic, formula prescription is the most senior part of KMD practice — software has no business there. The recognition-table approach in §2.5 is annotation only, never prescription.
4. **No "Sasang balance score" or constitutional-harmony composite.** The manifesto already refuses biological-age composites; the same refusal applies to a constitutional-score composite. Each signal is read on its own through the constitutional lens, never aggregated into a number.
5. **No replacement of the four examinations (사진) by sensors.** Pulse-quality features from PPG are *complementary* to wrist palpation, not substitutes. A practitioner who would read the PPG features as if they were 삼지구후 (three-finger nine-positions palpation) is misusing the data.
6. **No claim that the Sasang lens is "validated" by wearable signals matching the constitutional pattern.** The patterns and the signals are commensurate enough to be displayed together; whether they cross-validate is an empirical question on which Bios should hold no position. The owner and the KMD decide what they make of the joint reading.

These six negatives are the load-bearing constraint on every recommendation above.

---

## 4. Prioritised recommendations

**Tier A — high signal, low engineering cost, manifesto-compatible**

1. **`SasangConstitution` enum + owner-set Settings surface + `excludedConstitutions` field on [ConditionPattern](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L8-L37).** Mirrors the existing [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) + [excludedStates](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L36) mechanism. Adds the categorical gating that lets patterns mean different things for different owners and is the foundation for everything else in this audit. (§2.1)
2. **Constitution-aware threshold modifiers** on the patterns most prone to constitutional false-fire: [hypothyroidSignature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L225-L263), [cardiorespiratoryDeconditioning](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L274-L307), [cardiovascularStress](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L187-L208). (§2.1)
3. **Preserve PPG waveform morphology features in `PpgResult`** (joint with TCM audit §2.2). Stop discarding the amplitude-CoV, rise-time, and asymmetry features after rejection-checking. Unlocks both the TCM pulse-character view and the Sasang-constitution-aware annotation. (§2.8)
4. **Pull-side "constitutional hot/cold drift" view** reading existing [SKIN_TEMPERATURE_DEVIATION](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L69) + a new `subjective_thermal_preference` SELF_REPORTED key. (§2.3)

**Tier B — modest engineering, opens the Sasang lens**

5. **Four constitutional-projection pull-side views** (Taeumin metabolic-burden, Soumin cold-pattern, Soyangin sympathetic-load, Taeyangin descending-qi) reading existing wearable signals and biomarkers through the constitutional lens. Compose-only; no new data collection. (§2.2)
6. **Seven-emotion self-report keys + Sasang-temperament composer.** Owner-loggable from the journal; constitution-aware composer in the pull-side view. (§2.7)
7. **Sasang-aware pulse-character annotation** layered on the §2.8 / TCM-§2.2 pulse waveform preservation. Constitution-conditional framing of the same waveform features.
8. **`ACUPUNCTURE_SESSION` event MetricType + pre/post 72 h visualisation.** Owner-logged Saam session marker; pull-side rendering of surrounding wearable signals. (§2.4)
9. **Korean herbal formulation recognition table** consulted by the [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L33) read path; alert-explanation builder annotates formula-vs-observation alignment. (§2.5)

**Tier C — institutional integration**

10. **Korean locale for [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt)** — KFDA / NHIS regulatory disclaimer, Korean-language clinical-band classification text, Korean-locale-appropriate biomarker reference ranges where they diverge from US/EU defaults (notably eGFR, which uses a Korean-population-validated equation in clinical practice).
11. **`SasangShareExporter`** parallel to the existing FHIR exporter — Korean-language PDF or structured-text artefact carrying constitution, constitutional-lens projections, hot/cold drift summary, formula annotations, seven-emotion log. (§2.9)

**Tier D — defensible-to-defer boundary**

12. **Tongue / facial-photo journal in a separate SQLCipher database.** Architecturally consistent with the [reproductive-DB precedent](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L464-L474). Worth doing if Bios chooses to be a serious 망진 between-visit surface for KMDs; defensible to leave out if image capture is judged out of scope across the audits. (§2.6)

**Do not adopt**

- Automated Sasang constitutional typing (§3.1).
- Automated facial-photo classifier (§3.2).
- Composite "Sasang balance score" (§3.4).
- Herbal-formula prescription engine (§3.3).
- Push-notification alerts from the Sasang lens (the lens is strictly pull-side, mirroring the TCM audit's posture on its parallel zang-fu lens).

---

## 5. Summary line for the project

> Bios meets the institutional reality of Korean medicine practice — the parallel KMD-MD licensure, the routine of KMDs reading modern lab panels alongside pulse and 용모 examination — better than it meets any other classical-medicine system, by virtue of the Phase 8.6 FHIR-import surface and 30+ LOINC-mapped biomarker keys that a KMD already uses in clinic today. What is missing is the layer Korean medicine *adds* to the broader East Asian tradition: the four-type **Sasang constitutional model** (사상체질). Bios has no `SasangConstitution` enum, no owner-set constitutional selector, no `excludedConstitutions` gating, no constitutional-lens projections, no constitutional pulse-feature annotation, no hot/cold drift view, no Sasang-temperament emotion log, no formulation-aware annotation, no Korean-language share artefact. The data is largely on the bus; the constitutional lens is not. None of the recommendations in this audit require Bios to become a Korean medicine application — they are *layering*, owner-set, pull-side, never-pushed, all within the existing architecture and the manifesto's constraints. The constitution itself is the practitioner's call, made in clinic; Bios stores what the owner brings back, surfaces what the constitution implies for the signals already collected, and stays silent until asked. That is what an instrument does in the *Donguibogam* register, and it is what the manifesto already requires.
