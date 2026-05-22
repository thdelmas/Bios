# Ayurvedic Practitioner Audit — Bios Through the Lens of Ashtanga and Modern BAMS Practice

**Scope:** Bios's clinical reach as a preventive-monitoring instrument, evaluated against the diagnostic frame and therapeutic ordering of Ayurveda — both the classical eight-limb *Ashtanga* division (Charaka / Sushruta / Vagbhata) and the modern BAMS / MD-Ayurveda curriculum (Kayachikitsa, Panchakarma, Swasthavritta, Dravyaguna, Roga Nidana, Striroga / Prasuti, Manasa Roga, Marma chikitsa, Nadi pariksha).
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Ayurveda (BAMS practitioner reading the code to decide whether Bios could be a useful adjunct to their consultation). Not a comment on the validity of Ayurveda as a system; this audit takes the tradition's own internal taxonomy as given and asks where Bios fits inside it.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [docs/audits/MEDICAL_SPECIALTIES_WORLDWIDE.md](MEDICAL_SPECIALTIES_WORLDWIDE.md), [docs/audits/MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [CircadianConditionPattern.kt](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt).

---

## Executive summary

Bios's architectural posture is **deeply consonant with Ayurvedic clinical reasoning in three respects** and **structurally silent in seven**. The consonances are not surface coincidences: the engine's commitment to the *personal baseline as the unit of comparison* mirrors the Ayurvedic distinction between **Prakriti** (the owner's innate constitution) and **Vikriti** (their current deviation from it); the manifesto's *"instrument, not coach"* posture is the same ethical register as a *vaidya* who reads the patient first and prescribes second; and the entire preventive-monitoring scope is *Swasthavritta* and *Rasayana* by another name — surveillance of *swastha* (the well person) precisely so the window for simple intervention is not missed. A BAMS-trained reader will find Bios's bone-structure recognisable in a way that most consumer wearables are not.

What's missing, viewed from an Ayurvedic frame, is just as structural. Bios has no model of **dosha** (Vata / Pitta / Kapha) — the irreducible diagnostic axis of the tradition. The signals it collects (HRV, skin temp, RHR, sleep fragmentation, glucose variability, hsCRP) could each be re-projected onto a dosha-vitiation surface with literature support, but no such projection exists. There is no **Agni** composite — Ayurveda's central metabolic concept — even though Bios captures every input needed (postprandial glucose excursion, body temperature, GI-time-correlated activity, sleep). **Dinacharya** (daily-routine alignment to dosha-specific time-windows) is invisible: Bios tracks circadian phase but does not surface alignment with the classical *Vata / Pitta / Kapha kala* windows. **Ritucharya** (six-season regimen) has no analogue — baselines are 14-day rolling and season-blind. **Nadi pariksha** has no machine equivalent (and probably shouldn't, given the manifesto's posture on instrument vs. clinician). **Ashtavidha pariksha** — the classical eight-fold examination — has only one of its eight axes (pulse / *nadi*) crudely covered; tongue, eye, voice, skin, urine, stool, and *aakriti* (general appearance) are absent. And the *Dravyaguna / Rasashastra / Bhaishajya kalpana* pharmaceutical surface — herbs, mineral preparations, formulations — is wholly missing.

The audit surfaces twelve gaps ordered by clinical impact in Ayurvedic terms:

1. **No dosha (Vata / Pitta / Kapha) model anywhere in the schema.** This is the most load-bearing absence. Bios collects HRV instability, sleep fragmentation, cold extremities indirectly (skin temp), and (via W2F) anxiety markers — every Ayurveda textbook would read these as a *Vata-vitiation* signature. It collects skin temp elevation, hsCRP, inflammatory wearable proxies, GI-correlated glucose dysregulation — *Pitta-vitiation*. It collects rising body mass + body fat %, low active minutes, reduced VO2 max, weight gain, daytime lethargy — *Kapha-vitiation*. None of these patterns are projected onto a dosha axis, even on the pull side. A BAMS practitioner who pulled an FHIR export from a patient would still have to do the dosha mapping by hand.

2. **No Agni composite.** *Jatharagni* (the central digestive-metabolic fire) is the single most-cited diagnostic indicator in *Kayachikitsa* — *"Roga sarve api mandagnau"* (all disease arises from impaired Agni, Ashtanga Hridaya). Bios has fasting glucose, postprandial glucose (via CGM), HbA1c, GLUCOSE_CV, GLUCOSE_MAGE, GLUCOSE_TIME_IN_RANGE, ALT/AST/GGT (digestion-end-product enzymes), body temp, and exercise-session timing — every component to compose an Agni-quality index across the four classical states (*Sama*, *Vishama*, *Tikshna*, *Manda*). No such composite exists.

3. **No Ama (metabolic toxin) surface, though partial alignment exists.** The classical concept of *Ama* — incompletely-metabolised material that drives chronic illness — overlaps substantially with what Bios already calls `chronic_inflammation` (sustained mildly-elevated RHR + low HRV + sustained skin-temp elevation + hsCRP ≥ 1.0). The mapping is a *re-label*, not a re-build. Currently a BAMS reader who sees the [chronicInflammation pattern in ConditionPatterns.kt:309-336](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L309-L336) will recognise *Ama-sanchaya* in everything but name — but the name matters in this register.

4. **Dinacharya (daily-routine alignment) is not exposed.** Ayurveda prescribes specific *kalas* — pre-dawn (*Brahmi muhurta*, the Vata window) for waking, the Pitta noon window for the heaviest meal, the Kapha evening window for sleep onset by ~10pm. Bios has [CIRCADIAN_PHASE_SHIFT](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L101) and SLEEP_REGULARITY but renders them in pure-chronobiology terms (cosinor / DLMO). There is no surface that says "your sleep midpoint is 02:40 — outside the Kapha window of 18:00–22:00 sleep-onset that Ayurveda regards as restorative."

5. **Ritucharya (six-season regimen) has no analogue.** Baselines are 14-day rolling and season-blind. Ayurveda recognises six *ritus* (Shishira, Vasanta, Grishma, Varsha, Sharad, Hemanta), each with characteristic dosha-shifts that practitioners adjust regimen against — and which would predict normative baseline drift the engine currently treats as noise. There is no `RitucharyaSeason` modifier on baselines, no seasonal `excludedStates` analogue, no surface that explains "your RHR is elevated 1.5σ above your 14-day baseline, but this is *Grishma ritu* — heat-season elevation is normative across the population at this time."

6. **No Prakriti capture surface.** The owner's *constitutional type* (Vata, Pitta, Kapha, or any of the seven dual / *Sannipataja* combinations) is set at conception in Ayurvedic theory and is the lens against which every signal is read. Bios has [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) modelling pregnancy / postpartum / athlete / paediatric / frailty — but no equivalent for constitutional type. A Vata-prakriti owner whose RHR is 78 may be at their personal baseline; a Kapha-prakriti owner at 78 may be in mild stress; the population-level z-score machinery doesn't distinguish them, but a *vaidya* would, on first reading.

7. **Ashtavidha pariksha is one-eighth covered.** The classical eight-fold examination — *Nadi* (pulse), *Mutra* (urine), *Mala* (stool), *Jihva* (tongue), *Shabda* (voice), *Sparsha* (touch / skin), *Drik* (eye), *Aakriti* (form / general appearance) — has only the pulse axis crudely covered (HR / HRV / RHR are pulse-derived but *Nadi pariksha* in Ayurveda reads pulse *quality*, not rate). The other seven are absent. Some could be added with the existing manual-entry surface (tongue colour, stool form, voice quality) at very low cost; some legitimately can't be (the practitioner's eye on the patient is the practitioner's eye).

8. **Manasa roga (mental health) lacks the Triguna model.** Bios has [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) — sleep disruption + HRV decline + activity drop + (via W2F) typing cadence and circadian phase shift. Ayurveda would read these through the *Triguna* (Sattva / Rajas / Tamas) axis: Rajas-dominant patterns show restless sleep, racing HRV, hyperactivity; Tamas-dominant show prolonged sleep, low activity, *avasada* (depressive lethargy). The same physiological signals could be re-projected onto a Triguna surface, and the existing *Manasa roga* clinical vocabulary (*Unmada*, *Apasmara*, *Atatvabhinivesha*) would let a BAMS reader interpret the patterns in their own register.

9. **No Marma point modelling.** *Marma chikitsa* depends on 107 specific anatomical points; Bios has no anatomical surface at all. This is probably correct scope-wise — Bios isn't a manual-therapy app — but it limits Bios's utility as an instrument *during* a Marma session (heart-rate, HRV response to specific point stimulation could be a research surface). Flag only, not a recommended addition.

10. **Vajikarana (reproductive medicine) is partially covered, but not under that frame.** Bios has a separately-encrypted reproductive database with BBT, CYCLE_PHASE, MENSTRUATION_ONSET, the [menstrualCycleAnomaly pattern](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498), and endocrine biomarkers (TESTOSTERONE_TOTAL, ESTRADIOL, CORTISOL). The classical *Vajikarana* frame — *Shukra dhatu* nourishment, *Ojas* preservation, reproductive vitality — is invisible. A BAMS-Striroga practitioner reading the schema sees the right data but no language they would use.

11. **Dravyaguna / Rasashastra / Bhaishajya kalpana — no pharmacy surface at all.** This is the same gap flagged in the primary-care audit (§2.5: no medication context). For Ayurveda the gap is wider: not just allopathic drugs, but classical formulations (*Triphala*, *Chyavanaprasha*, *Ashwagandha*, *Guduchi*), *Rasashastra* mineral preparations (*Bhasma*, *Pishti*), and individual herbs (*Ekala dravya*). A patient on *Arjuna* for cardiac support or *Brahmi* for cognition or *Trikatu* for digestion should have that context available when the pattern engine reads RHR or sleep or glucose. Bios has shipped a `MedicationAnnotationRepo` ([wired into AnomalyDetector at line 34](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L34)) — extending its `substance_key` vocabulary to include Ayurvedic *dravyas* alongside RxNorm is a low-cost extension.

12. **Panchakarma has no place in the data model.** *Panchakarma* (the five purification therapies — *Vamana*, *Virechana*, *Basti*, *Nasya*, *Raktamokshana*) is a high-intensity clinical intervention, typically run as a 7–28 day in-clinic course. Pre/post-Panchakarma biomarker tracking would be a high-value research surface (does a *Virechana* course move hsCRP? does *Basti* move HRV?), and Bios already has the schema — but there is no `TreatmentCourse` entity that brackets a *Panchakarma* programme so the engine could distinguish pattern-shifts driven by the treatment from pattern-shifts driven by anything else. This is a deferred-roadmap surface, not a one-day change.

The remainder of this audit covers strengths a BAMS reader would actually appreciate, and each gap in more detail.

---

## 1. What Bios already does well, viewed through an Ayurvedic lens

| Quality | Evidence in code / docs | Why it matters in Ayurveda |
|---|---|---|
| **Personal baseline over population norm** | 14-day rolling per-metric baseline; z-score gate; multi-signal convergence ([ConditionPatterns.kt:113-124](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L113-L124)) | This *is* the Prakriti / Vikriti distinction in software form. Ayurveda has always insisted that "normal" is constitution-specific. A Kapha-prakriti patient with HR 56 is not bradycardic; a Vata-prakriti patient with HR 56 may be in early *kshaya*. Bios's engine treats this as the first principle, not an exception |
| **"Instrument, not coach"** | Manifesto Principle 7; [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) bans lifestyle judgments at the CI level | This is the *vaidya-rogi* relationship preserved across the device boundary. The classical texts insist the practitioner observes first, prescribes only after *darshana / sparshana / prashna* (inspection / palpation / interrogation). Bios's content policy enforces the same ordering: data first, evaluation belongs to the owner / their *vaidya* |
| **"Treating not-yet-disease"** | The entire preventive scope; manifesto "early detection saves lives" | *Swasthavritta* (preventive medicine) is the Ayurvedic specialty whose explicit charter is surveillance of *swastha* (the well person) — catching *purvarupa* (prodromal signs) before *rupa* (manifest symptoms). Bios's "1-2 days before symptoms appear" framing in [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) is *purvarupa* detection in modern instrument form |
| **Multi-signal convergence** | `minActiveSignals = 3` for [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156); 3-of-4 for [chronicInflammation](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L309-L336) | Mirrors the *Trividha pariksha* (three-fold examination — *Darshana, Sparshana, Prashna*) and *Ashtavidha pariksha* logic: diagnosis is a convergence of multiple observation channels, never a single sign |
| **Rasayana scope** | The longevity-informed patterns: [metabolicDrift](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L242-L271), [cardiorespiratoryDeconditioning](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L274-L307), [chronicInflammation](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L309-L336), [recoveryDeficit](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L339-L363), and the four epigenetic clocks held without composition | *Rasayana* (rejuvenation / longevity) is one of the eight Ashtanga branches and the explicit aim of *Charaka*'s second sthana. Bios's long-window patterns (168h to 336h evaluation) are doing *Rasayana*-class surveillance: catching slow declines in *Ojas* (vitality) before *Jara* (degeneration) sets in. The refusal to compose a single "biological age" from the four epigenetic clocks is *especially* Ayurvedic — it preserves the practitioner's evaluative role |
| **Separately-encrypted reproductive database** | Independent SQLCipher key, independent wipe; [menstrualCycleAnomaly](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498) routes through it | *Striroga* and *Prasuti tantra* practitioners would recognise the cycle-pattern logic. The privacy posture also rhymes with the classical confidentiality norms around reproductive consultation that *Charaka Vimana Sthana* describes |
| **Silence as a feature** | Manifesto Principle 7 ("speaks when something matters, not to fill a feed") | The classical *vaidya* speaks when the patient needs to be spoken to. *Mauna* (silence) is itself a *Sattvik* posture. The engine's restraint is more Ayurvedic than most modern healthcare software |
| **Literature anchoring** | 24 of 33 signal rules carry primary citations; biomarker thresholds match recognised guidelines | A BAMS practitioner working in an integrative setting needs to read both the modern citations and (where they exist) the classical references. The fact that Bios *cites* — and citations are auditable — means the practitioner can extend the citation set with their own *Pramana* (textual authority) work |
| **Source-agnostic schema, FHIR export** | [DATA_MODEL.md](../DATA_MODEL.md) §FHIR mapping; FHIR R4 bundle with LOINC | Lets an integrative clinic incorporate Bios data into a multi-practitioner record where the *vaidya*, the allopathic primary, and the patient all read from the same evidence base |

These are not parity wins. They are places where Bios's design choices independently arrive at conclusions Ayurveda has held for two millennia — the personal baseline, the instrument-vs-coach posture, the preventive-window emphasis, the convergence-of-signs diagnostic logic. A BAMS practitioner can take Bios seriously on these grounds before any Ayurveda-specific feature is added.

---

## 2. Ayurvedic gaps, ordered by impact

### 2.1 No dosha (Vata / Pitta / Kapha) model — the structural absence

This is the single largest gap, and it is structural rather than incremental. Ayurveda's *Tridosha* is the irreducible diagnostic axis: every classical symptom, every disease (*roga*), every therapeutic prescription is read against it. Bios has no representation of dosha anywhere — not in `MetricDomain`, not in `ConditionCategory` ([Enums.kt:102-105](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L102-L105)), not in `MetricType`, not in `PhysiologyState`.

The interesting observation is that **the signals Bios already collects map cleanly onto dosha-vitiation patterns** without any new sensor work:

| Dosha vitiation | Wearable / biomarker signature | Bios coverage |
|---|---|---|
| **Vata** (movement, dryness, cold, irregularity) | HRV instability / high variability of HR variability itself, sleep fragmentation, cold extremities (skin temp on the low side or wide diurnal swing), variable RHR, anxiety markers, constipation events, restlessness | HRV (RMSSD, LF/HF, LF power, HF power), SLEEP_FRAGMENTATION_INDEX, SLEEP_REGULARITY, SKIN_TEMPERATURE, RHR variability, TYPING_CADENCE (W2F), CIRCADIAN_PHASE_SHIFT |
| **Pitta** (heat, sharpness, inflammation, acid) | Skin temp elevation, sustained low-grade fever, hsCRP elevation, GI acidity markers, irritability, sleep disrupted by mid-night waking, sweating | SKIN_TEMPERATURE_DEVIATION, HSCRP, ALT / AST / GGT (hepatic Pitta), HEART_RATE elevation, body temp trends |
| **Kapha** (heaviness, slowness, accumulation) | Rising body mass, rising body fat %, declining VO2 max, declining active minutes, daytime lethargy, prolonged sleep duration, slow HR, low metabolic flexibility | BODY_MASS, BODY_FAT_PCT, LEAN_MASS, VO2_MAX, ACTIVE_MINUTES, SLEEP_DURATION (when long), RHR (when low + with low fitness — distinct from athletic bradycardia), GLUCOSE_TIME_IN_RANGE (when low and trending) |

A *dosha-vitiation* surface — surfaced strictly on the *pull side*, per [AlertContentPolicy](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) constraints — could:

1. Let the owner record their *Prakriti* (constitutional type) via a 30-question classical questionnaire, identical in form to the *Prakriti pariksha* tools BAMS clinics use today.
2. Run the existing signals through a *Vikriti* projection: "Vata: +1.8σ above your tracked baseline; Pitta: stable; Kapha: −0.4σ."
3. Surface this only when the owner asks (a Dashboard → Ayurvedic View toggle, off by default, exactly like the W2F / Smokeless companion surfaces).
4. Stay *strictly descriptive*: "Your HRV variability and sleep fragmentation are above baseline" stays in the push channel; the dosha re-projection stays on the pull side. No "your Vata is vitiated" push notification. The manifesto's content policy still holds.

Implementation note: a `DoshaProjection` entity could ride alongside `Anomaly`, computed at the same evaluation cadence, but consumed only by a pull-side composable. The same machinery already used to lift Smokeless / Virgil / W2F companion signals into the pattern surface could lift dosha projections out as a read-only view.

This is the highest-leverage Ayurveda-specific feature Bios could add. It would also be invisible to any owner who doesn't enable it, preserving the standalone posture.

### 2.2 No Agni composite

*Jatharagni* is the second-most-cited concept in *Kayachikitsa* and the explicit gate condition of *"Roga sarve api mandagnau"* (Ashtanga Hridaya). It has four classical states: *Sama* (balanced), *Vishama* (irregular — Vata-influence), *Tikshna* (sharp / over-active — Pitta), *Manda* (sluggish — Kapha). Each maps onto specific clinical observations: bowel-movement regularity, post-meal energy, postprandial glucose excursion, body temperature, *kshudha* (appetite quality).

Bios has every input needed:

- **Postprandial glucose** — CGM data via Dexcom/Abbott adapters, with derived [GLUCOSE_CV, GLUCOSE_MAGE, GLUCOSE_TIME_IN_RANGE, GLUCOSE_PEAK_24H](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L117-L120)
- **Postprandial HR / HRV response** — derivable from existing HR streams cross-referenced with EXERCISE_SESSION (which carries meal-adjacent timing in the [event_payloads sidecar](../DATA_MODEL.md#field-key-vocabulary))
- **Body temp** — SKIN_TEMPERATURE and SKIN_TEMPERATURE_DEVIATION
- **GI events** — not currently a MetricType, but `MEDICATION_INTAKE` and `EXERCISE_SESSION` show the pattern for adding `BOWEL_MOVEMENT` as an EVENT-unit metric in MetricDomain.METABOLIC (composite sidecar fields: *Bristol stool scale*, time-of-day, associated symptoms)

An *Agni-quality composite* surface could:

1. Classify recent metabolic state into one of the four Agni *avasthas* using the postprandial-glucose-excursion + body-temp + (when present) bowel-regularity triad.
2. Surface the classification only on the pull side. No "your Agni is *Manda*" push notification.
3. Use the existing [`metabolicDrift` pattern](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L242-L271) machinery — `metabolicDrift` is essentially detecting *Mandagni / Vishamagni transition*, just under a different name.

This is the *cleanest single mapping* between an Ayurvedic concept and Bios's existing data, and would close a meaningful gap in *Kayachikitsa* utility with low engineering cost.

### 2.3 Ama (metabolic toxin) — partial alignment, missing vocabulary

The classical concept of *Ama* — incompletely-digested / incompletely-metabolised material that accumulates and drives chronic disease — is one of Ayurveda's bridge concepts to modern systems-medicine ideas of chronic low-grade inflammation, dysbiotic metabolic byproducts, and post-translational protein damage.

Bios has, in [chronicInflammation](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L309-L336):

```
Sustained mild RHR elevation (>336h) + HRV depression (>336h) +
sleep duration shortfall (>168h) + sustained skin-temp elevation
(>168h, mild — only 0.5σ above baseline) — requires 3 of 4 active.
```

A BAMS reader sees *Ama-sanchaya* in this. Specifically — and citation-aware:

- Sustained low-grade *temperature elevation* is a classical *Ama* sign in [Vagbhata's Ashtanga Hridaya, Sutrasthana 13](https://en.wikipedia.org/wiki/Ashtanga_Hridayam): *"Apaktah ama"* — heaviness, mild warmth, lassitude
- HRV depression maps onto *Ojas-kshaya* (vitality decline) tracking the systemic-burden interpretation
- Sleep shortfall maps onto *Rajas-vrddhi* and accelerates Ama accumulation per *Charaka Sutrasthana 21*

This is not a re-build. It's a re-label, and could happen entirely on the pull side: when the owner views the chronic-inflammation pattern detail screen, an optional Ayurvedic-context section explains "in Ayurvedic terms, this multi-signal sustained-mild-deviation pattern overlaps substantially with what Ayurveda calls *Ama-sanchaya*." The push notification stays in modern-biomedicine language; the pull-side cross-reference is added.

Same logic could apply to:

- **Cardiovascular_stress** → *Hridroga purvarupa* (cardiac disease prodrome) in *Charaka Chikitsa 26*
- **Recovery_deficit** → *Ojas-kshaya* (vitality depletion) in *Charaka Sutrasthana 17*
- **Sleep_disruption** → *Nidranasha / Anidra* in *Charaka Sutrasthana 21*
- **Cardiorespiratory_deconditioning** → *Bala-kshaya* (strength depletion)

The re-label work is essentially copy-paste with citations to the classical texts. Engineering-light, scope-light, optional for the owner, manifesto-clean.

### 2.4 Dinacharya — daily-routine alignment to dosha-kalas

Ayurveda divides the 24-hour day into six four-hour windows, each dominated by one dosha:

| Window | Dominant dosha | Classical guidance |
|---|---|---|
| 02:00–06:00 | Vata | Wake before sunrise (*Brahmi muhurta*) for *Vata-stirring* alertness |
| 06:00–10:00 | Kapha | Morning activity; light breakfast; movement to disperse Kapha |
| 10:00–14:00 | Pitta | Heaviest meal at solar noon (digestive Pitta at peak) |
| 14:00–18:00 | Vata | Productive work; light afternoon snack |
| 18:00–22:00 | Kapha | Wind-down; light dinner; sleep onset by ~22:00 ideal |
| 22:00–02:00 | Pitta | Deep sleep; tissue repair; metabolic Pitta active |

Bios has every input to render alignment to these windows:

- **Sleep onset / offset times** — derived from SLEEP_STAGE transitions
- **SLEEP_REGULARITY** — already computed by [engine/SleepRegularityCalculator referenced in MetricType.kt:95-96](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L94-L96)
- **CIRCADIAN_PHASE_SHIFT** — already computed cosinor-style
- **Meal timing** — EXERCISE_SESSION sidecar has `start_utc` / `end_utc`; meal events are not yet a MetricType but are an obvious add (`MEAL_EVENT` in `MetricDomain.METABOLIC` with composite fields)
- **Activity timing** — STEPS and ACTIVE_CALORIES have timestamps

A *Dinacharya alignment* surface (pull-side, opt-in) could render the owner's actual 7-day pattern against the classical kalas, e.g.:

> Sleep onset: median 00:42, ranges 23:15 – 02:30 across the past 7 days. The Kapha sleep-onset window (18:00 – 22:00) is the Ayurvedic target; sleep onset later than this is associated with Vata vitiation in classical texts and with circadian misalignment in modern chronobiology.

This is the same rendering that already exists for [CircadianConditionPattern](../../android/app/src/main/java/com/bios/app/alerts/CircadianConditionPattern.kt), but expressed in the practitioner's own clinical vocabulary. The data is identical; the framing differs.

### 2.5 Ritucharya — six-season modulation of baselines

The 14-day rolling baseline is correct for stable adult physiology in a stable season. It is *systematically wrong* across season transitions, which Ayurveda recognises explicitly:

| Ritu (season) | Approx. months (N. India) | Dominant dosha effect | Expected physiological shift |
|---|---|---|---|
| Shishira (late winter) | Jan–Feb | Kapha-sanchaya | Slower HR, higher Kapha accumulation, increased sleep |
| Vasanta (spring) | Mar–Apr | Kapha-prakopa | Allergic / respiratory vulnerability rises |
| Grishma (summer) | May–Jun | Vata-sanchaya, Pitta-prakopa | RHR elevation, sleep onset later, skin temp elevation |
| Varsha (monsoon) | Jul–Aug | Vata-prakopa | HRV instability, joint discomfort, GI dysregulation |
| Sharad (autumn) | Sep–Oct | Pitta-prakopa | Inflammatory markers rise, skin issues |
| Hemanta (early winter) | Nov–Dec | Vata-sanchaya | Cold extremities, sleep duration lengthens |

[PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) already implements the right *mechanism* for modulating baselines — `excludedStates` on `ConditionPattern`, gating filter in [AnomalyDetector.applicablePatterns()](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L43-L44). A `RitucharyaSeason` extension would:

1. Add a season enum (six values, or twelve if mid-season transitions are needed).
2. Let the owner set the season manually (or auto-detect by date + locale, since Ayurveda's seasonal boundaries depend on hemisphere — a roadmap-localisation point).
3. Apply per-pattern *threshold modifiers* analogous to the pregnancy / athlete / paediatric posture: e.g. RHR baseline in Grishma may run 3–5 bpm higher across the population, so the `cardiovascular_stress` threshold could be relaxed by a small additive offset.
4. Surface a "season-adjusted" note in pattern explanations when active.

The Ayurvedic literature on seasonal baseline drift is qualitative; there's a research-surface question about whether the modifiers should be Ayurveda-prescribed or empirically learned (the federated-learning pipeline could derive per-region seasonal offsets). Either way, the gating machinery exists.

### 2.6 No Prakriti capture surface

*Prakriti* is the owner's innate constitutional type — set at conception per Ayurvedic theory, expressed across seven types (three single-dosha: Vata, Pitta, Kapha; three dual: Vata-Pitta, Pitta-Kapha, Vata-Kapha; one *Tridoshaja*) plus rare *Sannipataja* combinations. Modern BAMS clinics administer a 30–60-question *Prakriti pariksha* questionnaire that classifies the patient on first consultation.

Currently Bios has no place to store this. The closest existing structure is [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), but Prakriti is orthogonal to it — a pregnant woman has both a `PREGNANCY_T2` state *and* a *Vata-Pitta prakriti*. A new entity is warranted:

```kotlin
enum class AyurvedicPrakriti(val displayName: String, val description: String) {
    VATA("Vata-dominant", "..."),
    PITTA("Pitta-dominant", "..."),
    KAPHA("Kapha-dominant", "..."),
    VATA_PITTA("Vata-Pitta dual", "..."),
    PITTA_KAPHA("Pitta-Kapha dual", "..."),
    VATA_KAPHA("Vata-Kapha dual", "..."),
    TRIDOSHA("Balanced (rare)", "...")
}
```

This would:

1. Modulate the dosha-vitiation projection in §2.1 — Vata-prakriti owners have a higher baseline for some Vata signals (HRV variability, sleep fragmentation), and a Vikriti calculation has to subtract the Prakriti contribution.
2. Tag relevant patterns with prakriti-relative interpretation, all on the pull side.
3. Be a one-time setup item in onboarding (next to PhysiologyState), entirely optional, with full description for owners unfamiliar with the concept.

Without Prakriti, the dosha-vitiation surface in §2.1 still works (it can report "Vata-axis signals are +1.8σ above the engine's tracked 14-day baseline") but loses the constitutional interpretation a BAMS practitioner would want.

### 2.7 Ashtavidha pariksha — one-eighth covered

The classical eight-fold examination has these axes:

| Pariksha | What's examined | Bios coverage |
|---|---|---|
| 1. **Nadi** (pulse) | Pulse rate, rhythm, quality, "Vata / Pitta / Kapha-like" feel under three fingers | Partial: HR / RHR / HRV cover rate + variability; pulse-quality / *gati* / *vega* / *bala* are absent |
| 2. **Mutra** (urine) | Colour, clarity, odour, foam, sediment | None |
| 3. **Mala** (stool) | Form, colour, frequency, presence of *Ama* | None (Bristol scale would slot into a `BOWEL_MOVEMENT` MetricType) |
| 4. **Jihva** (tongue) | Coating, colour, papillae, *Ama* coating | None (could be a manual-entry surface with photo + categorical fields) |
| 5. **Shabda** (voice) | Quality, hoarseness, breathiness, pace | None (microphone-derived voice biomarkers exist as research; out of Bios scope for now) |
| 6. **Sparsha** (touch) | Skin temperature, moisture, texture | Partial: SKIN_TEMPERATURE; moisture / texture absent |
| 7. **Drik** (eye) | Sclera colour, conjunctival pallor, eye movement | None |
| 8. **Aakriti** (general form) | Body type, gait, posture | Partial: BODY_MASS, body composition; gait analysis is planned but not implemented |

The cheapest additions, ranked:

1. **`BOWEL_MOVEMENT` as a MetricType** in `MetricDomain.METABOLIC`, EVENT-unit, sidecar fields `bristol_scale` (1–7), `time_of_day`, `associated_discomfort` (0–10). Manual entry, low cost, immediately useful for *Mala pariksha* — and orthogonally useful for general gastroenterology.

2. **`TONGUE_OBSERVATION`** as a manual-entry MetricType in `MetricDomain.METABOLIC` (or a new `MetricDomain.AYURVEDIC_PARIKSHA` if a separate surface is wanted). Sidecar fields: `coating_thickness` (none/light/moderate/heavy), `coating_colour` (white/yellow/grey), `tongue_colour` (pink/red/pale/purple), optional `photo_uri`. The owner takes the observation; Bios records it.

3. **`URINE_OBSERVATION`** similarly. Categorical fields for *Mutra* colour and clarity.

4. **`PULSE_QUALITY`** as a manual-entry sidecar attached to a HEART_RATE reading. Categorical fields: `gati` (Vata-like /Pitta-like / Kapha-like), `bala` (weak / moderate / strong), `vega` (slow / moderate / fast). This is *self-pulse-reading*, not *Nadi pariksha* as performed by a practitioner — but it lets the owner record what they (or their *vaidya* between visits) felt.

None of these need new sensors. All use the manual-entry surface already wired for biomarkers and BBT. All ride on existing FHIR `Observation` mappings (FHIR supports categorical Observations and image attachments).

### 2.8 Manasa roga — the Triguna model is invisible

[`mentalHealthCorrelate`](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459) reads sleep + HRV + activity + (via W2F) typing cadence + circadian phase + mood drift. Ayurveda's *Manasa* (mental) frame has its own taxonomy:

- **Triguna axis** — *Sattva* (clarity, equanimity), *Rajas* (activity, agitation), *Tamas* (inertia, lethargy). Mental health imbalances are read as *Rajas-vrddhi* or *Tamas-vrddhi*.
- **Classical Manasa rogas** — *Unmada* (psychosis), *Apasmara* (epileptic / dissociative), *Atatvabhinivesha* (delusional fixation), *Mada* (intoxication), *Murcha* (syncope / fainting), *Sannyasa* (coma), *Chittodvega* (anxiety), *Vishada* (depression).

The wearable signals Bios collects map plausibly onto the Triguna axis:

| Triguna state | Wearable signature | Bios coverage |
|---|---|---|
| **Sattva** (balanced) | Stable HRV, regular sleep, moderate activity, stable circadian phase | All present |
| **Rajas-vrddhi** (excess Rajas — restlessness, anxiety, mania) | Low HRV with high variance, fragmented sleep, elevated activity, irregular typing cadence (fast + erratic), advanced circadian phase | All present — and the [mentalHealthCorrelate Companion signals](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L437-L443) already include TYPING_CADENCE and CIRCADIAN_PHASE_SHIFT |
| **Tamas-vrddhi** (excess Tamas — lethargy, depression, *avasada*) | Low HRV with low variance, prolonged sleep but unrefreshing, declining activity, delayed circadian phase, low typing cadence | All present |

A *Triguna projection* surface, pull-side, would render the same data through the classical lens. Like the dosha projection in §2.1, it can be a Dashboard → Ayurvedic View toggle.

The classical *Manasa roga* names *(Chittodvega, Vishada)* could be offered as alternate labels on the pull-side detail view, alongside the modern-biomedical framing (anxiety / depression correlates). This matters for clinical communication: a BAMS practitioner in conversation with an MD-Psychiatry colleague needs both registers, and Bios can be the bridge document.

### 2.9 Marma points — flagged, not recommended

*Marma chikitsa* requires anatomical-point modelling that Bios has no business doing. The 107 *marma* points are precise anatomical locations identified by *Sushruta* (*Sharira Sthana 6*); they are accessed by manual therapy (*marma abhyanga*), pressure (*marma chikitsa*), or — in some traditions — controlled stimulation.

A research-surface question exists: could HR / HRV / EDA response to specific *marma* point stimulation during a *marma chikitsa* session be recorded? The data model supports it (timestamps + HRV + skin temp); but the use case is narrow (clinic-internal research) and the privacy threat model around anatomical-point metadata is uninvestigated.

**Recommendation:** flag, do not implement. This is the kind of feature that should land via a third-party *vaidya*-facing companion app reading Bios's HRV stream via the ContentProvider — not a Bios-native feature.

### 2.10 Vajikarana — present in substance, missing in vocabulary

Bios's reproductive surface is solid: separately-encrypted database, [menstrualCycleAnomaly](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498), BBT, CYCLE_PHASE, MENSTRUATION_ONSET, plus endocrine biomarkers (TESTOSTERONE_TOTAL, ESTRADIOL, FREE_T4, FREE_T3, CORTISOL — see [MetricType.kt:178-181](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L178-L181)).

The *Vajikarana* frame would add:

1. **Pull-side classical labels** for what the existing surface already detects. *Artava-dushti* (menstrual disorder) for cycle anomaly. *Beeja-dushti* (gamete impairment) cross-referenced from sperm-analysis or AMH biomarkers if imported.
2. **Ojas / Shukra** vocabulary in the existing recovery-deficit and chronic-inflammation patterns — *Ojas-kshaya* and *Shukra-kshaya* (vitality / reproductive-tissue depletion) are the classical concepts a BAMS-Striroga practitioner would read these patterns under.
3. **Ritucharya-aware menstrual interpretation** — cycle norms shift with *ritu* per *Sushruta Sutrasthana 6*; the existing pattern reads cycle deviation against personal baseline, not seasonal baseline.

This is a vocabulary-overlay change, not a schema change. Same machinery as §2.3 (Ama re-labelling).

### 2.11 Dravyaguna / Rasashastra — the pharmacy gap

The primary-care audit flagged this as gap §2.5: no medication / current-treatment context, which has been partially closed by [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/data/MedicationAnnotationRepo.kt) (wired into the AnomalyDetector). For Ayurveda the gap is wider:

- **Single herbs (*Ekala dravya*)** — *Ashwagandha*, *Brahmi*, *Guduchi*, *Arjuna*, *Triphala*, *Trikatu*, *Yashtimadhu*, hundreds of others
- **Classical formulations (*Kashaya*, *Choorna*, *Vati*, *Ghrita*, *Taila*, *Asava*, *Arishta*, *Avaleha*)** — multi-herb preparations with characteristic effects
- **Rasashastra preparations (*Bhasma*, *Pishti*, *Rasayana yogas*)** — mineral-based, with specific clinical effects
- **Anupanas** — vehicles (honey, ghee, warm water) that modify drug action

Each of these can interact with the wearable signals Bios reads. *Arjuna* has documented cardioprotective effects — RHR drift on an *Arjuna*-using owner is interpretable. *Brahmi* affects HRV and sleep. *Triphala* affects bowel pattern. *Ashwagandha* affects sleep architecture and cortisol.

The right closure path uses the existing `MedicationAnnotationRepo` surface and extends its `substance_key` vocabulary:

1. **RxNorm coding** for allopathic drugs (the existing pattern).
2. **AYUSH / WHO ICD-11 traditional-medicine module codes** for Ayurvedic *dravyas* — ICD-11 Chapter 26 explicitly catalogues Ayurvedic substances; the same annotation surface could code Ayurvedic preparations alongside RxNorm.
3. **Free-text fallback** for *Bhaishajya kalpana* formulations that don't have a coded entry — same fallback the medication surface already supports.

The pattern-explanation builder (`buildExplanation` in [AnomalyDetector.kt:406-425](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L406-L425)) already appends "Annotated current medications" context; the same line would carry Ayurvedic substances. A BAMS practitioner reviewing the FHIR-exported bundle would see them.

Manifesto-clean: Bios still doesn't *prescribe* Ayurvedic substances. It records what the owner says they're taking. The substance-effect literature is left to the practitioner — `BiomarkerReference.kt` could carry classical references in the same way it carries Western citations, but evaluation belongs to the *vaidya*, not Bios.

### 2.12 Panchakarma — research-surface only, not a near-term feature

*Panchakarma* — *Vamana* (therapeutic emesis), *Virechana* (therapeutic purgation), *Basti* (medicated enema), *Nasya* (nasal administration), *Raktamokshana* (bloodletting) — is a high-intensity 7–28 day in-clinic programme. From Bios's perspective, the relevant question is: can pre / mid / post-Panchakarma biomarker tracking surface treatment effects?

The schema supports it:

- HSCRP, HBA1C, lipid panel, hepatic enzymes (ALT/AST/GGT), eGFR — direct biomarker shifts
- HRV, RHR, sleep architecture — autonomic shifts
- BODY_MASS, BODY_FAT_PCT, LEAN_MASS — body composition

What's missing: a `TreatmentCourse` entity that brackets a programme so the engine can:

1. Distinguish pattern-shifts driven by the treatment from pattern-shifts driven by anything else
2. Compute a pre / post comparison the BAMS practitioner can read
3. Avoid false-firing patterns *during* a Panchakarma course (a `Virechana`-induced HRV swing on day 3 is expected, not anomalous)

A minimal `TreatmentCourse` would have `start_utc`, `end_utc`, `programme_type` (string — `panchakarma_virechana`, `panchakarma_basti`, etc.), and a `pattern_suppression` flag the AnomalyDetector reads.

This is a deferred-roadmap item. The existing `PhysiologyState` mechanism shows how to gate patterns; extending it (or pairing it with a `TreatmentCourse` orthogonal axis) is the same engineering work as §2.5 (Ritucharya modifiers).

---

## 3. Manifesto / Ayurvedic-clinical tension points

These are *not* gaps — they are places where the Bios manifesto and Ayurvedic clinical practice could come into tension, and the audit is explicit about which side the choice falls.

### 3.1 "The owner is final" vs. *vaidya-rogi* relationship

Ayurveda is a practitioner-centred system in a way Bios is not. The *vaidya* (practitioner) classically holds evaluative authority — diagnosis (*Roga nidana*) and prescription (*Chikitsa*) are not patient-driven. The owner inputs *Prashna* (interrogation responses) and submits to *Darshana / Sparshana* (the practitioner's inspection / palpation), but the integrated reading belongs to the *vaidya*.

Bios's manifesto puts the owner at the centre. The owner reads the instrument; *evaluation belongs to the owner*. This is a load-bearing choice and I am not arguing against it — it is consonant with the broader LETHE ownership posture and with modern informed-consent norms.

The friction point is that an Ayurvedic clinical workflow expects the *vaidya* to see signals the patient may not be primed to interpret. The doctor-in-the-loop FHIR export surface (already shipped) is the manifesto-clean resolution: the owner *chooses* to share their Bios bundle with their *vaidya*, who then performs the integrated Ayurvedic reading. The export already carries the necessary structure; what's missing is the Ayurvedic-vocabulary overlay (§2.1 dosha projection, §2.3 Ama re-labelling, §2.8 Triguna projection) so the bundle is legible to a BAMS practitioner without translation.

### 3.2 "Silence is a feature" vs. *Purvarupa* surveillance

Ayurveda's *Swasthavritta* explicitly mandates that the *vaidya* surveils for *purvarupa* (prodromal signs) and intervenes *before* manifest disease. The classical instruction is closer to "speak when prodromes appear" than to "stay silent until certain."

Bios's [`infectionOnset`](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) — "1-2 days before symptoms" — is exactly *purvarupa* detection. The "silence is a feature" principle isn't in tension here: silence applies to lifestyle judgments and to noise, not to genuine *purvarupa* signals. Bios's `minActiveSignals` convergence requirement (3 of 6 for infection onset) is the same multi-channel discipline classical *Ashtavidha pariksha* applies before flagging *purvarupa*.

This is alignment, not tension. The audit flags it only to note that an Ayurvedic reader should not interpret "silence is a feature" as Bios being reluctant to surface *purvarupa* — it isn't.

### 3.3 "Personal baseline" vs. *classical norms*

Bios's commitment to the personal baseline is the first principle of its engine. Ayurveda has *both* — personal baselines (the *Prakriti*-relative norm for that constitution) *and* classical population norms (the texts describe expected *Vata-Pitta-Kapha* signatures across constitutions, ages, seasons).

The friction is that some Ayurvedic norms are absolute, not personal. *Mandagni* (sluggish digestive fire) has classical signs (post-meal heaviness, prolonged digestion, coated tongue) that apply regardless of personal baseline. Bios's engine currently treats every threshold as relative — except for the biomarker patterns, which already use `absoluteAbove` / `absoluteBelow` ([SignalRule.kt:62-65](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L62-L65)).

The closure is mechanically simple: dosha-vitiation signals (§2.1) and Agni signals (§2.2) can use the same `absoluteAbove` / `absoluteBelow` machinery the biomarker layer already uses, for the classical thresholds where they exist. Where the classical thresholds don't exist (most cases), personal-baseline-relative is the correct fallback, and incidentally matches Ayurveda's *Prakriti-relative* posture.

### 3.4 No "Ayurvedic score" — the right call

A particular tension to *avoid*: a single composite "Ayurvedic balance score" or "Tridosha index" that reduces a constellation of signals to a single number. This would be the Ayurvedic equivalent of the "biological age" composite the DATA_MODEL.md explicitly guards against, and the *same* answer applies — *evaluation belongs to the owner / the vaidya*. Surface the individual signals (HRV variability, sleep fragmentation, RHR trend, skin temp, glucose CV) and their dosha projections; do *not* compose them into a single "your Vata is 67/100" score. Bios's existing posture on epigenetic clocks is the right model.

---

## 4. What I would recommend, prioritised for an Ayurvedic-friendly Bios

**Tier A — vocabulary overlays, low engineering cost, high BAMS-utility uplift**

1. **Ayurvedic re-labelling on existing pattern detail pages** (§2.3, §2.8, §2.10). Pull-side only. Each of `chronic_inflammation`, `recovery_deficit`, `cardiovascular_stress`, `sleep_disruption`, `mental_health_correlate`, `menstrual_cycle_anomaly` gets an optional Ayurvedic-context section with classical name (*Ama-sanchaya*, *Ojas-kshaya*, *Hridroga purvarupa*, *Nidranasha*, *Chittodvega / Vishada*, *Artava-dushti*) and a citation to the classical text. No new entities, no new patterns. Closes most of the immediate "this feels untranslated" gap.

2. **Pull-side dosha projection surface** (§2.1). A Dashboard → Ayurvedic View toggle, opt-in. Renders existing signals projected onto Vata / Pitta / Kapha axes with classical references. Requires the dosha-projection engine code, but no new sensors, no new pattern definitions.

3. **Prakriti capture surface** (§2.6). One-time onboarding step (skippable). 30–60 question classical questionnaire, output an `AyurvedicPrakriti` enum, store in settings. Modulates the dosha projection above.

**Tier B — schema additions, single-day each**

4. **`BOWEL_MOVEMENT` and `TONGUE_OBSERVATION` MetricTypes** (§2.7). Manual-entry, sidecar fields. Closes the largest *Ashtavidha pariksha* gaps with existing infrastructure.

5. **Ayurvedic substances in `MedicationAnnotationRepo`** (§2.11). Extend `substance_key` vocabulary; pattern-explanation builder picks them up automatically. Cross-references can sit in `BiomarkerReference.kt` alongside Western citations.

6. **Dinacharya alignment surface** (§2.4). Pull-side rendering of the owner's existing sleep / meal / activity timing against the classical kalas. Reuses CIRCADIAN_PHASE_SHIFT and SLEEP_REGULARITY; no new computation, just a different visualisation.

**Tier C — engine-level, multi-day each**

7. **Agni composite** (§2.2). Postprandial glucose excursion + body-temp + (when present) bowel pattern → four-state Agni classifier. Sits next to the existing `metabolicDrift` pattern.

8. **Ritucharya seasonal modifier** (§2.5). Extend `PhysiologyState`-style gating with a `RitucharyaSeason` axis. Per-pattern threshold offsets, owner-set initially, auto-detected by date+locale as a follow-up.

9. **`TreatmentCourse` entity** (§2.12). Brackets Panchakarma / *Rasayana* programmes; suppresses pattern-firing during, computes pre/post comparison summaries.

**Tier D — flagged, not recommended (yet)**

10. **Marma point modelling** (§2.9). Defer to a third-party *vaidya*-facing companion app. Bios provides the HRV / temp data via ContentProvider; the companion handles the anatomical-point overlay.

11. **Nadi pariksha automation**. The classical three-finger pulse-quality assessment is not what wearable optical-HR sensors capture. Owner self-report (`PULSE_QUALITY` manual entry per §2.7) is the manifesto-clean closure; algorithmic *Nadi pariksha* should be left to the *vaidya*.

**Do not adopt**

- A composite "Tridosha balance score" or "Ayurvedic health index" (§3.4). Same reason DATA_MODEL.md guards against a composed epigenetic age — evaluating the person is not Bios's job.
- Push-side dosha alerts ("your Pitta is elevated"). Surfacing the projection is fine; pushing it as a notification is exactly what the [AlertContentPolicy](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) banlist exists to prevent.
- Automated Ayurvedic prescription. Bios records what the owner annotates; the practitioner prescribes.

---

## 5. Summary line for the project

> Bios's bones are deeply Ayurvedic — *Prakriti-relative baseline*, *instrument-not-coach*, *Swasthavritta surveillance*, *Rasayana-class long-window monitoring* — but its vocabulary is entirely modern-biomedical. The single highest-leverage change a BAMS-trained reader could ask for is **a pull-side Ayurvedic-vocabulary overlay** on the patterns Bios already detects: rename `chronic_inflammation` to *Ama-sanchaya* in the practitioner-facing view, project HRV / sleep / skin-temp onto Vata-Pitta-Kapha axes, surface *Dinacharya / Ritucharya* alignment as a chronobiology-with-classical-reference view. Schema-side, the cheapest additions are `BOWEL_MOVEMENT`, `TONGUE_OBSERVATION`, `URINE_OBSERVATION` as manual-entry MetricTypes to cover the missing *Ashtavidha pariksha* axes, and extending the existing `MedicationAnnotationRepo` substance vocabulary to include AYUSH-coded Ayurvedic *dravyas*. None of these violate the manifesto. None require new sensors. All would make Bios a credible adjunct in a BAMS consultation rather than the untranslated allopathic-instrument-only surface it is today.
