# Psychiatry Audit — Bios as Digital-Phenotyping Substrate for the Mental-Health Encounter

**Scope:** Bios viewed by a board-certified Western biomedical psychiatrist (general practice) with sub-exposure to mood disorders, addiction psychiatry, perinatal psychiatry, and consultation-liaison. The lens is the everyday psychiatric clinic — outpatient, inpatient C-L, the mood-disorders subspecialty visit, the OB-psych co-management note, the addiction follow-up. Not a regulatory review and not a forensic-psychiatry audit; forensic implications of any "passive surveillance of mental state" surface are flagged but out of scope here.
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** General psychiatry + mood disorders + addiction psychiatry + perinatal psychiatry + consultation-liaison.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [CompanionConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), [ScreeningCatalog.kt](../../android/app/src/main/java/com/bios/app/screening/ScreeningCatalog.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt). Skimmed for context: [MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md), [AFRICAN_TRADITIONAL_POV.md](AFRICAN_TRADITIONAL_POV.md) §3.4, [docs/audits/MEDICAL_SPECIALTIES_WORLDWIDE.md](MEDICAL_SPECIALTIES_WORLDWIDE.md) §1.5.

---

## A note before findings: tone

Psychiatry is the specialty in which most consumer wearable software is, in this auditor's clinical judgement, *net-harmful*. The pattern is consistent across the category: a "wellness score" that quantifies the unquantifiable, gamified streaks that operationalise obsessive-compulsive features in vulnerable users, mood-tracking gamification that recruits people with bipolar II into more meticulous self-surveillance than the diagnostic criteria require, sleep scores that drive orthosomnia, and dark-pattern subscription gating on tools the user has been told they need to feel safe. The wellness-app industry — Headspace, Calm, BetterHelp, Talkspace and their lookalikes — is partly an exception (the meditation tools are largely benign) and partly the rule (the algorithmic coaching is not).

Against this baseline, **Bios's manifesto-aware design is the most clinically defensible posture I have audited in this product class.** "Instrument, not coach." "Silence is a feature." The CI-enforced banlist in [AlertContentPolicy.kt:51-83](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L51-L83) — `streak`, `level up`, `daily goal`, `badge`, `leaderboard`, `you should`, `you haven't`, `your health score` — is institutionally remarkable. I have not seen another product in this size class that mechanically prohibits the exact phrasing patterns the gamification literature identifies as iatrogenic. The audit weights this heavily. Most of the gaps below are gaps in *coverage*; almost none are gaps in *posture*. Where the audit raises tension, it does so explicitly and prefers the manifesto answer over the conventional-product answer in nearly every case.

---

## Executive summary

Bios is best described, in psychiatry-field vocabulary, as a **privacy-preserving digital-phenotyping substrate**. The "digital phenotyping" framing (Insel 2017; the lineage of Mindstrong, BiAffect, Beiwe, RADAR-CNS) names exactly what Bios does in this space: passive smartphone and wearable data as candidate behavioural biomarkers for mental state. Bios's [`mental_health_correlate`](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) pattern operates squarely in that tradition — and, importantly, *cites the right literature*. BiAffect (typing cadence, npj Digital Medicine 2024) appears as a `ThresholdSource.LITERATURE` reference; Seoul National University's wearable-sleep/circadian mood-episode prediction work appears alongside it; Baglioni 2016 anchors the sleep-architecture rule; Koch 2019 anchors HRV; Schuch 2018 anchors activity; Wirz-Justice 2006 anchors circadian rhythm disruption. The pattern requires `minActiveSignals = 3` and explicitly states "This is a data observation, not a diagnosis." This is the framing a digital-phenotyping pipeline *should* have. Academic groups have written entire grant proposals to ship less.

Where Bios is clinically incomplete relative to a working psychiatrist's needs, ordered by clinical impact:

1. **No bipolar relapse-prevention pattern, despite having every input.** The single highest-clinical-impact use case for wearable psychiatry is bipolar relapse prediction — sleep loss with maintained or elevated activity is the most-validated prodromal signature for mania, and circadian phase shift is the strongest single predictor in the Seoul National University 2024 paper Bios already cites. The current `mental_health_correlate` pattern is **direction-agnostic and therefore unipolar-shaped** (sleep down, activity down, HRV down). It will miss the *inverse* signature (sleep down, activity up, typing cadence up) that is the hypomania/mania prodrome. The Seoul citation is in the references list but the *mania-direction* signal rule is not. This is a one-pattern addition with literature already in hand.

2. **The `mental_health_correlate` pattern is hard-coded with a sleep-disruption-anxiety mention in the alert text, but anxiety has no first-class pattern.** HRV depression is the most-validated wearable anxiety biomarker (Chalmers 2014; the meta-analytic literature is extensive). Panic attacks (sudden tachycardia + hyperventilation, characteristic 10–20 min envelope) are wearable-detectable. PTSD nightmares produce REM-period HR spikes that distinguish them from non-distressed REM. None of these has a dedicated pattern. The existing single pattern carries the entire mood-and-anxiety surface, and the alert text mentions anxiety only in a suggested-action paragraph.

3. **Substance-use detection is intentionally minimal and that is mostly correct — but the missing pieces are clinically load-bearing.** The Smokeless companion writes `TOBACCO_USE` / `CANNABIS_USE` / `*_CRAVING` events ([CompanionConditionPatterns.kt:135-227](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt#L135-L227)), and the `INTAKE` domain now carries `ALCOHOL_INTAKE` and `MEDICATION_INTAKE` ([MetricType.kt:218-242](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L218-L242)). What is missing from an addiction-psychiatry standpoint: an **opioid-respiratory-depression** detection path (the highest mortality signal in addiction medicine, and the one most amenable to wearable detection — sustained low respiratory rate + falling SpO2), a **stimulant-load cardiovascular** signature distinct from caffeine, and a **withdrawal-syndrome** signature (alcohol withdrawal: tremor proxy via accelerometer + tachycardia + sleep disruption + 24–72 h timing window). Bios is honest about scope (the Smokeless contract is dose-free; that is the right call) but the opioid-overdose case is the one place where "silence is a feature" can fail catastrophically and where the URGENT escalation pathway (now reachable per audit gap §2.1 and `EmergencyVitalPatterns`) should be wired through.

4. **No perinatal-psychiatry pattern, despite the perinatal infrastructure being in place.** The `POSTPARTUM` `PhysiologyState` ([PhysiologyState.kt:35](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L35)) and the isolated `ReproductiveDatabase` exist; the perinatal-psychiatry literature is unusually precise about *timing windows* for biomarker shifts (Edinburgh Postnatal Depression Scale validation; the 2-week, 6-week, 3-month, and 6-month windows that define the screening cadence in every developed jurisdiction's perinatal-mental-health pathway). Bios has no postpartum-depression-screening surface and no postpartum-mood-signature pattern. This is one of the highest-yield additions for the perinatal-psychiatry user, and the manifesto-aligned posture (owner-initiated, pull-side, never pushed) is exactly the right register for a population already over-surveilled by the perinatal-care system.

5. **No medication-context surface for the psychiatric pharmacopoeia, which is the specialty's single most-undisclosed class.** The general medication-annotation gap was raised in [MEDICAL_PROFESSIONAL_POV.md §2.5](MEDICAL_PROFESSIONAL_POV.md) and is now partially closed (anomalies append a `medsContext` line per [AnomalyDetector.kt:374-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L374-L378)). The psychiatric-medication-specific dimension *is not addressed*. SSRIs have characteristic initial-phase HRV effects (Kemp 2010) and discontinuation syndromes; antipsychotics produce metabolic syndrome over months ([WAVE biomarker patterns are in scope but not antipsychotic-tagged](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt)); lithium has a narrow therapeutic window with renal implications (eGFR is now in `MetricType`, but no lithium-aware pattern uses it); stimulants for ADHD elevate HR and BP. Bios's biomarker panel could catch the metabolic and renal sequelae if the medication context told it where to look.

6. **No suicidality-specific signal — and this is correct, but should be named explicitly.** The wearable-suicidality-detection literature (Coppersmith and colleagues, the BiAffect group's keyboard-dynamics work on suicidal ideation) exists but is ethically contested. Surveillance-of-suicidality has well-documented failure modes: false-positive escalations that traumatise users, false-negative reassurance that delays presentation, and the *iatrogenic* effect of being told an algorithm is watching for self-harm. **Bios's manifesto posture — `instrument, not coach`; `silence is a feature`; the AlertContentPolicy banlist — is exactly what a suicide-aware design should look like, and the absence of an auto-detection pattern is a feature, not a gap.** This audit's recommendation is that Bios should *continue* to avoid building this surface, and should explicitly document that decision so future contributors know it was deliberate.

7. **DSM-5-TR diagnostic conservatism is the right posture; the alert text is already there.** Bios consistently says "data observation, not a diagnosis" and routes the owner to a mental-health professional. This is the conservative answer DSM-5-TR's clinical-judgement-required language demands. Worth preserving as scope grows.

8. **Cultural concepts of distress are not modelled, and the alert language carries an implicit Western biomedical framing.** The African and Indigenous audits both raised this; from a consultation-liaison psychiatry standpoint working with diverse populations, the *idiom of distress* a patient uses is itself diagnostic. Bios's biomarker frame ("RHR +2σ for 48h") is post-cultural in a useful way (z-scores translate across cultures), but the explanation text ("If you're experiencing persistent low mood, anxiety, or loss of interest, speaking with a mental health professional can help") imports a Western symptom vocabulary. Not a defect — biomarker observation is not a transcultural psychiatry tool — but worth naming.

9. **Sleep is the right primary substrate for psychiatric monitoring, and Bios has already built it well; CBT-I integration would be the highest-leverage adjunct.** The sleep architecture, regularity, fragmentation, and apnea-passthrough infrastructure already in [MetricType.kt:82-101](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L82-L101) is, from the consultation-liaison standpoint, the strongest *psychiatry-adjacent* surface in Bios. Insomnia is both symptom and risk factor for nearly every Axis-I disorder; CBT-I is the first-line evidence-based intervention. A pull-side surface that lets the owner annotate sleep-difficulty episodes and surface them alongside the objective data — without prescribing CBT-I, without coaching, without scores — would meaningfully serve the population using Bios as adjunct data for a therapy relationship.

10. **PHQ-9 / GAD-7 are owner-administered, not algorithmic — and that distinction matters for whether they belong in Bios.** The `depression_screen` entry in [ScreeningCatalog.kt:108-113](../../android/app/src/main/java/com/bios/app/screening/ScreeningCatalog.kt#L108-L113) already lists PHQ-2/PHQ-9 at the USPSTF annual cadence. The current implementation is a *cadence prompt* (you are due for screening), not a *PHQ-9 delivery surface*. The audit's recommendation: if any self-administered instrument lands in Bios at all, it must be **owner-initiated, owner-scored, never auto-prompted, and never push-surfaced** — the manifesto's pull/push split is the right boundary. The PHQ-9 itself does not violate the "no scores" rule because the score is generated by the owner answering questions; it is not an algorithmic evaluation of the owner. That distinction is worth drawing in the contributing guide.

The remaining items below are detailed observations on lower-impact gaps and on the structural strengths that make Bios *meaningfully ahead* of the consumer mental-health category.

---

## 1. What Bios already does well, viewed psychiatrically

| Quality | Evidence | Why it matters in psychiatry |
|---|---|---|
| **CI-enforced banlist of gamification and judgment language** | [AlertContentPolicy.kt:51-83](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L51-L83) prohibits `streak`, `level up`, `badge`, `leaderboard`, `daily goal`, `points earned`, `your health score`, `you should`, `you must`, `you haven't`, `you missed`, `you failed` | This is the institutional safeguard most consumer mental-health products lack. The gamification literature (Cemiloglu 2020; Lewis 2014) repeatedly identifies these patterns as iatrogenic in mood-disorder and eating-disorder populations. A CI gate that fails the build is the right enforcement mechanism — soft "guidelines" do not survive product cycles |
| **Instrument-not-coach is operational, not aspirational** | Manifesto Principle 7; the push/pull split in [AlertContentPolicy.kt:9-31](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L9-L31) makes the distinction load-bearing in code | Most "coaching" products generate harm by issuing unsolicited evaluations. Bios's data-statement-only push surface ("RHR +2σ for 48h") is the register a psychiatrist would actually use in a chart note. The pull surface — where the owner asks — is where evaluation belongs |
| **Literature-anchored mood-correlate pattern** | [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) cites Baglioni 2016, Koch 2019, Schuch 2018, Wirz-Justice 2006, BiAffect 2024, Seoul Nat'l Univ 2024 — every signal rule carries a primary citation | Names the academic digital-phenotyping lineage explicitly. A clinician reviewing this code can audit each rule against the underlying paper, exactly as they would a clinical decision-support tool. This is unusual rigor for a wearable app |
| **Convergence requirement on the mood pattern** | `minActiveSignals = 3` of 7 signal rules; weights and time windows per rule | Single-signal mood inference is the bane of digital-phenotyping pipelines (a bad night's sleep is not a depressive episode). The 3-of-7 floor is the right epistemic posture — closer to a clinical interview than to a "your mood score is" output |
| **Reproductive data isolation extends to perinatal-mental-health threat model** | Separate `ReproductiveDatabase` with independent SQLCipher key and independent wipe; FHIR exporter skips `WOMENS_HEALTH` by default | The threat model for perinatal-depression data in jurisdictions that criminalise pregnancy outcomes (US post-Dobbs is the most cited case) is materially different from generic health data. Bios's existing architecture covers this without requiring a perinatal-specific feature |
| **Dose-free substance-use contract** | `TOBACCO_USE` / `CANNABIS_USE` are `MetricUnit.EVENT` (timestamp + opaque id, no dose, no brand, no method) per [MetricType.kt:219-223](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L219-L223) | Aligns with harm-reduction practice. The forensic and insurance-discrimination exposure of dose-level substance data is well-documented; Bios's minimum-necessary contract limits the damage of any compelled disclosure. This is what an addiction psychiatrist working in a US criminalisation context would actually want |
| **Silence-is-a-feature applied to follow-up reminders** | [AlertManager.kt:89-90](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt#L89-L90) schedules a single 24h follow-up; [cessationRecovery](../../android/app/src/main/java/com/bios/app/alerts/CompanionConditionPatterns.kt#L200-L227) is "surfaced once, with no follow-up reminders or progress tracking" | The "you have a 7-day streak" notification is a textbook gamification antipattern in cessation populations (relapse-shame loop). Bios's explicit non-celebration of cessation is the right call and reflects the addiction-psychiatry harm-reduction literature |
| **PhysiologyState includes postpartum** | [PhysiologyState.kt:35](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L35), with documentation noting "sleep fragmentation is normative for ~6 months" | A "sleep_disruption" alert in week 2 of a newborn is the kind of tone-deaf push that drives a perinatal-depression patient off a monitoring app. Bios's gating prevents this |
| **Owner sets state explicitly; nothing is inferred** | [PhysiologyState.kt:18-19](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L18-L19): "Manifesto guard: owner sets state explicitly. Bios never infers pregnancy from BBT or athleticism from RHR." | The auto-inference of physiological states from sensor data is the entry-point for the most discrimination-relevant inferences (pregnancy, mental-state, substance-use). Bios's refusal to infer is the right posture and should generalise |
| **No `MOOD_SCORE` composite** | The `MOOD_DRIFT_SCORE` is W2F-companion-produced and explicitly *drift*, not state. Bios itself produces no "mood score" | The "biological age" guard from `DATA_MODEL.md` (Bios never composes a longevity score) generalises to mental health. There is no Bios-produced "depression probability." The W2F companion can produce one for its own use, but Bios does not own the surface. This separation is correct |

These are not parity wins. Several of these are dimensions on which Bios is *meaningfully ahead* of the consumer mental-health-monitoring category — and where the alignment is *architectural* rather than cosmetic. The CI banlist alone is more institutional safety than most regulated medical software ships with.

---

## 2. Clinical gaps, ordered by impact

### 2.1 Bipolar relapse prevention — the highest-impact wearable-psychiatry use case, not yet expressed

Bipolar disorder is the diagnosis where wearable monitoring has the strongest evidence base for changing clinical outcomes. The pre-manic prodrome is *physiologically distinct from baseline* in ways the patient often does not yet recognise — and is the window in which a phone call to the prescribing psychiatrist averts a hospitalisation. The Seoul National University 2024 work in `npj Digital Medicine` is exactly this: wearable sleep and circadian features predict mood episodes (both manic and depressive) days in advance. Bios cites this paper. Bios uses circadian phase shift as a signal in the [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L440-L441) pattern. But the pattern only fires on signals *trending in the depression direction*: `SLEEP_STAGE` below, `HEART_RATE_VARIABILITY` below, `STEPS` below, `SLEEP_DURATION` irregular.

The *mania prodrome* signature is the inverse:

| Signal | Mania direction | Depression direction |
|---|---|---|
| Total sleep | Decreased (< 6h sustained) | Variable |
| Subjective energy and activity | **Maintained or increased** despite reduced sleep | Decreased |
| Step count | **Above** baseline | Below baseline |
| Typing speed / cadence | **Increased**, lower backspace rate | Variable; often slower |
| Circadian phase | Advanced or fragmented | Phase delay |
| HRV | Variable; can rise transiently | Depressed |

The key clinical distinguisher is the **sleep-loss-with-maintained-energy** dissociation. A depressed patient sleeps poorly *and* feels exhausted. A patient entering mania sleeps poorly *and feels productive*. The BiAffect group's typing dynamics work specifically identified this dissociation as detectable in keyboard cadence days before the patient or family notices behavioural change.

**Recommendation:** add a `mania_prodrome_signature` pattern as a sibling to `mental_health_correlate`. Signal rules (all literature-anchored already in Bios's citation library):

```kotlin
SignalRule(SLEEP_DURATION, BELOW, 1.5, 72, 1.5, ThresholdSource.LITERATURE,
    "Bauer et al. (2006) — reduced sleep with maintained energy is the most consistent mania prodrome"),
SignalRule(STEPS, ABOVE, 1.0, 72, 1.0, ThresholdSource.LITERATURE,
    "Merikangas et al. (2019) — increased activity precedes manic episode onset by days"),
SignalRule(TYPING_CADENCE, ABOVE, 1.5, 72, 1.5, ThresholdSource.LITERATURE,
    "BiAffect (npj Digital Medicine 2024) — increased typing speed + reduced backspace ratio is the mania-direction signal"),
SignalRule(CIRCADIAN_PHASE_SHIFT, IRREGULAR, 1.0, 72, 1.5, ThresholdSource.LITERATURE,
    "Seoul Nat'l Univ (npj Digital Medicine 2024) — circadian phase shift is the strongest single predictor of manic episodes"),
```

`minActiveSignals = 3`. Pattern should activate only when the owner has explicitly opted in (a `BIPOLAR_AWARE` flag in the same physiology-state surface), because the false-positive cost of a "you may be entering mania" alert in a unipolar or non-clinical user is exactly the kind of iatrogenic anxiety the manifesto guards against. With the opt-in, this becomes the single most clinically valuable pattern Bios could ship.

### 2.2 Anxiety disorders — HRV is the most-validated wearable biomarker; no dedicated pattern

HRV depression is the single best-validated wearable biomarker for trait and state anxiety (Chalmers 2014 meta-analysis; the GAD-and-HRV literature is large). Panic disorder produces a stereotyped acute envelope (sudden tachycardia, hyperventilation-driven respiratory rate elevation, peak in 10 minutes, resolution in 20–30 minutes). PTSD nightmares produce REM-period HR spikes that are distinguishable from normal REM autonomic activation (Mellman 2014).

The [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) pattern mentions "anxiety" in the alert text but does not have a signal rule shape that distinguishes anxiety from depression. The two are co-occurring often enough that this is forgivable, but the *panic attack* envelope is a discrete, detectable, often-undisclosed clinical event that a wearable can plausibly catch.

**Recommendation, in priority order:**

1. **`panic_envelope_signature`** — sustained HR > baseline + (whatever × 2σ) for 5–20 min, paired with respiratory rate elevation if available, with no preceding exercise context. Pull-side surface only — the owner asks "did I have a panic attack last Tuesday at 3pm?" and Bios shows the envelope. *Never auto-pushed* — a "we think you had a panic attack" notification is exactly the wrong register and would itself be panicogenic.
2. **`hrv_anxiety_correlate`** — sustained low HRV without inflammatory or infection co-signals over a 7-day window. Pull-side only, surfaced in the same review screen as the existing mood-correlate.
3. **REM nightmare detection** — deferred. The signal is real but the detection error rate on consumer wearables is currently high enough that this would generate more iatrogenic distress than clinical benefit. Revisit when consumer-grade EEG (e.g. Dreem-class) is in the ingestion pipeline.

### 2.3 Substance use and addiction psychiatry — the asymmetric-mortality cases

The Smokeless companion contract is clinically right: dose-free events, opaque IDs, no brand. The harm-reduction posture is correct. Two gaps from an addiction-psychiatry standpoint are clinically load-bearing:

**Opioid respiratory depression.** Overdose death in opioid use disorder is predominantly respiratory — the patient does not wake up from the sleep-onset of an overdose. Apple Watch's apnea detection (now FDA-cleared) and respiratory-rate continuous monitoring on Galaxy Watch and similar are the consumer-grade detectors that *can* catch a sustained respiratory-rate drop below ~8 breaths/min paired with falling SpO2. This is the single highest-mortality preventable event the wearable layer could address. The infrastructure exists: `RESPIRATORY_RATE`, `BLOOD_OXYGEN`, the `EmergencyVitalPatterns` URGENT escalation path. What is missing is the pattern.

**Recommendation:** an `opioid_respiratory_depression_signature` pattern in `EmergencyVitalPatterns` that fires on the combination, with `severityFloor = AlertTier.URGENT`. Naloxone-availability prompting is out of scope (that is companion territory — `RecoveryNet` or similar), but the URGENT alert with a "if you have naloxone, this is the time" suggestion in the manifesto-aligned data-statement register is admissible. This pattern is the one place where "silence is a feature" can fail fatally, and Bios should not be silent.

**Alcohol withdrawal.** AWS (alcohol withdrawal syndrome) has a well-characterised time course (6–24h tremor, 24–72h seizure risk, 48–96h delirium tremens). The biomarker signature is tachycardia + hypertension + tremor (accelerometer-detectable as a 6–12 Hz fine resting tremor) + insomnia + diaphoresis (skin-conductance correlate if available). The mortality of untreated severe AWS is non-trivial. A pattern that fires on the *combination* in a context where the owner has annotated recent heavy alcohol intake and now zero intake would be a meaningful addition.

**Recommendation:** a `withdrawal_syndrome_signature` pattern, *opt-in only* (owner annotates "I am attempting cessation"), that watches the autonomic and tremor signals over the 24–96h window. The opt-in is critical — auto-detection of withdrawal is the kind of inference that should not be made about a person without their consent.

**Stimulants.** Stimulant intoxication produces an HR + BP elevation envelope distinct from caffeine (the prescription-amphetamine envelope is longer; the recreational-stimulant envelope is sharper). The clinical use case is largely covered by the existing cardiovascular patterns — no specific addition recommended at this time. Cocaine-induced chest pain is a different category (the cardiology audit, if one is written, should cover it).

**Cannabis.** Acute HR elevation is well-described but rarely clinically actionable. The chronic-use neuropsychiatric effects (cannabinoid hyperemesis syndrome, persistent psychotic symptoms in vulnerable users) are not biomarker-detectable. The Smokeless companion's craving + use event contract is sufficient.

**TAC sensors (BACtrack Skyn, transdermal alcohol) and Bedtime AUD detectors.** Out of scope for v1 — none of these have a consumer-grade ingestion path. Worth a placeholder in `SourceType` if/when the hardware reaches Bios's adapter scope.

### 2.4 Perinatal psychiatry — postpartum depression is the highest-prevalence undiagnosed mood disorder Bios could meaningfully address

The perinatal-psychiatry literature is unusually precise about windows. EPDS screening is universally recommended at 6 weeks postpartum in nearly every developed jurisdiction's perinatal-care pathway; PHQ-9 supplements this. Postpartum depression has a peak onset 4–6 weeks postpartum, with a secondary peak at 12 weeks. Postpartum psychosis (rare but obstetric-emergency-grade) onset is typically 2 weeks postpartum. The `POSTPARTUM` `PhysiologyState` ([PhysiologyState.kt:35](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt#L35)) is the substrate for all of this; the corresponding patterns do not yet exist.

The wearable signature of postpartum depression is partly the same as the general depression signature (sleep architecture deterioration *beyond* the normative postpartum fragmentation; HRV depression; reduced activity), but with three clinical wrinkles:

1. **Baseline displacement.** The pre-pregnancy 14-day baseline is no longer valid; the postpartum baseline is its own thing. Bios's per-state baselining (when implemented per the `PhysiologyState` documentation's described follow-ups) is the right substrate.
2. **The "normative fragmentation" floor.** A 90-minute uninterrupted sleep stretch is *normal* for the parent of a 6-week-old. The pattern needs a postpartum-specific irregularity threshold; the existing 14-day baseline will misclassify normal postpartum sleep as alert-worthy.
3. **The screening cadence.** EPDS at 6 weeks, again at 3 months, again at 6 months is the standard. The [ScreeningCatalog](../../android/app/src/main/java/com/bios/app/screening/ScreeningCatalog.kt) has `depression_screen` (PHQ-2/PHQ-9 annual) but no `postpartum_depression_screen` (EPDS at the 6-week and 3-month windows post-delivery).

**Recommendation:**

- Add `postpartum_depression_screen` and `postpartum_psychosis_awareness` entries to `ScreeningCatalog`, gated by `POSTPARTUM` `PhysiologyState`, with the standard EPDS windows.
- Add a `perinatal_mood_correlate` pattern in `ConditionPatterns` that operates only in `POSTPARTUM` state, with postpartum-adjusted thresholds. Convergence requirement should be high (3 of 5 signals minimum) to avoid the tone-deaf "you slept badly" alert at week 2.
- The screen prompts should be **pull-side only** — surfaced when the owner navigates to the postpartum tab — *never* push-notified. Population-level over-surveillance of postpartum women is a documented harm; Bios's manifesto posture is exactly right here.
- Postpartum psychosis is a genuine psychiatric emergency. The `URGENT` tier could reach it via sleep deprivation + dramatically increased activity + circadian disruption in the 2-week postpartum window (the same signature as the bipolar mania prodrome in §2.1, with the postpartum context as an amplifier). This is the one perinatal-mental-health surface where push notification is admissible, because the consequence of undetected postpartum psychosis includes infanticide and suicide.

### 2.5 Psychiatric medication context — the most-undisclosed-to-physician class in medicine

The general medication-annotation infrastructure exists ([AnomalyDetector.kt:374-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L374-L378)). The psychiatric-medication-specific overlay does not. From the clinic, the patterns that recur:

| Drug class | Wearable-visible signature | Pattern that fires falsely without context |
|---|---|---|
| SSRIs (initial phase) | HRV depression, sleep architecture changes, transient activation | `mental_health_correlate`, `recovery_deficit` |
| SSRIs (discontinuation) | Dizziness, "brain zaps", sleep disruption | `recovery_deficit`, `sleep_disruption` |
| SNRIs | HR + BP elevation, sweating | `cardiovascular_stress`, `hyperthyroid_signature` |
| Tricyclics | QTc prolongation (not directly visible), orthostasis | `fall_orthostatic_pattern` |
| MAOIs | Hypertensive crisis on tyramine; serotonin syndrome on interaction | Currently unreachable signal |
| Lithium | Tremor, thirst, polyuria, renal load (eGFR drift over years) | No pattern currently |
| Valproate / lamotrigine | Weight gain (valproate), rash (lamotrigine, life-threatening if SJS) | `metabolic_drift`; rash is unreachable |
| First-generation antipsychotics | Bradykinesia (accelerometer), QTc, akathisia (paradoxical activity) | `recovery_deficit`, false positive |
| Second-generation antipsychotics | Weight gain, metabolic syndrome, glucose elevation, sedation | `metabolic_drift` will fire correctly but without context |
| Clozapine | Agranulocytosis (WBC; in biomarker panel), myocarditis (HR + chest sx) | `chronic_inflammation` partial |
| Stimulants (ADHD) | HR + BP elevation, sleep disruption | `cardiovascular_stress`, `sleep_disruption` |
| Benzodiazepines (acute) | Sedation, reduced activity, reduced respiratory rate (overdose) | `recovery_deficit` (false positive in therapeutic use), `opioid_respiratory_depression_signature` (true positive in overdose) |
| Benzodiazepine withdrawal | Tremor, tachycardia, seizure risk | `withdrawal_syndrome_signature` (§2.3) |

**Recommendations:**

1. **Psychiatric-medication tagging in the existing medication-annotation surface.** When the owner annotates an SSRI, antipsychotic, mood stabiliser, stimulant, or benzodiazepine, the explanation builder should append the medication-context line with the psychiatric-class-specific framing. The infrastructure exists; the class table does not.
2. **Lithium-aware monitoring**, since lithium has the clearest wearable-and-biomarker monitoring case: tremor (accelerometer) + thirst (self-report) + polyuria (self-report) + eGFR ([MetricType.kt:196](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L196)) drift over years + TSH ([MetricType.kt:154](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L154)) drift (lithium-induced hypothyroidism). A pull-side "lithium-monitoring panel" surface would be high-leverage for this owner cohort and aligns naturally with the FHIR lab-import pathway.
3. **Antipsychotic metabolic monitoring**, since the second-generation antipsychotics produce a stereotyped metabolic-syndrome trajectory (weight gain at 3 months, fasting glucose elevation, lipid derangement) that is well-characterised and currently caught by `metabolic_drift` *without context*. Adding the antipsychotic tag would make the alert actionable for the prescriber.
4. **Akathisia awareness**, since it is a clinically common, distressing, and under-recognised side effect that produces a paradoxical activity-elevation signature in an otherwise psychomotorically-retarded depressed patient. A pull-side note in the activity-pattern surface would be sufficient.

### 2.6 Suicidality — Bios should NOT auto-detect, and this is correct

This section is short on purpose. The wearable-suicidality-detection literature (Coppersmith et al.; the BiAffect group's keyboard-dynamics work) is scientifically interesting and ethically contested. There is no consensus that algorithmic suicide-risk surveillance reduces suicide; there is reasonable evidence that it produces iatrogenic harm (the act of being told an algorithm is watching for self-harm is itself anxiogenic; false-positive escalations to emergency services have well-documented harms in marginalised populations; false-negative reassurance delays presentation).

**Bios's manifesto posture is, in this auditor's judgement, exactly what a suicide-aware design should look like:**

- "Instrument, not coach" — Bios does not evaluate the person.
- "Silence is a feature" — Bios does not push unsolicited "we think you may be at risk" alerts.
- The AlertContentPolicy banlist — Bios cannot produce the kind of judgmental language that would be most harmful.
- The reproductive-DB isolation generalises — psychiatric data, like reproductive data, has discrimination consequences if leaked.
- Crisis-resource information appears in the existing `mental_health_correlate` `risks` field ("If you are in crisis, contact a crisis helpline or emergency services immediately") — this is the right level of resource referral.

**Recommendation:** *do not build* an auto-detection surface for suicidality. *Document explicitly* in [docs/PRIVACY_ARCHITECTURE.md](../PRIVACY_ARCHITECTURE.md) or a sibling doc that this is a deliberate decision, not an oversight, so future contributors do not "fix" it by adding the feature. If a future companion app (akin to BiAffect itself) wants to build owner-initiated suicidality-surveillance, the producer-by-capture-surface rule applies — Bios is not the right surface, and the per-package allowlist limits the blast radius.

### 2.7 DSM-5-TR diagnostic conservatism — already correct, worth preserving as scope grows

DSM-5-TR diagnoses are *clinical* — they require a clinician interview, attention to functional impairment, exclusion of organic causes, and judgement about cultural context. They are not biomarker-derived. Wearable products that assert "you have depression" or "you have ADHD" or "you have anxiety disorder" are committing a category error and, depending on jurisdiction, potentially practising medicine without a license.

**Bios consistently respects this line.** The `mental_health_correlate` explanation reads: *"This is a data observation, not a diagnosis."* The `suggestedAction` reads: *"speaking with a mental health professional can help."* This is the conservative answer DSM-5-TR's clinical-judgement-required language demands.

**Recommendation:** preserve. As biomarker coverage expands, the temptation to compose patterns into "Bios thinks you might have X" will recur. The DATA_MODEL.md guard against composite scores (epigenetic-age clocks must not be combined into a "biological age") is the analogue; the same discipline should apply to diagnostic composition. Add the diagnostic-conservatism rule explicitly to the contributing guide alongside the AlertContentPolicy rules.

### 2.8 Cultural concepts of distress — implicit Western framing in alert text

The African and Indigenous audits both raised this; from a consultation-liaison standpoint working in diverse populations, the patient's *idiom of distress* — *nervios*, *ataque de nervios*, *susto*, *amafufunyana*, *khyâl cap*, *taijin kyofusho*, the somatised presentations of depression common in East Asian and several African clinical populations — is itself diagnostic. The DSM-5-TR Appendix on Cultural Concepts of Distress exists because biomedical psychiatry has had to accommodate these as persistently valid within their communities of use.

Bios's biomarker frame is, usefully, *post-cultural*: z-scores translate. RHR is RHR. HRV is HRV. The signal layer is not the problem.

The *explanation text* is the problem. The `mental_health_correlate` `suggestedAction` reads: *"Consider whether recent life changes, stress, or seasonal factors may be affecting your wellbeing. If you're experiencing persistent low mood, anxiety, or loss of interest, speaking with a mental health professional can help."* This imports a Western symptom vocabulary (*low mood*, *anxiety*, *loss of interest* — the SIGECAPS / depression-as-anhedonia frame). A patient whose idiom of distress is somatic ("my heart is heavy", "my body is hot inside", "my head is full") may not recognise themselves in this language and may dismiss the alert as irrelevant.

**Recommendation:** as `RegionConfigProvider` localisation expands (the African audit's gap §2.3 and §2.5), the alert-text overlay should include culturally-appropriate idiom for the mental-health-correlate suggestion. The biomarker observation stays the same; the bridge-to-help language changes. This is a localisation-layer task, not an engine-layer task — meaning it can be added without touching the detection logic. The diagnostic-conservatism principle (§2.7) protects against the failure mode of substituting a culture-bound diagnosis for the universal data statement.

### 2.9 Sleep and CBT-I — Bios's strongest mental-health-adjacent infrastructure

The sleep schema in [MetricType.kt:82-101](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L82-L101) (`SLEEP_STAGE`, `SLEEP_DURATION`, `SLEEP_LATENCY`, `SLEEP_EFFICIENCY`, `SLEEP_FRAGMENTATION_INDEX`, `WAKE_AFTER_SLEEP_ONSET`, `SLEEP_REGULARITY`, `SLEEP_APNEA_EVENT`, `AHI`, `CIRCADIAN_PHASE_SHIFT`) is, from a consultation-liaison psychiatry standpoint, the strongest *psychiatry-adjacent* infrastructure in Bios. Insomnia is both symptom and risk factor for nearly every Axis-I disorder. Sleep deprivation is a documented trigger for bipolar relapse. Nightmare disorder is a primary diagnostic entity in DSM-5-TR. Hypersomnolence is a clinical feature of atypical depression. The sleep substrate Bios already has is the right place to start.

CBT-I (cognitive behavioural therapy for insomnia) is the first-line evidence-based treatment for chronic insomnia. It involves sleep restriction, stimulus control, sleep-hygiene education, and cognitive restructuring around sleep beliefs. Several apps deliver it (CBT-i Coach is the VA reference implementation, Somryst was FDA-cleared as a digital therapeutic). Bios should **not** become a CBT-I delivery tool — that is therapy delivery, not biomarker observation, and crosses the manifesto's instrument/coach line.

**Recommendation:** Bios should be the *measurement substrate* for whatever CBT-I tool the owner chooses. The data shape is already correct (sleep diary entries map cleanly onto `SLEEP_DURATION`, `SLEEP_LATENCY`, `WAKE_AFTER_SLEEP_ONSET`); the FHIR export path is the right interchange surface. A pull-side "share sleep summary with my CBT-I provider" surface would be high-leverage. The audit explicitly does *not* recommend that Bios deliver CBT-I content.

### 2.10 PHQ-9 / GAD-7 and the "scores" question

Bios's "no scores, no streaks" posture is in apparent tension with the PHQ-9 — the most widely-used depression-screening instrument in primary care, which produces a 0–27 score and four severity bands. This tension is more apparent than real, and the distinction is worth drawing explicitly:

- **The PHQ-9 score is owner-administered and owner-scored.** The owner reads nine questions, answers them, and the algorithm sums their self-report. The instrument is not evaluating the owner *over their head*; the owner is evaluating themselves.
- **The PHQ-9 is the clinical standard for screening cadence**, cited in USPSTF guidance (already in `ScreeningCatalog` as `depression_screen`).
- **The PHQ-9 score is not a "Bios health score."** Bios is not composing a metric from sensor data; it is hosting an owner-completed instrument.

The same distinction holds for GAD-7 (anxiety), EPDS (postnatal depression), AUDIT-C (alcohol), DAST-10 (drug use), MDQ (bipolar screening), CAGE (alcohol), Y-BOCS (OCD).

**Recommendation:** if any self-administered instrument lands in Bios, it must be:

1. **Owner-initiated** — surfaced on the screening-cadence pull surface only.
2. **Owner-scored** — the algorithm sums what the owner enters; Bios does not infer answers.
3. **Never auto-prompted with the score** — the cadence prompt says "you are due for screening" and links to the instrument; it does not push the result.
4. **Never displayed in a celebratory or shameful register** — a PHQ-9 of 14 is a clinical finding to share with a provider, not a "your score is" gamified display.
5. **Documented in the contributing guide** as the carve-out from "no scores." The carve-out is: *owner-completed clinical instruments are admissible; algorithmic scores derived from sensor data are not.* This distinction is load-bearing and should be explicit.

### 2.11 Eating disorders — sensitivity-first; surveillance vs. supportive

The biomarker signatures of eating disorders are real and clinically diagnostic: bradycardia + hypothermia + bradypnoea in anorexia; restrictive-eating-plus-electrolyte derangement in purging behaviours; weight cycling with metabolic chaos in binge-eating disorder. Bios has the substrate (HR, temperature, weight, biomarker panel) to detect all of these.

It should be **very careful** how it surfaces them. The eating-disorder population is the population most at risk from gamified "tracking" features, and the line between supportive biomarker observation and pro-anorexia data-pornography is thinner than non-clinicians appreciate. A user with anorexia who can watch their weight, their RHR, and their body-fat percentage decline daily is being given exactly the tool the disorder wants. This is the failure mode that has produced regulatory action against several wellness-tracker products.

**Recommendation:**

- **No body-composition celebration.** The existing AlertContentPolicy banlist covers most of the failure modes (no streaks, no level-up, no goals). Add explicit prohibition on weight-and-body-composition framing that implies a target or direction.
- **`bradycardia_with_low_weight_signature`** pattern, opt-in via a hypothetical `EATING_DISORDER_RECOVERY` physiology-state flag — *not* auto-detected. The owner who is in recovery and wants the safety signal can enable it; the owner who is not should not be told "we think you might have an eating disorder."
- **Pull-side biomarker surface for the clinical team.** A patient in eating-disorder treatment whose care team wants to monitor renal function, electrolytes, RHR, and weight trajectory should be able to share that via the FHIR export. This is the existing doctor-in-the-loop path; no new surface needed.
- **Active hiding option for body-composition metrics.** The owner who is in active disorder and trying to recover should be able to turn off the visibility of weight, body-fat percentage, and body composition while still allowing them to be recorded for the care team. The infrastructure for this (privacy-tier visibility filtering) partly exists; the eating-disorder-specific use case justifies elevating it.

### 2.12 Catatonia, NMS, serotonin syndrome — true emergencies blocked by the medication-context gap

These are psychiatric medical emergencies:

- **Neuroleptic malignant syndrome (NMS):** fever + rigidity + autonomic instability + altered mental status, in a patient on antipsychotics. Mortality 10–20% if untreated.
- **Serotonin syndrome:** clonus + hyperthermia + autonomic instability, in a patient on serotonergic agents (often a combination — SSRI + MAOI, SSRI + tramadol, SSRI + lithium).
- **Catatonia:** stupor, mutism, posturing, waxy flexibility — biomarker-poor but mortality-relevant (lethal catatonia).

Bios has the sensor substrate to flag the autonomic + temperature components (`SKIN_TEMPERATURE` is already a metric type; `RESTING_HEART_RATE` is in scope; `CONSCIOUSNESS_LEVEL` is a metric type per [MetricType.kt:80](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L80) and unused by any current pattern — same finding as the African audit). What it does not have is the *medication-context* to know that a sustained temperature rise plus tachycardia in an antipsychotic-treated patient is NMS-until-proven-otherwise. This is the §2.5 medication gap, in its most acute form.

**Recommendation:** when the psychiatric-medication-class tagging (§2.5) ships, gate an `NMS_signature` URGENT pattern on the combination of (antipsychotic-class active medication + fever + tachycardia + altered consciousness). Same shape for `serotonin_syndrome_signature` on serotonergic-class. Catatonia is biomarker-poor and not detection-tractable from wearables; leave it.

---

## 3. Manifesto / clinical-ethics tension points

These are not gaps. They are places where the manifesto's principles produce different answers than conventional psychiatric-product practice, and Bios should be aware of which it chose.

### 3.1 "Silence is a feature" vs. bipolar relapse prevention

Silence is the right default for mood-pattern observation. It is *less obviously right* for a patient with known bipolar disorder entering the mania prodrome where 48–72 hours of advance warning could prevent hospitalisation, broken relationships, financial harm, or police involvement. The push-side justification used in [AlertContentPolicy.kt:18-31](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L18-L31) for "Bios-state push" (disconnect notifications) — *"silent failure isn't silence, it's failure"* — generalises here: a silent bipolar relapse is failure, not silence, *for the owner who has opted into this monitoring*.

The reconciliation is the opt-in. The mania-prodrome pattern (§2.1) is admissible *if and only if* the owner has explicitly enabled bipolar-awareness in a physiology-state-like surface. Default off. Opt-in is informed. The owner reads "this pattern fires when sleep loss is paired with maintained energy and circadian phase shift, which research associates with manic episode onset; do you want Bios to flag this?" and decides. This honours both the manifesto and the clinical reality. The same logic applies to the postpartum-psychosis case in §2.4.

### 3.2 "No scores" vs. PHQ-9

Discussed in §2.10. The reconciliation: owner-completed clinical instruments are not "scores in the prohibited sense." The prohibited sense is *Bios algorithmically rating the person*. The owner answering nine questions and the algorithm summing them is not Bios rating the person; it is the person rating themselves with a standardised instrument. This distinction belongs in the contributing guide so future code review knows where the line is.

### 3.3 "Never evaluate the person" vs. crisis intervention

The `mental_health_correlate` `risks` field already contains: *"If you are in crisis, contact a crisis helpline or emergency services immediately."* This is admissible — it is information about resources, not an evaluation of the person. Crisis-resource provision is *not* coaching; it is the same category as the regulatory disclaimer that already appears on every alert. Worth preserving as the standard footer wherever mental-health-correlate-class patterns surface.

### 3.4 Telepsychiatry and remote monitoring — Bios fits naturally as adjunct data

The COVID-era surge in telepsychiatry created a clinical context in which the prescribing psychiatrist is making medication decisions from a video call and has no objective data beyond patient self-report. Bios's FHIR export is exactly the data the telepsychiatrist would want: sleep trajectory since last visit, HRV trajectory, activity trajectory, medication-context annotation, mood-drift composite if W2F is installed. The architecture is already correct; the surface is the share-with-clinician path the doctor-in-the-loop audit (§MEDICAL_PROFESSIONAL_POV §3) describes.

**Recommendation:** no new feature. Document the telepsychiatry use case in the FHIR-export documentation so providers know what they can ask for.

### 3.5 Insurance discrimination and the US context

Mental-health data carries discrimination consequences in employment, insurance (life, disability, long-term care — health insurance is largely protected by ACA but other insurance lines are not), child custody (post-divorce evaluations), professional licensure (pilots, physicians, some attorneys), and immigration. The forensic-psychiatry literature on the discrimination consequences of disclosed psychiatric data is extensive. Bios's on-device-only architecture is the strongest structural defence available; the reproductive-DB isolation generalises. Worth noting explicitly in the privacy documentation: *psychiatric data is among the most stigmatising data classes; Bios's architecture is intentional protection.*

---

## 4. Recommendations, tiered

### Tier A — clinical safety and high-leverage additions

1. **Bipolar mania-prodrome pattern (§2.1).** Opt-in via a `BIPOLAR_AWARE` flag. Inverse-direction signal rules for sleep, activity, typing cadence, circadian phase shift. Literature already in the citation pool. The highest-impact wearable-psychiatry feature Bios could ship.
2. **Opioid respiratory-depression URGENT pattern (§2.3).** `RESPIRATORY_RATE` + `BLOOD_OXYGEN` sustained drop. `severityFloor = AlertTier.URGENT`. The one mortality-relevant addiction case where silence is failure.
3. **Psychiatric-medication-class tagging in the existing medication-annotation surface (§2.5).** SSRIs, antipsychotics, mood stabilisers, stimulants, benzodiazepines. Enables NMS / serotonin-syndrome gating (§2.12), denoises the existing pattern library, and unblocks lithium-aware monitoring.
4. **Explicit documentation of the suicidality-detection non-decision (§2.6).** Add to the privacy / contributing docs so future contributors do not "fix" the deliberate absence.

### Tier B — psychiatric coverage completeness

5. **Panic envelope and HRV-anxiety patterns (§2.2).** Pull-side only. Owner asks; Bios shows the envelope.
6. **Perinatal mental-health pattern + EPDS in `ScreeningCatalog` (§2.4).** Gated by `POSTPARTUM` physiology state. Postpartum-psychosis URGENT path the one push-admissible perinatal-mental-health surface.
7. **Alcohol-withdrawal pattern (§2.3).** Opt-in (owner annotates cessation attempt). Time-window-aware (24–96 h post-cessation).
8. **PHQ-9 / GAD-7 / EPDS / MDQ / AUDIT-C as owner-completed instruments in the pull-side screening surface (§2.10).** Carve-out from the "no scores" rule explicitly documented.
9. **Lithium-monitoring pull surface (§2.5).** Tremor + eGFR + TSH + self-reported polyuria. Leverages existing infrastructure end-to-end.

### Tier C — population coverage and integration

10. **Eating-disorder safety pattern (§2.11), opt-in only, with body-composition-visibility gating.** Recovery-oriented; never auto-detection.
11. **Sleep / CBT-I integration via FHIR export surface (§2.9).** No CBT-I content delivery — Bios is the substrate, not the therapist.
12. **Culturally-aware alert-text overlay for the mental-health-correlate suggestion (§2.8).** Localisation-layer work; couples with the African / Indigenous / Asian region-config gaps.
13. **Akathisia awareness in the activity-pattern pull surface (§2.5).** Documentation-only; helps the prescriber identify a common, distressing, under-recognised side effect.

### Do not adopt

- **Auto-detection of suicidality (§2.6).** The clinical-evidence base does not support it, the iatrogenic harms are real, and the manifesto posture is the correct one. Preserve.
- **A "mood score" or "mental health score" composite produced by Bios itself.** The W2F companion may produce drift composites for its own use; Bios should not produce a state score. The DATA_MODEL.md guard against composite scores generalises.
- **Push-notified mood evaluations.** The pull/push split in `AlertContentPolicy` is correct. Mood and anxiety surfaces belong on the pull side, with the bipolar-mania and postpartum-psychosis opt-in exceptions in §2.1 and §2.4.
- **CBT-I content delivery, mindfulness coaching, therapy-app feature parity (§2.9).** Bios is the instrument; the therapy app is the coach. The line is the manifesto.
- **Diagnostic claims of any kind.** "You may have depression" / "You may have ADHD" / "You may have bipolar disorder" — none of these phrasings should ever appear in Bios alert text. DSM-5-TR diagnoses are clinical. Bios reports physiology.
- **Streaks, badges, leaderboards, daily goals, gamification of any mental-health metric.** Already prohibited by `AlertContentPolicy`. Worth keeping CI-enforced.
- **Algorithmic emotional-state inference from facial expression, voice, or text content.** Not Bios's surface even if technically possible. The producer-by-capture-surface rule and the manifesto's never-evaluate-the-person principle converge here.

---

## 5. Summary line for the project

> Bios is, in psychiatric vocabulary, a *privacy-preserving digital-phenotyping substrate* whose manifesto-aware design — instrument-not-coach, silence-is-a-feature, the CI-enforced gamification banlist, the diagnostic-conservatism in alert text, the reproductive-DB-class isolation that generalises to psychiatric data, the dose-free substance contract — is the most clinically defensible posture I have audited in this product class. The mental-health-correlate pattern operates squarely in the academic digital-phenotyping tradition (BiAffect, Mindstrong, Beiwe, RADAR-CNS) and cites the right literature. The high-leverage additions are the bipolar-mania prodrome (inverse-direction sibling of the existing depression-correlate, opt-in, the single most impactful wearable-psychiatry feature), opioid-respiratory-depression URGENT (the one mortality-relevant addiction case where silence is failure), perinatal-mental-health gating on the existing `POSTPARTUM` physiology state, and psychiatric-medication-class tagging that unblocks NMS / serotonin-syndrome / lithium-monitoring. The audit's strongest non-recommendation is that Bios should *not* auto-detect suicidality — the manifesto posture is exactly correct here, and the absence is a deliberate feature that should be documented as such. None of these additions violate the manifesto; all of them are within the existing architecture; most of them are pattern-library work, not architectural work.
