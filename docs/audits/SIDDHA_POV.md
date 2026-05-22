# Siddha Practitioner Audit — Bios Read Through the Tamil Siddhar Tradition

**Scope:** Bios's clinical reach as a preventive-monitoring instrument, evaluated through the diagnostic frame, therapeutic order and pharmacological commitments of Siddha medicine (சித்த மருத்துவம்) — the *Mukkuttram* (three humours), *Ezhu Udal Thathukkal* (seven body constituents), *Envagai Thervu* (eight-fold examination including *Neerkkuri* / *Neikkuri* urinalysis), *Varma chikitsa* (108 vital points), *Thokkanam* (nine-technique manipulation), *Kayakarpam* (rejuvenation) and *Gunapadam* (plant / mineral / animal materia medica) — as taught in BSMS / MD-Siddha curricula and in living rural and diasporic Tamil practice.
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** Siddha medicine. The reviewer is a BSMS- or MD-Siddha-trained practitioner working in Tamil Nadu, Puducherry, Sri Lanka, Singapore, Malaysia or the wider Tamil diaspora, reading the code to decide whether Bios has a place at the bench of a *vaidyar*'s clinic — alongside, not instead of, the *moonru viral naadi* (three-finger pulse) and the morning *neerkkuri* basin. The audit takes the tradition's own internal taxonomy as given and asks where Bios fits inside it; it does not adjudicate Siddha's clinical claims. The catalogue entry that frames this lens is [MEDICAL_SPECIALTIES_WORLDWIDE.md §5](MEDICAL_SPECIALTIES_WORLDWIDE.md).
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [docs/audits/MEDICAL_SPECIALTIES_WORLDWIDE.md](MEDICAL_SPECIALTIES_WORLDWIDE.md), [docs/audits/MEDICAL_PROFESSIONAL_POV.md](MEDICAL_PROFESSIONAL_POV.md), [docs/audits/AYURVEDA_POV.md](AYURVEDA_POV.md), [docs/audits/TCM_POV.md](TCM_POV.md), [docs/audits/SOWA_RIGPA_POV.md](SOWA_RIGPA_POV.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [EmergencyVitalPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/EmergencyVitalPatterns.kt), [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt).

This audit is deliberately written to *not* be a rebranded Ayurveda audit. Siddha and Ayurveda share an Indian substrate and a humoural vocabulary, but the Tamil tradition differs in load-bearing places: a distinct *Mukkuttram* characterisation that is not isomorphic to *Tridosha*; a *Neerkkuri / Neikkuri* urinalysis discipline that has no Ayurvedic equivalent in either depth or method; an alchemical pharmacy (*Muppu*, *chendooram*, *parpam*, *bhasma* / *chunnam*) where mineral and metallic preparations dominate to a degree they do not in classical Ayurveda; a vital-point system (*Varmam*) that doubles as a martial art and is unique to the South Indian and Sri Lankan transmission lines; a 4,448-disease nosology in the *Yugi Vaidya Chinthamani* that is taxonomically denser than Bios's pattern library by two orders of magnitude; and a Tamil-tropical climatology that the *Siddhars* codified in Madurai, Palani, Chidambaram, Kanyakumari and Tiruvannamalai — not in the cooler latitudes where Ayurveda's *ritucharya* was composed. Where this audit overlaps with [AYURVEDA_POV.md](AYURVEDA_POV.md), the overlap is acknowledged and the *Siddha-specific* point is what is recorded.

---

## Executive summary

Bios is, in the language of the *Siddhars*, an instrument for *Noi Naadal* — the act of "finding the illness," the eight-channel observation discipline that the *vaidyar* practises before any *marunthu* (medicine) is named. It records what the body manifests, refuses to evaluate the person, requires multiple signals to converge before it speaks, and stays silent otherwise. A *Siddha* practitioner reading [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) will recognise the register: an instrument that does not arrogate the practitioner's role, and that holds the personal baseline as the reference frame against which deviations are read. That posture rhymes with the *Siddhar*'s own self-understanding — *aṟivu uḷḷavarē vaidyar*, "the one with discernment is the physician" — the instrument informs; it does not pronounce. The preventive scope of Bios (catching deviations 1–2 days before symptoms manifest) maps onto the *Kayakarpam* posture of intervening before *noi* (disease) crystallises out of *noi munṉāl kuṟigaḷ* (pre-disease signs).

What the instrument *cannot* do, and does not pretend to, is the rest of *Siddha Maruthuvam*. None of the three *Mukkuttram* — *Vaadham*, *Pittham*, *Kabam* — are modelled. The seven *Ezhu Udal Thathukkal* are touched only at the *Oon* / *Kozhuppu* / *Enbu* level (muscle mass / body fat / bone) and silently at *Saaram* (chyle, via glucose and lipid panels); the deeper layers (*Cheneer*, *Moolai*, *Sukkilam*/*Suronitham*) are absent or only inferable. *Naadi* is reduced to rate. *Neerkkuri* and the uniquely Siddha *Neikkuri* (the sesame-oil drop on the urine surface, read for *Vaadha* / *Pittha* / *Kabha* signatures) have no capture surface. The 108 *Varmam* points have no anatomical surface. *Thokkanam* and *Yoga Maruthuvam* are absent. *Gunapadam* is invisible — Bios has shipped a `MedicationAnnotationRepo` but its vocabulary is RxNorm, not the *Siddhar*'s plant-mineral-animal triple. And the Tamil-tropical climatology that frames most Siddha clinical work — *Kaar* (rainy), *Koothir* (cold), *Munpani* / *Pinpani* (early / late winter), *Ilavenil* (early summer), *Mudhuvenil* (late summer / *kāḷaṉ paruvam*, the season of the angel of death) — is invisible to the engine.

Ordered by clinical impact in a Siddha setting:

1. **No *Mukkuttram* (Vaadham / Pittham / Kabam) model anywhere in the schema.** This is the single largest structural absence. Siddha's *Mukkuttram* is not a translation of Ayurveda's *Tridosha* — the Tamil characterisations differ in load-bearing places: *Vaadham* governs the ten kinds of *vaayu* including *praanan* and *abaanan* with a specifically respiratory-pulmonary emphasis that exceeds Ayurveda's *Vata*; *Pittham* in Siddha includes *pirana pittham* with a digestive-fire connotation distinct from Ayurveda's *Jatharagni* framing; *Kabam* in the *Yugi* texts has five subtypes that govern joint lubrication and sensory clarity in ways the Ayurvedic *Kapha* taxonomy organises differently. Bios collects HRV instability, RR irregularity, skin-temp swings, sleep fragmentation, glucose CV, body mass — every substrate for projecting onto the *Mukkuttram* axes — but produces no such projection. A BSMS-trained reader who pulls a Bios FHIR bundle for a patient must still do the *Mukkuttram* mapping by hand.

2. **No *Neerkkuri* / *Neikkuri* surface — the most Siddha-specific gap in the entire audit.** *Neerkkuri* (the morning urine observation discipline) and *Neikkuri* (the diagnostic reading of a single drop of *nallennai* — sesame oil — placed on the urine surface, with the pattern of spread classified into *Vaadha*-like, *Pittha*-like, *Kabha*-like or compound signatures) is the diagnostic refinement most distinctive to Siddha. No other major tradition reads urine this way. Bios has no urinalysis surface of any kind — neither categorical entry for the eight classical *Neerkkuri* axes (colour, smell, density, *nurai* foam, *enjal* sediment, vapour, taste — historically — and the *neikkuri* oil-spread pattern) nor a structured field for a clinical dipstick. The omission is shared with every other consumer wearable, but the absence is felt most sharply here because *Neikkuri* is doctrinally a *Mukkuttram* diagnostic, not a screening test.

3. **Pulse is captured as rate, not as *Naadi*.** *Naadi paritchai* in Siddha is performed with three fingers on the radial artery, distinguishing *Vaadha-naadi* (felt strongest under the index finger, irregular and "snake-like"), *Pittha-naadi* (under the middle finger, "frog-like" leaping), and *Kabam-naadi* (under the ring finger, "swan-like" slow and gliding) — and the *vaidyar* reads not only the rate but the strength, depth, regularity, and the relative dominance of the three positions. [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) measures the fingertip PPG waveform — peak amplitude, peak-to-peak interval, amplitude CoV, RR series, smoothed morphology — and discards everything except the IBI series at [PpgSignalProcessor.kt:144-152](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L144-L152). The morphology features that map plausibly onto Vaadha-/Pittha-/Kabha-naadi signatures are computed and then thrown away. This is the same latent-signal finding the TCM audit ([TCM_POV.md §2.2](TCM_POV.md)) made for the 28 *mai* qualities; the Siddha-specific version is that the three-position pressure-modulated reading is not optical-PPG-extractable (it requires manual palpation pressure variation) but the *waveform-shape* dimension is.

4. **No *Ezhu Udal Thathukkal* surface — body composition is only partially covered.** The seven *thathukkal* are *Saaram* (chyle / nutrient fluid), *Cheneer* (blood), *Oon* (muscle), *Kozhuppu* (fat / adipose), *Enbu* (bone), *Moolai* (marrow / nervous tissue), and *Sukkilam* / *Suronitham* (reproductive essence — semen / ovum). Bios has BODY_MASS, BODY_FAT_PCT, LEAN_MASS which cover *Oon* and *Kozhuppu* cleanly; bone density (DEXA) is on the [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) extension surface but underweighted as the primary-care audit ([MEDICAL_PROFESSIONAL_POV.md §2.8](MEDICAL_PROFESSIONAL_POV.md)) noted. *Saaram* maps onto the metabolic panel (glucose, lipids, albumin); *Cheneer* maps onto haemoglobin / haematocrit / ferritin; *Moolai* has no surface; *Sukkilam* / *Suronitham* maps onto the reproductive surface ([menstrualCycleAnomaly](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498) plus the testosterone / estradiol biomarkers). The *Thathukkal* are an *ordered* construction in Siddha — each tissue is the substrate for the next, and *Sukkilam-kshayam* (depletion of reproductive essence) is read as the terminal expression of *Saaram-kshayam* upstream. Bios sees the readings but the ordered-tissue interpretation is invisible.

5. **The Tamil-tropical climatology is not modelled.** Siddha was codified in Tamil Nadu's tropical-monsoonal climate — Madurai sits at 9°55′N, Palani at 10°27′N, Chidambaram at 11°24′N — and the *aaru paruvam* (six-season) calendar is anchored to the *Tamil* solar months, not the North Indian *ritucharya*. The seasons (and their *Mukkuttram* valences) are: *Kaar* காரிṟ (Aavani–Purattaasi, Aug–Oct, monsoon — *Vaadham* accumulates); *Koothir* கூதிர் (Aippasi–Kaarthigai, Oct–Dec, late-monsoon cool — *Vaadham* peaks, *Pittham* releases); *Munpani* முன்பனி (Maargazhi–Thai, Dec–Feb, early winter dew — *Kabam* accumulates); *Pinpani* பின்பனி (Maasi–Panguni, Feb–Apr, late winter dry — *Kabam* peaks); *Ilavenil* இளவேனில் (Chithirai–Vaikaasi, Apr–Jun, early summer — *Kabam* releases, *Pittham* accumulates); *Mudhuvenil* முதுவேனில் (Aani–Aadi, Jun–Aug, high summer — *Pittham* peaks, *Vaadham* accumulates). Bios's 14-day rolling baseline is correct for stable physiology in stable climate; in the Chennai / Madurai / Coimbatore / Jaffna / Trincomalee belt where most Siddha is practised, the high-summer RHR drift and skin-temperature elevation that the engine would read as *cardiovascular_stress* are the expected *Mudhuvenil-Pittha* seasonal rise. None of the [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) gates account for this, and the [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) thresholds are not season-modulated.

6. **No tropical-infectious-disease pattern library.** Siddha clinical practice in its catchment encounters dengue, chikungunya, leptospirosis, scrub typhus, *kuṣṭam* (leprosy), filariasis, hepatitis A/E, typhoid, melioidosis — the WHO–SEARO infectious-disease load is materially different from the North Atlantic respiratory-and-influenza dominance the Bios [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) pattern was authored against. The Mishra 2020 / Quer 2021 / Smarr 2020 citation set was derived primarily from COVID-19 in US cohorts. The same six-signal cluster (RHR↑, HRV↓, skin-temp↑, RR↑, sleep↓, steps↓) *will* fire on dengue, chikungunya, scrub typhus — those are all febrile illnesses with autonomic manifestations — but the *staging* (haemorrhagic risk window in dengue, the rash-and-eschar pattern in scrub typhus, the biphasic fever in leptospirosis) is invisible. A Siddha practitioner reading the alert cannot tell which *jura* (fever-class) Bios is detecting, and the prevention / healing fields are tuned for a Northern Hemisphere respiratory-viral context that does not fit the catchment.

7. **No *Gunapadam* pharmacy surface.** *Gunapadam* — Siddha materia medica — is split across three drug origins: *moolika* (plant), *thathu* (mineral / metallic), and *jangama* (animal). The mineral side is more central than in Ayurveda: *Muppu* (the alchemical "universal salt" tri-compound), *parpam* (calcined mineral / metallic powders), *chendooram* (red oxide preparations), *chunnam* (calcined limestone-class preparations), *kalangu* (specific stone preparations) — these are the *Siddhar*'s signature pharmacology. A patient on *Linga chendooram* for chronic respiratory complaint, *Rasagandhi mezhugu* for rheumatic *Vaadham*, *Thalaga parpam* for skin conditions, or *Chandamarutha chendooram* during a fever, will have HR / HRV / skin-temp signatures that the engine reads as anomalous. [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L34) accepts free-text annotations today and the alert-explanation builder appends them, so an owner *can* record `Linga chendooram`; what is missing is structured vocabulary so the practitioner reviewing the FHIR-exported bundle can read a coded entry. The AYUSH coding system covers Siddha *gunapadam* under ICD-11 Chapter 26 traditional-medicine module — extending the `substance_key` vocabulary to consume AYUSH codes alongside RxNorm is a low-cost mechanical extension that closes the gap for all three AYUSH systems (Siddha, Ayurveda, Unani) simultaneously.

8. **No *Varmam* surface — flagged, not recommended.** The 108 *Varma* points (12 *Padu varmam* "death points," 96 *Thodu varmam* "touch points," organised across the *naadi* / *kaalam* / *thodu* / *paadu* taxonomy) are unique to South Indian and Sri Lankan transmission; they double as the substrate of *Varma kalai* (Tamil martial art) and *Varma maruthuvam* (clinical *Varmam*). Bios has no anatomical surface at all, which is the manifesto-clean choice for a wearable instrument — *Varma* point work is manual therapy, not data ingestion. The narrow research-surface question is whether HRV / skin-temp / RHR response to *Varma chikitsa* at specific points could be recorded for the *vaidyar*'s own session log; the answer is "yes, via a third-party clinic-facing companion that reads Bios's HRV stream through the ContentProvider," not "Bios should ship anatomical metadata."

9. **No *Yoga Maruthuvam* / *Thokkanam* intervention-event surface.** The *Siddhar*'s therapeutic order is canonical: *paththiyam* (dietary regulation), *manam* (mental composure), then *thokkanam* (one of nine manipulation techniques — *podithal* pressing, *thirumal* rubbing, *thattal* tapping, *kottuthal* striking, *mukkal* rolling, *atangal* squeezing, *pidithal* gripping, *muruakkal* twisting, *vitethal* releasing), *yoga maruthuvam* (yogic therapy), and only then *gunapadam*. A *thokkanam* session has measurable autonomic effects (HRV shift, skin temperature, RR slowing). Bios has no `INTERVENTION_EVENT` to bracket "patient received *thokkanam* on day 14, observe the HRV / temp trace for 72 hours afterward" — the same gap the Sowa Rigpa audit ([SOWA_RIGPA_POV.md §2.7](SOWA_RIGPA_POV.md)) noted for *me btsa'* and *gtar ga*. The fix is identical and the surface is shareable across traditions.

10. **The 96 *Thathuvam* framing is not honoured in the way Bios presents itself.** The *Siddhars* hold that the human body is one expression of 96 universal principles (the *thathuvangal* — 5 great elements, 5 *jnanendriyas* / sense organs, 5 *karmendriyas* / action organs, 5 *thanmaathiras*, 4 *antakkaranas*, 10 *vaayus*, *mukkuttram* etc., totalling 96). This is metaphysics, not engineering — and Bios is right not to encode it as a `MetricType`. The point worth naming is *register*: a Bios screen that frames bodily monitoring as "tracking the body" misses the *Siddhar*'s framing that the body is one window onto a larger pattern. The Bios manifesto's stance — "your body speaks, we help you listen" — is closer to that register than most consumer health software, and the absence is one of *vocabulary* rather than schema. No engineering work is recommended here; this is noted to acknowledge that a *Siddhar* reading the manifesto will find its disposition more familiar than its surface.

The audit walks each gap and aligned strength in detail below.

---

## 1. What Bios already does well, viewed through a Siddha lens

| Quality | Evidence in code / docs | Why it matters in Siddha |
|---|---|---|
| **Personal baseline over population norm** | 14-day rolling per-metric baseline; z-score gate; multi-signal convergence ([ConditionPatterns.kt:113-124](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L113-L124)) | *Iyalbu* (innate constitution) determines what is *iyalbāna* (natural) for a given owner. A Vaadha-prakriti owner whose RHR is 78 is not in distress; a Kabam-prakriti owner at 78 may be in early *Pittha* excess. The engine's commitment to comparing the owner to themselves is the operational form of *iyalbu-relative* diagnosis the Siddha practitioner already uses |
| **Instrument, not coach** | Manifesto Principle 7; [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) bans evaluative language at the CI level | The *Siddhar* tradition holds that *vaidyar* (physician) and *rogi* (patient) are co-investigators of the *noi* — the practitioner does not impose a verdict. The CI-gated banlist on "you should / you need to / streak / level up" is the same posture in code. A BSMS reader will recognise the register before reading the schema |
| **"Treating not-yet-disease" rhymes with Kayakarpam** | Pre-symptomatic detection windows in [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156); 168h+ longitudinal patterns ([chronicInflammation](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L309-L336), [recoveryDeficit](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L339-L363), [cardiorespiratoryDeconditioning](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L274-L307)) | *Kayakarpam* (rejuvenation / longevity) is the Siddha branch whose explicit charter is preserving *aayul* (lifespan) by intervening before *noi* manifests. *Muppu* and the *chitra mooligai* (selected herbs of high potency) are the pharmacology of that branch. Bios's preventive scope is *Kayakarpam*-class surveillance — catching *Vaadha*-, *Pittha*-, *Kabam*-shifts before they crystallise as named *noi* |
| **Multi-signal convergence as diagnosis** | `minActiveSignals = 3` for [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156); 3-of-4 for [chronicInflammation](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L309-L336) | *Envagai Thervu* — the eight-fold examination — is itself a convergence discipline: no single channel resolves a *noi*, the *vaidyar* requires concord across *Naadi*, *Sparisam*, *Naa*, *Niram*, *Mozhi*, *Vizhi*, *Malam*, *Moothiram* before naming the imbalance. Bios's reluctance to alert on a single signal is structurally the same reluctance |
| **Behavioural and dietary primacy** | Every condition pattern carries `prevention` and `healing` fields leading with diet, sleep, movement, stress regulation before clinical referral | The *Siddhar*'s therapeutic order — *paththiyam* (diet) and *manam* (mental composure) before *gunapadam* (medicine) — is the same hierarchy. The `prevention` / `healing` fields are doing in software what the *vaidyar*'s consultation notes do in writing |
| **Silence is a feature** | Manifesto Principle 7; [AlertContentPolicy.kt:13-25](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L13-L25) carves push from pull | *Vaayadakkam* (verbal restraint) is a virtue the *Siddhar* texts repeatedly extol. The instrument that observes quietly and speaks only on convergence honours the register the *vaidyar* tries to embody |
| **Separately-encrypted reproductive database** | Independent SQLCipher key, independent wipe; [menstrualCycleAnomaly](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498) routes through it | *Magalir Maruthuvam* (women's medicine) is a distinct Siddha branch with its own consultation conventions; the data-isolation posture matches the cultural practice of separating reproductive consultation from general medicine |
| **Literature anchoring** | 24 of 33 wearable signal rules carry primary citations; biomarker thresholds match published guidelines | A BSMS practitioner working in an integrative setting needs to read both the Western literature and the Siddha classical references (the *Yugi Vaidya Chinthamani*, *Theraiyar Vagadam*, *Agasthiyar 2000*). The fact that Bios cites and the citations are auditable means the practitioner can extend the citation set with their own *prāmāṇika* (canonical-textual) work without fighting the architecture |
| **Source-agnostic schema, FHIR export** | [DATA_MODEL.md](../DATA_MODEL.md) §FHIR mapping; FHIR R4 bundle with LOINC | Lets an integrative clinic — common in Tamil Nadu where *Vaidyar* and *Allopathy* practitioners frequently co-refer — incorporate Bios data into a multi-practitioner record. The Tamil Nadu government's AYUSH integration policy explicitly anticipates this kind of cross-system data sharing |
| **Free for all, no Play Services** | Manifesto Principle 3; no Google Play dependency | A *vaidyar* running a rural clinic in Sivagangai, Tirunelveli, Theni, or Vavuniya (Sri Lanka) is serving a patient population for whom subscription health apps are unaffordable and Play Services-bound apps are sometimes inaccessible (degoogled handsets, Aurora-store ecosystems). Bios's posture matches the *Siddhar*'s historical commitment that medicine is offered without expectation of payment when the patient cannot pay |

These are not parity wins. They are places where Bios's design choices independently arrive at conclusions the *Siddhars* held for centuries — convergence-of-signs diagnosis, personal-iyalbu baselining, instrument-not-coach posture, preventive-window emphasis, behavioural-and-dietary primacy. A BSMS practitioner can take Bios seriously on these grounds before any Siddha-specific feature is added.

---

## 2. Siddha gaps, ordered by clinical impact

### 2.1 No *Mukkuttram* (Vaadham / Pittham / Kabam) model

This is the structural absence with the largest reach. Siddha's *Mukkuttram* is the irreducible diagnostic axis: every *noi* in the *Yugi Vaidya Chinthamani* (the 4,448-disease catalogue) is classified by which *kuttram* is *kondu* (dominant) and which are *aadhiga* (excess) or *kuraivu* (deficient). The three *kuttram* are not isomorphic to Ayurveda's *Tridosha*; the Siddha characterisations differ in load-bearing places:

| Siddha *kuttram* | Tamil characterisation | Subtypes / *bhedam* | Wearable substrate Bios already collects |
|---|---|---|---|
| ***Vaadham*** (வாதம்) | Movement, breath, nerve, articulation. Governs ten *vaayus*: *praanan* (in-breath), *abaanan* (out-breath), *vyaanan* (circulation), *udaanan* (upward), *samaanan* (digestion), and five lesser. Pulmonary-respiratory emphasis exceeds Ayurveda's *Vata* | *Praana-vaadham*, *Abaana-vaadham*, *Vyaana-vaadham*, *Udaana-vaadham*, *Samaana-vaadham*, plus *Naaga*, *Koorma*, *Kirukara*, *Devadattan*, *Dhananjayan* | HRV instability, RR irregularity, sleep fragmentation, SLEEP_REGULARITY, CIRCADIAN_PHASE_SHIFT, low skin temperature with wide diurnal swing, RHR variability |
| ***Pittham*** (பித்தம்) | Heat, metabolism, vision, complexion, decisive judgment. *Pirana pittham* governs digestive fire with a connotation distinct from Ayurveda's *Jatharagni* — closer to the cellular metabolism of the *Saaram* tissue | *Anila-pittham*, *Ranjaka-pittham*, *Saadhaka-pittham*, *Aalochaka-pittham*, *Bhraajaka-pittham* | SKIN_TEMPERATURE_DEVIATION, HSCRP, ALT / AST / GGT, HEART_RATE elevation, body temp, GLUCOSE_CV |
| ***Kabam*** (கபம்) | Structure, cohesion, lubrication, sensory clarity, stable mood. *Yugi*-text subtypes govern joint lubrication and sense-organ moistening | *Avalambaka-kabam*, *Kledhaka-kabam*, *Bodhaka-kabam*, *Tarpaka-kabam*, *Slesmaka-kabam* | BODY_MASS, BODY_FAT_PCT, VO2_MAX (when low), ACTIVE_MINUTES (when low), SLEEP_DURATION (when long with low efficiency), RHR (when low + low fitness), GLUCOSE_TIME_IN_RANGE (when low) |

Bios collects every substrate listed above. None of it is projected onto the *Mukkuttram* axes.

**Recommendation:** a pull-side *Mukkuttram* projection surface — strictly under the [AlertContentPolicy](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) constraints. The same machinery the Ayurveda audit recommended for *dosha* projection ([AYURVEDA_POV.md §2.1](AYURVEDA_POV.md)) and the TCM audit recommended for *zang-fu* lens ([TCM_POV.md §2.4](TCM_POV.md)) can host a parallel Siddha projection. The vocabulary differs (Vaadham/Pittham/Kabam in Tamil display, with the BSMS-standard transliteration alongside), the subtype breakdown is finer, and the recommendation is to *not* collapse Vaadha-/Pittha-/Kabha-projections into a single composite — Bios's existing guard against composing epigenetic clocks into a single "biological age" is the right model.

A BSMS-trained reader who opens the *Mukkuttram* view should see something like:

> *Vaadham* axis: +1.6σ above your tracked 14-day baseline. *Praana* subtype dominant (RR irregularity + HRV instability + sleep fragmentation). Consistent with *Vaadha-kuṟaippāṭu* if subjective symptoms (dryness, restlessness, joint stiffness) also present.
>
> *Pittham* axis: stable.
>
> *Kabam* axis: −0.4σ.

The wording is descriptive ("your Vaadham axis signals are +1.6σ"), not evaluative ("you have Vaadha imbalance"). The push channel never fires from this surface. Evaluation belongs to the *vaidyar*.

This is the highest-leverage Siddha-specific feature Bios could add. It would be invisible to any owner who does not enable it, preserving the manifesto.

### 2.2 *Neerkkuri* and *Neikkuri* — the most distinctively Siddha gap

*Neerkkuri* (urine examination) and *Neikkuri* (the oil-drop refinement) is the diagnostic refinement most specific to Siddha. Other traditions examine urine — Ayurvedic *mutra pariksha*, Sowa Rigpa *chu-rtags*, Greco-Arabic Unani *baul* — but the *Siddhar*'s method is distinctive in two respects:

1. **The eight classical *Neerkkuri* axes**: *niram* (colour), *manam* (smell), *aaviḷ* (vapour density and persistence), *nurai* (foam size, persistence and quantity), *enjal* (sediment — type, settling pattern, location), *edai* (density / heaviness), *kazhivu* (turbidity), and historically (no longer practised) *suvai* (taste). These are read on a urine sample collected before dawn (the *brahma muhurta*-equivalent — *araḻai paruvam*), examined in changing morning light.

2. ***Neikkuri***: a single drop of *nallennai* (cold-pressed sesame oil) is placed on the surface of the urine in a shallow flat-bottomed bowl. The *spread pattern* of the oil drop is read into one of the three *Mukkuttram*-correspondent signatures:
   - *Vaadha-neikkuri*: the drop spreads in a *serpent-like* (*pāmbu* போல) zigzag pattern, or *floats unchanged*
   - *Pittha-neikkuri*: spreads in a *ring* (*moodhirai* போல) or *star-burst* pattern
   - *Kabha-neikkuri*: forms a *pearl-like* (*muthu* போல) clustered or *sieve-like* (*sallaḍai* போல) dispersion
   - Compound signatures (*Vaadha-Pittham*, *Vaadha-Kabam*, *Pittha-Kabam*, *Tridosham* / *Sannipaada*) have their own characteristic spread patterns

Bios has no urinalysis surface at all. The omission is shared with every consumer wearable — no Bios-specific failure — but for a Siddha practitioner this is the most conspicuous gap in the *Noi Naadal* surface.

**Recommendation, tiered:**

1. **A manual-entry `URINE_OBSERVATION` MetricType** (the same shape the Ayurveda audit ([AYURVEDA_POV.md §2.7](AYURVEDA_POV.md)) recommended for *mutra pariksha*) in `MetricDomain.BIOMARKER` or a new `MetricDomain.NOI_NAADAL`, EVENT-unit, with structured sidecar fields covering the eight *Neerkkuri* axes as enums (colour: clear / pale-yellow / amber / dark-amber / red-tinged / cloudy-white; foam: none / fine-quick / fine-persistent / large-quick / large-persistent; etc.).

2. **A `NEIKKURI_OBSERVATION` extension** — the same MetricType with an additional sidecar field `neikkuri_pattern` (enum: *vaadha_serpentine*, *pittha_ring*, *pittha_starburst*, *kabha_pearl*, *kabha_sieve*, *compound_vaadha_pittham*, *compound_vaadha_kabham*, *compound_pittha_kabham*, *sannipaada*, *not_classified*). An optional `photo_uri` lets the owner photograph the oil-spread (in an isolated SQLCipher store, the same precedent as the reproductive database — *Neikkuri* photos are pre-dawn biometric-adjacent imagery and warrant the same isolation discipline).

3. **No automated *Neikkuri* classification.** A CNN that read the oil-spread pattern would be a Bios-evaluating-the-person surface in exactly the way the manifesto refuses. The owner classifies; Bios stores; the *vaidyar* reviews. (Research-surface note: there is a small academic literature on image-based *Neikkuri* classification — *Cherian 2015*, *Saraswathi 2018*, others — that a *vaidyar*-facing companion app could explore via the ContentProvider; that work does not belong in Bios core.)

This closes the largest *Noi Naadal* surface gap with low engineering cost — same manual-entry infrastructure already wired for BBT and biomarker entry. It also makes Bios uniquely useful for Siddha among the consumer-wearable category.

### 2.3 *Naadi paritchai* — pulse as morphology, not rate

The Siddha *Naadi paritchai* is performed with three fingers on the radial artery just below the wrist crease, with the practitioner's index finger reading *Vaadha-naadi*, middle finger reading *Pittha-naadi*, and ring finger reading *Kabam-naadi*. The reading attends to:

- **Rate** (*velam* — speed)
- **Strength** (*balam* — force, depth)
- **Quality** (*gati* — rhythm, regularity)
- **Three-position dominance** (which finger feels the strongest pulsation — diagnostic of which *kuttram* is dominant)
- **Constitutional pulse signatures**: *Vaadha-naadi* feels like a *snake* (*pāmbu* gati — quick, irregular, slithering); *Pittha-naadi* feels like a *frog* (*thavalai* gati — leaping, fast, pronounced); *Kabam-naadi* feels like a *swan* (*annam* gati — slow, gliding, broad)
- **Pathological pulses**: *koppulip-paṭṭa naadi* (knotted), *nilai-illāta naadi* (unstable), *thazhumpu naadi* (scarred / hesitant), and the catalogues in the *Theraiyar Naadi Saastram*

The TCM audit ([TCM_POV.md §2.2](TCM_POV.md)) made the latent-signal finding that [PpgSignalProcessor.kt](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt) computes the waveform morphology features that map onto pulse-quality classification, and then discards them. The Siddha-specific version of the finding:

- **Optical PPG cannot replicate the three-position pressure-modulated reading.** The *Vaadha-Pittha-Kabam* dominance under three fingers requires manual pressure variation that wrist-PPG and fingertip-PPG do not perform. This part of *Naadi paritchai* is not extractable, and a Bios feature claiming to do it would be misuse.
- **Waveform-shape qualities *are* in principle extractable.** *Snake-gati* irregularity (high RR-CoV combined with high peak-amplitude-CoV), *frog-gati* leaping (high peak amplitude with sharp rise-time), *swan-gati* smoothness (low peak-amplitude-CoV with low rise-time variance) — these are statistical summaries of features Bios already computes at [PpgSignalProcessor.kt:120-142](../../android/app/src/main/java/com/bios/app/engine/PpgSignalProcessor.kt#L120-L142) and discards.
- **The owner-self-pulse-reading surface** (`PULSE_QUALITY` manual entry) the Ayurveda audit ([AYURVEDA_POV.md §2.7](AYURVEDA_POV.md)) recommended applies here verbatim. A *vaidyar* who reads the patient's pulse between visits, or who teaches the owner self-pulse for daily *Naadi* awareness, needs a categorical entry field: `naadi_gati` (snake / frog / swan / compound / irregular), `dominant_position` (index / middle / ring / mixed), `bala` (weak / moderate / strong), recorded at a specific *paruvam* (time of day).

**Recommendation:**

1. **Preserve waveform morphology features in `PpgResult`** (same recommendation as [TCM_POV.md §2.2 Tier A item 1](TCM_POV.md#5-prioritised-recommendations)). One data-structure change unlocks both the TCM *mai* surface and the Siddha *gati* surface.
2. **A pull-side `NaadiCharacterView`** that summarises recent PPG morphology in terms a Siddha practitioner reads: snake-/frog-/swan-leaning signature with the underlying CoV / rise-time / amplitude numbers also shown. Read-only, never pushed.
3. **A `PULSE_QUALITY` manual-entry MetricType** for the self-pulse and practitioner-pulse readings that PPG cannot capture (three-position dominance, *bala*).

### 2.4 *Ezhu Udal Thathukkal* — ordered tissue interpretation is absent

The seven body constituents form an ordered hierarchy in Siddha (and Ayurveda, though the ordering and the lateral interpretation differ): each *thathu* is the substrate from which the next is formed, and depletion of an upstream tissue is read as the upstream cause of downstream depletion.

| *Thathu* | Tamil | Anatomical referent | Bios coverage |
|---|---|---|---|
| *Saaram* | சாரம் | Chyle / nutrient fluid; plasma proteins | Partial: glucose panel (BLOOD_GLUCOSE, HBA1C, GLUCOSE_CV, TIR), lipid panel (LDL, HDL, TRIGLYCERIDES, TOTAL_CHOLESTEROL), HSCRP, ALBUMIN if imported |
| *Cheneer* | செநீர் | Blood (the red constituent) | Partial: HEMOGLOBIN, HEMATOCRIT, FERRITIN if imported on the biomarker surface ([BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt)) |
| *Oon* | ஊன் | Muscle / lean tissue | Yes: LEAN_MASS, BODY_FAT_PCT (derived), VO2_MAX (proxy for muscle quality), ACTIVE_MINUTES |
| *Kozhuppu* | கொழுப்பு | Fat / adipose | Yes: BODY_FAT_PCT, BODY_MASS, lipid panel as biochemical correlate |
| *Enbu* | என்பு | Bone | Partial: DEXA biomarker if imported; FRAX inputs absent per [MEDICAL_PROFESSIONAL_POV.md §2.8](MEDICAL_PROFESSIONAL_POV.md) |
| *Moolai* | மூளை | Marrow + nervous tissue (the *Siddhar* texts conflate medullary marrow with central nervous tissue) | Absent: no neurological metric domain; W2F's TYPING_CADENCE and PVT-reaction-time are the closest proxies and live in the companion-signal layer |
| *Sukkilam* / *Suronitham* | சுக்கிலம் / சுரோணிதம் | Semen / ovum — reproductive essence | Partial: TESTOSTERONE_TOTAL, ESTRADIOL, AMH if imported; the [menstrualCycleAnomaly](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498) pattern reads cycle integrity |

The *Siddhar*'s reading is *ordered*: *Sukkila-kshayam* (reproductive-essence depletion) is read not as a primary endocrine pathology but as the terminal expression of *Saara-kshayam* (chyle / nutrient depletion) upstream, expressed through the seven-tissue cascade. Bios sees the readings; the cascade interpretation is invisible.

**Recommendation:** an `EzhuThathuView` pull-side surface that arranges the existing biomarker and body-composition readings along the seven-tissue cascade, surfacing the *upstream* findings before the *downstream* expressions. This is a re-arrangement of the data Bios already holds; no new ingestion. The same view honours Ayurveda's *sapta dhatu* doctrine (which uses the same tissue list with marginally different ordering — *rasa*, *rakta*, *mamsa*, *medas*, *asthi*, *majja*, *shukra*) so the engineering serves both audits.

The deeper-impact recommendation is to recognise *Moolai* as a structural absence and treat it as a Bios roadmap item: a neurological MetricDomain (or the addition of cognitive-probe metrics outside of W2F's companion layer) would close the most consequential gap in the *Thathukkal* coverage. This is also flagged in the primary-care audit ([MEDICAL_PROFESSIONAL_POV.md §2.8](MEDICAL_PROFESSIONAL_POV.md)) as the cognitive domain.

### 2.5 Tamil-tropical climatology — Bios is season-blind in the Madurai latitude

The *aaru paruvam* (six seasons) of the Tamil calendar do not correspond to either the four Western seasons or the Ayurvedic North Indian *ritus*. The Tamil months are solar, the climate is tropical-monsoonal, and the *Mukkuttram* valences of the seasons are distinctively Siddha:

| Tamil season | Tamil months (approx. Gregorian) | Typical climate (Tamil Nadu) | *Mukkuttram* valence |
|---|---|---|---|
| *Kaar* காரிṟ | Aavani–Purattaasi (mid-Aug to mid-Oct) | Southwest monsoon retreat; northeast monsoon onset | *Vaadham* accumulates (*sanchaya*) |
| *Koothir* கூதிர் | Aippasi–Kaarthigai (mid-Oct to mid-Dec) | Northeast monsoon; wet and cool | *Vaadham* peaks (*prakopa*); *Pittham* releases |
| *Munpani* முன்பனி | Maargazhi–Thai (mid-Dec to mid-Feb) | Dry, cool dawns; "early dew" | *Kabam* accumulates |
| *Pinpani* பின்பனி | Maasi–Panguni (mid-Feb to mid-Apr) | Dry, warming; "late dew" | *Kabam* peaks |
| *Ilavenil* இளவேனில் | Chithirai–Vaikaasi (mid-Apr to mid-Jun) | Pre-monsoon hot-dry; *agni* season | *Kabam* releases; *Pittham* accumulates |
| *Mudhuvenil* முதுவேனில் | Aani–Aadi (mid-Jun to mid-Aug) | High summer; *kāḷaṉ paruvam* — historically the season of highest mortality | *Pittham* peaks; *Vaadham* accumulates |

The clinical consequences for Bios in its Tamil catchment:

- A *Mudhuvenil* RHR drift upward of 3–5 bpm above 14-day baseline reads to Bios as `cardiovascular_stress`. To a *vaidyar* it is the expected *Pittha-prakopa* seasonal rise, normative across the population at 28–32°C and 75% relative humidity.
- A *Munpani* sleep-fragmentation rise reads as `sleep_disruption`. To a *vaidyar* it is the expected *Vaadha-prakopa* turbulence of cool dawns and a cue to recommend *thaila-abhyanga* (oil massage with warming sesame or castor oil) rather than evaluate the owner for an autonomic disorder.
- A *Kaar* RHR irregularity reads as `afib_screen` corroborator. To a *vaidyar* it is the *Vaadha-sanchaya* of the monsoon and worth recording, not yet worth escalating.
- The [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) thresholds (130/80, 140/90, etc., per JNC-8 / ESC) are seasonally stable in their Western validation cohorts but seasonally drifting in the Madurai latitude — heat-induced vasodilation reduces summer BP by 4–8 mmHg in tropical populations, a finding the Indian Council of Medical Research has flagged in its *Hypertension Management in India* guidance.

The mechanism for fix is the same as Ayurveda's *ritucharya* gap ([AYURVEDA_POV.md §2.5](AYURVEDA_POV.md)) and Sowa Rigpa's six-season gap ([SOWA_RIGPA_POV.md §2.5](SOWA_RIGPA_POV.md)): extend the [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt)-style gating with a `SeasonalModifier` axis. The Siddha-specific contribution to that infrastructure is the *Tamil-tropical* season-derivation table — a `TamilSeason` enum and a date-plus-latitude derivation that recognises the *aaru paruvam* alongside the Ayurvedic *ritus*. The same `SeasonalModifier` machinery serves Tamil, North Indian, Tibetan and Greco-Arabic seasonalities, with the per-tradition tables maintained separately.

A region-extension corollary: the [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) does not currently list `IN` (India) or `LK` (Sri Lanka) per [SOWA_RIGPA_POV.md §2.9](SOWA_RIGPA_POV.md). Adding these regions with the ICMR / SLMC threshold conventions and AYUSH regulatory framing closes the locale gap for the Siddha catchment simultaneously.

### 2.6 Tropical infectious-disease patterns are missing

The Bios [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) pattern is well-built (Mishra 2020 / Quer 2021 / Smarr 2020 anchored), but its evidence base is North American COVID-19 cohorts. The Siddha catchment in Tamil Nadu, Puducherry, Sri Lanka, and the Tamil diaspora encounters a substantially different infectious-disease load:

| Tropical infection | Typical autonomic / wearable signature | Siddha *jura* classification | Bios coverage |
|---|---|---|---|
| Dengue | Biphasic fever, RR↑, marked HRV depression, plasma-leakage warning signs (haematocrit rise, platelet drop, abdominal pain, mucosal bleed) in days 4-6 — the *critical phase* | *Saagiri jura* / *visha jura* (in the *Yugi* classification) | Generic `infection_onset` fires; haemorrhagic-window staging absent |
| Chikungunya | Fever + arthralgia (severe, often persistent for months); HRV depression, activity drop, sleep disruption | *Sanni jura* with *kīlvāyu* (joint involvement) | Generic `infection_onset` fires; arthralgia-persistence pattern absent |
| Scrub typhus | Eschar at chigger-bite site, fever, lymphadenopathy, RR↑, multi-organ involvement in severe cases | *Visha jura* with skin signs | Generic `infection_onset` fires; eschar / lymphadenopathy not detectable from wearables |
| Leptospirosis | Biphasic fever; first phase flu-like, second phase (immune) with jaundice, AKI, conjunctival suffusion | *Neera jura* (water-borne) | Generic `infection_onset` fires; biphasic pattern not modelled |
| Typhoid | Step-ladder fever pattern over 1-3 weeks; relative bradycardia (Faget sign — pulse-temperature dissociation) | *Sannipaada jura* | Generic `infection_onset` fires; *Faget sign* (HR-temp dissociation) is a Siddha-recognised pattern with no current pattern surface |
| Hepatitis A/E | Anorexia, RUQ discomfort, dark urine, jaundice; ALT/AST rise dramatic | *Kāmālai* | Biomarker pattern fires if ALT/AST imported; pre-symptomatic wearable signature is weak |
| Filariasis | Often subclinical until lymphoedema; episodic acute filarial fevers | *Yaanaikkaal* | Largely outside wearable detection |

**Recommendation:**

1. **A `TropicalInfectionStaging` annotation** on `infection_onset` that, when the owner has set their region to `IN`/`LK`/an AYUSH-recognised locale, surfaces the staging considerations (dengue critical-window, chikungunya arthralgia-persistence, *Faget sign* for typhoid). Pull-side; the explanation builder at [AnomalyDetector.kt:406-425](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L406-L425) is the right place. *Not* a separate pattern firing — an enrichment of the existing one.

2. **Region-aware citation extension** — the `references` list on [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L148-L151) currently cites Mishra and Quer. For the AYUSH-locale rendering, add the relevant ICMR / WHO-SEARO / Sri Lanka Medical Association references on tropical-disease wearable detection where they exist.

3. **A specific *Faget sign* corroborator**: when RHR is *not* rising in proportion to skin-temperature elevation (HR < expected for temp), flag this for the practitioner. The current `infection_onset` requires both signals; a *Faget*-aware variant would surface the *dissociation* as itself diagnostic. This is a Siddha-recognised pattern (*sannipaada jura* without *Vaadha-prakopa* on the pulse) and a Western-internal-medicine-recognised pattern (typhoid, *Yersinia*, certain viral haemorrhagic fevers) simultaneously.

The Bios `prevention` and `healing` field content in [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L153-L155) reads in a Northern Hemisphere respiratory-viral register ("during high-risk seasons, consider reducing exposure to crowded indoor spaces") that does not fit the catchment. A localised-content variant for the AYUSH regions — written in the Siddha *paththiyam* register (*Pittha*-pacifying foods during *Mudhuvenil* fevers, *thuvaram-paruppu* avoidance in haemorrhagic-prodrome periods, *vasambu* and *kaṭukkāy* as *paththiya* anchors) — would honour the practitioner's vocabulary without requiring Bios to *prescribe* in Siddha terms.

### 2.7 No *Gunapadam* pharmacy surface

*Gunapadam* — Siddha materia medica — is split across three origins:

- ***Moolika varkam*** (மூலிகை வர்க்கம் — plant origin): roots, leaves, bark, flowers, fruits. Examples: *thoothuvalai*, *adhathodai*, *karpooravalli*, *vasambu*, *seenthil*, *kaṭukkāy*, *neem*, *amukkaraa* (*Withania somnifera*, the Tamil name for Ayurveda's *Ashwagandha*).
- ***Thathu varkam*** (தாது வர்க்கம் — mineral / metallic origin): mercury (*rasam*), sulphur (*gandhakam*), arsenic (*arsenic / sengottai*), gold (*ponn*), silver (*velli*), iron (*irumbu*), zinc, copper, antimony, mica (*abrakam*), salts (especially *Muppu*). Preparations are calcined to *parpam* (white ash), *chendooram* (red oxide), or *chunnam* (lime-class) forms. Examples: *Linga chendooram* (mercury-sulphur), *Thalaga parpam* (arsenic), *Naga parpam* (lead), *Rasa karpooram* (mercury sublimate).
- ***Jangama varkam*** (ஜங்கம வர்க்கம் — animal origin): conch (*sangu*), pearl (*muthu*), coral (*pavalam*), shell (*sippi*), specific animal-derived preparations.

The mineral / metallic side is more central than in Ayurveda. *Muppu* — the alchemical "universal salt," a tri-compound of *vediyuppu* (potassium nitrate), *pooneeru* (Fuller's-earth-class salt), and *kalluppu* (rock salt), prepared under specific astrological windows — is the *Siddhar*'s signature pharmacy and the most identity-defining preparation of the tradition. *Rasa vaadham* (mercurial alchemy) is a doctrinal core of *Siddhar* practice in a way it is not for classical Ayurveda.

Each of these preparations has measurable wearable consequences. *Linga chendooram* for chronic respiratory complaint affects RR and SpO2 baselines. *Rasagandhi mezhugu* for *Vaadham* in joints affects HRV and activity. *Amukkaraa choornam* (Ashwagandha powder) affects cortisol, sleep architecture, and HRV. *Aṟavalli kiḻangu* preparations affect glucose. A pattern engine reading RHR / HRV / glucose on an owner taking these preparations needs to know the pharmacology.

The primary-care audit flagged this gap as §2.5 (no medication context) and the Ayurveda audit extended it to §2.11 (no Ayurvedic dravyas). Bios has since shipped a [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L34) which the AnomalyDetector reads and the explanation builder surfaces. The Siddha-specific extension:

1. **AYUSH-code support in the `substance_key` vocabulary.** ICD-11 Chapter 26 Module 2 catalogues Siddha *gunapadam* substances. The same extension that closes the Ayurveda gap closes the Siddha gap.
2. **Free-text fallback for *Bhaishajya kalpana*-equivalent compounded preparations.** Many *Siddhar* preparations are clinic-specific compounds without a stable AYUSH code; the existing free-text path handles them.
3. **A pharmacology-reference path** analogous to [BiomarkerReference.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerReference.kt) — when an annotated substance is recognised, the explanation builder can surface the *expected directional change* in the wearable signature, drawing on the *Gunapadam* pharmacology references (the Tamil-language standard texts *Sarakku Suthiram*, *Gunapadam Mooligai Vaguppu* by Murugesa Mudaliar; the AYUSH *Standard Operating Procedures for Siddha Drug Manufacturing*). This is a citations table, not new architecture.
4. **A *Muppu* and *Rasa* preparation safety note.** *Muppu* preparations and mercury-class *rasa* compounds are pharmacologically active and contraindicated in pregnancy, paediatrics, and certain chronic conditions. The [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) gating already handles pregnancy and paediatric flags; the medication-annotation surface should *not* prescribe but *should* recognise when an annotated substance is one for which the existing physiology gate is clinically meaningful.

Manifesto-clean: Bios records what the owner says they are taking. The substance-effect literature is the *vaidyar*'s domain. Bios does not prescribe Siddha *gunapadam*.

### 2.8 *Varmam* — flagged, not recommended

The 108 *Varma* points (or 107 in some lineages; the count varies between Agasthiyar and Bohar transmissions) are the substrate of *Varma chikitsa* and the related martial discipline *Varma kalai*. Points are organised across the *paadu* (death-point, 12) / *thodu* (touch-point, 96) taxonomy and further subdivided by *naadi*, *kaalam*, *thodu*, *padu* qualities. The therapeutic application — *Varma murai* — uses targeted manual pressure, rotation, or specific impact techniques to *adangal* (suppress) or *thirupp* (release) the imbalance.

This is anatomical-point modelling that Bios has no business doing — the same conclusion the Ayurveda audit reached for *Marma* ([AYURVEDA_POV.md §2.9](AYURVEDA_POV.md)). The research-surface question (could HRV / EDA / skin-temperature response to *Varma* point stimulation during a session be recorded for the *vaidyar*'s session log?) is answered the same way: the data model supports it (timestamps + HRV + temp + skin-conductance) but the use case is narrow and the privacy threat model around anatomical-point metadata is uninvestigated.

**Recommendation:** flag, do not implement in Bios core. A third-party *vaidyar*-facing clinic-companion app reading Bios's HRV / temp stream via the ContentProvider is the right shape. Bios provides the substrate; the clinical *Varmam* surface lives elsewhere.

### 2.9 *Thokkanam* and *Yoga Maruthuvam* — intervention-event surface missing

*Thokkanam* (Siddha-specific manipulation / massage with nine techniques: *podithal*, *thirumal*, *thattal*, *kottuthal*, *mukkal*, *atangal*, *pidithal*, *muruakkal*, *vitethal*) and *Yoga Maruthuvam* (yogic therapy integrated into Siddha clinical practice) are second-tier therapies in the *Siddhar*'s order — used after *paththiyam* and *manam* but before *gunapadam* in many presentations.

A *thokkanam* session has measurable autonomic effects (HRV elevation, RR slowing, skin-temperature normalisation). A *yoga maruthuvam* session — specific *asana* and *praanaayaama* sequences targeted at *Vaadha*, *Pittha*, or *Kabam* imbalance — has similar measurable signatures. A *vaidyar* observing a patient's wearable trace before and after the session reads it for *prabaava* (effect).

Bios has no surface for these interventions. The same gap was flagged in [SOWA_RIGPA_POV.md §2.7](SOWA_RIGPA_POV.md) for *me btsa'* / *gtar ga* / *gser khab* / *bsku mnye*, and the recommendation is identical: an `INTERVENTION_EVENT` MetricType (or a new `MetricDomain.INTERVENTION`) with structured sidecar fields:

- `intervention_type` — enum spanning Siddha (*thokkanam_podithal*, *thokkanam_thirumal*, ..., *yoga_maruthuvam_pranayama*, *yoga_maruthuvam_asana_sequence*), Sowa Rigpa (*me_btsa*, *gtar_ga*, *bsku_mnye*), Ayurveda (*panchakarma_virechana*, *panchakarma_basti*, etc.), and Western (*physical_therapy_session*, *acupuncture_session*, etc.)
- `duration_min`
- `practitioner_id` (optional, owner-controlled)
- `body_region` (optional categorical)
- `intensity` (mild / moderate / strong)

The pattern engine reads the event as a *pattern_suppression* window (the same machinery the Ayurveda audit recommended for *Panchakarma* courses in §2.12). HRV swings in the 4–24 hours after *thokkanam* are *expected*, not anomalous, and the engine should not false-fire `recovery_deficit` on them.

This is a shared-engineering item across multiple audits — the same `INTERVENTION_EVENT` surface serves Siddha, Sowa Rigpa, Ayurveda, TCM, and Western physiotherapy.

### 2.10 The 96 *Thathuvam* framing — vocabulary, not schema

The *Siddhars* hold that the human body is one expression of 96 universal principles (the *thathuvangal*): 5 *bhuta* (great elements — earth, water, fire, air, ether); 5 *jnanendriya* (sense organs); 5 *karmendriya* (action organs); 5 *thanmaathiras* (subtle elements); 4 *antakkaranas* (inner instruments — *manas*, *buddhi*, *chitta*, *ahankaara*); 10 *vaayus* (the ten kinds of vital breath); *mukkuttram* (three humours); *thiri-malam* (three excretions); and the rest, totalling 96.

This is metaphysics, not engineering, and Bios is entirely correct not to encode it as `MetricType`. The point worth recording is *register*: the Bios manifesto's phrasing — "your body speaks, we help you listen" — sits closer to the *Siddhar*'s view of the body as a window onto a larger pattern than most consumer health software's framing of the body as a *system to be optimised*. A *Siddhar*-trained reader will find the manifesto's disposition congenial in a way the schema cannot.

**Recommendation:** no engineering work. This is named to acknowledge that the *register* of the manifesto already aligns with the tradition, and to caution against ever shifting the framing toward "track your body, hit your goals, level up your wellness." That shift would put Bios on the wrong side of both the manifesto and the *Siddhar*'s framing simultaneously.

---

## 3. Manifesto / Siddha-clinical alignment and tension points

These are *not* gaps — they are places where the manifesto's principles and Siddha clinical practice produce the same answer (or, in one case, a different one), and Bios should be aware of which it chose.

### 3.1 "Evaluation belongs to the owner" vs. the *Vaidyar*'s discernment

Siddha is, like TCM and Ayurveda, a practitioner-centred system. The *vaidyar* holds the evaluative role — *Noi Naadal* and *Maruthuvam* are practitioner-driven, not patient-self-managed. The owner contributes *Mozhi* (the voice of the symptom-account) and submits to *Naadi* / *Sparisam* / *Vizhi* (the practitioner's examination), but the integrated reading belongs to the *vaidyar*.

The Bios manifesto puts the owner at the centre. This is a load-bearing choice that this audit does not contest — it is consonant with the broader LETHE ownership posture, modern informed-consent norms, and the digital-rights register that Siddha practitioners in the diaspora increasingly themselves adopt.

The friction-point closure is the same as the Ayurveda audit's: the doctor-in-the-loop FHIR / encrypted-share surface lets the owner *choose* to share their Bios bundle with their *vaidyar*, who then performs the integrated Siddha reading. The *Mukkuttram* projection (§2.1), the *Ezhu Thathu* view (§2.4), the *Naadi character* view (§2.3), and the *Tamil season* annotation (§2.5) are what would make the bundle *legible* to a BSMS-trained reader without the practitioner having to translate from biomedical to Siddha vocabulary first.

### 3.2 "Silence is a feature" vs. *Kayakarpam* prevention

The *Siddhar* tradition is heavily preventive in posture — *Kayakarpam* literally is rejuvenation-and-longevity practice, and the rationale for *Muppu* and the *chitra mooligai* is that the *Siddhar* intervenes *before* the body's *Vaadha-Pittha-Kabam* equilibrium has shifted to manifest *noi*. The instruction is closer to "speak when *noi-munṉāl-kuṟigaḷ* (pre-disease signs) appear" than to "stay silent until certain."

Bios's [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) — "1–2 days before symptoms" — is exactly *noi-munṉāl-kuṟigaḷ* detection. The "silence is a feature" principle is not in tension here: silence applies to lifestyle judgments and to noise, not to genuine pre-disease signals. The `minActiveSignals` convergence requirement (3 of 6 for infection onset) is the multi-channel discipline *Envagai Thervu* itself applies.

### 3.3 "Personal baseline" vs. *iyalbu*-relative diagnosis

Siddha is *iyalbu*-relative in much the same way Ayurveda is *Prakriti*-relative. Bios's 14-day personal baseline is the operational form. The same caveat applies as the Ayurveda audit: some Siddha thresholds are *absolute*, not personal — *Vaadha-prakopa* with specific autonomic signs (palpitations, dryness, restlessness lasting >7 days) is read as imbalance regardless of personal baseline. The closure is mechanically simple: the *Mukkuttram* projection signals (§2.1) can use the same `absoluteAbove` / `absoluteBelow` machinery the biomarker layer already uses, for the classical thresholds where they exist. Where they don't, personal-baseline-relative is the correct fallback and matches the *iyalbu*-relative posture.

### 3.4 "Free for all" rhymes with the *Siddhar*'s offering posture

The *Siddhar* tradition, like several other South Asian medical systems, historically treats medicine as a duty offered without expectation of payment when the patient cannot pay (varyingly observed in modern Tamil Nadu practice, but the doctrinal ideal remains). Manifesto Principle 3 — full health intelligence for everyone, no subscription gating — restates this. The alignment is not coincidental; both positions are taken by traditions that conceive of medicine as service, not market.

### 3.5 No "Siddha balance score" — the right call

A particular tension to *avoid*: a single composite "*Mukkuttram* balance score" or "*Kayakarpam* longevity index" that reduces a constellation of signals to a single number. This would be the Siddha equivalent of the "biological age" composite the [DATA_MODEL.md](../DATA_MODEL.md) explicitly guards against, the Ayurveda "Tridosha index" the Ayurveda audit refuses ([AYURVEDA_POV.md §3.4](AYURVEDA_POV.md)), and the TCM "five-element harmony index" the TCM audit refuses ([TCM_POV.md §4](TCM_POV.md)). Surface the individual signals and their *Mukkuttram* projections; do *not* compose them into a single "your Vaadham is 67/100." Evaluation belongs to the owner and the *vaidyar*. Bios's existing posture on epigenetic clocks is the right model.

---

## 4. What I would recommend, prioritised for a Siddha-friendly Bios

**Tier A — vocabulary overlays, low engineering cost, high BSMS-utility uplift**

1. **Siddha re-labelling on existing pattern detail pages.** Pull-side only. Each of [chronicInflammation](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L309-L336), [recoveryDeficit](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L339-L363), [cardiovascularStress](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L187-L208), [sleepDisruption](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L158-L185), [mentalHealthCorrelate](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L424-L459), [menstrualCycleAnomaly](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L475-L498) and [infectionOnset](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) gets an optional Siddha-context section with classical *noi*-name (*Saagaadha azhalkku noi* for chronic inflammation; *Vaadha-kshayam* with *Saara-kuraivu* for recovery deficit; *Hrudaya-Vaadham purvarupa* for cardiovascular stress; *Anidhirai-Vaadham* for sleep disruption; *Manak-kalakkam* with *Vaadha-Pittha* signature for mental-health correlate; *Sūthaga-vinai-deedham* for cycle anomaly; *Sannipaada jura* purvarupa for infection onset) and a citation to the classical Tamil reference. No new entities, no new patterns. Same machinery as the Ayurveda and TCM overlay recommendations — engineering-shared.

2. **Pull-side *Mukkuttram* projection surface** (§2.1). A Dashboard → Siddha View toggle, opt-in. Renders existing signals projected onto *Vaadham* / *Pittham* / *Kabam* axes with subtype dominance and classical references. Requires the projection engine code (shared infrastructure with Ayurveda dosha projection, TCM eight-principles view, Sowa Rigpa *Nyepa* overlay) but no new sensors and no new pattern definitions.

3. **`IN` (India) and `LK` (Sri Lanka) regions in [RegionConfigProvider](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt).** Correct ICMR / SLMC disclaimers, BP threshold conventions tuned for South Asian cardiovascular epidemiology, AYUSH regulatory framing.

**Tier B — schema additions, single-day each**

4. **`URINE_OBSERVATION` and `NEIKKURI_OBSERVATION` MetricTypes** (§2.2). Manual-entry, structured sidecar fields covering the eight classical *Neerkkuri* axes plus the *Neikkuri* oil-spread pattern. Optional photo storage in an isolated SQLCipher database (reproductive-DB precedent). This is the most Siddha-distinctive single feature Bios could add.

5. **`PULSE_QUALITY` manual-entry MetricType** (§2.3) for owner-self-pulse or *vaidyar*-recorded *Naadi gati* observations (snake / frog / swan classification, three-position dominance, *bala*). Sidecar attached to a HEART_RATE reading.

6. **AYUSH-coded Siddha *gunapadam* in [MedicationAnnotationRepo](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L34)** (§2.7). Extend `substance_key` vocabulary to consume ICD-11 Chapter 26 codes; pattern-explanation builder picks them up automatically. Closes the gap for all three AYUSH systems (Siddha, Ayurveda, Unani) simultaneously.

7. **`EzhuThathuView` pull-side surface** (§2.4). Re-arrangement of existing biomarker and body-composition readings along the seven-tissue cascade. No new ingestion.

**Tier C — engine-level, multi-day each**

8. **`SeasonalModifier` axis on `ConditionPattern`** (§2.5) with a `TamilSeason` derivation table (and parallel tables for Ayurvedic *ritus*, Tibetan six-seasons, Western four-seasons). Per-pattern threshold modifiers for the *aaru paruvam*. Same infrastructure recommended in the Ayurveda and Sowa Rigpa audits — engineering-shared.

9. **`INTERVENTION_EVENT` MetricType** (§2.9) with a multi-tradition `intervention_type` enum covering *thokkanam*, *yoga maruthuvam*, *me btsa'*, *Panchakarma* sub-procedures, Western physiotherapy. Pattern engine reads as a suppression window. Same infrastructure recommended across the Ayurveda, Sowa Rigpa, and Siddha audits.

10. **Preserve PPG waveform morphology in `PpgResult`** (§2.3). Stop discarding amplitude-CoV, rise-time, and asymmetry features after rejection-checking. Unlocks the *Naadi character* view, the TCM *mai* surface, the Sowa Rigpa pulse-morphology surface, and the Western pulse-wave-analysis literature simultaneously.

11. **Tropical-infectious-disease staging annotation on `infection_onset`** (§2.6) for the AYUSH-locale rendering. Adds dengue critical-window, chikungunya arthralgia, *Faget sign* enrichments. Region-gated.

**Tier D — flagged, not recommended (yet)**

12. **Varmam point modelling** (§2.8). Defer to a third-party *vaidyar*-facing companion app reading Bios's HRV / temp via ContentProvider. Anatomical-point metadata does not belong in Bios core.

13. **Automated *Neikkuri* classification.** The owner classifies; the *vaidyar* reviews. A CNN classifying oil-spread patterns would be a Bios-evaluating-the-person surface against the manifesto. Defer to research / third-party tooling.

14. **Automated *Naadi paritchai*.** The three-position pressure-modulated reading is not optical-PPG-extractable and a Bios feature claiming to do it would be misuse. The owner-self-pulse manual entry (Tier B item 5) is the manifesto-clean closure.

**Do not adopt**

- A composite "*Mukkuttram* balance score" or "*Kayakarpam* longevity index." Same reason DATA_MODEL.md guards against composing epigenetic clocks — evaluating the person is not Bios's job. The TCM, Ayurveda, Sowa Rigpa and Siddha audits all converge on this prohibition.
- Push-side Siddha alerts ("your *Vaadham* is elevated"). Surfacing the projection is fine; pushing it as a notification is exactly what the [AlertContentPolicy](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) banlist exists to prevent.
- Automated Siddha *gunapadam* prescription. Bios records what the owner annotates; the *vaidyar* prescribes.
- A "tracking-your-body, optimising-your-wellness" framing shift in the manifesto's surface text. This would put Bios on the wrong side of both the manifesto and the 96 *Thathuvam* register simultaneously (§2.10).

---

## 5. Summary line for a BSMS practitioner reading the printout

> Bios is, in its bones, an instrument a *Siddhar* would recognise: it observes through multiple channels, requires convergence before it speaks, holds the personal baseline as the reference frame, refuses to evaluate the person, and leads with diet-and-conduct guidance before clinical referral. As an instrument for *Noi Naadal* it is competent for the *Sparisam* (touch — skin temperature) and partial *Naadi* (pulse, rate only) axes; *Naa* (tongue), *Niram* (complexion), *Mozhi* (voice), *Vizhi* (eye), *Malam* (stool), and most consequentially *Moothiram* (urine — both *Neerkkuri* and the uniquely Siddha *Neikkuri*) are absent. The three *Mukkuttram* are not modelled, the seven *Ezhu Udal Thathukkal* are touched only at the surface tissues, and the Tamil-tropical *aaru paruvam* climatology that frames most Siddha clinical work in the Madurai latitude is invisible to the engine — the same `cardiovascular_stress` pattern that fires correctly in temperate climates will misread the *Mudhuvenil-Pittha* seasonal rise as pathology, and the [HypertensionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/HypertensionPatterns.kt) thresholds are unaware of the heat-induced summer BP reduction documented in South Asian cohorts. The single most distinctively Siddha addition Bios could make is a manual-entry *Neerkkuri* / *Neikkuri* surface with an isolated photo store — no other consumer wearable offers this, and the engineering is small. Closing the *Mukkuttram* gap is the highest-impact change overall but shares engineering with the Ayurveda *dosha*, TCM *zang-fu*, and Sowa Rigpa *Nyepa* overlays recommended elsewhere. None of these gaps require Bios to become a Siddha application; all of them are within the existing architecture; none of them violate the manifesto. The instrument can become legibly useful to a *vaidyar* in a Tamil Nadu, Sri Lankan, or diaspora clinic with a vocabulary layer over signals it already collects — *evaluation belongs to the owner and to the practitioner the owner invites in.*
