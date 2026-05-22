# TCM Practitioner Audit — Bios as a 治未病 ("Treat Not-Yet-Disease") Instrument

**Scope:** Bios's reach as a daily-monitoring instrument that a Traditional Chinese Medicine (中医) practitioner could read alongside the patient sitting in front of them. Evaluated against the diagnostic framework codified in the *Huangdi Neijing* lineage and the standard contemporary TCM curriculum (State Administration of TCM, PRC; WHO ICD-11 Chapter 26 Module 1).
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** TCM (中医) — eight-principle diagnosis (八纲辨证), four examinations (四诊), zang-fu pattern differentiation (脏腑辨证), constitutional theory (体质学), preventive medicine (治未病). Not a syncretist or "integrative-medicine" audit — written so a practitioner of TCM-as-its-own-system can decide whether Bios is useful in their clinic.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [CircadianConditionPattern.kt](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt).

This audit does **not** ask Bios to become a TCM application. It asks where, viewed through the TCM diagnostic frame, Bios's current observations are usable, where they are blind, and where the architecture leaves room without compromising the manifesto.

---

## Executive summary

Bios's underlying posture is, at the level of *principle*, closer to TCM than to most consumer health software written in the biomedical idiom. "Instrument, not coach. Silence is a feature. Evaluation belongs to the owner. Personal baseline over population norm." If you transposed these into classical Chinese — 工具不是教练；沉默是一种功能；判断属于本人；以己为度 — they would be entirely at home in a 养生 (yang-sheng) text. The architecture's choice to compare the owner against *themselves* rather than against a population mean is the operational equivalent of 因人制宜 ("adapt to the person"), one of the three cardinal individualisation principles of Chinese medicine (alongside 因时制宜 — adapt to season — and 因地制宜 — adapt to place).

Yet at the level of *content*, the model on the wire is biomedical. The 33 condition patterns are organised by Western organ system (cardiovascular, respiratory, metabolic, sleep, mental health, infectious, women's health); they cite Mishra/Quer/Ridker/ADA, not 张仲景/叶天士/吴鞠通; and the diagnostic axes a TCM practitioner reaches for — yin/yang, exterior/interior, cold/hot, deficiency/excess, qi/blood, zang-fu — are absent from the schema. A TCM practitioner who reads the code today will find a sophisticated 西医 (Western-medicine) instrument with the right philosophical scaffolding to host a TCM layer, but no TCM layer yet.

Ordered by impact in TCM terms, the gaps are:

1. **The eight principles (八纲) — yin/yang, exterior/interior (表里), cold/hot (寒热), deficiency/excess (虚实) — are not modelled, anywhere.** There is no enum, no annotation, no field in any of the 50+ MetricType keys that maps onto a 八纲 axis. The HR-up / HRV-down signature is read as "cardiovascular stress" only; in TCM the same physiological cluster, contextualised with pulse and tongue, is the *diagnostic substrate* that resolves into Liver-qi-stagnation (肝气郁结), Heart-yin-deficiency (心阴虚), Lung-qi-deficiency (肺气虚), or a 实 / 虚 wei-qi mobilisation. Without 八纲 axes Bios cannot speak the practitioner's first sentence.
2. **Pulse diagnosis (脉诊) is reduced to rate.** [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) does measure the fingertip PPG waveform — peak amplitude, peak-to-peak interval, amplitude variability, RR variability — but everything except the IBI series is *thrown away* after signal-quality checks at [PpgSignalProcessor.kt:144-152](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L144-L152). The 28 classical pulse qualities (28 脉象 — slippery 滑, wiry 弦, thin 细, choppy 涩, soggy 濡, tight 紧, hollow 芤, scattered 散, etc.) are encoded in the very waveform features Bios computes and discards. This is the single largest *latent* TCM signal in the codebase.
3. **No tongue diagnosis (舌诊) capture surface.** Tongue inspection is one of the four examinations (望 wàng — looking) and arguably the highest-yield single observation in a TCM clinic; Bios has no image-capture path for it. The omission is principled (Bios's threat model is hostile to image capture in general — see the post-Dobbs reproductive-DB isolation) but it is also total. A pull-side, owner-controlled tongue-photo journal would be modest engineering and the only way Bios could ever surface 望诊 data.
4. **Zang-fu (脏腑) organ-system reasoning is absent from the pattern library.** The condition library is well-built for cross-correlation, but the cross-correlations it draws are Western syndromes ("metabolic drift", "respiratory infection", "AFib screen"). None of them maps onto Liver-qi-stagnation, Spleen-qi-deficiency (脾气虚), Kidney-yang-deficiency (肾阳虚), Heart-blood-deficiency (心血虚), Lung-yin-deficiency (肺阴虚), Stomach-fire (胃火), etc. The wearable signals Bios *does* collect (HR, HRV, BBT, sleep architecture, RR, SpO2) could anchor a parallel pattern library written in zang-fu terms — but no such library exists.
5. **The seven emotions (七情) as etiology are entirely missing.** Bios models *mental_health_correlate* as a downstream physiological pattern (sleep, HRV, steps, typing cadence) — a 19th-century-psychiatry framing. In TCM, joy (喜) injures the Heart, anger (怒) injures the Liver, worry (思) injures the Spleen, grief (悲/忧) injures the Lung, fear (恐) injures the Kidney; the emotion *causes* the zang-fu pattern, not the other way. Bios has no schema field for owner-annotated emotion-of-the-day mapped to an organ correspondence.
6. **Constitutional type (体质) is unmodelled.** The 九种体质 (nine-constitution) framework standardised by 王琦 in 2009 — 平和质 (balanced), 气虚质 (qi-deficient), 阳虚质 (yang-deficient), 阴虚质 (yin-deficient), 痰湿质 (phlegm-damp), 湿热质 (damp-heat), 血瘀质 (blood-stasis), 气郁质 (qi-stagnant), 特禀质 (special/allergic) — is the TCM analogue of the "PhysiologyState" enum the primary-care audit recommended for pregnancy/paediatrics/frailty. Constitution gates *which patterns even apply* and *which interventions are appropriate*. Bios has no equivalent surface, and the 14-day personal baseline cannot substitute (constitution is lifelong; baseline is rolling).
7. **The six external pathogens (六淫) and the four-level (卫气营血) progression of warm-disease are not used for infection onset.** Bios has a respectable *infection_onset* pattern ([ConditionPatterns.kt:127-156](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156)) but it is undifferentiated. A TCM practitioner reading it cannot tell whether the wei-level (卫分 — surface, early) is breached, whether the qi-level (气分 — deeper, fever and thirst), the ying-level (营分 — nutritive, restless heat), or the xue-level (血分 — blood, haemorrhagic). The same six wearable signals Bios watches *would* allow a wei→qi→ying→xue staging if the pattern engine layered it on top.
8. **No diet therapy (食疗) or food-temperature (寒凉温热) surface.** Bios doesn't track food at all (no nutrition_log, no FOOD MetricDomain — verified in [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt)). For a system in which 药食同源 ("medicine and food share an origin") is foundational, this is a structural absence rather than a missing feature. Caffeine and alcohol are the only ingestion events on the bus, and they sit in `INTAKE` as substance-use rather than as 性味 (nature-and-flavour) entries.
9. **Acupoint, meridian, herbal-formula surfaces are completely absent.** There is no place in the data model for 经络 (meridian) palpation findings, no 穴位 (acupoint) tenderness log, no formula-and-dose annotation comparable to the medication-annotation surface added for biomedical drugs. This is not a defect — Bios is not a TCM clinic-management system — but it does delimit what Bios can *be* to a practitioner: read-only observation, never a workflow tool.
10. **Where Bios *aligns* with TCM, it does so quietly and well, and deserves naming.** 治未病 (treating-the-not-yet-ill, Su Wen Chapter 2: "the sage treats illness that is not yet illness; the inferior physician treats illness that is already illness"). The whole detection layer is built on this premise — Mishra/Quer/Smarr pre-symptomatic detection is *the same idea*, articulated 2400 years apart. The manifesto's principles 1 (prevention over reaction), 6 (science-grounded never fear-driven), and 7 (instrument not coach) are isomorphic to classical 养生 (yang-sheng) posture. The 子午流注 (zǐ-wǔ-liú-zhù) circadian meridian-time clock is implicitly available — Bios already tracks circadian phase shift and uses it in [CircadianConditionPattern](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt) — but the meridian-organ-hour mapping itself is not surfaced.

The rest of this audit walks each gap and aligned strength with concrete file references.

---

## 1. What Bios already does well, viewed through a TCM lens

| TCM principle | How Bios already embodies it | Evidence |
|---|---|---|
| **治未病 — treat the not-yet-ill** | The entire detection pipeline is pre-symptomatic by design. The Mishra/Quer/Smarr citations on `infection_onset` are 21st-century operationalisations of the classical doctrine. | [ConditionPatterns.kt:127-156](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156); manifesto Principle 1 |
| **因人制宜 — adapt to the person** | The 14-day personal baseline replaces population norms. Bradycardia at RHR 48 for a sedentary owner ≠ bradycardia at RHR 48 for an athlete — Bios captures that distinction natively. This is the *operational* form of one of the three classical individualisation principles. | `BaselineEngine` rolling window; biomarker patterns gate on personal-baseline z-scores ([ConditionPatterns.kt:30-46](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L30-L46)) |
| **观其脉证, 知犯何逆 — observe pulse and signs to know what has gone awry** (Shang Han Lun) | Multi-signal convergence (`minActiveSignals = 3` for infection onset, `required = true` for biomarker gates) is exactly the *convergence* reasoning a TCM practitioner uses across the four examinations. A single sign is rarely decisive; the pattern is. | [ConditionPatterns.kt:145](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L145); [BiomarkerConditionPatterns.kt:46-52](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L46-L52) |
| **不妄作劳 — do not exert recklessly** (Su Wen Chapter 1) | The `overtraining` and `recovery_deficit` patterns operationalise the classical injunction against depleting essence through over-exertion. The HRV-decline-with-elevated-RHR signature is the contemporary instrument-reading of 劳倦伤 (taxation injury). | [ConditionPatterns.kt:210-237](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L210-L237); [ConditionPatterns.kt:339-363](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L339-L363) |
| **女子以血为本 — for women, blood is the root** | The reproductive database is isolated, encrypted with its own key, and wiped independently. The BBT-anchored `menstrual_cycle_anomaly` pattern detects anovulation, luteal-phase shortfall, and cycle irregularity — clinical entities that map onto classical Liver-blood-deficiency, Kidney-essence-deficiency, and 冲任不调 (Chong-Ren disharmony) patterns the gynaecology specialty (妇科) reads daily. | [ConditionPatterns.kt:475-498](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498); `ReproductiveDatabase` isolation |
| **静坐, 沉默 — silence and stillness as restorative virtues** | "Silence is a feature." The push/pull-side split formalised in [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) refuses to interrupt the owner without cause. Compare to the classical posture: the physician speaks when the patient asks, and only then. | [AlertContentPolicy.kt:8-48](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L8-L48) |
| **不评判 — non-judgement** | "Evaluation belongs to the owner." The CI-gated banlist on "you should / you need to / streak / level up" is the strongest non-judgement guarantee I have ever seen in consumer health software. Doctors of TCM trained in the classical idiom address the patient as the agent of their own restoration; this is built into Bios's text layer. | [AlertContentPolicy.kt:51-83](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L51-L83); manifesto Principle 7 |
| **顺应四时 — accord with the four seasons** | The `circadian_disruption` pattern reads ambient-light irregularity + sleep degradation as a coherent misalignment signal. The conceptual frame ("the natural light-dark cycle is the dominant circadian zeitgeber") restates 顺应自然 — follow nature — in instrument terms. | [CircadianConditionPattern.kt:33-61](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt#L33-L61) |

These are *not* parity wins — these are places where Bios's underlying posture, written in biomedical idiom, *coincides with classical TCM principle*. A practitioner reading the codebase will recognise the orientation even where the vocabulary is unfamiliar.

---

## 2. Diagnostic gaps, ordered by impact

### 2.1 The eight principles (八纲) are absent from the schema

Eight-principle differentiation is the *first* layer of any contemporary TCM diagnosis. Every observation a practitioner takes is sorted along four binary axes:

- 阴 / 阳 (yin / yang)
- 表 / 里 (exterior / interior)
- 寒 / 热 (cold / hot)
- 虚 / 实 (deficiency / excess)

Bios has zero representation of any of these. Searching [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) and [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt) finds no enum, annotation, or field whose semantics correspond to any 八纲 axis. The same physiological cluster — RHR up, HRV down, sleep fragmented, skin temperature elevated — that Bios reads as `cardiovascular_stress + recovery_deficit` would, in clinic, be sorted by the practitioner into (e.g.) 阴虚火旺 (yin-deficiency with effulgent fire), 肝阳上亢 (Liver-yang ascendant), or 心火亢盛 (Heart-fire blazing) depending on tongue, pulse, and the owner's account of cold/hot preference, sweating pattern, and mental state.

**What a practitioner would want:**

A pull-side surface (never push) that annotates each currently-active anomaly with the *raw 八纲 candidate readings* derived from the wearable signals — explicitly *not* a diagnosis, in keeping with the manifesto, but the practitioner-readable substrate from which a diagnosis would be made:

- 寒/热 candidate: skin-temperature deviation direction + owner-annotated subjective cold/hot preference (new self-report key).
- 虚/实 candidate: HRV trend direction + activity-tolerance trend + autonomic-balance proxy.
- 表/里 candidate: respiratory-rate / SpO2 / temperature acuity vs. chronicity of the signal cluster.
- 阴/阳 candidate: nocturnal vs. diurnal symptom timing (Bios already has the clock; it just doesn't annotate this axis).

**What it would cost:** an `EightPrinciplesView` data class in the pull-side reporting layer, an owner self-report surface for the two subjective inputs (cold/hot preference, day/night symptom timing) using the existing `SELF_REPORTED` ReadingKind machinery. The wearable signals are already on the bus. No new MetricType keys are strictly required — this is a *view*, not new data — though if a TCM-aware companion app appeared on the metric bus, a `BAGUA_AXIS_*` annotation set would let it consume the view structurally rather than re-deriving.

**Manifesto check:** pull-side, owner-asked, never pushed. Closes the largest single TCM blindspot without violating any of the seven principles.

### 2.2 Pulse diagnosis (脉诊) — Bios discards what it already measures

This is the most consequential *latent* finding in the audit. The PPG signal processor at [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) computes, on the fingertip waveform:

- peak amplitude (the systolic upstroke height) — [PpgSignalProcessor.kt:120-124](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L120-L124)
- peak amplitude coefficient of variation (`peakAmpCov`) — same lines, intended as a motion-rejection signal
- inter-beat intervals (`rrMs`) — [PpgSignalProcessor.kt:133-134](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L133-L134)
- RR coefficient of variation (`rrCov`) — intended as an arrhythmia-rejection signal
- detrended/smoothed waveform morphology — [PpgSignalProcessor.kt:103-105](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L103-L105)

Every one of these is *also* a candidate feature for the classical pulse qualities. A non-exhaustive mapping a TCM practitioner familiar with PPG signal processing would draw:

| Classical quality (脉象) | Western description | Candidate PPG-derived feature | Already computed? |
|---|---|---|---|
| 滑脉 (huá — slippery) | Round, smooth, "pearls on a plate" — wide pulse with smooth upstroke | High peak amplitude + smooth waveform + normal/elevated rate | Peak amp computed; waveform smoothness *measured* (smoothing residual) but not exposed |
| 涩脉 (sè — choppy / hesitant) | Irregular cadence, thin, scraping | Elevated `rrCov` *and* elevated `peakAmpCov` together | Both computed; currently used only as *rejection* signals, not as classification features |
| 弦脉 (xián — wiry / taut) | Long, taut, like a guitar string | Sharp systolic upstroke (fast rise time), narrow pulse pressure proxy | Rise-time *derivable* from existing peak detection but not stored |
| 细脉 (xì — thin) | Fine, thread-like, hard to feel | Low peak amplitude relative to baseline noise | Peak amp computed |
| 数脉 (shuò — rapid) | >90 bpm | Direct rate | Computed |
| 迟脉 (chí — slow) | <60 bpm | Direct rate | Computed |
| 紧脉 (jǐn — tight) | Tense, twisted, like a stretched rope | Elevated rate + reduced HRV + elevated rise-time | All three computed |
| 濡脉 (rú — soggy / soft) | Floating, fine, soft | Low peak amp + variable peak amp | Both computed |
| 洪脉 (hóng — surging / flooding) | Large, forceful upstroke, gradual decline | High peak amp + high pulse-pressure-proxy + asymmetric waveform | Peak amp computed; asymmetry *measurable* from existing waveform |
| 沉脉 (chén — deep / sinking) | Felt only with deep pressure | Not extractable from optical PPG (requires manual palpation pressure variation) | Not extractable |
| 浮脉 (fú — floating) | Felt at light touch, disappears with pressure | Not extractable from optical PPG (same reason) | Not extractable |
| 结/代/促 (jié/dài/cù — knotted/intermittent/hurried) | Pause patterns, with or without rhythm | Specific RR-interval patterns | RR series computed; pattern recognition not done |

The honest distinction: optical PPG cannot replicate the *pressure-modulated* qualities (沉脉, 浮脉, the three positions 寸/关/尺 at three depths 浮/中/沉) that a wrist palpation gives a practitioner. Those require human touch and will never be captured by a phone camera. But the *waveform-shape and -rhythm* qualities — perhaps 12-15 of the 28 classical qualities, including the four that practitioners report most often (滑, 弦, 细, 数) — are in principle extractable from the data Bios already collects.

**What is currently done with these features:** they are computed at [PpgSignalProcessor.kt:120-142](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L120-L142), checked against rejection thresholds, and discarded. The function returns only `rrIntervalsMs`, `sqiScore`, `rejectionReason`, `peakCount`, `durationSec`. The waveform morphology is gone.

**What a practitioner would want:**

1. **Preserve, do not discard, the waveform features.** A `PulseWaveformFeatures` data class added to `PpgResult` carrying: peak-amplitude trimmed mean, peak-amplitude CoV, RR CoV, rise-time mean, rise-time CoV, decay-asymmetry index, dichrotic-notch position. These are statistical summaries — no raw waveform leaves the function, in keeping with Bios's storage-discipline posture.
2. **A pull-side "pulse character" view** that, when the owner explicitly requests it, surfaces the closest-matching classical qualities from the feature vector. *Not* a diagnosis — explicitly the same format as the existing biomarker view: data first, suggestion-of-meaning second, with "discuss with a TCM practitioner" as the action.
3. **A way for an owner who is consulting a TCM practitioner to export this view** in a form the practitioner can read alongside their own palpation. The FHIR Observation surface is the wrong artefact for this (no LOINC codes for 弦脉), but a parallel "TCM pulse summary" PDF or text fragment would be the natural shape.

**Manifesto check:** the data is already on the device; the camera-PPG capture is already opt-in and owner-initiated; surfacing the analysis is pull-side. No new sensors, no new permissions, no push notifications. Cost: one data class, one screen, one optional companion-readable contract key (`PULSE_WAVEFORM_FEATURES`).

This is the highest-leverage single TCM-aware change Bios could make. The signal is already in the building.

### 2.3 Tongue diagnosis (舌诊) — capture surface absent

Tongue inspection is, after pulse, the single most diagnostically dense observation in a TCM consult — five axes (tongue body colour, tongue body shape, coating colour, coating thickness/moisture, sub-lingual veins) each read against a dozen-plus reference patterns. The interaction between tongue and pulse is itself diagnostic (concordant or discordant readings carry different prognostic weight).

Bios has *no* image-capture path for tongue, by design. The codebase's posture on imagery is conservative (the reproductive-database isolation logic indicates the team has thought hard about image storage as a threat surface). This is principled and consistent.

The absence is nonetheless total. There is no tongue MetricType, no image storage path in the journal layer that would accept one, no FHIR resource mapping (FHIR R4 `Media` would be the technically correct resource), no pull-side "tongue today" surface.

**What a practitioner would want, if Bios chose to surface this:**

1. A reproductive-DB-style **isolated tongue-photo journal**: separate SQLCipher file, separate key, owner-controlled wipe independent of the rest of Bios. The threat-model reasoning that justified isolating reproductive data applies equally — tongue photos are biometric and could plausibly be subpoena targets.
2. **No automatic analysis.** A tongue-photo classifier would be a deep CNN, would require training data Bios does not have and should not collect, and would be a "Bios evaluating the person" surface the manifesto explicitly prohibits. The right shape is *journal*: the owner photographs the tongue, the photo is encrypted-at-rest, and the owner shows it to their practitioner. Bios stores; it does not interpret.
3. **A coupling between the tongue-photo timestamp and the wearable timeline.** When the owner shows the practitioner a tongue photo from "the morning I felt the heat rising," the wearable record of that morning's RHR/HRV/sleep/temp ought to be a click away.

**Manifesto check:** consistent with the reproductive-DB precedent (isolation by design, owner-controlled, never auto-analysed). The fact that Bios *already* has the architectural pattern for an isolated, encrypted, owner-wipeable secondary database makes this a smaller change than it appears.

If this is judged out of scope: that is a defensible choice, and the audit notes it as a deliberate boundary rather than a defect. But if Bios ever wants to be a serious 望诊 (looking-examination) instrument, this is the first piece.

### 2.4 Zang-fu (脏腑) pattern library is missing

The contemporary TCM internal-medicine specialty (内科) is organised by zang-fu pattern: Liver-qi-stagnation, Spleen-qi-deficiency, Kidney-yang-deficiency, Heart-blood-deficiency, Lung-yin-deficiency, Stomach-fire, Gallbladder-damp-heat, and roughly two dozen more standardised by the State Administration of TCM curriculum. The diagnostic gold standard for primary-care presenting complaints in a TCM clinic is *which zang-fu pattern best accounts for the eight-principle reading.*

Bios's `ConditionCategory` enum at [Enums.kt:102-105](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L102-L105) is Western: `CARDIOVASCULAR, RESPIRATORY, METABOLIC, SLEEP, MENTAL_HEALTH, INFECTIOUS, WOMENS_HEALTH, RECOVERY, SAFETY`. There is no `LIVER_QI`, `SPLEEN_QI`, `KIDNEY_YANG`, `HEART_BLOOD`, `LUNG_YIN` category. None of the 33 condition patterns in `ConditionPatterns.all` is named in zang-fu terms.

The interesting subtext: the signals Bios uses *would* support a parallel library. For example:

- **Liver-qi-stagnation (肝气郁结)** — irritability, sighing, plum-pit-throat sensation, premenstrual exacerbation, wiry pulse. Wearable proxies: HRV depression + elevated resting HR (autonomic stress signature) + cycle-phase-correlated mood drift (already on the bus via TYPING_CADENCE + MOOD_DRIFT_SCORE) + sleep latency increase. Bios already has *all five inputs*. The pattern itself does not exist.
- **Spleen-qi-deficiency (脾气虚)** — fatigue worse with mental work, loose stools, post-prandial drowsiness, tongue with teeth-marks. Wearable proxies (partial): persistent low activity, post-meal HR pattern (would require post-meal HR window analysis), cognitive-probe reaction-time slowing (W2F PVT exists per [MetricType.kt:255](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L255)). No bowel/stool signal — but a self-report key would be trivial.
- **Kidney-yang-deficiency (肾阳虚)** — cold limbs, low libido, low back ache, polyuria especially nocturnal, frequent night-waking, slow pulse. Wearable proxies (good coverage): low resting body temperature (skin-temp baseline shifted cold), bradycardia trend, nocturnal sleep fragmentation, sleep-quality decline. Bios already has these.
- **Heart-yin-deficiency (心阴虚)** — palpitations, insomnia with dream-disturbed sleep, night sweats, hot palms/soles, red tongue, thin/rapid pulse. Wearable proxies: elevated nocturnal RHR, nocturnal HRV depression, sleep architecture disruption (less deep, more REM), elevated skin temperature in the night window. All present.

**Recommendation:** a `TcmPatternLibrary` parallel to `ConditionPatterns.all`, gated behind an owner-selected "TCM lens" pull-side view. The same `SignalRule` machinery applies — the rules just reference the same metric types but cluster them along zang-fu lines rather than Western-system lines. The pattern explanations would use TCM vocabulary. The push-side alert channel does *not* fire from this library by default; the manifesto-compliant default is silent observation, surfaced only when the owner opens the TCM lens.

This is a substantial body of work — ~25 patterns to author with literature anchors from the modern TCM literature (the Chinese-language clinical-evidence corpus is large and partially indexed in CNKI; English-language meta-analyses exist in *Journal of Traditional Chinese Medicine*, *Chinese Medicine*, and *Evidence-Based Complementary and Alternative Medicine*). It is also the change that would make Bios *legibly* useful to a TCM practitioner rather than incidentally so.

### 2.5 Seven emotions (七情) as etiology — no organ correspondence surface

In TCM, emotion is *causal*, not consequent. The standard correspondences:

| 情 (emotion) | 脏 (organ injured) | Excess presentation |
|---|---|---|
| 喜 (joy / over-excitement) | 心 (Heart) | Palpitations, insomnia, scattered spirit |
| 怒 (anger) | 肝 (Liver) | Headache, red face, hypertension, wiry pulse |
| 思 (worry / overthinking) | 脾 (Spleen) | Poor appetite, fatigue, abdominal distension |
| 悲 / 忧 (grief / sadness) | 肺 (Lung) | Shallow breathing, fatigue, frequent sighing |
| 恐 (fear) | 肾 (Kidney) | Urinary frequency / incontinence, low back weakness |
| 惊 (fright) | 心 / 肾 | Acute palpitations, disturbed sleep |

Bios's mental-health surface is purely physiological: `mental_health_correlate` ([ConditionPatterns.kt:424-459](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459)) detects sleep/HRV/activity drift *downstream* of mood, with W2F's `MOOD_DRIFT_SCORE` and `CIRCADIAN_PHASE_SHIFT` as enrichers. There is no surface for an owner to log "I was angry today" mapped to 怒 → 肝, or "I have been grieving for two months" mapped to 悲 → 肺.

**What a practitioner would want:**

A self-report key set in `MetricDomain.MENTAL_HEALTH` for the seven classical emotions, owner-loggable from the journal surface. The pattern engine then reads the emotion log alongside the wearable physiology to draw the etiology-and-zang-fu inference: persistent 怒 logged with elevated RHR and wiry-pulse PPG features ⇒ Liver-yang-rising candidate. Persistent 思 with declining activity and post-meal sleepiness ⇒ Spleen-qi-deficiency candidate.

This is also where the seven-emotion model honours the manifesto better than the Western "mental_health_correlate" framing: in TCM the emotion is what the *owner* reports, never what the instrument infers. The owner annotates; Bios stores; the practitioner reads. That is precisely the pull-side, owner-evaluating posture the manifesto requires.

### 2.6 Constitutional type (体质) — no enum, no gating

The nine-constitution framework standardised by 王琦 (Wang Qi, Beijing University of Chinese Medicine, 2009; ZYYXH/T157-2009 Chinese Association of TCM standard) is the dominant contemporary system. Each owner is dominantly one of nine constitutional types, occasionally a mix of two; the constitution is stable across years and decades and *gates which patterns are clinically meaningful for that person*.

- 平和质 — balanced
- 气虚质 — qi-deficient
- 阳虚质 — yang-deficient
- 阴虚质 — yin-deficient
- 痰湿质 — phlegm-damp
- 湿热质 — damp-heat
- 血瘀质 — blood-stasis
- 气郁质 — qi-stagnant (constitutional)
- 特禀质 — special-constitution (allergic, hypersensitive)

A 阳虚 (yang-deficient) owner's resting body temperature is *constitutionally* lower; what would be hypothyroid signature in a 平和 (balanced) owner is normal for the 阳虚 constitution. A 湿热 (damp-heat) owner's elevated CRP and skin-temperature pattern is the *baseline state of the constitution*, not an inflammatory event. Bios's 14-day rolling baseline catches some of this implicitly — the personal baseline is the operational form of "what is normal *for this person*" — but the *categorical* gating (constitution determines which patterns even apply) is not modelled.

The primary-care audit recommended a `PhysiologyState` enum for pregnancy/paediatrics/frailty (audit §2.7). The TCM-side equivalent is a `Constitution` enum. The two could co-exist or merge; structurally they are the same pattern of "owner-set categorical context that gates which signal rules apply."

**Recommendation:**

1. A `TcmConstitution` enum at `com.bios.app.tcm` with the nine standard values plus an `UNKNOWN` default.
2. An owner-set, pull-side surface in Settings → "TCM constitution (optional)" with a brief explainer and the WHO/CATCM standard questionnaire (47 questions, validated, freely usable). Off by default; never inferred by Bios.
3. The `excludedStates` field on `ConditionPattern` (already used for pregnancy/postpartum/athlete exclusion at [ConditionPatterns.kt:206-208](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L206-L208)) extended with a sibling `excludedConstitutions: Set<TcmConstitution>` so the TCM-lens library can gate appropriately.
4. **Crucially, the constitution is *never* pushed back to the owner as an evaluation.** Bios stores it because the owner told Bios what it is; Bios uses it to filter what it shows; Bios never says "you are 阳虚." That is the practitioner's call, made in clinic.

### 2.7 Six pathogens (六淫) and the four-level (卫气营血) infection staging

`infection_onset` ([ConditionPatterns.kt:127-156](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156)) detects pre-symptomatic illness from a six-signal cluster — RHR↑, HRV↓, skin temp↑, RR↑, sleep↓, steps↓. The pattern is well-built and well-cited.

A TCM practitioner reads this with two questions:

1. **Which of the 六淫 (six external pathogens) is the offending agent?**
   - 风 (wind) — rapid onset, shifting symptoms, surface signs
   - 寒 (cold) — chills, body ache, no sweating, slow pulse
   - 暑 (summer-heat) — high fever, thirst, sweating, exhaustion
   - 湿 (damp) — heaviness, oedema, sluggish progression
   - 燥 (dryness) — dry cough, dry throat, dry skin
   - 火 / 热 (fire / heat) — high fever, restlessness, red face, rapid pulse
2. **What level of the 卫气营血 (wei-qi-ying-xue) progression has the pathogen reached?**
   - 卫 (wei, defensive) — surface, fever-and-chills, early infection
   - 气 (qi) — deeper, full fever, thirst, no chills
   - 营 (ying, nutritive) — restless heat, mild rash, mental disturbance
   - 血 (xue, blood) — haemorrhagic signs, severe mental disturbance, high mortality

Both of these are *stageable from the data Bios already collects*, if the pattern engine layered the staging on top:

| Wei-qi-ying-xue stage | Wearable signature | Bios has it? |
|---|---|---|
| 卫 (wei) — surface | Skin-temp deviation moderate, RHR mildly up, HRV mildly down, RR normal-to-mildly-up, no SpO2 drop, acuity <24 h | Yes |
| 气 (qi) — deeper | Skin-temp deviation large, RHR substantially up, HRV substantially down, RR up, mild SpO2 dip, acuity 24-72 h | Yes |
| 营 (ying) — restless | RHR very high, HRV severely depressed, sleep severely fragmented, *behavioral* signal (restlessness — accelerometer-derivable but not currently surfaced as "agitation"), acuity 2-5 days | Partial — no agitation metric |
| 血 (xue) — blood-level | SpO2 sustained drop, possible AFib-like rhythm irregularity, severe vital instability | Yes (this is essentially the `EmergencyVitalPatterns` territory) |

**Recommendation:** an `InfectionStaging` enum or annotation on the `infection_onset` anomaly that surfaces the *candidate stage* in TCM terms when the TCM lens is enabled. This is a *view* over the existing pattern, not a separate detection — same data, two vocabularies.

The 六淫 question is harder because seasonal and environmental context matters (dampness is a damp-climate-and-season pathogen; dryness is autumn-and-arid; the same fever pattern is read differently in Guangzhou monsoon vs. Beijing winter). The `ENVIRONMENT` MetricDomain already has `AMBIENT_LIGHT`, `AIR_PM25`, `AIR_VOC`, `AIR_CO2` — humidity is the obvious next addition for a TCM-aware environment, and would let the engine offer a *candidate* 六淫 reading. Single-most-impactful addition: `AMBIENT_HUMIDITY` (a relative-humidity sensor exists on most phones with a barometer chip and is trivially BLE-readable from ESS sensors Bios already supports).

### 2.8 Diet therapy (食疗) and food-temperature classification

Bios does not track food. There is no `FOOD` or `NUTRITION` MetricDomain in [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt). The only ingestion events on the bus are:

- `CAFFEINE_INTAKE` (mg)
- `ALCOHOL_INTAKE` (g ethanol)
- `MEDICATION_INTAKE` (mg, generic)
- `TOBACCO_USE`, `CANNABIS_USE` (event timestamps)

For 中医, this is a structural absence. 药食同源 ("medicine and food share an origin") is doctrinal; the same vocabulary that describes herbs (五味 — five flavours: sour, bitter, sweet, pungent, salty; 四气 — four natures: cold, cool, warm, hot; 归经 — channel-entry) describes food. Ginger is *warming*, watermelon is *cooling*, white rice is *neutral and tonifies the Spleen*. The diet a 阳虚 (yang-deficient) owner ought to eat is meaningfully different from what a 湿热 (damp-heat) owner ought to eat, and the practitioner expects the owner to know what they've been eating.

**What a practitioner would want, in the lightest form:**

A self-report `FOOD_INTAKE` event with a thin sidecar in `event_payloads`:
- `food_key` — owner-supplied free text or a future controlled vocabulary
- `nature_estimate` — owner-or-system enum {cold, cool, neutral, warm, hot} (五气/四气)
- `flavour_estimate` — owner-or-system enum-set {sour, bitter, sweet, pungent, salty} (五味)
- `meal_window` — breakfast / lunch / dinner / snack

Bios stores; the practitioner reads. No nutritional-coaching surface (manifesto-violating); no caloric counting (out of scope and ethically loaded); no scoring. Just a journal.

This is not a high-priority change in isolation — most owners will not log food consistently — but it is the only way the TCM lens has anything to say about diet-pattern correlation with the physiological observations.

### 2.9 Acupoint, meridian, herbal-formula annotation

These are clinic-side surfaces, not instrument-side, and a sensible boundary is that Bios does not implement them. A practitioner uses needle-and-moxa during a session; the owner does not self-administer between sessions in any data-relevant way.

The one place where a thin surface *would* matter: the medication-annotation surface added at [AnomalyDetector.kt:377-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L377-L378) currently reads from `MedicationAnnotationRepo` and appends an "Annotated current medications" line to alert explanations. For an owner who is on a Chinese herbal formula (汤剂, tang-ji) — say, 逍遥散 (xiao yao san, "Free and Easy Wanderer") for Liver-qi-stagnation, or 六味地黄丸 (liu wei di huang wan) for Kidney-yin-deficiency — the same false-positive-suppression logic applies as for a beta-blocker: a HR/HRV signature that would otherwise read as autonomic stress can be the *expected* directional change for a formula targeting that pattern.

**Recommendation:** the existing medication-annotation surface accepts free-text. Owners on TCM formulae can record them today. The honest improvement is for the alert-explanation builder to *recognise* that some annotations are TCM formulae and surface them with a "this formula targets [...]" note when the explanation is rendered through the TCM lens. This is a string-recognition convenience, not new architecture.

### 2.10 Meridian-time (子午流注) clock — present but not surfaced

The classical 12-meridian / 12-hour correspondence:

| Time (24h) | Channel | Time | Channel |
|---|---|---|---|
| 23:00–01:00 | 胆 Gallbladder | 11:00–13:00 | 心 Heart |
| 01:00–03:00 | 肝 Liver | 13:00–15:00 | 小肠 Small Intestine |
| 03:00–05:00 | 肺 Lung | 15:00–17:00 | 膀胱 Bladder |
| 05:00–07:00 | 大肠 Large Intestine | 17:00–19:00 | 肾 Kidney |
| 07:00–09:00 | 胃 Stomach | 19:00–21:00 | 心包 Pericardium |
| 09:00–11:00 | 脾 Spleen | 21:00–23:00 | 三焦 Triple Burner |

Symptoms or vital deviations occurring repeatedly in a particular two-hour window are read as involving the channel that "rules" that hour. A pattern of consistent 3 a.m. waking with mental restlessness suggests Liver-channel involvement; consistent 5 a.m. cough involves the Lung; consistent post-3 p.m. fatigue with afternoon urinary urgency involves the Bladder/Kidney shift.

Bios captures the timestamp of every reading and every anomaly. It has `CIRCADIAN_PHASE_SHIFT` and the full circadian-disruption pattern. It does *not* surface a "anomalies clustered in the [21-23] window" view that would let a practitioner read the meridian-time pattern at a glance.

**Recommendation:** in the pull-side TCM lens, a 12-window histogram of anomaly timing labelled with the corresponding channel. This is a UI/aggregation feature, no new data. Cost: a function and a chart.

---

## 3. Where Bios and TCM principles agree, and what that means

These are not gaps — these are convergences worth naming because they constrain what kind of TCM-aware features make sense.

### 3.1 "Instrument, not coach" ≈ 医者意也 ("medicine is the practitioner's discernment")

The manifesto's principle 7 is essentially classical-medicine posture. In TCM the instrument (the practitioner's fingers on the pulse, eyes on the tongue, ears at the voice) reports; the discernment — 意 — is the practitioner's, often unspoken, refined over decades. Bios refuses to do the discernment, and is therefore correctly shaped to slot into a TCM consult as a data source the practitioner reads, not as a competing diagnostic engine.

This forecloses one class of features: "Bios tells the owner their TCM diagnosis" should never ship. The pull-side surfaces recommended throughout this audit always stop at *candidate readings* and *standardised vocabularies*; they never resolve into a single TCM diagnosis. The manifesto and the classical-medicine posture converge on the same answer.

### 3.2 "Silence is a feature" ≈ 不治已病治未病 — Su Wen Chapter 2

The full passage:

> 是故聖人不治已病治未病，不治已亂治未亂，此之謂也。夫病已成而後藥之，亂已成而後治之，譬猶渴而穿井，鬬而鑄錐，不亦晚乎。

> "Therefore the sage treats the not-yet-ill rather than the already-ill, treats the not-yet-disordered rather than the already-disordered. To medicate after disease has formed, to govern after disorder has formed — is this not like digging the well only when thirsty, forging the spear only after the battle has begun? Is this not already too late?"

Bios's "silence is a feature, speak only when something matters" posture is the *operational* corollary of this. The pattern engine is built to find the not-yet-illness; the alert content policy refuses to fill the feed in its absence. These are 21st-century software-engineering implementations of a 2400-year-old clinical instruction.

This is, in honest fairness, the strongest single point of alignment between Bios and TCM. A practitioner reading the codebase will recognise it.

### 3.3 "Personal baseline over population norm" ≈ 因人制宜

The principle that the same disease in two different people requires two different treatments is canonical. Bios's 14-day rolling baseline is the operational form of this. The fact that biomarker patterns combine *personal-baseline z-scores* with *absolute clinical cutoffs* ([BiomarkerConditionPatterns.kt:46-52](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L46-L52)) is a sophisticated reading: the constitutional/baseline interpretation operates alongside the population threshold, neither overriding the other. This is exactly how a TCM practitioner reads a contemporary lab panel — the LDL is what the lab says, but how that LDL *means* for *this* owner depends on constitution, age, season, and the rest of the picture.

### 3.4 "Free to all" ≈ 大医精诚 — Sun Simiao's *On the Absolute Sincerity of Great Physicians*

Sun Simiao (孙思邈, 581–682):

> 凡大醫治病，必當安神定志，無欲無求，先發大慈惻隱之心，誓願普救含靈之苦。

> "When the great physician treats illness, he must settle his spirit and fix his intention, free of desire and demand, and first rouse a great compassion and sympathy, vowing to universally save the suffering of all sentient beings."

The continuation specifies: rich and poor, young and old, beautiful and ugly, friend and enemy, Chinese and foreigner, intelligent and slow — all treated equally, with the same care. The text is one of the foundational ethics documents of East Asian medicine.

Manifesto principle 3 (full health intelligence for everyone, never gated by payment) is the *same vow* in software form. A practitioner trained in the classical ethics will recognise this. It is also why a contribution-popup with feature gates or donor differentiation would violate the classical posture as severely as it would violate the manifesto — the alignment is not coincidental.

---

## 4. What this audit does *not* recommend

To be explicit about boundaries, since the question of "TCM in software" attracts a kind of credulous syncretism that this audit refuses:

1. **No automated TCM diagnosis from wearable data.** The pulse-feature surface, the eight-principle view, the zang-fu pattern library are all *pull-side reading surfaces*. Bios never says "you are Liver-qi-stagnant." That is the practitioner's call.
2. **No herbal-formula recommendations.** Even in clinic, formula prescription is the most senior part of TCM practice; software has no business there.
3. **No "TCM longevity score."** The manifesto already refuses biological-age composites, and the same refusal applies to a "constitutional balance score" or "five-element harmony index." These would be the TCM version of the same anti-pattern.
4. **No replacement of the four examinations by sensors.** Pulse-quality features from PPG are *complementary* to wrist palpation, not substitutes. A practitioner who would read the PPG features as if they were 三指九候 (three-finger nine-positions palpation) is misusing the data.
5. **No claim that the TCM lens is "validated" by the wearable signals matching the patterns.** The patterns and the signals are commensurate enough to be displayed together; whether they cross-validate is an empirical question on which Bios should hold no position. The owner and the practitioner decide what they make of the joint reading.

These five negatives are the load-bearing constraint on every recommendation above.

---

## 5. Prioritised recommendations

**Tier A — high signal, low engineering cost, manifesto-compatible**

1. **Preserve PPG waveform morphology features in `PpgResult`.** Stop discarding the amplitude-CoV, rise-time, and asymmetry features after rejection-checking. This is a data-structure change in [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) and unlocks every other pulse-related recommendation. (§2.2)
2. **Pull-side pulse-character view.** A screen the owner navigates into that summarises the recent PPG capture's waveform features and lists the closest-matching classical pulse qualities. Read-only, never pushed. (§2.2)
3. **Anomaly meridian-time histogram.** A 12-window aggregation of anomaly timestamps labelled with channel correspondences. Pure aggregation, no new data. (§2.10)

**Tier B — modest engineering, opens the TCM lens**

4. **Constitution enum + owner-set surface + `excludedConstitutions` field on `ConditionPattern`.** Mirrors the existing `excludedStates` mechanism. Adds the categorical gating that lets patterns mean different things for different owners. (§2.6)
5. **Eight-principles candidate-reading view.** Pull-side; reads existing wearable signals plus two new owner self-report keys (cold/hot preference, day/night symptom timing). (§2.1)
6. **Self-report keys for the seven emotions, mapped to organs.** Owner-loggable in the journal. (§2.5)
7. **`FOOD_INTAKE` event with 性味 sidecar fields.** Self-report only, no nutritional scoring. (§2.8)
8. **`AMBIENT_HUMIDITY` MetricType in `ENVIRONMENT`** — enables 六淫 candidate reading for damp/dry pathogens. ESS BLE sensors already exist on the bus. (§2.7)

**Tier C — substantial body of work, but high practitioner value**

9. **`TcmPatternLibrary` parallel to `ConditionPatterns.all`.** ~25 zang-fu patterns authored with Chinese-language and English-language literature citations, surfaced through the TCM lens only. Same `SignalRule` machinery. (§2.4)
10. **Wei-qi-ying-xue staging on `infection_onset`.** A four-stage candidate-reading view derived from the existing pattern's signals. (§2.7)

**Tier D — defensible-to-defer boundary**

11. **Tongue-photo journal in a separate SQLCipher database.** Architecturally consistent with the reproductive-DB precedent. Worth doing if Bios chooses to be a serious 望诊 surface; defensible to leave out if image capture is judged out of scope. (§2.3)

**Do not adopt**

- Automated pulse-quality classifier (the PPG features should be *displayed*, not auto-classified).
- Automated tongue-image analysis (CNN classifier would be a "Bios evaluating the person" surface).
- Composite TCM "balance score."
- Push-notification alerts from the TCM lens.
- Herbal-formula recommendation engine.

---

## 6. Summary line for the project

> Bios is the *posture* of classical Chinese medicine implemented in 21st-century software — 治未病, 因人制宜, 不评判, silence-as-a-feature — but with a strictly biomedical *vocabulary*. A TCM practitioner cannot read the current condition library as TCM; they can read it as Western medicine that happens to share the right principles. Closing the gap is mostly *layering* — pulse-feature preservation, constitution gating, eight-principle and zang-fu pull-side views — rather than rebuilding. The data is largely already there. The manifesto already permits what is needed. What is missing is a TCM-aware reading surface, written by people who can speak both vocabularies, that sits over signals Bios already collects. None of the recommendations in this audit require Bios to become a TCM application. All of them are within the existing architecture.
