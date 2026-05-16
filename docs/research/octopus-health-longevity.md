---
title: "Octopus Investigation — Health, Bio-hacking, Longevity"
date: 2026-05-16
origin: gedankenstuecke (Bastian Greshake Tzovaras, Open Humans / openSNP)
status: pass 2 — karlicoss + brainflow + NeuroTechX + awesome-qs crawl
serves: Bios, W2F, Virgil, SoulRadio, Smokeless
method: [docs/guides/octopus-investigation.md in World repo](https://github.com/mi4m/World/blob/main/docs/guides/octopus-investigation.md)
---

# Octopus Investigation — Health, Bio-hacking, Longevity

> Social-graph crawl of the open-source health/QS/longevity ecosystem, seeded from `gedankenstuecke`. Complements [health-prevention-apps-investigation.md](health-prevention-apps-investigation.md) (market intel) with practitioner/codebase intel.

---

## Meta-finding (pass 1)

**Longevity is under-represented on GitHub.** Bryan Johnson has 0 repos. "longevity" topic surfaces a handful of small projects (largest is `forever-healthy/AI4L` at 15★). The serious longevity dollars live in proprietary apps. By contrast, **quantified-self and citizen-science** ecosystems are deep and open. This is itself a Bios signal: the open longevity stack does not exist yet.

**Bio-hacking is huge but mis-labeled.** The `topic:biohacking` tag returns 62 small repos. The real density sits one layer down in **physiological signal processing** (`topic:eeg` = 1,787 repos; `topic:bci` = 540; `topic:hrv` = 161) and **neurotech libraries**. Bios's `CAMERA_PPG.md` direction has an entire mature library ecosystem to draw from.

---

## Origins

| # | Origin | Status | Why chosen |
|---|--------|--------|------------|
| 1 | [`gedankenstuecke`](https://github.com/gedankenstuecke) | Explored (pass 1) | Open Humans founder, 10+ years in personal-data-for-research, follows only 19 (high signal per edge) |
| 2 | [`karlicoss`](https://github.com/karlicoss) | **Explored (pass 2)** | Author of HPI (1.6k★) and promnesia (1.9k★). 263 repos, 84 follows, 1093 followers — central node of the personal-data ecosystem |
| 3 | [`brainflow-dev`](https://github.com/brainflow-dev) | Explored (pass 2, org) | BrainFlow 1684★ + BrainFlowAndroidTest exists — Android-capable biosignal lib |
| 4 | [`NeuroTechX`](https://github.com/NeuroTechX) | Explored (pass 2, org) | Awesome-bci 1469★, plus Android-relevant projects (`eeg-101`, `neurodoro`) |
| 5 | TBD (Android privacy-health) | **Partially filled** | `OpenTracks` (F-Droid privacy fitness) discovered via awesome-qs — crawl its maintainers next |
| 6 | TBD (longevity practitioner) | **Gap — to seed** | Longevity has weak GH presence; consider seeding from `docmanny` or accepting that the longevity community publishes elsewhere |

---

## Tentacles

### Tentacle A — Citizen-science personal data (Open Humans orbit)

Driving question: how do communities collect & share their own data for research?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`OpenHumans/open-humans`](https://github.com/OpenHumans/open-humans) (82★) | origin's affiliation | Powers openhumans.org platform | Data-export/import patterns for Bios CONSUMER_API |
| [`OpenHumans/Overland_android`](https://github.com/OpenHumans/Overland_android) (63★) | OH org | GPS logger Android app | Direct: GPS as a Bios signal |
| [`OpenHumans/quantified-flu`](https://github.com/OpenHumans/quantified-flu) (25★) | origin star | Wearable-based illness prediction citsci | Reference for detection module |
| [`oliviermirat/MyAIGuide`](https://github.com/oliviermirat/MyAIGuide) | origin star | Crowdsourced AI health-coach research | Coach-shaped layer above Bios |
| [`pleonova/data-diary`](https://github.com/pleonova/data-diary) (19★) | origin follow | Life dashboard auto-tracking common APIs | W2F-adjacent — review for ideas |

### Tentacle B — Quantified Self engines & aggregators

Driving question: who is already building the on-device data layer Bios needs?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`karlicoss/HPI`](https://github.com/karlicoss/HPI) (1,602★) | topic:quantified-self | **Human Programming Interface** — unified personal data access across services | **Highest** — Bios is the Android-native equivalent of HPI. Study schema & module pattern |
| [`ActivityWatch/activitywatch`](https://github.com/ActivityWatch/activitywatch) (17,557★) | topic:quantified-self | Privacy-focused cross-platform time tracker | W2F competitor/reference for on-device store + sync model |
| [`the-momentum/open-wearables`](https://github.com/the-momentum/open-wearables) (1,673★) | topic:quantified-self | Self-hosted unified wearable API | Direct Bios competitor — read their model |
| [`markwk/qs_ledger`](https://github.com/markwk/qs_ledger) (1,058★) | topic:quantified-self | Python QS data aggregator | Patterns for ETL |
| [`woop/awesome-quantified-self`](https://github.com/woop/awesome-quantified-self) (2,706★) | topic:quantified-self | Curated meta-list | **Meta-tentacle seed** — mine for more origins |

### Tentacle C — Diabetes / CGM / closed-loop (real biohacking community)

Driving question: who has shipped the most aggressive end-user health automation?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`nightscout/cgm-remote-monitor`](https://github.com/nightscout/cgm-remote-monitor) (2,752★) | org probe | Continuous glucose web monitor | Reference for real-time bio-data UX |
| [`nightscout/AndroidAPS`](https://github.com/nightscout/AndroidAPS) (1,069★) | org probe | Open-source automated insulin delivery | Closest analogue to a Bios "closed loop" |
| [`nightscout/Trio`](https://github.com/nightscout/Trio) (314★) | org probe | OpenAPS-based iOS automated delivery | Cross-platform reference |
| [`nightscout-connect`](https://github.com/nightscout/nightscout-connect) | org probe | Cloud-bridge for diabetes data | Anti-pattern reference (cloud) |

### Tentacle D — Bioinformatics / ontologies (research scaffolding)

Driving question: what schemas does the medical-research world use that Bios should align to?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`cmungall`](https://github.com/cmungall) (435 followers) | origin follow | Berkeley Lab; GO, Monarch, Alliance ontologies | Schema alignment for Bios 34-metric model |
| [`ekg`](https://github.com/ekg) (847 followers) | origin follow | UTHSC, pangenomics | Genomic-data tentacle anchor |
| [`philippbayer`](https://github.com/philippbayer) (217 followers) | origin follow | PYC, rare diseases + interpretable AI | Interpretable AI patterns for Bios ML layer |

### Tentacle E — DIY biology & wet-lab hardware

Driving question: who is making the bio-instrumentation cheap and self-buildable?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`DIYbiosphere/sphere`](https://github.com/DIYbiosphere/sphere) (110★) | topic:biohacking | DIYbio initiative directory | Discovery surface for more hardware tentacles |
| [`FourThievesVinegar/solderless-microlab`](https://github.com/FourThievesVinegar/solderless-microlab) (304★) | topic:biohacking | DIY pharma synthesis (active) — chemreaction control board | Edge — ethical posture / DIY medicine access ethos |
| [`FourThievesVinegar/askcos2_core`](https://github.com/FourThievesVinegar/askcos2_core) (51★) | org probe | Reverse-synthesis planning (Chemhacktica fork) | Same |
| [`idc-milab/openlh`](https://github.com/idc-milab/openlh) (23★) | topic:biohacking | DIY liquid handling (uARM-based) | Lab automation pattern |
| [`LinnesLab/LMP91000`](https://github.com/LinnesLab/LMP91000) (46★) | topic:biohacking | Arduino library for electrochemical sensing | Sensor frontend |
| [`reubn/ecg`](https://github.com/reubn/ecg) (26★) | topic:biohacking | Portable WiFi IoT ECG Monitor | DIY sensor — pair with Bios CAMERA_PPG |
| [`GGChe/ECG-Data-Acquisition-Board-AD8232`](https://github.com/GGChe/ECG-Data-Acquisition-Board-AD8232) (13★) | topic:biohacking | AD8232 ECG board | Same |
| [`bede/claranet4`](https://github.com/bede/claranet4) (30★) | origin star | Aranet4 CO2 sensor Python client | Environmental signal for Bios |
| [`flamebarke/biovault`](https://github.com/flamebarke/biovault) (14★) | topic:biohacking | AES-256 encrypted files on NFC implant (xSIID) | **Lethe-relevant** — implant as auth surface |

### Tentacle G — Neurosignal & biosignal processing (the big find)

Driving question: which mature libraries should Bios build on instead of reinvent?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`mne-tools/mne-python`](https://github.com/mne-tools/mne-python) (3,393★) | topic:eeg | Foundational MEG/EEG library | Schema reference for time-series biosignals |
| [`neuropsychology/NeuroKit`](https://github.com/neuropsychology/NeuroKit) (2,228★) | topic:hrv, topic:eeg | NeuroKit2: ECG/EDA/EEG/EMG/HRV/PPG signal processing toolbox | **Top adopt-or-port candidate** for Bios ml/ module |
| [`brainflow-dev/brainflow`](https://github.com/brainflow-dev/brainflow) (1,684★) | topic:bci, topic:eeg | EEG/EMG/ECG library, cross-platform incl. Android | **Direct Android relevance** — evaluate as Bios native lib |
| [`braindecode/braindecode`](https://github.com/braindecode/braindecode) (1,227★) | topic:eeg | Deep learning for EEG/ECG/MEG | ML layer reference |
| [`analyticalmonk/awesome-neuroscience`](https://github.com/analyticalmonk/awesome-neuroscience) (1,604★) | topic:eeg | Curated list | **Meta-tentacle seed** |
| [`NeuroTechX/moabb`](https://github.com/NeuroTechX/moabb) (987★) | topic:bci | Mother of All BCI Benchmarks | Eval methodology |
| [`pieeg-club/EEGwithRaspberryPI`](https://github.com/pieeg-club/EEGwithRaspberryPI) (936★) | topic:bci | DIY 8-ch EEG on Pi | DIY EEG path |
| [`pieeg-club/ironbci`](https://github.com/pieeg-club/ironbci) (598★) | topic:bci | Wearable BLE BCI w/ mobile SDK | Mobile BLE EEG — Bios-shaped |
| [`pyRiemann/pyRiemann`](https://github.com/pyRiemann/pyRiemann) (761★) | topic:eeg | Riemannian ML for multivariate biosignals | Advanced ML pattern |
| [`PGomes92/pyhrv`](https://github.com/PGomes92/pyhrv) (331★) | topic:hrv | HRV-specific toolbox | Port subset for Bios HRV |
| [`JanCBrammer/OpenHRV`](https://github.com/JanCBrammer/OpenHRV) (170★) | topic:hrv | HRV biofeedback with ECG chest strap | UX reference for biofeedback loop |
| [`obss/BIOBSS`](https://github.com/obss/BIOBSS) (144★) | topic:hrv | Wearable signal processing package | Direct wearable signal pipeline |
| [`partofthestars/PPGI-Toolbox`](https://github.com/partofthestars/PPGI-Toolbox) (87★) | topic:hrv | MATLAB PPG-imaging toolbox | **Direct: Bios CAMERA_PPG.md** — port algorithms |
| [`embodied-computation-group/systole`](https://github.com/embodied-computation-group/systole) (87★) | topic:hrv | Cardiac signal synchrony | Same |
| [`eegsynth/eegsynth`](https://github.com/eegsynth/eegsynth) (399★) | topic:bci | Convert real-time EEG into sounds, music, visuals | **🦞 SoulRadio-shaped — read first** |
| [`Elata-Biosciences/elata-bio-sdk`](https://github.com/Elata-Biosciences/elata-bio-sdk) (82★) | topic:biohacking | Cross-platform biosignal SDK + neurotech | Watch — Web3-flavored neurotech org |
| [`neurotech-berkeley/neurotech-course`](https://github.com/neurotech-berkeley/neurotech-course) (376★) | topic:neurofeedback | UC Berkeley intro to neurotech course | Curriculum entry point |
| [`neuromore/studio`](https://github.com/neuromore/studio) (146★) | topic:neurofeedback | All-in-one biofeedback suite | UX reference for Bios consumer surface |

### Tentacle H — Circadian, sleep & timing

Driving question: how is the timing/light/sleep axis being instrumented?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`claytonjn/hass-circadian_lighting`](https://github.com/claytonjn/hass-circadian_lighting) (883★) | topic:circadian | Home Assistant circadian light control | Environment/intervention side of Bios |
| [`ghammad/pyActigraphy`](https://github.com/ghammad/pyActigraphy) (165★) | topic:circadian | Actigraphy data analysis | Sleep/activity Bios module |
| [`Circadiaware/wearadian`](https://github.com/Circadiaware/wearadian) (17★) | topic:circadian | Wearable circadian rhythm telemonitoring | DIY sensor reference |
| [`Husseinfo/interfast`](https://github.com/Husseinfo/interfast) (39★) | topic:fasting | Intermittent Fasting Android app | Fasting tracker — Bios self-reported module |

### Tentacle J — Personal data export / interop protocols

Driving question: who else solved the "get my data out and into my own store" problem?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`Surfer-Org/Protocol`](https://github.com/Surfer-Org/Protocol) (1,474★) | karlicoss star | Open-source framework for exporting personal data | **Protocol-shaped — read before Bios CONSUMER_API freeze** |
| [`karlicoss/promnesia`](https://github.com/karlicoss/promnesia) (1,878★) | pass 2 origin | "Another piece of your extended mind" — browser context recall | Reference for cross-app context surfacing |
| [`karlicoss/orger`](https://github.com/karlicoss/orger) (338★) | pass 2 origin | Convert data into searchable interactive org-mode views | Output adapter pattern |
| [`karlicoss/grasp`](https://github.com/karlicoss/grasp) (381★) | pass 2 origin | Reliable org-capture browser extension | Capture-side reference |
| [`karlicoss/cachew`](https://github.com/karlicoss/cachew) (233★) | pass 2 origin | Transparent persistent cache via type hints | Eval for HPI port |
| [`karlicoss/exobrain`](https://github.com/karlicoss/exobrain) (89★) | pass 2 origin | His external brain (org-mode) | Mental model of full personal-infra stack |
| [`karlicoss/myinfra`](https://github.com/karlicoss/myinfra) (49★) | pass 2 origin | **Diagram of his personal infrastructure** | 🦞 Pattern to replicate: diagram Bios/W2F/Virgil/SoulRadio/Smokeless interactions |
| [`Own-Data-Privateer/hoardy-adb`](https://github.com/Own-Data-Privateer/hoardy-adb) (89★) | karlicoss star | Backup/restore Android devices, unpack Android Backup files | **Direct Android data-liberation tooling** |
| [`Own-Data-Privateer/hoardy-web`](https://github.com/Own-Data-Privateer/hoardy-web) (126★) | karlicoss star | Passively archive web browsing including content | Web-context capture reference |
| Many `karlicoss/*export` repos (pockexport, rexport, kobuddy, hypexport, ghexport, spotifyexport, fbmessengerexport) | pass 2 origin | Per-service personal data exporters | Pattern: one exporter per data source |

### Tentacle K — Extended mind / personal search & memory

Driving question: how does personal data become useful (searchable, recallable) rather than just stored?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`eagledot/hachi`](https://github.com/eagledot/hachi) (323★) | karlicoss star | Semantic + meta-data search engine for personal data | Direct: how Bios data becomes queryable |
| [`ErikBjare/quantifiedme`](https://github.com/ErikBjare/quantifiedme) (87★) | karlicoss star | "Analyzing all of my Quantified Self data" | **Worked example** — read end-to-end |
| [`janniks/awesome-personal-search-engines`](https://github.com/janniks/awesome-personal-search-engines) (21★) | karlicoss star | Meta-list | Meta-tentacle seed |
| [`orgzly-revived/orgzly-android-revived`](https://github.com/orgzly-revived/orgzly-android-revived) (1,054★) | karlicoss star | Android org-mode outliner | Android-side reference for note/data UX |

### Tentacle G+ — Neurosignal additions (pass 2)

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`brainflow-dev/BrainFlowAndroidTest`](https://github.com/brainflow-dev/BrainFlowAndroidTest) (10★) | org probe | Brainflow Android test harness | **Confirms Android viability** of brainflow for Bios |
| [`NeuroTechX/awesome-bci`](https://github.com/NeuroTechX/awesome-bci) (1,469★) | org probe | Curated BCI list | Meta-tentacle seed |
| [`NeuroTechX/eeg-101`](https://github.com/NeuroTechX/eeg-101) (265★) | org probe | **React Native + Muse EEG** tutorial app | Closest Android-native BCI pattern found |
| [`NeuroTechX/neurodoro`](https://github.com/NeuroTechX/neurodoro) (52★) | org probe | Brain-responsive pomodoro 🍅 | 🦞 W2F-shaped — brain-state-aware work timer |
| [`NeuroTechX/learn.neurotechedu.com`](https://github.com/NeuroTechX/learn.neurotechedu.com) (77★) | org probe | Open-source neurotech curriculum | Learning surface |
| [`NeuroTechX/Brainlock`](https://github.com/NeuroTechX/Brainlock) (7★) | org probe | EEG biometric authentication (N400) | **Lethe-relevant** — brain as auth |

### Tentacle L — Android privacy-respecting health (newly seeded)

Driving question: which Android-native, F-Droid-aligned health apps share Bios's privacy posture?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`OpenTracks`](https://github.com/OpenTracksApp/OpenTracks) | awesome-qs | Privacy-respecting offline-capable fitness activity tracker | **Direct ecosystem peer** — likely cross-pollination potential |
| [`NoTranslationLayer/biomarkerdash`](https://github.com/NoTranslationLayer/biomarkerdash) | awesome-qs | Simple dashboard for bloodwork biomarker trends | Bios self-reported labs surface |
| [`Tasker` (commercial)](https://tasker.joaoapps.com/) | awesome-qs | Android automation foundation | Integration target for Bios triggers |

### Tentacle I — Substance use & nootropic tracking (Smokeless-adjacent)

Driving question: who else builds substance-use ledgers — reduce-not-quit shaped?

| Discovery | Source | Notes | Smokeless relevance |
|-----------|--------|-------|----------------|
| [`aloth/mindful-coffee`](https://github.com/aloth/mindful-coffee) (6★) | topic:biohacking | iOS Caffeine tracker + brew journal | Closest shape to per-substance ledger |
| [`quantifiedbob/my-supplement-stack`](https://github.com/quantifiedbob/my-supplement-stack) (12★) | topic:longevity | Personal supplement stack | Self-reported intake pattern |
| [`corerat/Low-Dopamine-Meds`](https://github.com/corerat/Low-Dopamine-Meds) (1★) | topic:nootropics | Dopamine-reset resources | Conceptual neighbor |
| [`teoobarca/HealthOS`](https://github.com/teoobarca/HealthOS) (2★) | topic:biohacking | "AI-native health tracking, markdown-first, powered by Claude" | **Direct conceptual sibling** to Bios — watch |

### Tentacle F — Longevity (sparse but real)

Driving question: who is actually shipping longevity tooling rather than blogging?

| Discovery | Source | Notes | Bios relevance |
|-----------|--------|-------|----------------|
| [`albert-ying/longevity-os`](https://github.com/albert-ying/longevity-os) (14★) | topic:longevity | "Agentic Longevity OS with 10 AI physicians, N-of-1 trials" | **Conceptually closest to Bios direction** — read deeply |
| [`forever-healthy/AI4L`](https://github.com/forever-healthy/AI4L) (15★) | topic:longevity | AI for Practical Longevity, evidence-based | Watch their methodology |
| [`AkshajD/Epigenetic-Clock`](https://github.com/AkshajD/Epigenetic-Clock) (26★) | topic:longevity | Methylation analysis CpG sites | Bios genomics module reference |
| [`mpascariu/MortalityForecast`](https://github.com/mpascariu/MortalityForecast) (16★) | topic:longevity | R package, mortality forecasting | Methodology — long-horizon |
| [`quantifiedbob/my-supplement-stack`](https://github.com/quantifiedbob/my-supplement-stack) (12★) | topic:longevity | Personal supplement stack | Pattern: self-reported intake → Smokeless/Bios |
| [`docmanny`](https://github.com/docmanny) (29 followers) | user search | Penn State, evolution of longevity | **Origin candidate for next pass** |

---

## Queued leads (unexplored — next pass)

Sorted by expected signal.

Sorted by expected signal. ✓ = explored in pass 2.

1. ✓ **`karlicoss`** — explored pass 2
2. ✓ **`brainflow-dev`** — explored pass 2 (org)
3. ✓ **`NeuroTechX`** — explored pass 2 (org)
4. ✓ **`woop/awesome-quantified-self`** — mined pass 2
5. **`Surfer-Org/Protocol`** — read deeply; protocol-shaped data export framework
6. **`ErikBjare/quantifiedme`** — full worked example of personal QS analysis
7. **`Own-Data-Privateer`** — Hoardy projects; Android data liberation
8. **`OpenTracksApp/OpenTracks`** — F-Droid privacy fitness; gateway to F-Droid health ecosystem
9. **`gwern`** — Mentioned by awesome-qs; deep self-experiment practice
10. **`purarue`** — recurring in karlicoss network; likely HPI co-contributor
11. **`neuropsychology`** org — NeuroKit2 maintainers
12. **`eegsynth` contributors** — SoulRadio-shaped
13. **`analyticalmonk/awesome-neuroscience`** — meta-list mining
14. **`pieeg-club`** — DIY EEG hardware path
15. **`the-momentum`** org — open-wearables competitor
16. **`ActivityWatch`** org — contributor graph
17. **`OpenHumans`** org — full contributor list
18. **`Elata-Biosciences`** — neurotech + Web3 stack; watch only
19. **`teoobarca/HealthOS`** — Claude-powered concept-sibling
20. **`docmanny`** — only academic longevity profile
21. **F-Droid Health category** — needs scrape (not GH search)

---

## Gaps

| Gap | Why it matters | How to fill |
|-----|----------------|-------------|
| Longevity practitioners on GitHub | Bios is positioned around the longevity goal | Either accept that the longevity community publishes elsewhere (Substack, Twitter, papers) and crawl those instead, OR be early to the open-source longevity stack |
| Android-native privacy-health devs | Bios stack is Android-first | Crawl AndroidAPS contributors + F-Droid health category — neither surface well via GH search |
| Mental-health / psychometric tracking | Virgil/SoulRadio likely need this | Tentacle G to be opened — start from psychometric / EMA (ecological momentary assessment) repos |
| Substance-use harm reduction | Smokeless's actual domain | None found in pass 1 — needs targeted seed (e.g. drugsdata.org tooling, harm-reduction nonprofits) |

---

## Suggested next actions

**Reading queue (sorted by impact on Bios decisions):**

1. **`Surfer-Org/Protocol`** — read before freezing Bios CONSUMER_API.md; protocol-shaped framework that may obviate custom design
2. **`karlicoss/HPI`** — module schema; decide adopt-or-diverge for Bios DATA_MODEL.md
3. **`brainflow-dev/brainflow` + `BrainFlowAndroidTest`** — evaluate as Bios native biosignal lib (Android support confirmed)
4. **`partofthestars/PPGI-Toolbox`** — read alongside Bios CAMERA_PPG.md before writing more PPG code
5. **`NeuroKit2`** — port HRV/ECG/PPG subset into Bios ml/
6. **`ErikBjare/quantifiedme`** — full worked example of personal QS analysis end-to-end
7. **`eegsynth/eegsynth`** — architectural reference for SoulRadio
8. **`NeuroTechX/eeg-101`** + **`neurodoro`** — Android+EEG patterns; neurodoro is W2F-shaped
9. **`the-momentum/open-wearables`** — competitor arch (on-device vs server split)
10. **`karlicoss/myinfra`** — 🦞 model for diagramming Bios/W2F/Virgil/SoulRadio/Smokeless ecosystem

**Issues to file:**

- ~~**Bios:** "Evaluate Surfer-Org/Protocol for data-export interop before freezing CONSUMER_API"~~ — resolved (rejected)
- ~~**Bios:** "Evaluate HPI / open-wearables for schema alignment"~~ — resolved (see DATA_MODEL.md "Relation to HPI"); follow-ups below
- **Bios:** "Verify per-adapter failure isolation in IngestManager (HPI `@import_source` equivalent)"
- **Bios:** "Add explicit per-MetricType merge specification (HPI `all.py` equivalent) — make ConfidenceTier dedup debuggable"
- **Bios:** "Adopt directory-by-default for new ingest adapters (`ingest/oura/` not `OuraAdapter.kt`)"
- **Bios:** "Adopt or port NeuroKit2 HRV pipeline before writing custom code"
- **Bios:** "Audit AndroidAPS architecture for closed-loop UX patterns"
- ~~**Bios:** "Evaluate brainflow as native biosignal lib"~~ — resolved (TECH_STACK.md "Future: Raw Biosensor Boards")
- **SoulRadio:** "Read eegsynth/eegsynth — closest existing analogue to SoulRadio architecture"
- **W2F:** "Read NeuroTechX/neurodoro — brain-state-responsive pomodoro pattern"
- **Smokeless:** "Compare against mindful-coffee, quantifiedbob/supplement-stack for ledger patterns"
- **Ecosystem-level:** "Build myinfra-style diagram of Bios+W2F+Virgil+SoulRadio+Smokeless interactions"

**Pass 3 candidates:**

- `Surfer-Org/Protocol` (and contributors)
- `ErikBjare` (quantifiedme author, likely deep QS network)
- `OpenTracksApp` org (gateway to F-Droid health ecosystem)
- `gwern` (self-experiment culture node)

---

## Deep reads — decisions

Verdicts after reading READMEs + structure of the top three candidates from pass 2's reading queue.

### Surfer-Org/Protocol — **REJECT**

**What it actually is:** Electron desktop app + Python SDK that **scrapes UI** of platforms (opens Twitter/Notion/Gmail in an Electron window, waits for sign-in, scrapes the rendered DOM). Output is shape-less JSON blobs (`{"content": ["Post 1", "Post 2", ...]}`). No typed schema. Currently supports iMessages, Twitter Bookmarks, Notion, ChatGPT History, Gmail, LinkedIn. **MIT, last pushed 2024-12-25 (>1y stale).**

**What it isn't:** A protocol, despite the name. No Android. No biosignal model. No schema layer.

**Verdict:** Not a fit for Bios. Different stack, different problem, stale. The "build vs adopt" question for `CONSUMER_API.md` resolves to **build** — no convergence opportunity here. Cross it off the reading queue.

### karlicoss/HPI — **ADOPT THE PATTERN, NOT THE CODE**

**What it actually is:** Python package (`my`) that gives `import my.reddit.all`, `import my.health.sleep` etc. — one module per data source, each hiding "gory details of locating data, parsing, error handling and caching" and producing **typed Python objects**. Active (pushed 2026-05-10), MIT, topics: `data-liberation`, `extended-mind`, `lifelogging`, `personal-api`, `quantified-self`.

**What it isn't:** Portable to Android. Python-only.

**Verdict:** Bios already follows HPI's architecture (one ContentProvider, per-metric coverage registry, normalized 34-metric schema). HPI doesn't *change* the design — it **validates it**. Two concrete actions:
- Read [`HPI/doc/DESIGN.org`](https://github.com/karlicoss/HPI/blob/master/doc/DESIGN.org) and [`MODULE_DESIGN.org`](https://github.com/karlicoss/HPI/blob/master/doc/MODULE_DESIGN.org) for naming conventions / module boundary heuristics
- Future interop: a small HPI module (`my.bios`) that reads Bios's exported data on desktop → makes the Bios corpus queryable via HPI's full Python ecosystem. **Not now, but flag as a 6–12mo follow-up**

### brainflow — **EARMARK FOR EEG/BCI PHASE**

**What it actually is:** C/C++ core + bindings (Python, Java, R, C#, Matlab, Julia, Rust, Node). MIT, active (pushed 2026-05-14), 1684★, OpenBCI partnership. **Android support is real but partial:** runs in CI under Android NDK, separate [`BrainFlowAndroidTest`](https://github.com/brainflow-dev/BrainFlowAndroidTest) reference repo. Supports OpenBCI Ganglion/Cyton + WIFI shield + Synthetic Board on Android today.

**What it isn't:** Production-grade Android out of the box (author's own disclaimer on test repo). Not needed for camera-PPG or Health Connect signals.

**Verdict:** The right choice **when** Bios adds EEG/BCI ingestion (post v1). Don't write custom EEG handling. Likely path: Java binding → Kotlin import. Update `TECH_STACK.md`:

> Future biosignal acquisition (EEG/BCI): adopt brainflow Java binding rather than implement OpenBCI/Muse protocols natively. See BrainFlowAndroidTest for NDK setup.

---

## Net effect on Bios decisions

| Question raised in pass 1/2 | Resolution after deep reads |
|------------------------------|------------------------------|
| Should `CONSUMER_API.md` adopt Surfer-Org/Protocol's interop layer? | **No.** Surfer is desktop UI scraping. Build CONSUMER_API as planned |
| Should `DATA_MODEL.md` align to HPI conventions? | **Soft yes** — read HPI DESIGN.org for naming/module heuristics. No code dependency |
| Should Bios ml/ port NeuroKit2 HRV? | Still queued (HRV pipeline) — separate read |
| Should Bios native biosignal lib be custom or brainflow? | **brainflow when EEG/BCI lands.** Not needed for camera-PPG (current phase) |

---

## Log

- 2026-05-16: Pass 1 complete. Origin: gedankenstuecke. 6 tentacles (A–F), 28 discoveries.
- 2026-05-16: Bio-hacking deepening (Mía course-correction). Added Tentacles G (neurosignal/biosignal), H (circadian/sleep), I (substance-use ledger). Tentacle E expanded. **+34 discoveries** (now 62 total). Biggest find: `eegsynth` (SoulRadio-shaped) and `NeuroKit2` (port candidate for Bios ml/).
- 2026-05-16: Pass 2. Origins: karlicoss + brainflow-dev + NeuroTechX, plus mined awesome-quantified-self. Added Tentacles J (export protocols), K (personal search/memory), L (Android privacy-health). Tentacle G expanded. **+27 discoveries** (now 89 total). Biggest finds: `Surfer-Org/Protocol`, `karlicoss/promnesia`, `NeuroTechX/neurodoro`, `OpenTracks`.
- 2026-05-16: Deep reads pass on top 3 (Surfer/HPI/brainflow). Surfer rejected as not-a-fit. HPI validates current Bios architecture (pattern only, Python ≠ Kotlin). brainflow earmarked for future EEG/BCI phase.
- 2026-05-16: HPI design-docs audit (DESIGN.org + MODULE_DESIGN.org vs Bios DATA_MODEL.md). Findings codified into DATA_MODEL.md "Relation to HPI" section. Source-agnostic schema confirmed as intentional and correct divergence from HPI's source-typed approach. 3 concrete borrowings filed as issues: explicit per-MetricType merge spec, formalized per-adapter failure isolation, directory-by-default for new adapters.
