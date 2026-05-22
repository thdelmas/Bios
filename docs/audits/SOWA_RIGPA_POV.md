# Sowa Rigpa Audit — Bios as Instrument, Read by an Amchi

**Scope:** Bios's clinical reach evaluated through the lens of Sowa Rigpa (Tibetan medicine) as practised in the *Gyud Zhi* (Four Tantras) tradition — the diagnostic, constitutional, environmental, and behavioural framework taught at institutions such as Men-Tsee-Khang (Dharamsala) and the Central Institute of Higher Tibetan Studies, and in living rural practice across Ladakh, Sikkim, Bhutan, Mongolia, Nepal, Buryatia and Kalmykia.
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Sowa Rigpa / Tibetan medicine. The reviewer is an amchi reading the code to decide whether the instrument has value at the bench of a clinic where the diagnostic trinity is *Lta-ba* (observation), *Reg-pa* (pulse), and *Dri-ba* (questioning), and where most patients live above 3,000 m.
**Auditor:** Claude (Opus 4.7)

This audit does not validate or invalidate Sowa Rigpa as a medical system; it asks the narrower question — what does the instrument record, what does it miss, and where do its principles already rhyme with what an amchi would expect of any honest measurement tool. The catalogue entry that frames this lens is [MEDICAL_SPECIALTIES_WORLDWIDE.md §6](MEDICAL_SPECIALTIES_WORLDWIDE.md).

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [CircadianConditionPattern.kt](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt).

---

## Executive summary

Bios is, in the language of the *Gyud Zhi*, a quiet instrument for *Lta-ba* — observation. It records what the body manifests, holds the record without interpreting the person, and speaks only when the deviation is durable. That posture is the part of Sowa Rigpa that translates *most* cleanly into software: an amchi at Men-Tsee-Khang is trained to look first (tongue, urine, complexion, gait), to touch second (pulse), and to ask third (history, diet, sleep, season, place) — and to refrain from pronouncing the imbalance until those three sources agree. Bios already enforces that "minActiveSignals" discipline in code ([ConditionPatterns.kt:145](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L145)) and its [AlertContentPolicy](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) explicitly prohibits the unsolicited evaluation of the person — a register an amchi would recognise as appropriate humility for an instrument that is not yet the practitioner.

What the instrument *cannot* do, and does not pretend to, is the rest of the *Gyud Zhi* — none of the three *Nyepa* (humours: *rLung* / *mKhris-pa* / *Bad-kan*) are modelled; pulse is captured only as rate; urine is absent entirely; the six Tibetan seasons do not modulate baselines; and the high-altitude physiology that frames most Sowa Rigpa clinical work is invisible to the code. These are real gaps, ordered below by their impact on a practitioner who might consider Bios as an adjunct in a Dharamsala or Leh clinic.

Ordered by clinical impact in a Sowa Rigpa setting:

1. **Altitude is not modelled anywhere.** SpO2 thresholds in [EmergencyVitalPatterns.spo2Critical](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L58-L80) cite WHO 2011 at sea-level cutoffs ("≤85 % indicates significant hypoxia regardless of altitude, age, or fitness"). That sentence is clinically *incorrect for Sowa Rigpa's catchment*. Healthy Ladakhi and Tibetan residents at 3,500–4,500 m commonly sit at SpO2 88–93 % at rest, and an acclimatised herder at 4,800 m may baseline at 84–86 % without pathology. The instrument would fire URGENT alerts on healthy patients in Leh, Lhasa, Thimphu and Ulaanbaatar, and would *miss* genuine deterioration in an owner whose baseline has silently dropped from 90 % to 84 %. This is the single most consequential gap for the geographies where Sowa Rigpa is practised.

2. **No model of the three Nyepa.** The whole diagnostic framework of the *Gyud Zhi* hinges on *rLung* (wind), *mKhris-pa* (bile) and *Bad-kan* (phlegm). Bios captures many of the substrate signals — HR, HRV, breath, sleep fragmentation, skin temperature, digestive correlates via glucose, BMI — but never composes them along the humoural axes an amchi reasons with. This is not a request that Bios encode Tibetan ontology; it is a note that the data flowing through the pipe could be presented in a humoural overlay on a pull-side surface (manifesto-aligned, owner-requested), and an amchi reading a printout from a patient could then orient quickly. The closest existing surface — the mental-health correlate pattern ([ConditionPatterns.kt:424-459](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459)) with its sleep + HRV + activity triad — already, by accident, resembles a *rLung*-disturbance signature in Sowa Rigpa pathophysiology.

3. **Pulse is captured as rate, not as the diagnostic surface it actually is.** *Reg-pa* — pulse diagnosis — is one of the three pillars of *Gyud Zhi* diagnosis, and the amchi reads pulse on six positions (three fingers on each wrist, light and heavy pressure), distinguishing constitutional pulses (*rLung-zhu*, *mKhris-zhu*, *Bad-zhu*), seasonal pulses (spring–liver, summer–heart, late-summer–spleen, autumn–lung, winter–kidney), and pathological pulses (floating, sinking, slippery, choppy, wiry, knotted, hidden, ...). The Bios data model captures `HEART_RATE`, `RESTING_HEART_RATE`, `HEART_RATE_VARIABILITY` and AFib irregularity ([MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt)), and that is the entirety of its pulse surface. PPG morphology — pulse-wave shape, dicrotic-notch position, augmentation index, arrival timing — is the *signal an amchi would find legible*, but it is currently discarded by every adapter (Health Connect, Oura, WHOOP, Garmin, Withings — all emit rate, HRV, sometimes BP but not waveform). This is a roadmap item ([ECG_WAVEFORM is `[planned]` in DATA_MODEL.md:45](../DATA_MODEL.md)), and the same case could be made for PPG morphology.

4. **No urinalysis surface (*chu-rtags*).** Urine diagnosis is the second diagnostic pillar after pulse in Sowa Rigpa: colour, vapour, smell, bubble size and persistence, sediment, surface film, all read in changing morning light. Bios models no urinalysis metric — neither a manual-entry surface for an owner to log self-observed urine characteristics, nor a structured field for a clinical dipstick result. This omission is shared by every consumer wearable, and is therefore not a Bios-specific failure, but in a Sowa Rigpa context it is the most conspicuous gap in the *Lta-ba* surface.

5. **No seasonal modulation.** Sowa Rigpa recognises six seasons (early winter / late winter / spring / early summer / late summer / autumn) rather than four, and the *Gyud Zhi*'s behavioural and dietary regimen (*spyod-lam*, *zas-spyod*) is anchored to them. *Bad-kan* accumulates in late winter and is provoked in spring; *mKhris-pa* accumulates in summer and is provoked in autumn; *rLung* accumulates in summer and is provoked in late summer and early winter. Bios's 14-day rolling baseline and the [CircadianConditionPattern](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt) attend to within-day rhythm but have no notion of where the owner is in the seasonal cycle. The same RHR drift in late winter and mid-summer reads differently to an amchi; to Bios it is one signal.

6. **No place / geography awareness.** Closely related to (1) and (5): Sowa Rigpa is fundamentally a geographic medicine. The body's *Nyepa* equilibrium is read against the *desa* — the place, with its altitude, dryness, wind, smoke, fuel, water hardness, and pastoralist or sedentary daily rhythm. Bios does not record place (and is *right* not to record GPS by default — that aligns with LETHE's threat model), but the absence of even an owner-set "altitude band" / "climate band" annotation means baselines drift without context. A pull-side, owner-set "I live at 3,500 m, cold-dry climate, wood-stove heating" annotation would let SpO2 floors, RHR floors, and skin-temperature baselines be interpreted correctly.

7. **No surface for moxibustion, bloodletting, golden-needle therapy, or precious-pill intake.** Sowa Rigpa has a rich pharmacy (*rin chen ril bu* — precious pills, mineral-based, taken on auspicious days) and a set of physical interventions (*me btsa'* / moxibustion, *gtar ga* / bloodletting, *gser khab* / golden-needle, *bsku mnye* / oil massage). Bios has no medication-intake annotation surface beyond the recently added `MedicationAnnotationRepo` ([AnomalyDetector.kt:34](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L34)) and no intervention-event surface. The amchi-side use-case would be: a patient receives *me btsa'* at point *spyi-bo* on Tuesday; Bios should be able to log that as an intervention timestamp and let the pulse/HR/HRV trace be read against it.

8. **The "instrument, not coach" posture aligns deeply, but the catchment of "the owner is final" reads slightly differently.** In Sowa Rigpa the practitioner's *bodhicitta* (compassionate motivation) is doctrinally load-bearing; medicine is taught as a path of supportive presence, not authority. Bios's manifesto refusal to push judgments rhymes with this. The friction point — and it is small — is that an amchi *does* offer evaluation when asked, drawing on the *Gyud Zhi*, and Bios's pull-side surfaces (the screen the owner navigates into) need to support being read by a practitioner the owner has invited in. The doctor-in-the-loop sharing flow ([ROADMAP.md current state](../ROADMAP.md)) — FHIR / encrypted export / QR / verbal — is the manifestly correct architecture, but the format the amchi receives is FHIR R4 LOINC, not anything legible in Sowa Rigpa's vocabulary. A pull-side, owner-triggered "amchi-view" rendering of the same data would close that gap without violating any manifesto principle.

The remainder of the audit notes where the instrument's existing posture already aligns with what an amchi would value, then walks the gaps in detail.

---

## 1. What Bios already does well, viewed through a Sowa Rigpa lens

| Quality | Evidence in code | Why it matters to an amchi |
|---|---|---|
| **Instrument, not coach** | [AlertContentPolicy.kt:51-83](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L51-L83) bans "you should / you need to / streak / level up" via a CI-gated test | The amchi's ethical training places motivation (*kun slong*) at the centre of practice — the instrument should not pre-empt the practitioner's role of evaluation. Bios refuses to do so by construction. This is the most important alignment in the codebase |
| **Convergence required before signal** | `minActiveSignals = 3` for [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127), `minActiveSignals = 2` for most patterns, `required = true` gating for biomarker patterns | Mirrors the *Gyud Zhi* discipline of not pronouncing an imbalance from a single sign. An amchi reading one anomalous pulse does not yet conclude *mKhris-pa* heat — observation, questioning, and a second pulse reading must converge. The code's reluctance to alert on a single signal is the same reluctance |
| **Personal baseline, not population norm** | 14-day rolling per-metric baseline; the cardiovascular-stress pattern uses z-scores from the owner's own HR, not a textbook range ([ConditionPatterns.kt:187-208](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L187-L208)) | Sowa Rigpa is constitutional — the same RHR is *rLung* in one body and *Bad-kan* in another. An instrument that compares the owner only to herself is the right primitive. A Ladakhi herder's RHR of 52 and a Lhasa shopkeeper's RHR of 70 are both "their normal," and Bios honours that |
| **Behavioural and dietary regulation is foregrounded** | Every condition pattern carries a `prevention` and `healing` field oriented to sleep, diet, movement, stress ([ConditionPatterns.kt:152-185](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L152-L185)) | *Spyod-lam* (conduct regimen) and *zas-spyod* (dietary regimen) are *the first line of treatment* in the *Gyud Zhi* — before pharmacy, before physical interventions. Bios's emphasis on lifestyle context as the first-order intervention rhymes precisely with that order of operations |
| **Silence is a feature** | [AlertContentPolicy.kt:13-25](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L13-L25) carves out push-side vs pull-side, and most surfaces are pull-side | Quiet observation is what an amchi *does* between the moments of pulse-taking. The instrument that does not chatter aligns with the practice of patient observation that frames Sowa Rigpa training |
| **Reproductive data is isolated** | Separate SQLCipher key, independent wipe, [reproductiveReadingDao](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L33) injection | Sowa Rigpa gynaecology (*mo nad*) is a distinct branch; the menstrual cycle pattern ([ConditionPatterns.kt:475-498](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498)) treats BBT, HRV-by-phase and sleep-by-phase as separately governed data. The isolation aligns with the cultural and clinical separation Sowa Rigpa already practices around reproductive complaints |
| **Mental health correlate pattern is, accidentally, a rLung pattern** | [ConditionPatterns.kt:424-459](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) cross-correlates sleep architecture loss, HRV depression, activity reduction, and circadian shift | In the *Gyud Zhi*, what Western psychiatry calls anxiety-with-insomnia-with-restlessness is the canonical signature of *rLung*-disturbance — wind-element imbalance manifesting as scattered sleep, racing pulse, dryness, agitation. Bios's `mentalHealthCorrelate` rule is, structurally, a *rLung*-disturbance detector. This is not an argument that Bios should *call* it that — only that the underlying physiology being tracked is recognisable to an amchi |
| **Owner is final** | Manifesto principle 7; [AlertContentPolicy](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) enforcement; FHIR export gated by the owner | The *Gyud Zhi*'s ethical chapters frame the practitioner as servant of the patient's discernment, not its replacement. Bios's owner-sovereignty posture is the same posture in software form |
| **Privacy by default, no monetisation** | SQLCipher at rest, AES-256-GCM for export, no Play Services, free for all ([MANIFESTO §3, §5](../../MANIFESTO.md)) | An amchi running a rural clinic in Spiti or western Bhutan operates in a context where the threat model is real (data hostile to the patient's interests, weak legal protections, mobile-first patients). Bios's posture is the same posture rural Sowa Rigpa clinics already understand intuitively |

These are not parity wins. They are places where the instrument's foundational disposition is something an amchi would consider competent before reading the schema.

---

## 2. Gaps, ordered by impact in a Sowa Rigpa setting

### 2.1 High-altitude SpO2 baseline — the most consequential gap

[EmergencyVitalPatterns.spo2Critical](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L51-L80) explicitly states:

> SpO2 ≤85 %. Below the level at which clinical hypoxia is unambiguous **regardless of altitude, age, or fitness**.

The bolded clause is correct at sea level and wrong almost everywhere Sowa Rigpa is practised in living tradition:

| Location | Approx. altitude | Typical adult resting SpO2 (acclimatised) | Bios behaviour today |
|---|---|---|---|
| Dharamsala / McLeod Ganj (India) | 1,400–2,000 m | 94–97 % | OK |
| Sikkim / Darjeeling (mid-hill clinics) | 2,000–2,800 m | 92–96 % | Mostly OK |
| Leh / Ladakh | 3,500 m | 88–93 % | False-fire `cardiovascular_stress`, miss real drift |
| Lhasa / Tibet AR | 3,650 m | 88–93 % | Same |
| Thimphu (Bhutan) | 2,300 m | 92–95 % | Mostly OK |
| Spiti, upper Mustang (Nepal) | 3,800–4,300 m | 86–91 % | False-fire `respiratoryInfection`; miss real drift |
| Ulaanbaatar (Mongolia, winter) | 1,300 m + cold + smog | 92–95 % | OK on altitude alone, but no PM2.5 context |
| Rongbuk / nomadic Tibetan plateau | 4,500–5,000 m | 82–88 % at rest | URGENT escalation would fire on baseline healthy adults |

The fix is not to weaken the URGENT cutoff — at sea level it is correct. The fix is to **interpret the cutoff against the owner's place**:

1. An owner-set, pull-side altitude annotation (`ELEVATION_BAND`: `SEA_LEVEL_TO_1500`, `MID_1500_2500`, `HIGH_2500_3500`, `VERY_HIGH_3500_4500`, `EXTREME_ABOVE_4500`). Stored as an annotation, not derived from GPS — manifesto-aligned and matches how rural clinics already record patient *desa* (place).
2. A per-band shift of the absolute cutoff: e.g. `spo2Critical.absoluteBelow` modulated to 82 % at `VERY_HIGH_3500_4500`, 78 % at `EXTREME_ABOVE_4500`. Same mechanism the biomarker patterns already use; the data structure is in place.
3. The personal-baseline path already self-corrects — an acclimatised herder's 14-day rolling SpO2 baseline naturally sits at 88 %, so a `cardiovascular_stress` 1.5σ drop fires correctly *relative to that baseline*. The hard-cutoff path is the only path that breaks at altitude.

This change costs roughly the same engineering effort as the existing biomarker absolute-threshold work. The clinical upside in Sowa Rigpa's catchment is large: the instrument becomes safe to recommend in Leh, Lhasa, Thimphu and the high pastoralist zones, where it is currently miscalibrated by construction.

Secondary altitude consequences the audit notes but does not insist on:

- Resting HR baselines at altitude are 5–10 bpm higher in acclimatised residents than at sea level; the [cardiovascularStress](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L187-L208) pattern already uses personal baseline, so this is mostly self-correcting, but the hard cutoff in [EmergencyVitalPatterns.tachycardiaCritical](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt) (≥130 bpm) is altitude-stable and does not need modulation.
- Respiratory rate baselines are also shifted upward at altitude; same comment — personal baseline self-corrects, hard cutoffs do not. The [respiratoryInfection](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L367-L393) pattern uses 1.5σ from personal baseline, which is correct.

### 2.2 The three Nyepa are not modelled

Sowa Rigpa's diagnostic ontology rests on three *Nyepa* (loose English: "humours," more accurately "morbific factors that, in balance, constitute health"):

- ***rLung*** (wind, *lung* in Tibetan transliteration). Governs movement, breath, nervous activity, circulation of the other humours, mentation, articulate speech. Substrate signals an amchi reads for *rLung*: pulse irregularity and quickness, breath rate and depth, sleep fragmentation, mental restlessness, dryness of mouth and skin, cold extremities, irregular bowel habit, sighing.
- ***mKhris-pa*** (bile, *tripa*). Governs heat, metabolism, digestion, complexion, vision sharpness, decision-making. Substrate signals: skin temperature elevation, bitter taste, yellowing of conjunctiva and urine, loose hot stool, sharp localised pain, irritability, post-prandial sweating, elevated thirst.
- ***Bad-kan*** (phlegm, *bekan*). Governs structure, cohesion, lubrication, taste, immune defence, stable mood. Substrate signals: heaviness, slow digestion, cold pallor, weight gain, mucus, slow pulse, sleepiness, deep stable sleep, low motivation, slow recovery from illness.

Bios captures most of the underlying signals — HRV, RHR, respiratory rate, skin temperature, sleep architecture, glucose variability, weight, activity — but the *composition* into a humoural reading is absent. This is not a request to encode a non-Western ontology in core enums; *that* would be a category error against the manifesto. The right place for this is a **pull-side overlay**, owner-asked, that re-projects existing metrics onto a *Nyepa* axis:

- *rLung* axis: HRV depression + sleep-fragmentation rise + irregular sleep timing + low skin temperature + RHR irregularity → recognisable wind-disturbance signature.
- *mKhris-pa* axis: skin-temperature elevation + glucose variability + sleep latency rise + HRV depression with high RHR → heat-pattern signature.
- *Bad-kan* axis: weight rising + activity falling + sleep duration high but quality flat + low resting HR + slow glucose return-to-baseline → cold-stable-cohesion signature.

The MVP is a single Compose screen reachable from Settings → Pull-Side Overlays, rendered only when the owner enables it, populated by re-projecting metrics the engine already computes. No new ingestion, no new pattern firing, no push surface. This honours the manifesto's "instrument, not coach" posture (the owner asks for the overlay; Bios does not push a Tibetan-medicine evaluation), and it gives an amchi reading the printout a *Lta-ba* surface they can orient to in seconds.

The closest existing analogue in the code is the longevity reference view referenced in [ROADMAP.md current state](../ROADMAP.md). The architectural pattern would be identical.

### 2.3 Pulse is captured as rate, not as morphology

*Reg-pa* — the touch pillar of the diagnostic trinity — is the discipline an amchi spends a decade learning, and the signal it reads is **pulse-wave shape**, not pulse rate. The *Gyud Zhi* describes the constitutional pulses (*rLung-zhu*: floating, hollow, fast and irregular; *mKhris-zhu*: thin, taut, rapid; *Bad-zhu*: slow, deep, slippery, broad) and the seasonal pulses (each of the five solid organs has a pulse "season" tied to the Tibetan calendar). The pathological pulse catalogue runs to dozens of named shapes.

Modern PPG sensors on every Apple Watch, Oura ring, WHOOP band and Withings ScanWatch *do* record the underlying waveform — the morphology of the optical pulse — but every adapter in [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md) discards it and emits only rate, HRV, and AFib irregularity flags. From a Sowa Rigpa standpoint this is the equivalent of an amchi being handed a single number ("72 bpm") and asked to diagnose — the information present in the sensor is not present in the data Bios ingests.

**What would close the gap, in increasing order of effort:**

1. Persist raw PPG morphology when an adapter exposes it (Apple Watch and Withings ScanWatch both can, via vendor APIs). Add `PPG_WAVEFORM` as a `[planned]` metric type alongside `ECG_WAVEFORM` in [DATA_MODEL.md](../DATA_MODEL.md). No condition pattern needed yet.
2. Compute and expose pulse-wave-derived features (augmentation index, dicrotic-notch position, pulse-wave velocity proxy, rise-time) as derived metrics. These have a Western evidence base (vascular stiffness, central BP estimation) entirely independent of any traditional reading, so the addition is defensible in its own right.
3. (Pull-side, optional) Render those features in the *Lta-ba* overlay screen (§2.2) using vocabulary an amchi recognises. This is opt-in, owner-initiated, manifesto-aligned.

Step 1 is the only one that needs to land soon; the rest can be deferred.

### 2.4 No urinalysis surface

*Chu-rtags* — urine examination — is the second diagnostic pillar after pulse. The amchi reads urine in changing morning light, attending to:

- **Colour** (clear to dark amber; greenish, reddish, brownish)
- **Vapour** (duration, density, smell)
- **Smell**
- **Bubble size and persistence** (large quick-bursting vs small persistent)
- **Sediment** (cloud-like, sand-like, hair-like; settling pattern)
- **Surface film** (rainbow sheen, oily, none)

None of this is captured by any consumer wearable; this is not a Bios-specific failure. But Bios *does* have a manual-entry surface for self-reported biomarker values, and the architecture for `SELF_REPORTED` provenance is in place ([Enums.kt:27](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L27)). A minimal *chu-rtags* surface would be:

- A new `MetricDomain.URINALYSIS` (or, more cleanly, an entity outside `MetricReading` since the data is categorical, not scalar).
- An owner-facing structured-entry surface — pickers for colour, vapour, bubbles, sediment — using simple visual exemplars that work in low-bandwidth, low-literacy contexts. The Men-Tsee-Khang training charts have done this on paper for centuries; the digital version is mostly translation.
- No pattern firing on the data — purely a *Lta-ba* record the owner builds and an amchi reads.

This is not a high-priority addition relative to altitude (§2.1) or the *Nyepa* overlay (§2.2), but it would materially differentiate Bios from every other instrument in its category for Sowa Rigpa use.

### 2.5 No seasonal modulation

The *Gyud Zhi* divides the year into six seasons, not four:

| Tibetan season | Approx. months (N hemisphere) | Humoural valence |
|---|---|---|
| Early winter (*dgun smad*) | mid-Nov to mid-Jan | *Bad-kan* accumulates |
| Late winter (*dgun stod*) | mid-Jan to mid-Mar | *Bad-kan* peaks; *rLung* begins |
| Spring (*dpyid*) | mid-Mar to mid-May | *Bad-kan* releases; *mKhris-pa* begins to rise |
| Early summer (*so ka*) | mid-May to mid-Jul | *mKhris-pa* accumulates; *rLung* peaks |
| Late summer (*dbyar*) | mid-Jul to mid-Sep | *mKhris-pa* peaks; *rLung* falls |
| Autumn (*ston*) | mid-Sep to mid-Nov | *mKhris-pa* releases |

The behavioural regimen (*spyod-lam*) and dietary regimen (*zas-spyod*) the amchi recommends are anchored to this calendar — heating foods in late winter, cooling in late summer, oil massage in early winter, fasting interventions only outside *rLung*-peak seasons, and so on.

Bios's 14-day rolling baseline ([ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt)) collapses any seasonal trajectory into the immediate past two weeks and has no notion of "where in the year is the owner." This produces a few specific failures:

- An RHR drift upward in late summer reads to Bios as `cardiovascular_stress`; to an amchi it is the expected *mKhris-pa* seasonal rise and not yet pathological.
- A sleep-fragmentation rise in early winter reads to Bios as `sleep_disruption`; to an amchi it may be the expected *rLung* seasonal turbulence and a cue to adjust *spyod-lam*, not a clinical signal.
- A weight gain plus glucose-variability rise in late winter reads to Bios as a `metabolic_drift` precursor; to an amchi it is the expected *Bad-kan* accumulation and the cue to begin warming foods.

The fix sits naturally in the existing `excludedStates` machinery on [ConditionPattern](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L36-L37). Add a `SeasonalModifier` axis to the pattern definition: each pattern can declare that in some seasons its threshold is shifted (looser or tighter) without changing the underlying rule. The owner sets their hemisphere; the calendar derives the Tibetan season; the engine reads the modifier table.

This is not a *Gyud Zhi*-only change — Ayurveda's *ritucharya*, TCM's six-energy / five-element seasonal correspondences, and Greco-Arabic Unani's *fasl* framework all use seasonally-shifted baselines. The same infrastructure serves all of them.

### 2.6 No place / geography annotation

Closely related to §2.1 and §2.5: Sowa Rigpa is a geographically situated medicine. The amchi reads the patient against their *desa* — their place — and place is more than altitude. It includes:

- **Climate**: cold-dry (Tibetan plateau, Ladakh), cold-moist (Sikkim winter), hot-dry (Mustang summer), hot-moist (lowland Nepal).
- **Fuel source**: dung, wood, kerosene, electricity — each with different respiratory exposure.
- **Water hardness and source**: glacial melt, deep aquifer, stored rooftop, municipal.
- **Daily rhythm**: pastoralist (dawn–dusk outdoor), monastic (pre-dawn rise, midday rest), urban sedentary, mobile-trader.

Bios records none of this and is *correct* not to derive it from GPS (the LETHE/manifesto threat model is right to refuse). The minimal addition: an owner-set `EnvironmentalContext` annotation, pull-side, with five or six categorical fields. The amchi-aligned use: a high-altitude cold-dry pastoralist's *rLung*-baseline is constitutionally different from an urban-Lhasa shopkeeper's, and the engine could lift the personal-baseline z-score thresholds accordingly.

The fields belong nowhere near GPS or the sync layer; they are owner-self-described annotations the engine reads. Stored alongside the medication annotations introduced for [§2.5 of the primary-care audit](MEDICAL_PROFESSIONAL_POV.md) — same `SELF_REPORTED` provenance, same write-only-by-owner posture.

### 2.7 No surface for traditional interventions

The Sowa Rigpa therapeutic toolbox extends well beyond the lifestyle and pharmacy axes Bios already considers:

- ***rin chen ril bu*** — precious pills, mineral-based, taken on auspicious days. The most well-known is *rinchen mangjor*. Intake is rare but pharmacologically active.
- ***me btsa'*** — moxibustion at named points (*spyi-bo* crown, *snying-mig* heart point, *ru-mtshams* shoulder, etc.). Produces a local heat exposure with measurable systemic effects on HRV and skin temperature.
- ***gtar ga*** — bloodletting at named venous points. Acute haematocrit and volume effect.
- ***gser khab*** — golden-needle therapy at *spyi-bo*. Used for chronic *rLung* disorders.
- ***bsku mnye*** — oil massage with medicated sesame or mustard oils. Modulates *rLung*.
- ***sngo sbyor*** — herbal compounds, the broad pharmacy. Hundreds of formulae.

Bios has the `MedicationAnnotationRepo` ([AnomalyDetector.kt:34](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L34)) which the primary-care audit's §2.5 recommended — that surface is the right architectural home for *rin chen ril bu* and *sngo sbyor* intake annotations, treated as the same write-only owner-set context that beta-blockers and statins occupy. The physical-intervention timestamps (*me btsa'*, *gtar ga*, *gser khab*, *bsku mnye*) want a sibling surface: an `INTERVENTION_EVENT` metric on `MetricDomain.SAFETY` or a new `MetricDomain.INTERVENTION`, EVENT-unit, owner-annotated.

The pull-side use-case for the amchi reading the printout: "the patient received *me btsa'* on day 14; observe HRV and skin-temperature trace over the following 7 days." That kind of intervention-anchored time-series read is exactly what an amchi does on paper today, and the data are present in Bios — only the timestamp anchor is missing.

### 2.8 The mental-health correlate pattern is, accidentally, a *rLung* pattern

This is not a gap; it is a striking *alignment* worth naming explicitly. The [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) pattern requires three of: sleep architecture loss, HRV depression, daily-activity reduction, circadian phase shift, typing-cadence irregularity, mood-drift score. In Sowa Rigpa pathophysiology this constellation — scattered sleep, racing autonomic, restless mind, irregular daily rhythm — is the canonical *rLung*-disturbance signature. The pattern's prevention guidance (consistent sleep, daily movement, social connection, time in nature) maps almost exactly onto the amchi's prescription for *rLung* imbalance (regular schedule, oil massage, warm grounding foods, monastic-pace social presence).

The *Gyud Zhi* would add a few axes the pattern does not currently weigh: dryness of mouth and skin, articulation speed, cold extremities, irregular bowel — but those are not wearable-tractable, and Bios's restriction to wearable + biomarker signals is honest.

The amchi reading this pattern's output would recognise it. That recognition is the closest single thing to interoperability between Bios and the *Gyud Zhi* the codebase currently offers.

### 2.9 Geographic distribution vs current locale list

[RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) lists six regions in [ROADMAP.md](../ROADMAP.md): US, GB, EU, CA, AU, JP. The Sowa Rigpa catchment — India, Bhutan, Mongolia, Nepal, Buryatia, Kalmykia, Sikkim, Ladakh, Tibet AR — falls under "EU" (none of it actually), "US" (none) or no region at all. Practically: an owner in Leh or Thimphu sees the wrong regulatory disclaimer, the wrong unit conventions (mostly correct — most of the catchment uses SI/metric — but BP cuff thresholds default to ACC/AHA), and no Sowa Rigpa-aware content surfaces.

The fix is not to add Sowa Rigpa-specific regions to the regulatory layer; that overstates Bios's clinical mandate. The fix is to:

1. Add at least an `IN` (India) and `NP` (Nepal) region with the Indian Council of Medical Research clinical-threshold conventions and the AYUSH-recognised regulatory framing (AYUSH covers Sowa Rigpa under the 2010 act).
2. Add a `BT` (Bhutan) region with the Bhutan Health Trust Fund framing.
3. Decouple the Sowa Rigpa overlays (§2.2, §2.4, §2.5, §2.6) from region — they should be available to any owner who enables them, not gated by IP or locale, because Sowa Rigpa is a living tradition with practitioners and patients diasporically distributed.

### 2.10 The doctor-in-the-loop FHIR export is not amchi-legible

Bios's professional review flow exports FHIR R4 with LOINC coding (12 mapped metric types per [ROADMAP.md](../ROADMAP.md)). An amchi at Men-Tsee-Khang or in a rural Ladakhi clinic does not read FHIR LOINC, and the import side of any practice-management system in the Sowa Rigpa world is paper, not HL7.

The actually-useful share format from a Sowa Rigpa perspective:

- A single printable A4 page, owner-initiated.
- A 14-day time-series of RHR, HRV, RR, skin-temperature deviation, SpO2 (modulated by altitude per §2.1), sleep-stage breakdown — rendered as small multiples, not numbers.
- If the *Nyepa* overlay is enabled, three small panels showing the *rLung*/*mKhris-pa*/*Bad-kan* axes over the same window.
- The owner's own annotations (medication, intervention, season) on the timeline.
- No interpretation, no diagnosis, no LOINC. The amchi reads it.

This is a pull-side rendering, owner-triggered, sharing-by-print or sharing-by-QR. It does not violate the manifesto; it is the manifesto's posture applied to a paper-first clinical context.

---

## 3. Manifesto / Sowa Rigpa alignment points

These are *not* gaps. They are places where the manifesto and the *Gyud Zhi*'s ethical framing produce the same answer and should be noted as concordant.

### 3.1 "Evaluation belongs to the owner" vs. the amchi as servant of discernment

The *Gyud Zhi*'s opening chapters frame the practitioner's *kun slong* (motivation) as the doctrinal foundation of medicine. The amchi serves the patient's path; the medicine is not an authority above the person. Bios's Principle 7 — "instrument, not coach" — restates this in software ethics. There is no friction here; the postures rhyme.

### 3.2 "Silence is a feature" vs. observation as practice

In a Sowa Rigpa training, the apprentice spends years *watching* before they are permitted to evaluate. The instrument that observes quietly, accumulates, and speaks only when convergence is durable, is the instrument the practice trains people to be. The push-side silence in [AlertContentPolicy.kt:13-25](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L13-L25) is doing in code what the amchi is doing in person.

### 3.3 Behavioural and dietary regulation as the first-order intervention

The *Gyud Zhi*'s therapeutic order is: diet, conduct, medicine, physical intervention. Bios's per-pattern `prevention` and `healing` fields lead with diet, sleep, movement, stress regulation before recommending clinical referral. This is not a coincidence of taste — it is the same hierarchy of intervention, and an amchi reading the explanation text would find the order familiar.

### 3.4 Free for all — no subscription gating on early detection

The *Gyud Zhi* tradition has, historically, treated medicine as an offering made without expectation of payment when the patient cannot pay (this is the doctrinal ideal, varyingly observed in practice). Bios's Principle 3 — "no one should miss an early detection because they cannot afford a subscription" — restates this in modern terms. The alignment is not accidental; both are positions taken by traditions that conceive of medicine as a duty, not a market.

---

## 4. What I would recommend, prioritised

**Tier A — most consequential for Sowa Rigpa's catchment, ship before any new feature**

1. **Altitude-aware SpO2 cutoffs** (§2.1). Add `EnvironmentalContext.elevationBand` as an owner-set annotation; modulate the absolute SpO2 cutoff in [EmergencyVitalPatterns.spo2Critical](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt#L58-L80) per band. One-day change, large clinical safety upside in the geographies where Sowa Rigpa is practised.
2. **Owner-set place / climate annotation** (§2.6). The same `EnvironmentalContext` entity carries fuel source, climate band, daily-rhythm pattern. Pull-side, write-only-by-owner, read by the engine for baseline interpretation and by the share surface for context.
3. **`IN`, `NP`, `BT` regions in [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt)** (§2.9). Correct disclaimers and unit conventions for the catchment.

**Tier B — *Lta-ba* surface, next quarter**

4. **Pull-side *Nyepa* overlay** (§2.2). Re-projects existing HRV / RHR / sleep / temperature / glucose metrics onto the three-humour axes. Owner-enabled, pull-only, no push surface. The MVP is one Compose screen.
5. **Six-season modulation** (§2.5). Add `SeasonalModifier` to `ConditionPattern`; derive the Tibetan season from the owner's hemisphere + calendar; engine applies the modifier when present.
6. **Print-ready amchi-view share format** (§2.10). One A4 page of small multiples + annotations, owner-triggered. Reuse the same share flow as FHIR export.

**Tier C — record completeness, when the foundation is solid**

7. **Persist PPG morphology** (§2.3) as `PPG_WAVEFORM`. No pattern needed yet; just the data.
8. **Urinalysis surface** (§2.4) — categorical owner-entry of *chu-rtags* fields. Pull-side record only.
9. **Intervention-event metric** (§2.7) for *me btsa'*, *gtar ga*, *gser khab*, *bsku mnye* timestamps. Reuse the `SAFETY` domain or open `INTERVENTION`.
10. **Extend medication annotations** (§2.7) to cover *rin chen ril bu* and *sngo sbyor* — same surface, same `SELF_REPORTED` provenance.

**Do not adopt**

- A core-enum encoding of *rLung* / *mKhris-pa* / *Bad-kan* as `MetricType`s. The humours are interpretive projections of underlying signals, not first-order measurements — putting them in `MetricType` would conflate ontology with observation. The pull-side overlay is the right surface.
- A diagnostic claim of any Sowa Rigpa pattern. The instrument records; the amchi diagnoses. The same line Bios already draws with Western biomedicine should be drawn here, more carefully if anything.
- Pushed (notification-side) interpretation of the *Nyepa* overlay. The overlay is pull-side or it is not honest.

---

## 5. Summary line for an amchi reading the printout

> Bios is, in its posture, an instrument an amchi would recognise: it observes quietly, refuses to evaluate the person, requires convergence before it speaks, treats personal baseline as the unit of comparison, and leads with diet-and-conduct guidance before clinical referral. As an instrument for *Lta-ba*, it is competent for HR, HRV, breath, sleep, skin temperature and the biomarker labs an owner imports. As an instrument for *Reg-pa* it captures only rate, not the morphology the discipline reads. *Chu-rtags*, the three *Nyepa* axes, the six seasons, and the high-altitude physiology that frames most Sowa Rigpa clinical work are absent — the altitude gap in particular makes the URGENT SpO2 path miscalibrated for Leh, Lhasa, Thimphu and the high pastoralist plateau, and is the single most consequential fix. None of these gaps violate the manifesto; the *Nyepa* and seasonal overlays are exactly the kind of owner-asked, pull-side surface the manifesto's framing already supports.
