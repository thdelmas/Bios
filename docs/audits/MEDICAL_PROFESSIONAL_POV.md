# Medical Professional Audit — Bios as Preventive-Medicine Instrument

**Scope:** Bios's clinical reach as a preventive-medicine tool, evaluated against the standards used by the health systems most cited for population-scale prevention (Singapore, Japan, Netherlands, Australia/Norway, South Korea).
**Date:** 2026-05-21
**Branch:** `fix/db-downgrade-fallback`
**Lens:** primary care + preventive medicine. Not a regulatory or 510(k) audit — that is a separate body of work.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt).

---

## Executive summary

Bios is **the strongest privacy-preserving preventive-monitoring layer I've audited in this size class.** The condition-pattern library is literature-anchored (24 of 33 signal rules carry primary citations: Mishra 2020, Quer 2021, Smarr 2020, Furman 2019, Ridker 2003, ADA 2024, NCEP ATP III, AACE/ATA 2012, WHO 2011, etc.), the unified data model maps cleanly onto FHIR Observation / DetectedIssue, and the alert-content policy is exemplary — the formal CI gate against "you should / you need to / your health score" is something I've literally never seen in a consumer health product. From a primary-care standpoint, Bios is a credible *upstream observation feed for the patient–physician conversation*. That is the right level of ambition.

The audit nevertheless surfaces eight gaps that matter from a preventive-medicine perspective. Ordered by clinical impact:

1. **The "URGENT" alert tier is unreachable in code.** [AnomalyDetector.classifySeverity](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L377-L389) caps at `ADVISORY`. Channel `bios_urgent` exists ([AlertManager.kt:33](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt#L33)) but nothing ever lands in it. There is no hard-cutoff escalation for SpO2 <88 %, sustained HR >130 at rest, an AFib pattern lasting hours, hypoglycemia <54 mg/dL, or a hypertensive-emergency BP reading — all of which a primary-care physician expects an instrument to surface differently from a 7-day-trend notice.
2. **No age-and-sex-stratified screening prompts.** The features that put Japan (Tokutei Kenshin), South Korea (national screening), and Singapore (3Ms framework) at the top of preventive-medicine rankings are precisely the *cadence-driven, age-banded* prompts that catch cancer and cardiovascular disease early. Bios has no model of "is the owner due for a mammogram, an FIT, a Pap/HPV, an AAA ultrasound, a DEXA, a fasting lipid panel, an HbA1c, an eye exam." A pull-side, owner-controlled "what does USPSTF / your region recommend for someone with my demographics" surface would close the single biggest gap between Bios and the systems it implicitly benchmarks against.
3. **Vaccinations / immunisation status is entirely absent.** No `IMMUNIZATION` domain, no FHIR `Immunization` import alongside `Observation`. From a primary-care POV, immunisation status is a co-equal axis with biomarkers; the Netherlands' "integrated preventive network" assumes a clinician can see both.
4. **Blood pressure is a second-class metric.** `BLOOD_PRESSURE_SYSTOLIC` / `DIASTOLIC` are in the schema and Withings is wired, but there is no condition pattern that gates on BP itself (only the fall-orthostatic pattern uses BP, and only as a corroborator). Hypertension is the single highest-impact modifiable preventive target by population-level QALY in essentially every developed-country guideline (NCEP, ESC, JNC-8, JSH for Japan). Missing: a `hypertension_emerging` pattern, a stage-2 cutoff escalation, a "discuss home BP confirmation" suggested-action.
5. **No medication / adherence surface.** Posology is *explicitly deferred to Phase 9* per the roadmap. From a primary-care POV that is the wrong order: a substantial fraction of the cardiovascular and metabolic patterns Bios flags will fire on owners *already on statins, antihypertensives, metformin, levothyroxine, or beta-blockers*, and not knowing the medication context produces false-elevated severity (a beta-blocker user's bradycardia is not hypothyroid). At least a write-only "current medications" annotation, even if not adherence-tracked, would materially de-noise the alert layer.
6. **Family history / personal risk profile is unmodeled.** ASCVD, FRAX, and Gail-model risk inputs include first-degree-relative history. Bios has no place to put it. The patterns therefore treat a 32-year-old with no family history identically to a 52-year-old with two first-degree CAD relatives. Even a free-text `RiskContext` row consumed by the explanation builder would help.
7. **Children, pregnancy, frailty: no demographic gating.** The 14-day rolling personal baseline is the right primitive for an adult under normal physiology; it is the *wrong* primitive for pregnancy (where HR/temp baselines drift by trimester), peri-/postpartum recovery, paediatrics (HR ranges are age-dependent), and frailty assessment in the >75 cohort. Every signal rule currently uses a single `thresholdSigma`. No `PregnancyState`, `AgeBand`, or `Frailty` modifier exists. Falls + check-in-miss in Virgil-companion territory are the most age-sensitive signals in the library and don't carry an age modifier either.
8. **Severity classifier is signal-count-based, not absolute-value-based, for vitals.** Wearable-only patterns use z-score arithmetic on personal baseline; biomarker patterns now have `absoluteAbove`/`absoluteBelow`. The same machinery is **not yet applied to vital-sign emergencies** — there is no rule that fires *because SpO2 = 86 %*, only because SpO2 is 1.5 σ below this individual's baseline. For a clinically conditioned reader at 98 % baseline, 1.5 σ down may still be 95 %. For a COPD patient at 92 % baseline, a 1.5 σ drop is hypoxic. Bios needs the same hard-cutoff path it built for labs, applied to vitals.

The remaining items in this audit are observations on lower-impact gaps and on strengths worth preserving as scope grows.

---

## 1. What Bios already does well, viewed clinically

| Quality | Evidence | Why it matters in primary care |
|---|---|---|
| **Literature-anchored signal rules** | 24 of 33 rules carry primary citations; biomarker thresholds match AHA/CDC, ADA, NCEP, AACE/ATA, WHO, Endocrine Society | Removes the "wellness app vibes" problem. A physician can audit the references list and judge each pattern on its evidence, exactly as they would a clinical decision-support tool |
| **Pattern requires convergence** | `minActiveSignals = 3` for infection onset; `required = true` on biomarker gate rules | Mirrors clinical reasoning. A single elevated RHR ≠ illness; RHR↑ + HRV↓ + skin-temp↑ over 24 h is exactly how the Mishra/Quer COVID-detection literature defines pre-symptomatic illness |
| **Personal baseline as the unit of comparison** | 14-day rolling per-metric baseline; z-score gate; biomarker patterns use absolute cutoffs while wearable patterns are relative | Closer to clinical reality than population-mean thresholds. An athlete's RHR of 48 is not bradycardia; a sedentary 70-year-old's RHR of 48 is. Bios captures that distinction natively |
| **Multi-signal cross-correlation** | `cardiovascular_stress`, `chronic_inflammation`, `recovery_deficit`, `metabolic_drift`, `mental_health_correlate`, the seven biomarker signatures | Reproduces the multi-system reasoning that distinguishes a primary-care assessment from a single-test result |
| **Alert content policy** | [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) is a CI-gated banlist of "you should / you need to / streak / level up" | Solves the *iatrogenic anxiety* problem of consumer health tools. The data-first framing ("RHR +2σ for 48 h") is exactly the register a physician would want a patient-facing instrument to use |
| **FHIR R4 bidirectional** | Export with LOINC for 12 mapped types; import for 16 biomarker keys | Makes Bios *clinically interoperable* on day one. A patient can hand a clinician a real Observation bundle, not a screenshot |
| **Regulatory disclaimer per region** | `RegionConfigProvider` carries `alertDisclaimer` per FDA/MHRA/EMA/Health Canada/TGA | Correct posture for a non-cleared instrument. Worth keeping prominent — the medical-device legal line is the one place "you should" *does* belong, in the form "you should consult a clinician" |
| **Separate reproductive DB** | Independent SQLCipher key, independent wipe, FHIR exporter skips `WOMENS_HEALTH` by default | Materially correct for a post-Dobbs threat model. Most consumer health apps treat cycle data as just another stream |

These are not parity wins — these are areas where Bios is meaningfully ahead of the consumer-wearable category.

---

## 2. Clinical gaps, ordered by impact

### 2.1 The `URGENT` tier is unreachable

[AnomalyDetector.classifySeverity](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L377-L389):

```kotlin
return when {
    combinedScore > 3.0 || signalRatio > 0.8 -> AlertTier.ADVISORY
    combinedScore > 2.0 || signalRatio > 0.5 -> AlertTier.NOTICE
    else -> AlertTier.OBSERVATION
}
```

`AlertTier.URGENT` is declared in [Enums.kt:94](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L94) and a `CHANNEL_URGENT` exists in [AlertManager.kt:33](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt#L33), but no code path produces it. The ML path's severity ladder ([AnomalyDetector.kt:119-123](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L119-L123)) also tops out at `ADVISORY`.

**Clinical reading:** a 7-day-trend `ADVISORY` and a "SpO2 = 84 %" reading should not share a notification channel. In an Apple/Samsung instrument the latter is a high-priority `URGENT` push, often with a 911/999 deep-link. Bios has the channel; it never reaches it.

**Recommendation:** add an absolute-cutoff escalation rule that runs *before* pattern aggregation, with hard literature-anchored thresholds. Candidates (universal where the value is unambiguous; localisation-aware where ranges differ):

| Trigger | Cutoff | Escalation |
|---|---|---|
| SpO2 (sustained ≥5 min, awake) | <90 % | `URGENT`, suggest immediate medical evaluation |
| SpO2 (single, awake) | <85 % | `URGENT`, prompt manual re-reading first |
| Resting HR (sustained ≥30 min) | >130 bpm | `URGENT`, AFib/SVT/thyrotoxicosis prompt |
| Resting HR (sustained ≥1 h) | <40 bpm without prior beta-blocker annotation | `URGENT`, bradyarrhythmia / heart-block prompt |
| Blood glucose (CGM) | <54 mg/dL | `URGENT`, hypoglycemia |
| Blood pressure (single reading) | ≥180/120 | `URGENT`, hypertensive emergency / urgency |
| Fall event (Virgil) + no check-in | combination | `URGENT`, already covered by Virgil but needs to escalate the Bios alert too |

Same mechanism the biomarker layer already implements (`absoluteAbove` / `absoluteBelow`), applied to vital signs. This is a one-day change that closes the most clinically important gap in the system.

### 2.2 No screening cadence engine

This is the single largest gap relative to the preventive-medicine systems Bios implicitly benchmarks against. **Japan's Tokutei Kenshin, South Korea's national screening, Singapore's 3Ms, the Dutch primary-care gatekeeper model — every one of them is fundamentally a cadence-driven prompt system.** A wearable layer is necessary but not sufficient.

Concretely, Bios has no answer to:

- "Should this 50-year-old owner have had a colonoscopy or FIT this year?"
- "Has this 45-year-old woman had a mammogram in the last two years?"
- "Has this owner had a fasting lipid panel since their last weight change?"
- "Is this 65+ year-old male overdue for a one-time AAA ultrasound?"
- "Cervical screening — is HPV co-test current?"
- "DEXA cadence given osteoporosis risk?"
- "Eye / dental — annual?"

These are exactly the surfaces that elevate primary care from reactive ("I have a symptom") to preventive ("I'm overdue for this; let me book it"). They are also a perfect fit for the Bios manifesto: *pull-side*, owner-controlled, never pushed unsolicited, and entirely localisation-driven (USPSTF for US, NHS for GB, GR-HSP for AU, etc.).

**Recommendation:** a `ScreeningSchedule` surface on the Settings → Privacy-Tier side that:

1. Owner inputs (or imports from FHIR `Procedure` / `ImmunizationRecommendation`) what screenings they've already had and when.
2. The region config (already structured per locale) carries the recommended cadence for that jurisdiction's guideline.
3. The pull-side surface renders "your last X was 14 months ago — the [USPSTF / NHS / KCDC] cadence is N months."
4. Nothing is pushed. Silence is still a feature; this is owner-initiated.

This is a roadmap addition, not a one-day change. But it would be the most defensible single feature to differentiate Bios from "another wearable wrapper."

### 2.3 Immunisation domain absent

Bios models `MetricReading` and biomarker labs but has no `IMMUNIZATION` domain. From the primary-care side, vaccination status is a permanent part of the patient record. FHIR R4 has the `Immunization` resource, and the Bios FHIR importer is already in place — the addition is mostly: a new `MetricDomain.IMMUNIZATION` (or, more honestly, a new entity, since it isn't a measurement), a manual-entry surface, and a FHIR `Immunization` parser.

For the screening engine in §2.2 to be meaningful, vaccine recommendations (flu, COVID, Tdap, MMR, HPV catch-up, shingles 50+, pneumococcal 65+, etc.) need a place to live and be queried against.

### 2.4 Hypertension underweighted

[ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) defines `cardiovascularStress` but the rules are RHR + HRV + SpO2. Blood pressure is in the schema and ingested via Withings (and Samsung BP per `WEARABLES_AND_DETECTION.md`) but does not anchor any wearable-side pattern.

Hypertension is the *strongest modifiable risk factor* for stroke and MI in essentially every regional guideline:

- JNC-8 / ACC-AHA (US): stage-1 ≥130/80, stage-2 ≥140/90
- ESC/ESH 2023 (EU): ≥140/90
- JSH 2019 (Japan): ≥140/90 office, ≥135/85 home
- NICE NG136 (UK): stage-1 ≥140/90 office *with* ≥135/85 home/ABPM

Adding `hypertension_emerging` (multi-day BP trend over personal baseline + literature cutoff) and `hypertensive_urgency` (single ≥180/120) closes the gap. The pattern should *prefer home-measurement averages over single office-style readings* — i.e. require at least three readings across at least two days, since white-coat-style single-reading false positives would be a clinical embarrassment.

### 2.5 No medication / current-treatment context

Posology is on the deferred Phase 9 list. From the primary-care angle, this ordering is backwards.

Specific failure modes that medication context would resolve:

| Pattern that fires falsely | Underlying medication |
|---|---|
| `hypothyroid_signature` (bradycardia + fatigue) | Beta-blocker, calcium-channel blocker |
| `hyperthyroid_signature` (tachycardia) | Stimulants, decongestants, levothyroxine over-replacement |
| `cardiovascular_stress` (RHR↑, HRV↓) | Stimulants, steroid course, SSRIs (initial period) |
| `metabolic_drift` (glucose variability) | Steroid course, atypical antipsychotic, new metformin titration |
| `recovery_deficit` (HRV↓ persistent) | Antihistamines, alcohol, SSRIs |
| `mental_health_correlate` (sleep↓, HRV↓, activity↓) | Beta-blocker fatigue, statins, SSRI initial side-effects |

A *minimal* medication annotation surface (no adherence tracking, no reminders, just a free-text or RxNorm-coded "current meds" annotation that the pattern explanation builder can read) would let Bios suppress or re-label these patterns: "*RHR is below baseline. Beta-blocker is annotated as current — this is the expected direction*."

The same surface gives the doctor-in-the-loop review flow a list of meds to share alongside the FHIR bundle. This is high-leverage, low-engineering work that does not require building a full Posology app.

### 2.6 Family history / personal risk profile

ASCVD-pooled-cohort, FRAX, Gail, QRISK3 — all the calibrated primary-care risk scores take first-degree relative history as input. Bios has no place to store it.

Recommendation: a thin `RiskFactors` entity stored alongside biomarkers in the BIOMARKER domain (treat it as a "self-reported observation" using the same `SELF_REPORTED` provenance the lab-value entry already uses). Fields: first-degree CAD <55 (male) or <65 (female), first-degree breast/ovarian cancer, first-degree colon cancer, first-degree diabetes, tobacco history (Smokeless can populate this), etc.

The pattern explanation builder then has the information it needs to escalate weighting on a 45-year-old with a first-degree MI history at 50.

### 2.7 Demographic gating: pregnancy, paediatrics, frailty, athletes

The 14-day rolling baseline is correct for the stable adult. It produces bad signals in four populations:

- **Pregnancy.** RHR rises 10–20 bpm by Q2, BBT pattern is abolished, sleep architecture changes, activity drops. A naive z-score baseline will fire `cardiovascular_stress` and `recovery_deficit` repeatedly.
- **Postpartum / breastfeeding.** Sleep fragmentation is normative for the first ~6 months. A `sleep_disruption` alert in week 2 of a newborn would be technically correct and humanly tone-deaf.
- **Paediatrics.** HR ranges are age-dependent (a child's resting HR of 90 is normal); SpO2 cutoffs differ; "URGENT" thresholds from §2.1 are wrong for children.
- **Frailty / >75.** Falls and check-in misses (Virgil keys) have different prevalence and meaning. Recovery patterns operate on different timescales.
- **Endurance athletes.** Already partially captured by the personal baseline, but the "RHR of 42 = bradycardia" cutoff in §2.1 would false-fire constantly.

Recommendation: a `PhysiologyState` enum (`STANDARD`, `PREGNANCY_T1/T2/T3`, `POSTPARTUM`, `PAEDIATRIC_BAND_x`, `FRAILTY_FLAG`, `ATHLETE_HIGH_FITNESS`) that the owner sets in Settings, that gates *which patterns are active* and *which absolute cutoffs apply*. Off by default, owner-controlled, manifesto-aligned.

### 2.8 Pattern-coverage gaps relative to a primary-care checklist

The 33-signal-rule library is well-curated but has notable preventive-medicine omissions:

| Domain | Currently covered | Notably missing |
|---|---|---|
| Cardiovascular | RHR↑, HRV↓, SpO2↓, AFib screen, dyslipidemia (biomarker) | **Hypertension pattern (§2.4)**, ASCVD-risk summary, orthostatic hypotension as pattern (only used in fall correlation) |
| Respiratory | Respiratory rate, SpO2 desaturation, infection-respiratory pattern | **No COPD-exacerbation pattern**, no asthma-pattern (peak-flow / cough), no smoking-cessation cardiovascular-improvement *positive* pattern (Smokeless `cessation_recovery_pattern` exists, but no equivalent for general respiratory recovery) |
| Metabolic | CGM variability, HbA1c, prediabetes signature, dyslipidemia, lipid panel | **No fasting insulin / HOMA-IR pathway** (`fasting_insulin` is listed as future scope only), no NAFLD signal (ALT/AST), no uric-acid / gout pattern |
| Sleep | Disruption pattern, fragmentation, efficiency, latency, derived | **Sleep apnea screening is referenced in Wearables doc but no pattern** — Samsung/Apple ship FDA-cleared apnea detection; Bios should ingest it explicitly as a `SLEEP_APNEA_EVENT` or `AHI` metric |
| Cancer | None | Bios is explicitly out of cancer-detection scope and that is correct — but the screening-cadence engine (§2.2) is how this gets addressed |
| Bone health | None | No DEXA biomarker, no FRAX inputs, no calcium / vitamin-D-status pattern (vit-D-deficiency exists but is depression-coupled, not bone-coupled) |
| Renal | None | No eGFR / creatinine biomarker, no CKD pattern (eGFR is the single most important "preventive" lab for the >50 cohort after lipids and HbA1c) |
| Liver | None | No ALT / AST / GGT, no NAFLD signal — high-prevalence and silent |
| Cognitive | None | Out of scope for now; flag only |

**Recommendation, not a redesign:** prioritise the additions that map onto the existing biomarker machinery (eGFR + creatinine in BIOMARKER; ALT/AST/GGT for liver; fasting insulin) and the existing sleep-stage feed (`SLEEP_APNEA_EVENT` / AHI as a vendor-derived passthrough). Hypertension pattern and screening cadence are higher-priority than any individual missing biomarker.

---

## 3. Manifesto / clinical-ethics tension points

These are *not* gaps — they are places where the manifesto's principles and standard clinical practice produce different answers, and Bios should be aware of which it chose.

### 3.1 "Never evaluate the person" vs. clinical risk communication

The manifesto is explicit: Bios reports deviations, never evaluates. This is correct, defensible, and the right posture for the owner-protection mission.

The friction point: a 55-year-old male owner with LDL 195, family history of MI at 50, RHR drift upward, and HRV decline *is at materially elevated 10-year ASCVD risk*. A primary-care visit would say so. Bios will not, by design. The `dyslipidemiaSignature` pattern says "Discuss the lipid panel with a healthcare provider" — which is correct, but does not convey the *magnitude* of the risk.

This is a load-bearing manifesto choice and I am not arguing against it. I am noting that the screening-cadence engine (§2.2) and the family-history surface (§2.6) are the *manifesto-aligned ways* to close part of this gap: pull-side, owner-asked, never pushed. The owner can ask "what's my ASCVD risk?" on a screen they navigate into, and the answer can be specific. That is the framing that respects both the manifesto and the clinical reality.

### 3.2 "Silence is a feature" vs. emergent thresholds

Silence is correct for trend-based notices. It is *incorrect* for SpO2 = 86 %. The push-side / pull-side distinction in [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) already carves out "Bios-state push" (disconnect notifications) as admissible because failure is not silence. Hypoxia at 86 % is closer to the disconnect-notification category than to the trend-notice category. The URGENT escalation in §2.1 is the way to express this within the manifesto.

### 3.3 "Free to all" + "no Play Services" + clinical-grade screening

Bios's commitment to no-subscription-gating and no-Play-Services is laudable and rare. The friction: the highest-evidence preventive medicine in the world (Japan/Singapore/Korea) is delivered by the *state*, not by an app. Bios cannot book the colonoscopy. It can only prompt.

The screening-cadence engine in §2.2 is the right boundary: Bios tells the owner what's recommended for their demographics and how long it's been; the owner takes that to whichever care system they have access to. This is the manifesto-aligned ceiling.

---

## 4. What I would recommend, prioritised

**Tier A — clinical safety, ship before any new feature**

1. **Wire `URGENT` to absolute vital-sign cutoffs** (§2.1). Reuse the `absoluteAbove`/`absoluteBelow` `SignalRule` mechanism. One-day change, large clinical safety upside.
2. **Add a `hypertension_emerging` and `hypertensive_urgency` pattern** (§2.4). BP is already ingested; this is signal-rule work.
3. **Annotate current medications** as `SELF_REPORTED` (§2.5). Even a free-text field. The pattern explanation builder reads it and the doctor-in-the-loop FHIR bundle includes it.

**Tier B — preventive-medicine completeness, next quarter**

4. **Screening-cadence engine** (§2.2), region-config-driven, pull-side. This is the feature that lets Bios honestly compare itself to the preventive-medicine systems it implicitly benchmarks against.
5. **Immunisation domain** (§2.3), FHIR `Immunization` import. Pairs with §2.2.
6. **Sleep apnea passthrough** as `SLEEP_APNEA_EVENT` / AHI metric, since Samsung/Apple already detect it.
7. **eGFR, creatinine, ALT, AST, GGT** added to BIOMARKER (§2.8). These are the highest-yield preventive labs not yet covered.

**Tier C — population coverage, when the foundation is solid**

8. **PhysiologyState gating** for pregnancy / postpartum / paediatrics / frailty / athlete (§2.7).
9. **Family-history / risk-factor surface** (§2.6) feeding the screening-cadence engine and pattern explanations.
10. **COPD / asthma exacerbation patterns** building on existing respiratory rate + SpO2 + cough metrics.

**Do not adopt**

- A "biological age" or "longevity score" composite. The DATA_MODEL.md guard against composing epigenetic clocks is exactly right; do not retreat on it.
- Push-side risk-stratification scores ("you are at high risk for X"). The clinical and manifesto answers converge here: stratification belongs on the pull side, surfaced when the owner asks.

---

## 5. Summary line for the project

> Bios is a credible, literature-grounded *passive observation layer* that already outperforms the consumer-wearable category on privacy, ethics, and clinical defensibility. To match the preventive-medicine systems it implicitly benchmarks against (Singapore / Japan / Netherlands / Korea), it needs (a) a reachable URGENT tier with absolute vital-sign cutoffs, (b) a pull-side screening-cadence engine driven by the existing region-config layer, (c) a medication-annotation surface to denoise the patterns it already has, and (d) immunisation and BP as first-class entities. None of these violate the manifesto; all of them are within the existing architecture.
