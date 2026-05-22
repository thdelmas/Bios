# Neurology Audit — Bios as Patient-Side Neurological Observation Feed

**Scope:** Bios's clinical reach as a longitudinal, owner-side observation feed for the conditions a board-certified neurologist most often follows between office visits: stroke / TIA, epilepsy, movement disorders (Parkinson's disease and essential tremor), headache (migraine, cluster, medication-overuse), sleep neurology (insomnia, REM sleep behaviour disorder, restless legs, OSA-as-stroke-risk), neuro-immunology (MS), traumatic brain injury / concussion, dysautonomia / POTS, and early cognitive decline.
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Western biomedical neurology, board-certified, with sub-exposure across the [section 1.6 subspecialties](MEDICAL_SPECIALTIES_WORLDWIDE.md). Not a regulatory or 510(k) audit, and not a substitute for diagnostic imaging, neurophysiology (EEG, EMG/NCS), or supervised neurological examination. This is what a neurologist would say if asked "is this app worth having my patient install between visits?"
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [docs/audits/MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md) (skimmed to avoid repetition), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [Wave5BiomarkerPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/Wave5BiomarkerPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt), [SleepApneaPattern.kt](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt), [CircadianConditionPattern.kt](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [HrvAnalyzer.kt](../../android/app/src/main/java/com/bios/app/engine/HrvAnalyzer.kt), [PhoneSensorAdapter.kt](../../android/app/src/main/java/com/bios/app/ingest/PhoneSensorAdapter.kt), [PhoneSleepInference.kt](../../android/app/src/main/java/com/bios/app/engine/PhoneSleepInference.kt), [SleepDerivations.kt](../../android/app/src/main/java/com/bios/app/engine/SleepDerivations.kt), [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [MedicationAnnotation.kt](../../android/app/src/main/java/com/bios/app/model/MedicationAnnotation.kt).

---

## Executive summary

Bios is, from a neurology bench, **the most defensible privacy-preserving longitudinal-observation layer I have audited for this patient population.** The autonomic substrate (RHR, HRV with RMSSD / lnRMSSD / LF-HF / HF power), the multi-component sleep decomposition (latency, efficiency, fragmentation, WASO, regularity), the circadian-misalignment pattern over ambient-light + sleep-stage, the AFib screen, and the medication-annotation surface together cover most of the "what changed since last clinic visit?" questions I ask. The manifesto's instrument-not-coach posture maps cleanly onto how neurologists actually use patient diaries: data the owner brings, not a score the app pushes.

The audit nevertheless surfaces twelve gaps that matter from a neurology perspective. Ordered by clinical impact (where impact = morbidity / mortality / time-to-treatment-window the gap could shift):

1. **No seizure detection or seizure-event surface.** Wrist-worn accelerometer + HR pattern recognition for generalised tonic-clonic seizures is FDA-cleared (Empatica Embrace2, 2018; Empatica EmbracePlus, 2023). Bios has the substrate ([PhoneSensorAdapter.accelerometerFlow](../../android/app/src/main/java/com/bios/app/ingest/PhoneSensorAdapter.kt#L163-L184), HR, HRV) and the EVENT-unit schema for it, but no `SEIZURE_EVENT` MetricType, no convulsive-pattern detector, and no `status_epilepticus` URGENT pattern. For a patient with refractory epilepsy and SUDEP risk, this is the single most consequential omission in the library.
2. **Stroke / TIA: no FAST/BE-FAST surface, no acute-symptom intake.** AFib screening (the upstream embolic-prevention layer) is present and well-cited ([atrialFibrillationScreen](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L396-L419)); the URGENT bradycardia/tachycardia floor in [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) catches AFib RVR. But for the actual stroke event — sudden hemiparesis, dysarthria, facial droop, visual loss — Bios has nothing. No quick-tap FAST checklist that the owner or a bystander can run, no time-of-last-known-well capture, no symptom-onset-to-now timer (which is what every stroke centre asks first because the tPA window is 4.5 h and the thrombectomy window is 6–24 h with imaging). This is the highest-stakes neurology emergency by morbidity and the only one with a strict pharmacologic time window.
3. **Status epilepticus, stroke, and intracerebral hemorrhage are not first-class URGENT triggers.** The URGENT tier is now reachable ([EmergencyVitalPatterns.kt:40-180](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L40-L180)) — credit for closing the §2.1 primary-care gap — but the four patterns wired into it are respiratory (SpO2 ≤85), metabolic (glucose ≤54), and cardiac (HR ≥130 / ≤35). The neurology-emergency triad (seizure >5 min, sudden focal deficit, thunderclap headache + altered LOC) is unrepresented. The `CONSCIOUSNESS_LEVEL` MetricType exists with GCS encoding ([MetricType.kt:79-80](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L79-L80)), but no pattern reads it; GCS ≤8 (the airway-intubation threshold) is silent in the alert layer.
4. **No tremor analysis pipeline despite the sensors being live.** [PhoneSensorAdapter.kt](../../android/app/src/main/java/com/bios/app/ingest/PhoneSensorAdapter.kt) emits accelerometer and gyroscope flows; [docs/DATA_MODEL.md:76](../../docs/DATA_MODEL.md#L76) reserves `tremor_amplitude` (mm/s²) as "planned — neurological screening" but no MetricType ships, no FFT-band classifier ships (4–6 Hz rest tremor = PD; 4–12 Hz postural/action = essential tremor; 8–12 Hz physiologic). The same `engine/Spectral.kt` FFT machinery used for LF/HF HRV decomposition could be reused on the accelerometer magnitude series. Apple Movement Disorder API and Roche Floodlight Open are the precedents.
5. **No migraine pattern.** Migraine prodrome physiology is well-described: 24–48 h pre-attack HRV depression, RHR shift, sleep disturbance, photophobia / phonophobia behavioural changes, often a yawning/fatigue cluster (Giffin 2003; Karsan 2018). Bios already detects every one of these substrates separately. A `migraine_prodrome` pattern over HRV + sleep + circadian + (when W2F is connected) mood-drift would be the closest analogue in the suite to existing patterns like `recoveryDeficit` or `mentalHealthCorrelate`. Owner-annotated attack onset is the gold-standard labelling channel; without it, the pattern can't be validated against ground truth.
6. **No headache diary surface — and the medication-overuse-headache (MOH) gate is unbuilt.** Medication-overuse headache (IHS ICHD-3 §8.2) is one of the most missed and most reversible neurology diagnoses; the IHS criterion is ≥10 days/month of triptan, ergot, or combination-analgesic use for >3 months, or ≥15 days/month of simple analgesics. Bios now has a [MedicationAnnotation](../../android/app/src/main/java/com/bios/app/model/MedicationAnnotation.kt) entity with start/end dates and free-text name, but it has no per-event "I took a triptan today" surface (the `MEDICATION_INTAKE` MetricType exists in [MetricType.kt:242](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L242) but no in-app writer ships), and no rate-counting pattern. The information needed to flag MOH is exactly the kind of pull-side, owner-controlled aggregation the manifesto invites.
7. **No REM sleep behaviour disorder (RBD) screening surface.** RBD is the single strongest known prodromal marker for synucleinopathy: ~80 % of polysomnogram-confirmed RBD patients develop PD, DLB, or MSA within 10–14 years (Postuma 2019; Schenck longitudinal cohorts). [PhoneSleepInference.kt](../../android/app/src/main/java/com/bios/app/engine/PhoneSleepInference.kt) and the [SleepDerivations.kt](../../android/app/src/main/java/com/bios/app/engine/SleepDerivations.kt) only categorise stages as AWAKE / LIGHT / DEEP / REM and produce session-level metrics; in-REM movement events (the RBD signal) and audio (vocalisation) are not captured. The screening questionnaire RBDSQ / RBD1Q is a five-minute owner-facing survey that maps cleanly onto the existing `SELF_REPORTED` pathway.
8. **No POTS / orthostatic-intolerance tilt-test surrogate.** A 30 bpm sustained HR rise within 10 minutes of standing (40 bpm in adolescents) without orthostatic hypotension is the bedside POTS criterion (Sheldon 2015; Vernino 2021). Every sensor needed — continuous HR, posture inference from accelerometer, optional BP via Withings — is already on the bus. A 5-minute active-stand owner-initiated test surface would cover both POTS and orthostatic hypotension, the two highest-yield bedside autonomic tests in dysautonomia work-up. Currently the `fall_orthostatic_pattern` ([CompanionConditionPatterns](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt)) only fires *after* a fall — fine for Virgil's safety mission, useless as a screening tool.
9. **MS relapse monitoring is partially substrated but not patterned.** Bios has `chronic_inflammation` ([ConditionPatterns.kt:309-336](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L309-L336)) and `recovery_deficit`, both of which fire in MS prodromes — but neither is labelled or weighted as MS-aware. Floodlight Open (Roche) ships smartphone-based MS-relapse monitoring with a daily 2-minute battery (pinch / draw / U-turn / SDMT) plus passive gait. The active-test slot is reserved (`REACTION_TIME_MS` at [MetricType.kt:255](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L255)) but unwired.
10. **No cluster-headache circadian-periodicity surface.** Cluster headache is the most reliably circadian primary headache (the "alarm-clock" pattern; hypothalamic generator hypothesis, May 1998). Owner-logged attacks plotted against the existing `CIRCADIAN_PHASE_SHIFT` derivation would surface the diagnostic periodicity directly. No attack-log MetricType ships; no plotting surface ships.
11. **Cognitive decline / MCI screening uses none of the passive smartphone signals despite them being canonical.** Apple Cognition (preview), Akili EndeavorRx, Apple's typing-dynamics study, and the BiAffect (npj Digital Medicine 2024) work — already cited in [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) — all converge on a similar passive feature set: typing latency / hesitation, app-switching entropy, gait variability, sleep regularity, circadian fragmentation. Bios has the substrates (typing cadence via W2F, sleep regularity, circadian phase shift, ambient light) but no MCI-screen surface composes them; the `cognitive_decline_signature` slot is empty.
12. **Headache, seizure, and stroke owner-symptom logging is missing across the board.** [HealthEventType](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L116-L123) ships `SYMPTOM` / `HYPOTHESIS` / `DIAGNOSIS` / `TREATMENT` etc., but there is no structured neurology-symptom vocabulary on top — owners can write free-text but cannot mark "right-sided weakness onset 14:23" with the precision a stroke team needs, "generalised convulsive seizure, duration 45 s, postictal 12 min" with the precision an epileptologist needs, or "left peri-orbital, 9/10, autonomic features yes" with the precision a headache neurologist needs. The schema is generous; the vocabulary is empty.

The remaining sections walk these in clinical detail and then translate into Tier A/B/C recommendations honest about wearable neurology's true ceiling.

---

## 1. What Bios already does well, viewed from a neurology bench

| Quality | Evidence in code | Why it matters in neurology |
|---|---|---|
| **Full HRV decomposition, not just RMSSD** | [HrvAnalyzer.kt:36-61](../../android/app/src/main/java/com/bios/app/engine/HrvAnalyzer.kt#L36-L61) emits RMSSD, SDNN, pNN50, lnRMSSD, Baevsky stress index, LF power, HF power, LF/HF ratio | HF-HRV is the standard non-invasive vagal-tone marker (Task Force 1996; Shaffer & Ginsberg 2017). It is the substrate every neurology subspecialty needs — autonomic neurology directly, neuro-immunology (vagal anti-inflammatory pathway, Tracey 2002), MS (cardiovagal involvement, Flachenecker 1999), PD (cardiac sympathetic denervation, Goldstein 2014), epilepsy (peri-ictal autonomic instability, Bonnet 1997). Most consumer apps stop at RMSSD; Bios doesn't |
| **AFib screening with explicit ECG-confirmation language** | [atrialFibrillationScreen](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L396-L419), suggestedAction reads "wearable HRV data cannot definitively detect AFib — clinical ECG is required" | AFib is the single most common cardio-embolic stroke source. Apple Heart Study (Perez 2019) is correctly cited. The 34 % PPV is appropriately framed as screening, not diagnostic — exactly the language a stroke neurologist would want |
| **Sleep decomposition into clinical sub-metrics, not a single sleep score** | [SleepDerivations.kt](../../android/app/src/main/java/com/bios/app/engine/SleepDerivations.kt) emits SLEEP_LATENCY, SLEEP_EFFICIENCY, SLEEP_FRAGMENTATION_INDEX, WAKE_AFTER_SLEEP_ONSET; SLEEP_REGULARITY shipped per ROADMAP | These are the components of clinical sleep medicine (AASM scoring manual). Sleep latency >30 min, efficiency <85 %, WASO are the markers neurology cares about — they are the AD/PD prodrome markers, the migraine-trigger markers, the post-concussion recovery markers |
| **Circadian-misalignment pattern over ambient light + sleep** | [circadianDisruption](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt#L33-L61), citations Wright 2013 / Chang 2015 / Roenneberg 2007 | Circadian disruption is mechanistically implicated in seizure clustering (catamenial / sleep-cycle), migraine triggering, cluster-headache periodicity, bipolar mood episodes (Seoul Nat'l Univ 2024 — already cited in the mental-health pattern), and shift-work neurocognitive decline. The substrate is here |
| **Sleep apnea passthrough with AASM thresholds** | [SleepApneaPattern.kt:36-73](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt#L36-L73), AHI ≥5 cited to Berry 2020 AASM Scoring Manual; Somers 2008 AHA/ACC | OSA is a strong, modifiable, independent risk factor for ischemic stroke (Somers 2008; Yaggi 2005) and for AFib. A neurology patient with snoring + nocturnal SpO2 dips deserves a sleep-study referral; this pattern produces exactly the data needed to make that referral |
| **Hypertension patterns with median-of-multiple-readings home-BP convention** | [HypertensionPatterns.hypertensionEmerging](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt#L62-L101) requires ≥3 readings / 7 d, median check; [hypertensiveUrgency](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt#L118-L146) at 180/120 floor escalates to URGENT | Hypertension is the strongest single modifiable risk factor for both ischemic and hemorrhagic stroke and for vascular cognitive decline. The white-coat-robust median + minimum-readings gate is clinically correct. The 180/120 URGENT pattern would catch a hypertensive emergency / encephalopathy |
| **Medication annotation surface that the explanation builder reads** | [MedicationAnnotation.kt](../../android/app/src/main/java/com/bios/app/model/MedicationAnnotation.kt), [MedicationAnnotationRepo.formatActiveContext](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt), wired into [AnomalyDetector.evaluatePattern](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L375-L378) | Neurology runs on medication context. A patient on propranolol for tremor will look bradycardic; on topiramate for migraine, paresthesias; on a SSRI for chronic pain, HRV flattening; on levetiracetam, mood drift. Bios reads the meds list when it builds the explanation — so the patient can hand a clinician an explanation that already names the confound |
| **AlertContentPolicy as a CI gate** | [AlertContentPolicy.kt:51-83](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L51-L83) | This solves the iatrogenic-anxiety problem precisely the way a headache neurologist would want it solved. Headache patients in particular are sensitised to "you have/should/need to" language; the data-first framing is what allows long-horizon physiologic monitoring without provoking the anxiety amplification that worsens migraine and tension-type headache |
| **Physiology state gating exists as scaffolding** | [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) with PAEDIATRIC, FRAILTY_FLAG, PREGNANCY_T1..T3, POSTPARTUM, ATHLETE_HIGH_FITNESS, used by [cardiovascularStress.excludedStates](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L206-L207) | This is the right place to hang neurology-specific modifiers: an `EPILEPSY` state could suppress the AFib screen (peri-ictal HRV irregularity overlaps), a `MOVEMENT_DISORDER` state could re-weight tremor-band accelerometer signals, an `MS_RELAPSE_REMITTING` state could light up the relapse-monitoring battery. The mechanism exists |

These are real wins. The medication annotation surface in particular is the kind of un-glamorous infrastructure that materially improves any neurology consultation and that most consumer apps simply do not have.

---

## 2. Clinical gaps in detail, ordered by impact

### 2.1 Seizure detection and the unbuilt status-epilepticus URGENT path

ILAE 2017 classifies seizures by onset (focal / generalised / unknown) and by motor features. The wearable-detectable subset is narrow but high-yield: **generalised tonic-clonic seizures (GTCS)**, where the convulsive motor pattern + ictal HR surge + post-ictal HRV crash produces a clean, ~30–60 s signature that wrist accelerometer + PPG can recognise with FDA-cleared sensitivity. Empatica's Embrace2 (cleared 2018) and EmbracePlus (cleared 2023) are the de facto reference (Onorati 2017 sensitivity ~95 % for GTCS lasting >30 s in pivotal validation).

Bios has the substrate:
- [PhoneSensorAdapter.accelerometerFlow](../../android/app/src/main/java/com/bios/app/ingest/PhoneSensorAdapter.kt#L163-L184) is a continuous accelerometer stream.
- HR / HRV ingestion from any wearable adapter.
- The `EVENT`-unit pattern is well-established (`FALL_EVENT`, `TOBACCO_USE`, `SLEEP_APNEA_EVENT`).

What is missing:
- A `SEIZURE_EVENT` MetricType on `MetricDomain.NEUROLOGICAL` (the domain already exists at [MetricDomain.NEUROLOGICAL](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L310) for PAIN_SCORE and CONSCIOUSNESS_LEVEL — adding `SEIZURE_EVENT` is contract-additive, not breaking).
- A convulsive-pattern detector — band-pass 2–8 Hz, sustained ≥10 s rhythmic motion + concurrent ictal tachycardia + post-event RHR/HRV nadir. The `engine/Spectral.kt` FFT used for HRV LF/HF can be reused on accelerometer magnitude with a different window.
- A `status_epilepticus_emergency` ConditionPattern with `severityFloor = AlertTier.URGENT` mirroring the [EmergencyVitalPatterns](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) shape: `SEIZURE_EVENT` duration ≥300 s (the ILAE 2015 operational definition: t1 = 5 min for convulsive SE, the threshold beyond which spontaneous termination becomes unlikely and continued seizure causes neuronal injury), single-reading trigger.
- A `seizure_cluster_pattern` over event rate (≥3 in 24 h, the canonical "seizure cluster" definition, Haut 2007).

**Honest caveats from the bench:** focal seizures without prominent motor features (focal aware, absence, focal non-motor) are not wearable-detectable with current sensors and should not be promised. Psychogenic non-epileptic seizures (PNES) — see §2.14 — are an important differential where the *absence* of the expected ictal HR surge / postictal HRV crash is itself diagnostic and within Bios's reach. SUDEP risk reduction is the load-bearing reason for the seizure surface; nocturnal GTCS is the SUDEP-adjacent presentation, and wrist sensors miss many of them, but a sensitivity in the 50–80 % range still saves lives at population scale (Ryvlin 2013).

### 2.2 Stroke / TIA: the missing acute-symptom intake

Stroke is the unforgiving time-window neurology emergency. The 4.5 h IV-thrombolytic window (ECASS III; AHA/ASA 2019) and 6–24 h thrombectomy window with imaging selection (DAWN / DEFUSE-3) make **time-of-symptom-onset capture the highest-value single neurology data point a smartphone can record**. Stroke-detection apps (FAST-AI, BE-FAST.ai, several smartphone-based facial-asymmetry / speech-analysis prototypes) attempt to detect stroke by examining the patient through the camera and microphone; the literature on these is preliminary and the false-positive cost is non-trivial.

Bios's upstream coverage is good — [atrialFibrillationScreen](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L396-L419) is the embolic-prevention layer, [hypertensionEmerging](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) is the cerebrovascular-substrate layer, [sleepApneaScreen](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt#L36-L73) is the additional cardio-embolic-and-vascular-risk layer. The downstream — the acute event — is empty.

What I would build, kept manifesto-aligned (pull-side, owner-initiated, never push-evaluative):
- A **FAST/BE-FAST self-or-bystander checklist** as a single-screen owner-or-witness intake: Balance loss (sudden), Eyes (visual loss), Face (asymmetry), Arm (drift), Speech (slurred / aphasic), Time of last-known-well. Plain checkboxes, no scoring, no diagnostic claim. Output: a "send to emergency contact / call emergency services" prompt with the time-of-onset prominently displayed, plus a `STROKE_SYMPTOM_EVENT` row stored locally for later sharing with the stroke team.
- Auto-launch the FAST screen from the URGENT bradycardia/tachycardia notification (since AFib RVR can precipitate embolic stroke) and from a sudden BP spike pattern.
- **Do not** ship an in-app camera / microphone "stroke detector" v1. The literature is not yet at the point where the false-positive cost is acceptable for a 4.5 h window decision; this is a wearable-neurology overreach I would explicitly reject.

### 2.3 Status epilepticus, stroke, ICH as first-class URGENT triggers

The §2.1 primary-care gap on the unreachable URGENT tier is closed in code (good). The four current URGENT patterns ([EmergencyVitalPatterns.all](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L42-L49)) are SpO2, glucose, tachycardia, bradycardia. The neurology emergencies need parallel coverage:

| Trigger | Threshold | Source | Pattern |
|---|---|---|---|
| Generalised convulsive seizure duration ≥5 min | single event | ILAE 2015 operational definition of convulsive SE (Trinka 2015) | `status_epilepticus_convulsive` |
| Sudden focal deficit confirmed via FAST screen | owner / bystander entry | AHA/ASA 2019 stroke guideline | `acute_stroke_symptom_report` |
| GCS / CONSCIOUSNESS_LEVEL ≤8 | single reading | Standard airway-management threshold (ATLS) | `acute_altered_consciousness` |
| Thunderclap headache + altered LOC | symptom report | International Headache Society ICHD-3 §6.2.2 (SAH) | `thunderclap_headache_event` |
| Sustained nocturnal seizure rate ≥3 in 24 h | event count | Haut 2007 seizure-cluster definition | `seizure_cluster` |

The mechanism — `severityFloor = AlertTier.URGENT`, single-reading or single-event triggers, manifesto-clean text — is already proven by the existing four patterns. The work is wiring the inputs.

### 2.4 The unbuilt tremor-analysis pipeline

[docs/DATA_MODEL.md:76](../../docs/DATA_MODEL.md#L76) reserves `tremor_amplitude` as "planned — neurological screening." The sensor is on the bus ([PhoneSensorAdapter](../../android/app/src/main/java/com/bios/app/ingest/PhoneSensorAdapter.kt)) — accelerometer + gyroscope, continuous flow available. What needs to ship:

- Three MetricTypes on `MetricDomain.NEUROLOGICAL`: `TREMOR_AMPLITUDE_RMS` (mm/s²), `TREMOR_FREQUENCY_HZ` (peak FFT bin), `TREMOR_BAND` (categorical: REST_PARKINSONIAN_4_6 / POSTURAL_ESSENTIAL_4_12 / PHYSIOLOGIC_8_12 / OTHER).
- A `TremorAnalyzer` engine class that consumes a 30–60 s accelerometer-magnitude window (the standard MDS-UPDRS Part III postural / kinetic / rest tremor segment), runs an FFT (reuse [engine/Spectral.kt](../../android/app/src/main/java/com/bios/app/engine/Spectral.kt)), and emits the three metrics.
- An owner-initiated "tremor recording" surface — phone held in the dominant hand at rest, then with arms outstretched (postural), then performing a finger-to-nose proxy (kinetic). This is the MDS-UPDRS 3.15–3.18 capture pattern in a 90-second active-test.
- Two condition patterns:
  - `parkinsonian_tremor_signature` — sustained 4–6 Hz rest tremor on repeated weekly capture + (when present) reduced gait amplitude / arm-swing asymmetry derived from the same accelerometer stream during ambient activity.
  - `essential_tremor_signature` — sustained 4–12 Hz postural / action tremor without rest component, often bilateral.

**Precedents and honest caveats:** Apple Movement Disorder API, Roche Floodlight Open (PD), MJFF mPower, and the BRAIN-MS / Floodlight MS suite all demonstrate this works at population scale. Wearable tremor classification cannot replace MDS-UPDRS clinical examination by a neurologist — but a longitudinal at-home record across months is something *no clinic visit can produce*. The trajectory data (worsening / improving / asymmetry emerging) is what changes management. Drug-induced parkinsonism (antipsychotics, metoclopramide), enhanced physiologic tremor (thyrotoxicosis, alcohol withdrawal, beta-agonists), and psychogenic / functional tremor are differentials a clinician would weigh; the pattern explanation builder should hand off, not adjudicate.

### 2.5 Migraine prodrome — a pattern Bios is two short rules away from shipping

Migraine prodromal physiology (24–72 h pre-attack) is a well-characterised neurology phenotype:
- Yawning / mood change / cognitive dulling — behavioural (Giffin 2003).
- HRV depression — autonomic (Koenig 2016 meta-analysis).
- Sleep disturbance — both trigger and prodrome (Rains 2018; Vgontzas 2019).
- Photophobia threshold drops measurably 24 h before attack (Karsan 2018).
- Some patients show resting HR drift and skin-temperature changes.

Every one of these substrates is in Bios. A `migraine_prodrome` pattern would look almost exactly like `recoveryDeficit` ([ConditionPatterns.kt:339-363](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L339-L363)) crossed with `circadianDisruption`, gated on owner-annotated migraine attack history so it doesn't fire in the general population. The clinical value isn't push-side warning ("you will have a migraine in 36 hours" is too uncertain and too anxiety-provoking to push) — it's pull-side trend visibility for owners who already know they have migraine and want to identify their personal trigger pattern. That framing maps cleanly onto the manifesto.

What needs to ship:
- A `MIGRAINE_ATTACK_EVENT` MetricType (NEUROLOGICAL, EVENT) with owner-logged onset, duration, side, severity, autonomic features (yes/no), aura (yes/no).
- A trigger-correlation view that overlays attack timestamps against the existing autonomic / sleep / circadian / weather (future) trends.
- The `migraine_prodrome` pattern itself — owner-gated, pull-side first; push-side notification only if the owner explicitly opts in.

### 2.6 Cluster headache — the alarm-clock pattern

Cluster headache (IHS ICHD-3 §3.1) is the most diagnostically reliable circadian primary headache: 50–75 % of attacks recur at the same hour, often awakening the patient from sleep, in bouts lasting weeks-to-months separated by remission. The hypothalamic generator hypothesis (May 1998; Holland 2014) is grounded in this periodicity.

Bios already derives [CIRCADIAN_PHASE_SHIFT](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L100-L101). If a `CLUSTER_HEADACHE_ATTACK_EVENT` MetricType were added alongside the migraine event in §2.5, a simple time-of-day histogram (no ML needed — descriptive statistics) would surface the diagnostic periodicity directly. This is one of the few neurology conditions where the wearable record *is* the diagnostic gold standard, not a proxy.

### 2.7 RBD screening — the highest-value neurology prodrome marker we are missing

Idiopathic REM sleep behaviour disorder is the **strongest known prodromal biomarker for synucleinopathy** in clinical neurology: in the Schenck longitudinal cohorts and the Postuma 2019 multi-centre study, ~73–80 % of polysomnogram-confirmed iRBD patients develop PD, dementia with Lewy bodies, or multiple system atrophy within 12 years of diagnosis. This is a stronger prognostic association than almost any other prodromal signal in medicine.

Bios's sleep pipeline ([PhoneSleepInference.kt](../../android/app/src/main/java/com/bios/app/engine/PhoneSleepInference.kt), [SleepDerivations.kt](../../android/app/src/main/java/com/bios/app/engine/SleepDerivations.kt)) categorises stages into AWAKE / LIGHT / DEEP / REM but does not look for movement *during* REM (the RBD signal: loss of REM atonia, behavioural manifestations including arm movements, vocalisations, fall-from-bed events). Two paths forward, both modest in scope:

- **Passive accelerometer-during-REM**: when an external wearable provides REM staging and movement is detected during those windows above a calibrated threshold, emit a `REM_MOVEMENT_EVENT`. Repeated weekly emission triggers a `rbd_screen_signature` pattern with pull-side suggested-action "discuss polysomnogram evaluation with a sleep neurologist."
- **RBDSQ / RBD1Q questionnaire surface**: the 13-item RBDSQ (Stiasny-Kolster 2007) and the 1-question RBD1Q (Postuma 2012) are validated owner-facing screens with sensitivity ~80 % / specificity ~80 %. The `SELF_REPORTED` pathway and the [PAIN_SCORE / CONSCIOUSNESS_LEVEL](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L79-L80) precedent make this straightforward.

Catching RBD is the highest-leverage early-detection contribution a wearable can make to neurodegenerative disease — the window for disease-modifying intervention (when those drugs eventually arrive) will be defined by prodromal screening, not by clinical PD onset.

### 2.8 POTS / orthostatic intolerance — the 5-minute active-stand test

Postural orthostatic tachycardia syndrome is a wearable-friendly diagnosis precisely because the bedside test is a heart-rate trajectory:
- **POTS criterion** (Sheldon 2015; Vernino 2021): sustained ≥30 bpm HR rise within 10 min of standing (≥40 bpm in patients aged 12–19), without ≥20/10 mmHg drop in BP. Symptomatic > 3 months.
- **Orthostatic hypotension criterion** (Freeman 2011): ≥20 mmHg systolic or ≥10 mmHg diastolic drop within 3 min of standing.

Both are within Bios's existing sensor coverage. What needs to ship is a single active-test surface — owner taps "start active stand," lies supine for 5 min, then stands for 10 min while the phone records HR (and BP cuff if Withings is paired). Output: a `STANDING_HR_RISE_BPM` derivation and a `SUPINE_TO_STANDING_BP_DELTA` derivation, both with `SELF_REPORTED` / `ACTIVE_TEST` provenance.

The clinical value is enormous: dysautonomia is under-diagnosed, often dismissed for years as anxiety, and the bedside criterion is a literal trajectory measurement. A patient who arrives at clinic with three months of weekly active-stand tracings has done more than most subspecialty referrals produce. Long-COVID dysautonomia (Larsen 2022) makes this gap especially current.

### 2.9 MS relapse monitoring — the Floodlight Open template

Multiple sclerosis is the canonical chronic neuro-immunology condition for at-home longitudinal monitoring. Roche's Floodlight Open (and the MS Mosaic / Apple MS app) ships:
- A daily ~2-minute battery: pinch test (hand dexterity), draw a shape (motor planning), e-walk / U-turn test (gait), SDMT-analog (information processing speed).
- Passive: gait variability, step asymmetry, daily activity, sleep.

Bios has the passive substrate. The active-test slot is reserved at [MetricType.REACTION_TIME_MS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L255) but no companion writes it. The minimum-viable MS surface, in scope-discipline order:

- A `ms_relapse_prodrome` pattern over the existing [chronic_inflammation](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L309-L336) and [recovery_deficit](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L339-L363) signatures, gated on an owner-declared `MS_DIAGNOSED` flag in PhysiologyState (this is the pattern-by-pattern modifier the [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) scaffold contemplates).
- A `FATIGUE_SCORE` (NEUROLOGICAL, SCORE) self-report — Modified Fatigue Impact Scale or simplified Fatigue Severity Scale daily 1–10.
- A `COGNITIVE_FOG_SCORE` self-report (or, eventually, the SDMT companion).
- A `gait_variability_signature` derived from ambient phone-in-pocket accelerometer (stride-time coefficient of variation > personal baseline).

The disease-modifying-therapy adherence question (interferon, glatiramer, fumarates, anti-CD20, S1P modulators — all with distinct side-effect signatures) is the [MedicationAnnotation](../../android/app/src/main/java/com/bios/app/model/MedicationAnnotation.kt) surface's natural extension; per the §2.5 medication-context gap in the primary-care audit, this is high-leverage and already partially built.

### 2.10 Cognitive decline / MCI — passive smartphone signal composition

Bios cites BiAffect's typing-dynamics work in [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) (npj Digital Medicine 2024) and ingests [TYPING_CADENCE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L215) from W2F. The MCI / early-dementia literature (Kourtis 2019; Buegler 2020; Apple Cognition preview; the Akili EndeavorRx FDA clearance for pediatric ADHD as proof-of-concept that the regulator accepts smartphone-based cognitive endpoints) is built on a similar feature set:

- Typing speed and inter-keystroke variability ↓.
- App-switching entropy ↓.
- Gait variability ↑.
- Sleep regularity ↓ (existing [SLEEP_REGULARITY](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L95) metric).
- Circadian fragmentation ↑ (existing [circadianDisruption](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt) pattern).
- Hearing loss (Lin 2011 — strongest single modifiable midlife dementia risk factor; not currently captured).

A `cognitive_decline_signature` pattern over the existing substrates would not diagnose MCI — that is the MoCA / clinical examination's job — but it would surface the trajectory in the owner's own data. The honest framing: this is a *trajectory* signal, not a diagnostic. Sensitivity for early MCI is modest; specificity is poorer because many transient states (depression, sleep deprivation, medication side effects, post-COVID brain fog) produce the same passive-feature signature.

The manifesto-aligned framing: pull-side only. The owner navigates into a "cognitive trajectory" pull-side surface; nothing pushes "your typing speed suggests cognitive decline" because the false-positive cost is unacceptable.

### 2.11 Headache diary and medication-overuse headache (MOH)

A headache neurologist's clinic visit is structured around the diary. The IHS ICHD-3 diagnostic criteria for migraine, tension-type headache, cluster, and medication-overuse all require frequency and duration data the patient brings. Bios has the [HealthEventType.SYMPTOM](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L116-L123) primitive but no structured headache vocabulary on top.

The minimum-viable headache diary:
- `HEADACHE_ATTACK_EVENT` (NEUROLOGICAL, EVENT): onset, duration, severity 0–10, side, character (throbbing / pressing / stabbing), associated features (photophobia / phonophobia / nausea / aura / autonomic), abortive medication taken (links to `MEDICATION_INTAKE`).
- An aggregator that counts attacks/month and abortive-medication days/month.
- A `medication_overuse_headache_risk` pattern that fires when triptan/ergot/combination days exceed 10/month or simple analgesic days exceed 15/month over 3 consecutive months (IHS ICHD-3 §8.2 criterion).
- A `chronic_migraine_threshold` pattern at ≥15 headache days/month with ≥8 migraine-like days (IHS ICHD-3 §1.3) — this is the threshold for considering preventive therapy / CGRP-blocker indications.

The medication-context gap recurs here: per-event "I took a triptan today" capture is the entire point. `MEDICATION_INTAKE` ([MetricType.kt:242](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L242)) exists; it needs a writer.

### 2.12 Functional / psychogenic non-epileptic seizures (PNES)

This is a sensitive area and I will be careful with it. PNES (also called dissociative seizures, functional seizures) account for ~20 % of patients in epilepsy monitoring units. The clinical and management implications differ enormously from epileptic seizures, and the discrimination is often delayed by years.

Wearable data contributes to the differential, never resolves it:
- Epileptic GTCS: characteristic ictal tachycardia surge (sympathetic drive), postictal HRV crash sustained 30+ min, postictal RHR slow return to baseline.
- PNES: usually no ictal HR surge of the same magnitude, no postictal HRV crash, often shorter recovery to baseline. Patel & LaFrance 2018 review; Reinsberger 2015 cardiovagal data.

A `seizure_autonomic_signature` derivation that captures peri-event HR / HRV trajectory around any `SEIZURE_EVENT` would give an epileptologist exactly the data needed to support (not replace) video-EEG and clinical assessment. The manifesto framing matters intensely here: Bios reports the trajectory; it does not classify "this was psychogenic." That classification belongs to the clinician and the patient, not the instrument.

### 2.13 Concussion / TBI recovery monitoring

Sports-medicine / neurology overlap. Post-concussion syndrome is characterised by autonomic dysregulation (HRV depression, exercise intolerance with HR over-response), sleep disturbance (latency ↑, fragmentation ↑, efficiency ↓), and cognitive-symptom fluctuation — all measurable in Bios's existing substrate. The Buffalo Concussion Treadmill Test (Leddy 2018) is the gold-standard supervised exertion test; a smartphone surrogate "track HR response to a standardized 10-minute walk" would not replace it but would surface the trajectory.

A `concussion_recovery_track` pull-side view, owner-initiated after a head-injury event, that plots HRV / sleep / activity over the standard 7–28 day recovery arc would be high-yield. SCAT-5 and PCSS symptom inventories are validated self-report scales that map onto the same `SELF_REPORTED` pipeline as the existing biomarker entry surface.

### 2.14 Light exposure and the seasonal / shift-work / jet-lag axis

[circadianDisruption](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt#L33-L61) already covers the substrate. From a neurology bench, the explicit conditions to surface (pull-side, owner-initiated) are:

- **Seasonal affective disorder** — winter latitude + reduced morning bright-light exposure. Lewy 2006 bright-light therapy literature.
- **Bipolar disorder circadian instability** — already cited via the Seoul Nat'l Univ 2024 work in [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459); the headache and seizure overlap (catamenial and circadian seizure clusters in epilepsy; cluster-headache periodicity) deserves explicit cross-reference.
- **Shift-work disorder** — IHS / AASM categorisations. The owner-declared "I work nights" flag (a PhysiologyState extension) would suppress the misalignment pattern as pathologic and re-purpose it as a phase-shift-monitoring trajectory.
- **Jet-lag** — owner-declared travel events; circadianDisruption interpretation re-frames.

These are pull-side framings of an existing pattern; the work is interpretation surface, not new detection.

### 2.15 Vagal-tone proxy for neuro-immunology and gastroparesis assessment

[parasympathetic_tone](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L40) (lnRMSSD), [hrv_hf_power](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L43-L44), [lf_hf_ratio](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L42), [stress_score](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L41) (Baevsky SI) are all shipped. From a neuro-immunology and autonomic-medicine bench, this is genuinely impressive — most consumer apps stop at the RMSSD single number. The Tracey 2002 cholinergic anti-inflammatory pathway, the cardiovagal involvement in MS (Flachenecker 1999), the cardiac sympathetic denervation in PD (Goldstein 2014, MIBG imaging proxies), the gastroparesis-associated dysautonomia all have HF-HRV as their non-invasive surrogate.

The gap is not detection; it is exposure on the pull-side dashboard. A neurology patient who could show their neurologist a 90-day HF-HRV trend annotated with infusion days (the disease-modifying therapy gap from §2.5) would be doing something genuinely useful no clinic visit produces.

---

## 3. Manifesto / clinical-ethics tension points specific to neurology

### 3.1 "Never evaluate the person" vs. neurodegenerative prognostic data

The manifesto position holds for almost every Bios surface. The exception that needs explicit framing is the RBD / synucleinopathy prognostic association (§2.7). A patient who screens positive for RBD on the RBDSQ has, on present evidence, a 73 % twelve-year risk of developing PD / DLB / MSA. That number is true, load-bearing, and a clinician would communicate it — but it sits exactly where the manifesto says Bios should not.

The right resolution, I think, is the same as in the primary-care audit's §3.1: pull-side surfaces can be specific where push-side surfaces stay descriptive. The owner who navigates into "RBD screen result" sees the literature-anchored risk number with full citation. The owner who is *not* asking for it never sees a push notification telling them their REM behaviour might mean they have PD coming. That is the manifesto-aligned ceiling for prognostic neurology data.

### 3.2 "Silence is a feature" vs. status epilepticus

Silence is correct for trend notices; silence is incorrect for a generalised convulsive seizure that has continued for >5 min. The §2.3 URGENT pattern wiring is the way to honour both — push when the threshold is unambiguous and the time-cost of waiting is neurological injury, stay silent on trends.

The harder case is the seizure-cluster signal (≥3 in 24 h): close enough to clinical concern to push, far enough from emergency to avoid the URGENT channel. AlertTier.ADVISORY (already used for the existing trend patterns) is probably the right home; the manifesto framing — "data observation, discuss with epileptologist" — is the [AlertContentPolicy](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) banlist-clean register.

### 3.3 The Schwabe problem in neurology (PNES, functional neurological disorder)

PNES (§2.12), functional movement disorders, and functional cognitive disorder all share the property that the wearable signature can suggest the differential — but the *delivery* of that signal is clinically delicate. A patient who reads "your seizure event did not show the expected autonomic signature" in a push notification could reasonably feel disbelieved. This is one of the few places where Bios's push-side / pull-side architecture has to do real ethical work.

My recommendation: the autonomic-signature data is captured and stored; it surfaces only on the explicit "share with clinician" pathway (FHIR bundle, professional review flow). The owner never sees an in-app text that could be read as "this might not have been a real seizure." That interpretation belongs to the consultation room.

---

## 4. Tiered recommendations

**Tier A — wire before any new feature (neurology safety and clinical leverage)**

1. **`SEIZURE_EVENT` MetricType + `status_epilepticus_convulsive` URGENT pattern (§2.1, §2.3).** Same `severityFloor = URGENT` + single-event-trigger mechanism as the existing [EmergencyVitalPatterns](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt). Empatica-style convulsive-pattern detector can ship in a follow-up; owner-logged event entry is enough for v1.
2. **FAST/BE-FAST owner-or-bystander stroke intake surface (§2.2).** Pull-side, single-screen checklist, time-of-last-known-well prominent. Output a `STROKE_SYMPTOM_EVENT` row and a "call emergency services" prompt. No camera / microphone "AI stroke detection" — that is premature.
3. **GCS ≤8 → URGENT pattern over the existing [CONSCIOUSNESS_LEVEL](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L80) MetricType (§2.3).** Single-reading trigger, mirrors the SpO2 / glucose pattern shape.
4. **Tremor analysis pipeline (§2.4).** `TREMOR_AMPLITUDE_RMS` / `TREMOR_FREQUENCY_HZ` / `TREMOR_BAND` MetricTypes; `TremorAnalyzer` engine on top of the existing `engine/Spectral.kt` FFT; owner-initiated 90 s capture aligned with MDS-UPDRS 3.15–3.18.
5. **Per-event `MEDICATION_INTAKE` writer for triptans / abortives / DMTs (§2.11, §2.9).** The MetricType exists; the writer is the gap. Closes both the MOH detection path and the MS DMT adherence path in one PR.

**Tier B — neurology coverage breadth, next quarter**

6. **Migraine and cluster headache event surface + diary aggregations (§2.5, §2.6, §2.11).** `MIGRAINE_ATTACK_EVENT`, `CLUSTER_HEADACHE_ATTACK_EVENT`, `HEADACHE_ATTACK_EVENT` with IHS ICHD-3-shaped payloads. Per-attack overlay against the existing autonomic / sleep / circadian trends.
7. **MOH-risk and chronic-migraine-threshold patterns (§2.11).** Built on top of #5 and #6. IHS ICHD-3 §8.2 and §1.3 thresholds.
8. **RBD screening surface — RBDSQ questionnaire + (when wearable provides REM staging) REM-movement-event detection (§2.7).** Highest-leverage prodromal neuro-degeneration screen available to a wearable.
9. **POTS / OH 5-minute active-stand test (§2.8).** `STANDING_HR_RISE_BPM` and `SUPINE_TO_STANDING_BP_DELTA` derivations; pull-side test surface; owner-initiated.
10. **`MS_DIAGNOSED` PhysiologyState + `ms_relapse_prodrome` pattern (§2.9).** Re-purposes the existing chronic_inflammation + recovery_deficit signatures inside the owner-declared MS context.
11. **Cluster-headache circadian-periodicity visualisation (§2.6).** Time-of-day histogram over `CLUSTER_HEADACHE_ATTACK_EVENT`s. Diagnostic-quality data; no ML needed.

**Tier C — population coverage and longitudinal monitoring, once foundation is solid**

12. **Concussion recovery track pull-side surface (§2.13).** SCAT-5 / PCSS owner-entry + automatic HRV / sleep / activity overlay over the recovery arc.
13. **Cognitive trajectory pull-side surface (§2.10).** Composes existing typing cadence / sleep regularity / circadian fragmentation / gait variability into a single owner-navigable trajectory view. Pull-side only — never push.
14. **`FATIGUE_SCORE` and `COGNITIVE_FOG_SCORE` self-reports (§2.9, §2.13).** Validated short scales (FSS, modified Fatigue Impact, neuro-QOL cognitive fatigue) for MS, post-concussion, long-COVID, chronic neuro-inflammatory conditions.
15. **Seizure peri-event autonomic signature (§2.12).** Stored, but surfaced only via the clinician-share pathway. PNES discrimination is a clinical conversation, not an in-app message.
16. **Active-test suite for MS / PD (§2.4, §2.9).** SDMT digital, finger-tapping rate, U-turn / gait-variability passive — Floodlight-class. Needs a companion-style architecture so the active-test engine isn't core-Bios scope creep.

**Do not adopt**

- **Smartphone-camera / microphone-based "AI stroke detection" v1.** Literature is preliminary; the false-positive and false-negative costs at a 4.5 h pharmacologic window are unacceptable. The FAST checklist in Tier A is the manifesto-aligned ceiling for stroke surface.
- **Push-side prognostic communication of synucleinopathy risk from RBD screening.** Pull-side only. The number is real; the delivery channel matters.
- **A composite "neurological health score" or "brain age."** Same reasoning as the §3.1 primary-care audit's rejection of biological-age composites — and stronger here, because neurology patients are particularly sensitised to scoring of cognitive / motor / affective function.
- **In-app PNES classification of seizure events.** Capture the autonomic signature, share via the clinician pathway, do not surface a "psychogenic / epileptic" classification in the owner's view.
- **Push notifications for migraine prodrome prediction in the general population.** Pull-side and opt-in only, for owners already migraine-diagnosed who explicitly request the trigger-correlation view.

---

## 5. Summary line for the project

> Bios already gives a neurologist most of the substrate they would design a patient-side monitoring app to capture: full HRV decomposition with the vagal-tone proxies, multi-component sleep decomposition with regularity and fragmentation, ambient-light + sleep circadian-misalignment detection, AFib screening with proper ECG-confirmation framing, hypertension monitoring with the home-BP median convention, sleep-apnea passthrough with AASM thresholds, and — uncommon in this category — a medication-annotation surface the alert builder reads. The neurology gaps that remain are concrete and concentrated: no seizure event or status-epilepticus URGENT pattern, no acute-stroke FAST intake, no tremor analysis pipeline despite live accelerometer, no headache diary or medication-overuse-headache gate, no RBD screen, no POTS active-stand test, and no MS-aware re-weighting of the chronic-inflammation pattern. None of these requires architectural change — the EVENT-unit pattern, the `severityFloor = URGENT` mechanism, the PhysiologyState gating, the `MetricDomain.NEUROLOGICAL` slot, the `engine/Spectral.kt` FFT primitive, and the `MEDICATION_INTAKE` MetricType are all already in place. The work is wiring, the additions are largely owner-initiated pull-side surfaces, and the manifesto position is preserved. The single highest-stakes addition is the seizure-event surface with its status-epilepticus URGENT path; the single highest-leverage addition for population neurological health is the RBD screen, because the synucleinopathy prodrome window is where the disease-modifying therapies of the next decade will be deployed.
