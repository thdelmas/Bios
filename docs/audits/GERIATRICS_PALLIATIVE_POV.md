# Geriatrics & Palliative Medicine Audit — Bios at the End of the Lifespan and the End of Life

**Scope:** Bios's clinical reach as a longitudinal instrument for older adults, frail adults, and adults receiving palliative or hospice care, evaluated from the perspective of a panel of Western biomedical clinicians: a board-certified geriatrician, a hospital-based palliative-medicine physician, a hospice medical director, and a post-acute / skilled-nursing-facility (SNF) attending. The catalogue entries that anchor this audit are [MEDICAL_SPECIALTIES_WORLDWIDE.md §1.1](MEDICAL_SPECIALTIES_WORLDWIDE.md) ("Geriatric medicine") and §1.2 ("Palliative medicine / Hospice").
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** geriatric and palliative-medicine, AGS / BGS / EuGMS / NCP / AAHPM guideline-anchored. Not a long-term-care regulatory audit (CMS F-tag compliance, SNF MDS 3.0 reporting), not a hospice-benefit reimbursement audit, and not a bioethics consult. The panel reads Bios as an instrument the *older or dying owner* might hold themselves — or, with the owner's explicit consent, that a family caregiver or attending clinician might glance at alongside them.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [CompanionConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), [MedicationAnnotationRepo.kt](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt). Skimmed first: [MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md) — this audit re-frames gap #5 (polypharmacy) and gap #7 (no frailty / age modifier) with geriatric specificity. The neurology audit was consulted for the cognitive-decline section; the psychiatry audit for late-life mood and isolation.

---

## Executive summary

This is the rarest finding the panel has produced across any audit: **the manifesto's core ethical posture is, on the palliative-medicine half of this lens, already correct in a way no consumer health product we have ever reviewed achieves.** "Silence is a feature," "instrument, not coach," "never evaluate the person," and the CI-gated [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) banlist that prohibits "you should / streak / level up / daily goal" — every one of these reaches its purest justification at the bedside of a patient who is actively dying. A 92-year-old on hospice does not need a 14-day-baseline-deviation notification. They need the room to be quiet. Bios's default architecture *already produces that quiet*, which is more than can be said for any wearable product currently in market. The hospice medical director on the panel reads the manifesto and the alert-content policy and her note is: "I would let this on the bedside table."

The geriatric half of the lens is more conventional, and the gaps cluster around three structural facts about old age that Bios's adult-baseline architecture does not yet model:

1. **Frailty modifies everything.** Bios has the `FRAILTY_FLAG` enum value in [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) but it is unwired — no pattern reads it, no threshold modifier consumes it, no excludedStates set carves out the frail >75 cohort. The Fried phenotype (weight loss, exhaustion, low activity, slow gait, weak grip) and the Rockwood Clinical Frailty Scale (CFS) are the two operationally dominant frailty instruments in geriatrics, and *every* risk threshold in Bios's library should bend differently for a CFS-7 patient than for a CFS-3 patient. Treatment intensity, prognosis, escalation calculus, and what counts as an "URGENT" reading all change. This is the geriatric reframing of primary-care gap #7, and it is more consequential here than in any other specialty audit produced so far.
2. **Polypharmacy is not a side note — it is the single most-modifiable harm vector in the geriatric population.** Beers Criteria (AGS 2023), STOPP/START (O'Mahony 2023), and the ≥5-medications threshold above which adverse-event rates rise exponentially are core geriatric instruments. The [MedicationAnnotationRepo.kt](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt) surface that closed primary-care gap #5 is free-text and is consumed only as a one-line explanation appendage. It does not count medications, does not flag Beers-list anticholinergics or benzodiazepines, does not warn on a CFS-flagged owner being on ≥5 agents, and does not surface deprescribing candidates. This is a high-leverage extension of the surface that already exists.
3. **The owner-is-final framing struggles when cognition is failing.** The manifesto's core ethical primitive ("the owner decides") assumes a competent owner. Late-MCI, mild-to-moderate Alzheimer's, vascular dementia, Lewy-body dementia, and FTD all progressively impair the very faculty that consents — and Bios has no model for *when* the owner's earlier consent should bind their later, less-capacitated self. This is structurally analogous to the paediatrics audit's "owner who cannot fully consent" question, but in reverse: capacity is being lost, not gained. It is also the upstream framing for the advance-care-planning, POLST/MOLST, and dead-man's-switch design questions further down the audit.

Ordered by clinical impact across the panel's combined practice, the gaps are:

1. **`FRAILTY_FLAG` is declared but unused.** No threshold modifiers, no excludedStates, no pattern adjustment. The geriatric population is invisible to the pattern engine.
2. **No frailty-assessment surface.** No Fried phenotype capture, no Rockwood CFS, no FRAIL questionnaire (Morley 2012). The owner has no path to record "I am moderately frail" even if they want to.
3. **Polypharmacy is uncounted and Beers-naïve.** The medication annotation surface is a free-text list with no count threshold, no Beers/STOPP class flagging, and no deprescribing surface.
4. **No fall-risk *prediction* surface — only fall *detection* (via Virgil).** Gait variability, postural sway, the medication-class fall multiplier (benzodiazepines, opioids, anticholinergics, antihypertensives), and STEADI-style screening are all derivable from existing sensors; none are derived.
5. **No cognitive-screening surface or passive cognitive signal.** Bios collects every passive signal the late-MCI literature flags (typing cadence via W2F, sleep regularity, circadian phase shift, gait via accelerometer) and emits no cognitive pattern. No owner-administered MoCA / MMSE / Mini-Cog surface either.
6. **No advance-care-planning (ACP) surface.** No POLST / MOLST / DNAR storage, no goals-of-care field, no advance directive. URGENT-tier escalation language ("seek immediate medical attention or contact emergency services") is correct for the general adult population and clinically wrong for a documented DNAR/comfort-care patient.
7. **No hospice mode.** There is no mechanism to put Bios into a deliberately more silent posture for an actively dying patient — to suppress trend-based notifications while preserving symptom-burden tracking for the owner and caregiver.
8. **No symptom-burden capture (ESAS-r / IPOS).** Palliative medicine runs on owner-reported symptom scores. Bios captures `PAIN_SCORE` and `CONSCIOUSNESS_LEVEL` but no dyspnea, nausea, anxiety, fatigue, drowsiness, appetite, or wellbeing scale. ESAS-r and IPOS are the two standard instruments and neither has a home.
9. **No delirium screen.** Sleep-wake cycle disruption (the canonical delirium signature) is detectable from existing sleep architecture and circadian-phase metrics; no pattern flags acute confusion. Post-ICU and post-operative delirium are major hospital-discharge issues.
10. **No nutrition / sarcopenia trajectory pattern.** `BODY_MASS` and `LEAN_MASS` are in the schema. Unintentional weight loss ≥5 % in 6 months is one of the five Fried criteria and a standard hospice prognostic indicator. No pattern fires on it.
11. **No vision / hearing surface.** Sensory decline drives falls, social isolation, and apparent cognitive decline. Bios has no place to record audiometry, visual acuity, or sensory-aid use (hearing aids, glasses).
12. **No loneliness / social-isolation surface.** Holt-Lunstad's meta-analyses (2010, 2015) put social isolation at a mortality-risk magnitude comparable to smoking. Passive smartphone data (call frequency, SMS frequency, time outside the home from GPS) is in principle ingestible and currently is not.
13. **Living-alone / next-of-kin escalation pathway is partial.** Virgil's `CHECK_IN_MISS` event exists and the `check_in_decline_pattern` reads it. There is no "if URGENT fires and owner does not acknowledge within N minutes, alert next-of-kin" pathway, and no integration with the LETHE dead-man's-switch from a *health*-trigger angle.
14. **Bereavement / survivor-data path is undefined.** At owner death, what happens to the data? Default LETHE behaviour erases on dead-man's-switch — clinically and ethically correct in most cases, but the panel notes that some families want the data preserved for grief, for research consent honoured posthumously, or for a sibling who shares a hereditary risk.
15. **Geriatric vital-sign norms are not modeled.** Orthostatic hypotension is more common, Cheyne-Stokes breathing appears in HF and end-of-life, bradycardia is more often medication-induced. The pattern library treats these as universal-adult phenomena.
16. **Sleep-architecture changes of normal aging are not modeled.** Reduced SWS and fragmented sleep are normative for the >65 cohort; the existing `sleep_disruption` pattern can false-fire constantly in healthy older adults if `FRAILTY_FLAG` or an age-band modifier is not in effect.
17. **Pressure-injury, continence, and caregiver-burden surfaces are absent.** Largely outside wearable scope — flagged for completeness.

The panel's overall posture is **strong endorsement of the existing alert-content discipline, urgent request for frailty wiring, and a careful "do less, not more" recommendation for the palliative end of the lens**.

---

## 1. What Bios already does well, viewed from the geriatric and palliative bench

| Quality | Evidence | Why a geriatrician / palliative clinician cares |
|---|---|---|
| **The alert content policy is exemplary for end-of-life care** | [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) bans "you should / you need to / streak / level up / daily goal / achievement" — CI-enforced via `validateAll()` | A dying patient does not need to be told to "close your activity rings." Most wearable products are tone-deaf at end of life because their gamification was never designed to step back. Bios's banlist makes that tone-deafness *uncompilable*. The hospice medical director on the panel reads this file as the strongest single artefact in the codebase. |
| **"Silence is a feature" is a load-bearing manifesto commitment, not marketing language** | [MANIFESTO.md](../../MANIFESTO.md) Principle 7, reinforced in [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) header | Palliative care is unique in medicine for valuing *less monitoring* as a clinical good. The NCP (National Consensus Project) Clinical Practice Guidelines for Quality Palliative Care, 4th ed., frame the goal as comfort and presence rather than measurement. Bios's default-silent posture aligns with this. |
| **Personal baseline as the unit of comparison** | 14-day rolling baseline per metric; trend patterns are z-score gated, not population-norm gated | The geriatric cohort is heterogeneous: an 88-year-old marathon runner and a 72-year-old CFS-6 frail adult cannot share a threshold. Personal baseline is the only architecturally honest approach for this population, and Bios uses it natively. |
| **Medication-context line appended to every alert** | [AnomalyDetector.kt:376-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L376-L378) reads `medicationRepo.formatActiveContext()` | A bradycardia alert for a patient on metoprolol reads differently than the same alert without context. This is the single highest-yield denoising lever in geriatrics, and it is wired. |
| **`PhysiologyState.FRAILTY_FLAG` is at least *declared*** | [PhysiologyState.kt:37](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L37) | The architectural scaffolding to gate patterns on frailty exists. The wiring does not (see gap §2.1), but the schema awareness is there and the file header documents the intent. |
| **`PAIN_SCORE` and `CONSCIOUSNESS_LEVEL` are first-class manual-capture metrics** | [MetricType.kt:79-80](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L79-L80) — PAIN_SCORE (0-10 VAS/NRS/EVA interoperable), CONSCIOUSNESS_LEVEL (GCS canonical, AVPU-as-shortcut) | Pain and consciousness are two of the five universally-tracked palliative symptoms. That the schema documents EVA/VAS/NRS interoperability and GCS-as-canonical (with AVPU as a lossless input shortcut for ED triage) is the discipline a palliative-medicine fellow would write themselves. |
| **`FALL_EVENT`, `NEAR_MISS_FALL`, `CHECK_IN_MISS` are companion-bus first-class metrics** | [MetricType.kt:246-248](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L246-L248); [CompanionConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt) registers four fall- / check-in-derived patterns | Falls are the leading cause of geriatric injury mortality. That Virgil's stream is integrated and that `fall_orthostatic_pattern`, `fall_neurological_pattern`, `fall_hypoglycemia_pattern`, and `check_in_decline_pattern` exist is a real geriatric-grade integration — not just a "we have fall detection" tile. |
| **The `check_in_decline_pattern` cites cognitive-decline literature directly** | [CompanionConditionPatterns.kt:126-130](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt#L126-L130) — Riemann 2020 (insomnia → dementia risk), Koch 2019 (HRV → depression) | The pattern reads: check-in misses + irregular sleep + elevated mood drift + depressed HRV. That is the published prodromal pattern for late-life depression and mild cognitive impairment, and Bios cites it as such. It is the closest thing the codebase currently has to a cognitive-decline surface, and it is honest. |
| **Bradycardia and tachycardia URGENT thresholds carve out medication and athletic conditioning** | [EmergencyVitalPatterns.bradycardiaCritical](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) — explanation text names "documented rate-control medication" and "highly trained endurance athletes" | Medication-induced bradycardia is the single most common false-positive case in the geriatric population. The pattern explicitly acknowledges it. |
| **Hypertension pattern uses median + minimum-readings home-BP semantics** | [HypertensionPatterns.hypertensionEmerging](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) — `absoluteMinReadings = 3`, `absoluteWindowHours = 168`, median check | Geriatric BP swings — orthostatic dips after furosemide, post-prandial hypotension, white-coat hypertension — make single-reading triggers actively dangerous. The median + minimum-readings gate is the right shape and matches the ESH 2023 ABPM/HBPM convention. |
| **Reproductive-DB isolation generalises as a precedent for sensitive-domain isolation** | Independent SQLCipher key for `ReproductiveDatabase`, independent wipe, FHIR exporter skips WOMENS_HEALTH by default | The same architectural primitive is exactly what an "advance-directive / hospice / end-of-life" sensitive domain would need. The pattern is established. |

These are not parity wins. The first row — the alert-content policy at end-of-life — is **the single most clinically defensible artefact the panel has reviewed across any consumer health product**, full stop.

---

## 2. Geriatric- and palliative-specific gaps, ordered by impact

### 2.1 `FRAILTY_FLAG` is declared but unwired

[PhysiologyState.FRAILTY_FLAG](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L37) exists. Its file header documents the intent — "recovery patterns operate on different timescales" for the frail >75 cohort. No `ConditionPattern` references it in an `excludedStates` set. No threshold modifier consumes it. The pattern engine treats a CFS-7 frail adult identically to a CFS-3 robust older adult.

The clinical consequences:

- **The 14-day baseline calibrates to the *current* metabolic state.** For a robust older adult, that is a reasonable approximation of a stable physiology. For a CFS-6 frail adult, the baseline is itself drifting on the trajectory of frailty — and the personal-baseline z-score machinery flags day-to-day noise instead of the underlying decline. Frailty is a slow-rolling deconditioning, not an episodic event.
- **The `sleep_disruption` pattern false-fires constantly in the frail cohort.** Reduced SWS, increased awakenings, and fragmented sleep are *normative* aging (Mander 2017; Ohayon 2004); see also gap §2.16. The pattern's 72-hour minimum and 1.0σ threshold will flag noise as pathology in a frail owner.
- **The `cardiovascular_stress` pattern's exclusion set lists pregnancy and athletes but not frailty.** [ConditionPatterns.kt:207](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L207) — `excludedStates = PhysiologyState.PREGNANCY + setOf(PhysiologyState.POSTPARTUM, PhysiologyState.ATHLETE_HIGH_FITNESS)`. The frail-elderly absent presence is significant: the panel reads it as a tell that pregnancy was thought through but late-life physiology was not.
- **The `recovery_deficit` pattern's 14-day window is too short for the frail cohort.** Recovery debt accumulates over weeks-to-months in frailty, not 48-72 hours.
- **The URGENT-tier bradycardia (≤35 bpm) and tachycardia (≥130 bpm) cutoffs in [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) are correctly chosen for the general adult population.** They are *also* the cutoffs where the panel would expect a goals-of-care conversation to override the default "seek immediate medical attention" suggestedAction. A CFS-7 frail adult with documented DNAR who is dying of HF should not be told to call EMS for RHR 38; the family chose comfort. See gap §2.6.

**Recommendation:** wire `FRAILTY_FLAG` into the existing `excludedStates` and `severityFloor` machinery. The minimum useful change:

1. Add `FRAILTY_FLAG` to the `excludedStates` of `sleep_disruption`, `cardiovascular_stress`, `cardiorespiratory_deconditioning`, and `recovery_deficit` — these patterns describe deviations from a stable-adult baseline that frail older adults cannot maintain by definition.
2. Add a frailty-aware threshold band to the URGENT cutoffs in `EmergencyVitalPatterns` — not by relaxing them (a dangerously low SpO2 is dangerous regardless of frailty), but by *softening the suggestedAction* in a frailty-gated explanation appendix when `goalsOfCare = COMFORT` is also set (see gap §2.6).
3. Add explicit longer-window variants (`recovery_deficit_frail`, evaluation window 30 days rather than 14) for the deconditioning-class patterns where the timescale shift is the clinically relevant adjustment.

### 2.2 No frailty-assessment surface

The owner has no path to record their own frailty status. Three instruments are operationally dominant in geriatrics and should be implementable as owner-administered, pull-side surfaces — exactly the shape the manifesto endorses:

- **Fried frailty phenotype** (Fried 2001) — five criteria: unintentional weight loss ≥4.5 kg in past year, exhaustion (CES-D items 7 and 20), low physical activity (gender-stratified kcal/week), slow gait (≥6-7 seconds for 4.6m, gender- and height-stratified), weak grip (gender- and BMI-stratified). Frail ≥3/5; pre-frail 1-2/5. Bios *natively captures* weight (Withings, [BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L121)), activity, and partial gait (phone accelerometer); the missing inputs (exhaustion, grip) are short questionnaire items.
- **Rockwood Clinical Frailty Scale (CFS)** (Rockwood 2005, CFS-9 revised 2020) — single 1-9 ordinal, judgement-based, the dominant instrument in acute care, oncology, and the UK NHS. The owner or a family caregiver can score it themselves.
- **FRAIL questionnaire** (Morley 2012, IANA) — 5-item screening: Fatigue, Resistance, Ambulation, Illnesses, Loss of weight. Lighter-weight than the Fried phenotype; appropriate for self-administration.

The output is a categorical frailty state that sets `PhysiologyState.FRAILTY_FLAG` (or a finer-grained `FRAILTY_MILD / FRAILTY_MODERATE / FRAILTY_SEVERE` extension), which then drives gap §2.1's pattern gating.

**Manifesto alignment:** all three instruments are owner-administered, pull-side, never inferred. Bios does not "decide" the owner is frail — the owner records it. The instrument is on the pull side; the pattern adjustment is on the push side. This is precisely the architecture the manifesto endorses for sensitive evaluative judgments.

### 2.3 Polypharmacy is uncounted and Beers-naïve

[MedicationAnnotationRepo.kt](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt) — the surface that closed [MEDICAL_PROFESSIONAL_POV.md gap #5](MEDICAL_PROFESSIONAL_POV.md) — is a free-text list of medication names. It is read by `AnomalyDetector` to append one explanatory sentence: "*Annotated current medications: metoprolol, atorvastatin.*"

For primary care, that is sufficient. For geriatrics, it is the floor of what is needed, not the ceiling. Polypharmacy in the geriatric population is the single most-modifiable harm vector — adverse-drug-event rates rise exponentially above ~5 medications (Maher 2014; Rochon 2021), and Beers Criteria (AGS 2023) plus STOPP/START (O'Mahony 2023 v3) define which classes are most-risky for whom.

Specific extensions that fit the existing architecture:

1. **Count medications and flag the polypharmacy threshold.** `medicationRepo.fetchActive().size >= 5` is the standard polypharmacy cutoff (and ≥10 is the "hyperpolypharmacy" cutoff in the WHO definition). A dedicated `polypharmacy_load` surface on the pull side (Settings or a new Geriatrics screen) is the manifesto-aligned shape.
2. **Lightweight Beers-class flagging.** A small static table mapping common generic / brand names to Beers-AGS-flagged classes — benzodiazepines (diazepam, lorazepam, alprazolam), strong anticholinergics (diphenhydramine, amitriptyline, oxybutynin), Z-drugs (zolpidem, eszopiclone), long-acting sulfonylureas (glyburide), skeletal muscle relaxants (cyclobenzaprine, methocarbamol), first-generation antihistamines, NSAIDs in CKD/HF, PPIs >8 weeks without indication. The owner sees "Annotated medication 'diphenhydramine' is on the AGS Beers anticholinergic list — geriatricians often discuss alternatives." Pull side, owner-asked, never pushed.
3. **STOPP/START-aware suggestedAction.** When a `fall_orthostatic_pattern` fires and the active medication list contains a Beers anticholinergic, the suggestedAction text gains a sentence: "*Recent literature on geriatric polypharmacy identifies anticholinergics as a frequently-reversible fall contributor — worth discussing with a clinician.*" This is data-statement language, not coaching language.
4. **Anticholinergic burden score.** The ACB scale (Anticholinergic Cognitive Burden, Boustani 2008) and ADS (Carnahan 2006) are simple sum-of-class-scores instruments. An ACB ≥3 is associated with cognitive impairment and falls. The score is computable from the existing free-text list once a class mapping exists.

None of this requires re-architecting the medication surface. It extends the existing repository with a static reference table and a small derived-metric pathway.

### 2.4 Fall *prediction* (vs. fall *detection*)

Fall detection — the Apple Watch crash-detection, Pixel fall-detection, and Virgil `FALL_EVENT` companion stream — is the *event-shaped* signal: the fall already happened. Fall *prediction* is the more clinically valuable surface: the owner is at elevated risk, and intervention (PT referral, deprescribing, vision correction, home modification) can prevent the next fall.

Bios has the substrate for fall-risk prediction:

- **Gait variability** is derivable from the phone accelerometer ([DATA_MODEL.md](../DATA_MODEL.md) lists `gait_symmetry` and `accelerometer_raw` as `[planned]`). Increased step-time variability is the canonical wearable signal for fall risk (Hausdorff 2007; Verghese 2009).
- **Postural sway** (from accelerometer / gyroscope) is the second derivable signal — Bischoff 2003 "timed up and go" derivatives.
- **Medication-class multiplier** — falls risk from benzodiazepines, opioids, anticholinergics, antihypertensives is well-quantified (Hartikainen 2007; Park 2015); the medication-annotation surface (gap §2.3) supplies the input.
- **Orthostatic hypotension** — already partially detectable from the existing `fall_orthostatic_pattern` BP signals; the standing-up postural transition is detectable from the IMU.
- **STEADI** (CDC Stopping Elderly Accidents, Deaths & Injuries) is the canonical primary-care fall-screening algorithm. The 3-question STEADI screen (fall in past year? unsteady? worry about falling?) is owner-administrable.

**Recommendation:** a `fall_risk_emerging` pattern (or, more honestly, a pull-side fall-risk surface) that combines gait variability trend + medication burden + age-band + STEADI self-report into an explanatory observation. Push-side activation only at high confidence; pull-side always available.

### 2.5 Cognitive-decline detection

This is the gap the panel debated longest. The arguments for and against a cognitive-decline pattern in Bios are both strong:

**For:** the passive signals in the late-MCI / early-Alzheimer's literature are exactly the signals Bios already collects. Typing cadence ([TYPING_CADENCE](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L215), BiAffect 2024) tracks cognitive decline as well as it tracks mood. Sleep regularity ([SLEEP_REGULARITY](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L95), Roenneberg 2019) is a strong dementia-risk predictor (Lim 2013; Spira 2018). Circadian phase shift ([CIRCADIAN_PHASE_SHIFT](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L101)) is a Lewy-body prodromal signal (Hu 2016). Gait slowing (derivable from existing IMU) predates clinical cognitive symptoms by years (Buracchio 2010; Verghese 2007). The `check_in_decline_pattern` already gestures at this combination but is framed as safety, not cognition.

**Against:** an "early dementia" notification is one of the highest-stakes false-positive risks in all of digital health. The owner who receives it and is *not* developing dementia has been catastrophically harmed; the owner who receives it and *is* developing dementia may not retain enough insight to consent meaningfully to what the alert says. The diagnostic gold standards (MoCA, MMSE, ACE-III, neuropsychological battery) require trained administration; the wearable signals are correlates, not diagnoses.

**Panel recommendation:** the manifesto-aligned answer is **pull-side cognitive-screen surfaces, no push-side cognitive notifications**. Specifically:

1. **An owner-administered Mini-Cog surface** (Borson 2000) — 3-item recall + clock-draw, takes 3 minutes, validated for primary-care screening. Score is recorded as a self-administered active-test reading (existing `ACTIVE_TEST` `ReadingKind` in [Enums.kt:44](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L44)).
2. **An owner-administered MoCA path** — copyright-encumbered but free for individual use; owner enters their own score after administering it themselves or with a clinician.
3. **No push-side `cognitive_decline` pattern.** The passive signals can populate a *pull-side* "cognitive context" surface that aggregates SLEEP_REGULARITY trend, CIRCADIAN_PHASE_SHIFT trend, TYPING_CADENCE trend, and check-in-miss frequency — but does not synthesise a "you may be developing dementia" judgment. That synthesis belongs to the owner and their clinician.

The `check_in_decline_pattern`'s existing framing ("This is a data observation, not a diagnosis") is the right register. Keep it. Do not extend it into push-side cognitive screening.

### 2.6 Advance-care-planning, POLST/MOLST, goals-of-care

This is the gap where the URGENT-tier escalation language collides with palliative-medicine reality. [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) suggestedAction texts read:

> "If accompanied by chest pain, shortness of breath, dizziness, fainting, or palpitations, seek immediate medical attention."

This is correct for the general adult population. It is clinically wrong, and ethically wrong, for an owner with documented DNAR / DNI / comfort-only goals of care. A hospice patient with RHR 130 from terminal restlessness should not be told to call EMS. A patient with a POLST form indicating "comfort measures only" should not have their wearable instructing them toward escalation.

**Recommendation:**

1. **Add a `GoalsOfCare` enum** (`FULL_CODE`, `DNAR`, `DNAR_DNI`, `COMFORT_ONLY`) — owner-set, stored in the privacy-sensitive domain alongside the reproductive-DB precedent. Default `FULL_CODE` (preserves current behaviour). The enum is *owner-set* — Bios never infers goals of care.
2. **Add a `ClinicalDirective` entity** that holds the owner-recorded existence of a POLST/MOLST form, an advance directive, a healthcare proxy, and DPOA-HC. Bios does not store the document — only the *acknowledgement* that one exists and where it is (free-text location).
3. **Gate the URGENT suggestedAction language on goals of care.** When `goalsOfCare = COMFORT_ONLY`, the URGENT-tier text replaces "contact emergency services" with "this reading reflects significant physiological stress. Comfort-care planning and provider contact may be appropriate per your documented goals." The reading is still surfaced; the escalation language adapts.
4. **Apply the same gating to companion-pattern URGENT escalation** (fall + low BP, fall + hypoglycemia) — the comfort-care patient who falls likely should not have EMS auto-dispatched if that is contrary to their goals.

This is, structurally, the same kind of *gate-on-owner-state* mechanism that `PhysiologyState.excludedStates` already implements for pregnancy and athletes. It generalises cleanly.

### 2.7 No hospice mode

Closely related to gap §2.6, but distinct: a deliberate **"hospice mode" toggle** that puts Bios into its most-silent posture for an actively dying patient. The toggle would:

- Suppress all trend-based push notifications (the entire `ConditionPatterns.all` push surface other than `severityFloor = URGENT` patterns, which themselves rewrite their suggestedAction per gap §2.6).
- Preserve all pull-side surfaces — the owner, family, or attending clinician can still navigate in and see vitals, sleep, recent readings.
- Preserve symptom-burden capture (gap §2.8) — pain, dyspnea, nausea remain owner- or proxy-loggable.
- Optionally surface a *very* small set of clinically-relevant end-of-life observations: change in respiratory pattern (Cheyne-Stokes, agonal), markedly decreased urine output (if entered manually), mottling (if entered manually), cessation of oral intake (linked to weight trajectory).

**Manifesto reading:** hospice mode is the *purest* expression of "silence is a feature." The manifesto already permits Bios-state push (disconnect notifications) as admissible because failure is not silence — hospice mode is the corresponding *owner-state* posture: comfort is not measurement.

The panel's palliative-medicine and hospice members argued this should be the **highest-priority single feature** in the recommendations. It is *one toggle, one suppression filter, one explanatory screen* — small in engineering effort, transformative in clinical posture.

### 2.8 No symptom-burden capture (ESAS-r / IPOS)

Palliative medicine runs on owner- or proxy-reported symptom scores. The two dominant instruments are:

- **ESAS-r** (Edmonton Symptom Assessment System – Revised; Watanabe 2011) — 9 items, each 0-10 NRS: pain, tiredness, drowsiness, nausea, lack of appetite, shortness of breath, depression, anxiety, wellbeing. The dominant Canadian and international palliative-care instrument.
- **IPOS** (Integrated Palliative care Outcome Scale; Murtagh 2019) — 10-item instrument including physical, psychological, social, and spiritual concerns. The dominant European instrument.

Bios has `PAIN_SCORE` ([MetricType.kt:79](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L79)) and that is the right shape — 0-10 NRS, interoperable across VAS/EVA/NRS. The pattern should extend:

- `DYSPNEA_SCORE` (0-10)
- `NAUSEA_SCORE` (0-10)
- `FATIGUE_SCORE` (0-10)
- `DROWSINESS_SCORE` (0-10)
- `APPETITE_SCORE` (0-10, inverted so high = poor)
- `ANXIETY_SCORE` (0-10)
- `WELLBEING_SCORE` (0-10)

These complete the ESAS-r symptom set. They are all `SELF_REPORTED` (or proxy-reported), all owner-controlled, all pull-side displayed.

For a palliative-medicine clinician, a trended ESAS-r curve over the last 14 days is the single most useful piece of patient-side data they can read. Bios's architecture is the right substrate for it.

### 2.9 No delirium screen

Delirium — acute, fluctuating confusion — is the single most underdiagnosed condition in hospitalized and SNF-resident older adults. It has wearable-detectable signatures: sleep-wake cycle disruption (the canonical signal, captured by Bios's existing sleep architecture and circadian-phase metrics), motor restlessness or hypoactivity (from the IMU), and HRV changes (autonomic instability is part of the delirium phenotype).

The standard screening instruments are CAM (Confusion Assessment Method; Inouye 1990) and 4AT (Bellelli 2014). Neither is wearable-derived — they require an observer. But the wearable signals can flag *risk* in the post-discharge / post-operative window when delirium is most common.

**Recommendation:** a `delirium_risk_pattern` (or, more honestly, an "acute confusion risk" pull-side card) that fires on the convergence of: sleep-wake disruption (existing `circadian_disruption` pattern), markedly decreased activity, increased nocturnal activity, and HRV instability — within 7-14 days of an owner-flagged hospital discharge or surgery. Pull-side, never push-side at first, with a clear "this is not a delirium diagnosis — discuss with caregivers and clinicians" framing.

### 2.10 Nutrition, sarcopenia, weight-loss trajectory

[BODY_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L121) and [LEAN_MASS](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L123) are first-class metrics. The clinical patterns that should fire on them in the geriatric population are absent:

- **Unintentional weight loss ≥5 % in 6 months** is one of the five Fried frailty criteria and a standard hospice prognostic indicator. The pattern is mechanically simple over `BODY_MASS` readings; no pattern currently fires.
- **Sarcopenia** is the loss of muscle mass and function. EWGSOP2 (Cruz-Jentoft 2019) defines it as low grip strength (recordable via owner input) + low lean mass (Bios has `LEAN_MASS` from Withings BIA) + low physical performance (gait speed, derivable from IMU).
- **Mini Nutritional Assessment (MNA)** — Vellas 1999, MNA-SF Rubenstein 2001 — is the canonical geriatric nutrition screen. 6-item short form, owner-administrable.
- **Swallowing / dysphagia** — outside wearable scope, but a recordable self-report flag would matter for the end-of-life cohort where loss of swallow is a hospice trigger.

A `weight_loss_unintentional` pattern firing on ≥5 % body-mass loss over 180 days is the highest-yield single addition.

### 2.11 Vision and hearing

Sensory decline drives falls, social isolation, and *apparent* cognitive decline (a patient who can't hear the question scores as confused). Bios has no surface for:

- Audiometry (owner can record audiology-clinic results manually)
- Visual acuity (Snellen, owner-administrable approximation)
- Hearing aid use (binary, owner-set)
- Glasses / contact-lens use (binary, owner-set)

These are low-engineering, high-clinical-yield owner-input fields. Their main value is appearing alongside fall-risk and cognitive-context surfaces as denoising context.

### 2.12 Loneliness and social isolation

Holt-Lunstad's meta-analyses (2010, 2015, 2017) consistently place social isolation at a mortality-risk magnitude *comparable to smoking 15 cigarettes per day*. Western geriatrics chronically underweights this relative to several non-Western traditions — the Hawaiian Hoʻoponopono, Māori Whānau, and African collective-decision frames (see [AFRICAN_TRADITIONAL_POV.md](AFRICAN_TRADITIONAL_POV.md), [OCEANIC_ARCTIC_POV.md](OCEANIC_ARCTIC_POV.md)) all treat relational health as inseparable from individual health. The panel takes the view that Bios should at minimum *not* collude with the Western tendency to invisibilize relational health.

Passive smartphone signals that correlate with isolation:

- Call frequency / duration (telephony API)
- SMS frequency
- Time outside the home (GPS, on-device only)
- Bluetooth-detected proximity to other phones (paired devices) — Apple's contact-tracing era proved this is implementable privately

The manifesto-aligned shape is *pull-side, owner-asked, never inferred-as-pathology*. The owner can navigate to a "social context" surface and see their own social activity trend. Bios does not tell the owner they are lonely.

UCLA Loneliness Scale (Russell 1996) 3-item short form is the canonical owner-administrable instrument.

### 2.13 Living-alone / next-of-kin escalation

The `check_in_decline_pattern` ([CompanionConditionPatterns.kt:106](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt#L106)) reads `CHECK_IN_MISS` events from Virgil. There is no "if URGENT-tier health alert fires and owner does not acknowledge within N minutes, contact next-of-kin" pathway from the *Bios* side. Lifeline / Philips Lifeline / GreatCall (Lively) products' core feature is precisely this escalation; Bios could implement it manifesto-aligned:

- Owner-set escalation contact (name, phone, relationship)
- Owner-set escalation policy (which URGENT patterns trigger escalation; default off)
- N-minute owner-acknowledgement grace window
- On non-acknowledgement, deep-link to phone dialer with the contact pre-populated — Bios does not auto-place the call (the owner could be unable to). LETHE integration: the LETHE dead-man's-switch already has a notification path that could be repurposed for health triggers.

### 2.14 Bereavement and survivor-data path

At owner death, what happens to the data? The LETHE dead-man's-switch default is destruction, which the panel endorses as the correct default. But two edge cases warrant a deliberate surface:

- **Hereditary risk handoff.** A first-degree relative may share BRCA1, HCM-associated variants, familial hypercholesterolemia, Long-QT genotype. Some families want a deliberate posthumous data handoff to siblings or children for exactly this reason.
- **Research donation.** Some owners want their de-identified longitudinal dataset to outlive them — for the same reason people donate bodies to anatomy.
- **Family grief.** Some families want to see what their loved one's last days looked like — when sleep changed, when activity dropped, when the trajectory shifted.

This connects to [OBGYN_POV.md] Dobbs-threat-model precedent and to the oncology end-of-life-data question. The right shape is a **pull-side, owner-set "posthumous data preference"** that the LETHE wipe respects — defaults to destruction, owner can opt into preservation paths with specified destinations.

### 2.15 Geriatric vital-sign norms

The pattern library treats vital-sign norms as universal-adult. Several geriatric-specific deviations:

- **Orthostatic hypotension** is more common (10-30 % of >65, Ricci 2015); the `fall_orthostatic_pattern` uses BP as a corroborator but does not have a standalone "OH detected" pattern.
- **Cheyne-Stokes breathing** appears in HF and in the dying phase. Derivable from respiratory-rate variability; no pattern flags it.
- **Bradycardia is more often medication-induced** (beta-blockers, CCBs, digoxin) — the medication-annotation surface helps, but a frailty + bradycardia + active-rate-control-medication composite would be more informative than the current single-pattern bradycardia firing.
- **Isolated systolic hypertension** is the dominant geriatric BP phenotype and the existing [hypertensionEmerging](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) pattern correctly does *not* require both systolic and diastolic elevation — credit to the design.

### 2.16 Sleep-architecture changes of normal aging

Reduced SWS, increased awakenings, earlier bedtime / earlier wake, fragmented sleep are *normative* for the >65 cohort (Mander 2017; Ohayon 2004 meta-analysis). The existing `sleep_disruption` pattern's thresholds (fragmentation index +1.0σ over 72 h, sleep efficiency <85 % for 5+ nights) are calibrated to adult norms. In a frail older adult on the personal-baseline machinery, the baseline itself encodes the aged sleep pattern — so the pattern won't false-fire constantly *if* `FRAILTY_FLAG` is being read (gap §2.1). It *will* false-fire constantly if the cohort is frail-elderly and the flag is unwired, which is the current state.

This is gap §2.1 viewed from a different angle and the recommendation is the same: wire the flag.

### 2.17 Pressure injury, continence, caregiver burden

These are outside wearable scope, flagged for completeness:

- **Pressure injuries** — the Braden Scale (Bergstrom 1987) requires bedside assessment; turning frequency is the modifiable variable. Not wearable-detectable.
- **Continence (urinary, faecal)** — sensitive, often family-mediated, no wearable substrate.
- **Caregiver burden** — Zarit Burden Interview (Zarit 1980) is the standard instrument. Bios's owner-is-final framing makes a caregiver-facing surface complicated, and the panel takes the view that *caregiver burden is the caregiver's clinical concern, not the patient's*. If Bios extended to caregiver-side support, it would be a sibling app (akin to W2F's relationship to Bios), not a feature inside Bios itself. Flagged here only because the sandwich-generation reality is large enough that the architectural decision should be explicit.

---

## 3. Manifesto / clinical-ethics tension points

These are *not* gaps — they are places where the manifesto's principles and standard geriatric / palliative practice produce different answers, and the panel wants to surface which Bios chose.

### 3.1 "Owner is final" vs. progressive cognitive decline

The manifesto's foundational primitive is owner autonomy. Late-MCI, mild-to-moderate dementia, and severe dementia progressively erode the cognitive substrate on which autonomy depends. At what point does an earlier-recorded preference (when the owner was competent) bind a later, less-capacitated self? At what point does a caregiver get read-access? At what point does the dead-man's-switch fire on cognitive grounds rather than physical?

These are *unsettled questions in medical ethics* and Bios should not pretend to settle them. The panel's recommendation is that Bios should:

- Capture the owner's *advance* preferences (gap §2.6) — including specifically a "data sharing preference if I lose capacity" clause.
- Not implement any "Bios decides the owner has lost capacity" pathway. Capacity assessment is a clinician judgment, not an algorithmic one.
- Permit the owner to designate a *trusted reader* (separate from a caregiver — the trusted reader gets pull-side access to designated surfaces, the owner controls which). The trusted reader is a *role*, not a "next of kin gets everything when Bios decides you're confused" automation.

This is structurally similar to the paediatrics question of an "owner who cannot yet fully consent." The two specialties bracket the lifespan of the consent question.

### 3.2 "Silence is a feature" and the dying patient

The manifesto principle reaches its purest expression here. **A patient on active hospice does not need a 14-day baseline deviation notification.** The default-silent posture is *more* correct at end of life than at any other time. Hospice mode (gap §2.7) is the operational expression of this. The panel notes — and this is rare across the audit corpus — that Bios's *default* behaviour for this population, even before any hospice-specific feature lands, is closer to clinical correctness than any product the panel has previously reviewed. The CI-gated [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) banlist is the load-bearing artefact.

### 3.3 "Free to all" vs. caregiver-facing products

Lifeline, Philips Lifeline, GreatCall / Lively, and the entire personal-emergency-response category are *designed for caregivers* — adult children buying for parents, family members buying for relatives. They are paid products, often subscription-based, and the caregiver typically holds the account.

Bios's owner-is-final framing makes a caregiver-account architecture difficult. The right answer per the manifesto is a *trusted-reader role* (per §3.1) the owner sets up while competent. The wrong answer is to retrofit a caregiver-primary mode that displaces owner control. The panel endorses the manifesto on this point and flags it only so the design constraint is explicit when the inevitable feature request arrives.

---

## 4. What the panel recommends, prioritised

**Tier A — clinical safety and ethical correctness, ship before any new feature**

1. **Wire `FRAILTY_FLAG`** into the `excludedStates` of `sleep_disruption`, `cardiovascular_stress`, `cardiorespiratory_deconditioning`, and `recovery_deficit` (gap §2.1). One-day change; closes the largest single false-firing source in the geriatric cohort.
2. **Add `GoalsOfCare` enum and gate URGENT-tier suggestedAction language on it** (gap §2.6). The URGENT-tier patterns in [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) and [HypertensionPatterns.hypertensiveUrgency](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) need to read goals-of-care before telling a comfort-care patient to call EMS.
3. **Ship a hospice-mode toggle** (gap §2.7). One toggle, one suppression filter, one explanatory screen. Highest clinical-posture leverage in the recommendations.

**Tier B — geriatric completeness, next quarter**

4. **Frailty-assessment surface** (gap §2.2) — Fried, CFS, FRAIL questionnaire as pull-side owner-administered surfaces that set `PhysiologyState.FRAILTY_FLAG`.
5. **Polypharmacy count + Beers-AGS class flagging** (gap §2.3) — extends the existing [MedicationAnnotationRepo.kt](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt) with a small static reference table.
6. **Symptom-burden capture extending `PAIN_SCORE` to the ESAS-r set** (gap §2.8) — dyspnea, nausea, fatigue, drowsiness, appetite, anxiety, wellbeing as `SELF_REPORTED` 0-10 NRS metrics.
7. **Unintentional weight-loss pattern** (gap §2.10) — ≥5 % `BODY_MASS` loss over 180 days as a `weight_loss_unintentional` pattern; gateways to Fried frailty score and hospice prognostic flagging.
8. **`ClinicalDirective` entity** for advance directive / POLST / healthcare proxy acknowledgement (gap §2.6) — store *that* one exists and *where*, not the document itself.

**Tier C — population coverage, when the foundation is solid**

9. **Pull-side cognitive-screen surface** (Mini-Cog, owner-recorded MoCA) (gap §2.5). No push-side cognitive notifications.
10. **Fall-risk prediction surface** (gap §2.4) combining gait variability, medication burden, STEADI self-report.
11. **Delirium-risk pull-side card** (gap §2.9) gated on owner-flagged hospital discharge / surgery window.
12. **Loneliness / social-context pull-side surface** (gap §2.12) — passive social signals + UCLA-3 self-report.
13. **Next-of-kin escalation pathway** (gap §2.13) — owner-set contact, owner-set policy, owner-grace-window, dialer deep-link (no auto-call).
14. **Sensory-aid status owner-input fields** (gap §2.11) — hearing-aid use, glasses use, last audiometry, last visual acuity.
15. **Posthumous data preference** (gap §2.14) — owner-set destruction-vs-preservation, integrated with the LETHE dead-man's-switch.
16. **Sarcopenia / EWGSOP2 surface** (gap §2.10) tying `LEAN_MASS`, owner-recorded grip strength, and gait speed.

**Do not adopt**

- **A "biological age" or "frailty index composite" push surface.** The data-model guard against composing epigenetic clocks ([DATA_MODEL.md:106-112](../DATA_MODEL.md#L106-L112)) is exactly right and applies *more* strongly to a derived frailty score. Frailty is a *pull-side observation surface*, never a *push-side judgment*.
- **An algorithmic "Bios decides the owner has lost capacity" pathway.** Capacity assessment is a clinician role. Bios captures the owner's advance preferences, surfaces signals to clinicians, and gets out of the way.
- **A caregiver-primary mode that displaces owner control.** The trusted-reader role (§3.1) is the manifesto-aligned answer; a caregiver-account architecture is not.
- **Push-side cognitive-decline notifications.** The false-positive and false-comfort costs are both catastrophic. Pull-side only.
- **Auto-dispatching EMS on URGENT-tier patterns.** Owner deep-links to a dialer; Bios does not place the call. This is the same shape as the manifesto's existing refusal to take action *on* the owner without explicit consent.
- **More notifications at end of life.** The panel cannot stress this enough. Silence is the feature. Hospice mode is the operational expression.

---

## 5. Summary line for the project

> Bios's manifesto-level commitments — "silence is a feature," "instrument, not coach," the CI-gated alert-content banlist — are the most clinically correct posture the panel has reviewed for the palliative half of the geriatric / palliative-medicine lens. The geriatric half needs three things: wire the `FRAILTY_FLAG` that is declared and unused, extend the medication-annotation surface to count polypharmacy and flag Beers-AGS classes, and add a `GoalsOfCare` gate so URGENT-tier escalation language stops telling comfort-care patients to call EMS. Add a hospice-mode toggle and the ESAS-r symptom-burden set, and Bios becomes the rare consumer-grade health instrument a hospice medical director would let on the bedside table.
