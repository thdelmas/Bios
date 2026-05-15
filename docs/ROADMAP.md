# Bios Roadmap: Protect the Owner

> Every item on this roadmap answers one question: **does this protect the person holding the device?**
>
> Bios is the health layer of LETHE. LETHE protects the owner's digital life; Bios protects the owner's physical life. This roadmap builds toward that integration, while keeping Bios functional as a standalone app on any Android 9+ device.

---

## Current State (v0.2.0)

**Core health pipeline:**
- Health data ingest: 10 adapters (Health Connect, Gadgetbridge, Direct Sensors, Oura, WHOOP, Garmin, Withings, Dexcom, Polar, Phone Sensors)
- SQLCipher encrypted local database (7 tables, AES-256, key in Android Keystore)
- Separate encrypted reproductive health database (independent key, independent wipe)
- 14-day rolling personal baselines per metric
- 12 condition patterns: infection onset, sleep disruption, cardiovascular stress, overtraining, metabolic drift, cardiorespiratory deconditioning, chronic inflammation, recovery deficit, respiratory infection, AFib screening, mental health correlate, menstrual cycle anomaly
- 33 signal rules (24 literature-backed with citations)
- LiteRT anomaly model wrapper (heuristic fallback active — model asset not shipped)
- 4-tier alert system with push notifications (Observation / Notice / Advisory / Urgent)
- Alert content policy enforcing "never evaluate the person" principle
- Biomarker reference knowledge base linking wearable metrics to clinical research

**Platform & protection:**
- Build flavors: `lethe` (embedded system app) and `standalone` (portable APK)
- Platform detection (LETHE vs stock Android) with capability-based feature gates
- LETHE wipe signal integration (burner mode, dead man's switch, panic, duress)
- Coercion-resistant safe mode (duress PIN → fresh-install appearance)
- Local health API for LETHE agent (localhost:8080/health/)
- OTA coordination (sleep/alert-aware reboot scheduling)
- Forensic risk monitoring with data footprint visibility
- Privacy dashboard (data audit, quick-wipe, reproductive data controls)
- No Google Play Services dependency

**Data & export:**
- Full data export (JSON + CSV ZIP) with AES-256-GCM encrypted export option
- FHIR R4 Bundle export with LOINC coding (12 mapped metric types)
- E2E encrypted multi-device sync protocol (HKDF-SHA256 key derivation, AES-256-GCM)
- Differential privacy aggregation (Community tier, Laplace noise, epsilon=1.0)

**Intelligence:**
- Federated learning framework (on-device gradient computation, DP noise, encrypted export)
- Model update manager (OTA via IPFS on LETHE, in-app on stock Android)
- Population health signal receiver (anonymous fetch, no location sent)
- Research pipeline (separate consent, full de-identification, k-anonymity)

**Backend (Go + PostgreSQL):**
- Sync gateway (E2E encrypted blob storage, zero-knowledge)
- Community contribution aggregation
- Population health signal distribution
- Research contribution storage
- Model version management
- Account deletion (immediate, irreversible)

**UI:** 13 Compose screens, onboarding, privacy dashboard, longevity reference view, diagnostics with condition details, professional review flow, pipeline health dashboard

**Decentralized architecture (LETHE IPFS stack):**
- Community contributions via IPFS PubSub (`bios-community`), population signals via IPNS (`bios-signals`), sync via content-addressed blobs, model updates via IPNS (`bios-models`) + Ed25519
- All traffic Tor-routed on LETHE; HTTP fallback on stock Android. Go backend optional.

**Localization & governance:**
- Config-driven localization: 6 regions (US, GB, EU, CA, AU, JP) with locale-aware units, clinical thresholds, regulatory disclaimers
- Async-first governance model (GOVERNANCE.md)
- Doctor-in-the-loop professional review: owner-controlled sharing via 6 methods (FHIR, encrypted export, QR, verbal, telemedicine, screenshot)
- Detection latency SLO tracking: 7 pipeline stages instrumented with p50/p90/p99 percentiles and violation logging

**P2P sync (Iroh/Willow):**
- Embedded Iroh node with Ed25519 identity, Willow protocol delta sync (encrypted per-entity)
- Ticket-based device pairing, mDNS/DNS-SD discovery + Iroh relay for NAT traversal
- Transport priority: Iroh P2P > IPFS (LETHE) > HTTP (fallback). Emergency wipe destroys all P2P state

**ML pipeline:** Trained anomaly detection model (`anomaly_detector.tflite`) shipped in app assets. Training pipeline in `ml/` (synthetic data generation + training script).

---

## Completed phases (1–6)

Phases 1–6 are all `[COMPLETE]` and have been moved to
[ROADMAP_HISTORY.md](ROADMAP_HISTORY.md) to keep this document focused on
what's next. The completed scope includes platform abstraction, LETHE
emergency wipe, encrypted exports, Guardian APIs, reproductive data
isolation, forensic awareness, the ML pipeline, federated learning, FHIR
export, decentralized sync (Iroh/IPFS), longevity baselines, doctor-in-
the-loop review, localization (6 regions), and detection latency SLOs.

---

## Phase 7: Companion Ecosystem — Open the metric bus to sibling apps [IN PROGRESS]

> Bios is the suite hub: W2F (mood/bipolar) already reads sensors and writes
> three MENTAL_HEALTH keys back; Virgil (solo-living safety) has discrete
> fall events to contribute; SoulRadio is a sibling (not a companion) and
> stays outside the bus. This phase formalizes the contract so any companion
> the owner explicitly approves can read and (narrowly) write.
>
> **Access model [SHIPPED]:** per-app keystores + `dangerous` OS permissions +
> per-package owner allowlist managed in Bios Settings → Companion Apps.
> First-connection notification deep-links into the consent UI. Per-package
> key isolation prevents cross-companion spoofing. See
> [CONSUMER_API.md](CONSUMER_API.md#access), `provider/CompanionGate.kt`,
> `provider/CompanionContract.kt`, and `ui/companions/CompanionsScreen.kt`.
> Third-party companions are explicitly supported; a shared signing key
> is **not** required.

### 7.1 Three new MetricType keys for Virgil outbound [COMPLETE]

`FALL_EVENT`, `NEAR_MISS_FALL`, and `CHECK_IN_MISS` live on the new
`MetricDomain.SAFETY` with `MetricUnit.EVENT` (timestamp + opaque event-id
only — no GPS, no SMS contents, no contact identity). The cross-correlation
engine can use them like any other metric. See `model/Enums.kt`.

Clinical context preserved here for future condition-pattern work (§7.3):
- `FALL_EVENT` — verified fall, dispatch fired. Clinical proxy for gait
  instability, syncope, orthostatic hypotension, neuropathy, MS relapse,
  medication side effects, alcohol.
- `NEAR_MISS_FALL` — fall detected but cancelled during countdown. Near-miss
  signal; rate change is the meaningful pattern.
- `CHECK_IN_MISS` — 5-minute grace expired with no response. Recurrent
  patterns are signals for cognitive decline, syncope, depression.

### 7.2 Extend companion-write URI [COMPLETE]

The `/companion/{metric_type}` URI accepts mental-health keys (W2F →
`com.w2f.app`), Smokeless intake keys (`com.smokless.smokeless`), and the
three Virgil safety keys (`com.virgil.app`), all gated per package by
`CompanionContract.PACKAGES`. Each companion is tagged with its own
`sourceId` for provenance. Documented in [CONSUMER_API.md](CONSUMER_API.md);
contract tests in `BiosHealthProviderContractTest` pin per-package isolation
and source attribution.

### 7.3 Cross-correlation patterns over the new keys [COMPLETE]

Six event-driven `ConditionPattern`s landed in `alerts/CompanionConditionPatterns.kt`
and are registered in `ConditionPatterns.all`. Each gates on a required EVENT-unit
rule so vital-sign drift alone never triggers a companion pattern; every supporting
rule is literature- or companion-sourced.

- `fall_orthostatic_pattern` — FALL_EVENT + low systolic/diastolic BP (Rutan 1992,
  Ricci 2015).
- `fall_neurological_pattern` — FALL_EVENT + NEAR_MISS_FALL + depressed HRV
  (Sleimen-Malkoun 2014).
- `fall_hypoglycemia_pattern` — FALL_EVENT + low blood glucose (Yale & Begg 2002).
- `check_in_decline_pattern` — CHECK_IN_MISS + irregular sleep + elevated
  mood_drift_score + depressed HRV (Riemann 2020, Koch 2019).
- Plus the substance-use patterns from §7.7.

Content-policy compliance (`AlertContentPolicy.validateAll()`) is enforced as a
unit test so future edits cannot regress the manifesto.

### 7.4 Extract `bios-contracts` artifact [COMPLETE]

Shipped as the `:bios-contracts` Gradle module, published to mavenLocal as
`com.bios:bios-contracts:0.1.0` via `./gradlew :bios-contracts:publishToMavenLocal`.
Surface (single source of truth — renames here recompile every consumer):

- `MetricType` (+ `MetricUnit`, `MetricDomain`)
- `BiosHealthContract` — authority, URI paths, column-name arrays, `CompanionInsert` keys
- `BiosPermissions` — `READ_HEALTH`, `WRITE_COMPANION`
- `BiosIntentActions` — `ACTION_SUGGEST_BAND`, `ACTION_REQUEST_STOP` (reserved for §7.5)

App code now imports from `com.bios.contracts.*`; `BiosHealthProvider` reads
its authority + paths + column names from the contract. Consumer ProGuard
rules ship inside the AAR. Usage example in `android/bios-contracts/README.md`.
Contract test (`MetricTypeTest`) guards every key against accidental renames.

### 7.5 SoulRadio inbound suggest API (manifesto-bounded)

Wire the two intent actions reserved in
[SoulRadio/docs/ECOSYSTEM.md](../../SoulRadio/docs/ECOSYSTEM.md):
`ACTION_SUGGEST_BAND` (non-modal hint, 5-min hysteresis) and
`ACTION_REQUEST_STOP` (used by Virgil during alert, by W2F during SOS).
On the Bios side, opt-in emission from HRV/arousal classifications.

**Manifesto risk:** highest of any Phase 7 item. Defer until 7.1–7.3 land
and there's a clear, listener-facing reason to ship it.

### 7.6 Cross-repo CI / contract tests [COMPLETE]

`cross-repo-contracts.yml` publishes `bios-contracts` to a per-job
mavenLocal, then builds each public companion (Smokeless, SoulRadio,
Virgil, Fil) against it through `.github/scripts/consumer-init.gradle`.
Runs on every change to `android/bios-contracts/**` or the workflow
itself, plus manual dispatch. Until a companion adds
`implementation("com.bios:bios-contracts:0.1.0")`, this also serves as a
companion build-health probe; the day a consumer adopts the artifact, the
workflow becomes a real contract gate without further setup.

### 7.7 Smokeless companion: substance-use signals [BIOS COMPLETE — awaiting Smokeless adoption]

Smokeless ([thdelmas/Smokeless](https://github.com/thdelmas/Smokeless))
already captures two discrete event streams locally (`smoking_sessions`
and `cravings`, timestamp-only Room entities). Wire them into the metric
bus so the cross-correlation engine can use them like any other event.

Substance use is a high-yield Bios signal: tobacco affects RHR, HRV, SpO2,
sleep latency, and skin temperature — all metrics Bios already ingests
and baselines. Cravings (rate, time-of-day clustering, response to sleep
debt) carry independent predictive value for relapse risk.

**Initial reserved keys [COMPLETE]** — landed in `bios-contracts`
`MetricType`:

- `TOBACCO_USE` — discrete tobacco consumption event. Timestamp + opaque
  event-id. No dose, no brand, no method.
- `TOBACCO_CRAVING` — discrete craving event, same shape.
- `CANNABIS_USE` — reserved for when Smokeless ships multi-substance
  support (Smokeless [issue #3](https://github.com/thdelmas/Smokeless/issues/3)).
  Form (joint / vape / edible) is Smokeless-local; Bios sees the event only.
- `CANNABIS_CRAVING` — same shape.

**New `MetricDomain`: `INTAKE` [COMPLETE]** — added to `bios-contracts`
`MetricDomain`. Leaves room for the W2F caffeine/meal signals to hoist up
later if a second consumer appears (per the ECOSYSTEM_BOUNDARIES.md "case
study: nutrition in W2F" rule). `MetricUnit.EVENT` (count-style, value
always `1.0`) is the canonical encoding — these are discrete events, not
continuous readings.

**Companion-write URI extension [COMPLETE]** — the four keys are
whitelisted for `com.smokless.smokeless` in
`provider/CompanionContract.kt` alongside the mental-health and Virgil
keys from 7.1–7.2; Smokeless is tagged `SELF_REPORTED` for provenance.
`BiosHealthProviderContractTest` pins writability, cross-package
isolation, and source attribution for every key.

**Cross-correlation patterns [COMPLETE]** — landed in
`alerts/CompanionConditionPatterns.kt` alongside the §7.3 patterns:

- `substance_use_cv_load_pattern` — TOBACCO_USE + RHR ↑ / HRV ↓ / SpO2 ↓
  (Benowitz 2009, Mahmud 2003, Macnee 2005).
- `craving_sleep_debt_pattern` — TOBACCO_CRAVING + short sleep + low sleep
  efficiency + depressed HRV (Jaehne 2012, Hamidovic 2009, Eddie 2015).
- `cessation_recovery_pattern` — gated on `DeviationDirection.ABSENT` of
  TOBACCO_USE for ≥72h, surfaces the literature-backed positive
  trajectory (RHR ↓, HRV ↑, SpO2 ↑). Information-only, no praise, no
  streaks. Content-policy enforced by unit test.

**Remaining work is on the Smokeless side:** call the companion URI for
each `Substance`-routed session/craving event. The Bios-side surface is
frozen via `bios-contracts` 0.1.0 and the cross-repo CI workflow from §7.6
will catch any drift the day Smokeless adopts `bios-contracts`.

**Acceptance:** Smokeless writes `TOBACCO_USE` / `TOBACCO_CRAVING` and
(as of its Phase 2.1) `CANNABIS_USE` / `CANNABIS_CRAVING` events to Bios
via the companion URI, routed per the `Substance` persisted on each
session. Bios surfaces them in diagnostics the same way it surfaces other
event streams. Cross-correlation pattern runs alongside the existing
patterns.

See also: [Smokeless/docs/ECOSYSTEM.md](../../Smokeless/docs/ECOSYSTEM.md).

---

## Phase 8: Perimeter completion — close the bio-hacking coverage gaps [PLANNED]

> A gap audit across the five-app suite (Bios, W2F, Smokeless, Virgil,
> SoulRadio) surfaced canonical signals that the schema either reserves
> but doesn't implement, or doesn't cover at all. Phase 8 ships the
> *Bios-owned* additions — passive sensors, derivations, and multi-system
> body signals that pass question 1 of the
> [ECOSYSTEM_BOUNDARIES.md](ECOSYSTEM_BOUNDARIES.md) three-question test.
> Companion-owned gaps (substance ledger expansion, Virgil wire-up) are
> tracked in the paired companion ROADMAPs.

### 8.1 Ambient light writer (Phone Sensors adapter)

Cheapest-win in the audit. Phone's ambient-light sensor is already a
declared adapter source in [ARCHITECTURE.md](ARCHITECTURE.md); the
`ambient_light` key is in the planned-but-unimplemented set. Wire the
adapter to emit lux samples on a duty cycle that doesn't burn battery
(default: 1 sample / 5 min during waking hours, gated by screen-on).

Unlocks circadian-alignment baselines, which pair with W2F's
`circadian_phase_shift` and SoulRadio's 24-hour loop without violating
SoulRadio's manifesto (Bios derives, SoulRadio does not consume).

### 8.2 Body composition via Withings adapter

Withings is already an active adapter ([§Current State](#current-state-v020)),
but `body_mass` and `body_fat_pct` are not yet in `MetricType`. Add the
keys (domain: `METABOLIC` or new `BODY_COMPOSITION`), wire the Withings
adapter to write them, and baseline like any other metric.

### 8.3 HRV decomposition (autonomic-tone derivations) [PARTIAL — parasympathetic shipped, LF/HF deferred]

Raw HRV is canonical but the clinically useful numbers (LF/HF ratio,
parasympathetic tone) aren't. Resolves the "raw HRV present, no
autonomic surface" finding from the audit.

- `parasympathetic_tone` (CARDIOVASCULAR, SCORE) — **shipped.** Computed
  as `ln(RMSSD)` in `HrvAnalyzer.HrvResult.lnRmssd` and emitted by both
  PPG and direct-sensor adapters alongside `HEART_RATE_VARIABILITY`.
  `ln(RMSSD)` is the standard time-domain proxy for HF spectral power
  (Shaffer & Ginsberg 2017, Kleiger 2005) — correlates ~0.9 with `ln(HF)`
  in healthy adults and is approximately normally distributed across
  individuals.
- `lf_hf_ratio` (CARDIOVASCULAR) — **deferred.** Requires real spectral
  analysis (FFT or Lomb-Scargle over the IBI tachogram); no time-domain
  proxy is clinically defensible. Worth its own PR with a vetted FFT
  implementation and synthetic-signal test coverage.

### 8.4 Sleep derivations: latency + components [LATENCY + COMPONENTS SHIPPED]

Derive from the existing `sleep_stage` time-series in `engine/SleepDerivations.kt`:

- `sleep_latency` (SECONDS) — first AWAKE → first non-AWAKE.
- `sleep_efficiency` (PERCENT) — TST / TIB × 100; the canonical clinical
  formula. Threshold ≥85% is the well-established normal range.
- `sleep_fragmentation_index` (COUNT) — number of post-onset awakenings;
  contiguous AWAKE blocks count as one.

**Reframe from `sleep_score`:** the original 8.4 specified a composite
score. Shipping a 0–100 grade on the owner's sleep clashes with the
"never evaluate the person" principle — even with literature-anchored
thresholds, a single number invites comparison and reads as judgment.
The decomposed components above are clinically richer (each is its own
literature-backed signal) and stay within the manifesto. `sleep_score`
is parked unless a future condition pattern needs a single-number input
that can't be expressed as a rule over the components.

### 8.5 Cycle inference from BBT [PARTIAL — cycle_phase shipped, cycle_day deferred]

`basal_body_temperature` is in the schema with no consumer; `cycle_day`
and `cycle_phase` are planned-but-unimplemented. Cycle-phase inference
ships now; persistence routing and `cycle_day` follow when their
prerequisites land.

- `cycle_phase` (WOMENS_HEALTH, CATEGORY) — **shipped.** Implemented as
  the classic biphasic 3-day rule (Marshall 1968; Barron & Fehring 2005)
  in `engine/CycleInference.kt`: follicular baseline = mean of the first
  six daily BBTs, coverline = baseline + 0.1°C, ovulation = the day
  before three consecutive daily BBTs sit strictly above the coverline.
  Pure function over `MetricReading` rows — unopinionated about
  persistence, so the caller routes the derived readings to the encrypted
  reproductive-health DB once reproductive-data consent is on. Emits
  FOLLICULAR / OVULATORY / LUTEAL via the `CyclePhase` enum.
- `cycle_day` — **deferred.** The clinical numbering convention starts
  cycle day 1 at the first day of menstruation; BBT alone cannot
  reliably distinguish menstrual from early follicular, so we'd have to
  fabricate an origin or invent a non-standard numbering. Ships when a
  menstruation-onset signal exists (manual log, or a wearable that
  reports it).
- `MENSTRUAL` is reserved on the `CyclePhase` enum for the same reason.

A BBT writer is still missing (no current adapter emits
`basal_body_temperature`); the inference is dormant until a writer
appears, mirroring how `SleepDerivations` waited for SLEEP_STAGE writers
to come online.

### 8.6 Lab / biomarker inbound surface [FOUNDATION SHIPPED]

FHIR R4 export is shipped ([§Current State](#current-state-v020)); FHIR
*import* is the symmetric add. Accept lab results (CBC, lipid, ApoB,
hsCRP, HbA1c, vitamin D, thyroid, etc.) via FHIR file picker. Manual
structured entry as a second path. Store as time-series in a new
`BIOMARKER` domain. Reference ranges are localization-aware (clinical
thresholds already region-config'd per the localization layer).

No new companion required — the data is canonical multi-system body
signal, and the import surface is a thin settings flow on top of the
existing FHIR machinery.

**16 biomarker keys shipped** across four waves in
`bios-contracts/MetricType` (`MetricDomain.BIOMARKER`), each with its
canonical LOINC + UCUM mapping wired into `export/FhirExporter.kt`:

| Wave | Keys | Units added |
|------|------|-------------|
| Foundation | `HBA1C` (4548-4), `HSCRP` (30522-7) | `MG_PER_L` |
| Lipid + ApoB | `TOTAL_CHOLESTEROL` (2093-3), `LDL_CHOLESTEROL` (2089-1), `HDL_CHOLESTEROL` (2085-9), `TRIGLYCERIDES` (2571-8), `APO_B` (1884-6) | — (reuses `MG_PER_DL`) |
| Vit D + thyroid | `VITAMIN_D_25OH` (14635-7), `TSH` (3016-3), `FREE_T4` (3024-7), `FREE_T3` (3051-0) | `NG_PER_ML`, `NG_PER_DL`, `PG_PER_ML`, `MIU_PER_L` |
| CBC panel | `HEMOGLOBIN` (718-7), `HEMATOCRIT` (4544-3), `WBC` (6690-2), `RBC` (789-8), `PLATELETS` (777-3) | `G_PER_DL`, `GIGA_PER_L` (UCUM `10*9/L`), `TERA_PER_L` (UCUM `10*12/L`) |

Each thyroid assay keeps its own clinically-conventional unit (TSH
`mIU/L`, free T4 `ng/dL`, free T3 `pg/mL`); collapsing onto one unit
would force misleading conversions across assay-specific reference
ranges. SI ↔ US conversions are a localization concern owned by
`RegionConfigProvider`, not contract changes.

**Write path (shipped):** `BiomarkerEntryRepo` (`data/`) wraps a single
`SELF_REPORTED` `DataSource` (new `SourceType.SELF_REPORTED`,
`ReadingKind.SELF_REPORTED`). `BaselineEngine` already filters
`kind != SENSOR` per decision 3 in `docs/SELF_REPORTED_DATA_HOME.md`,
so lab values show up in trends and FHIR export but never corrupt
sensor-derived baselines.

**Manual entry (shipped):** `BiomarkerEntryScreen` (`ui/biomarkers/`)
reachable from Settings → "Add Lab Values". Picker over the 16
biomarkers, numeric value with the per-key unit suffix, Material3
date picker, recent-entries list.

**FHIR Observation import (shipped):** `FhirImporter` (`export/`)
reads a FHIR R4 JSON file (Bundle or single Observation), extracts
LOINC + `valueQuantity` + date (with `issued` / `effectivePeriod.start`
fallbacks), and routes accepted readings through the same
`BiomarkerEntryRepo` write path. The LOINC → MetricType reverse map is
built by scanning `MetricType.entries` through `loincCode()`, so
export and import sides never drift. Skip-and-report semantics: every
rejection (no LOINC coding, unmapped code, non-biomarker mapping,
missing `valueQuantity`, unparseable date) is captured in
`FhirImportSummary.skipped` with a reason and the offending LOINC.
UI: "Import from FHIR" card on the entry screen with a SAF file
picker + summary dialog.

**Remaining work (separate PRs):**

- Region-aware reference-range expansion in `RegionConfigProvider`'s
  `ClinicalThresholds` (the last piece of §8.6).

### 8.7 Stress score — Bios-only autonomic derivation [SHIPPED]

`stress_score` (CARDIOVASCULAR, SCORE) is computed as Baevsky's Stress
Index (SI, "Index of Tension") over the cleaned RR tachogram:
`SI = AMo / (2 × Mo × MxDMn)` on 50ms bins, computed in
`HrvAnalyzer.HrvResult.stressIndex` and emitted by the PPG and
direct-sensor adapters alongside `HEART_RATE_VARIABILITY` and
`PARASYMPATHETIC_TONE`. Baevsky SI rises with sympathetic activation /
parasympathetic withdrawal; typical rest range ~50–150, with >200
indicating elevated sympathetic tone (Baevsky & Berseneva 2008). Zero
in the degenerate constant-IBI case.

Audit ownership debate resolved: `stress_score` is a *passive autonomic
derivation* over HRV (RHR was considered; the chosen Baevsky formula
uses the tachogram alone, so no baseline coupling is needed). Bios
derives it; W2F consumes if it wants, no W2F-side write. Avoids overlap
with `mood_drift_score` and keeps the boundary clean (W2F owns mood;
Bios owns autonomic state).

### 8.8 Acceptance for Phase 8

- All eight new/derived keys live in `MetricType` with literature
  citations in the rule files.
- Each has at least one cross-correlation use (a `ConditionPattern` that
  consumes it, or a baseline-engine derivation, or a documented
  companion read).
- No new companion shipped; no companion-write URI changes (these are
  all Bios-internal).
- Schema-waste audit re-run: `skin_temperature`, `respiratory_rate`,
  `basal_body_temperature` all have at least one writer or consumer.

---

## Phase 9 (deferred): New companions if earned

Two companions are noted in the audit but explicitly *not* committed:

- **Posology** — meds + supplement adherence with reminders. New keys
  `med_taken`, `supplement_taken` on `INTAKE`. Migrates W2F's Mg/B2
  prophylaxis out of FuelLog. Ship only if the longevity-stack daily
  adherence surface becomes load-bearing.
- **Journaling / reflection** — *out of scope* for the bio-hacking
  suite. miam-knowledge-base holds the reflective surface; the therapy
  register is a closed direction. Recorded here only to prevent
  re-litigation.

---

## Non-negotiable principles

1. **The owner is final.** Bios advises, never overrides. Every feature is off by default or requires explicit opt-in.
2. **Defense only.** Encryption, erasure, on-device processing. Never offense, never data monetization.
3. **Silence is a feature.** No engagement farming, no streaks, no gamification.
4. **Never evaluate the person.** Report deviations from baseline, never lifestyle judgments.
5. **Erasure by design.** Every data store destroyable in < 1 second via key destruction.
6. **No Play Services.** Every feature works on degoogled devices.
7. **Portable.** Single codebase, two flavors. LETHE gets deeper protection; stock Android gets the same intelligence.
8. **Auditable.** The owner can inspect what data exists, where it lives, and what has been transmitted.
