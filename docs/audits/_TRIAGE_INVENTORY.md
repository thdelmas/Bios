# Triage Inventory — Audit-Derived Issues

Date: 2026-05-22
Source: 22 lens audits in /home/mia/Bios/docs/audits/
Status: ready to file via `gh issue create`

This inventory consolidates the actionable gaps surfaced across the 22 medical-perspective audits. Each entry represents a coherent body of work, not a single audit's flag — convergence count is the triage signal. Items already closed in code (URGENT-tier reachability, hypertension first-class, medication-context, region-aware screening cadence) are listed under "Closed / partially closed" with extension recommendations folded into appropriate Tier-A/B/C issues where extensions remain.

A large fraction of the tradition-medicine "gaps" are **pull-side vocabulary overlays on existing engine machinery** — most can be served by one or two shared primitives (e.g. a `TraditionalMedicineContext` projection layer, a generic `INTERVENTION_EVENT` metric, an extended `MedicationAnnotationRepo` substance vocabulary). They have been merged accordingly.

Fundamental orthogonalities (divination, spirit-illness etiology, energy medicine, homeopathic repertorisation, anthroposophic threefoldness, miasms, marma anatomy, Vijjadhara alchemy) are deliberately **not** filed — multiple audits flag these as correct silence rather than gaps to close.

## Summary
- Total consolidated issues: **32**
- Tier A (highest priority): **10**
- Tier B: **13**
- Tier C: **9**
- Already closed / partially closed: see final section (URGENT escalation infrastructure, hypertension patterns, medication-annotation, screening-cadence engine, PhysiologyState scaffolding all shipped — extensions captured in B-tier issues below)

---

## Tier A

### #1 — feat(alerts): wire URGENT escalation destination + emergency contact
**Lens convergence:** Emergency/Critical Care, Geriatrics/Palliative, Primary Care, Cardiology, Ob/Gyn
**Clinical impact:** The URGENT tier now fires but the notification terminates in the drawer. No `tel:` deep-link to the regional emergency number, no designated emergency-contact surface, no acknowledgement-timeout escalation. For hypoglycaemic, post-arrest, opioid-overdose, or hypoxic owners the alert reaches the wrong target because the owner may be cognitively impaired or alone. This is the single highest-impact EM-side recommendation across the corpus.
**Suggested files:** [android/app/src/main/java/com/bios/app/config/RegionConfig.kt](android/app/src/main/java/com/bios/app/config/RegionConfig.kt), [android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt](android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), [android/app/src/main/java/com/bios/app/alerts/AlertManager.kt](android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), new `android/app/src/main/java/com/bios/app/model/EmergencyContact.kt`, new `android/app/src/main/java/com/bios/app/alerts/UrgentAckTimeoutWorker.kt`
**Body sketch:**
```
URGENT escalation infrastructure ships ([EmergencyVitalPatterns.kt](android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [HypertensionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt)) but the notification has nowhere to go: no tap-to-call regional emergency number, no designated emergency contact, no "no acknowledgement → notify next-of-kin" timeout. EM/CC audit calls this "the URGENT pathway terminates at the notification drawer" — see docs/audits/EMERGENCY_CRITICAL_CARE_POV.md §2.1. Geriatrics/Palliative audit reinforces with hypoglycaemia + frailty cases (docs/audits/GERIATRICS_PALLIATIVE_POV.md §2.13).

Three deliverables:
1. Add `emergencyNumber: String?` to [RegionConfig](android/app/src/main/java/com/bios/app/config/RegionConfig.kt) populated per locale (911 US, 112 EU, 999 GB, 119 JP, 000 AU, etc.). Surface a `tel:` PendingIntent action in [AlertManager.sendNotification](android/app/src/main/java/com/bios/app/alerts/AlertManager.kt) when `tier == URGENT`.
2. Add an `EmergencyContact` entity (name, relationship, phone, per-tier opt-in policy, optional acknowledgement-timeout minutes). Storage in encrypted main DB, included in LETHE wipe pathway. Owner-set, owner-revocable, off by default.
3. Add a `UrgentAckTimeoutWorker` that, on a configured timeout without owner acknowledgement, executes the escalation policy (locally-composed SMS to the contact; no cloud).

Manifesto-aligned: prospective consent (owner sets the policy while competent), matches advance-directive framework. Cross-references docs/audits/MEDICAL_PROFESSIONAL_POV.md §3.2 and docs/audits/GERIATRICS_PALLIATIVE_POV.md §3.1.
```

### #2 — feat(alerts): PPG-derived AFib screening over RR series
**Lens convergence:** Cardiology, Neurology, Emergency Medicine (stroke prevention angle), TCM, Sowa Rigpa, Kampo, Korean Medicine, Siddha, Unani (latent-pulse-morphology angle)
**Clinical impact:** Today's `atrialFibrillationScreen` is a baseline-deviation pattern on HRV irregularity that does not run a Poincaré/sample-entropy classifier over the RR series Bios already extracts. It claims the "AFib" name but produces a generic autonomic-shift signal that fires on infection, dehydration, thyrotoxicosis, etc. The actual FDA-cleared category (Apple Heart Study, mAFA-II, Fitbit Heart Study) runs a rhythm classifier on the IBI tachogram — Bios has the substrate and declines to use it. AFib is the dominant cardio-embolic stroke source; this is the upstream lever EM and Neurology both prioritise.
**Suggested files:** [android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt](android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt), [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), new `android/app/src/main/java/com/bios/app/engine/RhythmClassifier.kt`
**Body sketch:**
```
Per docs/audits/CARDIOLOGY_POV.md §2.1: the existing `atrialFibrillationScreen` in [ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) is z-score-on-HRV — not a rhythm classifier. The Apple Heart Study (Perez 2019), mAFA-II (Guo 2019), Fitbit Heart Study (Lubitz 2022) all run a Poincaré-dispersion / sample-entropy / dispersion-of-successive-ΔRR classifier over the raw RR series that [PpgSignalProcessor.kt](android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) already extracts.

Deliverables:
1. Add a `RhythmClassifier` that consumes the `rrIntervalsMs` series, computes Poincaré dispersion + sample entropy + ΔRR turning-point ratio, and scores each window regular / irregularly irregular.
2. Add `IRREGULAR_RHYTHM_BURDEN` MetricType (PERCENT, CARDIOVASCULAR) = fraction of valid windows scored irregular over rolling N days.
3. Add `paroxysmal_afib_screen` ConditionPattern firing on burden ≥ X% (Apple Heart Study used 5-of-6 consecutive irregular tachograms within 48h). Severity ADVISORY (pull-side detail view, not URGENT push — manifesto-aligned, see audit §3.1).
4. Rename existing pattern to `autonomic_pattern_shift` so the AFib name isn't claimed by something that isn't an AFib screen.

Cross-references docs/audits/NEUROLOGY_POV.md §1 (AFib screen is the upstream stroke-prevention lever) and docs/audits/EMERGENCY_CRITICAL_CARE_POV.md §2.6.
```

### #3 — feat(engine): preserve PPG waveform morphology features in PpgResult
**Lens convergence:** Cardiology, TCM, Sowa Rigpa, Kampo, Korean Medicine, Siddha, Unani, Ayurveda, Indonesian (Hilot), Mongolian, Modern Non-Allopathic (osteopathy)
**Clinical impact:** [PpgSignalProcessor.kt:120-152](android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) computes peak amplitude, peak-amplitude CoV, RR CoV, and waveform morphology — and discards everything except `rrIntervalsMs` and SQI. Western cardiology wants these for arterial-stiffness proxies (augmentation index, pulse-wave-velocity proxy, dichrotic-notch position). Eight tradition-medicine audits independently flag the same finding from their pulse-quality angles. A single data-structure change unlocks every downstream surface.
**Suggested files:** [android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt](android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt), [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt)
**Body sketch:**
```
Eight independent audits flag the same latent finding. From docs/audits/CARDIOLOGY_POV.md §2.2: augmentation index, PWV proxy, reflected-wave timing, dichrotic-notch position, peak-amplitude CoV are validated arterial-stiffness and central-BP surrogates (Vlachopoulos 2010, Townsend 2015, ESC 2018) — none of the consumer wrist category surfaces them. From docs/audits/TCM_POV.md §2.2, docs/audits/SOWA_RIGPA_POV.md §2.3, docs/audits/KAMPO_POV.md §2.7, docs/audits/KOREAN_MEDICINE_POV.md §2.8, docs/audits/SIDDHA_POV.md §2.3, docs/audits/UNANI_POV.md §2.3, docs/audits/AYURVEDA_POV.md §2.7, docs/audits/OTHER_ASIAN_SYSTEMS_POV.md §2.11: 12-15 of the 28 classical Chinese pulse qualities (and the equivalent Naadi / Reg-pa / Pulso / Nabz vocabularies) are encoded in the same waveform features Bios computes and throws away.

Deliverables:
1. Extend `PpgResult` with a `PulseWaveformFeatures` nested class: peak-amplitude trimmed mean, peak-amplitude CoV, rise-time mean, rise-time CoV, decay-asymmetry index, dichrotic-notch position. Only statistical summaries persist — no raw waveform stored.
2. Add a `PPG_WAVEFORM_*` family in MetricType (CARDIOVASCULAR) for the persisted summaries.
3. No new condition pattern required initially; pull-side reference view + a future arterial-stiffness or constitutional-pulse view consume them.

No new sensors, no new permissions, no push notifications. One data structure change closes a recurring cross-lens finding.
```

### #4 — feat(alerts): sepsis screening (NEWS2 / qSOFA) URGENT pattern
**Lens convergence:** Emergency/Critical Care, Surgical, Primary Care, Geriatrics, Oncology (chemotherapy + neutropenic-fever overlap)
**Clinical impact:** Every NEWS2 input is on the bus (RR, SpO2, OXYGEN_FLOW_RATE, BP-systolic, HR, skin-temp, CONSCIOUSNESS_LEVEL); qSOFA's three inputs are present too. No pattern composes them. For post-surgical, immunocompromised, indwelling-device, and elderly owners the Surviving Sepsis Campaign mortality arithmetic (Kumar 2006 — each hour of antibiotic delay) makes this the highest-leverage missing pattern.
**Suggested files:** new `android/app/src/main/java/com/bios/app/alerts/SepsisScreenPattern.kt`, [android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt](android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt)
**Body sketch:**
```
docs/audits/EMERGENCY_CRITICAL_CARE_POV.md §2.2 lays out the case: NEWS2 (RCP 2017) inputs are all present in [MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt); qSOFA (Singer 2016 JAMA) is RR ≥22 + SBP ≤100 + altered mentation — all three on the bus. Literature anchors: Seymour 2016 JAMA (qSOFA derivation), Evans 2021 Surviving Sepsis Campaign, Henry 2019 Nat Med (TREWS).

Deliverables:
1. `sepsis_screen` pattern computing rolling-6h NEWS2 (or qSOFA where vitals are sparse); ADVISORY at NEWS2 ≥3, URGENT at ≥5 (NHS Sepsis Six trigger) or qSOFA ≥2.
2. Suppressible via PhysiologyState gating for owners with chronic-illness baseline elevation (stable COPD on home O2 scores ≥2 for SpO2 alone). Same shape as existing pattern excludedStates.
3. Same convergence machinery as [infectionOnset](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt); threshold is composite-score not signal-count.
4. Data-statement framing per existing AlertContentPolicy ("vital signs meet NHS Sepsis Six trigger criteria — seek immediate medical assessment"). Cross-references docs/audits/SURGICAL_POV.md §2.5 (post-op overlap) and docs/audits/ONCOLOGY_POV.md §2.4 (neutropenic-fever convergence).
```

### #5 — feat(alerts): peri-operative state + SSI/VTE/anastomotic-leak post-op patterns
**Lens convergence:** Surgical, Emergency Medicine (post-ED discharge), Cardiology (post-PCI/CABG rehab), Oncology (post-treatment surveillance)
**Clinical impact:** Bios has no concept of "an operation happened." The infection-onset pattern false-fires constantly on a post-op patient whose RHR is normatively elevated for 2-4 weeks, or — if dialed down — misses the genuine POD7-14 SSI. NHSN 30-day SSI surveillance, anastomotic leak (POD3-7), and post-op VTE (Wells/Caprini) are the highest-value post-discharge wearable use cases.
**Suggested files:** [android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt](android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), new `android/app/src/main/java/com/bios/app/alerts/PerioperativePatterns.kt`, [android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt)
**Body sketch:**
```
docs/audits/SURGICAL_POV.md §2.1-2.4 makes the case end-to-end. Add `PhysiologyState.PREHAB_WINDOW / POD_0_30 / POD_30_90 / CARDIAC_REHAB_PHASE_2 / CARDIAC_REHAB_PHASE_3` so the pattern engine pivots around an event date.

Patterns:
1. `ssi_surveillance` — post-op-windowed infection-onset variant; thresholds account for expected POD-day RHR/HRV/skin-temp deviation; mirrors NHSN.
2. `anastomotic_leak_screen` — POD3-7 fever + tachycardia + activity-drop (ileus proxy from STEPS).
3. `post_op_vte_screen` — sudden persistent tachycardia + unexpected SpO2 drop in otherwise-recovering owner.
4. `discharge_window` flag (EM-side, docs/audits/EMERGENCY_CRITICAL_CARE_POV.md §3.6) lowering relevant thresholds for 72h post-ED-discharge + 24/48/72h check-in.

Suppress false-positive [cardiovascularStress](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L207), [cardiorespiratoryDeconditioning](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L274), [recoveryDeficit](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L339) in the post-event reconditioning window (docs/audits/CARDIOLOGY_POV.md §2.10). Generates a discharge-window FHIR bundle the owner can share back to the surgeon/ED.
```

### #6 — feat(model): GoalsOfCare + ClinicalDirective + hospice mode
**Lens convergence:** Geriatrics/Palliative, Oncology, Emergency/Critical Care, Cardiology (HF prognosis), Modern Non-Allopathic (advance directives)
**Clinical impact:** Today's URGENT alerts tell every owner to "seek immediate medical attention" — clinically and ethically wrong for documented DNAR/comfort-only owners. The palliative-care panel calls this the strongest single intervention they would ask for: a `GoalsOfCare` gate plus a "hospice mode" toggle that drops Bios into its most-silent posture for actively dying patients while preserving symptom-burden capture.
**Suggested files:** [android/app/src/main/java/com/bios/app/model/Enums.kt](android/app/src/main/java/com/bios/app/model/Enums.kt), new `android/app/src/main/java/com/bios/app/model/ClinicalDirective.kt`, [android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt](android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt), [android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt](android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt)
**Body sketch:**
```
docs/audits/GERIATRICS_PALLIATIVE_POV.md §2.6 and §2.7 are the load-bearing references. Two coupled additions:

1. `GoalsOfCare` enum (`FULL_CODE`, `DNAR`, `DNAR_DNI`, `COMFORT_ONLY`) — owner-set, default FULL_CODE. Gate URGENT-tier suggestedAction in [EmergencyVitalPatterns.kt](android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) and [HypertensionPatterns.hypertensiveUrgency](android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt): when `COMFORT_ONLY`, replace "contact emergency services" with "this reading reflects significant physiological stress; comfort-care planning and provider contact may be appropriate per your documented goals." The reading is still surfaced; the escalation language adapts. Same gate applies to companion-pattern URGENT escalations (fall + low BP, fall + hypoglycemia).

2. `ClinicalDirective` entity storing the *existence* (not the content) of POLST/MOLST/advance-directive/healthcare-proxy + free-text location. Bios records the acknowledgement.

3. Hospice mode toggle — single switch that suppresses all non-URGENT push patterns, preserves all pull-side surfaces, preserves symptom-burden capture (#7). Cross-references docs/audits/ONCOLOGY_POV.md §2.20.

Manifesto reading: "owner is final" via prospective consent (advance-directive framework), preserves "silence is a feature" at its purest moment.
```

### #7 — feat(data): ESAS-r symptom-burden capture + PRO surfaces
**Lens convergence:** Geriatrics/Palliative, Oncology, Surgical (functional recovery PROs), Neurology (MS / concussion fatigue), Psychiatry
**Clinical impact:** Palliative medicine runs on owner-reported symptom scores; Bios captures PAIN_SCORE and CONSCIOUSNESS_LEVEL but no dyspnea, nausea, fatigue, drowsiness, appetite, anxiety, or wellbeing. ESAS-r (Watanabe 2011) and IPOS (Murtagh 2019) are the dominant instruments. Oncology, post-ICU, MS, post-concussion, post-surgical, and chronic-pain owners all benefit from the same SCORE-unit primitive.
**Suggested files:** [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [android/app/src/main/java/com/bios/app/ui/clinical/ClinicalReadingEntryScreen.kt](android/app/src/main/java/com/bios/app/ui/clinical/ClinicalReadingEntryScreen.kt)
**Body sketch:**
```
docs/audits/GERIATRICS_PALLIATIVE_POV.md §2.8 lists the full ESAS-r set; docs/audits/ONCOLOGY_POV.md §2.7/2.9 wants the same for fatigue and cancer pain trajectories; docs/audits/NEUROLOGY_POV.md §2.9/2.13 wants FATIGUE_SCORE + COGNITIVE_FOG_SCORE for MS / post-concussion / long-COVID; docs/audits/SURGICAL_POV.md §2.10 wants PROMIS/KOOS/HOOS PRO capture for functional recovery.

Add as SELF_REPORTED 0-10 NRS MetricTypes (matching existing PAIN_SCORE convention):
- DYSPNEA_SCORE, NAUSEA_SCORE, FATIGUE_SCORE, DROWSINESS_SCORE, APPETITE_SCORE (inverted), ANXIETY_SCORE, WELLBEING_SCORE (the seven completing ESAS-r)
- COGNITIVE_FOG_SCORE (MS / post-concussion / long-COVID)

Surfaces:
- Owner-loggable from the journal screen
- Trended on the pull-side detail view for any active condition pattern that names these as relevant
- Included in the FHIR Observation export (LOINC codes exist for ESAS items)

Manifesto-clean: all self-reported, all owner-controlled, pull-side displayed. No coaching, no scoring.
```

### #8 — feat(physiology): wire FRAILTY_FLAG + frailty assessment surface
**Lens convergence:** Geriatrics/Palliative, Primary Care, Surgical (pre-op risk), Kampo (Japan ageing demographic), Cardiology (older HFrEF cohort)
**Clinical impact:** [PhysiologyState.FRAILTY_FLAG](android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L37) is declared and unwired — no pattern reads it, no threshold modifier consumes it. Every wearable pattern false-fires in the frail >75 cohort because normative aging sleep architecture, deconditioning, and bradycardia look like pathology. Geriatrics panel calls this the largest single false-firing source for older adults.
**Suggested files:** [android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt](android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), new `android/app/src/main/java/com/bios/app/ui/geriatrics/FrailtyAssessmentScreen.kt`
**Body sketch:**
```
Per docs/audits/GERIATRICS_PALLIATIVE_POV.md §2.1-2.2:

1. Add `FRAILTY_FLAG` to `excludedStates` of sleep_disruption, cardiovascular_stress, cardiorespiratory_deconditioning, recovery_deficit. One-day change; closes the largest false-firing source for older adults.
2. Optional sub-banding (`FRAILTY_MILD/MODERATE/SEVERE`) so threshold relaxation matches Rockwood CFS gradient.
3. Add longer-window variants (e.g. `recovery_deficit_frail`, 30-day evaluation window) where the timescale is the relevant adjustment.
4. Pull-side frailty-assessment surface offering Fried phenotype (5 criteria), Rockwood CFS (1-9 ordinal), and FRAIL (Morley 2012) — owner-administered, never inferred. Output sets the PhysiologyState.

Cross-references docs/audits/SURGICAL_POV.md §2.7 (pre-op Fried surrogate), docs/audits/KAMPO_POV.md §5 (Japanese ageing demographic), and docs/audits/MEDICAL_PROFESSIONAL_POV.md §2.7.
```

### #9 — feat(alerts): heart-failure decompensation pattern (HeartLogic-lite)
**Lens convergence:** Cardiology, Geriatrics/Palliative, Emergency Medicine
**Clinical impact:** Boston Scientific HeartLogic (Boehmer 2017 MultiSENSE; Gardner 2018) is FDA-cleared for CRT-D and predicts HF decompensation 7-14 days ahead. Bios has every wearable input HeartLogic uses except thoracic impedance (RHR, nocturnal RR, activity, body weight via Withings). For ~6.7M US HFrEF/HFpEF patients this is the single most consequential missing pattern.
**Suggested files:** new `android/app/src/main/java/com/bios/app/alerts/HeartFailurePatterns.kt`, [android/app/src/main/java/com/bios/app/model/RiskProfile.kt](android/app/src/main/java/com/bios/app/model/RiskProfile.kt) or [android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt](android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt)
**Body sketch:**
```
docs/audits/CARDIOLOGY_POV.md §2.4 details the substrate mapping. Add `RiskProfile.knownHeartFailure` boolean OR `PhysiologyState.HEART_FAILURE_DIAGNOSED`. Gate the pattern on owner-set HF flag — firing on a 32-year-old with viral illness would erode credibility.

`hf_decompensation_prodrome` ConditionPattern:
- Sustained day-RHR ↑ ≥1.0σ for 7 days (Conraads 2011 OptiLink)
- Sustained nocturnal RR ↑ ≥1.0σ for 5 days (Boehmer 2017)
- Activity-minutes ↓ ≥1.0σ for 7 days
- BODY_MASS ↑ ≥2 kg / 3 days OR ≥2.3 kg / 7 days (ACC/AHA 2022 HF guideline self-management threshold)
- minActiveSignals = 3, severityFloor = ADVISORY (7-day prodrome, not minutes-scale)

Framing per docs/audits/CARDIOLOGY_POV.md §3.2: data statement + referral, not diagnosis. "Resting heart rate has been above your baseline for 7 days, nocturnal respiratory rate elevated for 5 days, weight up 2.3 kg over 7 days — these trends together can precede heart-failure decompensation by 1-2 weeks. Discuss with your HF clinician."
```

### #10 — feat(adapter): single-lead ECG strip ingestion path
**Lens convergence:** Cardiology, Emergency Medicine, Neurology (stroke workup)
**Clinical impact:** Apple Watch, Samsung Galaxy Watch, Withings ScanWatch, KardiaMobile, WHOOP MG, Fitbit Sense all emit single-lead ECG strips with vendor-derived classifications. From the cardiology bench this is the single most clinically actionable signal a wearable produces. [DATA_MODEL.md](docs/DATA_MODEL.md) lists `ecg_waveform` as `[planned]`; no adapter consumes it. Owners currently take screenshots and email them.
**Suggested files:** [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [android/app/src/main/java/com/bios/app/ingest/](android/app/src/main/java/com/bios/app/ingest/), [android/app/src/main/java/com/bios/app/export/FhirExporter.kt](android/app/src/main/java/com/bios/app/export/FhirExporter.kt)
**Body sketch:**
```
docs/audits/CARDIOLOGY_POV.md §2.8 makes the case: a 30-second Lead-I rhythm strip is the most clinically dense home cardiology data point. Add:

1. `ECG_STRIP` MetricType (CARDIOVASCULAR, EVENT) with sidecar fields: `vendor_classification` (string enum SINUS/AFIB/INCONCLUSIVE/OTHER), `duration_sec`, `sampling_rate_hz`, optional `raw_payload` (owner-deletable, never leaves device).
2. Wire Apple HealthKit and Samsung Health adapters to consume the vendor-classified strips.
3. FHIR export maps to `Observation` with `DocumentReference` for the waveform; or `Media` per FHIR R4 conventions.

Cross-references docs/audits/NEUROLOGY_POV.md (stroke FAST workup wants AFib evidence) and docs/audits/EMERGENCY_CRITICAL_CARE_POV.md.
```

---

## Tier B

### #11 — feat(alerts): pregnancy-specific patterns (preeclampsia + complete pregnancy gating)
**Lens convergence:** Ob/Gyn, Primary Care, Cardiology (peripartum cardiomyopathy)
**Clinical impact:** No pre-eclampsia signature despite BP ingestion + PhysiologyState being ready — the #1 preventable maternal mortality cause in the developed world. Multiple class-2 patterns (`infectionOnset`, `chronicInflammation`, `recoveryDeficit`, `metabolicDrift`, `overtraining`) false-fire constantly in T2/T3 pregnancy and erode URGENT-tier credibility for the alert that actually matters (severe-range BP).
**Suggested files:** [android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt), [android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt)
**Body sketch:**
```
docs/audits/OBGYN_POV.md §2.1-2.3 covers the gaps comprehensively.

1. `preeclampsiaSignature` pattern in [HypertensionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt): active only in PREGNANCY_T2+T3. ACOG Practice Bulletin 222 (2020): gestational HTN ≥140/90, severe-range ≥160/110 (URGENT escalation). Postpartum extension up to 6 weeks (PPCM/postpartum pre-eclampsia is the highest-mortality window — CDC PMSS 2017-2019).
2. Add `excludedStates = PhysiologyState.PREGNANCY` to: infectionOnset, chronicInflammation, recoveryDeficit, metabolicDrift, overtraining, cardiorespiratoryDeconditioning, sleepDisruption. One-line additions per pattern; large noise-reduction and reclaims URGENT credibility.
3. Postpartum replacement patterns for the high-mortality 6-week window: ppcm_screen (peripartum cardiomyopathy: HR↑, exercise intolerance, sleep dyspnea), VTE pull-side reminder (4-5× baseline through 6 weeks), postpartum-tuned mentalHealthCorrelate variant.
4. GDM cadence in ScreeningCatalog gated on PREGNANCY_T2; CGM time-in-range target shift to 63-140 mg/dL in pregnancy state.
```

### #12 — feat(alerts): acute-window patterns (anaphylaxis, opioid respiratory depression, DKA, hypotensive shock)
**Lens convergence:** Emergency/Critical Care, Psychiatry (addiction), Paediatrics (DKA), Endocrinology
**Clinical impact:** Bios's pattern engine runs ≥12h windows. Anaphylaxis is a 5-minute multi-system event. Opioid overdose's bradypnea-to-apnea trajectory is ~5 minutes from RR <8 to anoxic injury. DKA in T1D children, hypotensive shock convergence — all need a fast-loop engine alongside the existing pattern engine.
**Suggested files:** new `android/app/src/main/java/com/bios/app/engine/VitalsAccelerationDetector.kt`, [android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt](android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt](android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt)
**Body sketch:**
```
docs/audits/EMERGENCY_CRITICAL_CARE_POV.md §2.3-2.10 outlines the gap. New `VitalsAccelerationDetector` engine runs every 60s over the last 5 min of HR + SpO2 + (when available) BP, alongside the existing slow-pattern detector.

Patterns:
1. `anaphylaxis_pattern` — HR rising >30 bpm/5 min above 24h baseline + SpO2 drop ≥3% + (if BP avail) MAP drop ≥15. Severity URGENT. Gated by owner-set `KNOWN_ANAPHYLAXIS_RISK` flag (exercise produces similar HR rise kinetics).
2. `acute_respiratory_depression` — sustained RR <8 ≥2 min + SpO2 trajectory. Severity URGENT. Manifesto-aligned naloxone-prompt language (not "you should not use opioids"). Population: opioid epidemic + chronic-pain + MAT.
3. `hypotensive_shock_screen` — SBP ≤90 single reading with rest-5-min-re-check protocol; URGENT.
4. `dka_signature` — sustained glucose >400 mg/dL + Kussmaul-pattern RR + altered mentation; gated on `KNOWN_DIABETES_INSULIN_DEPENDENT` PhysiologyState. Particularly relevant for paediatric T1D — see docs/audits/PAEDIATRICS_POV.md §2.8.
```

### #13 — feat(model): screening-cadence engine extensions (region + risk + immunization)
**Lens convergence:** Primary Care, Oncology, Ob/Gyn, Paediatrics, Cardiology (one-time AAA/Lp(a)), Indigenous Americas (IHS cadence), African Traditional, Geriatrics
**Clinical impact:** The screening-cadence scaffolding ships ([ScreeningCadenceEngine.kt](android/app/src/main/java/com/bios/app/screening/ScreeningCadenceEngine.kt), [ScreeningCatalog.kt](android/app/src/main/java/com/bios/app/screening/ScreeningCatalog.kt)) but is USPSTF-only, missing hereditary-syndrome cadence shifts, missing paediatric ACIP/NIP vaccine schedules, missing Ob/Gyn-society cadences, missing IHS/PAHO/non-NHS regional alternatives, missing one-time cardiology screens (AAA, Lp(a)).
**Suggested files:** [android/app/src/main/java/com/bios/app/screening/ScreeningCatalog.kt](android/app/src/main/java/com/bios/app/screening/ScreeningCatalog.kt), [android/app/src/main/java/com/bios/app/screening/ScreeningCadenceEngine.kt](android/app/src/main/java/com/bios/app/screening/ScreeningCadenceEngine.kt), [android/app/src/main/java/com/bios/app/model/RiskProfile.kt](android/app/src/main/java/com/bios/app/model/RiskProfile.kt), [android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt](android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt)
**Body sketch:**
```
The cadence engine and `RiskProfile` exist; this is extension work across several audits:

1. Hereditary cancer syndromes (docs/audits/ONCOLOGY_POV.md §2.2): `hereditarySyndrome` enum on RiskProfile (BRCA1/2, Lynch, Li-Fraumeni, FAP, etc.) + `geneticTestingDate`. BRCA1/2 carriers get annual MRI + mammogram from 25-30 per NCCN v2.2025.
2. Paediatric vaccine schedules (docs/audits/PAEDIATRICS_POV.md §2.4): extend VaccineCatalog (currently adult-only by design) with ACIP/NIP/Green Book/NACI/CDC pediatric schedules — birth-dose HepB, 2/4/6-month series, MMR/VAR/HepA, school-entry boosters, age-11 Tdap/HPV/MenACWY.
3. Ob/Gyn-society cadences (docs/audits/OBGYN_POV.md §2.5): add ACOG/ACS variants alongside USPSTF for mammography; BRCA-modifier advancing start; ASCCP risk-based management for cervical.
4. Cardiology one-time screens (docs/audits/CARDIOLOGY_POV.md §3.3): AAA ultrasound 65-75 male ever-smokers (USPSTF B), Lp(a) once-in-lifetime (ESC 2019), coronary calcium for ASCVD borderline.
5. Non-USPSTF regional cadences (Indigenous Americas §2.5, African Traditional §1.3 implied, Oceanic §2): IHS Adult Preventive Care Bundle, NACCHO/Closing the Gap, PAHO Indigenous-health-specific, Pacific MoH frames. Configurable via RegionConfig.
6. Cancer-screening framing for the lifestyle substrate (docs/audits/ONCOLOGY_POV.md §2.13-2.18): IARC-anchored relative-risk trajectory info for tobacco, alcohol, obesity, UV, HPV/HBV vaccination — pull-side, no nudging.
```

### #14 — feat(data): generic INTERVENTION_EVENT + TreatmentCourse entities
**Lens convergence:** Modern Non-Allopathic (all 9 traditions), Sowa Rigpa, Ayurveda (Panchakarma), Siddha (thokkanam), Unani (Ilaj bil Tadbeer), Oceanic (Hoʻoponopono/karakia), Indigenous Americas (sweat lodge/ceremony), Other Asian (Nuad Thai/jamu/pijat), Oncology (chemo course), Surgical (peri-op)
**Clinical impact:** The single highest-leverage cross-tradition addition. Manual therapies (chiropractic, OMT, Nuad Thai, hilot, pijat, thokkanam, sangkal putung, *me btsa'*, *gtar ga*), ceremonial events (sweat lodge, hoʻoponopono, karakia, joik, Reiki/TT), multi-week treatment courses (Panchakarma, mistletoe, elimination diet, IV protocols, IVF stimulation), and oncology/peri-op windows all need timestamped event anchors so wearable trends can be read against them.
**Suggested files:** [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), new `android/app/src/main/java/com/bios/app/model/InterventionEvent.kt`, new `android/app/src/main/java/com/bios/app/model/TreatmentCourse.kt`, [android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt](android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt)
**Body sketch:**
```
Cross-references at least 10 audits. Two coupled primitives:

1. `INTERVENTION_EVENT` MetricType (EVENT, RECOVERY or new MANUAL_THERAPY domain) — single-timestamp event with sidecar fields: `intervention_type` (free-text or coded), `practitioner_type` (free-text), `duration_min`, `body_region` (optional). Subtypes covered by free-text owner annotation: chiropractic adjustment, OMT, craniosacral, Reiki, Therapeutic Touch, Nuad Thai, luk pra kob, hilot, pijat, thokkanam, *me btsa'* moxibustion, *gtar ga* bloodletting, *bsku mnye* oil massage, *Hijama* cupping, *Fasd* venesection, *cạo gió*, *giác hơi*, Vamana/Virechana/Basti/Nasya, Marma chikitsa, Saam acupuncture, biofeedback session, IV nutrient therapy, sauna protocol, hydrotherapy, *karakia*, *hoʻoponopono*, *yaqona*/*sevusevu*, sweat lodge, vision quest, Ngangkari healing, *joik*, *algys*, etc. Bios records the timestamp; Bios makes no claim about mechanism.

2. `TreatmentCourse` entity bracketing multi-week interventions: `start_utc`, `end_utc`, `programme_type` (free-text), `pattern_suppression` flag the AnomalyDetector reads. Enables pre/during/post comparisons; suppresses pattern-firing during expected-treatment-response windows. Covers Panchakarma (docs/audits/AYURVEDA_POV.md §2.12), elimination diets, mistletoe courses (anthroposophic), OMT series, chemotherapy cycles (docs/audits/ONCOLOGY_POV.md §2.4), IVF stimulation (docs/audits/OBGYN_POV.md §2.13), prehabilitation windows (docs/audits/SURGICAL_POV.md §2.6).

3. Rename `HealthEventType.DOCTOR_VISIT` → `PRACTITIONER_VISIT` with `practitionerType` annotation (free-text — never a closed enum of Indigenous practitioner role names). See docs/audits/AFRICAN_TRADITIONAL_POV.md §2.9, docs/audits/INDIGENOUS_AMERICAS_POV.md §2.8, docs/audits/OCEANIC_ARCTIC_POV.md §2.10.

One entity-pair closes a recurring gap across at least 10 audits.
```

### #15 — feat(data): TraditionalMedicineContext pull-side projection layer
**Lens convergence:** TCM, Ayurveda, Siddha, Sowa Rigpa, Unani, Korean Medicine, Kampo, African Traditional, Oceanic, Indigenous Americas, Modern Non-Allopathic
**Clinical impact:** Eleven traditions independently ask for the same shape: a pull-side, owner-selected, never-pushed vocabulary overlay that re-projects existing signals (HRV, RHR, sleep, skin-temp, glucose, biomarkers) onto the tradition's diagnostic axes. Single shared primitive — a `TraditionalMedicineContext` annotation surface + a constitutional/Prakriti/Sasang/Mizaj enum + pull-side projection views — serves every tradition simultaneously and doesn't violate the manifesto.
**Suggested files:** new `android/app/src/main/java/com/bios/app/tradmed/TraditionalMedicineContext.kt`, new `android/app/src/main/java/com/bios/app/tradmed/Constitution.kt`, new `android/app/src/main/java/com/bios/app/ui/tradmed/TraditionalMedicineLensScreen.kt`, [android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt)
**Body sketch:**
```
Eleven tradition audits converge on the same architectural pattern. Rather than 11 separate implementations:

1. `Constitution` enum (owner-set, optional, never inferred) — accepts:
   - TCM 9-constitution (王琦 2009): 平和/气虚/阳虚/阴虚/痰湿/湿热/血瘀/气郁/特禀
   - Ayurveda Prakriti: Vata / Pitta / Kapha + duals + Tridoshaja
   - Siddha Mukkuttram: Vaadham / Pittham / Kabam dominant
   - Sasang (Korean): Taeyangin / Soyangin / Taeumin / Soumin
   - Unani Mizaj: nine-fold compound temperament
   - Sowa Rigpa Nyepa: rLung / mKhris-pa / Bad-kan dominant
   See docs/audits/TCM_POV.md §2.6, docs/audits/AYURVEDA_POV.md §2.6, docs/audits/KOREAN_MEDICINE_POV.md §1.1, docs/audits/SIDDHA_POV.md §2.1, docs/audits/UNANI_POV.md §2.1, docs/audits/SOWA_RIGPA_POV.md §2.2.

2. `excludedConstitutions` field on ConditionPattern mirroring `excludedStates`.

3. Pull-side `TraditionalMedicineLensScreen` (Dashboard → opt-in toggle, never push) rendering existing signals through the selected tradition's vocabulary: dosha projection, Eight Principles candidate readings, kyo-jitsu axis, Triguna projection, hot/cold drift, Ama-sanchaya re-labelling of `chronic_inflammation`, Ojas-kshaya re-labelling of `recovery_deficit`, Hridroga-purvarupa for cardiovascular_stress, Nidranasha for sleep_disruption, etc. Same data, multiple vocabularies.

4. Self-report keys for the seven emotions / Triguna / Quwa-Nafsaniyya etiology (TCM §2.5, Ayurveda §2.8, Donguibogam §2.7). Owner-loggable in journal.

5. Meridian-time / Dinacharya / dosha-kala 12-window histogram of anomaly timing — pure aggregation, no new data (TCM §2.10, Ayurveda §2.4).

6. Sho-candidate annotations on existing condition patterns (docs/audits/KAMPO_POV.md §2 — `chronic_inflammation` ⇒ 瘀血/気滞/湿熱 candidates).

Manifesto-clean throughout: pull-side, owner-asks, never pushed, never auto-classifies constitution. Same machinery that lifts Smokeless/Virgil/W2F companion signals into the pattern surface lifts these projections.
```

### #16 — feat(data): extend MedicationAnnotationRepo to traditional + functional pharmacopoeias
**Lens convergence:** Ayurveda, TCM, Kampo, Korean, Siddha, Unani, Sowa Rigpa, African Traditional, Indigenous Americas, Other Asian (Vietnamese/Thai/Burmese/Indonesian/Filipino), Modern Non-Allopathic (Western herbal, homeopathic, anthroposophic), Cardiology (QT-prolonging registry), Geriatrics (Beers/STOPP)
**Clinical impact:** [MedicationAnnotationRepo](android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt) accepts free-text but is RxNorm-centric. Practitioners across 13+ traditions want the existing surface to *recognise* the substances they prescribe so the pattern-explanation builder can de-noise alerts. Cardiology wants QT-prolonging-class flagging; geriatrics wants Beers-AGS anticholinergic flagging.
**Suggested files:** [android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt](android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt), new `android/app/src/main/java/com/bios/app/data/SubstanceRegistry.kt`, [android/app/src/main/java/com/bios/app/alerts/BiomarkerReference.kt](android/app/src/main/java/com/bios/app/alerts/BiomarkerReference.kt)
**Body sketch:**
```
Extend the existing substance vocabulary to ride multiple coding systems. Cross-references docs/audits/AYURVEDA_POV.md §2.11, docs/audits/TCM_POV.md §2.9, docs/audits/KAMPO_POV.md §2.4 (148 NHI Tsumura/Kotaro/Kracie/Sanwa formulas), docs/audits/KOREAN_MEDICINE_POV.md §1.5, docs/audits/SIDDHA_POV.md §2.7, docs/audits/UNANI_POV.md §2.7, docs/audits/AFRICAN_TRADITIONAL_POV.md §2.4, docs/audits/INDIGENOUS_AMERICAS_POV.md §2.7, docs/audits/MODERN_NON_ALLOPATHIC_POV.md §2.8.

Deliverables:
1. `SubstanceRegistry` accepts: RxNorm, ATC, WHO ICD-11 Chapter 26 Traditional Medicine Module codes (covers Ayurveda/Unani/Siddha AYUSH), Tsumura/Kracie/Kotaro/Sanwa Kampo NHI codes, AHP/ESCOP Western herbal nomenclature, HAB/HPUS homeopathic codes, ethnobotanical Latin binomials + common names with locale alias, free-text fallback.
2. `BiomarkerReference`-style catalog for each substance: classical-system effect note (e.g. "*Arjuna* — cardioprotective, expected RHR/HRV directional effect"; "*yokukan san* — BPSD-target, expected HRV recovery + sleep stabilisation"; "*Sutherlandia frutescens* — adaptogenic, mild HR effects"). Pattern explanation builder reads these.
3. Class flagging for cardiology (QT-prolonging via CredibleMeds — docs/audits/CARDIOLOGY_POV.md §2.7) and geriatrics (Beers AGS 2023 anticholinergic burden, Z-drugs, long-acting sulfonylureas — docs/audits/GERIATRICS_PALLIATIVE_POV.md §2.3; anticholinergic burden score ACB).
4. Polypharmacy count surface (≥5 / ≥10) on pull-side Geriatrics screen.

Manifesto-clean: Bios never prescribes; records what the owner annotates; surfaces de-confounding context. AYUSH/ICD-11 traditional-medicine module covers the AYUSH bloc in one schema move.
```

### #17 — feat(model): RegionConfig coverage + emergencyNumber + Aboriginal/Indigenous/NZ/Pacific/Latin America/Africa
**Lens convergence:** Indigenous Americas, African Traditional, Oceanic/Arctic, Other Asian, Primary Care (emergencyNumber), Cardiology, Ob/Gyn (Medsafe / SAHPRA / COFEPRIS / ANVISA)
**Clinical impact:** RegionConfig has 6 entries (US/GB/EU/CA/AU/JP); silently falls back to EU for everything else, citing CE-mark and EMA for owners in Mexico, Nigeria, Indonesia, Mongolia, NZ, etc. The disclaimer text is meaningless to the user and the cardiovascular cutoffs are mis-sourced. Several audits also want region-specific emergency numbers (911/112/999/119/000/110), Aboriginal/IHS subflavours, and Pacific-NCD presets.
**Suggested files:** [android/app/src/main/java/com/bios/app/config/RegionConfig.kt](android/app/src/main/java/com/bios/app/config/RegionConfig.kt), [android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt](android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt)
**Body sketch:**
```
Add region configs for: NZ (Medsafe), MX (COFEPRIS), BR (ANVISA), AR (ANMAT), CL (ISP), PE (DIGEMID), BO (AGEMED), CO (INVIMA), ZA (SAHPRA), NG (NAFDAC), KE (PPB), ET (EFDA), GH (FDA Ghana), SN (DPM), VN (DAV), TH (FDA Thailand), ID (BPOM), PH (FDA Philippines), MY (NPRA), MN (Mongolian MoH), MM (DTM-MoHS), KH (Cambodian MoH), LA (Lao MoH), IS (Lyfjastofnun), NO/SE/FI (national agencies with Sámi flavour), RU (Roszdravnadzor), plus Pacific island states (FJ, WS, TO, NR, MH, FM, KI, TV, PG, SB, VU, PF, CK).

Also add:
1. `emergencyNumber: String?` field surfaced via tap-to-call in URGENT notifications (issue #1).
2. `traditionalMedicinePresets: Set<String>` so a Polynesian/Aboriginal/Sasang/AYUSH preset can be toggled per-region.
3. Aboriginal IHEMS/NACCHO sub-flavour for AU; IHS sub-flavour for US.
4. Pacific NCD preset (Polynesian/Micronesian DM/HTN/gout/obesity-elevated background prevalence — docs/audits/OCEANIC_ARCTIC_POV.md §2.2).
5. The EU fallback must explicitly log when used and surface a "your region isn't configured — using EU defaults" disclosure (docs/audits/AFRICAN_TRADITIONAL_POV.md §2.3).

Cross-references docs/audits/INDIGENOUS_AMERICAS_POV.md §2.3, docs/audits/OTHER_ASIAN_SYSTEMS_POV.md §2.2.
```

### #18 — feat(alerts): tropical / endemic-disease pattern library
**Lens convergence:** African Traditional, Indigenous Americas, Other Asian, Oceanic/Arctic, Siddha (Tamil-tropical), Emergency Medicine
**Clinical impact:** The 33+ patterns are Northern-temperate-anchored (Mishra/Quer/Smarr/Ridker/ADA citations). No malaria (with quartan/tertian periodicity from skin-temp time-series), no dengue (biphasic saddleback fever-curve + WHO 2009 warning signs), no chikungunya, no typhoid, no scrub typhus, no leptospirosis, no melioidosis, no ciguatera (bradycardia + cold-allodynia), no Lassa, no severe-malaria URGENT escalation. Major morbidity in the regions traditional practitioners serve.
**Suggested files:** new `android/app/src/main/java/com/bios/app/alerts/TropicalEndemicPatterns.kt`, [android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt](android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt)
**Body sketch:**
```
docs/audits/AFRICAN_TRADITIONAL_POV.md §2.1+§2.7, docs/audits/OTHER_ASIAN_SYSTEMS_POV.md §1, docs/audits/OCEANIC_ARCTIC_POV.md §2.6, docs/audits/INDIGENOUS_AMERICAS_POV.md §4.5, docs/audits/SIDDHA_POV.md §2.6 all flag overlapping tropical-disease pattern gaps.

Patterns (region-gated to avoid false fire in temperate cohorts):
1. `malaria_onset` — uses periodogram analysis over 3-7-day skin-temperature time-series to expose tertian (48h, P. vivax/ovale) and quartan (72h, P. malariae) periodicity. Sustained/quotidian for P. falciparum.
2. `severe_malaria` URGENT — sustained fever + falling SpO2 + rising RHR + CONSCIOUSNESS_LEVEL drop (CONSCIOUSNESS_LEVEL is on the bus, unused by any current pattern — high-leverage signal addition).
3. `dengue_progression` — biphasic saddleback fever-curve + WHO 2009 warning-sign staging (plasma-leak / bleeding tendency).
4. `chikungunya_pattern` — acute polyarthralgia + biphasic fever.
5. `scrub_typhus`, `leptospirosis`, `melioidosis`, `typhoid` staging patterns.
6. `ciguatera_signature` — persistent bradycardia + owner-annotated cold-allodynia symptom.

Region-gated: silent in temperate-only cohorts to avoid false-fire on non-tropical-endemic populations. Cross-references EM audit's heat illness pattern (#19).
```

### #19 — feat(alerts): environmental context patterns (altitude, heat, cold, photoperiod)
**Lens convergence:** Sowa Rigpa, Indigenous Americas (Andes/Mesoamerica), Oceanic/Arctic (Sápmi/Sakha/Chukchi cold + polar photoperiod), Other Asian (Mongolia cold), Emergency/Critical Care (heat/wilderness), Ayurveda (Ritucharya), TCM (six pathogens), African Traditional (Sahel heat)
**Clinical impact:** SpO2 cutoffs in [EmergencyVitalPatterns.spo2Critical](android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) cite WHO sea-level cutoffs that fire URGENT on healthy 3,500-4,500m residents (Leh, Lhasa, Cusco, La Paz, Quito, Santa Fe). Heat illness (ACSM, climate-change relevance), hypothermia in circumpolar populations, Sápmi/Sakha photoperiod extremes — all need owner-set environmental context to interpret baselines correctly.
**Suggested files:** new `android/app/src/main/java/com/bios/app/physiology/EnvironmentalContext.kt`, [android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt](android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt)
**Body sketch:**
```
docs/audits/SOWA_RIGPA_POV.md §2.1+§2.6, docs/audits/INDIGENOUS_AMERICAS_POV.md §2.2, docs/audits/OCEANIC_ARCTIC_POV.md §2.7, docs/audits/EMERGENCY_CRITICAL_CARE_POV.md §3.1+§3.2+§3.5 converge on owner-set environmental context.

Deliverables:
1. `EnvironmentalContext` — owner-set categorical annotations: `elevationBand` (sea-level / 1500-2500m / 2500-3500m / 3500-4500m / >4500m), `climateBand` (temperate / tropical-monsoonal / tropical-arid / continental-cold / polar / arctic), `photoperiodRegime` (standard / polar-night / midnight-sun seasonal).
2. Per-band shift of [EmergencyVitalPatterns.spo2Critical](android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) absolute cutoffs. Same `absoluteBelow` mechanism the biomarker patterns use.
3. `altitude_acclimatisation_monitor` pattern — early HAPE/HACE signature from SpO2 trajectory at elevation. EM wilderness-medicine relevance.
4. `heat_illness_screen` — sustained skin-temp + tachycardia + (when avail) `AMBIENT_HUMIDITY` (#21).
5. `cold_illness_screen` — sustained skin-temp drop + paradoxical bradycardia + activity drop.
6. Per-season Ayurvedic Ritucharya / TCM six-pathogens / Tamil six-paruvam baseline-modifier overlay rides on the same context surface (docs/audits/AYURVEDA_POV.md §2.5, docs/audits/TCM_POV.md §2.7, docs/audits/SIDDHA_POV.md §2.5).
```

### #20 — feat(physiology): age-banded paediatric sub-states + vital-sign norms
**Lens convergence:** Paediatrics (entire audit), Primary Care, Emergency/Critical Care (PALS), Oncology (paediatric haem-onc), Ob/Gyn (adolescent confidentiality)
**Clinical impact:** A 2-month-old with a normal RHR of 130 fires URGENT `tachycardiaCritical` (≥130). Bradycardia at ≤35 misses a 2-month-old at 80 bpm who is in genuine bradyarrhythmia (PALS infant cutoff <100). SpO2, BP (percentile-based), RR all need age-banded modifiers. PAEDIATRIC is one bucket covering 0-18 — clinically six bands.
**Suggested files:** [android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt](android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt](android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), new `android/app/src/main/java/com/bios/app/physiology/PaediatricVitalBands.kt`
**Body sketch:**
```
docs/audits/PAEDIATRICS_POV.md §2.1+§2.3 — the single biggest paediatric finding.

1. Sub-band `PAEDIATRIC_NEONATE` (0-28d), `PAEDIATRIC_INFANT` (1-12mo), `PAEDIATRIC_TODDLER` (1-3y), `PAEDIATRIC_PRESCHOOL` (3-5y), `PAEDIATRIC_SCHOOLAGE` (5-12y), `PAEDIATRIC_ADOLESCENT` (12-18y). PALS 2020 / EPLS 2021 / WHO IMCI ranges.
2. Per-band HR/RR/SpO2/BP threshold tables shipped as `PaediatricVitalBands`. EmergencyVitalPatterns gates URGENT thresholds on the active sub-band.
3. Paediatric-specific patterns: PEWS-shaped surface (docs/audits/PAEDIATRICS_POV.md §2.7) gating on age-banded HR/RR/SpO2 + OXYGEN_FLOW_RATE + CONSCIOUSNESS_LEVEL (already on the bus); paediatric asthma exacerbation pattern (§2.9) with environment trigger correlation via existing AIR_PM25/AIR_VOC; ISPAD-specific T1D thresholds (§2.8).
4. Growth-tracking primitive (#22) hangs off the same sub-band logic.
5. Adolescent confidentiality framing (§2.5) is the ethically hardest gap — flag as documentation work in PRIVACY_ARCHITECTURE.md; defer full family-mediated entity model (paediatrics §2.12).

Out of scope per paediatrics audit: SIDS/Owlet-class neonatal monitoring (correctly out, the Bios manifesto refuses the false-reassurance failure mode that AAP has criticised).
```

### #21 — feat(data): paediatric growth tracking + body-composition primitives
**Lens convergence:** Paediatrics, Primary Care, Geriatrics (sarcopenia/cachexia), Oncology (cachexia)
**Clinical impact:** Paediatric primary care is principally growth surveillance — height, weight, head circumference (until 36mo), BMI percentile against WHO/CDC charts. Failure-to-thrive crosses two major percentile lines downward; obesity ≥95th percentile. Bios has BODY_MASS via Withings but no HEIGHT/BODY_LENGTH/HEAD_CIRCUMFERENCE/percentile computation/chart view. Same primitive serves geriatric sarcopenia (EWGSOP2) and oncology cachexia (Fearon 2011).
**Suggested files:** [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), new `android/app/src/main/java/com/bios/app/engine/GrowthPercentileEngine.kt`, new `android/app/src/main/java/com/bios/app/alerts/WeightTrajectoryPatterns.kt`
**Body sketch:**
```
docs/audits/PAEDIATRICS_POV.md §2.2 names this as the highest-leverage paediatric primary-care primitive. docs/audits/GERIATRICS_PALLIATIVE_POV.md §2.10 and docs/audits/ONCOLOGY_POV.md §2.8 want the inverse signal for unintentional weight loss.

Deliverables:
1. New MetricTypes: `HEIGHT` (or `BODY_LENGTH` for <2y), `HEAD_CIRCUMFERENCE` (0-36mo), `GRIP_STRENGTH` (geriatric sarcopenia + Fried).
2. `GrowthPercentileEngine` — WHO Child Growth Standards 0-5y, CDC growth charts 2-20y. Computes per-metric percentile-for-age (and sex when set).
3. `failure_to_thrive_signature` — paediatric pattern: crossing two major percentile lines downward.
4. `weight_loss_unintentional` — adult/geriatric/oncology pattern: ≥5% BODY_MASS loss over 180 days. Single signal rule; gates onto Fried-frailty composite and hospice prognostic flagging.
5. Sarcopenia composite (EWGSOP2 — Cruz-Jentoft 2019): low LEAN_MASS + low GRIP_STRENGTH + low gait speed (when accelerometer-derivable).
6. Pull-side growth-chart view with percentile bands.
```

### #22 — feat(alerts): respiratory + AMBIENT_HUMIDITY + asthma/sleep apnea extensions
**Lens convergence:** Cardiology (OSA = AFib/HF risk), Neurology (OSA = stroke risk), Paediatrics (asthma), TCM (six pathogens / damp), Emergency Medicine (COPD/asthma exacerbation, sleep apnea)
**Clinical impact:** Sleep apnea pattern ships but vendor-derived AHI passthrough should be more explicit; paediatric-asthma exacerbation pattern is missing despite air-quality substrate being on the bus; AMBIENT_HUMIDITY would unlock damp/dry pathogen interpretation across TCM/Ayurveda/Siddha + heat-index for EM heat-illness.
**Suggested files:** [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [android/app/src/main/java/com/bios/app/alerts/RespiratoryExacerbationPatterns.kt](android/app/src/main/java/com/bios/app/alerts/RespiratoryExacerbationPatterns.kt), [android/app/src/main/java/com/bios/app/ingest/](android/app/src/main/java/com/bios/app/ingest/)
**Body sketch:**
```
1. `AMBIENT_HUMIDITY` MetricType in ENVIRONMENT. Phone barometer + most BLE ESS sensors emit it. Enables: heat-index for heat_illness_screen (#19), damp/dry six-pathogens overlay for TCM-lens (docs/audits/TCM_POV.md §2.7), seasonal humidity context for Siddha/Ayurveda/Unani Ritucharya/six-paruvam patterns.
2. Paediatric asthma exacerbation pattern in [RespiratoryExacerbationPatterns.kt](android/app/src/main/java/com/bios/app/alerts/RespiratoryExacerbationPatterns.kt) — age-banded RR thresholds (#20), AIR_PM25/AIR_VOC trigger correlation, manual peak-flow entry path. docs/audits/PAEDIATRICS_POV.md §2.9.
3. Make SLEEP_APNEA_EVENT / AHI passthrough explicit — Samsung/Apple ship FDA-cleared apnea detection; consume the events into the existing SleepApneaPattern rather than re-deriving from SpO2. docs/audits/MEDICAL_PROFESSIONAL_POV.md §2.8.
```

### #23 — feat(alerts): cardio-oncology + treatment-toxicity surveillance
**Lens convergence:** Oncology, Cardiology, Emergency Medicine (chemo emergencies), Geriatrics (Beers + oncology overlap)
**Clinical impact:** Anthracycline cardiomyopathy, trastuzumab LVEF decline, ICI fulminant myocarditis (0.3-1.1% incidence per JAMA Oncol 2018), chemotherapy myelosuppression / neutropenic fever (STAR trial Basch JAMA 2017: median 5-month OS benefit from wearable-adjacent monitoring), irAE pneumonitis/colitis/hepatitis/thyroiditis, radiation toxicity. Bios has nearly all the substrate; gates and patterns are missing.
**Suggested files:** new `android/app/src/main/java/com/bios/app/physiology/OncologyState.kt`, new `android/app/src/main/java/com/bios/app/alerts/OncologyPatterns.kt`, [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt)
**Body sketch:**
```
docs/audits/ONCOLOGY_POV.md §2.3-2.6+§2.10-2.12 details the substrate.

1. PhysiologyState additions: `ON_ACTIVE_CHEMOTHERAPY`, `ON_ACTIVE_IMMUNOTHERAPY`, `ON_CARDIOTOXIC_THERAPY` (with agent-class sidecar), `RADIATION_THERAPY_COURSE` (with anatomic-site sidecar), `CANCER_SURVEILLANCE_WINDOW` (with cancer_type + treatment_completion_date).
2. Cancer-cardiotoxicity pattern — gated on `ON_CARDIOTOXIC_THERAPY`, watches RHR/HRV/BP trajectory per ESC 2022 Cardio-Oncology Guideline. Pairs with HF prodrome pattern (#9).
3. Chemo toxicity convergence — gated on `ON_ACTIVE_CHEMOTHERAPY`, reuses [infectionOnset](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) machinery with chemotherapy-cycle-aware thresholds. URGENT escalation for neutropenic-fever criteria (ANC <500 + temp ≥38.3°C).
4. irAE multi-organ patterns — gated on `ON_ACTIVE_IMMUNOTHERAPY`, reuses thyroid/hepatic/renal/glucose patterns + new pneumonitis pattern.
5. Tumour markers + cardio-oncology biomarkers in MetricType: `CEA`, `CA125`, `CA199`, `PSA`, `HCG_BETA`, `AFP`, `CA15_3`, `CA27_29`, `CALCITONIN`, `THYROGLOBULIN`, `LDH`, cardiac `TROPONIN_HSCTNI/HSCTNT`, `NT_PROBNP`, `LP_A` (also requested by cardiology #25).
6. Cancer-related fatigue + cachexia trajectory patterns piggyback on #7 (ESAS-r) and #21 (weight-loss-unintentional).
```

---

## Tier C

### #24 — feat(alerts): seizure events + neurology URGENT triggers
**Lens convergence:** Neurology, Emergency/Critical Care, Paediatrics
**Clinical impact:** No SEIZURE_EVENT MetricType, no convulsive-pattern detector, no `status_epilepticus_convulsive` URGENT pattern, no GCS ≤8 URGENT, no thunderclap-headache event. Empatica Embrace2/EmbracePlus are FDA-cleared for GTCS detection from wrist accelerometer + HR; Bios has the substrate. SUDEP risk reduction is the load-bearing argument.
**Suggested files:** [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), new `android/app/src/main/java/com/bios/app/engine/SeizureDetector.kt`, [android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt](android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt)
**Body sketch:**
```
docs/audits/NEUROLOGY_POV.md §2.1+§2.3. Add SEIZURE_EVENT MetricType (NEUROLOGICAL, EVENT). Owner-logged entry sufficient for v1.

Patterns:
1. `status_epilepticus_convulsive` URGENT — single SEIZURE_EVENT ≥300s (ILAE 2015 t1=5min operational definition, Trinka 2015).
2. `seizure_cluster` ADVISORY — ≥3 events in 24h (Haut 2007).
3. `acute_altered_consciousness` URGENT — CONSCIOUSNESS_LEVEL ≤8 (already on the bus). Mirrors SpO2/glucose URGENT pattern shape.
4. `thunderclap_headache_event` URGENT — owner-logged symptom (IHS ICHD-3 §6.2.2 SAH).
5. Convulsive-pattern detector (follow-up) — band-pass 2-8Hz, sustained ≥10s rhythmic motion + ictal tachycardia + post-event RHR/HRV nadir. Reuses [engine/Spectral.kt](android/app/src/main/java/com/bios/app/engine/Spectral.kt) FFT.

Honest scope: focal/absence seizures not wearable-detectable, PNES discrimination via peri-event autonomic signature (stored, surfaced only via clinician-share path — docs/audits/NEUROLOGY_POV.md §2.12+§3.3).
```

### #25 — feat(biomarkers): expand panel (renal, hepatic, functional-medicine, cardio-oncology)
**Lens convergence:** Primary Care, Oncology, Cardiology, Modern Non-Allopathic, Geriatrics
**Clinical impact:** eGFR/creatinine, ALT/AST/GGT (GGT present, ALT/AST missing), fasting insulin/HOMA-IR (present), hsCRP (present). Missing: reverse T3, TPO/Tg antibodies, homocysteine, uric acid, bilirubin, DHEA-S, omega-3 index, fasting leptin/adiponectin, troponin (hs-cTnI/hs-cTnT), NT-proBNP, Lp(a), Lp-PLA2, ferritin (present).
**Suggested files:** [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [android/app/src/main/java/com/bios/app/alerts/BiomarkerReference.kt](android/app/src/main/java/com/bios/app/alerts/BiomarkerReference.kt), [android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt](android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt)
**Body sketch:**
```
Cross-references docs/audits/MEDICAL_PROFESSIONAL_POV.md §2.8, docs/audits/CARDIOLOGY_POV.md §2.11+§2.13, docs/audits/MODERN_NON_ALLOPATHIC_POV.md §2.3, docs/audits/ONCOLOGY_POV.md §2.11.

Add MetricTypes in BIOMARKER with reference ranges:
- Renal: eGFR, CREATININE (high-yield preventive labs for >50 per primary-care)
- Hepatic: ALT, AST, BILIRUBIN_TOTAL, BILIRUBIN_DIRECT (NAFLD signal)
- Thyroid extension: REVERSE_T3, TPO_ANTIBODY, TG_ANTIBODY (autoimmune thyroid panel as a unit — Hashimoto's, Graves')
- Cardio: TROPONIN_HSCTNI, TROPONIN_HSCTNT, NT_PROBNP (cardio-oncology + HF), LP_A (single most important inherited atherogenic factor, ESC 2019)
- Functional-medicine: HOMOCYSTEINE, URIC_ACID, DHEA_S, OMEGA_3_INDEX, LEPTIN, ADIPONECTIN
- Tumour markers: covered in #23
- Fertility/menopause: FSH, AMH (NAMS 2022 menopause staging — docs/audits/OBGYN_POV.md §2.6)
- PCOS expansion: SHBG, FREE_ANDROGEN_INDEX (docs/audits/OBGYN_POV.md §2.8)

Each requires one MetricType entry + universal/regional reference band entry. Rides on existing FHIR import.
```

### #26 — feat(alerts): cardiology-specific advanced patterns (POTS, HRR, ectopy, QT-class)
**Lens convergence:** Cardiology, Neurology (POTS / long-COVID), Emergency Medicine
**Clinical impact:** POTS criterion is sustained HR ≥30 bpm rise within 10 min of standing (Sheldon 2015; long-COVID prevalence-explosion population). HRR1/HRR2 (Cole 1999, Jouven 2005) is one of the strongest single prognostic indicators in non-invasive cardiology and is mechanically derivable. PVC-burden estimate from PPG-IBI compensatory-pause signature (Baman 2010 — 10% threshold for PVC-cardiomyopathy). QT-prolonging medication interaction view (CredibleMeds — covered in #16).
**Suggested files:** new `android/app/src/main/java/com/bios/app/alerts/AdvancedCardiologyPatterns.kt`, [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt](android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt)
**Body sketch:**
```
docs/audits/CARDIOLOGY_POV.md §2.3+§2.5+§2.6. Cross-references docs/audits/NEUROLOGY_POV.md §2.8 for POTS active-stand test.

Deliverables:
1. POTS / orthostatic patterns:
   - `postural_orthostatic_pattern` — accelerometer-derived supine→standing posture transition + HR rise ≥30 bpm within 10 min (≥40 in adolescents). Sheldon 2015 / Vernino 2021.
   - `orthostatic_hypotension` — separable pattern, SBP/DBP drop ≥20/10 within 3 min (Freeman 2011). Today buried in fall-correlation pattern.
   - Pull-side 5-min owner-initiated active-stand test surface.
2. HRR derivation — peak HR during EXERCISE_SESSION minus HR at session_end+60s and +120s. Emit as `HR_RECOVERY_1MIN` / `HR_RECOVERY_2MIN`. Pattern fires on sustained HRR1 <12 bpm decline (Cole 1999, Imai 1994).
3. PVC-burden estimate — PPG-IBI compensatory-pause signature; emit `ECTOPY_BURDEN_ESTIMATE` (PERCENT). Pattern at 10% (Niwano 2009) / 15% (Park 2018) thresholds.
4. CIED summary annotation type (free-text; documents the gap until FHIR-pushed CIED data becomes accessible — docs/audits/CARDIOLOGY_POV.md §2.9).
```

### #27 — feat(alerts): psychiatry-specific patterns (bipolar / anxiety / perinatal / OUD)
**Lens convergence:** Psychiatry, Ob/Gyn (perinatal), Emergency Medicine (OUD)
**Clinical impact:** Existing `mental_health_correlate` is direction-agnostic and effectively unipolar-shaped (sleep down + activity down + HRV down). Misses bipolar mania prodrome (sleep down + activity up + circadian-phase shift — Seoul Nat'l Univ 2024 already cited in references but mania-direction rule not implemented). No dedicated anxiety pattern despite HRV being the most-validated wearable anxiety biomarker (Chalmers 2014). No perinatal-depression-specific pattern despite infrastructure being ready.
**Suggested files:** [android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), new `android/app/src/main/java/com/bios/app/alerts/PsychiatryPatterns.kt`
**Body sketch:**
```
docs/audits/PSYCHIATRY_POV.md §2.1-2.5.

1. `mania_prodrome` pattern — inverse signature: sleep duration↓ + activity↑ + circadian phase advance + (when W2F available) typing cadence ↑/erratic. Seoul Nat'l Univ 2024 citation already in references list; just add the mania-direction signal rule. Gated on `BIPOLAR_DIAGNOSED` (owner-set).
2. `anxiety_signature` — HRV depression + sleep-latency increase + typing-cadence change. Distinct from mental_health_correlate. Chalmers 2014 anchor.
3. `panic_episode` candidate — sudden tachycardia + hyperventilation (10-20 min envelope). Pull-side only.
4. `perinatal_depression_signature` — POSTPARTUM-gated variant of mental_health_correlate aligned with EPDS screening cadence (2-week, 6-week, 3-month, 6-month windows).
5. Suicide-specific surveillance: explicitly do not build — docs/audits/PSYCHIATRY_POV.md §2.6 makes the manifesto-aligned case for this being a "feature not gap." Document the deliberate omission so future contributors know.
6. Crisis-resources pull-side surface keyed to region (988 US, Samaritans GB, Lifeline AU, etc.) — owner-asked, never pushed.
```

### #28 — feat(adapter): single-lead ECG, RBD screen, tremor pipeline (Apple/Roche-class)
**Lens convergence:** Neurology (RBD prodrome, tremor pipeline), Cardiology (covered in #10)
**Clinical impact:** RBD is the strongest known prodromal biomarker for synucleinopathy (~73-80% develop PD/DLB/MSA within 12 years — Postuma 2019). Bios has sleep staging but doesn't track movement DURING REM (the RBD signal). RBDSQ/RBD1Q questionnaires are validated owner-facing screens. Tremor pipeline: accelerometer/gyroscope are live, FFT in [engine/Spectral.kt](android/app/src/main/java/com/bios/app/engine/Spectral.kt) reusable; 4-6Hz rest tremor = PD, 4-12Hz postural = essential.
**Suggested files:** new `android/app/src/main/java/com/bios/app/engine/TremorAnalyzer.kt`, new `android/app/src/main/java/com/bios/app/sleep/RbdMovementDetector.kt`, [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), new `android/app/src/main/java/com/bios/app/ui/neurology/RbdScreenScreen.kt`
**Body sketch:**
```
docs/audits/NEUROLOGY_POV.md §2.4+§2.7. Two parallel pipelines:

RBD screen (highest-leverage prodromal-neurodegeneration screen):
- RBDSQ (13-item Stiasny-Kolster 2007) and RBD1Q (Postuma 2012) — pull-side questionnaires using the SELF_REPORTED pathway.
- `REM_MOVEMENT_EVENT` — when wearable provides REM staging, detect movement above calibrated threshold during REM windows.
- `rbd_screen_signature` pattern on repeated weekly emission. Pull-side detail with literature-anchored risk number; never push-side prognostic.

Tremor pipeline:
- New MetricTypes (NEUROLOGICAL): `TREMOR_AMPLITUDE_RMS`, `TREMOR_FREQUENCY_HZ`, `TREMOR_BAND` (REST_PARKINSONIAN_4_6 / POSTURAL_ESSENTIAL_4_12 / PHYSIOLOGIC_8_12).
- `TremorAnalyzer` — FFT over 30-60s accelerometer-magnitude windows (reuse engine/Spectral.kt).
- Owner-initiated 90-second capture aligned with MDS-UPDRS 3.15-3.18.
- Patterns: `parkinsonian_tremor_signature`, `essential_tremor_signature`. Gated on owner-set neurology context.

Drug-induced parkinsonism + functional tremor differentials handed off, not adjudicated.
```

### #29 — feat(adapter): neurology owner-symptom logging (FAST stroke, headache diary, MIGRAINE_ATTACK_EVENT)
**Lens convergence:** Neurology, Emergency Medicine (stroke FAST), Primary Care (MOH prevalence)
**Clinical impact:** No structured neurology-symptom vocabulary — stroke FAST/BE-FAST intake (time-of-last-known-well is the highest-value single data point for a 4.5h tPA window), migraine attack event (prodrome correlation), cluster-headache event (circadian periodicity histogram is diagnostic), medication-overuse-headache rate-counting per IHS ICHD-3 §8.2.
**Suggested files:** [android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), new `android/app/src/main/java/com/bios/app/ui/neurology/FastStrokeScreen.kt`, new `android/app/src/main/java/com/bios/app/alerts/HeadachePatterns.kt`
**Body sketch:**
```
docs/audits/NEUROLOGY_POV.md §2.2+§2.5+§2.6+§2.11+§2.12. Cross-references docs/audits/EMERGENCY_CRITICAL_CARE_POV.md §2.6.

1. FAST/BE-FAST stroke intake — pull-side single-screen checklist (Balance, Eyes, Face, Arm, Speech, Time-of-last-known-well). Output: STROKE_SYMPTOM_EVENT row + "call emergency services" prompt (deep-links to issue #1's regional emergencyNumber). NO camera/microphone "AI stroke detection" — too premature for 4.5h window.
2. MetricTypes (NEUROLOGICAL, EVENT) with IHS ICHD-3-shaped payloads:
   - HEADACHE_ATTACK_EVENT — onset, duration, severity 0-10, side, character, photophobia/phonophobia/nausea/aura/autonomic, abortive med linked to MEDICATION_INTAKE
   - MIGRAINE_ATTACK_EVENT (specific sub-event for prodrome correlation)
   - CLUSTER_HEADACHE_ATTACK_EVENT
3. Per-event MEDICATION_INTAKE writer (MetricType exists at [MetricType.kt:242](android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L242), no writer ships) — closes triptan/abortive logging for MOH detection and MS DMT adherence simultaneously.
4. `medication_overuse_headache_risk` pattern — triptan/ergot/combination days ≥10/month OR simple analgesic days ≥15/month over 3 consecutive months (IHS ICHD-3 §8.2).
5. `chronic_migraine_threshold` pattern — ≥15 headache days/month with ≥8 migraine-like (IHS ICHD-3 §1.3) for CGRP/preventive-therapy discussion.
6. Cluster-headache circadian-periodicity histogram — time-of-day pull-side view; diagnostic-quality data, no ML.
7. Migraine prodrome pattern (HRV + sleep + circadian + W2F mood-drift) — owner-gated, pull-side first, push only on opt-in.
```

### #30 — feat(alerts): fall-risk prediction + delirium-risk + cognitive trajectory (geriatric pull-side)
**Lens convergence:** Geriatrics/Palliative, Neurology (MCI), Emergency Medicine (post-discharge delirium)
**Clinical impact:** Fall *detection* (Virgil FALL_EVENT) is event-shaped; fall *prediction* is the clinically valuable upstream signal (gait variability, postural sway, medication-class multiplier, STEADI). Delirium is the most underdiagnosed condition in hospitalised/SNF older adults; wearable-detectable risk signature (sleep-wake disruption + activity inversion + HRV instability). Cognitive trajectory needs pull-side surface, not push prediction (false-positive cost catastrophic).
**Suggested files:** new `android/app/src/main/java/com/bios/app/alerts/GeriatricPatterns.kt`, new `android/app/src/main/java/com/bios/app/ui/geriatrics/CognitiveTrajectoryScreen.kt`
**Body sketch:**
```
docs/audits/GERIATRICS_PALLIATIVE_POV.md §2.4+§2.5+§2.9. Cross-references docs/audits/NEUROLOGY_POV.md §2.10.

1. Fall-risk prediction pull-side surface — gait variability trend (when accelerometer-derived) + medication burden (#16 anticholinergic + Z-drug flagging) + age-band + STEADI 3-question self-report. Pull-side only at high confidence; push-side never at first.
2. `delirium_risk_pattern` pull-side card — convergence of sleep-wake disruption (existing circadian_disruption) + decreased activity + increased nocturnal activity + HRV instability, within 7-14 days of owner-flagged hospital discharge or surgery. "Not a delirium diagnosis — discuss with caregivers and clinicians" framing.
3. Cognitive trajectory pull-side surface — composes existing TYPING_CADENCE (W2F) + SLEEP_REGULARITY + CIRCADIAN_PHASE_SHIFT + gait variability into single owner-navigable trajectory view. Pull-side ONLY. Owner-administered Mini-Cog (Borson 2000) + owner-entered MoCA score path.
4. No push-side cognitive_decline pattern. The false-positive and false-comfort costs are both catastrophic.
```

### #31 — feat(data): reproductive completeness (contraception, menopause, PCOS/endo, gender-affirming, anatomy labels)
**Lens convergence:** Ob/Gyn, Primary Care, Endocrinology, Modern Non-Allopathic (perimenopause), Indigenous Americas (two-spirit accommodation), Cardiology (peripartum)
**Clinical impact:** No CONTRACEPTION_METHOD annotation (OCP HRV depression + abolished BBT shift currently silent), no MENOPAUSAL_TRANSITION PhysiologyState despite vasomotor signature being detectable on skin-temp + HR, no PCOS biomarker signature, no pain-cycle correlation for endometriosis (median diagnostic delay 7-10 years). "Female-bodied / Male-bodied" UI labels exclude transmasculine + two-spirit + intersex + fourth-gender frames.
**Suggested files:** [android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt](android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [android/app/src/main/java/com/bios/app/ui/screening/PreventiveCareScreen.kt](android/app/src/main/java/com/bios/app/ui/screening/PreventiveCareScreen.kt)
**Body sketch:**
```
docs/audits/OBGYN_POV.md §2.6-2.13 + docs/audits/INDIGENOUS_AMERICAS_POV.md §2.6.

1. `CONTRACEPTION_METHOD` annotation (SELF_REPORTED) — combined OCP, progestin-only, hormonal IUD, copper IUD, DMPA, implant, fertility-awareness, sterilisation. Pattern-explanation builder reads it to de-mystify silent cycle inference, suppress mood-pattern false alarms in placebo week, gate fertility-awareness messaging.
2. `PhysiologyState.PERIMENOPAUSAL_TRANSITION` and `MENOPAUSAL` — owner-asserted. Gates cycle-phase classification (stops being emitted in true menopause).
3. `vasomotorPattern` ConditionPattern — sub-30-min skin-temp + HR transients ≥3/night sustained ≥7 nights. Pull-side, opt-in push.
4. FSH + AMH biomarkers (covered in #25 cross-link).
5. `pcos_signature` biomarker pattern — elevated testosterone OR low SHBG (free-androgen-index proxy) + elevated HOMA-IR + cycle-length irregularity. Add SHBG, FREE_ANDROGEN_INDEX, DHEA-S to MetricType (#25 cross-link).
6. `pain_cycle_correlation` pull-side surface — PAIN_SCORE × CYCLE_PHASE across multiple cycles for endometriosis/adenomyosis identification.
7. `MENSTRUAL_FLOW_VOLUME` light/moderate/heavy categorical in PeriodEntryRepo (adenomyosis use case).
8. `GENDER_AFFIRMING_HRT` PhysiologyState (testosterone shifts HRV, sleep, hematocrit; estrogen similar).
9. Re-label PreventiveCareScreen "Female-bodied/Male-bodied" → direct anatomy questions ("Have breast tissue?", "Have a cervix?", "Have prostate/testes?", "Could become pregnant?"). Small cost, removes binary imposition across transmasculine + two-spirit + intersex frames.
10. Pregnancy-loss workflow with offered accelerated wipe; explicit no-anniversary commitment in any future calendar surface.
```

### #32 — feat(localization): externalize alert text + add high-impact locales
**Lens convergence:** African Traditional, Indigenous Americas, Other Asian, Oceanic/Arctic, Ob/Gyn (maternal-mortality access framing)
**Clinical impact:** [ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) hardcodes 6 paragraphs per pattern in English (`explanation`, `suggestedAction`, `earlyDetection`, `prevention`, `healing`, `risks`) across 33+ patterns. Currently un-localisable in the existing architecture. Te Reo Māori is partially required under NZ Crown Treaty obligations; ʻōlelo Hawaiʻi is co-official in HI; Sámi languages are official in defined municipalities; Spanish absence affects most of Latin America + Indigenous Americas catchment. Same refactor unblocks every non-English locale.
**Suggested files:** [android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [android/app/src/main/res/values/strings.xml](android/app/src/main/res/values/strings.xml), new locale-specific `values-*/strings.xml` overlays
**Body sketch:**
```
Convergent finding across docs/audits/AFRICAN_TRADITIONAL_POV.md §2.5, docs/audits/INDIGENOUS_AMERICAS_POV.md §2.4, docs/audits/OTHER_ASIAN_SYSTEMS_POV.md §6, docs/audits/OCEANIC_ARCTIC_POV.md §2.5.

Two deliverables:
1. Architectural refactor — move all pattern text from data-class fields to `strings.xml` (keyed by pattern ID + slot). This is the unblocking step every locale audit named.
2. Prioritised locale overlays:
   - Tier-A: Spanish (es / es-rMX / es-rAR for Latin America), Portuguese (pt-rBR), Te Reo Māori (mi-rNZ — Treaty-relevant), ʻōlelo Hawaiʻi (haw — HI state co-official)
   - Tier-B: Sámi family (se / smj / sma — official in Sápmi municipalities), Swahili (sw), Hausa (ha), Yoruba (yo), Zulu (zu), isiXhosa (xh), Amharic (am), Arabic (ar — Maghreb)
   - Tier-C: Vietnamese (vi), Thai (th), Bahasa Indonesia (id), Filipino/Tagalog (tl), Khmer (km), Lao (lo), Burmese (my), Mongolian (mn — both Cyrillic and traditional script), Quechua (qu), Aymara (ay), Guaraní (gn), Nahuatl (nah), Yucatec Maya (yua), Navajo (nv), Inuktitut (iu)

Pairs with #17 (region configs name the regulatory body the prescriber actually references).
```

---

## Closed / partially closed

The following gaps were flagged in earlier audits and have shipped (the later specialty audits explicitly note these are closed):

- **URGENT-tier reachability** — [EmergencyVitalPatterns.kt](android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) ships SpO2/glucose/HR ≥130/HR ≤35 with literature-anchored absolute cutoffs. The remaining work (escalation destination, sepsis-screen, hypotensive-shock, acute-window patterns) is captured in issues #1, #4, #5, #12.
- **Hypertension first-class** — [HypertensionPatterns.kt](android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) ships `hypertensionEmerging` (median-of-multiple-readings, 7-day window, 3-reading floor) and `hypertensiveUrgency` (180/120 single-reading URGENT). The pregnancy-specific extension (#11) and region-aware threshold tightening are the only remaining work.
- **Medication-annotation context** — [MedicationAnnotationRepo.kt](android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt) ships and is wired into [AnomalyDetector.kt:374-378](android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt). Extensions for traditional pharmacopoeias / Beers-AGS / CredibleMeds class flagging / TCM-Kampo-Ayurveda-Siddha-Unani formularies in #16.
- **PhysiologyState scaffolding** — [PhysiologyState.kt](android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) ships PREGNANCY_T1/T2/T3, POSTPARTUM, ATHLETE_HIGH_FITNESS, FRAILTY_FLAG, PAEDIATRIC + the `excludedStates` gate on ConditionPattern. Wiring (#8 frailty, #11 pregnancy, #20 paediatric sub-bands), new states (#5 peri-op, #6 goals-of-care + hospice, #9 HF, #23 oncology, #31 menopause + gender-affirming) all extend the existing mechanism.
- **Screening-cadence engine** — [ScreeningCadenceEngine.kt](android/app/src/main/java/com/bios/app/screening/ScreeningCadenceEngine.kt), [ScreeningCatalog.kt](android/app/src/main/java/com/bios/app/screening/ScreeningCatalog.kt), [RiskProfile.kt](android/app/src/main/java/com/bios/app/model/RiskProfile.kt) ship USPSTF-only with first-degree-CAD-early/BRCA-flag/colorectal/melanoma. Extensions (hereditary syndromes, paediatric vaccines, Ob/Gyn-society cadences, IHS/PAHO regional, cardiology one-time screens) in #13.
- **Immunisation domain** — [ImmunizationRecord.kt](android/app/src/main/java/com/bios/app/model/ImmunizationRecord.kt) + [ImmunizationRepo.kt](android/app/src/main/java/com/bios/app/data/ImmunizationRepo.kt) ship; [VaccineCatalog.kt](android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt) honestly defers paediatric vaccines as out of scope. Closure in #13.
- **Sleep apnea passthrough** — [SleepApneaPattern.kt](android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt) ships with AHI ≥5 cited to Berry 2020 AASM + Somers 2008 AHA. Vendor-event ingestion explicitness in #22.

The remaining items below were named in earlier audits but are deliberately **not** filed per the consolidation rules:

- **Divination, oracle, repertorisation surfaces** — flagged as correct silence across African Traditional §3.2, Indigenous Americas §3.2, Modern Non-Allopathic §2.11 (Hahnemannian totality), Other Asian §3 (Burmese Vijjadhara, Thai saiyasart, Khmer ritual purification).
- **Energy medicine / biofield / chi-prana measurement** — flagged as orthogonal across Modern Non-Allopathic §2.12, Oceanic §3 (wairua), Indigenous Americas §3.3, TCM §4, Ayurveda §4.
- **Spirit-illness, ancestral, and ceremonial etiology** — flagged as correct silence across African Traditional §3.3, Indigenous Americas §3.3, Oceanic §3.
- **Marma point anatomy, acupoint maps, anatomical-overlay diagnostic surfaces** — Ayurveda §2.9, TCM §4, Siddha §2.8 all explicitly defer to third-party companion apps via the ContentProvider.
- **Composite "biological age" / "longevity score" / "frailty index composite" / "tridosha balance score" / "neurological health score" / "cardiovascular health score" / "Ayurvedic health index" / "Functional Medicine Score" / "Naturopathic Index"** — every audit that mentions a composite reaffirms the DATA_MODEL.md guard. Do not adopt.
- **Push-side prognostic communication** (RBD → synucleinopathy risk, push-side AFib notification, push-side migraine prediction, push-side cognitive-decline notification, push-side dosha vitiation) — surfaces are pull-side only; push channels reserved for unambiguous URGENT events.
- **Automated PNES classification, automated TCM/Ayurvedic diagnosis, automated pulse-quality classifier, automated tongue-image analysis, automated Mizaj inference** — Bios captures and stores; the practitioner classifies.
