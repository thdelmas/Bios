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

**UI:** 11 Compose screens, onboarding, privacy dashboard, longevity reference view, diagnostics with condition details

**Remaining work:**
- Ship trained TFLite anomaly model (requires offline ML training with medical datasets)
- Backend deployment and infrastructure

---

## Phase 1: Foundation — Make Bios protectable [COMPLETE]

> Give LETHE the hooks to include Bios in emergency protocols, and give Bios enough data sources to function on degoogled devices.

### 1.1 Platform abstraction layer

Create the `platform/` package that lets Bios detect its environment and behave accordingly.

```
platform/
  PlatformDetector.kt        -- detect LETHE vs stock Android at runtime
  PlatformCapabilities.kt    -- what this platform supports (agent IPC, direct sensors, wipe signals)
  LetheCompat.kt             -- interface: agent API, wipe hooks, launcher cards, OTA coordination
  LetheCompatNoop.kt         -- stock Android: all methods return no-ops
  KeystoreProvider.kt        -- LETHE hardware keystore vs Android Keystore
  BackgroundScheduler.kt     -- LETHE job scheduler vs WorkManager
```

**Runtime detection:** Check for LETHE system property (`ro.lethe.version`) or signature. Fall back to stock behavior if absent.

**Build flavors:** Add `lethe` and `standalone` flavors to `build.gradle.kts`. The `lethe` flavor includes LETHE-specific implementations; `standalone` includes no-ops. Both share all other code.

**Acceptance:** App compiles and runs identically on stock Android. `PlatformDetector.isLethe()` returns true on LETHE builds.

### 1.2 LETHE emergency wipe integration

Register a BroadcastReceiver that listens for LETHE wipe signals:
- Burner mode boot wipe (`lethe.intent.action.BURNER_WIPE`)
- Dead man's switch escalation (`lethe.intent.action.DMS_WIPE`)
- Panic button (`lethe.intent.action.PANIC_WIPE`)
- Duress PIN activation (`lethe.intent.action.DURESS_WIPE`)

On receiving any wipe signal:
1. Destroy the SQLCipher encryption key from keystore
2. Delete the database file
3. Wipe Oura OAuth tokens from EncryptedSharedPreferences
4. Wipe any plaintext export files in app storage
5. Cancel all pending WorkManager jobs
6. Clear all notifications

**Standalone behavior:** Receiver not registered. "Delete all data" in Settings remains the manual path.

**Acceptance:** Simulated wipe broadcast → all health data irrecoverable within 1 second. Key destruction verified. No stale tokens. No export files.

### 1.3 Direct sensor + Gadgetbridge fallback

On LETHE and other degoogled devices, Health Connect may not be available. Bios needs alternative data paths.

**Gadgetbridge integration:**
- Gadgetbridge is the open-source wearable companion app for degoogled Android (supports 40+ devices: Mi Band, PineTime, Amazfit, Fossil, Casio, etc.)
- Read exported data from Gadgetbridge's local database or ContentProvider
- Map Gadgetbridge metrics to Bios unified schema
- New adapter: `ingest/GadgetbridgeAdapter.kt`
- New source type: `GADGETBRIDGE` in `SourceType` enum

**Direct Android sensor APIs (no Health Connect):**
- Expand `PhoneSensorAdapter` to read from `SensorManager` directly:
  - `TYPE_HEART_RATE` (if available on device)
  - `TYPE_HEART_BEAT` (inter-beat interval → HRV)
  - `TYPE_STEP_COUNTER` (already implemented)
  - `TYPE_ACCELEROMETER` (already implemented)
- Wear OS Companion: If a Wear OS watch is paired, read sensor data via `DataClient` API without Health Connect

**Adapter selection logic in `IngestManager`:**
1. Health Connect available → use it (preferred)
2. Gadgetbridge installed → use it
3. Neither → fall back to phone sensors + any connected API adapters (Oura)

**Acceptance:** On a LETHE device with no Health Connect, paired with a Mi Band via Gadgetbridge, Bios ingests HR, steps, and sleep data. Baselines compute. Anomaly detection runs.

### 1.4 Export encryption

`DataExporter` currently writes plaintext JSON/CSV. A seized device could have unencrypted health data in Downloads.

- Add password-based export encryption (AES-256-GCM, key derived via Argon2id from user-chosen passphrase)
- Default: encrypted export. User must set a passphrase before exporting.
- Plaintext export available only as explicit opt-in ("I understand this file is unencrypted")
- Encrypted export format: single `.bios` file (JSON payload + encryption metadata header)
- Import: Bios can re-import `.bios` files with the correct passphrase

**Acceptance:** Exported file is indistinguishable from random data without the passphrase. Plaintext export requires explicit confirmation.

---

## Phase 2: Guardian — Make Bios a health guardian on LETHE [COMPLETE]

> Agent speaks about the owner's health, launcher shows health cards, sensitive data gets extra protection.

### 2.1 Local health API for LETHE agent

Expose a local HTTP API on `localhost:8080/health/` that the LETHE agent queries. No network exposure — localhost only, verified by UID check.

**Endpoints:**
```
GET /health/status          -- overall health status (normal / observation / notice / advisory / urgent)
GET /health/summary         -- today's key metrics vs baseline (HR, HRV, sleep, steps)
GET /health/alerts          -- active unacknowledged alerts
GET /health/baseline/{type} -- personal baseline for a specific metric
POST /health/acknowledge    -- agent can relay user's alert acknowledgment
```

**Response format:** Minimal JSON. No raw readings — only computed summaries and alert metadata. The agent never sees the raw health database.

**Agent integration examples:**
- Launcher card: "Resting HR normal. HRV trending up. Sleep 7h 12m."
- Alert surfacing: "Resting HR elevated +2σ for 48h. Tap to see details."
- Quiet state: no card shown when everything is normal (silence is a feature)

**Acceptance:** LETHE agent mascot displays today's health summary in the Void launcher. Tapping opens Bios to the relevant screen.

### 2.2 Reproductive health data isolation

Cycle tracking data (`basal_body_temperature`, `cycle_day`, `cycle_phase`) is uniquely dangerous post-Dobbs. It needs extra protection beyond the main database.

**Separate encrypted store:**
- Reproductive health metrics stored in a second SQLCipher database with its own encryption key
- Separate keystore alias (`bios_repro_key` vs `bios_main_key`)
- Owner can set a separate passphrase (not just device credentials) for the reproductive store
- Independent retention period (default: 3 cycles or 90 days, owner configurable)
- Independent wipe: owner can destroy reproductive data without touching the rest

**LETHE integration:**
- Duress PIN wipes reproductive store first (fastest path to protecting the most dangerous data)
- Dead man's switch Stage 1 (lock) → reproductive store key destroyed immediately
- Burner mode: reproductive store always wiped, even if owner has exempted main health data

**Standalone behavior:**
- Same separate store, same independent wipe
- Settings: "Reproductive Data" section with its own passphrase, retention, and instant delete

**Acceptance:** Reproductive data encrypted with independent key. Duress PIN destroys it in < 500ms. Main health data survives if only reproductive wipe triggered.

### 2.3 Forensic risk awareness

The owner should know what they're accumulating. Bios stores 90 days of readings by default — on a device that could be seized, that's a liability.

**Data age indicator:**
- Settings and home screen show: "You have X days of health data on this device"
- If on LETHE with burner mode OFF: surface a one-time advisory — "Burner mode is off. Health data accumulates between boots. Review your retention settings."
- If retention > 30 days: subtle indicator in Settings (not a nag — information, not judgment)

**Quick-wipe shortcut:**
- Settings: "Wipe last N days" — selective deletion without destroying baselines
- Notification action on LETHE: long-press Bios notification → "Wipe recent data" (configurable window: 24h, 7d, 30d)

**Acceptance:** Owner can see and control their data footprint. No nagging. Information available when they look for it.

### 2.4 Privacy dashboard

A transparency view where the owner can audit their own exposure. Aligned with LETHE's "every action visible and explainable" principle.

**Dashboard shows:**
- Total data stored (readings count, DB size on disk, date range)
- Connected sources and last sync time per source
- Privacy tier (Private / Community) and what it means
- Community contributions: last 5 sent (anonymized vectors — owner can inspect before they leave)
- Export history: when, format, encrypted or not
- Active permissions: what Health Connect data types Bios can read
- Other apps with Health Connect access (if detectable) — "These apps can also read your health data"
- LETHE status (if embedded): burner mode on/off, dead man's switch armed/disarmed, last wipe event

**Acceptance:** Owner can answer "who has my health data and what can they see?" from a single screen.

### 2.5 Ship TFLite anomaly model

Train and ship the `anomaly_detector.tflite` model that `TFLiteAnomalyModel.kt` already wraps.

**Model spec (from existing code):**
- Input: 9 normalized z-score features (HR, HRV, resting HR, SpO2, respiration, skin temp, sleep quality, steps, active calories)
- Output: anomaly probability (0.0–1.0)
- Architecture: lightweight autoencoder or isolation forest, < 1 MB
- Training: synthetic data + anonymized research datasets (Mishra 2020, Quer 2021)

**Delivery:**
- Ship v1 model in app assets
- OTA model updates via LETHE's IPFS update channel (lethe flavor) or in-app update (standalone)
- Model versioning: `model_version` field in anomaly records for traceability

**Acceptance:** ML-based detection running alongside pattern-based detection. Heuristic fallback no longer the default path.

---

## Phase 3: Expand — More data, stronger baselines, better protection [COMPLETE]

> More sources = richer baselines = earlier detection = better protection.

### 3.1 WHOOP adapter

- OAuth2 integration with WHOOP API v2
- Ingest: HR, HRV, skin temp, SpO2, sleep stages, strain, recovery
- Map to Bios unified schema (vendor_derived confidence for strain/recovery scores)
- Token storage in EncryptedSharedPreferences (same pattern as Oura)
- Wipe hook: tokens destroyed on emergency wipe

### 3.2 Garmin adapter

- OAuth1.0a integration with Garmin Connect API
- Ingest: HR, HRV (if available), sleep, steps, SpO2, stress, body battery
- Higher API rate limits than most vendors — schedule sync conservatively to avoid throttling

### 3.3 Withings adapter

- OAuth2 integration with Withings Health Mate API
- Ingest: weight, body composition, blood pressure, sleep, activity, temperature
- Withings scales and BPMs are common among health-conscious users who aren't fitness enthusiasts — expands Bios beyond the wearable-first audience

### 3.4 Dexcom adapter

- OAuth2 integration with Dexcom API (CGM data)
- Ingest: continuous glucose readings (5-min intervals)
- Confidence: `high` (FDA-cleared)
- CGM data is extremely sensitive — ensure same wipe/isolation protections apply
- Consider: separate retention controls for glucose data (similar to reproductive data isolation rationale)

### 3.5 Coercion-resistant UI

Complement LETHE's duress PIN with a Bios "safe mode":
- Duress PIN activation → Bios shows empty state ("No data yet — connect a wearable to get started")
- Under the hood: main DB key destroyed, reproductive DB key destroyed, export files wiped
- Visually indistinguishable from a fresh install
- On stock Android: owner can set a separate "safe PIN" in Bios settings that triggers the same behavior within the app

### 3.6 Alert content policy

Enforce the "never evaluate the person" principle at the code level:
- Lint rule: alert `title`, `explanation`, and `suggestedAction` fields cannot contain second-person lifestyle judgments (blocklist: "you should", "try to", "consider exercising", "you need to sleep")
- Allowed patterns: data statements ("resting HR +2σ"), questions ("have you felt unwell?"), professional referrals ("discuss with your healthcare provider")
- Test: every `ConditionPattern` in `ConditionPatterns.kt` passes the lint rule
- Future-proofing: any new pattern added must pass the same check in CI

### 3.7 Network transparency for Community tier

- Before first Community contribution: show exactly what will be sent and where
- On stock Android without Tor: warn that ISP/employer can see the owner connects to Bios servers (even though content is anonymized)
- Option: "Only contribute over Tor" (works automatically on LETHE; on stock Android, requires Orbot or similar)
- Option: "Only contribute over VPN"
- Contribution log in privacy dashboard: timestamp, size, what was included (inspectable)

---

## Phase 3B: Longevity Baselines — Learn from open longevity science [COMPLETE]

> Absorb open longevity science (Blueprint, PhenoAge, DunedinPACE) to make detection smarter. Take the science, leave the gamification behind.

### 3B.1 Biomarker reference knowledge base

Expand the condition knowledge base (`ConditionPatterns.kt`) with longevity-science-informed reference data. Blueprint and similar projects (Levine PhenoAge, DunedinPACE) publish the clinical biomarkers that best predict health decline. Many have wearable-derivable proxies Bios already ingests.

**New reference mappings (informational, not prescriptive):**

| Clinical biomarker (Blueprint) | Wearable proxy (Bios) | Already ingested? |
|---|---|---|
| hsCRP (inflammation) | Resting HR elevation + HRV depression | Yes |
| HbA1c (metabolic health) | Glucose variability (CGM) + sleep disruption | CGM: Phase 3.4 |
| VO2 max (cardiorespiratory fitness) | VO2 max estimate from wearable | Metric exists, not ingested by all adapters |
| Arterial stiffness | Blood pressure + pulse wave velocity | BP: Withings adapter (Phase 3.3) |
| Cortisol / stress load | HRV + EDA + sleep latency | Partial (HRV yes, EDA metric exists, sleep latency yes) |
| Body composition (visceral fat) | Body fat % + body mass trends | Withings adapter (Phase 3.3) |
| Sleep architecture quality | Deep/REM/latency scoring | Yes |

**Implementation:**
- Add a `BiomarkerReference` data class linking clinical biomarkers to their wearable proxies, published normal ranges, and source citations
- Populate from Blueprint's published ranges and peer-reviewed longevity literature (Levine 2018, Lu 2019, Belsky 2020)
- Surface in the diagnostics view: "What this metric tells you" — explain what clinical insight each wearable reading is a proxy for
- No scores, no biological age, no rankings — information only

**What Bios does NOT adopt from Blueprint:**
- No biological age score or pace-of-aging metric (evaluates the person)
- No leaderboards or gamification (engagement farming)
- No protocol prescriptions ("take supplement X") — only data-grounded observations
- No cloud dependency for any of this

**Acceptance:** Diagnostics view shows biomarker reference info for each tracked metric. Owner understands what resting HR deviation means in clinical terms, without being told what to do about it.

### 3B.2 Calibrated anomaly thresholds from longevity research

The current anomaly detector uses fixed sigma thresholds (1.0–2.0σ) set by engineering judgment. Blueprint and longevity research publish evidence-based thresholds for when deviations become clinically meaningful.

**Threshold refinements (literature-backed):**
- **Resting HR**: +10% sustained >48h correlates with infection onset (Mishra 2020, already used). Add: +15% sustained >7d correlates with cardiovascular deconditioning (Booth 2012)
- **HRV (RMSSD)**: -20% sustained >72h correlates with autonomic stress (Plews 2013, already referenced). Add: progressive weekly decline over 4+ weeks correlates with overtraining syndrome (Meeusen 2013)
- **Sleep efficiency**: <85% for >5 nights correlates with immune suppression (Prather 2015)
- **Skin temperature**: +0.5°C sustained deviation correlates with pre-symptomatic infection (Smarr 2020, Quer 2021)
- **SpO2**: <95% at rest is clinically significant; <92% is urgent (existing thresholds already align)

**Implementation:**
- Add `ThresholdSource` enum: `ENGINEERING`, `LITERATURE`, `PERSONAL` (from owner's own baseline history)
- Update `SignalRule` to carry a `source` and `citation` field
- Migrate existing thresholds to `LITERATURE` where research supports them, keep `ENGINEERING` where they're heuristic
- Over time, Bios can learn `PERSONAL` thresholds from the owner's own data (Phase 4 intelligence)

**Acceptance:** Every threshold in `ConditionPatterns.kt` has a documented source. New thresholds from longevity literature improve detection sensitivity without increasing false positives (validated against existing test data).

### 3B.3 Expanded condition patterns from longevity science

Blueprint tracks 100+ biomarkers and correlates deviations with specific health risks. Bios can add new condition patterns informed by this research, using only wearable-derivable signals.

**New patterns:**

1. **Metabolic drift** (requires CGM from Phase 3.4)
   - Signals: glucose variability +1.5σ (7d), sleep quality -1.0σ (7d), resting HR +1.0σ (7d)
   - Min active: 2 of 3
   - What it proxies: pre-diabetic metabolic changes (HbA1c rise)
   - Category: `METABOLIC`
   - References: Hall 2018 (CGM in non-diabetics), Zeevi 2015 (personal glycemic responses)

2. **Cardiorespiratory deconditioning**
   - Signals: VO2 max estimate -1.5σ (30d), resting HR +1.0σ (14d), active minutes -1.5σ (14d)
   - Min active: 2 of 3
   - What it proxies: cardiovascular fitness decline (VO2 max drop)
   - Category: `CARDIOVASCULAR`
   - References: Ross 2016 (VO2 max as mortality predictor), Kodama 2009 (meta-analysis)

3. **Chronic inflammation signal**
   - Signals: resting HR +1.0σ (14d), HRV -1.0σ (14d), sleep latency +1.5σ (7d), skin temp +0.5σ (7d)
   - Min active: 3 of 4
   - What it proxies: sustained low-grade inflammation (hsCRP elevation)
   - Category: `CARDIOVASCULAR`
   - References: Furman 2019 (chronic inflammation and aging), Ridker 2003 (hsCRP)

4. **Recovery deficit** (distinct from acute overtraining)
   - Signals: HRV not recovering to baseline within 48h post-exercise (pattern, not just threshold), sleep quality declining week-over-week, resting HR drifting upward over 2+ weeks
   - Min active: 2 of 3
   - What it proxies: accumulated recovery debt, adrenal stress
   - Category: `RECOVERY`
   - References: Stanley 2015 (HRV recovery kinetics), Saw 2016 (monitoring athlete training load)

**Alert language — compliant with existing content policy:**
- "Glucose variability has increased while sleep quality has decreased — a pattern associated with metabolic stress in clinical literature."
- NOT: "You should eat better and sleep more."

**Acceptance:** New patterns pass the alert content policy lint rule (Phase 3.6). Each pattern has peer-reviewed citations. Detection runs alongside existing 4 patterns without increasing alert noise (respects 24h cool-down).

### 3B.4 "What longevity science tracks" reference view

A read-only informational view in the UI that helps the owner understand what health-conscious monitoring looks like, without pushing them toward any protocol.

**Content structure:**
- **Metrics Bios tracks** — listed with current status (data available / no source connected / needs additional device)
- **Metrics from clinical research** — what Blueprint and longevity studies track that requires lab work (blood panels, imaging), with explanation of which wearable signals serve as proxies
- **"Why this matters"** — for each metric category, a 2-3 sentence evidence summary from published research
- **No recommendations** — purely informational. "These are the signals. Here's what the research says about them. You decide."

**Sources:**
- Blueprint's published protocol (publicly available at blueprint.bryanjohnson.com)
- Levine 2018 (PhenoAge biological clock)
- Belsky 2020 (DunedinPACE pace of aging)
- Attia 2023 (Outlive — longevity medicine framework)

**Implementation:**
- New Compose screen: `ui/reference/LongevityReferenceScreen.kt`
- Data: static reference content bundled in app (no network fetch)
- Accessible from diagnostics view or settings
- Updated via app releases, not OTA (content is curated, not dynamic)

**Acceptance:** Owner can browse what longevity science measures and make informed decisions. No nudging, no scores.

---

## Phase 4: Intelligence — Smarter detection, federated learning [COMPLETE]

> Better protection as models improve. Data never leaves the device.

### 4.1 Federated learning framework

- On-device model fine-tuning using local data
- Gradient computation happens locally; only encrypted gradients leave the device
- Secure aggregation protocol: server combines gradients without seeing individual contributions
- Model updates distributed via LETHE IPFS channel or in-app OTA
- Owner can opt out at any time; opting out doesn't reduce their feature set

### 4.2 Expanded condition patterns

Add detection patterns based on medical literature:
- **Respiratory infection** (distinct from general infection onset): elevated respiratory rate + SpO2 dip + cough count
- **Atrial fibrillation screening**: HRV irregularity patterns + elevated resting HR
- **Diabetes risk indicators**: glucose variability + sleep disruption + activity decline
- **Mental health correlates**: sleep pattern changes + HRV decline + activity changes (report data patterns only — never diagnose or label mental state)
- **Menstrual cycle anomalies**: BBT pattern deviation, cycle length irregularity (stored in isolated reproductive DB)

### 4.3 Multi-device sync (E2E encrypted)

For owners with multiple devices (phone + tablet, or migrating to a new phone):
- Sync key derived from passkey via HKDF-SHA256
- Server stores opaque ciphertext only (zero-knowledge)
- XChaCha20-Poly1305 encryption per blob
- Optional: self-hosted sync server for maximum control
- Reproductive data sync is separately gated (owner must explicitly enable)

### 4.4 FHIR export for healthcare interoperability

- Export Bios data as FHIR R4 Bundle (Observation, Device, DetectedIssue resources)
- Encrypted by default (same passphrase-based scheme as regular export)
- Owner can share with their doctor via QR code (contains decryption key + download link from local server)
- No cloud intermediary — direct device-to-device transfer option

---

## Phase 5: Ecosystem — Bios beyond a single device [COMPLETE]

### 5.1 OTA coordination with LETHE
- LETHE's `lethe-ota-update.sh` queries Bios before rebooting
- Bios responds: "safe to reboot" / "delay — active sleep tracking" / "delay — elevated monitoring"
- Owner can override: "reboot now anyway"
- Post-OTA: Bios verifies database integrity and re-schedules workers

### 5.2 Anonymized population health signals
- Aggregate Community tier contributions into regional health signals
- Surface on-device: "Respiratory illness activity elevated in your area" (no server knows the owner's location — signal is derived from anonymized aggregate patterns)
- Owner can disable population signals independently of their Community tier choice

### 5.3 Research pipeline (opt-in)
- Formal opt-in flow with informed consent
- Full de-identification (k-anonymity, l-diversity) on-device before transmission
- Owner can review exactly what will be shared, withdraw at any time
- Research contributions are distinct from Community contributions — separate consent, separate toggle

### 5.4 Backend services (Go + PostgreSQL)
- Sync gateway for E2E encrypted multi-device sync
- Model update service for OTA TFLite models and federated gradients
- Aggregate insights API for population health signals
- Deployable as self-hosted Docker container or managed service
- Zero-knowledge: server never holds encryption keys or plaintext health data

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
