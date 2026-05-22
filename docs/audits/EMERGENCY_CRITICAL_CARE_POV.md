# Emergency Medicine & Critical Care Audit — Bios at the Door of the ED

**Scope:** Bios's clinical reach evaluated from the standpoint of an EM board-certified attending with critical-care fellowship exposure. Bios is *outpatient* / *consumer-Android* software; the EM lens is therefore mostly **upstream** ("what would have brought this owner through my door, and could it have been detected earlier?") and **downstream** ("what does Bios do for the patient I just discharged, and for the one I just admitted to the ICU?"). The third lens — **at the door** — is narrower but real: when Bios decides an event is `URGENT`, what does it actually do, and is that the right thing?
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Emergency medicine + critical care, ACEP / SCCM / AHA-ACLS / NEWS2-anchored. Not a 510(k) or pre-hospital-protocol audit. The catalogue entry that anchors this audit is [MEDICAL_SPECIALTIES_WORLDWIDE.md §1.2](MEDICAL_SPECIALTIES_WORLDWIDE.md) ("Critical care medicine / Intensive care; Emergency medicine; Toxicology; Hyperbaric and undersea medicine; Wilderness medicine; Disaster medicine"). High-sensitivity, false-positive-tolerant framing — an EM physician would rather a wearable over-call than miss the one event that mattered.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt), [SleepApneaPattern.kt](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt), [RespiratoryExacerbationPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/RespiratoryExacerbationPatterns.kt), [CompanionConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt). Skimmed first: [MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md) and [CARDIOLOGY_POV.md](CARDIOLOGY_POV.md) to avoid re-litigating closed Gaps #1 and #8 (URGENT-tier reachability and absolute vs personal-baseline cutoffs).

---

## Executive summary

Bios is, from an EM bench, **the most coherent privacy-preserving telemonitoring layer I have audited for the patient population that ends up in my department.** The URGENT tier is reachable as of [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) (closing primary-care audit Gap #1) and uses literature-anchored absolute cutoffs (closing primary-care audit Gap #8). The hypertensive-crisis pattern at [HypertensionPatterns.hypertensiveUrgency](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) correctly uses the universal 180/120 threshold with the AHA-standard "rest 5 min, re-check on the opposite arm" guidance — that text reads like ED triage-nurse training material. The medication-annotation surface, the manifesto's refusal to push behavioural nudges, the explicit 34 % PPV citation on the AFib pattern, the multi-tier respiratory exacerbation patterns for COPD and asthma — these are the choices of a team that has talked to clinicians.

But the EM-and-critical-care lens surfaces a different gap profile from the primary-care or cardiology audits. The relevant questions are not "is the lipid panel multi-marker" — they are: *when this owner is dying, does anyone know? when this owner survives the ICU, does anything help them recover? when this owner avoids the ED because they can't afford it, does Bios at least keep them safer?* Ordered by impact (morbidity / mortality / time-to-treatment-window the gap could shift):

1. **The URGENT pathway has no destination.** [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) correctly classifies a SpO2 ≤85 or glucose ≤54 as `AlertTier.URGENT` and pushes through `CHANNEL_URGENT` ([AlertManager.kt:33](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt#L33)). The notification appends the regional regulatory disclaimer and offers a `BigTextStyle` explanation. What it does **not** do is offer a one-tap deep-link to the regional emergency number (911 in the US, 112 across the EU, 999 GB, 119 JP, 113 several Latin American countries), and what Bios does not *hold* at all is an emergency-contact / next-of-kin surface. [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) carries `regulatoryBody` and `alertDisclaimer` per locale but no `emergencyNumber`. From an EM standpoint this is the single most consequential gap: an owner with confirmed hypoxia at 84 % alone in their apartment needs a path *out of the alert* into pre-hospital care, and Bios currently terminates at the notification drawer.
2. **No sepsis-screening pattern despite every input being present.** qSOFA (RR ≥22, SBP ≤100, AMS), SIRS, and NEWS2 ([Royal College of Physicians 2017](https://www.rcplondon.ac.uk/projects/outputs/national-early-warning-score-news-2)) are the multi-signal early-warning composites the Surviving Sepsis Campaign (Evans 2021) and the in-hospital MET/RRT literature have converged on. Bios already ingests resting HR, respiratory rate ([MetricType.RESPIRATORY_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L52)), BP via the cuff adapter, skin temperature, and SpO2 — every NEWS2 input except level of consciousness (and even that exists as [CONSCIOUSNESS_LEVEL](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L80) per the manual-clinical-reading entry surface). A `sepsis_screen` convergence pattern that scores NEWS2 ≥5 (or qSOFA ≥2) on rolling-window vitals is mechanically the same shape as the existing `infectionOnset` ([ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt)) pattern but anchored on the *severity composite*, not the *onset signature*. The Henry et al. (2019 Nat Med) and Kollef et al. (Crit Care Med) wearable-sepsis-early-warning work is exactly the literature this would cite. For the population of immunocompromised, post-surgical, chronically catheterised, or simply elderly owners, this is the single highest-leverage missing pattern.
3. **No opioid-overdose / respiratory-depression pattern.** The University of Washington SAFE study and the Apple Watch–derived sonar / accelerometer-RR opioid overdose work (Nandakumar 2019 *Sci Transl Med*; Mathur 2024) have established that a wearable can detect the bradypnea-to-apnea trajectory of opioid overdose, and there is now consumer-grade hardware in the field. Bios has [RESPIRATORY_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L52) (Withings, Garmin, WHOOP MG all emit it) and SpO2. An `acute_respiratory_depression` URGENT pattern over sustained RR <8 + SpO2 dropping would catch the most clinically important *acute* respiratory event in the US between 2015 and 2024. The overdose-epidemic population overlaps almost entirely with the manifesto's "owner whose autonomy must be protected" framing — these are exactly the people for whom institutional systems have failed.
4. **No anaphylaxis convergence.** Sudden HR ≥120 + SpO2 dropping + (if a cuff is present) BP dropping is the wearable-detectable anaphylaxis signature; the inflection is the *speed* (minutes, not hours) of multi-system shift. Bios's pattern engine is window-based ([AnomalyDetector.fetchRecentValues](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L427-L434), all patterns use ≥12 h windows). Anaphylaxis is a 5-minute event and the existing pattern shape will not catch it. From an EM standpoint, anaphylaxis is one of the few diagnoses where wearable detection could plausibly change pre-hospital outcomes — an EpiPen reminder at the right moment is the difference between an outpatient observation and intubation. Auto-EpiPen actuation is out of scope; the alert is not.
5. **No cardiac-arrest detection.** Apple Watch fall-detect (deceleration + impact pattern) and Pixel Watch crash-detect (multi-axis kinematics) are FDA-cleared and routed to emergency services with location. Bios has the phone accelerometer ([PhoneSensorAdapter](../../android/app/src/main/java/com/bios/app/ingest/PhoneSensorAdapter.kt)) and the [Virgil `FALL_EVENT` companion channel](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt), but no convulsive-collapse or asystolic-PPG signature, and no integration into a regional emergency-services dispatch pathway. The fall-detection use case is largely covered by Virgil (correct ecosystem split per [ECOSYSTEM_BOUNDARIES.md](../ECOSYSTEM_BOUNDARIES.md)); the cardiac-arrest variant is not.
6. **Stroke recognition is upstream-only and rightly so.** Sudden hemiparesis, facial droop, dysarthria — the bedside FAST exam — is essentially undetectable from a wrist wearable. The neurology audit ([NEUROLOGY_POV.md §2.2](NEUROLOGY_POV.md)) flagged the missing FAST-checklist intake surface; the EM perspective converges. **AFib upstream detection is the real lever** — the Apple Heart Study / mAFA-II PPV is honest enough that catching paroxysmal AF before the embolic event is the cardiology-flagged ([CARDIOLOGY_POV.md §2.1](CARDIOLOGY_POV.md)) high-leverage move. EM agrees: the stroke we want to prevent is the one whose AFib substrate we caught months earlier.
7. **No pulmonary-embolism convergence.** Sudden unexplained tachycardia + SpO2 drop + (if pleuritic chest pain were a logged symptom) the convergence is recognisable to wearable-aware clinicians. Bios has the substrate; it has no PE-aware pattern. The cardiology audit also flagged this; from an EM standpoint PE is the high-impact missed diagnosis in the under-50 syncope-or-dyspnea cohort, and the post-COVID hypercoagulability cohort makes the population larger than it was pre-2020. Wells score / D-dimer are clinic-only; the *pattern-recognition* prompt could be Bios-only.
8. **Hypoglycaemia is correctly URGENT — but doesn't escalate cognitively.** [EmergencyVitalPatterns.hypoglycemiaCritical](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L89-L111) fires at glucose ≤54 with ADA Level-2-anchored text and correct 15–20 g fast-carb guidance. The gap: this is the population (insulin-treated, often living alone, often with hypoglycaemia unawareness from autonomic neuropathy) for whom *the owner is the wrong escalation target* — by the time the alert lands, they may be cognitively impaired. The pattern needs to know about glucagon, about the regional emergency number, and ideally about a previously-named emergency contact. Same root cause as Gap #1.
9. **Hypotensive shock has no pattern.** The four shock states (septic, cardiogenic, hypovolaemic, anaphylactic) converge on SBP <90 / MAP <65 with compensatory tachycardia. Bios has BP and HR; the only BP-anchored URGENT pattern is the hypertensive-crisis upper bound. The single-reading SBP-cutoff URGENT pattern for low BP is missing — and is mechanically identical to the existing high-BP pattern, just inverted.
10. **Hyperglycaemic emergencies (DKA / HHS) are absent.** Sustained glucose >400 mg/dL with rising RR (Kussmaul) is the wearable-detectable DKA signature in CGM-equipped diabetic owners. Bios has CGM ingest (Dexcom adapter) and respiratory rate. No `dka_signature` pattern ships. For Type-1 owners — particularly the paediatric subset — this is a recurrent ED admission diagnosis whose at-home detection materially shifts outcomes.
11. **No emergency-contact / next-of-kin surface anywhere in the schema.** When a URGENT alert fires and the owner is incapacitated, Bios has no model of *who to notify* and no mechanism to notify them. This is the structural form of Gap #1: without an "if the owner does not acknowledge within N minutes, do X" pathway, the URGENT tier is clinically a dead-end notification.
12. **Trauma / fall / crash detection is partial.** Virgil's FALL_EVENT is wired ([Virgil companion contract](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt)), but vehicular crash-detect (Pixel Watch precedent) is not, and Bios's phone-sensor adapter does not currently run a crash-kinematics classifier. The cross-walk between trauma kinematics and physiologic instability (Trauma Triage Criteria: SBP <90, HR >120, GCS ≤13, RR <10 or >29 — Cooper 2018, ACS-COT Field Triage Decision Scheme) is unbuilt.

Items 13–21 (heat / cold illness, suicide-ideation acute, mass-casualty fit, wilderness medicine, post-ED discharge monitoring, post-ICU recovery, EMS interoperability, telemedicine triage, healthcare-access framing) are flagged as moderate-impact and discussed in §3.

The standing strength to call out before the gaps: **Bios's offline-first, no-Google-Play-Services architecture is, from a disaster-medicine standpoint, the most clinically valuable choice in the entire portfolio.** That is the §3.5 finding and it deserves its own paragraph in any ED-physician summary of the platform.

---

## 1. What Bios already does well, viewed from the resus bay

| Quality | Evidence in code | Why an EM attending cares |
|---|---|---|
| **URGENT-tier pathway exists with absolute cutoffs, not z-scores** | [EmergencyVitalPatterns.kt:40-180](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L40-L180) — SpO2 ≤85, glucose ≤54, RHR ≥130 / ≤35; `severityFloor = URGENT`; explicit "wearable readings can have artifact, re-check on warm clean finger" language | This is what the primary-care audit asked for and the cardiology audit confirmed: a SpO2 of 86 is not a 1.5σ deviation from this owner's baseline, it is hypoxia. The architecture now distinguishes *trend* from *value*, which is the same distinction ED triage makes between a chief complaint and a vital sign |
| **Hypertensive-crisis pattern uses universal 180/120 cutoff with the AHA "rest 5 min, re-check on opposite arm" protocol** | [HypertensionPatterns.hypertensiveUrgency:118-146](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt#L118-L146) | This is the same instruction a triage RN gives at the door. Most consumer apps either never recognise the threshold or skip the re-check step, which sends well-controlled white-coat patients to the ED unnecessarily. Bios's text is clinically literate |
| **Pulse-oximetry alert correctly accounts for artifact** | [spo2Critical.suggestedAction](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L74) — "Motion artifact, cold fingers, or nail polish can produce false low readings" | Every EM physician has seen the panicked patient with a SpO2 of 78 % whose finger was cold. Acknowledging the artifact in the alert text protects against the over-triage that consumer-pulse-ox alerts otherwise produce |
| **Bradycardia pattern explicitly carves out athletic conditioning and rate-control medication** | [bradycardiaCritical](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L157-L179) names "highly trained endurance athletes" and "documented rate-control medication (beta-blockers, certain calcium channel blockers)" | The single most common false-positive shape an EM physician sees from consumer wearables. Bios's acknowledgement of the differential in the alert itself is the right framing |
| **Multi-tier respiratory exacerbation patterns for COPD and asthma** | [RespiratoryExacerbationPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/RespiratoryExacerbationPatterns.kt) — GOLD-cited COPD pattern over personal-baseline SpO2 drift; GINA-cited asthma pattern emphasising symptom-cluster signals because SpO2 is a late sign in asthma | The post-ED COPD/asthma patient is one of the highest 30-day-readmission-rate populations in EM. A pattern that fires during the *prodromal* window (1–2 weeks pre-exacerbation per GOLD 2024) is the data substrate a discharge plan should have referenced |
| **Sleep-apnea screen with AASM thresholds + AHA cardiovascular framing** | [SleepApneaPattern.kt](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt) cites Somers 2008 AHA/ACC scientific statement and Berry 2020 AASM scoring manual | OSA is the most underdiagnosed condition that ED physicians see consequences of — the AFib-with-RVR at 3 am, the obesity-hypoventilation patient in respiratory failure, the hypertensive emergency on an undiagnosed apnoeic. Bios correctly classifies it as cardiology-adjacent, not respiratory-only |
| **The alert text obeys a CI-gated content policy** | [AlertContentPolicy.kt:51-83](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L51-L83) | The EM-discharge-instructions parallel: every ED in the country has at some point shipped patient-education material that read like a wellness-app push. Bios's banlist of "you should / streak / level up / health score" is the same discipline a thoughtful discharge-instruction set applies |
| **FHIR R4 export with LOINC coding** | [FhirImporter.kt](../../android/app/src/main/java/com/bios/app/export/FhirImporter.kt) plus FHIR-bundle export | An ED patient who arrives with a USB stick or a phone-share of their last 30 days of vitals is no longer an abstract idea. The cardiology audit makes the same point; from EM, what matters is that the bundle conforms — receiving systems will actually parse it |
| **Region-config carries jurisdiction-specific clinical thresholds and disclaimers** | [RegionConfigProvider.kt:234-402](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt#L234-L402) — six regions, locale-aware fever / SpO2 / hypertension thresholds, FDA/MHRA/EMA/Health Canada/TGA/PMDA disclaimers | Different EDs in different jurisdictions use different reference ranges. The Japanese fever threshold of 38.5 °C, the UK NICE hypertension cutoff of 140/90, the Canadian Diabetes Canada fasting-glucose threshold — Bios localises these correctly. This is unusually disciplined for a consumer app |
| **Manual clinical-reading entry surface (TAS/TAD/Sat/FC/Temp/FR/EVA/GCS/O2 flow as one event)** | [ClinicalReadingEntryScreen](../../android/app/src/main/java/com/bios/app/ui/clinical/ClinicalReadingEntryScreen.kt), [ManualReadingRepo.kt](../../android/app/src/main/java/com/bios/app/data/ManualReadingRepo.kt) — captures the standard EM/critical-care vital-sign bundle in one event, including pain (EVA/VAS/NRS), GCS (with AVPU lossless conversion via [ConsciousnessScale.kt](../../android/app/src/main/java/com/bios/app/data/ConsciousnessScale.kt)), and oxygen flow rate | This is the *charting language* of EM triage. Most consumer apps cannot represent a GCS or an O2 flow rate; Bios models both with correct scale provenance. The owner who survives an ICU stay and goes home on supplemental O2 can actually record what they need to record |
| **Offline-first, no-Play-Services, IPFS-and-Tor-routed-on-LETHE architecture** | Build flavors `lethe` and `standalone` per [ROADMAP.md](../ROADMAP.md); no Google Play Services dependency anywhere | This is the disaster-medicine win. See §3.5 below — in a mass-casualty / grid-down / cellular-saturation event, Bios continues to function. No commercial consumer health platform makes this choice |

These are the strengths an EM physician should not take for granted. The URGENT-tier infrastructure was the precondition for everything in §2; without it, the gaps below would be unaddressable at the architectural level.

---

## 2. Clinical gaps in detail, ordered by impact

### 2.1 The URGENT pathway terminates at the notification drawer

[AlertManager.sendNotification](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt#L48-L91) routes `AlertTier.URGENT` to `CHANNEL_URGENT` with `NotificationCompat.PRIORITY_HIGH`, appends the regional disclaimer, and ends. That is the full path. The notification has:

- No tap-to-call deep link to the regional emergency number.
- No "I am OK" / "I need help" acknowledgement that triggers a follow-up timeout.
- No designated emergency-contact notification.
- No persistent surface on the lock screen beyond the standard high-priority Android channel.

[RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) carries `regulatoryBody`, `alertDisclaimer`, `fhirProfileUrl`, and the unit/clinical-threshold tables — but no `emergencyNumber` field. The regional emergency numbers are public (911 US, 112 EU-wide, 999 GB, 119 JP, 000 AU, 911 CA, 113 several Latin American countries, 110/119 KR, etc.) and adding a single `String?` field per region with a `tel:` deep-link `PendingIntent` on the notification would be a one-day change.

The deeper architectural change is the **emergency-contact surface**. There is no entity in [/home/mia/Bios/android/app/src/main/java/com/bios/app/model/](../../android/app/src/main/java/com/bios/app/model/) that holds a designated contact, a relationship label, or an opt-in escalation policy. From an EM standpoint, this is the missing piece that converts URGENT from "the owner sees an alert" to "*someone* knows." For the population most likely to benefit — elderly, living alone, diabetic, post-discharge, on opioids, on insulin — the owner being the only escalation target is structurally the wrong answer.

The manifesto's "owner is final" principle is *not* violated by this. The owner sets the contact, the owner sets the policy (notify after N minutes of non-acknowledgement, never notify, notify only for specific tiers), the owner can erase it in the same wipe that destroys everything else. The principle is *autonomy*, not *isolation*. Most owners, asked, would name a person.

**Recommendation:**

1. Add `emergencyNumber: String?` to [RegionConfig](../../android/app/src/main/java/com/bios/app/config/RegionConfig.kt) populated per locale. Surface a `tel:` action in [AlertManager.sendNotification](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt#L48-L91) when `tier == URGENT`.
2. Add an `EmergencyContact` entity (name, relationship, phone, escalation policy, per-tier opt-in). Storage in the encrypted main DB, included in the LETHE wipe pathway. Owner-set, owner-revocable, off by default.
3. Add a `URGENT_ACKNOWLEDGEMENT_TIMEOUT` worker — when an URGENT alert is not acknowledged within owner-set N minutes (default disabled), execute the escalation policy (SMS to contact, locally-composed; *not* via any cloud service).

This is the single most clinically impactful EM-side change and it is fully within the manifesto envelope.

### 2.2 Sepsis screening: every input is present, no pattern fires

NEWS2 ([Royal College of Physicians 2017](https://www.rcplondon.ac.uk/projects/outputs/national-early-warning-score-news-2)) is the validated multi-signal early-warning composite the NHS and a growing share of US and international hospital systems use to trigger MET/RRT activation. Its inputs:

| Input | Bios MetricType | Available? |
|---|---|---|
| Respiratory rate | [RESPIRATORY_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L52) | Yes (Withings, Garmin, WHOOP MG, manual) |
| SpO2 | [BLOOD_OXYGEN](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L49) | Yes |
| Supplemental O2 (yes/no) | [OXYGEN_FLOW_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L56) | Yes (manual entry surface ships) |
| Systolic BP | [BLOOD_PRESSURE_SYSTOLIC](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L47) | Yes |
| Heart rate | [HEART_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L38) / [RESTING_HEART_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L46) | Yes |
| Temperature | [SKIN_TEMPERATURE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L68) (proxy) | Partial — wearable wrist-temp is not core temp |
| Level of consciousness | [CONSCIOUSNESS_LEVEL](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L80) (GCS or AVPU lossless) | Yes (manual entry only) |

qSOFA (Singer 2016, *JAMA*; the simplified pre-hospital / ED-triage variant): RR ≥22, SBP ≤100, altered mentation. **All three inputs are on the bus already.** No pattern composes them.

A `sepsis_screen` pattern would be mechanically identical to the existing convergence patterns ([infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156), [cardiovascularStress](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L187-L208)) but the threshold logic is composite-score, not signal-count: NEWS2 ≥5 escalates to `URGENT` (the NHS Sepsis Six trigger), qSOFA ≥2 escalates to `URGENT` (the Surviving Sepsis Campaign 2021 pre-hospital trigger). The literature anchors are tight:

- Seymour CW et al. (2016) — *JAMA* — Assessment of Clinical Criteria for Sepsis (qSOFA derivation cohort)
- Singer M et al. (2016) — *JAMA* — The Third International Consensus Definitions for Sepsis (Sepsis-3)
- Evans L et al. (2021) — *Crit Care Med* — Surviving Sepsis Campaign: International Guidelines
- Henry KE et al. (2019) — *Nat Med* — Targeted real-time early warning score (TREWS) for septic shock
- Kollef MH et al. (2019) — *Crit Care Med* — Modern early warning scores in septic patients

The population this pattern matters for: post-surgical owners, immunocompromised owners (chemotherapy, transplant, biologic-DMARD), chronically catheterised owners, residents of nursing facilities, owners with chronic indwelling devices (CVC, PICC, peritoneal dialysis catheter). EM physicians routinely see these patients arrive in septic shock with a 24–48 h window of pre-presentation deterioration that the wearable substrate could have caught. From a Surviving Sepsis Campaign standpoint, every hour of antibiotic delay in septic shock is associated with measurable mortality increase (Kumar 2006 — *Crit Care Med*).

**Recommendation:** ship a `sepsis_screen` pattern that computes NEWS2 from the available inputs on a 6-hour rolling window, escalates `URGENT` at ≥5, and is *suppressible* via PhysiologyState gating for owners with a chronic-illness flag that produces a permanently-elevated baseline NEWS2 (e.g. a stable COPD owner on home O2 will score ≥2 for SpO2 alone). The manifesto-aligned framing is the same as the existing `infectionOnset` pattern — data observation plus professional-referral language, never a diagnosis claim.

### 2.3 No opioid-overdose / acute respiratory-depression pattern

The acute respiratory-depression signature is canonical: opioid → respiratory rate falls from baseline (typically 14–18) to <8, then to apnoea; SpO2 lags by 2–5 minutes (the oxygen reservoir of the lungs and pulmonary capillaries buffers the gas exchange). For the opioid-overdose epidemic specifically (~80,000 US deaths/year in the 2020–2024 window, CDC NVSS), the time window from bradypnoea to irreversible anoxic injury is ~5 minutes.

Wearable detection has been demonstrated:

- Nandakumar R et al. (2019) — *Sci Transl Med* — Opioid overdose detection via smartphone sonar (the University of Washington SAFE study).
- Mathur A et al. (2024) — wrist-PPG respiratory rate + accelerometer-derived posture as a multi-signal classifier.
- Schwartz JD et al. (2022) — *Drug Alcohol Depend* — wearable PPG-derived RR as a naloxone-actuation surrogate.

Bios has [RESPIRATORY_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L52) and [BLOOD_OXYGEN](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L49). An `acute_respiratory_depression` pattern over RR <8 sustained ≥2 minutes + SpO2 trajectory + sleep-context (more specific during awake periods, but apnoea-of-overdose is detectable even during sleep) is buildable. It belongs in [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) at `severityFloor = URGENT`.

The manifesto-aligned framing matters here. This is *not* a "you should not use opioids" pattern. Owners on prescribed long-term opioid therapy (chronic pain, palliative care, post-surgical), owners in MAT (buprenorphine/methadone), owners with substance-use disorder who explicitly do not want a treatment-coercion overlay — all are within the manifesto's owner-protection framing. The pattern is a *naloxone-prompt* pattern: "respiratory rate has been below 8 for several minutes. If you are with someone who has used opioids, naloxone may be indicated." It is the same shape as the hypertensive-crisis pattern — a data observation paired with a regional-protocol referral.

The cardiology audit did not flag this because PE / MI dominates the cardiology respiratory-depression differential. The EM lens prioritises it because the overdose population is the one Bios's no-paywall / no-Play-Services / "free to all" architecture was implicitly designed to reach.

### 2.4 Anaphylaxis: the window is 5 minutes, the engine is 12 hours

Every pattern in [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) uses windows of ≥12 hours (most are 24–168 h). [AnomalyDetector.fetchRecentValues](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L427-L434) is hard-coded around this assumption. Anaphylaxis is a 5-minute multi-system event: sudden tachycardia (HR rising 30+ bpm above baseline within minutes), SpO2 dropping if bronchospasm is present, BP dropping in the distributive-shock subtype, often a sense of impending doom that precedes the objective vitals.

The wearable substrate is sufficient for detection in the cuff-equipped subpopulation. The pattern shape is *not* convergence-over-hours; it is *rate-of-change-over-minutes*. The detection engine would need a separate fast-loop alongside the existing slow-pattern engine — a `vitalsAccelerationDetector` that runs every 60 s over the last 5 min of HR + SpO2 + (when available) BP.

From an EM standpoint anaphylaxis is one of the few diagnoses where wearable detection could plausibly change pre-hospital outcomes. The autoinjector-prompt at the right moment is the difference between an outpatient observation and intubation. The pattern text would be the standard "Use your epinephrine autoinjector if you have one, then call emergency services" — the same script every EM physician gives every food/insect/drug-allergy patient at discharge.

**Recommendation:** add a `vitalsAccelerationDetector` engine alongside [AnomalyDetector](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt). Initial pattern: `anaphylaxis_pattern` — HR rising >30 bpm/5 min above the rolling-24h baseline + SpO2 drop ≥3 % from baseline + (if BP available within window) MAP drop ≥15. Severity URGENT. Gated by an owner-set `KNOWN_ANAPHYLAXIS_RISK` PhysiologyState flag to manage false positives in the general population (exercise produces HR rises that match anaphylaxis kinetics).

### 2.5 Cardiac-arrest detection: Apple precedent, Bios silence

Apple Watch fall-detect, Pixel Watch crash-detect, and the Apple Watch Series 9+ "Loss of Pulse Detection" feature (CE-cleared 2024) are the consumer-grade precedents. Bios has the phone accelerometer ([PhoneSensorAdapter](../../android/app/src/main/java/com/bios/app/ingest/PhoneSensorAdapter.kt)), the Virgil `FALL_EVENT` companion stream, and (for owners with a paired wearable) the PPG signal — every input the cleared classifiers use. Bios does not run a convulsive-collapse classifier, does not run a pulse-loss classifier, and (per Gap #1) has nowhere to route the alert if it did.

The correct ecosystem split here is Virgil for fall + check-in-miss (already shipping per [CompanionConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt)) and Bios for the *physiologic* cardiac-arrest signature (sudden PPG signal loss + sustained heart-rate drop to undetectable + concurrent accelerometer immobility). The American Heart Association Lifesaver app and the FirstNet / PulsePoint dispatcher integration are the regional benchmarks for what should happen *after* detection. Bios has no integration with any of them, by design — the cloud-dependence of those services is incompatible with the manifesto. The fall-back is the Gap #1 escalation path: regional emergency number + designated contact.

### 2.6 Stroke: time-is-brain, wearable is the wrong instrument

Sudden hemiparesis, facial droop, dysarthria, visual loss — the FAST exam — is essentially undetectable from a wrist wearable. The neurology audit ([NEUROLOGY_POV.md §2.2](NEUROLOGY_POV.md)) flagged the missing FAST-checklist intake surface (bystander or owner-tap, captures the time-of-last-known-well, the symptom onset timer, the call-to-stroke-centre prompt). The EM perspective agrees: a FAST intake surface is the *only* clinically useful stroke pattern a wearable layer can ship. The detection itself belongs to the human in the room.

The high-leverage move is *upstream* — AFib screening, per the cardiology audit's §2.1 [CARDIOLOGY_POV.md](CARDIOLOGY_POV.md). The stroke we want to prevent is the embolic stroke whose AFib substrate the wearable caught months earlier. The neurology and cardiology audits converge on this; the EM audit endorses both.

What the EM lens adds: when a stroke *does* happen at home, the FAST intake surface should be combined with a "time-of-last-known-well" stopwatch and a regional emergency-services deep-link that bundles the owner's recent BP, INR (if [INR](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) is tracked), and current anticoagulant medication list. That bundle is what every stroke centre asks for in the first 60 seconds of the door-to-needle workflow.

### 2.7 Pulmonary embolism

Cardiology audit flagged the absent PE convergence pattern. From EM, PE is the high-impact missed diagnosis in two specific populations:

- The under-50 syncope-or-dyspnea cohort (Wells score ≥4 should drive D-dimer / CTPA; Wells 2000 / PERC-rule Kline 2008).
- The post-COVID hypercoagulability cohort (sustained tachycardia + episodic SpO2 drift weeks after acute infection).

Bios has the multi-signal substrate. The pattern would gate on sudden tachycardia (HR rise sustained >5 min at >1.5σ above baseline) + SpO2 trajectory (>2 % drop from baseline) + (if pleuritic chest pain were a logged symptom — see §3.7 below on owner-symptom logging) the convergence. The wearable cannot *diagnose* PE; the alert text is "this combination has multiple possible causes including PE, which is time-sensitive. Discuss with a healthcare provider promptly." That is the appropriate ceiling.

### 2.8 Hypoglycaemia: correct pattern, missing escalation

[EmergencyVitalPatterns.hypoglycemiaCritical](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L89-L111) is clinically correct: ADA Level 2 threshold (≤54 mg/dL / 3.0 mmol/L), 15–20 g fast-acting carb guidance, glucagon mention for the unable-to-swallow case. The gap is downstream: by the time an insulin-treated Type-1 owner is at 50 mg/dL with hypoglycaemia unawareness (autonomic neuropathy from long-standing T1DM), they may already be cognitively impaired. The pattern needs an escalation path *because the owner is the wrong target* — the alert text presumes the owner can read and respond.

This is structurally the same problem as Gap #1. The solution is the same: designated emergency contact + regional emergency-number deep-link + an acknowledgement-timeout worker. The hypoglycaemia population may be the highest-priority candidate population for the escalation feature to ship.

A separate sub-recommendation: ship a `severe_hypoglycemia_with_hypoglycemia_unawareness` PhysiologyState modifier that lowers the escalation threshold to 70 mg/dL for owners who flag themselves as hypoglycaemia-unaware. The ADA Standards of Medical Care explicitly identify this subpopulation; the threshold for action is different.

### 2.9 Hypotensive shock

The four shock states converge on SBP <90 / MAP <65 with compensatory tachycardia (until decompensation, when the tachycardia fails). Bios has BP and HR. The only BP-anchored URGENT pattern is the hypertensive-crisis upper bound at [HypertensionPatterns.hypertensiveUrgency](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt#L118-L146). The single-reading-cutoff URGENT pattern for low BP is missing.

**Recommendation:** add a `hypotensive_shock_screen` URGENT pattern in [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) — SBP ≤90 single reading (with white-coat-equivalent re-check protocol of "rest 5 min, re-check on opposite arm; if confirmed and accompanied by dizziness, syncope, cold extremities, or altered mentation, seek immediate medical attention"). The cardiology audit's POTS / orthostatic-intolerance recommendation ([CARDIOLOGY_POV.md §2.5](CARDIOLOGY_POV.md)) is the orthostatic-specific variant; this is the catch-all shock-screen variant.

### 2.10 DKA / HHS

For CGM-equipped Type-1 owners — particularly the paediatric subset — DKA is a recurrent ED admission diagnosis. The wearable-detectable signature: sustained glucose >250–400 mg/dL (varies; 400 is conservative) + rising RR (Kussmaul respirations, often 24–32) + (in moderate-severe presentations) altered mentation. Bios has CGM via the Dexcom adapter and RR; the pattern is buildable.

**Recommendation:** ship a `dka_signature` URGENT pattern gated on `KNOWN_DIABETES_INSULIN_DEPENDENT` PhysiologyState. Without that gating it would over-call (non-diabetic hyperglycaemia of stress / dexamethasone / dawn phenomenon), but for the gated population it would be one of the highest-yield acute patterns in the entire library. Cross-reference with the paediatrics audit when that ships.

### 2.11 Emergency contact / next-of-kin

Already discussed as Gap #1. The structural form: there is no entity, no DAO, no UI screen anywhere in [/home/mia/Bios/android/app/src/main/java/com/bios/app/model/](../../android/app/src/main/java/com/bios/app/model/) for a designated emergency contact. This is the precondition for several other URGENT-tier patterns to be clinically actionable.

### 2.12 Trauma kinematics

Virgil's `FALL_EVENT` covers the most common at-home trauma vector (falls). Vehicular crash-detect (Pixel Watch precedent) is not wired into Bios's phone-sensor adapter. The cross-walk between trauma kinematics and physiologic instability — the ACS-COT Field Triage Decision Scheme (Cooper 2018: SBP <90, HR >120, GCS ≤13, RR <10 or >29 — physiologic criteria; high-energy mechanism, fall >20 ft, etc. — mechanism criteria) — is what a paramedic uses to choose the trauma centre destination.

The minimum viable EM-aligned addition: a `post_trauma_monitoring` pattern that fires *after* a logged `FALL_EVENT` or owner-entered trauma flag, and watches for the physiologic criteria over the next 24–48 h. Delayed splenic rupture, intracranial bleed in the anticoagulated owner, compartment syndrome (already flagged in [AFRICAN_TRADITIONAL_POV.md](AFRICAN_TRADITIONAL_POV.md) and the various bonesetting-tradition audits) — all are post-trauma evolutions Bios's data feed could catch.

---

## 3. Lower-priority observations and EM-adjacent surfaces

### 3.1 Heat illness

Heat exhaustion / heatstroke is the climate-change-relevance EM topic. Core temperature is the canonical metric; wrist skin temperature ([SKIN_TEMPERATURE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L68)) is an imperfect proxy but trends meaningfully in sustained heat exposure. A `heat_illness_screen` pattern over sustained skin-temp elevation + tachycardia + (when available) ambient-temperature context (Bios has [BLE_PERIPHERAL](../../android/app/src/main/java/com/bios/app/ingest/BleAirQualityAdapter.kt) for ESS-class environmental sensors) would surface the high-risk window — particularly relevant for outdoor labourers, elderly owners without air conditioning, and athletes during summer training. Tier B.

### 3.2 Cold illness

Symmetric to §3.1. Hypothermia <35 °C; the wearable-detectable signature is sustained skin-temp drop + bradycardia (paradoxical) + activity drop. Cross-reference [SOWA_RIGPA_POV.md](SOWA_RIGPA_POV.md) and [INDIGENOUS_AMERICAS_POV.md](INDIGENOUS_AMERICAS_POV.md) — both flag the same gap from their cold-climate-medicine angles. Tier C.

### 3.3 Suicide ideation acute

The psychiatry audit ([PSYCHIATRY_POV.md](PSYCHIATRY_POV.md)) covers the longitudinal pattern detection. The EM-specific add: the 988 lifeline (US), Samaritans (UK), Lifeline (AU), and equivalents are owner-facing, opt-in, immediate-access resources. The manifesto's "owner is final" principle constrains how Bios can act here — *unsolicited* crisis-intervention push violates the manifesto. A *pull-side* surface ("if you're in crisis, here are the resources for your region") aligned with the existing region-config infrastructure is the manifesto-compatible answer. Tier B.

### 3.4 Mass-casualty / disaster medicine

**This is the EM-and-critical-care strength to call out.** In a mass-casualty event (natural disaster, mass-shooting, infrastructure attack), the standard consumer health platform fails in three specific ways: cellular network saturation, Google Play Services dependency requiring authentication, and cloud-dependent inference. Bios's architecture sidesteps all three:

- Offline-first inference (on-device pattern detection, no cloud round-trip required).
- No Google Play Services dependency (functions on degoogled ROMs, functions when Play Services are unreachable).
- IPFS + Tor routing on LETHE (mesh-network-tolerant for the small subset of owners on LETHE).

For a disaster-medicine physician, this is *exactly* the architecture the field has been asking commercial platforms to adopt for a decade. Bios should not retreat from this position. The roadmap correctly preserves it. The EM lens's contribution: this is a *clinical* strength, not just a privacy strength, and it should be named as such in any positioning material.

### 3.5 Wilderness medicine

Altitude (cross-reference [SOWA_RIGPA_POV.md](SOWA_RIGPA_POV.md)), envenomation, marine, diving — the wilderness-medicine subspecialty operates in environments where cloud connectivity is absent and battery management is a constraint. Bios's offline-first architecture is a fit. Specific tactical addition: an `altitude_acclimatisation_monitor` pattern over SpO2 trajectory at elevation, which would catch the early HAPE / HACE signature before it becomes acute. The Sowa Rigpa audit's altitude framing is the right starting point. Tier C.

### 3.6 Post-ED discharge monitoring

This is the **biggest EM-side opportunity** in the audit. Every ED has a discharge population in the "low-acuity, ruled-out-for-the-serious-thing, sent home with instructions" bucket: chest-pain-ruled-out (HEART score 0–3, troponin negative, sent home with cardiology follow-up); mild cellulitis; community-acquired pneumonia treated outpatient; ankle sprain; concussion. The 72-hour re-presentation rate is a CMS-tracked quality metric (Hospital Outpatient Quality Reporting Programme), and a meaningful fraction of those re-presentations would be preventable with passive vital-sign monitoring.

Bios's URGENT-tier patterns + the proposed sepsis-screen + the existing COPD / asthma exacerbation patterns are *exactly* the substrate a post-ED discharge programme would want. The missing piece is a **discharge-window PhysiologyState flag** that:

1. Lowers thresholds for the specific diagnosis discharged (e.g. a "ruled out for ACS" patient gets a tighter cardiovascular-stress threshold for 72 hours).
2. Surfaces a brief owner-facing "how are you feeling" check-in at 24 / 48 / 72 h post-discharge.
3. Generates a discharge-window FHIR bundle the owner can share back with the ED or primary-care follow-up.

This is the surface that would let an EM physician say "I want my discharge patients to install this." It is also explicitly within the manifesto envelope: the owner sets the flag, the owner removes it, nothing is pushed to the ED or anyone else by default. Tier B but conceptually high-priority.

### 3.7 Post-ICU syndrome (PICS) recovery

Post-intensive-care syndrome — physical, cognitive, and mental decline after critical illness — affects a substantial fraction of ICU survivors and is poorly tracked outside research cohorts (Needham 2012; Ohtake 2018 — *Crit Care Med*). The Bios substrate maps onto PICS recovery surprisingly well:

- Physical: activity minutes, VO2 max trajectory, gait stability (if phone-sensor gait inference ships).
- Cognitive: typing cadence (W2F), reaction time ([REACTION_TIME_MS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) is reserved).
- Mental: sleep regularity, HRV, mood-drift score (W2F).

A `post_icu_recovery_monitor` PhysiologyState that lights up a recovery-trajectory dashboard for the 6–12 months post-discharge would be a distinctive add. Cross-references the psychiatry, neurology, and pulmonology audits. Tier C.

### 3.8 EMS / paramedic interoperability

The fully-bidirectional dream: an arriving paramedic taps an NFC reader against the owner's phone, gets the last 72 h of vitals + active medications + allergies + emergency contact. The realistic near-term: the FHIR R4 bundle the doctor-in-the-loop flow already produces ([ProfessionalReview](../../android/app/src/main/java/com/bios/app/model/ProfessionalReview.kt)) is the same artefact an EMS receiver could parse. The standardisation work has been done (NEMSIS 3.5 maps to FHIR Observation). Bios does not need to build the EMS-side; it needs to ensure the bundle it already exports is NEMSIS-compatible. Tier C, but worth a single review pass to confirm compatibility.

### 3.9 Telemedicine triage

Wearable-augmented urgent-care telemedicine is an emerging clinical space (Teladoc, Amwell, MDLIVE, plus the regional public-system variants — NHS 111 online + wearable feed, etc.). Bios's FHIR export is the substrate; the integration surfaces are vendor-specific and not Bios's problem to build. Tier C, flag only.

### 3.10 Honesty about the wearable ceiling

Charcot's triad (RUQ pain + fever + jaundice for ascending cholangitis), Murphy's sign for cholecystitis, Cullen's / Grey-Turner's signs for pancreatitis, Battle's sign for basilar skull fracture, the McBurney's-point tenderness of appendicitis — these are physical-exam findings. Bios cannot replicate them and should not pretend to. The alert text for any abdominal / surgical-emergency-adjacent pattern should be explicit: "this pattern has many causes; physical examination by a clinician is what distinguishes them." Most of the existing patterns already are; this is a discipline to preserve, not a gap.

### 3.11 Healthcare access framing

The uninsured / underinsured / low-resource-setting owner is the population for whom the manifesto's "free to all" / "no Play Services" / "owner controls everything" framing matters most. From an EM standpoint: this is the population that *avoids the ED for cost reasons* until the condition has decompensated past the point of outpatient management. A working URGENT-tier pathway with regional emergency-number deep-link and the sepsis-screen pattern would materially shift outcomes for this population, *because* they delay presentation. The post-ED-discharge monitoring framing in §3.6 also lands disproportionately here — discharge from a high-deductible ED visit is the worst possible moment for the owner to lose monitoring. This is not a code change; it is a positioning observation the EM lens should make explicit.

---

## 4. Manifesto / clinical-ethics tension points

### 4.1 "Owner is final" vs. cognitive incapacitation

The owner being final presumes the owner is competent to evaluate. Hypoglycaemia at 40 mg/dL, opioid overdose, post-arrest, post-seizure, severe sepsis — the owner is *not* competent in these moments. The manifesto-compatible answer is *prospective consent*: the owner, while competent, sets the escalation policy (designated contact, threshold for emergency-services notification, opt-in per tier). The policy is owner-set, owner-revocable, owner-erasable. The escalation in the acute moment is not a violation of autonomy; it is the *expression* of autonomy made earlier.

This is the framing every advance-directive document uses. EM physicians manage advance directives daily; the legal and ethical framework is well-established. Bios's URGENT-tier escalation belongs in that framework.

### 4.2 "Silence is a feature" vs. acute deterioration

Silence is correct for trend-based notices. Silence is *incorrect* for sustained RR <8 in an opioid-using owner. The primary-care audit already made this argument at §3.2 of [MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md). The EM lens reinforces it: a wearable that watches a patient die silently is not a feature, it is a failure mode. The URGENT-tier escalation in §2.1 + the acute patterns in §2.2–§2.10 + the disconnect-notification carve-out already in [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) are how Bios already distinguishes silence-from-feature and silence-from-failure. The architectural language is correct; the implementation needs the URGENT-tier escalation path Gap #1 / §2.1 describes.

### 4.3 No Play Services + no cloud + ACLS / emergency-services dispatch

The standard consumer health platform integration with emergency services (Apple SOS, Pixel emergency SOS, the various nation-state public-safety apps) all depend on cloud infrastructure that Bios refuses to depend on, by design. The EM lens accepts this as a tradeoff: Bios cannot dispatch an ambulance directly. What Bios *can* do is deep-link a `tel:` URI to the regional emergency number, which is universal across Android and requires no Play Services / cloud infrastructure. That is the manifesto-aligned ceiling for emergency-services integration, and it is sufficient for the URGENT-tier patterns to be clinically actionable.

---

## 5. What I would recommend, prioritised

**Tier A — clinical safety, ship before any new pattern**

1. **Add `emergencyNumber` to [RegionConfig](../../android/app/src/main/java/com/bios/app/config/RegionConfig.kt) per locale; surface a `tel:` deep-link `PendingIntent` in [AlertManager.sendNotification](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt#L48-L91) for `URGENT` alerts.** §2.1. One-day change.
2. **Add an `EmergencyContact` entity + URGENT-acknowledgement-timeout worker.** §2.1. Multi-day change but architecturally bounded. This is the single most clinically impactful EM-side recommendation.
3. **Ship a `sepsis_screen` pattern computing NEWS2 from the existing inputs, escalating URGENT at ≥5.** §2.2. The Surviving Sepsis Campaign + NEWS2 literature anchors are tight; the substrate is fully present.
4. **Ship a `hypotensive_shock_screen` URGENT pattern (SBP ≤90 single reading with re-check protocol).** §2.9. Mechanically identical to the hypertensive-crisis pattern, inverted.
5. **Add a `vitalsAccelerationDetector` engine + `anaphylaxis_pattern` over 5-minute windows.** §2.4. New engine class but small.

**Tier B — high-yield acute patterns, next quarter**

6. **`acute_respiratory_depression` URGENT pattern over RR <8 + SpO2 trajectory.** §2.3. Population is the opioid-overdose epidemic; manifesto fit is strong.
7. **`dka_signature` URGENT pattern gated on `KNOWN_DIABETES_INSULIN_DEPENDENT` PhysiologyState.** §2.10. Paediatric subpopulation particularly relevant.
8. **`post_trauma_monitoring` pattern triggered by `FALL_EVENT` + owner-entered trauma flag.** §2.12. Post-event physiologic surveillance.
9. **Discharge-window PhysiologyState flag + 24/48/72-h check-in surface.** §3.6. The EM "I want my discharge patients to install this" surface. Generate a discharge-window FHIR bundle for the post-ED follow-up.
10. **Pull-side crisis-resources surface keyed to region (988, Samaritans, Lifeline, etc.).** §3.3. Manifesto-compatible — surfaced when the owner asks, never pushed.
11. **`heat_illness_screen` pattern over sustained skin-temp + tachycardia (+ optional environmental sensor).** §3.1. Climate-change-relevance.

**Tier C — longitudinal and ecosystem**

12. **`post_icu_recovery_monitor` PhysiologyState + 6–12 month recovery-trajectory dashboard.** §3.7. Cross-references neurology, psychiatry, pulmonology audits.
13. **`altitude_acclimatisation_monitor` pattern over SpO2 trajectory at elevation.** §3.5. Cross-references [SOWA_RIGPA_POV.md](SOWA_RIGPA_POV.md).
14. **`cold_illness_screen` pattern.** §3.2. Cross-references [INDIGENOUS_AMERICAS_POV.md](INDIGENOUS_AMERICAS_POV.md), [SOWA_RIGPA_POV.md](SOWA_RIGPA_POV.md).
15. **NEMSIS-compatibility audit pass on the existing FHIR export.** §3.8. Single review pass, no new code.
16. **PE-aware convergence pattern (sudden tachycardia + SpO2 drop + symptom-log integration).** §2.7. Depends on a structured owner-symptom logging surface that does not yet exist.

**Do not adopt**

- A "triage score" or "ED-acuity predictor" composite presented to the owner. The ESI / CTAS / MTS triage scales are clinician-applied for a reason; an owner-facing "your ESI is 3" presentation would be either misleading (the scale was not validated for self-application) or paternalistic (the same failure mode the manifesto's content policy already prohibits).
- A `physical_exam_simulator` (Murphy's-sign / Charcot's-triad / etc. surrogate). Bios cannot do physical exam; pretending otherwise would erode the credibility the rest of the platform has earned.
- Direct emergency-services dispatch via a cloud service. The `tel:` deep-link to the regional emergency number is the manifesto-compatible ceiling. Cloud-mediated dispatch would re-introduce the dependency the architecture refuses.
- A "treatment recommendation" surface for the acute patterns (e.g. "give 1 mg epinephrine IM"). The autoinjector-prompt language for anaphylaxis is appropriate (the device is owner-administered); the dose / route language for medications the owner does not possess is not. Stay on the right side of the instrument / coach line.

---

## 6. Summary line for the project

> Bios is the most clinically literate privacy-preserving telemonitoring layer I have audited for the population that ends up in my emergency department. The URGENT tier is reachable, the hypertensive-crisis pattern reads like ED triage training, the medication-annotation surface is rare in this category, and the offline-first / no-Play-Services architecture is a genuine disaster-medicine strength. To close the EM lens specifically: (a) the URGENT pathway must have a destination — regional emergency number deep-link and a designated-emergency-contact surface with an acknowledgement timeout, (b) NEWS2-anchored sepsis screening and a hypotensive-shock pattern from the existing substrate, (c) acute-window patterns for anaphylaxis, opioid respiratory depression, and DKA that the current 12-hour engine cannot reach, and (d) a discharge-window PhysiologyState that turns Bios into the post-ED monitoring substrate every emergency physician quietly wishes their low-acuity discharges already had. None of these violate the manifesto; the prospective-consent framing for URGENT escalation is the same framework EM physicians use for advance directives every day.
