# Paediatrics Audit — Bios in the Paediatric Population

**Scope:** Bios's clinical reach as a longitudinal observation layer for children and adolescents, evaluated from the perspective of a board-certified general paediatrician with subspecialty exposure to neonatology, adolescent medicine, paediatric cardiology, paediatric endocrinology, and developmental-behavioural paediatrics. Anchored against the AAP Bright Futures schedule, NICE NG143 (fever <5y) / NG87 (T1D children & young people), WHO Child Growth Standards, the PALS / EPLS / APLS reference ranges, and the canonical paediatric early-warning literature (Monaghan 2005 PEWS, Parshuram 2009 Bedside-PEWS, RCPCH 2017 PEWS standardisation).
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Paediatric primary care + paediatric subspecialty literacy. Catalogue entries that anchor this audit are [MEDICAL_SPECIALTIES_WORLDWIDE.md §1.1](MEDICAL_SPECIALTIES_WORLDWIDE.md) ("Paediatrics (general)") and §1.2 paediatric subspecialty references. The primary-care audit's Gap #7 ("Children, pregnancy, frailty: no demographic gating") is the entry point for this lens — this audit reframes that gap with paediatric specificity rather than re-litigating it.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [ImmunizationRecord.kt](../../android/app/src/main/java/com/bios/app/model/ImmunizationRecord.kt), [VaccineCatalog.kt](../../android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt), [ScreeningEntry.kt](../../android/app/src/main/java/com/bios/app/model/ScreeningEntry.kt). Skimmed first: [MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md) (primary-care audit — Gap #7 is the entry point), [PSYCHIATRY_POV.md](PSYCHIATRY_POV.md) (cross-referenced for adolescent mental health), [CARDIOLOGY_POV.md](CARDIOLOGY_POV.md) (cross-referenced for the small paediatric-cardiology overlap).

---

## Executive summary

Bios is, viewed by a general paediatrician, a **carefully built adult-physiology instrument applied to a population it does not yet model.** The architectural primitives that would make paediatric use safe are partially present — [`PhysiologyState.PAEDIATRIC`](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L38) exists as an enum value, the `excludedStates` mechanism on [`ConditionPattern`](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L36) can suppress adult-only patterns, the URGENT escalation tier is reachable per [`EmergencyVitalPatterns`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) — but the *content* layer has not been written: none of the existing emergency vital-sign cutoffs are age-banded, the [`VaccineCatalog`](../../android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt) explicitly excludes paediatric vaccines, there is no growth-tracking primitive, and no PEWS-shaped pattern exists. A 2-month-old wearing a sensor-bearing device (rare but real — Owlet sock, Nanit band, Garmin BounceJr-class kid wearable) whose data lands in Bios today would have a normal heart rate of 130–160 bpm parsed by [`tachycardiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L122) (≥130 bpm = URGENT) as a clinical emergency, and every adult-baseline pattern would mis-fire constantly.

Bios is also honest about scope. The [`VaccineCatalog`](../../android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt#L20) header explicitly says paediatric vaccines are "out of scope until ACIP-specific paediatric work lands"; [`PhysiologyState.kt`](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L25) acknowledges paediatric age-band tables as a follow-up. The instrument-not-coach manifesto stance is *especially* well-suited to paediatrics, where the wearable consumer category (Owlet, Snuza, Nanit) has been repeatedly criticised by AAP for alarm-based selling of false reassurance. Bios's posture is structurally favourable. What is missing is the paediatric-specific code that honours that posture.

The audit nevertheless surfaces twelve gaps that matter clinically when the owner — or, more honestly, the child whose parent is the device owner — is under 18. Ordered by clinical impact on the paediatric population:

1. **Age-banded vital-sign norms do not exist anywhere in the codebase.** This is the single biggest finding. [`EmergencyVitalPatterns.tachycardiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L122) fires URGENT at ≥130 bpm. PALS / EPLS / WHO reference ranges have a normal resting HR of **100–160 bpm for neonates (0–1 mo), 90–160 for infants (1 mo–1 yr), 80–140 for toddlers (1–3 yr), 65–130 for preschool (3–5 yr), 60–110 school-age (5–12 yr), and 55–95 for adolescents (12–18 yr)** (PALS 2020; EPLS 2021; WHO IMCI). Bradycardia ([`bradycardiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L157) at ≤35 bpm) is similarly age-inverted — a 2-month-old at 80 bpm is in genuine bradyarrhythmia territory (PALS bradycardia cutoff for infants is <100 bpm, not <35). The same problem applies to SpO2 (neonates accept 90–95 % preductal in the first 24 h of life), BP (paediatric hypertension uses age + sex + height percentile tables, not a fixed 130/80 mmHg), and RR (newborn 30–60 br/min is normative; adult tachypnoea cutoff would fire constantly). Without age-band tables, **every emergency vital cutoff is clinically wrong for everyone under 18**, and the personal-baseline patterns are also wrong because a 14-day baseline on a developing physiology drifts with growth, not pathology.

2. **No growth-tracking primitive.** Paediatric primary care is, more than anything else, growth surveillance: length / height, weight, head circumference (until 36 months), BMI percentile, and weight-for-length (until 24 months) plotted against WHO Child Growth Standards (0–5 years) or CDC growth charts (2–20 years). Failure-to-thrive is defined as crossing two major percentile lines downward; obesity is defined ≥95th percentile BMI-for-age; short stature is <3rd percentile height-for-age. Bios has [`BODY_MASS`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L121) via Withings, but no `HEIGHT` / `BODY_LENGTH` metric, no `HEAD_CIRCUMFERENCE`, no percentile computation, no growth-chart view. This is the highest-leverage paediatric primary-care primitive Bios could ship — and the data model already supports the canonical-keys pattern; this is one schema entry plus a percentile-table lookup.

3. **The `PAEDIATRIC` PhysiologyState is enumerated but unwired.** [`PhysiologyState.PAEDIATRIC`](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L38) is a single bucket covering ages 0–18. Clinically that is six bands: neonate (0–28 d), infant (1–12 mo), toddler (1–3 y), preschool (3–5 y), school-age (5–12 y), adolescent (12–18 y). Each has a different normal-HR/RR/BP envelope, a different SpO2 floor, a different temperature interpretation, a different sleep-architecture profile, a different developmentally-appropriate activity range, and a different vaccine schedule. A single PAEDIATRIC state cannot encode any of this without sub-banding. The doc-string on [`PhysiologyState`](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L24) already notes that "age-band tables (paediatric HR-high, athlete RHR-low, pregnancy SpO2 floor)" are follow-ups; this audit converts that note into a concrete six-band design.

4. **Immunisation domain exists but is adult-only.** [`ImmunizationRecord`](../../android/app/src/main/java/com/bios/app/model/ImmunizationRecord.kt) and [`VaccineCatalog`](../../android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt) are the architecturally correct primitives, and the [`VaccineCatalog`](../../android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt#L20) header is honest that "paediatric vaccines and the long tail" are deferred. **Paediatrics is *the* specialty where the immunisation schedule is most load-bearing** — birth-dose HepB, 2/4/6-month DTaP/Hib/IPV/PCV/RV, 12-month MMR/VAR/HepA, kindergarten-entry boosters, age-11 Tdap/HPV/MenACWY, age-16 MenACWY booster + MenB. ACIP / CDC, WHO EPI, UK Green Book, Canada NACI, Australia NIP, and every national paediatric body publishes a schedule that is the *single most consulted document in primary paediatric care*. The screening-cadence engine the primary-care audit recommended would land on this entity; it does not yet.

5. **Adolescent confidentiality is unmodelled and the manifesto framing has unresolved tension.** "The owner is final" is a strong, defensible posture for an adult. For a 14-year-old whose parent owns the device, "owner" is ambiguous in a way the manifesto does not address. Adolescent medicine in essentially every Western jurisdiction grants minors confidentiality protections around reproductive health, mental health, substance-use treatment, and (variably) gender-affirming care — in the US, the AAP Bright Futures and the SAHM consensus statement on confidential care, in the UK the Gillick competence framework, in Canada the mature-minor doctrine. **A parent-installed Bios on a teen's phone, with cycle data, mood-correlate patterns, and substance-use companion signals visible to the parent-as-owner, is a confidentiality breach by design.** This is the most ethically significant paediatric gap and it sits at the intersection of the [`ReproductiveDatabase`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) isolation, the [`MOOD_DRIFT_SCORE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L216) companion signal, and the manifesto principle of owner finality. It is also genuinely hard — there is no clean answer — but the gap should be named.

6. **Childhood fever is parsed adult-style.** [`RegionConfigProvider`](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) carries a single `highFeverCelsius` per region (US: 39.4 °C; EU/GB/CA/AU: 39.0 °C; JP: 38.5 °C). NICE NG143 (Fever in under 5s) uses a fundamentally different traffic-light algorithm: any temperature ≥38 °C in an infant <3 months old is a red feature warranting urgent assessment, ≥39 °C in 3–6 months is amber, and the response also depends on appearance, hydration, capillary refill, and the rash / non-blanching test. The single-threshold model misses neonatal fever entirely (38.0 °C in a 6-week-old is an emergency department visit; in an adult it is a low-grade fever). Bios's wearable temperature is also **wrist skin-temperature**, not core temperature — the clinical interpretation of fever requires rectal (gold standard <2 yr), axillary, oral, tympanic, or temporal-artery measurement, and Bios's wrist-derived signal is none of these. The fever-pattern logic needs a paediatric age modifier and an explicit acknowledgement of the measurement-site limitation.

7. **No PEWS-shaped early-warning surface.** Paediatric Early Warning Score (PEWS — Monaghan 2005; Parshuram 2009 Bedside-PEWS; RCPCH 2017 standardisation; NHS England paediatric early-warning system 2022) is the canonical paediatric deterioration tool — a composite of respiratory effort, oxygen requirement, conscious level (AVPU), HR, RR, capillary refill, and behaviour. Bios already has the substrate for a wearable-PEWS surrogate (HR, RR, SpO2, [`OXYGEN_FLOW_RATE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L56), [`CONSCIOUSNESS_LEVEL`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L80) for GCS/AVPU). The research literature on wearable PEWS surrogates is young (Brekke 2019; Sefton 2021) but converging. A pull-side PEWS-style view that renders age-banded HR/RR/SpO2 against PEWS bands would be a genuinely useful paediatric primary-care surface and is mechanically straightforward once age-banding (Gap #1) lands.

8. **Type 1 diabetes patterns are absent despite glucose substrate being present.** Paediatric T1D is the highest-prevalence chronic disease Bios is positioned to support — incidence ~22 per 100,000 children under 14, rising 3–4 % annually (DIAMOND, SEARCH). The CGM ingestion path ([`BLOOD_GLUCOSE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L114), Dexcom adapter), [`GLUCOSE_CV`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L117), [`GLUCOSE_TIME_IN_RANGE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L119), and the [`hypoglycemiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L89) URGENT escalation are all present. What is missing: a **DKA-onset convergence pattern** (Kussmaul breathing → elevated RR, dehydration → tachycardia, polyuria pattern, sometimes detectable hypothermia in late decompensation), the paediatric Time-In-Range target (≥70 % between 70–180 mg/dL per ISPAD 2022 — different from the adult target), and the paediatric-specific hypoglycaemia threshold (children tolerate lower glucose less well; the ISPAD threshold for severe hypoglycaemia is ≤54 mg/dL same as adult but the symptomatic threshold is age-modified). The insulin-pump integration that would close T1D management properly is out of scope (CIQ / Loop / OmniPod 5 data) but the detection / observation side is achievable.

9. **No asthma exacerbation pattern.** Childhood asthma is the most common chronic disease of childhood (~8 % US prevalence) and exacerbations follow a clinically recognisable pattern: increased cough frequency, tachypnoea, accessory-muscle use, falling SpO2 (typically the late sign), reduced peak flow. Bios has SpO2 + HR substrate, no respiratory-rate continuous-monitor on most wearables (Apple Watch and WHOOP estimate RR during sleep only), and no peak-flow ingestion path. The [`RespiratoryExacerbationPatterns.kt`](../../android/app/src/main/java/com/bios/app/alerts/RespiratoryExacerbationPatterns.kt) file exists for adult COPD — a paediatric-asthma sibling pattern with age-banded RR thresholds, trigger-correlation surface (the air-quality metrics in [`AIR_PM25`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L138) etc. are perfectly suited to this), and a peak-flow manual-entry path would close most of this.

10. **Neonatal monitoring is correctly out of scope but the framing matters.** Owlet (sock), Snuza (clip), Nanit (camera + breathing band), and similar consumer infant monitors have been the subject of repeated AAP cautionary statements (2017, 2018, 2021) on the grounds that (a) they have not been shown to prevent SIDS / SUDC, (b) they generate parental anxiety and ED visits for false alarms, (c) they create false reassurance against safe-sleep practices. Bios should *not* try to enter this market. **Bios's manifesto posture — instrument not coach, no alarm-based selling, no false reassurance, "silence is a feature" — is the structurally correct response to the Owlet failure mode**, and worth explicitly stating in any paediatric scope document: Bios does not claim to detect SIDS / BRUE / apnoea-of-prematurity, and a parent looking for that should be directed to a clinically-prescribed monitor, not a consumer wearable.

11. **Adolescent mental health and eating-disorder biomarker patterns are absent, and the appropriate framing is delicate.** Anorexia nervosa, atypical anorexia, and ARFID present with a recognisable biomarker envelope: bradycardia (HR in the 30s–40s in inpatient cases — Mehler 2018), hypothermia, orthostatic intolerance with sustained ΔHR ≥35 bpm on standing, prolonged QTc, reduced HRV, hypotension. Adolescent depression rates have risen sharply post-COVID (CDC YRBSS 2023: 42 % of high-school students reported persistent sadness; suicide is the second leading cause of death ages 10–14). Bios has substrate to detect *patterns consistent with* these conditions; **the manifesto-aligned framing is unusually well-suited here** — no scores, no streaks, no body-shaming, data-only, pull-side, owner-asks. But the bradycardia URGENT cutoff at ≤35 bpm ([`bradycardiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L157)) will fire on an inpatient anorexic adolescent, and the surrounding alert text frames this as "highly trained endurance athletes" or "documented rate-control medication" — neither of which is the relevant differential in a 15-year-old girl with a falling BMI. This is sensitive surfacing territory; cross-reference [PSYCHIATRY_POV.md](PSYCHIATRY_POV.md) §2 for the broader mental-health framing.

12. **Family-based-care model is unrepresented.** Paediatrics is uniquely a *family-mediated* specialty: the paediatric patient is generally not the device owner; the parent is. The pre-visit data flow looks like "mother brings child to clinic, hands the clinician a phone with the child's growth chart and recent sleep data" — and the entity model that supports this (a child profile linked to a parent profile, with appropriate inheritance of consent, data ownership, and confidentiality rules per Gap #5) does not exist. Bios's single-owner data model is correct for adult use; for paediatric use, a `Dependent` / `ChildProfile` concept linked to the owner-account, with explicit data-segregation, would be needed. This is a substantial architectural lift and arguably should *not* be undertaken — Bios may legitimately decide that paediatric use is out of v1 scope and direct families to a paediatric-specific companion app instead.

Items 13–18 (concussion / return-to-play, sleep-architecture age differences, anaphylaxis convergence, child-abuse safeguarding data-subpoena exposure, ADHD passive-biomarker research, ASD biomarker research) are flagged as lower-priority paediatric considerations and discussed in §2.13–§2.18.

---

## 1. What Bios already does well, viewed from the paediatric clinic

| Quality | Evidence | Why it matters in paediatrics |
|---|---|---|
| **Instrument-not-coach posture is structurally favourable for the paediatric wearable category** | [MANIFESTO.md](../../MANIFESTO.md) principle 7; [`AlertContentPolicy`](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) CI-gated banlist | The dominant failure mode of the consumer paediatric wearable category (Owlet, Snuza, Nanit) is alarm-based selling of false reassurance — AAP has issued formal statements against it. Bios's "silence is a feature" and "instrument not coach" posture is the correct philosophical answer; the architecture follows the philosophy |
| **`PhysiologyState.PAEDIATRIC` enumerated and the gating mechanism exists** | [`PhysiologyState.PAEDIATRIC`](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L38); [`ConditionPattern.excludedStates`](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L36) | The architectural primitive that *would* let adult patterns be suppressed in paediatric use exists. What is missing is the content layer (age-band sub-states, age-banded cutoffs, paediatric replacement patterns) — the rails are laid |
| **Reproductive-data isolation generalises usefully to adolescent confidentiality** | Separate SQLCipher key for the reproductive database; FHIR exporter skips `WOMENS_HEALTH` by default | Adolescent confidentiality (Gap #5) is a different problem but the *isolation primitive* — encrypted-separately, excluded-from-export-by-default — is the right shape and reuse is plausible |
| **Vaccine domain primitive is correct, even though paediatric vaccines are not enumerated** | [`ImmunizationRecord`](../../android/app/src/main/java/com/bios/app/model/ImmunizationRecord.kt) accepts arbitrary CVX codes; the doc-string explicitly notes paediatric scope is deferred | The substrate is here. Adding the ACIP paediatric vaccine list is a content addition, not an architectural lift |
| **`OXYGEN_FLOW_RATE` and `CONSCIOUSNESS_LEVEL` are first-class metrics** | [`OXYGEN_FLOW_RATE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L56), [`CONSCIOUSNESS_LEVEL`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L80) (GCS / AVPU encoding) | These are the two non-vital-sign inputs PEWS / Bedside-PEWS scores require. Their presence in the canonical-keys schema means a PEWS-shaped view (Gap #7) does not need new MetricType keys |
| **CGM substrate handles paediatric T1D** | Dexcom adapter; [`BLOOD_GLUCOSE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L114) + [`GLUCOSE_CV`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L117) + [`GLUCOSE_TIME_IN_RANGE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L119); [`hypoglycemiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L89) URGENT path | The T1D adolescent population is the highest-prevalence chronic-disease cohort Bios is well-positioned to serve. Substrate is shared with adult diabetes management, ISPAD-specific paediatric thresholds are the additions (Gap #8) |
| **Environmental metrics support asthma trigger correlation** | [`AIR_PM25`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L138), [`AIR_VOC`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L139), [`AIR_CO2`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L140) | Paediatric asthma is uniquely trigger-driven (allergen, air quality, exercise, viral). The environment metrics give Bios a substrate no clinical EMR has |
| **Regional config supports the WHO / NICE / AAP guideline divergence** | [`RegionConfigProvider`](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) per-region clinical thresholds | The paediatric guidelines that differ by jurisdiction (WHO IMCI vs AAP Bright Futures vs NICE NG143 fever) can ride on the existing region-aware infrastructure — the regional split is already designed |
| **Honest deferral language** | [`VaccineCatalog.kt`](../../android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt#L20) explicitly says paediatric vaccines are out of scope; [`PhysiologyState.kt`](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L25) acknowledges age-band tables as follow-up | A paediatrician reading the code does not have to discover the paediatric gap by surprise — the deferrals are documented. This is the right discipline for a project that has chosen not to ship a paediatric layer yet |

These are not parity wins — they are the architectural primitives a paediatric layer would build on. The gap is content, not structure.

---

## 2. Paediatrics-specific gaps, ordered by impact

### 2.1 Age-banded vital-sign norms — the single largest paediatric gap

This is the finding to act on first. Every paediatrician reading the [`EmergencyVitalPatterns`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) file will identify the same problem.

The PALS / EPLS / APLS / WHO IMCI reference ranges:

| Age band | Resting HR (bpm) | Resp rate (br/min) | SBP (mmHg) | SpO2 floor |
|---|---|---|---|---|
| Neonate (0–28 d) | 100–205 | 30–60 | >60 | ≥90 (preductal first 24 h) / ≥95 otherwise |
| Infant (1–12 mo) | 100–180 | 30–53 | >70 | ≥95 |
| Toddler (1–3 y) | 90–150 | 22–37 | >70 + (2×age) | ≥95 |
| Preschool (3–5 y) | 80–140 | 20–28 | >75 | ≥95 |
| School-age (5–12 y) | 70–120 | 18–25 | >80 | ≥95 |
| Adolescent (12–18 y) | 60–100 | 12–20 | >90 | ≥95 |

(Sources: PALS 2020 provider manual; EPLS 2021; APLS 2024; WHO IMCI chartbook; AAP textbook of paediatric care.)

The clinical implications for the current emergency vital-sign patterns:

- [`tachycardiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L122) fires URGENT at ≥130 bpm. **Normal for any child ≤3 years old.** A toddler's resting HR of 130 is the **midpoint** of the normal range. The pattern needs an age modifier or paediatric exclusion.
- [`bradycardiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L157) fires URGENT at ≤35 bpm. **A neonate at 80 bpm is in clinical bradycardia (PALS cutoff <100); the current pattern would not fire until 35.** A 4-year-old at 60 bpm warrants evaluation; the current pattern is silent until 35. The age-banded inverse is required.
- [`spo2Critical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L58) fires URGENT at ≤85 %. This is *approximately* correct for older children (≥5 y), low for adolescents matching adult thresholds, and wrong for neonates in the first 24 h of life (where 85 % preductal is acceptable transitional physiology) — but the neonatal exception is so narrow it probably doesn't merit modelling. The bigger gap is the **paediatric SpO2 concern threshold of ≥95 %** (vs the [`RegionConfigProvider`](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt#L243) `spo2ConcernThreshold` of 95.0 % which matches), where a sustained reading of 92 % in a 6-year-old is more clinically significant than the same value in a 60-year-old with COPD.
- [`HypertensionPatterns.hypertensionEmerging`](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt#L62) fires on median ≥130 systolic. **Paediatric hypertension is defined as ≥95th percentile for age, sex, and height** (AAP Clinical Practice Guideline 2017 — Flynn et al.). A 5-year-old boy at the 50th height percentile has a 95th-percentile SBP of ~112 mmHg, not 130. Adult absolute thresholds dramatically under-detect paediatric hypertension. The percentile-table approach is the only correct one and is *exactly* the kind of lookup-table content the [`RegionConfigProvider`](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) layer is set up to host.

Concrete recommendation:

1. Sub-band [`PhysiologyState.PAEDIATRIC`](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L38) into `PAEDIATRIC_NEONATE`, `PAEDIATRIC_INFANT`, `PAEDIATRIC_TODDLER`, `PAEDIATRIC_PRESCHOOL`, `PAEDIATRIC_SCHOOL_AGE`, `PAEDIATRIC_ADOLESCENT`. Add a date-of-birth field to the owner profile so the state can update automatically as the child ages out of a band (this is the only place in Bios where time-based auto-state-transition makes sense; manifesto-clean because the owner has set DOB explicitly).
2. Add a `PaediatricVitalBands` table (parallel to the existing `BiomarkerBands` machinery in [`RegionConfigProvider`](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt)) keyed by `PhysiologyState` sub-band, with HR/RR/SBP/DBP/SpO2 normal-and-critical thresholds per band.
3. Add `excludedStates = PhysiologyState.PAEDIATRIC_ALL` to the current adult-cutoff patterns ([`tachycardiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L122), [`bradycardiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L157), [`hypertensionEmerging`](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt#L62), [`hypertensiveUrgency`](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt)) so they go silent for paediatric owners.
4. Create a `PaediatricEmergencyVitalPatterns.kt` sibling object with band-aware cutoffs, using a new pattern shape that reads the active `PhysiologyState` sub-band and selects the corresponding band thresholds at evaluation time.

This is the largest single piece of work in the paediatric audit and the prerequisite for everything else. Until it lands, **Bios should not be marketed for paediatric use** — the URGENT-tier false-positive rate in any patient under 12 is unacceptably high.

### 2.2 Growth tracking — the highest-leverage paediatric primary-care primitive

WHO Child Growth Standards (0–5 years; the WHO MGRS reference) and the CDC growth charts (2–20 years; the older 2000 reference) are the canonical paediatric percentile sources. Length / height, weight, head circumference (until 36 months), BMI-for-age, weight-for-length (until 24 months) — each plotted as a percentile band against age and sex.

Clinical use cases:

- **Failure-to-thrive screening** — crossing two major percentile lines (5th/10th/25th/50th/75th/90th/95th) downward over consecutive visits is the classic FTT signal. Bios's longitudinal storage is exactly suited to this.
- **Obesity surveillance** — BMI ≥95th percentile-for-age defines paediatric obesity (AAP 2023 obesity clinical practice guideline); ≥85th is overweight. Adult BMI cutoffs do not apply.
- **Short stature workup trigger** — height <3rd percentile or growth velocity <5 cm/y after age 4 prompts endocrinology referral (growth-hormone deficiency, Turner syndrome, constitutional delay).
- **Microcephaly / macrocephaly** — head circumference <3rd or >97th percentile or crossing percentile lines in the first 36 months.

Bios has [`BODY_MASS`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L121) via Withings. Missing metric keys:

- `BODY_HEIGHT` (or `BODY_LENGTH` for under 2 y, conventionally measured recumbent) — cm, `METABOLIC` or new `ANTHROPOMETRY` domain, `allowsManualEntry = true`.
- `HEAD_CIRCUMFERENCE` — cm, until ~36 months clinically.
- A derived `BMI_PERCENTILE_FOR_AGE` and `HEIGHT_PERCENTILE_FOR_AGE` computed against the WHO / CDC tables.

A pull-side `GrowthChartView` rendering the percentile bands and the owner's measurements is the visible deliverable. The WHO LMS parameter tables are public-domain CSVs; the percentile-calculation math is well-established. This is the single highest-leverage paediatric primary-care addition.

### 2.3 PhysiologyState sub-banding — the architectural pre-requisite

Already discussed in §2.1 as the prerequisite. Spelling out the design:

```
enum class PhysiologyState {
    STANDARD,
    PREGNANCY_T1, PREGNANCY_T2, PREGNANCY_T3,
    POSTPARTUM,
    ATHLETE_HIGH_FITNESS,
    FRAILTY_FLAG,
    // Replaces the single PAEDIATRIC bucket:
    PAEDIATRIC_NEONATE,        // 0–28 d
    PAEDIATRIC_INFANT,         // 1–12 mo
    PAEDIATRIC_TODDLER,        // 1–3 y
    PAEDIATRIC_PRESCHOOL,      // 3–5 y
    PAEDIATRIC_SCHOOL_AGE,     // 5–12 y
    PAEDIATRIC_ADOLESCENT,     // 12–18 y
    ;

    companion object {
        val PAEDIATRIC_ALL = setOf(
            PAEDIATRIC_NEONATE, PAEDIATRIC_INFANT, PAEDIATRIC_TODDLER,
            PAEDIATRIC_PRESCHOOL, PAEDIATRIC_SCHOOL_AGE, PAEDIATRIC_ADOLESCENT,
        )
    }
}
```

Convenience set for `excludedStates` declarations; automatic band-transition driven by owner-set DOB (the auto-transition is a manifesto edge case but justifiable — the owner explicitly opted into "paediatric mode at age X" and the band update is the natural consequence). PSYCHIATRY_POV's `POSTPARTUM` precedent is the closest existing analogue and works the same way (a time-window state that the owner sets once and the engine respects without re-prompting).

### 2.4 Immunisation domain — paediatric ACIP / NIP / Green Book content

[`VaccineCatalog`](../../android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt) currently lists 22 adult vaccines. The paediatric ACIP / CDC schedule additions:

- **Birth** — HepB dose 1
- **2 months** — HepB dose 2, RV (rotavirus), DTaP, Hib, PCV, IPV
- **4 months** — RV, DTaP, Hib, PCV, IPV
- **6 months** — HepB dose 3, RV (if RV5), DTaP, Hib, PCV, IPV, flu (annual starting 6 mo)
- **12–15 months** — Hib booster, PCV booster, MMR, VAR, HepA dose 1
- **15–18 months** — DTaP booster
- **4–6 years** — DTaP, IPV, MMR, VAR (kindergarten boosters)
- **11–12 years** — Tdap, HPV (2-dose if <15, 3-dose if ≥15), MenACWY dose 1
- **16 years** — MenACWY dose 2; MenB (shared decision)

(CVX codes for the additions: DTaP=20, Hib=49, PCV13=133, PCV15=215, IPV=10, RV1=119, RV5=116, HepA pediatric=83, MenB-4C=162.)

WHO EPI adds BCG (CVX=19, neonatal in TB-endemic regions), and country-specific schedules diverge meaningfully — the UK Green Book includes MenB at 8 weeks, the Japanese NIP places PCV at slightly different intervals, Australia's NIP includes meningococcal B for Aboriginal and Torres Strait Islander children. The [`RegionConfigProvider`](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) per-region layer is the natural home for these divergences.

The screening-cadence engine the primary-care audit recommended ([MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md) §2.2) is the consumer of this content. For a paediatric owner, the pull-side surface would say "next due: 4-month DTaP, Hib, PCV, IPV, RV — typically 2 months from your last visit on [date]" rather than the adult "Tdap booster due in 4 years."

### 2.5 Adolescent confidentiality — the ethically hardest gap

The clinical / legal framework:

- **US:** AAP / SAHM joint statement on confidential adolescent care (2016, reaffirmed 2022); state-by-state minor-consent laws vary widely on reproductive health (most states permit confidential STI testing and contraception ≥12 y), mental health (most permit confidential outpatient mental-health care ≥12–14 y), substance-use treatment (most permit ≥12 y).
- **UK:** Gillick competence (1985 Gillick v West Norfolk; Fraser guidelines for contraception); GMC 0–18 guidance.
- **Canada:** Mature minor doctrine; provincial variation.
- **Australia:** Gillick-derived; state variation.

The Bios manifesto's "owner is final" framing treats the device owner as the single party with full data access. For an adult owner this is correct; for a parent-installed-on-teen's-device case, this conflicts with established adolescent confidentiality norms in essentially every Western jurisdiction.

The cleanest design answer is probably:

1. A device installed for paediatric use carries an *explicit installation type* — "parent-managed (under 13)" vs "adolescent-self-managed (≥13)". The age threshold here is debatable (13 is COPPA-aligned; 12 is more adolescent-medicine-aligned; some jurisdictions use 14).
2. For the adolescent-self-managed case, parental access to specific data domains (reproductive, mental-health-correlate, substance-use companion signals, sexual-health screening cadence) is **excluded by default**, mirroring the reproductive-data isolation pattern.
3. The adolescent owner can grant specific access; the parent cannot un-grant the adolescent's confidentiality default.
4. This is documented in the manifesto as the paediatric-specific exception to "owner is final" — the device owner's authority is bounded by the data subject's confidentiality rights when those subjects are different parties.

This is a substantial design conversation. The point of raising it in the audit is to name that the current single-owner model cannot honour adolescent confidentiality without modification, and the design conversation should happen *before* a paediatric layer ships, not after.

Cross-reference [PSYCHIATRY_POV.md](PSYCHIATRY_POV.md) on the broader digital-phenotyping privacy frame — the perinatal section there discusses an adjacent case where the owner-is-final framing has tension with established clinical confidentiality norms.

### 2.6 Childhood fever — measurement-site and age-modifier gaps

NICE NG143 (Fever in under 5s, updated 2021) defines the paediatric fever traffic-light:

- **Any temperature ≥38 °C in <3 months** → red (urgent assessment, blood culture, urine, often LP)
- **Temperature ≥39 °C in 3–6 months** → amber (clinical assessment)
- **Temperature in >6 months** → assess in context with appearance, hydration, respiratory effort, rash

AAP's clinical practice statement on the febrile young infant (2021, Pantell et al.) carves out the 8–60-day infant as a special category warranting structured evaluation; the AAP fever-without-source guidance for older children focuses on duration, appearance, and source.

Bios's [`RegionConfigProvider.ClinicalThresholds.highFeverCelsius`](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt#L246) is a single adult-style threshold per region. It does not encode the age modifier. The neonatal-fever case in particular is dangerous to miss: 38.0 °C in a 6-week-old is an emergency department visit; in an adult it is a low-grade fever that warrants supportive care.

The bigger structural issue: **Bios's wearable skin temperature is wrist-derived, not core temperature.** [`SKIN_TEMPERATURE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L68) and [`SKIN_TEMPERATURE_DEVIATION`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L69) carry the right semantics for adult infection-detection (delta from baseline is what Smarr 2020 validated), but for paediatric fever clinical decision-making, the measurement site matters: rectal is gold standard <2 y, axillary is acceptable but underestimates, oral is age-inappropriate <4 y, tympanic has wide variance, temporal-artery is convenient but less validated. **A `BODY_TEMPERATURE_CORE` metric with a `measurementSite` payload field** (rectal / oral / axillary / tympanic / temporal / wrist) would let paediatric fever logic apply the right interpretation rules — and for adults the same plumbing allows higher-confidence fever detection from a tympanic thermometer than from a wrist sensor.

### 2.7 PEWS / Bedside-PEWS — the wearable-PEWS surrogate opportunity

Paediatric Early Warning Score (Monaghan 2005 — Royal Hospital for Sick Children Edinburgh) and Bedside-PEWS (Parshuram 2009) are the canonical paediatric deterioration tools, used inpatient on essentially every paediatric ward in the UK / Canada / Australia / progressive US centres. RCPCH (UK) standardised PEWS in 2017; NHS England rolled out the System-Wide Paediatric Observations Tracking (SPOT) in 2022.

The PEWS components — behaviour / consciousness, cardiovascular (HR, capillary refill, colour), respiratory (RR, effort, oxygen) — map well onto Bios's substrate:

- [`HEART_RATE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L38) + age-banded thresholds (per Gap #1)
- [`RESPIRATORY_RATE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L52) + age-banded thresholds
- [`BLOOD_OXYGEN`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L49) + [`OXYGEN_FLOW_RATE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L56) for supplemental-O2 context
- [`CONSCIOUSNESS_LEVEL`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L80) using AVPU / GCS encoding (manual entry by caregiver)

Wearable-PEWS-surrogate research (Brekke 2019; Sefton 2021; Roland 2024 RCPCH wearable consensus draft) suggests the wrist-based vital-sign signal can support PEWS-style early-warning at home for selected populations (post-op paediatric cardiac, oncology febrile-neutropenia surveillance, asthma exacerbation early warning). The literature is young but converging.

A pull-side `PaediatricEarlyWarningView` that renders the four PEWS components against age-banded thresholds, with a 0–9 PEWS-style composite for context, would be:

- A defensibly framed instrument (it shows the components; the caregiver / clinician interprets the composite, per the manifesto).
- An exact match for the paediatric ED triage workflow ("show me their PEWS trend for the last 6 hours").
- Mechanically straightforward once age-banding lands.

### 2.8 Paediatric T1D — DKA detection and ISPAD-specific thresholds

Paediatric T1D incidence is rising 3–4 % annually (DIAMOND study; SEARCH for Diabetes in Youth). The substrate is largely in place:

- CGM ingestion via Dexcom adapter
- [`BLOOD_GLUCOSE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L114), [`GLUCOSE_CV`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L117), [`GLUCOSE_MAGE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L118), [`GLUCOSE_TIME_IN_RANGE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L119), [`GLUCOSE_PEAK_24H`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L120)
- [`hypoglycemiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L89) URGENT path at ≤54 mg/dL

The paediatric-specific gaps:

- **ISPAD 2022 Time-In-Range target** is ≥70 % between 70–180 mg/dL for children and adolescents with T1D (same range as adult ADA target — but the framing and clinical interpretation differ; paediatric endocrinology pays closer attention to overnight hypoglycaemia and post-prandial excursions because of growth-hormone effects and meal-pattern variability).
- **DKA-onset convergence pattern.** New-onset T1D often presents in DKA (~30 % of paediatric new-onset cases — Cherubini 2020). Established T1D patients have lifetime risk of recurrent DKA, often associated with insulin omission, illness, or pump failure. The detection-able signature: rising glucose over 12–24 h (often >250 mg/dL sustained), Kussmaul breathing (RR elevation with deep pattern), tachycardia from dehydration, sometimes mild hypothermia in late presentation, polyuria pattern (could be inferred from sleep fragmentation + nocturnal awakening if a smart-toilet or owner-logged urination is present, though no clean signal exists today). A `paediatric_dka_emerging` pattern at ADVISORY with the glucose + RR + HR convergence, age-restricted to PAEDIATRIC sub-bands and gated on a T1D diagnosis flag, would be defensible.
- **Severe hypoglycaemia in young children** — the cognitive-impairment risk from recurrent severe hypoglycaemia in children <6 is the historical reason ISPAD targets were originally relaxed for that age; the current consensus tightens them but the **counter-regulatory response is age-dependent** and a paediatric-specific symptomatic-hypoglycaemia warning at higher glucose (≤70 mg/dL with symptoms) is reasonable.

Insulin pump integration (Tandem Control-IQ, Medtronic 780G, OmniPod 5, Loop / OpenAPS) is genuinely useful but vendor-locked and not in current Bios scope — flag and defer.

### 2.9 Paediatric asthma — exacerbation pattern with environmental correlation

Asthma is the most common chronic disease of childhood. Paediatric asthma exacerbations have a recognisable signature:

- **Early** — increased cough frequency (often nocturnal), increased rescue-inhaler use, mild RR elevation
- **Mid** — sustained tachypnoea, audible wheeze, reduced peak flow (typically <80 % personal best, severe <60 %), HR elevation
- **Severe** — accessory-muscle use, falling SpO2 (typically late sign in children — they desaturate later than adults), inability to complete sentences, silent chest

NICE NG80 (asthma diagnosis & monitoring), GINA paediatric (2023), and AAP guidance all converge on multi-component monitoring.

Bios substrate:

- SpO2 + HR are present
- RR is sleep-only on most consumer wearables (Apple Watch, WHOOP estimate during sleep; Fitbit doesn't continuously)
- Peak flow has no ingestion path — peak-flow meters are cheap (~$20) and the value is the canonical paediatric asthma at-home metric; a manual-entry `PEAK_EXPIRATORY_FLOW` (L/min) MetricType is the obvious addition
- Cough-count is `[planned]` in [DATA_MODEL.md](../DATA_MODEL.md) but unimplemented; microphone-based cough detection (Hyfe, ResApp) is the relevant tech
- [`AIR_PM25`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L138) + [`AIR_VOC`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L139) + [`AIR_CO2`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L140) give Bios a *trigger-correlation surface no clinical EMR has* — paediatric asthma is uniquely environment-driven

A `paediatric_asthma_exacerbation` pattern in [`RespiratoryExacerbationPatterns.kt`](../../android/app/src/main/java/com/bios/app/alerts/RespiratoryExacerbationPatterns.kt), age-banded RR, peak-flow drop, SpO2 corroborator, and an environment-trigger correlation pull-side view would be a high-yield paediatric primary-care addition. The peak-flow manual-entry surface is the prerequisite.

### 2.10 Neonatal monitoring — correctly out of scope

The consumer infant-monitor category (Owlet Smart Sock, Snuza, Nanit Breathing Band, Sense-U) is uniquely controversial:

- **AAP** has issued formal statements (2017 — Bonafide et al.; 2018 — Boundy et al.; 2021 — Choi et al.) warning against routine use; the core findings are inaccurate alarm thresholds, parental anxiety, ED visits triggered by false alarms, and substitution for evidence-based safe-sleep practices.
- **FDA** has taken enforcement action against several products marketed with disease-prevention claims (Owlet FDA warning letter 2021 for marketing as a medical device without clearance).
- **Cochrane review** (Strehle 2012, updated 2020) found no evidence that home cardiorespiratory monitors prevent SIDS / SUDC / BRUE.

Bios should not enter this category. The manifesto posture provides the structural argument:

- **"Silence is a feature"** — Bios does not generate alarms designed to drive engagement or false reassurance
- **"Instrument, not coach"** — Bios does not push behavioural prescriptions (Owlet's UI nudges include sleep-position recommendations that conflict with AAP back-to-sleep guidance)
- **"No false reassurance"** — implicit in the alert-content policy

The recommendation is to **explicitly document this scope decision**: Bios does not claim to detect SIDS, SUDC, BRUE, apnoea-of-prematurity, or any infant cardiorespiratory event, and parents looking for that should be directed to a clinically-prescribed event recorder (the only category with evidence of clinical utility, in narrow at-risk populations — Brockmann 2015).

The transcutaneous-bilirubin / jaundice case is genuinely interesting — smartphone-camera-based bilirubin estimation (BiliCam, Picterus, the Aceso device) is an emerging research area for paediatric jaundice screening in the first 5 days of life, validated in low-resource settings (Inamori 2023; Aune 2020). This is a future capability rather than a current gap; flag and defer.

### 2.11 Adolescent mental health and eating disorders — sensitive surfacing

Adolescent depression and anxiety rates have risen sharply post-COVID:

- CDC YRBSS 2023 — 42 % of US high-school students reported persistent sadness or hopelessness; 22 % seriously considered attempting suicide
- Suicide is the second leading cause of death ages 10–14 in the US (CDC WISQARS)
- Eating-disorder presentations have doubled at paediatric hospitals (Trafford 2022 BMJ Open analysis)

The biomarker signatures of relevance:

- **Adolescent depression** — sleep disruption (often hypersomnia in adolescents, distinct from adult insomnia), activity decline, social-withdrawal proxies (typing cadence per [`TYPING_CADENCE`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L215) if W2F is paired, screen-time / phone-use patterns)
- **Anorexia nervosa / atypical anorexia** — bradycardia (often 30s–40s on inpatient admission per Mehler 2018), hypothermia, orthostatic intolerance (ΔHR ≥35 bpm on standing), prolonged QTc, reduced HRV, hypotension, *and* weight trajectory crossing percentile lines downward (per Gap #2)
- **Bulimia / purging behaviours** — less detectable from wearable substrate; hypokalemia from purging is a lab finding not in the current Bios biomarker set, dental erosion is clinical exam

The manifesto framing is unusually well-suited here: no scores, no streaks, no body-shaming language, data-only, pull-side, owner-asks. This is the framing the SAMHSA / NEDA / AAP eating-disorder community has been *asking* consumer tech to adopt for a decade.

The specific concrete fix: the [`bradycardiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L157) explanation text currently names "highly trained endurance athletes" and "documented rate-control medication" as the differentials. **For an adolescent owner with a downward-trending weight percentile, eating disorder is a major differential and the URGENT alert should not silently exclude it.** Adding a paediatric-adolescent-specific eating-disorder differential note (carefully — not a diagnosis, not body-shaming, just "in an adolescent, sustained resting heart rate at this level can also indicate medical complications of restricted eating; discussing with a paediatrician or adolescent-medicine specialist is appropriate") would be defensible.

Cross-reference [PSYCHIATRY_POV.md](PSYCHIATRY_POV.md) §2 for the broader mental-health framing, and §2.4 for the perinatal-psychiatry confidentiality parallels.

### 2.12 Family-based-care model — the entity-model question

Paediatrics is uniquely family-mediated. The clinical workflow is "parent brings child to clinic." The Bios single-owner data model assumes the device owner is the data subject; for paediatrics this is generally not true.

Options:

- **Option A: Paediatric is out of v1 scope; defer to a paediatric companion app.** Cleanest. The manifesto's owner-is-final stance stays intact. The paediatric layer is a separate concern.
- **Option B: Add a `Dependent` / `ChildProfile` concept** linked to the owner-account, with the device owner = guardian, the data subject = dependent. Adds substantial schema and UX complexity (per-dependent baselines, per-dependent screening cadence, per-dependent immunisation history, per-dependent consent surfaces) and forces a hard conversation about adolescent confidentiality (Gap #5).
- **Option C: Hybrid — `PhysiologyState.PAEDIATRIC_*` sub-bands on a single-owner profile, treating "this owner is a child whose parent helps manage the device" as the model.** Avoids the schema explosion; depends on a single-child-per-device assumption that breaks down for families with multiple children.

This audit does not recommend a choice — the choice is a product-strategy decision that depends on whether paediatric is a Bios target market or whether the architecturally-clean answer is to direct families to a paediatric companion. The point is to surface that the choice exists and that Gap #1 (age-banding) and Gap #4 (paediatric vaccines) are achievable under Option C without committing to Option B.

### 2.13–2.18 Lower-priority paediatric considerations

**2.13 Concussion / return-to-play (sports paediatric medicine).** Youth-sport concussion is the highest-volume paediatric mild-TBI presentation. The biomarker substrate Bios already collects — HRV (suppressed in subacute concussion per Hutchison 2017), exercise tolerance, sleep architecture — is partially relevant. Return-to-play protocols (CISG / SCAT5 consensus 2017; AAP 2018) are stepwise and would benefit from a pull-side `ConcussionRecoveryTrajectoryView`. Real but lower-priority; defer to a sports-medicine-specific scope.

**2.14 Sleep-architecture age differences.** Children sleep longer, with higher SWS proportion, longer REM, and earlier sleep onset; sleep needs are AAP-specified (4–12 mo: 12–16 h; 1–2 y: 11–14 h; 3–5 y: 10–13 h; 6–12 y: 9–12 h; 13–18 y: 8–10 h). Adult sleep-pattern interpretation against the existing 14-day personal baseline does not encode this; a paediatric sleep-band table parallel to the vital-sign band table (Gap #1) would be the natural fix. Paediatric OSA is also distinct (adenotonsillar > obesity-driven in children, often presents with behavioural rather than daytime-sleepiness symptoms — AAP 2012 OSA clinical practice guideline) and the existing AHI threshold (AASM ≥5 = mild) applies to adults; paediatric AHI ≥1 is the AASM threshold for OSA diagnosis in children. The [`SleepApneaPattern`](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt) would need a paediatric variant.

**2.15 Anaphylaxis convergence.** Paediatric anaphylaxis (food allergy, insect sting, idiopathic) is increasingly common and presents as sudden multi-system event: urticaria (no Bios signal), tachycardia + hypotension + hypoxia (Bios has these), wheeze + respiratory distress (RR elevation + SpO2 drop). The URGENT tier and convergence-reasoning are well-placed but no specific pattern exists; a `paediatric_anaphylaxis_emerging` pattern at URGENT (≥2 of: sudden HR step ≥30 bpm, SpO2 drop ≥3 %, RR rise ≥5 br/min within 30 minutes) is defensible. Epinephrine auto-injector use logging would close the loop; defer.

**2.16 Child safeguarding / data subpoena risk.** Bios is not a safeguarding tool. However, **unexplained-injury patterns** (frequent falls from the [`FALL_EVENT`](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L246) companion stream, malnutrition trajectories from Gap #2 growth tracking, sleep-disruption patterns) are areas where data could become evidence in a child-protection investigation. This parallels the post-Dobbs reproductive-data threat model: data that is *clinically informative* can also be *legally compelled*. The same isolation primitives that protect reproductive data (separate SQLCipher key, default-excluded from FHIR export) should be considered for paediatric data in jurisdictions where mandatory-reporting and family-court subpoena rules differ. This is a threat-modelling exercise, not a code change — but it should sit in the same threat-model document as the reproductive-data Dobbs analysis.

**2.17 ADHD passive-biomarker research.** Smartphone-based attention / impulsivity / hyperactivity passive biomarkers (Faedda 2016; Insel 2017 digital phenotyping; the Mindstrong / Beiwe lineage) have an emerging paediatric literature. None of this is diagnostic; the AAP 2019 ADHD clinical practice guideline still requires DSM-5 criteria assessment. Bios could surface relevant patterns to inform a parent's conversation with the paediatrician but should not claim diagnostic relevance. Flag, defer.

**2.18 Autism spectrum disorder biomarker research.** Wearable autonomic-pattern research in ASD (Goodwin 2019; Ferguson 2017) is genuinely early. Same caveat as ADHD — not diagnostic, sensitive surfacing, defer.

### Out-of-scope paediatric concerns (not gaps)

- **Bedwetting / enuresis, encopresis, toilet-training tracking** — no consumer wearable substrate; the AAP guidance is observational and questionnaire-based; not a Bios scope item.
- **Developmental milestone tracking** (rolling, sitting, walking, first words, M-CHAT-R/F for ASD screening, ASQ-3 for general developmental screening) — questionnaire / observation domain; the AAP Bright Futures schedule is the canonical source. Could fit a future `DevelopmentalMilestone` entity but is outside the wearable-biometric scope Bios is built for.
- **Vision and hearing screening** — Snellen acuity, audiometry, otoacoustic-emissions; specialised equipment, defer to clinical settings.

---

## 3. Manifesto / paediatric-ethics tension points

These are friction points where the manifesto's principles and paediatric clinical practice produce different answers. The point is to name where the choice has been made, not to argue the manifesto should retreat.

### 3.1 "Owner is final" vs adolescent confidentiality

Discussed in Gap #5. The manifesto framing is correct for the adult; it cannot remain correct without modification when the device owner and the data subject are different parties, which is the paediatric default. The cleanest resolution is a documented exception ("owner is final, except where the data subject is a minor and the data category is one that adolescent-medicine practice protects from parental access by default — reproductive, mental health, substance use, sexual health"). This is one of the few places the manifesto needs explicit prose to handle a population that wasn't in the original framing.

### 3.2 "Silence is a feature" vs paediatric early-warning

Silence is the right register for trend-based notices. For paediatric URGENT-tier emergencies — severe hypoxia in a 4-year-old, glucose <54 in an adolescent with T1D, PEWS-equivalent deterioration in an asthmatic — silence is *more dangerous* than in adults because paediatric clinical deterioration is faster, less verbally communicated by the patient, and more dependent on caregiver vigilance. The URGENT tier ([`EmergencyVitalPatterns`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt)) is the right place for paediatric-aware escalation; the gap is content (age-banded cutoffs per Gap #1), not philosophy.

### 3.3 "No false reassurance" — the Owlet-style failure mode Bios is structurally protected against

Bios's manifesto posture (no alarm-based selling, no engagement optimisation, no false reassurance, instrument-not-coach) is the **structural answer to the consumer infant-monitor failure mode** that AAP has been warning against for a decade. Worth explicit credit. The recommendation is to keep this posture and document it in any paediatric-scope conversation — it is a defensible competitive advantage against Owlet / Snuza / Nanit and against the inevitable next entrant.

### 3.4 Family-mediated care vs single-owner data model

Discussed in Gap #12. The manifesto assumes a single owner; paediatric care is family-mediated. The choice is whether to extend the data model to support dependents (Option B in Gap #12), defer paediatric scope (Option A), or accept a single-child-per-device approximation (Option C). This is a product strategy decision, not a manifesto failure.

---

## 4. What I would recommend, prioritised

**Tier A — clinical safety, ship before any paediatric marketing**

1. **Sub-band [`PhysiologyState.PAEDIATRIC`](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L38)** into the six PALS / EPLS age bands (Gap #1, §2.3). The architectural prerequisite for everything below. Adds owner-DOB capture and auto-band-transition.
2. **Add age-banded vital-sign band tables** parallel to the existing `BiomarkerBands` machinery (Gap #1). Mark current adult [`EmergencyVitalPatterns`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) entries with `excludedStates = PhysiologyState.PAEDIATRIC_ALL`. Create `PaediatricEmergencyVitalPatterns.kt` with band-aware cutoffs.
3. **Document the no-paediatric-marketing constraint** until Tier A items 1–2 land. Bios should not be promoted for paediatric use while adult URGENT cutoffs apply to children — the false-positive (and false-negative bradycardia) rate is unacceptable.

**Tier B — high-yield paediatric primary-care additions**

4. **Growth tracking** — add `BODY_HEIGHT` / `BODY_LENGTH`, `HEAD_CIRCUMFERENCE` MetricTypes; ship WHO + CDC LMS percentile tables; pull-side `GrowthChartView` (Gap #2, §2.2). This is the highest-leverage paediatric primary-care primitive.
5. **Paediatric ACIP / NIP / Green Book vaccine catalog** — extend [`VaccineCatalog`](../../android/app/src/main/java/com/bios/app/ui/immunisations/VaccineCatalog.kt) with the paediatric schedule (Gap #4, §2.4). Pairs with the screening-cadence engine the primary-care audit recommended.
6. **PEWS-shaped pull-side view** — render HR / RR / SpO2 / consciousness against age-banded thresholds with a 0–9 composite (Gap #7, §2.7). Mechanically straightforward once Tier A items 1–2 land.
7. **Paediatric T1D additions** — ISPAD TIR target, `paediatric_dka_emerging` ADVISORY pattern, age-modified symptomatic-hypoglycaemia threshold (Gap #8, §2.8). Substrate is in place; pattern work only.
8. **Paediatric fever logic** — NICE NG143 traffic-light age modifiers, `BODY_TEMPERATURE_CORE` metric with `measurementSite` payload field (Gap #6, §2.6).

**Tier C — important paediatric additions, more engineering**

9. **Paediatric asthma exacerbation pattern** — peak-flow manual-entry path, age-banded RR rules, environment-trigger correlation view (Gap #9, §2.9).
10. **Adolescent confidentiality model** — installation-type distinction, default-excluded data domains for adolescent owners, documented manifesto exception (Gap #5, §2.5). This is the ethically hardest gap and a substantive design conversation, not a one-PR change.
11. **Eating-disorder differential text** in the [`bradycardiaCritical`](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L157) explanation for adolescent owners (Gap #11, §2.11). Sensitive surfacing; small, careful text change.
12. **Paediatric OSA `AHI` threshold** — paediatric variant of [`SleepApneaPattern`](../../android/app/src/main/java/com/bios/app/alerts/SleepApneaPattern.kt) using the AASM ≥1 paediatric criterion (Gap #14, §2.14).

**Tier D — paediatric-relevant, longer-horizon**

13. Concussion / return-to-play trajectory view (Gap #13).
14. Paediatric anaphylaxis convergence pattern (Gap #15).
15. Child-safeguarding / paediatric-data threat-model document, parallel to the post-Dobbs reproductive-data analysis (Gap #16).
16. Family-based-care model decision — Options A / B / C in Gap #12. Strategy call, not engineering scope.

**Do not adopt**

- **SIDS / SUDC / BRUE / apnoea-of-prematurity claims of any kind.** Bios should explicitly remain out of the consumer infant-monitor category (§2.10). The Owlet / Snuza / Nanit failure mode is exactly what the manifesto's posture protects against; that protection is squandered if any product copy or any pattern title implies the kind of disease-prevention claim the FDA has been enforcing against.
- **A "child health score" or "growth grade" composite.** The DATA_MODEL guard against composed epigenetic age clocks applies equally here. Growth charts show components; clinicians and parents interpret them.
- **Push-side ADHD / ASD diagnostic-claim patterns** (§2.17, §2.18). The biomarker research is too early; the diagnostic standard is DSM-5 clinical assessment; pushing biomarker patterns as diagnostically suggestive would be exactly the over-claim consumer health tools repeatedly make in paediatric mental health. If any surface for these conditions is built, it must be pull-side, observational, and explicitly non-diagnostic.
- **Marketing Bios for paediatric use before Tier A items 1–2 land.** Restated for emphasis.

---

## 5. Summary line

> Bios is an architecturally clean adult-physiology instrument with the correct philosophical posture for the paediatric wearable category (instrument-not-coach, no alarm-based selling, no false reassurance) but with no paediatric-specific content layer yet built. The `PAEDIATRIC` PhysiologyState is enumerated, the URGENT tier is reachable, the immunisation primitive exists — but every emergency vital-sign cutoff is adult-shaped (a 2-month-old's normal HR of 130 fires URGENT, a neonate at 80 bpm in true bradycardia stays silent), no growth-tracking primitive exists, the vaccine catalog is adult-only, and adolescent confidentiality conflicts unaddressed with the manifesto's owner-is-final framing. None of these violate the manifesto; all of them are within the existing architecture. The prerequisite work is sub-banding [`PhysiologyState.PAEDIATRIC`](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L38) into the six PALS age bands and writing age-banded vital-sign tables — until that lands, Bios should not be marketed for paediatric use. Wearable paediatrics is a relatively immature field; Bios is well-positioned to enter it carefully if it chooses to, and well-positioned to defer it cleanly if it chooses not to.
