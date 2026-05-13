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

### 7.1 Three new MetricType keys for Virgil outbound

Reserve canonical keys for fall and check-in events so the cross-correlation
engine can use them like any other metric. Each carries timestamp + opaque
event-id only — no GPS, no SMS contents, no contact identity (see
[Virgil/docs/ECOSYSTEM.md](../../Virgil/docs/ECOSYSTEM.md)).

- `FALL_EVENT` — verified fall, dispatch fired. Clinical proxy for gait
  instability, syncope, orthostatic hypotension, neuropathy, MS relapse,
  medication side effects, alcohol.
- `NEAR_MISS_FALL` — fall detected but cancelled during countdown. Near-miss
  signal; rate change is the meaningful pattern.
- `CHECK_IN_MISS` — 5-minute grace expired with no response. Recurrent
  patterns are signals for cognitive decline, syncope, depression.

### 7.2 Extend companion-write URI [PARTIAL]

The `/companion/{metric_type}` URI now accepts the three mental-health keys
(W2F → `com.w2f.app`) and the two Smokeless intake keys (`tobacco_use`,
`tobacco_craving` → `com.smokless.smokeless`), gated per package by
`CompanionContract.WHITELIST_BY_PACKAGE`. Extend the whitelist to include the
three Virgil keys above when Virgil's `applicationId` is finalised. Document
the new metric_types in [CONSUMER_API.md](CONSUMER_API.md). Add contract
tests so a future companion can write each whitelisted key end-to-end.

### 7.3 Cross-correlation patterns over the new keys

Once data arrives, the condition engine should correlate:

- Fall frequency vs. resting HR, blood pressure (orthostatic hypotension)
- Fall frequency vs. blood glucose (hypoglycemia)
- Fall frequency vs. HRV trend (neurological deterioration)
- `CHECK_IN_MISS` rate vs. sleep latency, mood_drift_score (cognitive /
  depressive load)

Add one new ConditionPattern per cluster (literature-backed where research
supports — gait instability literature is rich) following the Phase 3.6
"never evaluate the person" content rule.

### 7.4 Extract `bios-contracts` artifact

Today W2F duplicates the `MetricType` enum and URI constants. With a second
real consumer (Virgil) the duplication cost exceeds the extraction cost.
Ship a thin AAR (or Maven-local) containing:

- `MetricType` enum
- `BiosHealthProvider` URI constants
- Intent action strings (e.g. for SoulRadio's `ACTION_SUGGEST_BAND`)
- Permission name constants

Companions consume the artifact; Bios publishes it on tag. Single source
of truth for the inter-app contract.

### 7.5 SoulRadio inbound suggest API (manifesto-bounded)

Wire the two intent actions reserved in
[SoulRadio/docs/ECOSYSTEM.md](../../SoulRadio/docs/ECOSYSTEM.md):
`ACTION_SUGGEST_BAND` (non-modal hint, 5-min hysteresis) and
`ACTION_REQUEST_STOP` (used by Virgil during alert, by W2F during SOS).
On the Bios side, opt-in emission from HRV/arousal classifications.

**Manifesto risk:** highest of any Phase 7 item. Defer until 7.1–7.3 land
and there's a clear, listener-facing reason to ship it.

### 7.6 Cross-repo CI / contract tests

Once `bios-contracts` exists (7.4), add a CI workflow that verifies
companion consumers compile against the latest artifact. Catches breakages
before they reach a release of either side.

### 7.7 Smokeless companion: substance-use signals

Smokeless ([thdelmas/Smokeless](https://github.com/thdelmas/Smokeless))
already captures two discrete event streams locally (`smoking_sessions`
and `cravings`, timestamp-only Room entities). Wire them into the metric
bus so the cross-correlation engine can use them like any other event.

Substance use is a high-yield Bios signal: tobacco affects RHR, HRV, SpO2,
sleep latency, and skin temperature — all metrics Bios already ingests
and baselines. Cravings (rate, time-of-day clustering, response to sleep
debt) carry independent predictive value for relapse risk.

**Initial reserved keys** (two pairs, matching Smokeless's current
single-substance scope plus its near-term cannabis expansion):

- `TOBACCO_USE` — discrete tobacco consumption event. Timestamp + opaque
  event-id. No dose, no brand, no method.
- `TOBACCO_CRAVING` — discrete craving event, same shape.
- `CANNABIS_USE` — reserved for when Smokeless ships multi-substance
  support (Smokeless [issue #3](https://github.com/thdelmas/Smokeless/issues/3)).
  Form (joint / vape / edible) is Smokeless-local; Bios sees the event only.
- `CANNABIS_CRAVING` — same shape.

**New `MetricDomain`: `INTAKE`.** None of the existing domains fit
substance-use events. `INTAKE` is the natural home and leaves room for
the W2F caffeine/meal signals to hoist up later if a second consumer
appears (per the ECOSYSTEM_BOUNDARIES.md "case study: nutrition in W2F"
rule). `MetricUnit.EVENT` (count-style, value always `1.0`) is the
canonical encoding — these are discrete events, not continuous readings.

**Companion-write URI extension.** Whitelist the four keys above in
`content://com.bios.app.health/companion/{metric_type}` alongside the
mental-health and Virgil keys from 7.1–7.2. Contract test:
Smokeless-shape insert end-to-end on each key.

**Cross-correlation patterns** (Phase 3.6 content-policy compliant —
data statements, never lifestyle judgments):

- Tobacco-use rate vs. RHR drift, HRV trend (cardiovascular load from
  active use; recovery signal during cessation)
- Craving rate vs. sleep efficiency, sleep latency (sleep debt is the
  best-documented craving amplifier — Jaehne 2012, Hamidovic 2009)
- Craving rate vs. HRV (autonomic stress correlate)
- Tobacco-use → SpO2 dip + skin-temp deviation pattern (already detected
  by existing condition patterns; tagging with the event aids causal
  attribution)

One new `ConditionPattern` proposed: **cessation recovery signal** —
during a sustained tobacco-use absence (>72h), surface the literature-
backed positive trajectory (RHR ↓, HRV ↑, SpO2 ↑ over 2–12 weeks; Benowitz
2009, Mahmud 2003). Information-only, no praise, no streaks — respects
the "silence is a feature" principle.

**Acceptance:** Smokeless writes `TOBACCO_USE` and `TOBACCO_CRAVING`
events to Bios via the companion URI. Bios surfaces them in diagnostics
the same way it surfaces other event streams. Cross-correlation pattern
runs alongside the existing patterns. No keys added beyond what Smokeless
actively emits today (YAGNI applies — cannabis keys reserved, not yet
whitelisted in the URI).

See also: [Smokeless/docs/ECOSYSTEM.md](../../Smokeless/docs/ECOSYSTEM.md).

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
