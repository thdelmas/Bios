# African Traditional Medicine Practitioners' Audit — Bios as a Body-Watching Instrument in a Plural Tradition

**Scope:** Bios viewed not from a single tradition but from the *plural* landscape of African traditional medicine — codified to different degrees across South Africa (Traditional Health Practitioners Act 2007), Ghana (TMPC), Nigeria (TCM Act), Ethiopia (Tewahedo lineage and registered herbalists), Mali and Senegal (formal *tradipraticien* registers under WHO-AFRO and African Union frameworks), and the long-recognised Yoruba bonesetting tradition documented in the orthopaedic literature. Read by a panel: a Yoruba *babalawo* (Nigeria — Ifá diviner-herbalist), a Zulu *inyanga / sangoma* (Southern Africa — herbalist and diviner; Nguni vocabulary used here for *amaXhosa* *amagqirha* and *izangoma* by extension), an Ethiopian *medhanit awaki* (Tewahedo-tradition herbalist), a West African *tradipraticien* registered under the Malian / Senegalese framework, and a Yoruba bonesetter trained in the closed-reduction lineage internationally studied by orthopaedic surgeons (OlaOlorun, BMJ; Owoseni; multiple ortho-trauma papers since the 1980s).
**Date:** 2026-05-22
**Branch:** `feat/metric-info-sheets-on-read`
**Lens:** African traditional medicine, *plural*. The five practitioner perspectives above do not collapse into a single voice. Where they converge, this audit says so. Where they diverge, both readings are reported. Where Bios's biomedical frame is *orthogonal* to the practitioner's diagnostic frame, that is stated as orthogonality — not as a defect.
**Auditor:** Claude (Opus 4.7)

Files reviewed (deep-read): [MANIFESTO.md](../../MANIFESTO.md), [docs/ROADMAP.md](../ROADMAP.md), [docs/DATA_MODEL.md](../DATA_MODEL.md), [docs/WEARABLES_AND_DETECTION.md](../WEARABLES_AND_DETECTION.md), [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt), [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt), [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt), [AlertManager.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertManager.kt), [AnomalyDetector.kt](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt), [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt), [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt), [Enums.kt](../../android/app/src/main/java/com/bios/app/model/Enums.kt), [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt), [docs/audits/MEDICAL_SPECIALTIES_WORLDWIDE.md](MEDICAL_SPECIALTIES_WORLDWIDE.md) §10.

This audit does **not** ask Bios to become a divination tool, a herbal-pharmacy register, or a bonesetting clinic-management system. It asks where, viewed through each of the five practitioner frames, Bios's current observations are usable, where they are silent, where they are *structurally aligned* with African traditional practice (in ways even the manifesto may not have noticed), and where the differences are differences of *kind* that should be named rather than papered over.

---

## A note on language and naming

This audit uses the names traditions use for themselves: *babalawo* (Yoruba diviner-priest of Ifá), *inyanga* (Zulu — emphasis on herbalist), *sangoma / isangoma* (Nguni — emphasis on diviner; some practitioners are both), *amagqirha* (Xhosa diviner), *medhanit awaki* (Amharic — herbal practitioner; literally "medicine-knower"), *tradipraticien* (the term used by the WHO-AFRO and Bamako-Dakar registered-practitioner framework), *dibia* (Igbo), *nganga* (Bantu), *bonesetter* (the English term used in the surgical literature; in Yoruba, *Onibu-egungun* and *Onisegun-egungun* refer specifically to the trade). It does **not** use "African shaman," "witch doctor," or "African folk medicine" — those terms misname the traditions and obscure the regulatory and clinical distinctions between them.

It also does not treat African traditional medicine as a single system. The Yoruba bonesetter and the Zulu *sangoma* do work that is as unlike each other as the work of an orthopaedic surgeon and a psychiatrist; the *medhanit awaki* working within a Tewahedo monastic pharmacy and the *tradipraticien* registered under Mali's 1994 traditional-medicine framework operate in different institutional registers. They are grouped only by region; they are not grouped by method.

---

## Executive summary

Bios is, at the level of *structural posture*, **more compatible with the institutional shape of African traditional medicine than its biomedical idiom would suggest.** Local-first storage, no cloud dependency, no Google Play Services, the entire app working on Android 9+ on the lowest-spec device the owner happens to have — this is *exactly* the posture that has made M-PESA, mTRAC, Praekelt's MomConnect, and the WHO-AFRO mobile-health stack viable in sub-Saharan Africa where mid-tier Android with intermittent connectivity is the dominant computing surface. The "data stays on device unless the owner moves it" architecture is also a structural defence against the pattern of *biopiracy* that has shaped the political history of African ethnobotanical knowledge (Hoodia, rooibos, devil's claw, *Sutherlandia*, *Pelargonium sidoides*). Bios is one of very few consumer health products in this size class whose technical architecture is *already* aligned with the data-sovereignty norms that the African Union's 2001–2010 / 2011–2020 / current Decade of Traditional Medicine frameworks have been articulating. The manifesto's posture of "the owner is final" coincides, on data, with the regulatory direction of travel — even if it does not coincide on collective decision-making, where the panel will register a tension below.

At the level of *content*, Bios is implicitly Northern-temperate. Its 33+ condition patterns track infections, cardiovascular drift, sleep architecture, metabolic dysregulation, and Northern-hemisphere chronic-disease patterns. It has no pattern for malaria, no pattern for sickle-cell crisis, no pattern for schistosomiasis, no pattern for dengue or chikungunya in the Sahel, no pattern for Lassa or for Ebola monitoring during outbreak periods, no pattern for trypanosomiasis. It has no demographic gating for pregnancy-attended-by-TBA contexts despite traditional birth attendants still attending a substantial fraction of births in rural sub-Saharan Africa. It has six region configs — US, GB, EU, CA, AU, JP — and no African region in [RegionConfigProvider.kt:200-206](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt#L200-L206); the EU fallback at [RegionConfigProvider.kt:209](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt#L209) silently substitutes ESC/ESH cut-offs for any device locale set to Kenya, Nigeria, South Africa, Ethiopia, Senegal, Mali, Ghana, Tanzania, Uganda, or any other African country. The app has not been localised into Swahili, Hausa, Yoruba, Zulu, isiXhosa, Amharic, Wolof, Bambara, Lingala, or Arabic, and [strings.xml](../../android/app/src/main/res/values/strings.xml) is 31 lines long with no `values-*` overlays.

Beyond the implementation gaps, there are also *fundamental philosophical differences* that the panel agrees are not Bios's to fix. Divination — *Ifá-opele* and palm-nut chains for the *babalawo*, the throwing of *amathambo* (bones) for the *sangoma*, cowrie casts (*owo-mẹrindinlogun*) for sixteen-cowrie diviners, *Vodun* card-and-shell systems in Benin and Togo — is *orthogonal* to biomarker measurement. It is not a less-precise version of pulse-taking; it is a different epistemology of diagnosis. Bios reads HR and HRV; the *babalawo* reads the configurations of palm-nuts mediating a consultation between the client, the priest, and the orisha Ifá. These two procedures answer different questions. The audit names this orthogonality as orthogonality, not as a gap.

Ordered by impact within the things Bios *could* address without overreaching, the gaps are:

1. **No regional disease-pattern library for sub-Saharan or Sahel Africa.** The condition library in [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) and [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) is anchored on Northern-temperate disease prevalence. There is no pattern for *malaria-onset* (the rising-RHR / rising-skin-temperature / falling-HRV cluster Bios already detects via `infection_onset` will fire — but generically; no malaria-aware staging, no quartan/tertian periodicity model, no fever-curve recognition for *P. falciparum* vs. *P. vivax*). There is no pattern for *sickle-cell crisis-onset*; SpO2 baselines differ in heterozygous-trait carriers (largely unchanged) and HbSS homozygotes (chronically 90–95 % at steady state), and Bios's fixed `spo2ConcernThreshold = 95.0` in every region config will false-fire continuously for SCD homozygotes. There is no pattern for *schistosomiasis* (haematuria / chronic anaemia), no pattern for *typhoid-onset*, no pattern for *Lassa* or *dengue* or *chikungunya* fever progressions. This is the single largest content gap relative to the disease landscape of the regions African traditional practitioners actually work in.
2. **No SCD-aware SpO2 baseline.** The [RegionConfigProvider.kt:243-244](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt#L243-L244) threshold of `spo2ConcernThreshold = 95.0` / `spo2UrgentThreshold = 92.0` is correct for most populations but *systematically wrong* for HbSS homozygotes and HbSC compound heterozygotes — populations that are concentrated in the regions African traditional medicine serves. The [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) enum has eight states (`STANDARD`, three pregnancy trimesters, `POSTPARTUM`, `ATHLETE_HIGH_FITNESS`, `FRAILTY_FLAG`, `PAEDIATRIC`) and none of them is `SICKLE_CELL_TRAIT` or `SICKLE_CELL_DISEASE`. G6PD-deficiency status — also concentrated in malaria-endemic regions — has similar implications for several drug-interaction patterns Bios does not currently model. This is the single highest-priority demographic-gating addition for the African owner cohort.
3. **No African region config; the EU fallback is silent.** [RegionConfigProvider.kt:215-218](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt#L215-L218) returns the EU config when the country code is not in the explicit map. A device locale set to `en_NG`, `sw_KE`, `am_ET`, `fr_SN`, `zu_ZA`, or `en_GH` therefore receives `ESC/ESH` hypertension cut-offs (140/90 mmHg), an EMA regulatory disclaimer, and `mmol/L` glucose units — none of which is wrong per se, but none of which corresponds to the actual regulatory bodies the owner's prescriber would cite (e.g. South Africa's SAHPRA, Nigeria's NAFDAC, Ghana's FDA, Ethiopia's EFDA, the African Medicines Agency once operational). The disclaimer text refers to "CE-marked medical device" status that has no jurisdictional meaning for the user. A `ZA`, `NG`, `KE`, `ET`, `SN`, `GH` (at minimum) region config would close this silently.
4. **No surface for herbal-medication context.** The Phase 7.4 medication-annotation surface read by [AnomalyDetector.kt:34, 376-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L34) lets the owner record current medications so the pattern-explanation builder can suppress false-positive autonomic signatures (the beta-blocker-bradycardia case the primary-care audit raised). That same surface is the *only* hook a traditional-medicine practitioner has to tell Bios that the owner is taking *Sutherlandia frutescens* (cancer-bush / *unwele* / *kankerbos*), *Hypoxis hemerocallidea* (African potato), *Artemisia afra* (African wormwood), *Catharanthus roseus* (Madagascar periwinkle — source of vincristine), *Pelargonium sidoides*, *Combretum micranthum* (*kinkéliba* in Senegal), *Moringa oleifera*, or any of the thousands of plants in the ethnobotanical pharmacy. Bios currently has no first-class field for herbal-formula annotation, no RxNorm-equivalent for ethnobotanical names, and no way to express that what the owner is taking is a *decoction* (Yoruba *agbo*, Wolof *ndeup-related preparations*, Amharic *atikilt-medhanit*), a *powder*, a *poultice*, or an *infusion*. A free-text medication annotation could accept these, but without an explicit ethnobotanical / traditional-pharmacy surface, the practitioner has no clean way to give Bios the context.
5. **No language localisation into any African language.** [strings.xml](../../android/app/src/main/res/values/strings.xml) has no `values-sw/` (Swahili), `values-ha/` (Hausa), `values-yo/` (Yoruba), `values-zu/` (Zulu), `values-xh/` (isiXhosa), `values-am/` (Amharic), `values-ar/` (Arabic — Maghreb and Sudan), `values-fr/` (Francophone West Africa, though largely English already on the strings side), or `values-pt/` (Lusophone Africa). The Decade of African Traditional Medicine specifically prioritises *vernacular access to health information* as a policy goal. Even with the small surface in `strings.xml` today, the absence of any African-language overlay is a real adoption barrier — practitioners cannot demonstrate the app to clients in the client's own language.
6. **No pregnancy-traditional-birth-attendant gating.** The [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) enum has `PREGNANCY_T1/T2/T3` and `POSTPARTUM` but the surrounding flow assumes biomedical antenatal care. In much of rural sub-Saharan Africa, the *traditional birth attendant* (TBA) is the primary attendant; WHO has shifted from "phase out TBAs" to "integrate TBAs into the referral chain" over the last fifteen years. Bios's pregnancy-state surface has no concept of "who is the attendant" and no field that a TBA could read or write. The primary-care audit (§2.7) flagged pregnancy gating as a generic gap; this audit reframes it: the *attended-by* surface is what would let a TBA-attended pregnancy be visible to Bios.
7. **No tropical-disease-aware fever logic.** The `infection_onset` pattern in [ConditionPatterns.kt:127-156](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L127-L156) treats fever as undifferentiated. In a malaria-endemic region, the fever-curve *shape* — quartan (every 72 h, *P. malariae*), tertian (every 48 h, *P. vivax* / *P. ovale*), or quotidian/sustained (*P. falciparum*) — is itself diagnostic. The same skin-temperature time-series Bios already records would, with periodogram analysis, expose this rhythm. Bios discards the rhythm and reports only the level.
8. **No surface for the *babalawo* / *sangoma* / *amagqirha* practitioner-in-the-loop, parallel to the doctor-in-the-loop.** The Bios "professional review" flow lets the owner share a FHIR bundle or encrypted export with a *biomedical* clinician (six methods listed in the roadmap: FHIR, encrypted export, QR, verbal, telemedicine, screenshot). For an owner whose primary diagnostician is the *babalawo* or *isangoma*, the equivalent flow does not exist; the FHIR bundle is the wrong format anyway. A practitioner-agnostic "share with practitioner" surface — that does not assume FHIR fluency, and that can produce a printable / orally-summarisable account of the owner's recent baseline excursions — would let a traditional practitioner read what Bios saw without having to learn HL7.
9. **No place to record divinatory consultation outcomes — and this is correct, but should be acknowledged.** The panel does *not* ask Bios to model Ifá *Odu* outcomes, *amathambo* configurations, cowrie casts, or *Vodun* card readings. These are not biomarker data. But Bios's `HealthEventType` enum in [Enums.kt:116-123](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L116-L123) (`SYMPTOM`, `HYPOTHESIS`, `DOCTOR_VISIT`, `DIAGNOSIS`, `TREATMENT`, `NOTE`) presumes a biomedical encounter chain. An owner who consults a *babalawo* on Monday and a clinic on Wednesday has no consistent way to record both visits as *the same kind of event* — a consultation, with a practitioner, that yielded a reading. This is a small naming choice (`PRACTITIONER_VISIT` as a parent category, or accepting `DOCTOR_VISIT` more liberally with a `practitionerType` annotation) that costs almost nothing and acknowledges the plural practitioner landscape.
10. **Fundamental philosophical difference, named honestly: divination vs. biomarker; ancestral / spirit etiology vs. physiological etiology.** Many of the diagnostic frames the panel uses — *amafufunyana* / *ufufunyane* (Nguni — spirit-possession-related distress), *umnyama* (darkness / spiritual heaviness), *abiku* and *ogbanje* (Yoruba and Igbo — repeated infant death attributed to a spirit-child returning), the seven-emotions-equivalents in various traditions, ancestral-displeasure etiologies — are not biomarker-visible and were not constructed to be. These appear in DSM-5-TR's "Cultural Concepts of Distress" section precisely because biomedical psychiatry has had to accommodate their persistent diagnostic validity *within* their communities of use. Bios is silent on all of this. The panel does not ask Bios to *speak* on it — Bios is an instrument for biomarker observation, not a cosmological-diagnostic engine. But Bios should at least *not assume* its biomarker frame is the only frame the owner is operating in. Practically, this means the alert text should not say "this is what your body is telling you" with the implication that biomarker readings are the whole truth of the owner's condition. The push-side language in [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) already avoids this, almost by accident, by sticking to factual data statements ("RHR +2σ for 48h") rather than interpretive ones. That instinct is correct; the audit asks that it be preserved deliberately.

The rest of this audit walks each of the strengths, the gaps, and the orthogonalities with file references.

---

## 1. What Bios already does well, viewed through African traditional-medicine frames

| Principle / structural posture | How Bios already embodies it | Why it lands with this panel |
|---|---|---|
| **Data sovereignty** | On-device SQLCipher; the owner decides what leaves the device; nothing leaves by default; export is encrypted and opt-in; no Google Play Services dependency | The history of biopiracy around African ethnobotanical knowledge (Hoodia, rooibos, devil's claw, *Sutherlandia*, *Pelargonium sidoides*) has shaped a regulatory orientation in which *who holds the data* is not separable from *who can monetise the medicine*. Bios's local-first posture is structurally protective in exactly the way the African Union's traditional-medicine framework calls for |
| **No subscription gating, no premium tier** | Manifesto Principle 3 ("full health intelligence for everyone"); no Play Billing | This is not philanthropy in the African mobile-health context — it is a baseline. The cost-sensitivity of mid-tier Android adoption in sub-Saharan Africa is what differentiates an app that gets used from one that gets installed and uninstalled |
| **Works on low-spec, intermittently-connected Android** | API 28+; no cloud dependency; works offline; phone-sensor adapters cover the case where no wearable is paired | The dominant smartphone in sub-Saharan Africa is a 2017-class Android with intermittent data. Most consumer health apps assume always-on cloud sync and recent flagship hardware. Bios does not |
| **The owner is final** | Manifesto Principle 7 ("instrument, not coach"); the CI-gated banlist in [AlertContentPolicy.kt:51-83](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt#L51-L83) | Aligns with the *tradipraticien* register's framing of the consultation as advisory-to-the-client rather than directive. Less aligned with traditions in which illness is family-or-community-framed — see §3 for the tension |
| **Personal baseline, not population norm** | 14-day rolling baseline; z-score deviation; biomarker patterns gate on absolute cutoffs as a separate layer | Closer to the *individual-as-their-own-reference* logic that pulse-and-tongue traditions across the continent use than to a Northern-population-norm frame. A child with a baseline RHR of 95 is not "tachycardic" in Bios's reading; this is structurally correct in any tradition |
| **Convergence reasoning** | `minActiveSignals = 3` in `infection_onset`; `required = true` on biomarker-gate rules; multi-signal cross-correlation | Maps onto the multi-sign reasoning practitioners across the continent use — *inyanga* herbalists do not diagnose on one observation alone; bonesetters use palpation + visual + functional-test convergence; the *medhanit awaki* assesses pulse + colour + complaint together |
| **Silence is a feature** | Push/pull split in [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt); no daily digest unless owner enables it | The *sangoma* speaks when the bones speak; the *babalawo* speaks when the Odu speaks. Unsolicited interruption is uncharacteristic across the panel's traditions. Bios's restraint matches |
| **No image-capture as default, isolated reproductive DB** | Separate SQLCipher key for reproductive data, post-Dobbs threat model | Adapts well to African contexts where reproductive surveillance has its own histories (colonial-era forced contraception, contemporary criminalisation of abortion in many jurisdictions). The threat model generalises |
| **FHIR R4 export exists but is opt-in** | Doctor-in-the-loop flow; six sharing methods; nothing pushed to a backend the owner did not choose | The fact that Bios *can* speak the biomedical interchange format but does not *insist* on it is the right posture for an owner whose primary diagnostician may not use FHIR at all |
| **Caesarean tradition acknowledgment in the historical record** | (Not in Bios; mentioned for context.) The precolonial Buganda caesarean tradition documented by R. Felkin in the *Edinburgh Medical Journal* in 1879 is the standing exemplar that African surgical practice is not a 20th-century import | This audit does not claim Bios "captures" this. It notes that Bios's posture of *not assuming* biomedicine is the only legitimate medical lineage matters in a region whose own surgical history predates Lister |

These are not parity wins. Several of these are places where Bios is *meaningfully ahead* of the consumer-health category on dimensions that matter specifically in African deployment contexts — and where, the panel notes, the alignment is *structural* rather than performed. Bios did not build a "for-Africa" feature set. It built a privacy-first, owner-sovereign, low-spec-friendly architecture, and that architecture *happens to be* the architecture African health-tech policy has been arguing for since the African Union's 2001 declaration.

---

## 2. Content gaps, ordered by impact

### 2.1 No regional disease-pattern library for sub-Saharan / Sahel / Maghreb Africa

The 33+ condition patterns in [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) and [BiomarkerConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/BiomarkerConditionPatterns.kt) are anchored to Northern-temperate chronic-disease prevalence. The citation list reads Mishra, Quer, Smarr, Ridker, ADA, NCEP, AACE/ATA, Endocrine Society — all institutionally Western. The pattern library has nothing for the conditions that account for a substantial fraction of morbidity and mortality in the regions African traditional practitioners actually serve:

| Condition | Bios coverage | What the panel would want |
|---|---|---|
| **Malaria (uncomplicated)** | None. `infection_onset` would fire generically | A *malaria-onset* pattern with periodicity-aware fever-curve detection. The skin-temperature time-series Bios already has would, with simple periodogram analysis over 3–7 days, expose tertian (48 h, *P. vivax* / *P. ovale*) and quartan (72 h, *P. malariae*) periodicities. *P. falciparum* fever is sustained/quotidian and lacks the rhythm, but the *combination* of sustained fever + falling SpO2 + rising RHR + altered consciousness is the severe-malaria signature WHO and MSF train field staff to recognise |
| **Severe malaria** | None | A `severe_malaria_signature` pattern that uses SpO2 + temperature + the `CONSCIOUSNESS_LEVEL` field already present in [MetricType.kt:80](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L80) (Glasgow Coma Scale 3–15) to escalate to `URGENT`. The CONSCIOUSNESS_LEVEL field is already on the bus and unused by any current pattern; this is one of the highest-leverage signal additions Bios could make |
| **Sickle-cell crisis (VOC)** | None; baselines are wrong for SCD homozygotes (see §2.2) | A `vasoocclusive_crisis_signature` pattern that, in an owner with `SICKLE_CELL_DISEASE` physiology state, watches for the sudden onset of pain (`PAIN_SCORE` is already in [MetricType.kt:79](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L79)) + falling SpO2 below the SCD-adjusted baseline + rising RHR. The pattern is well-characterised in the haematology literature (Steinberg, Vichinsky, Ware) |
| **Schistosomiasis** | None | Hard for a wearable to see directly. The biomarker import path could flag chronic eosinophilia and microscopic haematuria when those labs are imported — both are pull-side surfaces |
| **Typhoid (enteric fever)** | None | The classical *step-ladder* fever rise (slow daily climb 0.5–1°C over a week) is exactly what the skin-temperature time-series can detect. A `typhoid_fever_signature` pattern using that rise shape, plus relative bradycardia for the level of fever (Faget's sign — a clinically taught feature), would be a real contribution |
| **Lassa fever / VHF** | None | Outbreak-period-only pattern; would need a regional alert-window flag (West Africa during dry season) and would surface as `URGENT` on fever + bleeding-tendency markers if the owner had recently logged exposure. Hard to detect from wearables alone; better as a pull-side risk-context surface |
| **Dengue / chikungunya** (Sahel and East Africa, expanding) | None | The dengue *fever / arthralgia / rash / bleeding tendency* progression is staged; the WHO 2009 classification distinguishes dengue / dengue-with-warning-signs / severe dengue. A staged pattern reading temperature trajectory + platelet count (when imported as biomarker) + reported pain location would be the format |
| **Cholera / acute watery diarrhoea outbreaks** | None | Outpacing wearable detection; better as a pull-side outbreak-aware advisory tied to the regional config |
| **Trypanosomiasis (sleeping sickness)** | None | Late-stage CNS-involvement signs are exactly the sleep-architecture inversion patterns Bios already detects via `CircadianConditionPattern` — but the *interpretation* in HAT-endemic regions is different. A regional advisory in pull-side context would be the way in |
| **Yellow fever, meningococcal disease (Sahel meningitis belt)** | None | Outbreak-period pattern; pull-side regional advisory |
| **Tuberculosis** | None | Chronic cough, night sweats, weight loss — none of which Bios captures directly today, though the audit notes [MetricType.kt](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt) has no weight metric in the permissions array, only HC permission entries for it |
| **HIV-related opportunistic-infection patterns** | None | Out of scope as a primary detection target — but the convergence-reasoning machinery would let an owner who has annotated HIV status receive context-appropriate pattern interpretations. This is the medication-annotation surface (§2.4 herbal extension) generalised |
| **Anaemia of various causes** (iron-deficiency, SCD, malaria-related, hookworm) | None | Pull-side biomarker surface for haemoglobin / haematocrit / ferritin / MCV would slot into the existing BIOMARKER domain |

**Practitioner reading:**

- The *babalawo*: "Bios is silent on the conditions my clients come to me for. The infection-onset pattern is generic; it cannot tell me *which* infection. For a febrile child in Ibadan, the question is not 'is this an infection' but 'is this malaria, typhoid, sepsis, or something else.' Bios does not answer the second question."
- The *inyanga / sangoma*: "The cardiovascular patterns are well-developed for someone whose risk profile is European. For a Zulu patient with sickle-cell trait — which is many of them — the SpO2 thresholds are wrong. For a patient with HIV — also many — the immune-correlate baselines are wrong. The app does not know who is in front of it."
- The *medhanit awaki*: "In Tewahedo monastic practice we have a long pulse and visual examination tradition. Bios does not see pulse qualities — it sees rate. But the more pressing thing is that we cannot read the app in Amharic, and the patient cannot read it either."
- The *tradipraticien* (Mali/Senegal register): "The framework I work under expects me to refer biomedical cases to biomedical practitioners and vice versa. For that referral to be meaningful, the biomedical app must speak the biomedical clinical language of *this region*, not Europe. It does not."
- The *bonesetter*: "Bios watches biomarkers. My work is fractures and dislocations. Bios cannot help me reduce a Colles' fracture and I do not expect it to. But the post-reduction monitoring period — compartment-syndrome watch, distal pulse, neurovascular check — is exactly the kind of monitoring it *could* do, and does not."

**Recommendation:** a regional condition-pattern overlay, analogous to the existing region-config layer, that adds African-disease-aware patterns when the owner's region is set to an African country code. Not an automatic-by-IP-geolocation feature — the manifesto's posture of explicit owner choice applies. A `useTropicalDiseasePatterns` flag in the region config, with a documented list of patterns added (malaria, typhoid, dengue, schistosomiasis biomarker, severe-malaria escalation, SCD-crisis), is the manifesto-aligned shape.

### 2.2 Sickle-cell disease and trait — no demographic gating

[RegionConfigProvider.kt:243-244](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt#L243-L244) declares:

```kotlin
spo2ConcernThreshold = 95.0,
spo2UrgentThreshold = 92.0,
```

These are the same in every region config. They are *clinically correct* for the normal-haemoglobin majority. They are *wrong* for HbSS homozygotes, who at steady-state baseline run 88–95 % SpO2; for HbSC compound heterozygotes; and to a lesser extent for HbS trait carriers under exertional stress. In sub-Saharan Africa, SCD-trait prevalence reaches 10–40 % in some populations and HbSS-homozygote prevalence is 1–2 % in West Africa. An app that treats every SpO2 < 95 % as concerning will produce continuous false-positive `URGENT` alerts for a meaningful fraction of African owners.

The [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) enum has eight entries; none is `SICKLE_CELL_TRAIT`, `SICKLE_CELL_DISEASE_SS`, `SICKLE_CELL_DISEASE_SC`, `BETA_THALASSAEMIA_TRAIT`, or `G6PD_DEFICIENT`. The enum's class-doc opens "The 14-day rolling personal baseline is correct for a stable adult. It produces systematically wrong signals in five populations" and lists pregnancy, postpartum, paediatrics, frailty, athletes. Haemoglobinopathy belongs on that list.

**Practitioner reading:**

- The *inyanga*: "If a patient with sickle-cell trait wears this device through a stressful day or at altitude, the alerts will scream. The patient learns to ignore them. Then a real desaturation comes and the alert means nothing."
- The *babalawo*: "The SCD-homozygote child in my client's family will, by the device's measure, be in a constant state of urgent alarm. The device is not wrong about the number; it is wrong about whose baseline it is comparing to."

**Recommendation:**

1. Add `SICKLE_CELL_TRAIT`, `SICKLE_CELL_DISEASE_SS`, `SICKLE_CELL_DISEASE_SC` to [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) (with doc-comment on the haematology literature and acknowledgment that this is *owner-set*, never inferred — same posture as pregnancy).
2. Per-state SpO2 baselines (HbSS steady-state: 88–95 %; HbSC: 90–96 %; trait: normal at rest, owner-set warning at altitude or exertion).
3. A `vasoocclusive_crisis_signature` condition pattern with `severityFloor = AlertTier.URGENT` for SCD-state owners — onset triad of acute pain, SpO2 fall *below the SCD-adjusted baseline*, RHR rise.
4. G6PD-deficiency status as a similar self-declared state, gating drug-interaction-related patterns once the medication-annotation surface knows about G6PD-implicated drugs (primaquine, dapsone, sulphonamides, several antimalarials).

This is the single highest-priority demographic-gating addition for the African owner cohort, and the file-level changes are small.

### 2.3 No African region config; the EU fallback is silent and misleading

[RegionConfigProvider.kt:200-218](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt#L200-L218):

```kotlin
private val configs: Map<String, RegionConfig> = mapOf(
    "US" to usConfig(),
    "GB" to gbConfig(),
    "EU" to euConfig(),
    "CA" to caConfig(),
    "AU" to auConfig(),
    "JP" to jpConfig()
)
private val defaultConfig = euConfig()
```

There is no `ZA`, `NG`, `KE`, `ET`, `SN`, `GH`, `EG`, `MA`, `DZ`, `TN`, `UG`, `TZ`, `RW`, `CI`, `CM`, `SD`, `AO`, `MZ`. A device locale set to `en_NG` returns `euConfig()`, which carries:

- `regulatoryBody = "EMA"` — the European Medicines Agency has no jurisdiction in Nigeria; the Nigerian owner's prescriber would reference NAFDAC.
- `alertDisclaimer = "Bios is not a CE-marked medical device..."` — CE marking is irrelevant to the Nigerian regulatory landscape.
- `fhirProfileUrl = null` — fine for the EU fallback but not useful in countries where the local FHIR profile (e.g. South Africa's HL7 SA work) is the right reference.
- `hypertensionSystolic = 140` — `ESC/ESH 2023` thresholds. South Africa's Hypertension Society uses 140/90 for office BP, aligning broadly with ESC; Nigeria's hypertension guidelines (Ogah et al.) also use 140/90; but the *reference* is wrong-sourced.
- `glucoseInMmol = true` — mostly correct across the continent, but South Africa uses mmol/L while Nigeria reports both mg/dL and mmol/L in different settings.

**Recommendation:** at minimum, add `ZA` (South Africa — SAHPRA), `NG` (Nigeria — NAFDAC), `KE` (Kenya — PPB), `ET` (Ethiopia — EFDA), `GH` (Ghana — FDA), `SN` (Senegal — DPM), `MA` (Morocco — DMP), `EG` (Egypt — EDA). Each carries its local regulatory disclaimer text and its local guideline reference for the hypertension and diabetes thresholds. Per-country mapping is loose enough that an `AF_DEFAULT` (African Union / AMA fallback) for unmapped African country codes would be a defensible compromise — it would still be wrong to silently substitute EU.

Practitioners on the panel asked for this in unanimous agreement; it is the single change with the largest "I would actually use this" effect.

### 2.4 No surface for herbal-medication context

The medication-annotation surface read by [AnomalyDetector.kt:34, 376-378](../../android/app/src/main/java/com/bios/app/engine/AnomalyDetector.kt#L34) was added to suppress false-positive autonomic signatures when the owner is on a beta-blocker, calcium-channel blocker, stimulant, etc. The pattern explanation builder appends "Annotated current medications" so the alert text can read in light of pharmacology.

The same need exists for traditional pharmacopoeia. A non-exhaustive list of African medicinal plants with known measurable physiological effects that would *change how Bios should read an autonomic signature*:

- **Sutherlandia frutescens** (*unwele* / *kankerbos* / cancer-bush, Southern Africa) — adaptogenic; influences cortisol and HPA-axis markers; significant immunological literature.
- **Hypoxis hemerocallidea** (African potato) — sterol/sterolin content; widely used by people living with HIV; immunomodulatory.
- **Artemisia afra** (African wormwood) — antimalarial activity (distinct from *A. annua* but related); cardiovascular effects.
- **Catharanthus roseus** (Madagascar periwinkle) — source of vincristine and vinblastine; *not safe to take alongside chemotherapy without medical supervision*, but used traditionally for diabetes.
- **Pelargonium sidoides** (*umckaloabo*) — respiratory; standardised extract EPs7630 is registered as a medicine in Europe; significant cough literature.
- **Combretum micranthum** (*kinkéliba*, Senegal/Mali) — hepatoprotective claims; used for fever and digestive issues.
- **Moringa oleifera** — nutritional supplementation; affects glycemic response; multiple metabolic markers.
- **Hibiscus sabdariffa** (*bissap* / roselle / *karkadeh*) — significant antihypertensive literature; consumed widely as a beverage.
- **Aloe ferox / vera** — laxative; can cause electrolyte and renal effects at chronic dosing.
- **Cinchona-derived bark** (historically, throughout West Africa) — quinine; QT interval effects.
- **Khat** (*Catha edulis*, Horn of Africa) — sympathomimetic; will inflate RHR and reduce HRV.
- **Kola nut** (*Cola acuminata* / *nitida*, West Africa) — caffeine-containing; similar profile to coffee.
- **Rauvolfia vomitoria** (West Africa) — reserpine-related; antihypertensive and sedating; significant cardiovascular and CNS effects.

Bios has no first-class field for any of this. The owner could type these into a free-text medication note when that surface ships, but:

1. There is no ethnobotanical equivalent of RxNorm to standardise the names (Yoruba *ewe* names, Zulu *muthi* names, Amharic *medhanit* names, Wolof *garaab* names — none have a unified code-set, though Plant List / WFO / IPNI cover the botanical Latin).
2. There is no concept of *preparation form* — decoction (*agbo*, *atikilt*, *infusion*) vs. powder vs. poultice vs. tincture vs. burned-and-inhaled (*umsizi*).
3. There is no concept of *practitioner-prescribed-vs-self-administered* — a *babalawo*-prescribed *agbo* is a different clinical context from a self-purchased market-bought *agbo*, in the same way that a prescription drug is different from an OTC.

**Recommendation:**

1. Treat herbal medications as first-class entries in the medication-annotation surface — not "other / free-text" but structured: botanical Latin name (the one stable global identifier), vernacular name(s), preparation form, prescriber-or-self.
2. For the highest-prevalence and most-physiologically-active plants (the ~30–50 most-used across the continent), pre-populate a reference list with known cardiovascular / endocrine / respiratory / hepatic / renal effects so the alert-explanation builder can append "Annotated *Rauvolfia vomitoria* — known antihypertensive effect; observed BP lowering may reflect this medication."
3. *Critically*: this list is reference data only. Bios does not recommend or warn against any traditional medication; it reports observed effect direction alongside the annotation, in the same way it does for biomedical drugs. The manifesto principle holds.
4. Allow practitioners (the *babalawo*, *isangoma*, *medhanit awaki*, *tradipraticien*) to share an "annotation card" they can scan-or-type into the owner's app — same shape as the doctor-in-the-loop direction reversed.

The panel agreed this is the single most respectful addition Bios could make. It does not validate or invalidate traditional pharmacy; it lets the practitioner give Bios the context Bios needs to *not* misread an autonomic signature that has a known traditional-pharmacy cause.

### 2.5 No language localisation into any African language

[strings.xml](../../android/app/src/main/res/values/strings.xml) is 31 lines; there is no `values-sw/`, `values-ha/`, `values-yo/`, `values-zu/`, `values-xh/`, `values-am/`, `values-ar/`, `values-fr-rSN/`, `values-fr-rML/`, `values-pt-rAO/`, `values-pt-rMZ/`. The string surface today is small (app name, monthly-ask copy, a few permission descriptions) but the alert-text surface in [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) is *substantial* and currently hardcoded in English in `explanation`, `suggestedAction`, `earlyDetection`, `prevention`, `healing`, `risks` fields — six paragraphs per pattern, 33+ patterns. None of this is in `strings.xml`; none of it is localisable in the current architecture.

**Practitioner reading:**

- The *medhanit awaki*: "If I am to explain this to a patient in Addis, I need the text in Amharic. Translating live for every alert is not feasible."
- The *babalawo*: "Yoruba-language alerts would let me hand this to clients without educational gatekeeping. The English is exclusionary."
- The *tradipraticien* (Senegal): "Wolof for the consultation, French for the referral letter, English for the technical export — that is the realistic workflow. Bios can do the last two with effort and not the first at all."

**Recommendation:**

1. Refactor `ConditionPatterns.kt` text fields out of the data class and into `strings.xml` keys so they are localisable. This is a one-engineer week and unlocks every subsequent localisation.
2. Prioritise Swahili (East Africa, ~150M speakers), Hausa (West Africa, ~80M), Yoruba (~45M), Amharic (~32M, with the additional complexity of the Ge'ez script), Arabic (Maghreb + Sudan, already a high-priority global language), French (for Francophone West and Central Africa, already partially relevant for EU localisation work).
3. Zulu and isiXhosa for the South African market where the Traditional Health Practitioners Act 2007 formally recognises *amagqirha*, *izangoma*, *izinyanga* — and where the bilingual-clinician + traditional-practitioner workflow is most institutionally codified.

### 2.6 Pregnancy attended by traditional birth attendant — gating absent

The [PhysiologyState](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt) enum has the three pregnancy trimesters and postpartum, and the cardiovascular-stress pattern correctly excludes those states ([ConditionPatterns.kt:207](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L207)). What it does not have is any field for *who is attending* — biomedical antenatal care, a traditional birth attendant, a faith-based midwife in a religious-medicine tradition (still significant in parts of Ethiopia and Nigeria), or unattended.

WHO's posture has shifted over the last fifteen years from "phase out TBAs" to "integrate TBAs into the referral chain," recognising that in much of rural sub-Saharan Africa the TBA is the primary attendant for a substantial fraction of births. A pregnancy-monitoring instrument that assumes biomedical antenatal care is the only context will not be useful to either the TBA or the pregnant woman who consults one.

**Recommendation:**

1. An `attendantType` field on the pregnancy-state surface: `BIOMEDICAL_OB`, `MIDWIFE_CERTIFIED`, `TRADITIONAL_BIRTH_ATTENDANT`, `OTHER_TRADITIONAL_PRACTITIONER`, `UNATTENDED`, `NOT_DISCLOSED`. Owner-set, never inferred.
2. The TBA-attended case unlocks a "share with attendant" surface (§2.8) that is printable and does not assume FHIR fluency.
3. The pre-eclampsia screening pattern that the primary-care audit recommended (BP rise + proteinuria + symptoms) is *more* important in TBA-attended pregnancies than in biomedically-attended ones — because the TBA does not have the lab access to detect it through standard antenatal urinalysis. Bios's BP-trend reading is the most accessible early signal in that context.

### 2.7 No tropical-disease-aware fever logic

The `infection_onset` pattern reads skin-temperature deviation as a level (`SKIN_TEMPERATURE_DEVIATION`, `DeviationDirection.ABOVE`, `1.5σ`, 12 h minimum) at [ConditionPatterns.kt:136-137](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt#L136-L137). The *shape* of the fever curve over 3–7 days is discarded.

In malaria-endemic regions the fever-curve shape is itself diagnostic. *P. vivax* and *P. ovale* produce tertian (48 h) periodicity; *P. malariae* produces quartan (72 h). *P. falciparum* is sustained or quotidian and is the dangerous one; relapse is uncommon for *falciparum* but is the rule for *vivax*. Typhoid produces a step-ladder rise of 0.5–1°C per day over a week. Dengue produces a biphasic *saddleback* curve.

The same skin-temperature time-series Bios already records, fed through a simple periodogram or autocorrelation analysis over a rolling 7-day window, would expose these rhythms. The *interpretation* layer can be regional-config-gated (only surfaces malaria-period interpretations in malaria-endemic country codes).

**Recommendation:** add a `fever_pattern_recognition` analytical layer over the skin-temperature stream that classifies the rhythm (sustained, tertian, quartan, step-ladder, saddleback, intermittent-without-pattern) and feeds the classification into the regional disease-pattern overlay from §2.1.

### 2.8 No practitioner-in-the-loop, parallel to doctor-in-the-loop

The Bios "professional review" flow lets the owner share with a biomedical clinician via FHIR bundle, encrypted export, QR, verbal, telemedicine, or screenshot (six methods listed in the roadmap). For an owner whose primary practitioner is the *babalawo*, *isangoma*, *medhanit awaki*, *tradipraticien*, or bonesetter, FHIR is the wrong format, and the screenshot is a workable but unstructured fallback.

What the panel would want:

1. A "share with practitioner" surface that does not assume FHIR. A printable A4/A5 PDF summary in the device locale's language, showing: recent baseline excursions in plain language, the last 14 days of trend, any active condition patterns, the medication-annotation list (including herbal entries from §2.4), the physiology state.
2. The same surface in an orally-summarisable form — a one-screen "tell your practitioner" view that the owner can read aloud during a consultation, listing what Bios saw in the language Bios saw it (not the language of HL7).
3. For practitioners with mobile phones (which is most of them in the relevant regions), a one-direction QR receive surface — the practitioner does not need an app installation; they need the ability to scan a QR that gives them a read-only summary on a web view.
4. No backend dependency. All of this is local-device PDF rendering and QR generation, manifesto-aligned.

This is more about *naming the workflow* than about new infrastructure. The Bios doctor-in-the-loop machinery has most of the pieces; what is missing is the recognition that the recipient may not be a doctor.

### 2.9 `HealthEventType` enum presumes biomedical encounter chain

[Enums.kt:116-123](../../android/app/src/main/java/com/bios/app/model/Enums.kt#L116-L123):

```kotlin
enum class HealthEventType(val label: String) {
    SYMPTOM("Symptom"),
    HYPOTHESIS("Hypothesis"),
    DOCTOR_VISIT("Doctor Visit"),
    DIAGNOSIS("Diagnosis"),
    TREATMENT("Treatment"),
    NOTE("Note")
}
```

An owner who consults a *babalawo* on Monday and a clinic on Wednesday has two practitioner encounters with one event-type label that fits (`DOCTOR_VISIT`). The owner can use `NOTE` for the *babalawo* visit, but then the events are typed differently in a way that does not reflect the owner's experience of having had two practitioner consultations.

**Recommendation:** rename `DOCTOR_VISIT` to `PRACTITIONER_VISIT` and add an optional `practitionerType` field on the event payload — `MEDICAL_DOCTOR`, `NURSE`, `MIDWIFE`, `TRADITIONAL_PRACTITIONER`, `RELIGIOUS_HEALER`, `BONESETTER`, `BIRTH_ATTENDANT`, `OTHER`. This is a small, localisation-friendly change that acknowledges the plural practitioner landscape without making any claims about clinical equivalence.

The same logic extends to `DIAGNOSIS` and `TREATMENT`, where a *babalawo*'s reading of an Ifá-Odu chapter as the appropriate frame for the client's situation, and the *agbo* prescribed in response, are not the same kind of artefact as an ICD-10 code and a generic prescription. They are also not nothing. A `source` annotation on the diagnosis / treatment row (biomedical vs. traditional vs. self-research vs. other) lets the data structure carry the distinction without flattening it.

---

## 3. Manifesto / traditional-medicine tension points

These are *not* gaps. They are places where Bios's manifesto commitments and the traditions on the panel produce different answers, and the audit names the differences rather than smoothing them.

### 3.1 "The owner is final" vs. family-and-community decision frames

Manifesto Principle 7 and the framing throughout the documentation centres the *individual* owner as the decision-maker. This is correct for the contemporary biomedical / consumer-protection / GDPR-compatible posture Bios has chosen, and it is correct for the manifesto's autonomy commitment.

Several traditions on the panel operate in a frame where illness is *family-and-community-situated* rather than individual-located. In Zulu and Xhosa traditions, *ukuthwasa* (the calling to become a *sangoma*) and the diagnosis of *ufufunyana* or *amafufunyana* (spirit-possession-related distress) explicitly involve the family and the ancestors; the patient is not the sole decision-maker by tradition. In Yoruba tradition, the diagnostic encounter with the *babalawo* often involves family members and is structured around ancestral and lineage considerations. In Ethiopian Tewahedo-tradition healing, the family and the church community are often co-deciders. *Abiku* (Yoruba) and *ogbanje* (Igbo) — the syndrome of repeated infant death attributed to a spirit-child returning — is unambiguously a family-and-lineage frame, not an individual one.

This is not a flaw in Bios. It is a real difference. The audit's position:

- The data-sovereignty side of "the owner is final" is correct *everywhere* and the panel endorses it strongly — the biopiracy history makes the data-control principle non-negotiable.
- The decision-making side of "the owner is final" is correct *for biomedical decisions* and is the right posture for an app that may be used by an owner whose family is *not* aligned with their preferences (a teenager hiding contraception use from a hostile parent, a woman concealing pregnancy in a coercive context, etc.). This is also the post-Dobbs threat model.
- A *consent-to-share-with-family* surface, owner-controlled and revocable, would let the owner who *wants* family co-deciders to have them, without imposing them on the owner who does not. This already exists in spirit via the doctor-in-the-loop / encrypted-export flows; it just needs to be named in the language of family-and-community-decision rather than only in the language of clinical referral.

The panel agreed: do not retreat on the data-sovereignty principle. Do extend the sharing surfaces to recognise that "with my practitioner" and "with my family" are both legitimate owner-chosen sharing modes.

### 3.2 Divination is orthogonal to biomarker measurement; this is fine

A *babalawo*'s Ifá divination via *opele* chain or palm-nuts (*ikin*), an *isangoma*'s casting of *amathambo* (the bones), a sixteen-cowrie diviner's reading of *owo-mẹrindinlogun*, a *Vodun* practitioner's card-and-shell systems — these are *diagnostic procedures* in their traditions, and they produce diagnostic conclusions of the form "this is what the situation is, here is what the appropriate response is."

They are *not* less-precise versions of biomarker measurement. They do not measure HR, HRV, SpO2, temperature, or sleep. They answer different questions: *what is the meaning of what has happened to this person*, *what is the appropriate response*, *what does the configuration of forces around this person require*.

The panel is unanimous that Bios should not attempt to "integrate" divination — neither by claiming biomarker patterns can predict divinatory outcomes, nor by adding a "divination journal" surface that would trivialise the practice. Bios watches biomarkers; the diviner divines. Both can coexist in the owner's life, in the same way that a stethoscope and a psychiatric interview coexist in biomedical practice without one being reducible to the other.

What Bios *can* do is *not get in the way*:

- The alert text should not say "this is what is happening with you" with the implication that the biomarker reading is the whole truth. The push-side language in [AlertContentPolicy.kt](../../android/app/src/main/java/com/bios/app/alerts/AlertContentPolicy.kt) already sticks to factual data statements ("RHR +2σ for 48h") rather than interpretive ones, and this is exactly right. Preserve it deliberately.
- The `HypothesisType`-equivalent (the `HYPOTHESIS` event type in `HealthEventType`) and the `NOTE` event type already let the owner record the diviner's reading as a private annotation if the owner wants to. No code change needed; just acknowledgment that this is a legitimate use.
- The condition-pattern explanations should not be totalising. "Multiple vital signs are deviating from your personal baseline in a pattern consistent with the early stages of illness" is the right register; it makes a specific, falsifiable claim about biomarker data. It does not claim to be the only legitimate reading of the owner's condition.

### 3.3 Ancestral and spirit etiology — Bios's silence is correct

In many traditions on the panel, the etiology of an illness can include ancestral displeasure, spirit involvement, *umnyama* (spiritual heaviness / darkness), or the unmet calling of *ukuthwasa*. *Abiku* and *ogbanje* — the syndrome of repeated infant death attributed to a spirit-child — are recognised in DSM-5-TR's *Cultural Concepts of Distress* section specifically because biomedical practice has had to find a way to *not deny* the diagnostic validity of these frames within their communities of use.

Bios is silent on all of this. The panel's reading: this is the *correct* silence for an instrument whose epistemological scope is biomarker measurement. Bios is not a cosmological-diagnostic engine and should not pretend to be one. The instrument is the instrument; the practitioner is the practitioner; the owner moves between the two as their tradition and preference dictate.

What the panel asks for is *epistemological humility in the text*. The condition-pattern explanations should not foreclose other readings — they should report what Bios's biomarker observations show, and stop there. The current text largely does this; the manifesto principle that produces this restraint is Principle 6 ("science-grounded, never fear-driven") plus Principle 7 ("instrument, not coach"). Both are well-aligned.

### 3.4 Cultural concepts of distress in DSM-5-TR — Bios's mental-health-correlate is partial

The `mental_health_correlate` pattern in [ConditionPatterns.kt](../../android/app/src/main/java/com/bios/app/alerts/ConditionPatterns.kt) reads sleep + HRV + activity + typing cadence as a depression / anxiety proxy. This is reasonable in a biomedical-psychiatry frame.

In the traditions on the panel, the analogous syndromes are differently structured:

- ***Ufufunyane / amafufunyana*** (Nguni — Zulu, Xhosa, Swazi) — a spirit-possession-related affliction often manifesting as somatic, motor, and affective symptoms; treated by the *isangoma*. Documented in DSM-5-TR.
- ***Umnyama*** (Nguni) — spiritual darkness / heaviness; not always pathological but can become so.
- ***Abiku*** (Yoruba) and ***ogbanje*** (Igbo) — the spirit-child syndrome; a family-and-lineage frame for repeated infant death.
- ***Zar*** (Horn of Africa — Ethiopia, Sudan, Somalia) — spirit-possession syndrome, ceremonially addressed; documented in DSM-5-TR.
- ***Buda / evil-eye*** complexes — recognised across the Horn of Africa and the Sahel.
- ***Bouffée délirante*** — the term used in Francophone West African psychiatry for an acute polymorphic psychotic presentation; appears in DSM-5-TR.

Bios's `mental_health_correlate` will detect *some* of the somatic correlates of *some* of these — sleep disturbance, HRV depression — but the categorical *label* it produces ("mental health correlate") is biomedical, and the suggested-action text refers the owner to a "healthcare provider" rather than to the practitioner type their tradition would consult.

**Recommendation:** the audit does *not* recommend adding a "spirit-illness detector" — that would be inappropriate. It does recommend that the `mental_health_correlate` pattern's suggested-action text be reviewed for cultural concept of distress sensitivity in the language overlays (§2.5). The Swahili / Hausa / Yoruba / Zulu / Amharic overlays should not blanket-recommend "consult your healthcare provider" — they should recommend, in the locally appropriate register, "consult a practitioner you trust." This is a translation choice, not a code change.

---

## 4. Latent strengths the codebase has not yet named

These are places where Bios is *already aligned* with African traditional-medicine concerns, and where the audit recommends only that the alignment be named so it can be preserved.

### 4.1 Mobile-Android-first architecture matches African mobile-health context

Africa has world-leading mobile-money (M-PESA in Kenya, MTN MoMo across West and Central Africa, Wave in Senegal) and mobile-health (mTRAC in Uganda, MomConnect in South Africa, the WHO-AFRO mobile-health stack) adoption. The structural conditions: low-cost Android on a 2017-class chipset is the dominant computing surface; data is metered and intermittent; no Google Play Services on a growing fraction of devices; Bluetooth pairing with cheap peripheral devices works better than Wi-Fi cloud sync.

Bios's architecture — Android 9+, no Google Play Services, no cloud dependency, offline-first, SQLCipher local storage, low memory footprint, optional BLE peripheral integration — is *exactly* the architecture this context calls for. Most consumer health apps assume always-on cloud sync, recent flagship hardware, and Google ecosystem availability; Bios does not.

**The panel asks that this alignment be named in the project's positioning**, not because it requires code changes, but because it is one of the few consumer health products whose technical architecture is *structurally* appropriate for African deployment without retrofitting.

### 4.2 Local-first storage as anti-biopiracy posture

The history of *Hoodia gordonii* (San traditional appetite-suppressant, patented by CSIR and licensed to Pfizer/Unilever), *rooibos* (Aspalathus linearis, with prolonged trademark and benefit-sharing disputes), *devil's claw* (Harpagophytum, with similar histories), *Pelargonium sidoides* (umckaloabo, patent disputes 2010s), and *Sutherlandia frutescens* has produced a regulatory and political orientation across the continent where *who holds the data* is recognised as a question of who can monetise the medicine.

Bios's architecture — health data stays on the device by default, export is encrypted and explicit, community contributions are anonymised with differential privacy noise (`epsilon = 1.0` per the data-export documentation), there is no backend that sees raw readings — is structurally protective. It is also *not framed as such* in any of the project documentation. The panel recommends that the documentation acknowledge this alignment explicitly, because it is the kind of alignment that gets lost when documentation focuses only on individual-user privacy and not on collective-knowledge sovereignty.

### 4.3 The variolation precedent matters for vaccination-record framing

The primary-care audit (§2.3) flagged the absence of an immunisation domain as a preventive-medicine gap. The audit notes here that the *historical* alignment is real: variolation against smallpox was practised across the Sahel and into the Horn of Africa for centuries before Jenner's cowpox-vaccine work in 1796. Sudanese, West African, and Ethiopian variolation traditions are documented (Herbert; Hopkins, *Princes and Peasants*).

If and when Bios adds an `IMMUNIZATION` domain, the panel suggests:

- The domain should accept entries with non-standardised vaccine names (vernacular and local-trade-name) alongside the WHO standardised list, because vaccine availability and naming vary substantially across the continent.
- The framing should not be specific to a Northern-temperate childhood-immunisation schedule (which is the EU/US/UK/CA/AU/JP default in every existing region config). African EPI schedules differ — yellow fever is in the routine schedule across much of West Africa and is not in the EU/UK schedules.

### 4.4 Bonesetting and the post-reduction monitoring window

The Yoruba bonesetter tradition has been internationally studied since the 1980s — *BMJ*, *Injury*, *Journal of Orthopaedic Surgery and Research*, *African Health Sciences*, and elsewhere have documented outcomes; closed-reduction techniques are systematically taught; the limitation is well-documented (poor outcomes in displaced intra-articular fractures, neurovascular compromise, and compartment syndromes if not promptly referred).

Bios cannot help reduce a fracture. What it *could* do, in a way that respects the bonesetter's clinical scope:

- A post-reduction monitoring template — owner-set context: "fracture, reduced [date], affected limb [side]" — that watches for the warning signs the bonesetter is trained to refer on. Pain trajectory (the `PAIN_SCORE` field at [MetricType.kt:79](../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt#L79) is already in the schema), distal warmth proxy via the wearable temperature sensor, asymmetry in HR / activity, swelling-related sleep disruption.
- An `URGENT` escalation path for the post-reduction compartment-syndrome signature (rapidly escalating pain disproportionate to expected, distal-temperature divergence, gradual mobility loss) that prompts the owner to seek surgical evaluation.

The bonesetter on the panel: "The work I do is mine. What I want from a monitoring instrument is the same thing the surgeon wants — for the patient to come back if something is going wrong, not to wait until it is irreversible. If the app can help the patient notice 'something is going wrong' before they would notice on their own, it serves my work as well as the surgeon's."

This is a Tier-B-or-C recommendation; it is not the highest-priority work. But it is the *bonesetter-respectful* shape of the recommendation, and it illustrates the broader pattern: Bios's instrument-not-coach posture lets it serve practitioners across the panel without claiming to replace any of them.

---

## 5. Recommendations, tiered

**Tier A — owner safety in African deployment, ship before any further pattern expansion**

1. **SCD demographic gating** (§2.2). Add `SICKLE_CELL_TRAIT`, `SICKLE_CELL_DISEASE_SS`, `SICKLE_CELL_DISEASE_SC` to [PhysiologyState.kt](../../android/app/src/main/java/com/bios/app/physiology/PhysiologyState.kt); per-state SpO2 thresholds; add G6PD-deficient as a state. Highest single-change clinical-safety impact for the African owner cohort.
2. **African region configs** in [RegionConfigProvider.kt](../../android/app/src/main/java/com/bios/app/config/RegionConfigProvider.kt) — at minimum ZA, NG, KE, ET, GH, SN, MA, EG. Closes the silent-EU-fallback issue and gives owners region-appropriate disclaimer text.
3. **Vasoocclusive-crisis-signature pattern** with `severityFloor = AlertTier.URGENT` for SCD-state owners.

**Tier B — content completeness for the African disease landscape, next quarter**

4. **Tropical-disease-aware regional pattern overlay** (§2.1): malaria-onset, severe-malaria escalation (uses the existing `CONSCIOUSNESS_LEVEL` field), typhoid step-ladder, dengue saddleback. Regional-config-gated.
5. **Fever-curve shape recognition** (§2.7) as analytical layer over skin-temperature time-series.
6. **Herbal-medication annotation surface** (§2.4) as first-class extension to the medication-annotation surface, with reference data for the ~30–50 most-physiologically-active African medicinal plants.
7. **Language localisation pipeline** (§2.5): refactor `ConditionPatterns.kt` text fields to `strings.xml`; prioritise Swahili, Hausa, Yoruba, Amharic, Arabic, French overlays.

**Tier C — workflow and ecosystem fit, when the foundation is solid**

8. **Practitioner-in-the-loop sharing surface** (§2.8) parallel to doctor-in-the-loop — printable summary in device locale, oral-summary view, QR-receive-on-web for practitioners without app installations.
9. **`HealthEventType` rename to `PRACTITIONER_VISIT`** (§2.9) with `practitionerType` annotation. Small change, large naming clarity gain.
10. **TBA-attended-pregnancy gating** (§2.6) with `attendantType` field on pregnancy state.
11. **Family-or-community consent-to-share surface** (§3.1) — extending existing sharing flows to acknowledge that "with my family" is a legitimate owner-chosen sharing mode for the owners whose traditions situate decision-making collectively.
12. **Anaemia / haemoglobinopathy biomarker import** (haemoglobin, MCV, ferritin) into the existing BIOMARKER domain.
13. **Post-reduction monitoring template** for bonesetter-served owners (§4.4) — owner-set fracture-reduction context, distal-warmth and pain-trajectory watch, compartment-syndrome escalation.

**Do not adopt**

- A "divination-pattern detector" or any attempt to predict divinatory outcomes from biomarker data. This is a category error.
- A "spirit-illness detector" or biomarker-based attempt to identify *ufufunyane*, *abiku*, *ogbanje*, *zar*, or *umnyama*. These are not biomarker-visible by construction. Bios's silence on them is the correct silence.
- A push-side "you should consult a traditional practitioner" recommendation. The owner is final; the practitioner is the owner's choice; Bios reports observations and lets the owner decide who to consult about them.
- A "validate vs. invalidate" stance on any tradition. Bios is an instrument. Instruments do not adjudicate epistemologies. The instrument reports what it sees and stops.

---

## 6. Summary line for the project

> Bios is, structurally, one of the most African-traditional-medicine-compatible consumer health products in its size class — not by content but by posture. Local-first storage, no cloud dependency, low-spec Android, no premium tier, the manifesto's restraint against unsolicited evaluation, and the encrypted-export-on-owner-action sharing model are *exactly* the architectural commitments that African traditional-medicine regulatory frameworks and the African mobile-health context have been calling for. The content layer is implicitly Northern-temperate: no tropical-disease patterns, no SCD-aware physiology gating, no African region configs, no African-language overlays, no first-class surface for herbal-pharmacy annotation, no practitioner-in-the-loop sharing format that does not assume FHIR. None of the gaps require the manifesto to retreat. Several of the *non-gaps* — the orthogonality between biomarker observation and Ifá / *amathambo* divination, the silence on ancestral and spirit etiology — are correct silences for an instrument of biomarker measurement and should be preserved deliberately. The audit's single most important recommendation is the *combined* one: an African region-config overlay, SCD demographic gating, language localisation, and a first-class herbal-medication-annotation surface, shipped together as a "Bios on the African continent" capability that names the alignments that already exist and closes the content gaps that do not.
