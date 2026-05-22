# Surgical Audit — Bios as Pre-operative Optimisation and Post-operative Monitoring Substrate

**Scope:** Bios's clinical reach as a peri-operative monitoring instrument, evaluated by a composite Western surgical panel — general surgery, orthopaedic surgery, vascular surgery, plastic and reconstructive surgery, trauma surgery, bariatric surgery. Section 1.3 of [MEDICAL_SPECIALTIES_WORLDWIDE.md](MEDICAL_SPECIALTIES_WORLDWIDE.md) anchors the surgical specialty taxonomy.
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Surgical specialties — pre-operative optimisation (prehabilitation), post-operative remote monitoring, chronic post-surgical state. Not a regulatory or 510(k) audit; not an OR-side decision-support audit (OR-side belongs to anaesthesia / institutional monitoring stacks).
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt). Skim: [MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md), [CARDIOLOGY_POV.md](CARDIOLOGY_POV.md).

---

## Executive summary

Surgery is **event-driven medicine.** The operation is the inflection point; the months before and the weeks after are where ambulatory monitoring earns its keep. The OR itself is owned by anaesthesia and the institutional patient monitor — Bios does not belong there and the panel is not asking it to. What the panel *is* asking is whether the same passive-monitoring substrate that already detects pre-symptomatic infection, autonomic stress, and metabolic drift can be turned, with modest additional scope, into a credible **prehabilitation** layer (the 4-12 week elective-surgery window) and a credible **post-operative remote monitoring** layer (POD0-POD30, where surgical site infection, venous thromboembolism, anastomotic leak, and post-operative ileus produce signal weeks before the patient self-presents).

Bios is unusually well-placed for both. The personal-baseline architecture, the convergence-required pattern engine ([ConditionPatterns.kt:127-156](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156)'s infection-onset pattern is essentially a generic SSI surveillance kernel), the reachable URGENT tier ([EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt)), and the FHIR-import path for biomarkers already give Bios most of what the ERAS Society and ACS NSQIP literature ask remote monitoring to do. The gaps are about **surgical context** — Bios has no concept of "an operation happened" and therefore cannot pivot its evaluation window, threshold direction, or pattern selection around the event.

The panel surfaces ten gaps, ordered by surgical impact:

1. **No "peri-operative state" concept.** Bios has [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) for pregnancy, postpartum, athlete, frailty, paediatric — every surgical context is missing. There is no `PREHAB_WINDOW`, no `POD_0_30`, no `POD_30_90`, no way to tell Bios "I had a sigmoidectomy on date X" and have the pattern engine respond. Every other gap on this list is, in part, a consequence of this one.

2. **No surgical site infection (SSI) surveillance pattern.** The infection-onset pattern at [ConditionPatterns.kt:127](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127) is close in spirit but generic — it baselines a stable adult, not a post-operative one whose RHR is *expected* to be 10-20 bpm above baseline for 2-4 weeks, whose HRV is *expected* to be depressed, and whose skin temperature is *expected* to run elevated through POD3-5. Applied naively in the post-op window, the existing pattern will either false-fire constantly (every post-op patient) or, if dialed down, miss the genuine secondary infection at POD7-14. NHSN 30-day SSI surveillance is the canonical framework the pattern should mimic.

3. **No anastomotic leak pattern.** POD3-7 fever + tachycardia + post-operative ileus is one of the highest-stakes patterns in bowel surgery; a 12-hour earlier detection meaningfully changes mortality. Bios has every input (RHR, skin temperature, activity drop as ileus proxy via reduced steps) but no pattern that fuses them on a post-op timeline.

4. **No venous thromboembolism (VTE) pattern.** Wells / Caprini risk stratification, post-operative immobility (the dominant risk factor), tachycardia, and SpO2 desaturation make this a strong wearable target. The cardiorespiratory-deconditioning pattern at [ConditionPatterns.kt:274](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L274) is wrong-shape — it operates on a 336-hour window and expects fitness loss, not the acute embolic event that presents as sudden persistent tachycardia and unexpected SpO2 drop in an otherwise-recovering patient.

5. **No prehabilitation surface.** The ERAS Society's pre-operative recommendations for elective surgery (4-12 weeks of aerobic conditioning, smoking and alcohol abstinence, nutritional optimisation, glycaemic control in diabetics, anaemia correction) are *exactly* what Bios already monitors. There is no pull-side "operation in N weeks, here is what your prehab trajectory looks like" surface. VO2 max trend, RHR trend, sleep efficiency, body composition, HbA1c, hemoglobin, ferritin — every input is on the bus. Nothing assembles them around a planned event.

6. **No frailty index surface.** Fried's five criteria (gait speed, grip strength, unintentional weight loss, exhaustion / self-reported fatigue, low physical activity) define the most-cited pre-operative risk stratifier in elective surgery for the over-65 cohort. Bios has weight ([BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L121)), activity ([STEPS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L104), [ACTIVE_MINUTES](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L106)), and HRV-derived recovery signals; it lacks grip strength and gait speed, both of which a phone IMU can plausibly proxy (the Virgil safety adapter already does fall-related accelerometry). A composite Fried surrogate, computed and surfaced only when the owner requests it, would be a meaningful pre-operative clearance instrument.

7. **No medication-adherence path for VTE prophylaxis or post-operative pain regimen.** [MEDICATION_INTAKE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L242) is reserved as a generic dose-carrying metric, but the medication-annotation surface that the primary-care audit (§2.5) called out is also load-bearing here: LMWH / DOAC course for VTE prophylaxis, opioid tapering trajectory, antibiotic course completion. Without a substrate for "did the prescribed perioperative medication get taken," the post-op patterns lose context they need.

8. **No respiratory-rate surveillance for opioid-related post-operative respiratory depression.** Pain management post-op increasingly leans on opioid stewardship; the most actionable wearable signal for opioid-induced respiratory depression is sustained low respiratory rate. Bios has [RESPIRATORY_RATE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L52) ingested and a respiratory-infection pattern that uses it in the *elevated* direction. The mirror pattern — *suppressed* respiratory rate with overlapping opioid intake annotation — is absent, and would be a defensible URGENT-tier addition for the post-op window.

9. **No flap / distal-perfusion monitoring scaffold.** Free-flap microvascular surgery and digital reimplantation lean heavily on post-operative perfusion checks — clinical hourly assessment is still standard, but ring PPG and skin-temperature wearables placed distally have growing literature support. Bios has no concept of "compare distal limb temperature/PPG against contralateral baseline." This is plastic surgery's most surgery-specific wearable opportunity and worth scoping even if it stays in research-companion territory.

10. **No patient-reported outcome (PRO) capture for functional recovery.** PROMIS, KOOS, WOMAC, EQ-5D, HOOS — orthopaedic and bariatric surgery in particular use validated PRO instruments at scheduled post-operative intervals (6 wk, 3 mo, 6 mo, 1 yr). Bios has owner-annotation capacity via the SELF_REPORTED [ReadingKind](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L40) but no structured PRO surface. A surgical patient's recovery trajectory is not fully described by passive sensor data; the owner's own scored answers are the second axis.

The remainder of this audit details these gaps, what Bios already does well that surgery should adopt as-is, and the recommended tiering.

---

## 1. What Bios already does well, viewed from the surgical bedside

| Quality | Evidence | Why it matters peri-operatively |
|---|---|---|
| **Convergence-required infection pattern** | [ConditionPatterns.kt:127-156](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) — RHR↑ + HRV↓ + skin-temp↑ + RR↑ with `minActiveSignals = 3` | This is essentially the NHSN SSI surveillance kernel waiting to be re-targeted at a post-op window. Mishra/Quer/Smarr citations are exactly the wearable-SSI-detection literature surgical groups are publishing into. |
| **Personal baseline as unit of comparison** | 14-day rolling per-metric baseline in [BaselineEngine](../../android/app/src/main/java/com/bios/app/engine/BaselineEngine.kt) | A pre-operative baseline established over the 4-12 week prehabilitation window is the right substrate against which to evaluate POD0-30 deviations. The architecture is already there; surgery just needs to *anchor* a baseline at the right time. |
| **URGENT tier reachable, with absolute cutoffs** | [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) — SpO2 ≤85 %, RHR ≥130, glucose ≤54, RHR ≤35 | Post-operative haemorrhage (tachycardia + hypotension) and post-operative hypoxia (PE, atelectasis, pneumonia) need exactly this tier. The pattern shape is already in place; what is missing is BP-anchored haemorrhage and the surgical context for differential. |
| **Biomarker hard-cutoff machinery + FHIR import** | [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [FhirImporter.kt](../../android/app/src/main/java/com/bios/app/export/FhirImporter.kt) | Pre-operative HbA1c (the surgical risk literature uses 7.0% as the elective-threshold inflection), hemoglobin (NSQIP transfusion risk threshold), albumin/pre-albumin (nutritional clearance), ferritin (iron deficiency anemia correction window) — all map onto the existing 16+ biomarker keys without architectural change. |
| **PhysiologyState gating** | [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) + `excludedStates` on patterns | The mechanism for "this pattern doesn't fire in this physiological context" is already shipped. Adding peri-operative states is a strict extension, not a redesign. |
| **Content policy bans push-side judgment** | [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) | Surgical patients are anxious. A wearable that tells a POD4 colectomy patient "you should walk more" would be clinically counterproductive. The "data first, owner evaluates" register is exactly right for the post-op window. |
| **Owner-controlled FHIR export** | [FhirExporter](../../android/app/src/main/java/com/bios/app/export/FhirExporter.kt) + the doctor-in-the-loop review surface | A surgical follow-up appointment is the canonical setting where a real Observation bundle (RHR trend POD0-14, temp trace, step count recovery curve) is worth more than a screenshot. Bios ships this. |
| **Fall and check-in events on the bus** | `FALL_EVENT` / `NEAR_MISS_FALL` / `CHECK_IN_MISS` in [MetricType.kt:246-248](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L246-L248) via the Virgil companion | Post-arthroplasty and post-spine-surgery patients have meaningful fall risk during the recovery window. The wiring exists; the surgical pattern that *consumes* fall events specifically as a post-op signal does not. |

These are not parity wins. The first two items in particular put Bios noticeably ahead of the commercial post-discharge monitoring SKUs the panel has reviewed.

---

## 2. Surgical gaps, ordered by impact

### 2.1 No peri-operative state — the missing root primitive

Every other gap in this audit is partly a consequence of this one. The pattern engine cannot reason about "post-operative day 4" because nothing in the schema records that an operation happened.

Recommendation: extend [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) with `PREHAB_WINDOW`, `POD_0_30`, `POD_30_90`, `POD_90_PLUS` — owner-set, with a single companion field for "procedure type" (free-text or SNOMED-coded) and "procedure date." Treat procedure type as a tag, not a clinical classification; the panel does not want Bios to claim it knows the difference between a sleeve gastrectomy and a Roux-en-Y on physiology alone. The owner tells Bios, and Bios uses that to:

- Anchor a *frozen* prehab baseline at the date the procedure is scheduled (snapshot of the 14-day baseline at that timestamp, stored as a `PerioperativeBaseline` row distinct from the rolling personal baseline).
- Switch which patterns are active during POD0-30 — generic infection-onset off, SSI surveillance on, deconditioning suppressed, prophylactic-VTE pattern on, etc.
- Compare POD30 / POD90 / POD180 readings against the pre-op snapshot, not the rolling window that the post-op recovery itself contaminated.

Manifesto-clean: owner sets the state explicitly, no inference, no nudges.

### 2.2 SSI surveillance pattern (post-operative infection)

The clinical reading: a post-operative patient's RHR runs 10-20 bpm above pre-op for 2-4 weeks; HRV is depressed; skin temperature is mildly elevated through POD3-5. The infection-onset pattern at [ConditionPatterns.kt:127-156](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) — calibrated against a stable adult — will fire constantly on POD2-7 if applied as-is. Either the surgical patient mutes Bios (worst outcome — they then miss the genuine secondary infection at POD10-14) or the pattern is suppressed during POD0-30 and the panel loses the most valuable surveillance window in the entire surgical recovery.

The NHSN 30-day SSI surveillance framework is what an `ssi_surveillance` pattern should mimic:

- **Baseline:** the frozen pre-op snapshot from §2.1, not the rolling window.
- **Expected post-op deviation curve:** Bios needs an *expected recovery trajectory* per metric. RHR returns to baseline over 2-4 weeks; skin temperature normalises by POD5-7; sleep efficiency improves over POD10-21. The pattern fires when a metric *re-elevates* after starting to normalise, or *fails to begin normalising* by the expected POD.
- **Trigger:** RHR re-elevation ≥1.5σ above the *expected POD-N value* + skin temp re-elevation + sleep fragmentation re-elevation, sustained ≥24h, in the POD3-30 window.
- **Tier:** `ADVISORY` with a strong "discuss with your surgical team — incisional erythema, drainage, fever ≥38.3°C, wound dehiscence are the symptoms to look for" suggested action. This is *not* URGENT; URGENT is sepsis, which has its own pattern (§2.6).

References the pattern should carry: CDC NHSN SSI definitions (2024 update); Sands 2025 wearable-detection-of-post-discharge-SSI literature; ACS NSQIP 30-day surveillance methodology.

### 2.3 Anastomotic leak pattern (bowel surgery)

POD3-7 is the classic anastomotic leak window for colorectal anastomoses. Mortality is high, and the 12-24 hour earlier detection that a wearable could plausibly provide is clinically meaningful.

Pattern shape:

- **Required:** sustained RHR ≥2σ above the *expected POD-N value* for ≥6h in the POD3-7 window.
- **Required:** skin-temperature deviation ≥1.0°C above the expected POD-N value, sustained ≥6h.
- **Supporting:** activity drop ≥1.5σ below the post-op recovery curve (proxy for post-operative ileus — patients with a leak stop ambulating before the pain becomes their reason for not ambulating).
- **Supporting (when CGM is paired):** post-prandial glucose response flattening (stress response, ileus, reduced absorption — multi-signal, low specificity, only useful as a corroborator).
- **Tier:** `ADVISORY` with explicit anastomotic-leak language in the suggested action and a strong "contact your surgical team today" call. Not URGENT because false positives in this window are common and the action is a phone call, not 911.

References: Daams et al. — anastomotic leak detection; den Dulk et al. — Dutch leak score; ERAS Society colorectal guideline (the post-op trajectory expectations).

### 2.4 VTE pattern (DVT / PE)

VTE risk is bimodal post-operatively: low-grade DVT can be silent until embolisation, at which point the presentation is acute. The wearable opportunity is the acute event, not the silent DVT.

Pattern shape (acute PE focus):

- **Required:** sudden RHR step-up ≥2σ over a ≤2h window, sustained ≥30 min, in the POD0-21 window — a discontinuity, not a trend.
- **Required:** SpO2 step-down ≥1.5σ below expected POD-N value, sustained ≥10 min.
- **Supporting:** respiratory rate elevation ≥1.5σ.
- **Supporting:** activity drop coincident with the cardiopulmonary deviation (patient sits down because they can't breathe).
- **Tier:** `URGENT` via `severityFloor` — PE is a 911-class differential.

Caprini score inputs (age, BMI, prior VTE, malignancy, hormonal therapy) feed a pull-side *risk-stratification* surface, not the push-side trigger. The trigger fires on the acute physiology; the risk score lets the pattern's suggested-action text escalate ("you are in a high VTE-risk category and these signals match a PE pattern — call 911" vs. "these signals warrant prompt clinical evaluation").

### 2.5 Sepsis-surrogate pattern (qSOFA / SIRS)

Post-operative sepsis is the highest-mortality surgical complication and the URGENT-tier event that overrides everything else.

qSOFA in clinical use is RR ≥22, altered mentation, SBP ≤100. Bios has RR (continuous via wearable / phone PPG); has BP only when home cuff is paired; cannot directly measure mentation. SIRS criteria (HR >90, temp >38 or <36, RR >20, WBC abnormality) are partially wearable-derivable and partially require labs.

A `sepsis_surrogate` pattern:

- **Required:** RHR >100 sustained ≥30 min.
- **Required:** skin temperature ≥1.5σ above expected, or absolute ≥38°C.
- **Required:** respiratory rate ≥22 sustained ≥10 min.
- **Supporting:** SpO2 declining trend.
- **Supporting (when paired):** SBP ≤100 from home cuff or smartwatch BP.
- **Supporting (when paired):** WBC outside reference range from FHIR import.
- **Tier:** `URGENT` via `severityFloor`. Suggested action explicitly names emergency services.

The pattern should be active in POD0-30 (post-op sepsis), in immunocompromised owners, and in any owner who annotates a recent infection — but not as a perpetual everyone-everywhere trigger, because the false-positive rate in healthy adults is too high.

### 2.6 Prehabilitation surface (the ERAS-aligned pre-op window)

ERAS Society guidelines for colorectal, oesophagogastric, lung, urology, and orthopaedic surgery converge on the same prehabilitation pillars:

1. **Aerobic conditioning** — 4-8 weeks of moderate aerobic training; the surrogate for CPET (cardiopulmonary exercise testing) in non-research settings is wearable-derived VO2 max. [VO2_MAX](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L45) is in the schema; the trajectory surface is not.
2. **Nutritional optimisation** — albumin / pre-albumin / weight / muscle mass. Albumin is not yet in the biomarker set; weight and body composition are (Withings).
3. **Glycaemic control in diabetics** — HbA1c <7-8% target pre-elective; Bios has this and the prediabetes signature ([BiomarkerConditionPatterns.kt:80](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt#L80)).
4. **Anaemia correction** — hemoglobin and ferritin pre-op (NATA / IRON-MAN guidelines); both are in the schema.
5. **Smoking cessation** — 4-8 weeks abstinence improves wound healing and pulmonary outcomes. The Smokeless companion's `cessation_recovery_pattern` already maps to this. Surgical surface needs to consume the same signal in the prehab context.
6. **Alcohol abstinence** — 4-8 weeks improves cardiac function, immune competence, bleeding diathesis. [ALCOHOL_INTAKE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L237) is on the bus.

The recommendation is a *pull-side* "Prehab dashboard" that, when the owner enters `PREHAB_WINDOW` state and a target operation date, surfaces each pillar's trajectory against an evidence-based target. No nudging, no scores — the owner sees the data and the literature target ("ERAS Society recommends ≥4 weeks of moderate aerobic activity pre-operatively. Your active minutes have averaged 28/day over the last 14 days; ERAS-aligned guidance is ≥30 min/day"). The surgical team consumes the FHIR-exported version at the pre-op clinic visit.

### 2.7 Fried frailty surrogate (pre-op risk stratification for >65)

Fried's five criteria for the over-65 elective-surgery cohort:

| Fried criterion | Bios captures it? | Notes |
|---|---|---|
| Unintentional weight loss (≥4.5 kg or 5% in 1 year) | Yes — [BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L121) trajectory | Needs a 1-year trend surface; the 14-day baseline is too short. |
| Self-reported exhaustion | Partial — recovery score, HRV trend | Should be augmented with a PRO instrument (§2.10). |
| Weakness (grip strength, low-quartile) | No | Phone-IMU grip-pressure proxies exist in the literature but are imprecise; a Bluetooth-paired dynamometer (Camry / Jamar) would be the credible path. |
| Slow walking speed (4-metre walk, low-quartile) | Approximate — step cadence from accelerometer is a weak proxy | Dedicated 4-metre walk test as an active-test surface (similar to the reaction-time reservation in [MetricType.kt:255](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L255)) would close the gap. |
| Low physical activity | Yes — [ACTIVE_MINUTES](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L106) | Already on the bus. |

Bios captures 2-3 of the 5 well, 1-2 weakly. A composite "Fried surrogate" surface — pull-side, owner-initiated — would give the pre-op clinic a meaningful starting point for clearance discussions. Sarcopenia / cachexia detection (weight loss + activity drop + grip strength + albumin) sits on the same substrate and is a 90-day trajectory analysis on existing keys plus the grip-strength gap.

### 2.8 Wound-related surveillance (general / plastic / orthopaedic shared)

Localised skin temperature elevation is the literature-supported wearable signal for incisional infection and diabetic foot ulcer monitoring. Bios's [SKIN_TEMPERATURE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L68) is wrist-only — it cannot directly proxy an abdominal incision or a plantar ulcer. The honest panel position: this is a sensor gap, not a Bios architecture gap. Multi-site temperature monitoring is an open hardware question.

What Bios *can* do without new sensors:

- **Owner-attached wound photo** as a [LoggedEvent](../../android/app/src/main/java/com/bios/app/data/) attachment, never analysed, never uploaded, never inferred-upon — purely a recall surface for the owner to review against the previous photo and bring to clinic. Privacy framing matters: encrypted at rest, never transmitted, owner-controlled deletion.
- **Owner-rated wound score** as a PRO surface (§2.10) — Bates-Jensen or REEDA scales structured as a simple pull-side entry.

### 2.9 Distal-perfusion scaffold (plastic / reconstructive — free-flap monitoring)

The most surgery-specific wearable opportunity in the audit. Free-flap microvascular surgery (DIEP, ALT, fibula, radial forearm) and digital reimplantation use hourly clinical flap checks for the first 24-72h, then q4-8h. The wearable literature on PPG-based flap monitoring is early but credible.

Bios cannot ship this as a standalone product surface — it requires sensor placement protocols that belong in a hospital workflow. What it *can* do is reserve the schema: a per-anatomical-site temperature stream, a per-site PPG perfusion stream, and a `FLAP_PERFUSION_CONCERN` event-type that a future hospital-side companion can write. Schema reservation, not product commitment.

### 2.10 PRO capture (functional recovery scoring)

Orthopaedic surgery (KOOS, HOOS, WOMAC, Oxford Knee, Oxford Hip), bariatric surgery (BAROS, IWQOL-Lite), general surgery (EQ-5D, SF-36, PROMIS), spine surgery (ODI, NDI) — all use structured PRO instruments at scheduled post-op intervals. Recovery is not fully described by passive sensor data; the patient's own scored answers are the second axis.

Bios has self-report capacity ([ReadingKind.SELF_REPORTED](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L40)) but no structured PRO surface. Recommendation: a `PROInstrument` entity (instrument id, version, question set, scoring rubric, validated reference ranges) and a scheduled pull-side prompt ("the 6-week post-op KOOS questionnaire is due — 5 minutes — answer when you're ready"). The schedule is pull-side: the prompt is silent until the owner navigates to the post-op surface, consistent with "silence is a feature." Scores export via FHIR as `QuestionnaireResponse` resources.

This is the cleanest single addition that makes Bios useful to a surgical follow-up clinic.

---

## 3. Surgical-specialty cross-cuts

These are surgical specialty-specific notes; most map onto the gaps above plus a few specialty-specific details.

### 3.1 Orthopaedic surgery — arthroplasty and ACL recovery

The Mobility Center literature on passive wearable monitoring of ACL and total joint arthroplasty recovery is well-developed and well-cited. The signal is step count, gait cadence, weight-bearing tolerance, and pain-trajectory PROs. Bios has steps; gait cadence and asymmetry are derivable from the same accelerometer the phone-sensor adapter already taps; weight-bearing tolerance requires either a pressure-sensing insole (sensor gap) or PRO entry (the practical answer).

Specific recommendation: an `arthroplasty_recovery` *trajectory* surface (not a pattern) that compares the owner's POD0-90 step count and gait cadence against published recovery curves for the procedure tag. Pull-side, descriptive, never evaluative.

### 3.2 Bariatric surgery — RYGB / sleeve / DS / SADI-S

Strong fit. Bios captures weight ([BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L121)), body composition (Withings BIA), HbA1c, lipid panel, and CGM-derived metabolic signals. Long-term post-bariatric monitoring needs:

- **Weight trajectory against the procedure-specific expected curve** (sleeve loses ~25-30% TWL at 12 mo, RYGB ~30-35%, DS ~40%).
- **Nutritional deficiency surveillance** — B12, iron / ferritin, vitamin D, calcium, folate, magnesium. Every one is already in the [biomarker schema](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L146-L200). What is missing is a bariatric-specific surveillance cadence (B12 q6mo, iron q12mo, etc.) and a pull-side dashboard.
- **Dumping syndrome detection** — post-prandial tachycardia within 15-30 min of meals (early dumping) or hypoglycaemia 1-3h post-prandial (late dumping). With CGM + RHR + meal timing (which the owner annotates), the pattern is feasible: a `dumping_syndrome_pattern` that fires on a paired post-prandial RHR step-up + glucose nadir signature.

The panel notes bariatric surgery as the surgical specialty with the strongest *chronic* (rather than acute peri-operative) fit for Bios — and as the specialty where the existing biomarker machinery does most of the work.

### 3.3 Vascular surgery — claudication, CLI, AAA

- **Claudication tracking** — the 6-minute walk test is the canonical surrogate; phone-pedometer step counts during a structured walk are a credible at-home proxy. An active-test surface (`SIX_MIN_WALK_DISTANCE` as a new `MetricType` on `MetricDomain.ACTIVITY`) would be a clean addition.
- **Critical limb ischaemia (CLI) and wound healing** — overlaps with §2.8 and with the diabetic-endocrinology audit (cross-reference). Distal temperature monitoring as a sensor gap.
- **AAA surveillance** — blood-pressure control is a wearable target; aneurysm growth-rate monitoring is imaging-only and out of scope. The hypertension pattern from [HypertensionPatterns](../../android/app/src/main/java/com/bios/app/alerts/) already covers the BP-control dimension; nothing AAA-specific needs to ship.

### 3.4 Trauma surgery — fall and crash detection

Bios has fall events via the Virgil companion. Crash detection (Apple Watch, Pixel Watch) is wearable-native; ingestion via Health Connect would carry it to Bios. TBI follow-up belongs partly here and partly in neurology (cross-reference NEUROLOGY_POV.md). Polytrauma recovery is multi-system and benefits more from the prehab/post-op architecture in §2.1 than from a dedicated trauma pattern.

### 3.5 Plastic / reconstructive — flap and lymphedema

Flap monitoring scaffold per §2.9. Post-mastectomy lymphedema — limb circumference and bioimpedance spectroscopy are the standard measurement modalities and require dedicated hardware; Bios cannot directly capture them. Owner-entered limb circumference at scheduled intervals (PRO-shaped surface) is the realistic path.

### 3.6 General / bowel surgery — anastomotic leak per §2.3

The highest-stakes specialty-specific pattern in the audit. The panel ranks the anastomotic leak pattern as the single most valuable surgical addition Bios could ship.

---

## 4. Manifesto / clinical-ethics tension points

### 4.1 "Silence is a feature" vs. post-operative active surveillance

A post-op patient is in a different relationship with their wearable than a stable adult on a 14-day baseline. The implicit contract changes: the patient *is* asking Bios to watch them. The push-side rules need to recognise the peri-operative state as a higher-attention context. This is not a manifesto violation — it is the owner explicitly opting into closer surveillance via the `POD_0_30` state. Patterns active in POD_0_30 can carry a lower `minActiveSignals` threshold and a higher push frequency. The owner sets the state; Bios responds.

### 4.2 "Never evaluate the person" vs. surgical clearance scoring

The Fried frailty surrogate, the ASA-grade composite, the prehab dashboard — these all flirt with composite scoring. The panel-aligned answer is the same as the primary-care audit's: **pull-side only, never pushed.** The owner navigates to the prehab surface and sees their frailty surrogate components; Bios does not push a frailty score at them. The surgical clinic, when the FHIR bundle is shared, reads the components and synthesises the ASA grade — the synthesis belongs to the clinician, not the app.

### 4.3 Patient-facing language post-op

Surgical patients are anxious, often in pain, often on opioids that impair judgment. The "data first, then professional referral" register of [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) is well-suited; the new patterns (SSI, anastomotic leak, PE) must continue to honour it. An anastomotic-leak alert that reads "you might have a leak" would be both clinically wrong (overspecific) and emotionally harmful. The pattern text should describe the data ("your resting heart rate has been 18 bpm above your pre-op baseline since 6 hours ago, your skin temperature is 0.8°C elevated, your step count is down 60% from your typical POD4 trajectory") and refer to the surgical team. Bios is the instrument; the surgeon evaluates.

---

## 5. Recommendations, prioritised

### Tier A — surgical safety, ship before any specialty-specific scope

1. **`PerioperativeState` extension to [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt)** (§2.1) with `PREHAB_WINDOW`, `POD_0_30`, `POD_30_90`, `POD_90_PLUS`, plus a single procedure-date + procedure-tag annotation. This is the load-bearing primitive; everything else in this audit depends on it.
2. **Frozen pre-op `PerioperativeBaseline`** snapshot taken at procedure-scheduling time. Distinct entity from the rolling personal baseline. The 14-day baseline keeps rolling; the perioperative baseline does not.
3. **SSI surveillance pattern** (§2.2) anchored on the perioperative baseline, with NHSN-aligned references. Re-elevation-after-normalisation as the trigger shape.
4. **Anastomotic leak pattern** (§2.3) — POD3-7 window, RHR + skin temp + activity drop, ADVISORY tier with strong surgical-team referral.
5. **Acute PE pattern** (§2.4) — discontinuous RHR step + SpO2 step + RR elevation, URGENT tier.
6. **Sepsis-surrogate pattern** (§2.5) — qSOFA-shaped, URGENT tier, gated to peri-operative and immunocompromised contexts.

### Tier B — surgical completeness, next quarter

7. **Prehabilitation pull-side dashboard** (§2.6) — ERAS-aligned trajectories on existing keys; no new patterns required.
8. **Fried frailty surrogate** (§2.7) — composite over weight trajectory, activity, recovery; flag grip-strength + 4-min-walk as active-test scope.
9. **Opioid-aware respiratory-rate pattern** (§2.8 / item 8 in summary) — mirror of the respiratory-infection pattern, suppressed-RR direction, gated to POD0-30 with an opioid annotation present.
10. **PRO instrument surface** (§2.10) — `PROInstrument` entity, scheduled pull-side prompts, FHIR `QuestionnaireResponse` export.
11. **Bariatric long-term surveillance dashboard** (§3.2) — procedure-specific weight curve, nutritional deficiency cadence, dumping-syndrome pattern.

### Tier C — specialty-specific scope, when foundation is solid

12. **Arthroplasty / ACL recovery trajectory surface** (§3.1) — procedure-tag-specific recovery curves; pull-side descriptive only.
13. **6-minute walk test** as active-test (`SIX_MIN_WALK_DISTANCE`) for vascular claudication (§3.3).
14. **Flap / distal-perfusion schema reservation** (§2.9 / §3.5) — per-site temperature and PPG metric types, `FLAP_PERFUSION_CONCERN` event type. Reserved, no product surface yet.
15. **Owner-attached wound photo + REEDA / Bates-Jensen wound score** (§2.8) — encrypted-at-rest, never transmitted, owner-reviewed-only.
16. **Crash-detection ingestion** via Health Connect (§3.4) — pass-through, no new pattern needed.
17. **Limb-circumference and BIS schema reservation** for post-mastectomy lymphedema (§3.5).

### Do not adopt

- **OR-side monitoring integration.** The OR is anaesthesia and institutional patient-monitor territory. Bios's value is in the windows around the event, not in the event itself. Resist scope creep into intra-operative monitoring; it is a different regulatory class and a different product.
- **Procedure-specific anatomical inference.** Do not infer "this patient had a colectomy" from physiology. The owner tells Bios; Bios does not guess. This is the same manifesto guard that keeps Bios from inferring pregnancy from BBT — it applies identically to surgical context.
- **Push-side prehab nudging.** The temptation to send "you're 6 weeks from surgery — get out and walk" is exactly the engagement-driven judgment the manifesto rules out. The prehab dashboard is pull-side. The owner asks.
- **Composite surgical risk scores delivered as push.** ASA grade, Caprini score, Fried index — assembled on the pull-side, never pushed at the owner, never used to gate access to anything. The surgical clinic owns the synthesis.

---

## 6. Summary line for the project

> Bios is already most of what an ambulatory peri-operative monitoring substrate needs: convergence-required pattern detection, personal-baseline arithmetic, a reachable URGENT tier with hard cutoffs, FHIR-bidirectional lab integration, and a content policy that respects an anxious post-op patient. To become a credible prehabilitation and post-operative remote monitoring layer in the ERAS / NHSN SSI / ACS NSQIP register, Bios needs (a) a peri-operative `PhysiologyState` with a frozen pre-op baseline, (b) SSI-surveillance, anastomotic-leak, and acute-PE patterns shaped to the POD timeline, (c) a pull-side prehabilitation dashboard built over the existing biomarker and activity keys, (d) a Fried frailty surrogate for the over-65 elective-surgery cohort, and (e) a PRO-instrument capture surface for KOOS / WOMAC / EQ-5D / BAROS. None of these violate the manifesto; all of them are within the existing architecture, and the load-bearing addition — a peri-operative state and a frozen baseline — is one well-scoped extension to a primitive Bios already ships.
