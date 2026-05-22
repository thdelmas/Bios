# Unani Tibb Audit — Bios as a Wearable Adjunct to Bedside Tibb

**Scope:** Bios's clinical reach as a wearable, on-device monitoring layer evaluated against the diagnostic, regimenal and preventive framework of Unani Tibb (طب یونانی) — the Greco-Arabic / Islamic medical system codified by Ibn Sina, Al-Razi and Al-Zahrawi, and today state-licensed in India (BUMS / MD-Unani via CCIM-NCISM), Pakistan (BEMS via NCT), Bangladesh (BUMS via BUMS Council) and Sri Lanka, with living practice continuing in Iran (where it overlaps with revived Tibb-e Sonnati), Afghanistan, parts of Central Asia and the Levant.
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Hakim / BUMS-trained reader. Not a regulatory audit. Not a comparative-efficacy argument. This document neither validates nor invalidates Unani Tibb as a clinical system — see point 3 of [MEDICAL_SPECIALTIES_WORLDWIDE.md §15](MEDICAL_SPECIALTIES_WORLDWIDE.md#15-notes-on-the-catalogue). What it does ask: *would Bios, as it stands, be useful in the consulting room of a practising Hakim?*
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [docs/audits/MEDICAL_SPECIALTIES_WORLDWIDE.md](MEDICAL_SPECIALTIES_WORLDWIDE.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt).

---

## Executive summary

Read from the standpoint of a Hakim, Bios is best understood as **a high-resolution longitudinal observation layer for those of the *Asbab e Sitta Zarooriya* that biomedicine has already instrumented** — sleep/wake (Naum wa Yaqzah), motion/rest (Harkat wa Sukoon), and a sliver of mental states (Infial-e-Nafsani). On those three axes its instruments are genuinely good. Its underlying posture — silence-as-feature, instrument-not-coach, the owner is final, evaluation belongs to the owner — *rhymes* unusually well with Tibb's tradition of *Hifzan-e-Sehat* (preventive medicine, طب وقائی): the Hakim observes, advises, hands the choice to the patient, and treats lifestyle (تدبیر) before drugs (دوا). On those grounds I would call this the most philosophically Tibb-compatible health app I have audited in the consumer category.

What it does *not* do — and what a Hakim reading the code will see immediately — is engage with any of Tibb's primary diagnostic and theoretical scaffolding. There is no model of *Mizaj* (مزاج, temperament), no representation of the four humours (*Akhlat-e-Arba*: *Dam, Balgham, Safra, Sauda*), no surface for *Nabz* (نبض, the ten-quality pulse) beyond rate, no *Bawl wa Baraz* (urine and stool observation), and no integration point for any modality of *Ilaj bil Tadbeer* (regimenal therapy) — *Hijama, Fasd, Taleeq, Dalk, Riyazat, Hammam, Sout, Nutool*. The wearable surface and the FHIR lab-import path are by themselves alien to classical bedside Unani, which derives its diagnosis from the practitioner's senses, not the patient's instruments. They are not *incompatible* — they are simply orthogonal to how the Hakim works.

The list below orders gaps by the impact a working Hakim would feel, not by engineering effort.

1. **No representation of *Mizaj*.** The single most load-bearing diagnostic primitive in Unani — the nine-fold compound temperament (الحارّ الرطب, الحارّ اليابس, البارد الرطب, البارد اليابس, plus the four simple and the equable *muʿtadil*) — has no place in the schema. Every signal-rule treats every owner identically. A *sanguine* (دموی) constitution and a *melancholic* (سوداوی) constitution metabolise stress, illness and exertion differently; Unani treats them differently from first principles. Bios, by contrast, treats them with a single rolling 14-day z-score. This is not a failure of Bios — it is simply that the most important Unani primitive is absent, and a Hakim will compensate for it externally. A `Mizaj` annotation field, even free-text, would let a Hakim record the owner's constitution alongside the readings for their own use; no engine logic needed for it to be valuable at the bedside.
2. **No humoral mapping of metrics.** Inflammation, fluid balance, glucose handling, autonomic tone — the wearable and biomarker signatures Bios surfaces map *partially* onto humoral pathology (sustained inflammation has classical kinship with *Safra* الصفراء / yellow bile excess; fluid retention and lethargy with *Balgham* البلغم / phlegm excess; sustained sympathetic activation and "dryness" with *Sauda* السوداء / black bile; *Damawi* الدموی states with elevated cardiovascular drive). A Hakim could readily annotate the surface themselves, but the schema offers no anchor — no `HumoralCorrelate` enum, no per-pattern "what classical analogue does this map onto." Adding one would be cheap and would let a Hakim read the existing `chronicInflammation`, `cardiovascularStress`, `metabolicDrift` and `recoveryDeficit` patterns through the frame they actually think in.
3. **The *Nabz* (pulse) reduction.** Wearable optical HR and ECG give the Hakim *one* of the ten classical pulse qualities — *al-sur'a wa'l-buṭ'* (speed) — and a partial second via HRV irregularity (*nizam* / regularity). Eight others remain invisible to the device: *al-kibar wa'l-ṣighar* (size / amplitude), *al-quwwa wa'l-ḍa'f* (strength), *al-līn wa'l-ṣalābah* (softness / hardness, the closest classical equivalent to modern arterial-stiffness assessment), *al-imtilāʾ wa'l-khulūw* (fullness), *al-ʿarḍ wa'l-ʿumq* (breadth / depth), *al-istiwāʾ wa'l-ikhtilāf* (evenness), *al-tawātur wa'l-tafāwut* (frequency interval), and *al-naghamāt* (rhythmic patterns / *ghazāl*, *dhanab al-faʾr*, etc.). Bios cannot *measure* these without different sensors, and that is fine — but its pulse surface in [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) is implicitly framed as *the* pulse, not as one quality of it. A Hakim who reads the AFib screen pattern needs to understand it as *rate-and-rhythm-only*, not as a substitute for *Jasse Nabz* (palpation). Naming the constraint in the pulse-related metric-info sheets (the Phase from branch `feat/metric-info-sheets-on-read`) would be the manifesto-consistent fix.
4. **No surface for *Ilaj bil Tadbeer***. Regimenal therapy — *Hijama* (حجامة, cupping, wet and dry), *Fasd* (فصد, venesection), *Taleeq* (تعلیق, leech therapy), *Dalk* (دلک, therapeutic massage), *Riyazat* (ریاضت, prescribed exercise), *Hammam* (حمام, therapeutic bath), *Sout* (سَعوط, nasal insufflation), *Nutool* (نطول, irrigation/affusion), *Imala* (إمالة, derivation), *Takmid* (تکمید, fomentation) — is the *first* therapeutic layer in Tibb, before single drugs (*Mufrad*) and before compound formulations (*Murakkab*). Bios has no representation of any of these. There is no `RegimenalEvent` analogue to the `EXERCISE_SESSION` payload table, no way to log "Hijama session, 6 cups, dorsal, 12 ml exudate, post-procedure RHR/SpO2 trace" so the Hakim can correlate the regimen with the wearable trend. This is the single highest-leverage Tibb-specific addition Bios could make: regimenal therapy *is* the day-to-day work of the Hakim, and a wearable instrument that could timestamp the procedure and show the next 72 hours of cardiovascular and sleep response would be genuinely useful adjunctive evidence at the bedside.
5. **No *Bawl wa Baraz*.** Urine examination (*Tafarrus al-Bawl*) and stool observation are second only to *Nabz* in classical Unani diagnostics. The *Ten Qualities of Urine* (colour, consistency, odour, sediment, foam, quantity, frequency, etc.) are taught explicitly and are the route by which renal, hepatic, biliary and metabolic disturbance enter the Hakim's reasoning. Bios's FHIR import surface accepts serum lab biomarkers but offers no place to record urine observation. A free-text `UrineObservation` annotation, even unparsed, would let the Hakim's daily note enter the corpus the owner controls.
6. **No seasonal / climatic modulation.** Greco-Arabic medicine ties humoral balance to the four seasons (*spring* → *Dam*, *summer* → *Safra*, *autumn* → *Sauda*, *winter* → *Balgham*) and to geography (*Aab-o-Hawa*, climate) — every classical regimen specifies seasonal adjustments. Bios's baseline engine is a flat 14-day rolling window with no seasonal stratification. A Hakim in Hyderabad in May and a Hakim in Tehran in January are working in different humoral environments; the patient too. There is no equivalent of the *PhysiologyState* gating (which exists for pregnancy, athletes, frailty per the Phase 8.7 work referenced in [ConditionPatterns.kt:207](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L207)) for season. This is a real gap — and one Tibb would also expect biomedicine to be wrong about, which is interesting.
7. **No surface for *Mufrad* and *Murakkab dawai*.** The primary-care audit ([MEDICAL_PROFESSIONAL_POV.md §2.5](MEDICAL_PROFESSIONAL_POV.md#25-no-medication--current-treatment-context)) already flagged the medication-context gap for allopathic prescribing, and the engineering response (the `MedicationAnnotationRepo` read in [AnomalyDetector.kt:374-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L374-L378)) has begun to close it. From a Tibb angle the same surface needs a wider vocabulary: the daily corpus of a Hakim's practice runs on single drugs (e.g. *Khar Khasak / Tribulus*, *Sana Makki / Senna*, *Asgand / Withania*, *Mastagi Rumi / Mastic*, *Zafran / Saffron*) and compound formulations (*Habb*, *Sufoof*, *Joshanda*, *Sharbat*, *Majun*, *Khamira*, *Itrifal*, *Arq*) — none of which map onto RxNorm or the ATC tree the West uses. A free-text medication annotation already exists, which is the right primitive; what would help the Hakim is the existence of a non-Latinate placeholder for these names in the medication-context surface, so that "Itrifal Ustukhuddus" entered by the owner is not silently broken or rewritten by an autocorrect pipeline.
8. **The *Quwa* (faculties) frame is implicit, not named.** Tibb organises bodily function around three sets of faculties — *Quwwat-e-Tabi'iyya* (طبيعية, natural / vegetative — digestion, growth, generation), *Quwwat-e-Hayawaniyya* (حيوانية, vital — cardiorespiratory, the heart's animation), and *Quwwat-e-Nafsaniyya* (نفسانية, psychic — perception, voluntary motion, cognition). The Bios pattern library implicitly distributes itself across these: the *Hayawaniyya* maps onto `CARDIOVASCULAR` + `RESPIRATORY`, the *Tabi'iyya* onto `METABOLIC` + parts of `WOMENS_HEALTH`, the *Nafsaniyya* onto `MENTAL_HEALTH`. A Hakim could read the existing `ConditionCategory` enum ([Enums.kt:103-104](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L103-L104)) through that lens, but no part of the surface acknowledges the framing. This is a low-cost, high-meaning rename / co-label at the metric-info-sheet level.

The remaining sections expand on each item, name the genuine alignments, and offer the points at which the Hakim would *choose* to use Bios in their practice.

---

## 1. What Bios already does well, viewed through a Unani lens

| Quality | Evidence | Why a Hakim would value it |
|---|---|---|
| **Posture of *Hifzan-e-Sehat*** | [MANIFESTO.md](../../MANIFESTO.md): "Health is not the absence of disease — it is the presence of awareness"; the prevention-over-reaction principle; *silence-is-a-feature* | This *is* Tibb's first principle. Classical Unani locates the practitioner upstream of pathology — observation, regimen, dietary instruction (*Tadbeer-e-Ghiza*), seasonal adjustment, restoration of the equable temperament. Bios's posture is structurally aligned, not merely cosmetically so |
| **Three of the six *Asbab*** | `SLEEP_*`, `STEPS`, `ACTIVE_MINUTES`, `EXERCISE_SESSION`, the mental-health surface, `AMBIENT_LIGHT`, `AIR_CO2` | *Asbab e Sitta Zarooriya* enumerates the six essential preconditions: *Hawa* (air), *Makool wa Mashroob* (food and drink), *Harkat wa Sukoon* (motion and rest), *Naum wa Yaqzah* (sleep and wakefulness), *Ihtibas wa Istifragh* (retention and evacuation), and *A'rad-e-Nafsaniyya* (mental and emotional states). Bios instruments three of these — sleep, motion, and a sliver of mental — at higher temporal resolution than any classical method permitted, and gestures at a fourth (air, via CO2 and ambient light). That is real coverage, even if the framing isn't named |
| **Personal baseline as the unit** | 14-day rolling baseline, z-score gates against the *owner's own* prior data, not a population mean | Maps cleanly onto Tibb's *individualised mizaj-based reasoning*. Galenic-Avicennian medicine is explicit that "normal" is relative to the individual's temperament; what is *muʿtadil* for a *Damawi* constitution is excess for a *Sauda* one. Population-mean thresholds have always been theologically suspect in Tibb — Bios's personal-baseline default sits in the same posture |
| **"Never evaluate the person"** | [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) — CI-enforced banlist of *"you should," "you need to," "your health score," streaks, badges, leaderboards* | The Hakim-patient relationship is, in classical practice, advisory and discursive — the Hakim *informs* the patient and *the patient decides*. Apps that score the person are alien to that posture. Bios is, of all consumer-health software, the closest to the Hakim's register |
| **Multi-signal convergence over single readings** | `minActiveSignals` ≥ 2 (mostly ≥ 3) on every pattern; *required* gating; biomarker patterns require both a lab anchor and wearable corroboration before firing | Classical *tashkhis* (diagnosis) was always multi-modal — *Nabz* + *Bawl* + complexion + voice + smell + temperature + history. A modern device that fires on a single anomaly is foreign to Tibb's reasoning; a device that requires convergence is recognisable to it |
| **Owner sovereignty over data** | SQLCipher AES-256, on-device, no Play Services, FHIR export only when the owner exports, separate encrypted reproductive DB | A Hakim's clinical relationship presumes patient sovereignty over their record. Bios's privacy posture removes the structural obstacle (cloud capture by foreign platforms, insurer access) that has prevented many traditional-medicine practitioners from adopting digital tools |
| **Free, no subscription gating** | Manifesto principle 3 — "no one should miss an early detection because they cannot afford a subscription" | Tibb in South Asia and West Asia is, for many patients, the medicine of access — the *unani dawakhana* exists alongside an under-resourced biomedical primary-care layer. A free instrument extends that access posture into the digital plane |
| **Polite refusal to compose a *biological age*** | [DATA_MODEL.md L106-112](../DATA_MODEL.md#epigenetic-age-clocks--manifesto-guard) — four epigenetic clocks imported as standalone biomarkers, never composed into a "Bios age" | Classical Tibb's account of ageing — drying of the radical moisture (*Rutubat al-Aṣliyya*) and cooling of the innate heat (*Hararat al-Gharīziyya*) — is qualitatively temperamental, not numerically reductive. Refusing to compress that into a single integer is, accidentally, aligned with Tibb's preference for descriptive over quantitative summary of constitution |
| **FHIR import as an option, not a requirement** | The lab-import path is a Settings flow, not a default; biomarker patterns *require* a lab anchor before firing | Means a Hakim whose primary diagnostic remains *Nabz wa Bawl* is not pressured to manufacture lab values to make Bios useful. The wearable patterns work without any biomedical lab on file |

These alignments are not parity wins from biomedicine — they are independent properties of Bios's design that happen to map onto Tibb's posture better than the consumer-app category does in general.

---

## 2. Gap analysis — section by section

### 2.1 *Mizaj* (مزاج, temperament) is unmodeled

In Unani Tibb, *Mizaj* is what *body type / constitution* tries to be in popular wellness writing — but precise, classical, and load-bearing for every clinical decision. The nine compound temperaments (`Damawi` / sanguine, `Safrawi` / choleric, `Balghami` / phlegmatic, `Saudawi` / melancholic, and the cross-pairings of hot-dry, hot-moist, cold-dry, cold-moist) plus the equable (*muʿtadil*) constitution determine: which foods are safe, which seasons are dangerous, which regimens are indicated, which drugs are appropriate (and at what *darja* / potency grade), and how a given symptom should be interpreted.

[Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt) defines `BaselineContext` (RESTING / ACTIVE / SLEEPING / ALL), `TrendDirection`, `AlertTier`, `ConditionCategory`, `PrivacyTier`, `HealthEventType`. There is no `Temperament` / `Mizaj` field anywhere. The `PhysiologyState` enum referenced in [ConditionPatterns.kt:207](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L207) carries pregnancy / postpartum / athlete / frailty bands — the closest existing primitive — but is dimensionally orthogonal to *Mizaj*.

**Why it matters at the bedside.** Two owners with identical 14-day RHR and HRV traces are *not* clinically identical to a Hakim. A *Safrawi* (hot-dry) constitution running a sustained RHR + 1.5σ above baseline in mid-summer is interpreted as *Safra* excess provoked by season; the same trace in a *Balghami* (cold-moist) constitution in winter is interpreted as something else and merits a different regimen. Bios treats them identically.

**Manifesto-aligned recommendation.** Add a self-described `Mizaj` annotation on Settings — *off by default*, owner-set, free-text or a picker over the nine compounds. The pattern explanation builder reads it (the same way it now reads `MedicationAnnotationRepo`) and *includes* it in the explanation surface ("Annotated temperament: *Damawi*") without using it to alter the engine's z-score arithmetic. The instrument is unchanged; the Hakim reads the annotation alongside the deviation. This costs almost nothing and respects "evaluation belongs to the owner."

### 2.2 The four humours (*Akhlat-e-Arba*) are not visible

The four humours — *Dam* (دم, blood), *Balgham* (بلغم, phlegm), *Safra* (صفراء, yellow bile), *Sauda* (سوداء, black bile) — are the explanatory currency of classical Tibb. Modern biomedicine maps onto them only partially and unevenly, but a Hakim has well-established correspondences in their head while reading any case.

Bios's existing patterns admit partial mappings without strain:

| Bios pattern | Closest classical humoral analogue (a Hakim's reading, not a code claim) |
|---|---|
| [`chronicInflammation`](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) (Furman 2019; hsCRP + RHR↑ + HRV↓ + skin temp drift) | *Safra* غلبة / yellow-bile excess pattern; *hararah ghariba* (foreign heat) — sustained warmth without acute fever |
| [`inflammationSignature`](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) (hsCRP ≥ 1.0 mg/L) | The biomedical instrument that comes closest to *Hararat-e-Ghair-Tabiʿiyya* (unnatural heat). The hsCRP ladder maps unevenly onto Tibb's heat-grading (*darja* I–IV) but a Hakim can read it that way |
| `metabolicDrift` (glucose variability + sleep disruption + RHR drift) | Mixed *Damawi* / *Safrawi* derangement; classical *Ziabetus* (diabetes — the term itself entered Arabic via Greek δια-βαίνειν → دیابیطس) was already understood as a derangement of the digestive faculty and the humoral balance of the liver |
| `cardiovascularStress` (RHR↑, HRV↓, SpO2↓) | *Quwwat-e-Hayawaniyya* (vital faculty) strain; in classical reasoning a *Damawi* / *Safrawi* presentation |
| `recoveryDeficit` (sustained low HRV + high RHR + poor sleep over 14 days) | The closest analogue to *Sauda* غلبة / black-bile dominance — chronicity, dryness, melancholy, sympathetic tone. The pattern even maps onto the classical sequelae (mood, immune compromise, cognitive impairment) |
| `mentalHealthCorrelate` (sleep + HRV + steps + circadian + mood-drift) | *Infial-e-Nafsani* dysregulation — the mental-emotional cause class within the *Asbab e Sitta*. Tibb is explicit that emotional states are aetiological, not just symptomatic |
| `sleepDisruption` (fragmentation + efficiency + HRV + RHR + bedroom CO2) | Affects every humour, classically — *Naum wa Yaqzah* derangement is one of the six essential preconditions. The bedroom-CO2 corroborator is a nice modern echo of Tibb's emphasis on *Hawa* (air quality) as one of the six causes |
| `menstrualCycleAnomaly` | *Amraz-e-Niswan* — the Hakim already has a domain-specific surface (`Ilmul Qabalat wa Amraz e Niswan`); this is the cleanest mapping in the catalogue |

**Recommendation.** A `humoralCorrelate: HumoralFrame?` annotation on `ConditionPattern` (not enforced, owner-readable, region-config-gated so it only renders in locales where Tibb is recognised — India, Pakistan, Bangladesh, Iran, parts of the Levant). The metric-info sheets currently being built on `feat/metric-info-sheets-on-read` would be the right surface for this. No engine logic touches the field; it is descriptive scaffolding the Hakim reads.

### 2.3 *Nabz* (pulse): rate without the other nine qualities

Classical Unani pulse examination identifies *ten primary qualities* of the pulse (the canonical list varies slightly between Avicenna's *Qanun* book I and Al-Razi's *Hawi*, but converges on ten):

1. *al-kibar wa'l-ṣighar* — size / amplitude
2. *al-quwwa wa'l-ḍa'f* — strength / weakness
3. *al-sur'a wa'l-buṭ' wa'l-iʿtidāl* — speed (the one Bios *does* see, via HR)
4. *al-tawātur wa'l-tafāwut* — frequency-of-beats interval (HR variability — Bios partially sees this via RMSSD)
5. *al-līn wa'l-ṣalābah* — softness / hardness (arterial tone)
6. *al-imtilāʾ wa'l-khulūw* — fullness / emptiness
7. *al-istiwāʾ wa'l-ikhtilāf* — evenness / unevenness (HRV irregularity — Bios partially sees this)
8. *al-niẓām wa'l-ikhtilāl* — regularity / irregularity (Bios sees this via the AFib screen)
9. *al-ʿarḍ wa'l-ʿumq* — breadth and depth
10. *al-naghamāt* — the rhythmic / "musical" pulses (*ghazāl* — gazelle, *dhanab al-faʾr* — mouse-tail, *minshāri* — saw-tooth, *naml* — ant-walk, *mawjī* — wavy)

[ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) and the contracts MetricType give Bios solid access to qualities 3, 4, 7, and 8 (rate, variability, irregularity), partial access to *al-quwwa* via PPG amplitude (which the existing adapters discard rather than expose), and no access to 1, 5, 6, 9, or 10. Quality 5 — *al-līn wa'l-ṣalābah* — is the classical analogue of modern arterial-stiffness assessment (pulse-wave velocity, augmentation index), and is the most clinically valuable of the missing qualities; a Hakim trained in pulse-by-palpation can detect what cardiologists now call "stiffening" in patients decades before standard cuff BP catches the corresponding hypertensive drift.

**Recommendation, in two parts.**
- *Honest framing.* The metric-info sheets on `feat/metric-info-sheets-on-read` should, in Unani-recognised locales, name the pulse surface for what it is — *rate and rhythm*, not the comprehensive *Nabz*. A Hakim reading the AFib screen output should not be tempted to treat it as a substitute for palpation.
- *PPG amplitude exposure.* The PPG waveform amplitude (currently discarded after HR extraction in the adapters) is the closest non-cuff signal to *al-quwwa* and *al-līn*. If the adapter pipeline preserved an amplitude / waveform-quality scalar — `PULSE_AMPLITUDE` (CARDIOVASCULAR, SCORE) — the Hakim would have a fifth pulse quality on the screen, derived rather than measured, and a useful one. This is also genuinely useful biomedically (low amplitude correlates with peripheral vasoconstriction, dehydration, shock).

### 2.4 *Bawl wa Baraz* (urine and stool examination)

In classical Unani diagnosis, *Tafarrus al-Bawl* — urine examination — is taught as a structured ten-quality inspection: colour (the canonical ten-colour wheel from *abyaḍ* white through *aṣfar* yellow through *aḥmar* red to *aswad* black, with intermediates), consistency (*qiwām*), odour (*rāʾiḥah*), foam (*raghwah*), sediment (*thafl* — graded *muʿallaq* suspended, *munṣabb* settled, *mustanqaʿ* layered), quantity, frequency, time-of-day, transparency, and the rare qualities (*zubdiyya* — buttery; *daysamiyya* — fatty). Stool observation runs in parallel for *Tabi'iyya* (digestive-faculty) assessment.

Bios's FHIR import surface ([§8.6 of the roadmap](../ROADMAP.md#86-lab--biomarker-inbound-surface-foundation-shipped)) accepts the serum and CBC panels — 16 keys, LOINC-coded, with clinical bands per region. There is no urine surface beyond what FHIR might carry as a separate Observation (urinalysis dipstick → individual LOINC codes for protein, glucose, ketones, blood, etc.), and the importer's mapping table is keyed on serum-specific LOINCs. There is no stool surface at all.

**Why it matters at the bedside.** The Hakim's daily clinical note routinely contains *Bawl* and *Baraz* observations. A patient who reports their morning urine looked *aṣfar fāqiʿ* (deep saffron-yellow) with *zubdiyya* foam is communicating dehydration + protein, which a Hakim hears clearly. There is currently no place in Bios for that note to live alongside the wearable trend.

**Recommendation.** A `BodilyFluidObservation` annotation under `MetricDomain.BIOMARKER` — free-text, owner-attached, never parsed by the engine. Optional structured fields (colour from the ten-colour wheel; consistency; sediment) for owners who want to use them, omitted for those who don't. The FHIR import path already handles `valueQuantity`-bearing Observations; this is the symmetric pull-side surface for the qualitative observation the Hakim makes at the bedside. Implementation cost is low; clinical cost of omission is the entire urine diagnostic tradition.

### 2.5 *Ilaj bil Tadbeer* — no integration point for regimenal therapy

This is the largest *Tibb-specific* gap in Bios, and the highest-leverage one to close.

*Ilaj bil Tadbeer* (علاج بالتدبیر) is the first-line therapeutic register in Unani — regimenal therapy, used before single drugs and before compound formulations. The CCIM-NCISM BUMS curriculum teaches it as a discipline in its own right (one of the formal Unani specialties in [MEDICAL_SPECIALTIES_WORLDWIDE.md §4](MEDICAL_SPECIALTIES_WORLDWIDE.md#4-unani-tibb-greco-arabic--islamic-medicine)). The principal modalities:

- **Hijama** (حجامة, cupping) — dry (*jaffa*) and wet (*ratba*, with scarification and exudate collection). Practised throughout the Muslim world; also a recognised ICD-11 traditional-medicine intervention. Indicated for headache, musculoskeletal pain, hypertension, dysmenorrhoea, sciatica, and a long list of *kayfiyya* (qualitative) imbalances.
- **Fasd** (فصد, venesection / phlebotomy) — therapeutic bloodletting from named veins (*basilic*, *cephalic*, *median cubital*, *saphenous*). Now restricted in many BUMS jurisdictions but still practised; the closest modern analogue is therapeutic phlebotomy for haemochromatosis or polycythaemia.
- **Taleeq** (تعلیق, leech therapy / *Hirudo*) — recognised in modern microsurgical practice (venous-congestion management) and still in classical Unani indications (varicose disease, certain dermatological presentations).
- **Dalk** (دلک, therapeutic massage) — multiple subtypes: *Dalk Layyin* (soft), *Dalk Khashin* (rough), *Dalk Mu'tadil* (moderate), with classical indications around *Quwwat-e-Hayawaniyya* circulation and *Tabi'iyya* digestion.
- **Riyazat** (ریاضت, prescribed exercise) — graduated, prescribed by intensity and timing in relation to meals and seasons. The closest analogue to the modern *exercise prescription*; Bios's `EXERCISE_SESSION` payload table can already carry this if a Hakim prescribes it.
- **Hammam** (حمام, therapeutic bath) — hot, cold, alternating; medicated; with named regimens for specific *amraz*. Closest modern analogues are contrast hydrotherapy and the European *Kneipp* tradition (the Hammam-tradition is older).
- **Sout** (سَعوط, nasal insufflation), **Nutool** (نطول, irrigation / affusion), **Imala** (إمالة, derivation), **Takmid** (تکمید, fomentation), **Aabzan / Mahin** (sitz / vaginal bath) — the long-tail modalities.

[ROADMAP.md](../ROADMAP.md) and [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) contain no entry that maps onto any of these. `EXERCISE_SESSION` exists, can be co-opted for *Riyazat*, and is the precedent for what the rest should look like.

**Recommendation — a `REGIMEN_EVENT` MetricType.** New key `REGIMEN_EVENT` (TRADITIONAL_MEDICINE domain, EVENT unit, value always `1.0`), with a structured `EventPayloadField` schema:

| Field key | Type | Notes |
|---|---|---|
| `modality` | `string_value` | Free string or controlled vocabulary including `HIJAMA_DRY`, `HIJAMA_WET`, `FASD`, `TALEEQ`, `DALK_LAYYIN`, `DALK_KHASHIN`, `RIYAZAT`, `HAMMAM`, `SOUT`, `NUTOOL`, `IMALA`, `TAKMID`, `AABZAN`, `OTHER`. Open vocabulary — the Tibb taxonomy has long-tail entries the Hakim should be able to record verbatim |
| `practitioner_role` | `string_value` | `SELF`, `HAKIM`, `OTHER_TM_PRACTITIONER`, `BIOMEDICAL_CLINICIAN` — clinically useful provenance for the wearable correlation downstream |
| `start_utc` | `long_value` | epoch ms |
| `end_utc` | `long_value` | epoch ms — nullable for instantaneous events |
| `site` | `string_value` | Free-text anatomical site / cupping point / vein |
| `note` | `string_value` | Owner-private Hakim notes — never read by engine, mirrors [DATA_MODEL.md L162-167](../DATA_MODEL.md) |

The cross-correlation payoff: the existing pattern engine could ship one Tibb-aware pattern — *post-Hijama autonomic recovery trace* — that runs a 72-hour HRV / RHR window from the timestamp of a `REGIMEN_EVENT` with `modality = HIJAMA_*` and surfaces the trajectory on the diagnostic screen. This is information-only, never evaluative; it is the wearable equivalent of the Hakim's classical follow-up palpation 72 hours after the procedure. The literature anchor for that pattern would be the modern Hijama research base ([Aboushanab & AlSanad 2018](https://www.sciencedirect.com/science/article/pii/S2225411017301141) and the JIMA cupping literature) which is already in the peer-reviewed corpus the existing pattern citations draw from.

This is the single most Tibb-aligned addition Bios could ship, and the cost is bounded: one new MetricType, one new payload schema, one new ConditionPattern. No engine rework.

### 2.6 No seasonal modulation (*Faṣl* and *Aab-o-Hawa*)

The Greco-Arabic seasonal framework is explicit: each of the four seasons aligns with one of the four humours (spring → *Dam*; summer → *Safra*; autumn → *Sauda*; winter → *Balgham*), and the regimen is adjusted accordingly. *Tadbeer-e-Faṣli* (seasonal regulation) is a discrete section of every classical *Qanun*-derived clinical manual. *Aab-o-Hawa* (climate-and-air) extends the same reasoning to geography.

Bios's baseline engine (14-day rolling) has no seasonal stratification. The `PhysiologyState` enum (used in [ConditionPatterns.kt:36](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L36) for `excludedStates`) carries pregnancy, postpartum, frailty, athlete bands but no `SeasonContext`. A Hakim in Lucknow in May (mid-summer, *Safra*-dominant) and a Hakim in Lucknow in January (winter, *Balgham*-dominant) are reading the same patient through different humoral lenses. The regional config (`RegionConfigProvider`) is locale-aware but season-blind.

**Recommendation.** A `SeasonContext` derivation from device locale + date — cheap to compute, never sent off-device, gated by the same Unani-region check as the *Mizaj* annotation. Exposed in the metric-info sheets and the pattern explanation: "Detected in late spring (*Rabi*) for this region. Classical seasonal correspondence: *Dam*." The engine does *not* re-weight thresholds by season (that would be an evaluative claim Bios should not make); the surface simply renders the contextual frame the Hakim reasons in.

A second, optional layer: an *air-quality* corroborator. `AIR_CO2` is already on the bus ([ConditionPatterns.kt:175-176](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L175-L176) via the bedroom-CO2 rule in `sleepDisruption`). The same pattern, generalised to include particulate matter (PM2.5) from a paired BLE air-quality sensor (issue #43 lineage in the codebase) would let the Hakim read environmental *Hawa* as a classical *Asbab*.

### 2.7 *Mufrad* and *Murakkab dawai* — the pharmacopeia gap

Tibb's daily clinical work runs on two pharmaceutical layers below regimenal therapy:

- *Mufrad dawai* (single drugs) — *Khar Khasak* (Tribulus), *Sana Makki* (Cassia angustifolia / Senna), *Asgand Nagori* (Withania somnifera), *Zafran* (Crocus sativus / saffron), *Mastagi Rumi* (mastic), *Aloe-Vera* (*Sibr*), *Kalonji* (Nigella sativa), *Habba Sauda* (the same), and several hundred others catalogued in classical *Ilmul Advia* texts (Al-Biruni's *Saidana*, Ibn al-Baytar's *Jamiʿ*) and their modern updates (the *Unani Pharmacopoeia of India*, the *Khwasul Advia* of the Indian Ministry of AYUSH).
- *Murakkab dawai* (compound formulations) — *Habb* (pills), *Sufoof* (powders), *Joshanda* (decoctions), *Sharbat* (syrups), *Majun* (electuaries — *Majun Najah*, *Majun Mughalliz*, *Majun Salajeet*), *Khamira* (fermented preparations — *Khamira Marwareed*, *Khamira Gawzaban*), *Itrifal* (the trifala-based group — *Itrifal Ustukhuddus*, *Itrifal Sageer*, *Itrifal Mulayyin*), *Arq* (distillates — *Arq Gulab*, *Arq Mako*, *Arq Badiyan*), *Roghan* (medicated oils).

The primary-care audit already flagged the medication-context gap for Western prescribing ([MEDICAL_PROFESSIONAL_POV.md §2.5](MEDICAL_PROFESSIONAL_POV.md#25-no-medication--current-treatment-context)). Bios responded with `MedicationAnnotationRepo` (read in [AnomalyDetector.kt:374-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L374-L378)), which appears to be free-text rather than RxNorm-coded. **That is the right primitive for Tibb too** — the Hakim's pharmacopeia simply does not map onto RxNorm, ATC, or any biomedical terminology service in a usable way. The existing free-text field already accommodates *Itrifal Ustukhuddus* if the owner enters it.

The remaining gap is therefore not the data shape but the surface and the localisation:

- The medication-entry screen ([MedicationsScreen.kt](../../android/app/src/main/java/com/bios/app/ui/medications/MedicationsScreen.kt)) should *not* steer the owner toward Western drug names in Unani-recognised locales — no English-only autocomplete, no validation that fails on Devanagari / Urdu / Arabic script, no implicit Latin-script assumption.
- The pattern-explanation surface that names "Annotated current medications: ..." should treat *Mufrad* and *Murakkab* names as first-class strings. A Hakim's daily prescription of "*Sharbat Bazoori* 30ml BD" should appear in the explanation verbatim.

**Recommendation.** A unicode + RTL audit of the medication-entry path (Phase work, not a one-day change), and an explicit note in the region-config for India / Pakistan / Bangladesh / Iran that "medication" includes the entire Unani pharmacopeia — no rewriting, no autocorrect, no Latinate normalisation.

### 2.8 *Quwa* (faculties) — the implicit organisation

Tibb organises bodily function into three faculty-classes:

- *Quwwat-e-Tabi'iyya* (طبيعية) — the natural / vegetative faculties: nutrition (*ghādhiya*), growth (*nāmiya*), generation (*muwallida*), and the four servant powers (*jādhiba* attractive, *māsika* retentive, *hāḍima* digestive, *dāfiʿa* expulsive). These are *liver-anchored* in the classical scheme.
- *Quwwat-e-Hayawaniyya* (حيوانية) — the vital faculties: pulse, respiration, the animation of warmth. *Heart-anchored*.
- *Quwwat-e-Nafsaniyya* (نفسانية) — the psychic faculties: perception (the five external senses + the five internal — *al-ḥiss al-mushtarak* common sense, *al-khayāl* imagination, *al-mufakkira* cogitation, *al-wāhima* estimation, *al-ḥāfiẓa* memory), voluntary motion, cognition. *Brain-anchored*.

Bios's existing `ConditionCategory` enum implicitly distributes itself across these:

- CARDIOVASCULAR + RESPIRATORY → *Quwwat-e-Hayawaniyya*
- METABOLIC + parts of WOMENS_HEALTH + the digestive subset of MENTAL_HEALTH (appetite) → *Quwwat-e-Tabi'iyya*
- MENTAL_HEALTH + SLEEP + cognitive aspects of RECOVERY → *Quwwat-e-Nafsaniyya*
- INFECTIOUS + SAFETY → cut across all three

**Recommendation.** This is the cheapest of the suggestions in this audit: the metric-info sheets on `feat/metric-info-sheets-on-read` can name the *Quwa* correspondence in Unani-region locales without changing a line of engine code. The Hakim then reads "Possible illness onset detected" through the frame "deviation across *Hayawaniyya* + *Tabi'iyya*" — which is how they would write the case note anyway.

### 2.9 Bedside diagnosis as the primary modality

This is not a *gap* in the Bios codebase. It is a *philosophical observation* the Hakim reading this code will make on their own, and I record it here for symmetry with the primary-care audit's manifesto-vs-clinical-practice section ([MEDICAL_PROFESSIONAL_POV.md §3](MEDICAL_PROFESSIONAL_POV.md#3-manifesto--clinical-ethics-tension-points)).

Classical Unani diagnosis is *Tashkhis bil-Hawass* — diagnosis through the practitioner's own senses, structured around *Nabz*, *Bawl*, *Baraz*, *Lawn* (complexion), *Sawt* (voice), *Rāʾiḥa* (smell), *Mass* (touch / palpation), and *Istinṭāq* (interrogation / history-taking). The diagnostic locus is the Hakim's hands, eyes, ears, nose, and the patient's report. Instruments — the patient's own wearables, the lab — are *adjuncts*, not the source of truth.

Bios is therefore, from a Hakim's standpoint, **a longitudinal sensory adjunct**. It does not replace *Tashkhis bil-Hawass*; it cannot, structurally. What it *does* is offer a window of objective observation between consultations: a 14-day record of *Nabz*-rate, *Nabz*-rhythm, sleep architecture, motion, and (when the owner reports them) the lab markers that didn't exist in classical practice. This is materially useful to a Hakim — *but only if it is framed as such*. A Hakim's risk in adopting Bios is not over-trust in any single reading; it is the slow drift of the practitioner's own clinical attention from the bedside to the screen. That drift is well-documented in the biomedical literature on EHR-mediated consultations, and it would be a real loss in Tibb.

The manifesto's posture mitigates this. *Silence is a feature.* *The instrument is not the practitioner.* *Evaluation belongs to the owner* (and, by extension, to the practitioner the owner consults). These are Bios's own principles, articulated independently, and they happen to be the right *philosophical hygiene* for a Tibb-adjacent use case.

### 2.10 Mental and emotional states (*Infial-e-Nafsani*) as aetiology

Classical Unani is explicit that the six *A'rad-e-Nafsaniyya* (mental and emotional accidents) — *Ghadab* (anger), *Ghamm* (grief / depression), *Khauf* (fear), *Surur* (joy / mania, when excessive), *Hayrah* (anxiety), *Hubb* (love / attachment) — are aetiological, not merely symptomatic. They sit alongside the other five *Asbab e Sitta* as *causes* of disease, not consequences.

Bios's `mentalHealthCorrelate` pattern ([ConditionPatterns.kt L424-459](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459)) is one of the most ambitious in the catalogue — sleep + HRV + steps + circadian + mood-drift + typing-cadence, with three of seven signals required. The W2F companion writes the mood-drift and circadian signals back. The framing in the explanation text is *physiological pattern shift*, not mental-health diagnosis — which is the manifesto-correct posture.

For a Hakim, this pattern is *already legible*: it maps onto *Infial-e-Nafsani* dysregulation. The only thing missing is the explicit naming, which the metric-info sheets could supply.

**Recommendation.** In Unani-region locales, label the `mentalHealthCorrelate` pattern's info sheet with the classical correspondence — "*Infial-e-Nafsani* derangement: emotional state recognised in Tibb as an *Asbab Zarooriya* (essential cause)." Same engine, same logic, same alert text. Different framing for the practitioner reading it.

### 2.11 *Hifzan-e-Sehat* — actively aligned, name it

This is the closest single alignment between Bios and Unani. *Hifzan-e-Sehat* (حفظ الصحة, preservation of health) is the classical preventive-medicine discipline — the work of keeping the equable temperament equable, the six *Asbab* in balance, the *Quwa* unburdened. The BUMS curriculum carries it as a discipline in its own right, alongside *Tahaffuzi wa Samaji Tibb* (preventive and social medicine).

The manifesto is *Hifzan-e-Sehat* re-stated in modern English: *"prevention over reaction," "catch a pattern weeks earlier," "the difference between a manageable condition and a crisis is often just time."*

**Recommendation.** In the Unani-region locale's onboarding text, name the alignment. Bios is not "wellness software." It is, philosophically, the digital instrument-arm of *Hifzan-e-Sehat*. A Hakim reading that framing will understand instantly what the device is for and what its limits are. This costs one paragraph in the localisation file.

---

## 3. Manifesto / Tibb-philosophy alignment points

These are not gaps. They are points where Bios's manifesto and Tibb's posture *converge*, in some cases more cleanly than Bios's manifesto and biomedical posture do.

### 3.1 *"The instrument is not the practitioner"*

The manifesto's "instrument, not coach" formulation is essentially the Avicennian distinction between the *ālah* (آلة, instrument / tool) and the *ṭabīb* (طبيب, physician). In Tibb the instrument never has agency; it is the *ṭabīb* who reasons, and the patient (*marīḍ*) who decides. The manifesto restates this in modern terms. This is the most important single alignment in this audit.

### 3.2 *"Silence is a feature"*

Tibb explicitly warns against the practitioner who speaks more than they observe. The *Qanun* and the *Hawi* both contain passages on the discipline of clinical patience — the willingness to wait, to re-palpate, to refrain from premature pronouncement. Bios's silence-as-feature is the same discipline encoded into a notification policy.

### 3.3 *"Evaluation belongs to the owner"*

Classical Tibb's clinical relationship presumes the patient's *iḥtiyāj* (agency) — they consult the Hakim, hear the advice, and decide. The Hakim's role ends at advisement; the patient's begins at decision. Bios's manifesto Principle 7 is the same posture, redrawn for a software instrument that the owner consults rather than the practitioner.

### 3.4 *No subscription gating*

Tibb in South Asia, in particular, is the medicine of access — the *unani dawakhana* in a town of a hundred thousand is often the only affordable health-care option. Bios's commitment to no-subscription, no-Play-Services, free-to-everyone aligns with that access posture more cleanly than any commercial wearable platform does. A Hakim in a tier-3 Indian city or a Pakistani district headquarters can reasonably tell a patient *"use this; it is free; the data does not leave your phone"* — a sentence that does not work with Apple Health, Samsung Health, Oura, WHOOP or Garmin.

### 3.5 The reproductive-data isolation

The separate SQLCipher database for `WOMENS_HEALTH` ([§Current State](../ROADMAP.md#current-state-v020), independent key, independent wipe, FHIR exporter skips by default) is correctly motivated by the post-Dobbs threat model. From a Tibb angle, it has an additional resonance: *Amraz-e-Niswan* (women's diseases) has historically been a sensitive practice domain in Muslim and South Asian contexts, with strong patient-privacy norms that Bios's isolation respects without naming.

---

## 4. What I would recommend, prioritised

**Tier A — high value, low engineering cost, defensible against the manifesto**

1. **Name *Hifzan-e-Sehat***. One-paragraph addition to the Unani-region localisation; aligns the framing the Hakim sees with the framing they reason in.
2. **Name *Quwa* and *Infial-e-Nafsani* correspondences in the metric-info sheets.** The new sheets on `feat/metric-info-sheets-on-read` are the natural surface. No engine change.
3. **Add a `Mizaj` annotation field.** Off by default, owner-set, free-text. Read by the pattern-explanation builder the same way medication annotations are now read. No engine logic; descriptive only.
4. **Name the pulse surface honestly.** In Unani-recognised locales, the AFib screen and the cardiovascular patterns should explicitly state that the wearable pulse is *rate and rhythm only*, not classical *Nabz*. One-sentence addition to the relevant info sheets.

**Tier B — moderate engineering, high Tibb-specific payoff**

5. **`REGIMEN_EVENT` MetricType with payload schema** (§2.5). One MetricType, one payload table extension, one ConditionPattern for post-Hijama autonomic recovery. This is the single highest-impact Tibb-aligned addition.
6. **`BodilyFluidObservation` annotation under BIOMARKER** (§2.4) — the *Bawl wa Baraz* surface. Free-text by default; optional structured fields for owners who want them.
7. **Unicode / RTL / non-Latin medication-name audit** (§2.7) on `MedicationsScreen.kt`. Ensures *Mufrad* and *Murakkab* names enter the annotation surface verbatim and render in the explanation correctly.
8. **`SeasonContext` derivation** (§2.6) — locale + date, surfaces classical seasonal correspondence in info-sheet text. No threshold re-weighting (that would be evaluative).

**Tier C — would be valuable but require sensor or pipeline work**

9. **PPG amplitude surface** (`PULSE_AMPLITUDE`, CARDIOVASCULAR, SCORE; §2.3). Closest the wearable surface can get to *al-quwwa* and *al-līn*. Useful biomedically as well.
10. **Particulate-matter / *Hawa* corroborator** in the existing `sleepDisruption` and `circadianDisruption` patterns (§2.6, building on the existing `AIR_CO2`).
11. **`HumoralCorrelate` annotation on `ConditionPattern`** (§2.2). Descriptive, region-config-gated, never read by the engine. Renders in metric-info-sheet text only.

**Do not adopt**

- Engine-level humoral classification. Auto-assigning a pattern to "*Safrawi* dominance" without owner / Hakim input would be an evaluative claim Bios should not make. The manifesto's "instrument not coach" rules this out, and Tibb's own posture ("*tashkhis* belongs to the *ṭabīb*, not the *ālah*") agrees.
- Engine-level *Mizaj* threshold adjustment. The 14-day rolling personal baseline is, paradoxically, the most *muʿtadil*-respecting primitive available — it is calibrated to the individual, which is the same direction Tibb wants. Modulating thresholds by self-reported *Mizaj* would inject an evaluative layer the manifesto bars and the Hakim would not trust without their own examination.
- Any framing that positions Bios as a *substitute* for *Tashkhis bil-Hawass*. The wearable is adjunctive; the bedside is primary. This should be made explicit in the Unani-region onboarding, not implicit.

---

## 5. Summary line for the Hakim reading the code

> Bios is, philosophically, the most Tibb-compatible consumer health instrument in its category — its silence-as-feature, instrument-not-coach, owner-final posture maps cleanly onto *Hifzan-e-Sehat* and the Avicennian *ṭabīb/ālah* distinction. Its sensors give a Hakim three of the six *Asbab e Sitta Zarooriya* (sleep, motion, partial mental) at unprecedented temporal resolution, plus a useful adjunctive lab surface via FHIR import. It does not — and structurally cannot — replace *Nabz*, *Bawl*, *Baraz* or *Tashkhis bil-Hawass*; it is an instrument between consultations, not a diagnostic peer. The Tibb-specific additions that would materially improve it at the bedside are a *Mizaj* annotation, a `REGIMEN_EVENT` surface for *Ilaj bil Tadbeer*, a free-text *Bawl wa Baraz* observation field, honest framing of the pulse limit, named *Quwa* and seasonal correspondences in the metric-info sheets, and Unicode-safe entry for the *Mufrad / Murakkab* pharmacopeia. None of these violate the manifesto; several are descriptive scaffolding the Hakim reads alongside the instrument, exactly as Bios's own design language already prefers.
