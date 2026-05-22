# Cardiology Audit — Bios as a Patient-Side Cardiac Observation Feed

**Scope:** Bios's clinical reach as a longitudinal cardiology-relevant observation layer, evaluated from the perspective of a board-certified general cardiologist with subspecialty exposure to interventional cardiology, clinical cardiac electrophysiology, and advanced heart failure / transplant.
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Western cardiology, ESC and AHA/ACC/HRS guideline-anchored. Not an electrophysiology-only or HF-only audit — written so a general cardiologist with reasonable subspecialty literacy can decide whether Bios is useful as patient-side context in a typical out-patient or device clinic. The catalogue entry that anchors this audit is [MEDICAL_SPECIALTIES_WORLDWIDE.md §1.2](MEDICAL_SPECIALTIES_WORLDWIDE.md) ("Cardiology (general, interventional, electrophysiology, heart failure, adult congenital)").
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt), [SleepApneaPattern.kt](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt), [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), [MedicationAnnotationRepo.kt](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt), [RiskProfile.kt](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt). Skimmed first: [MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md) (primary-care audit) — this audit deliberately does not re-litigate findings already closed there.

---

## Executive summary

Bios is, from a cardiology-specialist standpoint, the most clinically literate consumer health platform I have audited in its size class. The post-primary-care work has closed the items that an out-patient generalist would have flagged: the URGENT tier is reachable ([EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt)), hypertension is a first-class pattern with home-BP median semantics ([HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt)), a medication-annotation surface exists ([MedicationAnnotationRepo.kt](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt)), and the alert-content policy is the most clinically defensible I have encountered in a consumer product. The dyslipidemia pattern is multi-marker including ApoB, the lipid bands are NCEP-anchored, the BP urgency pattern correctly references the universal 180/120 crisis cutoff, and the AFib screening pattern explicitly cites the Apple Heart Study (Perez 2019) and quotes a realistic 34 % PPV. These are not the choices of a wellness app.

The cardiology lens nevertheless surfaces a set of gaps that a generalist would not notice. Ordered by clinical impact on the patient population a cardiologist actually sees:

1. **AFib detection is a baseline-deviation pattern, not a rhythm-strip analysis.** [ConditionPatterns.kt → atrialFibrillationScreen](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) gates on HRV "irregularity" via z-score against a personal baseline, not on the pulse-interval irregularity that defines AF. The Apple Heart Study, mAFA-II (Guo 2019), Fitbit Heart Study (Lubitz 2022), and the entire FDA-cleared PPG-AFib category run a Poincaré / RR-irregularity classifier on the **raw RR series** ([PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) computes and exposes those intervals). Bios *has the substrate*, declines to use it, and ends up with a pattern that fires when an owner's HRV trend shifts — physiologically a different event from a PPG-derivable AF episode. This is the single most clinically impactful cardiology gap, and it is closeable inside the existing architecture.
2. **PPG waveform morphology is computed and discarded.** [PpgSignalProcessor.kt:120-152](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L120-L152) computes peak amplitudes, peak-amplitude CoV, rise-time-relevant series, dichrotic-notch context, and RR series — and returns only `rrIntervalsMs`, `sqiScore`, `peakCount`, `durationSec`. The seven prior tradition-medicine audits (TCM §2.2, Sowa Rigpa §2.3, Kampo §2.7, Korean §2.8, Siddha §2.3, Unani, others) all flag the same finding from their angles. The Western cardiology argument is independent and equally strong: augmentation index, pulse-wave-velocity proxies, reflected-wave timing, and the dicrotic-notch position are validated surrogates for arterial stiffness and central blood pressure (Vlachopoulos 2010; Townsend 2015; ESC 2018 stiffness consensus). Preserving these features would unlock a screening surface for arterial stiffness — which **none** of the consumer wearable category currently offers.
3. **No premature-beat / ectopy surface.** PPG can flag isolated ectopic beats with reasonable sensitivity (Bashar 2019; Eerikäinen 2020). High PVC burden (>10–15 %) is the threshold at which PVC-induced cardiomyopathy enters the differential (Baman 2010; Park 2018). Bios's existing PPG-IBI pipeline could flag a **PVC-burden estimate** as a slow-rolling metric without claiming arrhythmia diagnosis. Currently there is no metric, no pattern, no surface.
4. **Heart failure decompensation is unaddressed despite all the inputs being present.** The Boston Scientific HeartLogic algorithm (Boehmer 2017; Gardner 2018) — FDA-cleared for CRT-D — predicts HF decompensation 7–14 days ahead from RHR trend, nocturnal RR, activity decline, S3-gallop heart sounds, and thoracic impedance. Bios has RHR, nocturnal HR, activity, sleep fragmentation, body weight (via Withings, [MetricType.BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt)) — every input the device-side algorithm uses except impedance. There is no HF-decompensation pattern. For the ~6.7M US patients with HFrEF/HFpEF, this is the single most consequential missing pattern.
5. **No orthostatic / POTS pattern.** Bios has BP, HR, and the `FALL_EVENT` companion stream from Virgil; the fall-orthostatic pattern uses BP as a *corroborator* only. There is no first-class pattern that fires on **ΔHR ≥30 bpm within 10 min of standing without ≥20/10 mmHg BP drop** — the canonical POTS criterion (Sheldon 2015 consensus; Vernino 2021 NIH workshop). Long-COVID POTS is a population that didn't exist in 2019 and is now sizeable; Bios collects exactly the data that defines it and emits no signal.
6. **No exercise-HR-recovery (HRR) derivation.** HRR1 (the heart-rate fall from peak in the first minute post-exercise) is one of the strongest single prognostic indicators in cardiology — a Cole 1999 finding repeated across cohorts (Lauer 2007; Jouven 2005). Bios has [EXERCISE_SESSION](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) with `avg_hr_bpm` plus continuous heart-rate readings, so the derivation is mechanically straightforward. It is not derived. This is the closest Bios can come to a non-invasive stress-test surrogate, and the substrate is already there.
7. **QTc and Torsades-risk medication context.** Bios does not estimate QT (wearable QT is unreliable outside single-lead ECG and that is the correct call). However the medication-annotation surface ([MedicationAnnotationRepo.kt](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt)) is free-text and does not understand Torsades-risk classes. A cardiologist starting sotalol, dofetilide, methadone, IV haloperidol, or an azole/macrolide on a patient already on a QT-prolonging agent should *want* Bios to surface that — even as a pull-side warning. The CredibleMeds list is the canonical source; the data is on record at Bios's annotation layer, just not categorised.
8. **No single-lead ECG ingestion path.** Apple Watch, Withings ScanWatch, KardiaMobile, Samsung Galaxy Watch, Fitbit Sense, and WHOOP MG all emit single-lead ECG strips. [DATA_MODEL.md:45](../DATA_MODEL.md) lists `ecg_waveform` as `[planned]`. No adapter consumes it. From a cardiology standpoint a 30-second Lead-I strip with an AFib classification result is the most clinically actionable signal a wearable produces, and Bios silently throws it away.
9. **Cardiac implantable electronic device (CIED) data is entirely absent.** Medtronic CareLink, Boston Scientific LATITUDE, Biotronik Home Monitoring, Abbott Merlin@home — pacemaker, ICD, CRT, and ILR remote-monitoring streams are the single largest source of cardiology-relevant home data, and Bios has no ingestion path. This is partly out of scope (the data is vendor-locked, no FHIR endpoint), but acknowledging the gap is important: a cardiologist auditing Bios for a CIED patient will read the manifesto and conclude that the most clinically dense home signal is invisible.
10. **No cardiac-rehabilitation surface.** Bios's RHR + activity + sleep + HRV stack is *exactly* what Phase 2 / Phase 3 cardiac rehab programmes track. Post-MI, post-CABG, post-PCI, and post-HFrEF rehab is an emerging at-home use case (AACVPR home-based rehab guidance, 2019; CMS reimbursement for virtual cardiac rehab, 2021). Bios does not know that an owner is in a rehab window, does not adjust thresholds to the post-event reconditioning trajectory (RHR is *expected* to be elevated for weeks after an MI), and has no surface for a cardiologist to read a rehab trajectory.

Items 11–16 (sudden cardiac arrest detection, pulmonary embolism convergence, lipid → ASCVD calculator, Lp(a) and hsCRP advanced lipid panel, anticoagulation context, phonocardiography for aortic stenosis) are flagged as lower-priority but discussed below.

---

## 1. What Bios already does well, viewed from the cardiology bench

| Quality | Evidence | Why a cardiologist cares |
|---|---|---|
| **AFib screening explicitly cites the canonical literature and the PPV honestly** | [atrialFibrillationScreen.references](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) cites Perez 2019 (Apple Heart Study) and quotes "34% positive predictive value for AFib on follow-up ECG — meaningful for screening but not diagnostic" | This is the framing the field landed on after the 2019–2022 wave of large-scale PPG-AFib studies. A pattern card that quotes the actual PPV and tells the owner that "wearable HRV data cannot definitively detect AFib — clinical ECG is required" is what a cardiologist would write themselves. Compare to Apple's own watchOS UX, which under-discloses PPV. |
| **Hypertension pattern uses **median of multiple readings** with a 3-reading floor over a 7-day window** | [HypertensionPatterns.hypertensionEmerging](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) — `absoluteWindowHours = 168`, `absoluteMinReadings = 3`, median check | This is the home-BP convention (ESH 2023 ABPM/HBPM guidance, ACC/AHA 2017 §8). Most consumer apps fire on a single reading and produce constant white-coat-style false positives. Bios's median + minimum-readings gate is the right shape. |
| **Hypertensive crisis is a separate `URGENT` pattern with the universal 180/120 cutoff** | [HypertensionPatterns.hypertensiveUrgency](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) — `severityFloor = URGENT`, single-reading semantics, explicit "rest 5 min and re-check" guidance | The "re-check after 5 minutes" protocol is the canonical AHA crisis-management guidance. Most consumer health apps either don't recognise the threshold or fail to instruct the re-check, which leads to ED over-utilisation. Bios's text reads like a hypertension-clinic intake form. |
| **Dyslipidemia is multi-marker including ApoB** | [BiomarkerConditionPatterns.dyslipidemiaSignature](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) — LDL ≥160 + (HDL <40 OR TG ≥200 OR ApoB ≥120, Sniderman 2019) | ApoB inclusion is ahead of where most consumer apps are. ApoB is the AACC 2023 / EAS 2020 atherogenic-particle-count gold standard and is where lipid management is going clinically. |
| **Personal baseline is the unit of comparison for trend patterns; absolute cutoffs reserved for unambiguous values** | [SignalRule.isAbsolute](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) splits trend-relative from absolute-cutoff evaluation | An endurance athlete with an RHR of 42, a beta-blocked HFrEF patient with an RHR of 54, and a 78-year-old on dronedarone all need *different* baselines. Bios captures that natively. |
| **Medication context appended to every alert** | [AnomalyDetector.kt:376-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L376-L378) reads `medicationRepo.formatActiveContext()` and appends to the explanation | A rate-controlled AFib patient's RHR of 58 with "annotated current medications: metoprolol, apixaban" reads differently than the same RHR without context. This denoising is exactly what the primary-care audit asked for and is now shipping. |
| **Bradycardia pattern explicitly carves out athletic conditioning and rate-control therapy** | [EmergencyVitalPatterns.bradycardiaCritical](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) explanation text names "highly trained endurance athletes" and "documented rate-control medication" | The single most common false-positive case for a 35-bpm RHR alert. Most consumer apps don't acknowledge it; Bios writes it into the explanation. |
| **Sleep apnea is recognised as cardiology-adjacent and is wired** | [SleepApneaPattern](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt) gates on AHI ≥5 with SpO2 + RHR corroborators; cites Somers 2008 AHA/ACC scientific statement | OSA is the strongest modifiable AFib-recurrence risk factor (Kanagala 2003; Linz 2018) and a major HF-with-preserved-EF driver. Treating it as a respiratory-only pattern would miss the cardiology relevance; Bios correctly cites the cardiology literature. |
| **Region-aware hypertension thresholds with documented choice rationale** | [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) header comment explicitly explains the ACC-AHA 130/80 vs ESC/NICE/JSH 140/90 split and chooses 130/80 as the "conservative universal floor" for v1 | Most consumer apps pick one number with no acknowledgement that the choice differs by jurisdiction. The audit-trail discipline matters when a cardiologist is being asked to interpret an alert that originated outside their own guideline jurisdiction. |
| **Risk-profile entity exists for ASCVD-relevant family history** | [RiskProfile.firstDegreeCadEarly](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt) captures first-degree CAD <55/<65 — the strongest single ASCVD risk multiplier | A 45-year-old male with a first-degree MI at 50 is a different ASCVD calculation than the same man without that history. The substrate to run a Pooled Cohort Equation is present even if Bios doesn't currently run it. |

These are not parity wins; they are areas where Bios is **ahead** of the consumer-wearable category as a cardiologist reads it.

---

## 2. Cardiology-specific gaps, ordered by impact

### 2.1 AFib detection is the wrong shape

[ConditionPatterns.atrialFibrillationScreen](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) is a trend-deviation pattern:

```
HEART_RATE_VARIABILITY IRREGULAR 2.0σ 12h  (weight 1.5)
RESTING_HEART_RATE     ABOVE     1.5σ 12h  (weight 1.0)
BLOOD_OXYGEN           BELOW     1.0σ 12h  (weight 0.6)
minActiveSignals = 2
```

This is a useful pattern. It is not, however, an AFib screen — it is a "your autonomic state has shifted irregularly while resting HR climbed" pattern, which fires on infection, dehydration, alcohol withdrawal, thyrotoxicosis, post-vaccination autonomic perturbation, and dozens of other things that look identical to it on a personal-baseline z-score. The Apple Heart Study (Perez 2019), Huawei mAFA-II (Guo 2019), Fitbit Heart Study (Lubitz 2022), and the underlying classifiers (Tison 2018; Bumgarner 2018 KardiaMobile) all do the same fundamental computation: **classify the RR-interval series as regular or irregularly irregular** using a Poincaré-plot dispersion metric (commonly the dispersion of successive ΔRR), the sample-entropy / turning-point ratio, or a CNN over a transformed IBI tachogram.

The substrate is already present: [PpgSignalProcessor.extract](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) returns `rrIntervalsMs` — the exact input those classifiers consume. Bios passes the IBI series to `HrvAnalyzer` and then to `BaselineEngine`, never running a rhythm classifier over it.

A defensible cardiology-grade addition:

1. Add `IRREGULAR_RHYTHM_BURDEN` as a [MetricType](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) (PERCENT, CARDIOVASCULAR) computed from PPG sessions: fraction of valid windows over the past N days that the RR-series classifier scored as irregularly irregular.
2. Add a new `paroxysmal_afib_screen` pattern that fires on `IRREGULAR_RHYTHM_BURDEN ≥X %` (X to be tuned against an internal reference, but the Apple Heart Study used 5 of 6 consecutive irregular tachograms within 48 h as the notification gate).
3. Keep the existing HRV-irregularity pattern but **rename it** to something honest like `autonomic_pattern_shift` so the AFib name isn't claimed by a pattern that isn't an AFib screen.

Manifesto-aligned: the rhythm classifier is computed on-device, the burden is a passive observation, the pattern reports data and recommends ECG confirmation. No new push-side judgment, no diagnosis claim, no second-person language. The Apple Heart Study and mAFA-II have established the regulatory and ethical framing for exactly this surface.

Why this is the #1 gap: PPG-based AFib detection is the single most clinically-impactful wearable cardiology feature of the last decade. Apple, Fitbit, Samsung, and Huawei have all FDA / CE / NMPA-cleared their version. Bios has the substrate, the literature awareness (the existing pattern's citation list is correct), and the manifesto-aligned framing already, and is one classifier away from a real screening surface.

### 2.2 PPG morphology is computed then discarded

[PpgSignalProcessor.kt:120-142](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L120-L142) computes peak amplitudes, trimmed amplitude CoV, and infrastructure that touches dichrotic-notch position — and returns nothing but the IBI series and a SQI score. The seven traditional-medicine audits (TCM §2.2, Sowa Rigpa §2.3, Kampo §2.7, Korean §2.8, Siddha §2.3, Unani, Indonesian/Filipino) all noted that pulse-quality morphology is discarded; the Western cardiology argument is independent.

**Validated cardiovascular markers derivable from PPG waveform morphology:**

| Feature | Clinical correlate | Citation anchor |
|---|---|---|
| Augmentation index (AIx) | Arterial stiffness; central BP estimation | Vlachopoulos 2010; Townsend 2015 (AHA stiffness statement); ESC 2018 |
| Pulse-wave-velocity proxy (rise-time, foot-to-foot) | Aortic stiffness; CV mortality predictor | Mitchell 2010 (Framingham); Ben-Shlomo 2014 meta-analysis |
| Reflected-wave timing | Central aortic pressure proxy | Westerhof 2017; Karamanoglu 1993 |
| Dichrotic-notch position | Diastolic function proxy; vascular tone | Allen 2007; Elgendi 2012 |
| Peak-amplitude CoV (with respiration removed) | Vasomotor tone; sympathetic activity | Selvaraj 2008 |

These are not exotic. Withings BPM Core, Aktiia, Empatica, and the academic vascular-aging literature all surface a subset. None of the major consumer wrist wearables surface any of them.

A minimal change: extend [PpgResult](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L267-L295) with a `PulseWaveformFeatures` nested data class carrying peak-amplitude trimmed mean, peak-amplitude CoV, rise-time mean, rise-time CoV, decay-asymmetry index, dichrotic-notch position. Persist as derived metrics in a new `MetricType.PPG_WAVEFORM_*` family (no waveform stored, only the statistical summaries — keeps storage discipline). No new condition pattern needed yet; the data exists, the pull-side reference view can render it, and a future arterial-stiffness pattern has somewhere to live.

The recommendation overlaps the seven prior tradition-medicine audits, which is itself an argument for prioritisation: a single data-structure change closes a recurring finding across very different reading frames.

### 2.3 No ventricular ectopy / PVC-burden surface

PPG cannot replace ECG for ventricular vs supraventricular ectopy discrimination, but it can flag *ectopic beats* with reasonable specificity (Bashar 2019; Eerikäinen 2020 — Fitbit Charge feasibility). High PVC burden (>10–15 % over 24h Holter) is the threshold above which PVC-induced cardiomyopathy enters the differential (Baman 2010 — 24 % at 24 % burden; Park 2018 — review). For an EP cardiologist, **a Bios surface that reports estimated ectopy burden across the past 7–30 days would be one of the most useful signals a wearable could surface** — it answers the question patients ask after an ablation ("is it back?") and the question physicians ask before referring to EP ("is this worth a Holter?").

Practical shape: PPG-derived `ECTOPY_BURDEN_ESTIMATE` (PERCENT, CARDIOVASCULAR) computed as the fraction of valid IBI windows containing a beat that is >25 % shorter than the rolling median followed by a compensatory pause >25 % longer than the median — the classical PVC PPG signature. Surface as an observation; a pattern that fires above a literature-anchored cutoff (the 10 % threshold from Niwano 2009; the 24 % Baman threshold for definite cardiomyopathy risk) is the next step.

No claim of arrhythmia diagnosis. Bios reports an estimate; the cardiologist orders the Holter.

### 2.4 Heart failure decompensation is unaddressed despite all inputs being present

The Boston Scientific HeartLogic algorithm (Boehmer 2017 MultiSENSE; Gardner 2018) is FDA-cleared for CRT-D devices and predicts HF decompensation 7–14 days ahead with ~70 % sensitivity at one false alert per patient-year. Its inputs:

| HeartLogic input | Bios analogue | Status |
|---|---|---|
| First heart sound (S1) intensity | — | Not present (would require microphone-based phonocardiography) |
| Third heart sound (S3) intensity | — | Not present |
| Thoracic impedance | — | Not measurable without device |
| Respiratory rate | [MetricType.RESPIRATORY_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) | **Present** |
| Day/night resting HR | [MetricType.RESTING_HEART_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) | **Present** |
| Activity | [MetricType.ACTIVE_MINUTES](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [STEPS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) | **Present** |
| Body weight (independent inputs, daily) | [MetricType.BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) via Withings | **Present** |

The wearable-derivable subset is the **HeartLogic-lite** signal that Cleveland Clinic / Cedars-Sinai / Mayo are actively prototyping for non-CIED HF populations. The Conraads 2011 OptiLink trial and the Hindricks 2014 IN-TIME trial both demonstrated that the RHR + nocturnal RR + activity + weight composite predicts decompensation; the HF community has been waiting for a consumer-side implementation that respects patient autonomy.

**Concrete recommendation:** a `hf_decompensation_prodrome` ConditionPattern in a new `HeartFailurePatterns.kt`:

- Sustained day-RHR ↑ ≥1.0σ for 7 days (Conraads 2011)
- Sustained nocturnal RR ↑ ≥1.0σ for 5 days (Boehmer 2017)
- Activity-minutes ↓ ≥1.0σ for 7 days
- Body weight ↑ ≥2 kg over 3 days OR ≥2.3 kg over 7 days (canonical HF self-management threshold; ACC/AHA 2022 HF guideline)
- `minActiveSignals = 3`, `severityFloor = ADVISORY` (not URGENT — HF prodrome is days, not minutes)

This pattern should **only fire when the owner has self-identified as having an HF diagnosis** via a new `RiskProfile.knownHeartFailure` boolean or a future `PhysiologyState.HEART_FAILURE_DIAGNOSED`. Firing a HF prodrome alert on a 32-year-old with a viral illness would be exactly the kind of false positive that erodes alert credibility.

For the ~6.7M US HF patients (and the comparable European cohort), this is the single most consequential missing pattern, and Bios has every wearable input the FDA-cleared HeartLogic device-based algorithm uses except impedance.

### 2.5 No orthostatic / POTS pattern

Bios has BP (via Withings, Samsung, manual entry), HR (continuous), and `FALL_EVENT` (Virgil). The existing [CompanionConditionPatterns.fall_orthostatic_pattern](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt) uses BP as a corroborator for fall events. There is no first-class orthostatic pattern.

The canonical POTS criteria (Sheldon 2015 heart-rhythm-society consensus; Vernino 2021 NIH workshop): **sustained HR increase ≥30 bpm within 10 minutes of standing (≥40 bpm in adolescents) without orthostatic hypotension (≥20/10 mmHg drop)**, symptoms ≥3 months. The wearable-derivable subset is the HR rise on standing — the BP component requires a cuff, but the HR component is fully detectable from continuous PPG paired with an accelerometer-derived posture transition.

Post-COVID POTS has become a sizeable population that did not exist in 2019 (Bisaccia 2021; Raj 2021 Vanderbilt cohort). Long-COVID clinics across major academic centres are diagnosing it routinely. Bios collects exactly the data that defines it. A `postural_orthostatic_pattern` ConditionPattern gating on the HR rise within 10 minutes of accelerometer-detected supine→standing transitions would surface a population the cardiology and rehabilitation services are actively trying to identify.

Distinct from this, **orthostatic hypotension as a standalone pattern** (≥20/10 mmHg drop on standing) belongs in the cardiology library, not buried in the fall-correlation pattern. OH is a major fall predictor and a clinically actionable finding independent of any fall event.

### 2.6 Heart-rate recovery (HRR) is underivable from the current schema

Cole 1999 — abnormal HRR1 (HR fall <12 bpm in the first minute post-exercise) doubled mortality in a 6213-patient cohort. Jouven 2005 (Paris Prospective Study) — abnormal HRR1 carried an independent risk multiplier for sudden cardiac death. The signal is one of the strongest single prognostic indicators in non-invasive cardiology.

Bios has [EXERCISE_SESSION](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) with `start_utc`, `end_utc`, `avg_hr_bpm` (per [DATA_MODEL.md](../DATA_MODEL.md) sidecar fields) plus continuous [HEART_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) readings. The derivation is mechanical:

- Take the peak HR within the session window.
- Take the HR at session_end + 60s (HRR1) and + 120s (HRR2).
- Compute the fall: peak − recovery.
- Emit as `HR_RECOVERY_1MIN` and `HR_RECOVERY_2MIN` derived metrics.

Reference values (Cole 1999, Imai 1994): HRR1 ≥12 bpm normal (≥18 bpm fit), <12 bpm abnormal. The wearable category has not surfaced this systematically — Garmin's "training effect" composite is the closest, and it conflates HRR with several other signals. Bios is positioned to be the clean implementation.

A pattern that fires on a sustained HRR1 decline over multiple exercise sessions would be a cardiology-grade prognostic surface that no consumer wearable currently offers.

### 2.7 QTc and Torsades-risk medication context

This is the gap that needs the most nuance. Wearable QT estimation is unreliable outside single-lead ECG, and the cardiology field is rightly cautious about consumer-app QT estimation (the failure mode is missed Torsades or, worse, false reassurance). Bios's decision to *not* estimate QT is correct.

The cardiology-relevant addition is **medication-class awareness**. The CredibleMeds list classifies QT-prolonging drugs into Known Risk, Possible Risk, Conditional Risk, and Avoid in Congenital LQTS categories. The free-text [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt) entry "sotalol 80 mg BID" is currently a string; it could be a classified entry that triggers a pull-side warning when a second QT-prolonging drug is added.

Concrete shape: a small Kotlin data class `QtProlongingDrugRegistry` that maps RxNorm codes (or free-text fuzzy match) to CredibleMeds tier. When the owner adds a second drug from the registry, a pull-side `MedicationInteractionView` surfaces "Annotated medications include 2 agents associated with QT prolongation (sotalol — Known Risk; methadone — Known Risk). Discuss with your prescribing clinician." No push. No diagnosis. Information that is on file and that the owner can ask for.

This is manifesto-aligned (pull-side, owner-asked, never pushed), low-engineering (a static map plus a screen), and substantially raises the safety profile for owners on the ~200 drugs in the CredibleMeds Known + Possible Risk lists. Methadone, antipsychotic, fluoroquinolone, macrolide, ondansetron, and class-III antiarrhythmic users are the high-value populations.

### 2.8 No single-lead ECG ingestion path

Apple Watch Series 4+, Samsung Galaxy Watch 4+, Withings ScanWatch, KardiaMobile, WHOOP MG, and Fitbit Sense all emit single-lead ECG strips with vendor-derived classifications (Sinus / AFib / Inconclusive). [DATA_MODEL.md:45](../DATA_MODEL.md) lists `ecg_waveform` as `[planned]` and no adapter consumes it.

A 30-second Lead-I rhythm strip is, from a cardiology standpoint, the **single most clinically actionable signal a wearable produces.** Bios silently throws it away. The Apple HealthKit API exposes the waveform and the classification; the Samsung Health SDK exposes the same; KardiaMobile has its own SDK with raw-trace export.

Minimal scope: add `ECG_STRIP` as a [MetricType](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) (CARDIOVASCULAR, EVENT) with sidecar fields for `vendor_classification` (string enum: SINUS / AFIB / INCONCLUSIVE / OTHER), `duration_sec`, `sampling_rate_hz`. Optionally persist the raw waveform in [DATA_MODEL.md](../DATA_MODEL.md)'s `raw_payload` field — owner-deletable, never leaves the device — for clinician review. The FHIR export already maps to `Observation`; ECG fits cleanly as a `Media`-attached or `DocumentReference`-shaped resource.

The cardiology use case is straightforward: an owner with palpitations does an ECG on their Apple Watch, Bios captures the strip with the AFib classification and the timestamp, and the owner brings the FHIR export to the appointment. Currently that flow requires the owner to take screenshots and email them.

### 2.9 Cardiac implantable electronic device (CIED) data is invisible

Medtronic CareLink, Boston Scientific LATITUDE, Biotronik Home Monitoring (the pioneer of the category, 2001), and Abbott Merlin@home are the four major pacemaker / ICD / CRT remote-monitoring streams. They emit a dense daily packet: rhythm episodes, device diagnostics, intrathoracic impedance trend (Medtronic OptiVol; Boston Scientific HeartLogic), pacing percentage, lead impedance, battery status. For a CIED patient — which means most stable HF, most heart-block, most secondary-prevention ICD, and most ablation-rejection AFib patients — this stream is the most clinically dense home signal that exists.

Bios has no ingestion path. This is partly correct (the data is vendor-locked behind clinician-portal access, no patient-facing FHIR endpoint exists for any of the four), but acknowledging the gap matters:

- A cardiologist auditing Bios for a CIED patient will read the manifesto, conclude that the most clinically dense home signal is invisible, and reduce their use of the Bios trace accordingly.
- The patient-side workaround is to manually transcribe the quarterly remote-monitoring summary into the medication-annotation or `LoggedEvent.note` surface — feasible but lossy.
- A future advocacy / FHIR-push direction is to lobby for patient-facing API access to the remote-monitoring streams. The CARIN Alliance and ONC are pushing for it; Bios is in a position to be ready to consume it when it lands.

For now, the manifesto-aligned step is a documented surface acknowledgement: a `CIED_REMOTE_MONITORING_SUMMARY` free-text annotation type with a structured prompt ("Most recent CareLink / LATITUDE / Home Monitoring / Merlin summary, date and key findings") so the cardiology-context is at least *recorded* alongside the wearable trace.

### 2.10 No cardiac-rehabilitation surface or context

Phase 2 (outpatient supervised, 12 weeks post-event) and Phase 3 (long-term maintenance) cardiac rehab is one of the strongest mortality-reducing interventions in cardiology (Anderson 2016 Cochrane — 26 % reduction in CV mortality; Dibben 2023 update). Home-based rehab (AACVPR 2019 statement; CMS-reimbursable since 2021) is rising as the default for low-to-moderate-risk post-MI / post-PCI / post-CABG / post-valve / stable HFrEF patients.

Bios's RHR + HRV + activity + sleep stack is *exactly* what a Phase 2 rehab programme tracks. The mismatch:

1. **No rehab-phase awareness.** A patient 4 weeks post-CABG will have an elevated RHR, depressed HRV, low active-minutes — all by design as part of normal post-surgical reconditioning. Bios's current patterns will fire `cardiovascular_stress`, `cardiorespiratory_deconditioning`, `recovery_deficit` on this trajectory continuously. The owner gets a stream of false alerts during the precise period a cardiologist wants them to follow a careful reconditioning curve.
2. **No structured rehab-trajectory view.** A cardiologist seeing a post-MI patient at 8 weeks should be able to ask: "What's the RHR trend since the event? HRR1 trend? Active-minute trend? How does it compare to a normal recovery curve?" Bios has every input; there is no view.

Concrete recommendation: add `PhysiologyState.CARDIAC_REHAB_PHASE_2` / `CARDIAC_REHAB_PHASE_3` to the existing [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) machinery that already gates other patterns. Suppress the false-positive patterns above. Add a pull-side `CardiacRehabTrajectoryView` that renders RHR / HRV / active-minutes / sleep efficiency / HRR1 (per §2.6) over the past N weeks anchored to a user-entered event date. This is a high-leverage, low-engineering surface that would make Bios the **first** consumer health app with a serious rehab trajectory view.

### 2.11–2.16 Lower-priority cardiology gaps

**2.11 Sudden cardiac arrest detection.** [docs/ARCHITECTURE.md:209](../ARCHITECTURE.md) names the URGENT tier as the relevant infrastructure ("Acute anomaly... option to call emergency services"). Apple Watch's fall-detection + crash-detection auto-dial is the consumer-side reference. Bios's `FALL_EVENT` companion stream (Virgil) covers the falls case; cardiac arrest detection would require sustained PPG-no-pulse + accelerometer-no-motion convergence. Real, but engineering-heavy and overlaps with what Apple already ships at the OS level — defer in favour of higher-yield items, but the URGENT-tier infrastructure to support it exists.

**2.12 Pulmonary embolism.** Sudden unexplained tachycardia + hypoxia + sustained RR elevation is the classic PE convergence signal — a multi-signal pattern that Bios's convergence-reasoning is well-suited to detect. No `pulmonary_embolism_convergence` pattern exists. A pattern that gates on: sudden RHR step-change ≥20 bpm + new RR ≥22 + SpO2 ≥2 % below baseline + active-minutes-drop, all within a 24h window after a sustained low-activity period (the PE risk-factor proxy: post-surgery, post-flight, post-long-illness), would surface a population that's currently invisible to wearable monitoring. The PE community (Konstantinides 2020 ESC guideline) has not had a wearable surface to lean on.

**2.13 Lipid panel → ASCVD risk calculation.** Bios has LDL, HDL, TG, total cholesterol, ApoB, hsCRP. The [RiskProfile](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt) entity has first-degree-CAD-early. The Pooled Cohort Equations (Goff 2014; ACC/AHA 2018 update; SCORE2 for Europe, Hageman 2021) take age, sex, race, total cholesterol, HDL, systolic BP, treated-hypertension status, smoking, diabetes — *every input* is present in the existing schema or addable as a [RiskProfile](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt) field. A pull-side `ASCVD10YearRiskView` that computes the 10-year ASCVD risk band would be a directly cardiology-aligned surface. **Lp(a) is a notable absent biomarker** — it is the single most important inherited atherogenic factor (Tsimikas 2017; ESC 2019), measured once in a lifetime, and would slot into the [BIOMARKER](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) family alongside ApoB.

**2.14 Anticoagulation context.** If Bios is going to surface AFib (per §2.1), it must understand whether the owner is anticoagulated. The CHA₂DS₂-VASc score (Lip 2010) determines stroke-prevention indication; the HAS-BLED score (Pisters 2010) frames bleeding risk. INR for warfarin is a periodic lab that fits [MetricType.BIOMARKER](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt). DOACs (apixaban, rivaroxaban, dabigatran, edoxaban) don't need monitoring but do interact with the bleed-risk frame and with the alert tone — a Bios pattern that says "discuss this irregular-rhythm finding with your healthcare provider" reads differently to an anticoagulated patient than to one without. The medication-annotation surface already exists; classifying which annotations are anticoagulants is the small addition.

**2.15 Phonocardiography for aortic stenosis screening.** Smartphone-microphone-based heart sound capture for AS screening is emerging research (Thompson 2019; Kosaraju 2023) — sensitive enough for "consider an echo" framing. Not in Bios. The smartphone microphone is available, the signal-processing infrastructure (the same DSP discipline as PPG) is in place. This is a defensible **future** addition rather than a current gap, but worth flagging because AS is the most common surgical valve lesion in the >65 population and the early-detection window is large.

**2.16 Wearable ECG ingestion** is covered in §2.8.

---

## 3. Manifesto / cardiology-ethics tension points

These are friction points where the manifesto's principles and cardiology practice produce different answers. None of them require the manifesto to retreat; the point is to name where the choice has been made.

### 3.1 Rhythm-classifier silence vs cardiology practice

A general cardiologist would say: "If you have an AFib screen, run it." Apple has FDA clearance to push the notification. The manifesto's "silence is a feature" and "evaluation belongs to the owner" frame this differently — Bios reports the deviation, recommends ECG confirmation, and does not push a diagnostic claim. This is correct posture for a non-cleared instrument, but it means the AFib-screen surface (per §2.1) should ship as a *pull-side reference view* with passive observation events, **not** as an URGENT push. The Apple Heart Study population (419,297 owners, 0.5 % notification rate over 8 months) gives a reasonable expected event rate; even at that low rate, a push notification carries a regulatory and ethical weight Bios is choosing not to carry. That choice is defensible and the audit endorses it.

### 3.2 HF decompensation prediction vs not-evaluating-the-person

A HF cardiologist managing decompensation wants the system to **alert the patient that a decompensation is brewing 7–14 days out** — that's the entire point of the HeartLogic algorithm. The manifesto's framing is closer to "show the data, don't push the judgment." The resolution is: the HF prodrome ConditionPattern in §2.4 should fire at `ADVISORY` (not URGENT — 7 days isn't a minute-scale emergency), with text framed as a **data-statement plus referral**, not a diagnosis: "Resting heart rate has been above your baseline for 7 days, nocturnal respiratory rate has been elevated for 5 days, weight is up 2.3 kg over 7 days. These trends together can precede heart-failure decompensation by 1–2 weeks. Discuss these readings with your HF clinician." The cardiologist gets the early-warning signal they want; the manifesto gets the framing it requires.

### 3.3 Push-side cardiology screening vs pull-side preventive medicine

The primary-care audit's §2.2 (screening-cadence engine) framed routine prevention as a pull-side surface. Cardiology has a small set of **age-and-sex-stratified one-time screenings** that should sit on the same pull-side surface: one-time AAA ultrasound at 65–75 in male ever-smokers (USPSTF B recommendation; ESC 2017 guideline), one-time Lp(a) measurement in adulthood (ESC 2019), HDL/LDL/TG at age 21 + every 4–6 years thereafter (ACC/AHA 2018), coronary calcium scoring for ASCVD borderline-risk decision-making (ACC/AHA 2018, Class IIa). These belong in the screening-cadence engine framework the primary-care audit recommended and are mentioned here only to ensure cardiology-specific entries make the list.

### 3.4 Region-aware thresholds and the EU/JP cardiologist

Bios's hypertension pattern uses 130/80 as a "conservative universal floor" because ACC/AHA 2017 is the lowest threshold across major jurisdictions. An ESC / NICE / JSH cardiologist reading the trace will see patterns firing on owners they would not yet treat. This is acknowledged in the [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) file header and explanation text. The text framing is correct ("regional guidelines such as NICE / ESC / JSH use 140/90... the provider applies the regional guideline cutoff for diagnosis"). The cardiology cost is that the EU/JP cardiologist will see ~10 % more `hypertension_emerging` patterns than they would clinically act on. This is a defensible trade — silence on a 132/82 reading would be worse — but the eventual region-aware threshold layer (already tracked as a follow-up in the file) closes this fully.

---

## 4. What I would recommend, prioritised

**Tier A — closeable now, large clinical impact**

1. **Real PPG-based AFib screening over the RR series** (§2.1). Add a Poincaré / sample-entropy / dispersion classifier downstream of [PpgSignalProcessor.extract](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt). Surface as `IRREGULAR_RHYTHM_BURDEN` metric + `paroxysmal_afib_screen` pattern at `ADVISORY` tier, pull-side details view. Rename existing pattern to `autonomic_pattern_shift` so the AFib name isn't claimed inaccurately. The Apple Heart Study and mAFA-II have established the regulatory / ethical framing; Bios's manifesto is *more* protective than that framing requires.
2. **PPG waveform-morphology features preserved in `PpgResult`** (§2.2). One data-structure change unlocks: arterial-stiffness proxies (augmentation index, PWV proxy, dichrotic-notch position), the seven traditional-medicine audits' recurring finding, and a future cardiovascular-aging surface. No new pattern required initially.
3. **HF decompensation pattern over RHR + nocturnal RR + activity + weight** (§2.4). Gated on owner-set HF diagnosis flag (new boolean on [RiskProfile](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt) or new `PhysiologyState`). Wearable-derivable HeartLogic-lite, framed as ADVISORY with data-statement text.
4. **Single-lead ECG ingestion path** (§2.8). Add `ECG_STRIP` to [MetricType](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), wire Apple HealthKit and Samsung Health adapters to consume the vendor-classified strips. FHIR export.

**Tier B — moderate clinical impact, more engineering**

5. **HRR1 / HRR2 derivation from EXERCISE_SESSION + HEART_RATE** (§2.6). Mechanical computation; adds a Cole-1999-grade prognostic surface no consumer wearable currently has.
6. **Postural orthostatic pattern** (§2.5). Gated on accelerometer-derived supine→standing transitions; fires on ΔHR ≥30 bpm within 10 min. Addresses the post-COVID POTS population.
7. **PVC / ectopy burden estimate** (§2.3) from PPG-IBI compensatory-pause signature. Slow-rolling weekly burden metric; pattern at the 10 % literature-anchored cutoff.
8. **QT-prolonging medication interaction view** (§2.7). Static CredibleMeds-list registry, pull-side warning on second QT-prolonging agent addition. Manifesto-aligned: pull-side, owner-asked, never pushed.
9. **Cardiac rehab `PhysiologyState`** (§2.10). Suppresses false-positive `cardiovascular_stress` / `cardiorespiratory_deconditioning` / `recovery_deficit` patterns during the 12-week post-event reconditioning window; adds a `CardiacRehabTrajectoryView`.

**Tier C — important but lower-impact or longer-horizon**

10. **ASCVD 10-year-risk calculator view** (§2.13) over existing biomarker + [RiskProfile](../../android/app/src/main/java/com/bios/app/model/RiskProfile.kt) inputs. Pull-side. **Lp(a)** added as a [BIOMARKER](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) key alongside ApoB.
11. **Anticoagulation context** (§2.14) — classifier over medication-annotation strings, INR as a [BIOMARKER](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), warfarin / DOAC tagging, alert-tone awareness when AFib pattern fires on an anticoagulated owner.
12. **Pulmonary embolism convergence pattern** (§2.12). Multi-signal convergence over a 24h window post-immobilisation.
13. **CIED remote-monitoring summary annotation type** (§2.9). Documented surface acknowledgement until FHIR-pushed CIED data becomes accessible.
14. **Orthostatic hypotension as standalone pattern** (separable from §2.5 POTS).

**Do not adopt**

- A composite "cardiovascular health score" or "heart age." Same reasoning as the [DATA_MODEL.md](../DATA_MODEL.md) guard against composed epigenetic age clocks: collapsing the substrate into a single judgment number is exactly the "evaluate the person" failure mode the manifesto prohibits. Cardiologists read the components.
- Wearable QT estimation (per §2.7). Bios's decision to not estimate QT is correct and should be preserved. Medication-class awareness is the right answer; estimating QT from PPG or from the Apple Watch single-lead is not.
- Push-side AFib **notifications** (per §3.1). The Apple Heart Study population gave a sense of the event rate; the regulatory and ethical weight of a push diagnostic claim is something Bios is choosing not to carry, and the pull-side pattern surface honours that.
- Phonocardiographic AS screening on the push side (§2.15) — even if the pull-side capture surface lands, AS classification should be a pull-side reference view, not a push alert.

---

## 5. Summary line

> Bios is a clinically literate, manifesto-aligned passive cardiology observation feed that already outperforms the consumer-wearable category on AFib-screen text framing, hypertension home-monitoring statistics, dyslipidemia multi-marker reasoning, and medication-aware alert context. To match what a generalist cardiologist with subspecialty literacy would want from a patient-side observation layer, it needs (a) a real PPG-rhythm classifier over the RR series Bios already extracts, (b) preserved PPG waveform morphology, (c) a HeartLogic-lite HF prodrome pattern over the inputs already present, and (d) a single-lead ECG ingestion path. The substrate for every one of these is already in the codebase; what is missing is the cardiology-specific layer that reads it.
