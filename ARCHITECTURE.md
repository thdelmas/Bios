# Bios Technical Architecture

## Platform Model

Bios is **primarily designed to be embedded on LETHE** (OSmosis privacy-hardened Android overlay, LineageOS 22.1 base) and **portable across stock Android versions** (API 28–35).

### Build Flavors

| Flavor | Context | Install method | Permissions |
|---|---|---|---|
| `lethe` | Embedded system app on LETHE ROM | Pre-installed via OSmosis overlay | System-level sensor access, LETHE agent IPC, launcher integration |
| `standalone` | Portable APK for any Android 9+ device | Sideload or app store | Standard app permissions, Health Connect where available |

### Platform Abstraction

All platform-specific behavior is abstracted behind interfaces in the `platform/` package:

```
platform/
  PlatformDetector.kt        -- detect LETHE vs stock Android at runtime
  LetheCompat.kt             -- interface for LETHE agent, launcher cards, OTA hooks
  HealthDataSource.kt        -- Health Connect (API 14+) vs direct sensor fallback
  KeystoreProvider.kt        -- LETHE hardware keystore vs Android Keystore
  BackgroundScheduler.kt     -- WorkManager (stock) vs LETHE job scheduler
```

On stock Android, LETHE-specific interfaces return no-ops. No LETHE classes are loaded.

### Android Version Compatibility

| API Level | Android Version | Support Level |
|---|---|---|
| 35 | Android 15 | Full (targetSdk) |
| 34 | Android 14 | Full (Health Connect native) |
| 33 | Android 13 | Full (Health Connect via APK) |
| 28–32 | Android 9–12L | Core features, direct sensor adapters instead of Health Connect |
| < 28 | Android 8.1 and below | Not supported |

**No Google Play Services dependency.** Bios must run on degoogled ROMs (LETHE, LineageOS, CalyxOS, GrapheneOS, etc.).

---

## System Overview

```
+------------------------------------------------------------------+
|                     MOBILE APP (Android 9+)                       |
|              [LETHE embedded  /  Standalone portable]             |
|                                                                   |
|  +------------------+  +------------------+  +-----------------+  |
|  | Sensor Ingest    |  | Baseline Engine  |  | Insight UI      |  |
|  | Layer            |  | (on-device ML)   |  | & Alerts        |  |
|  +--------+---------+  +--------+---------+  +--------+--------+  |
|           |                     |                      |          |
|  +--------v---------------------v----------------------v--------+ |
|  |                   Local Data Store                           | |
|  |              (Room + SQLCipher encrypted)                    | |
|  +-------------------------------+------------------------------+ |
|                                  |                                |
|             (opt-in sync only)   |                                |
+----------------------------------+--------------------------------+
                                   |
                    +--------------v---------------+
                    |       Sync Gateway           |
                    |  (E2E encrypted, user-keyed) |
                    +--------------+---------------+
                                   |
                    +--------------v---------------+
                    |        Backend Services       |
                    |  (stateless, zero-knowledge)  |
                    +--------------+---------------+
                                   |
              +--------------------+--------------------+
              |                    |                     |
   +----------v--------+ +--------v---------+ +--------v---------+
   | Model Update       | | Aggregate        | | Research         |
   | Service            | | Insights API     | | Pipeline         |
   | (federated / OTA)  | | (anonymized)     | | (opt-in, de-ID)  |
   +--------------------+ +------------------+ +------------------+
```

## Core Components

### 1. Sensor Ingest Layer

Responsible for pulling data from all supported sources and normalizing it into Bios's unified schema.

**Adapters** (one per data source, 9 implemented):
- **Health Connect Adapter** (Android 14+) -- Samsung Galaxy Watch, Pixel Watch, Fitbit, other Android wearables
- **Gadgetbridge Adapter** -- open-source companion for degoogled devices (Mi Band, PineTime, Amazfit, 40+ devices)
- **Direct Sensor Adapter** -- HR, HRV (inter-beat interval), steps via Android SensorManager APIs
- **Oura API Adapter** -- Oura Ring v2 REST API (HR, HRV, sleep stages, temperature, readiness)
- **WHOOP API Adapter** -- WHOOP v2 REST API (HR, HRV, SpO2, skin temp, sleep, strain, recovery)
- **Garmin API Adapter** -- Garmin Connect API (HR, resting HR, SpO2, respiration, sleep, steps)
- **Withings API Adapter** -- Withings Health Mate API (blood pressure, skin temp, sleep, body composition)
- **Dexcom API Adapter** -- Dexcom CGM API (continuous glucose, 5-min intervals, FDA-cleared)
- **Phone Sensor Adapter** -- raw accelerometer, step counter (activity intensity proxy)

**Responsibilities:**
- Poll or subscribe to data source changes
- Convert vendor-specific formats into Bios Unified Metrics (see DATA_MODEL.md)
- Deduplicate overlapping readings (e.g., HR from both Apple Watch and Oura)
- Tag each reading with source, confidence level, and timestamp
- Buffer and batch-write to local store

**Deduplication strategy:**
- When multiple sources report the same metric type for overlapping time windows, prefer the source with the highest sensor confidence tier (ECG > optical chest > optical wrist > phone camera)
- Store all raw readings but flag the "primary" reading used for analysis

### 2. Local Data Store

All health data lives on-device by default. The store is the single source of truth.

**Structure:**
- **Raw readings** -- immutable append-only log of every ingested data point
- **Computed metrics** -- rolling aggregates (hourly, daily, weekly) pre-calculated for performance
- **Baselines** -- per-user personal baselines computed by the Baseline Engine
- **Alerts** -- generated anomaly records with severity, explanation, and linked readings
- **User preferences** -- alert thresholds, connected sources, display settings

**Requirements:**
- AES-256 encryption at rest (SQLCipher)
- Key stored in Android Keystore, protected by device credentials
- Exportable (user owns their data -- full export in open format)
- Prunable (user controls retention period)

### 3. Baseline Engine (On-Device ML)

The core intelligence of Bios. Runs entirely on-device to preserve privacy.

**What it does:**
- Builds a **personal baseline** for each metric over a rolling window (default: 30 days)
- Computes **normal ranges** per user (not population averages)
- Detects **deviations** from personal baseline using statistical and ML methods
- Cross-correlates **multiple signals** to increase detection confidence (e.g., elevated resting HR + rising temperature + declining HRV = potential infection onset)

**Detection pipeline:**

```
Raw readings
    |
    v
Rolling statistics (mean, std, percentiles per metric)
    |
    v
Deviation scoring (z-score against personal baseline)
    |
    v
Multi-signal correlation (weighted combination of deviations)
    |
    v
Pattern matching (known condition signatures from medical literature)
    |
    v
Confidence scoring + severity classification
    |
    v
Alert generation (if threshold met)
```

**Condition patterns (12 implemented, 33 signal rules):**
- Infection onset, sleep disruption, cardiovascular stress, overtraining (Phase 1)
- Metabolic drift, cardiorespiratory deconditioning, chronic inflammation, recovery deficit (Phase 3B — longevity science)
- Respiratory infection, AFib screening, mental health correlate, menstrual cycle anomaly (Phase 4)
- 24 of 33 signal rules carry literature-backed thresholds with citations

**Model approach:**
- **Statistical methods** (current): Rolling z-scores, exponential moving averages, percentile-based thresholds. Simple, interpretable, low compute.
- **On-device ML**: LiteRT (Google's rebranded TensorFlow Lite). Anomaly detection model wrapper implemented with heuristic fallback. Shipped as frozen models, updated OTA via ModelUpdateManager.
- **Federated learning**: FederatedTrainer computes on-device gradients from owner feedback, applies differential privacy, encrypts for server aggregation. Owner opts in; opting out doesn't reduce features.

### 4. Insight & Alert System

A guardian that watches for what the owner might miss, then speaks clearly when something matters.

**Alert tiers:**
| Tier | Trigger | User experience |
|------|---------|-----------------|
| **Observation** | Single metric mild deviation (1-2 sigma) | Visible in daily summary, no push notification |
| **Notice** | Sustained deviation (2+ sigma for 48h+) or multi-signal mild correlation | Push notification, detail card in app |
| **Advisory** | Strong multi-signal correlation matching a known condition pattern | Prominent alert, explanation, suggested action (e.g., "consider talking to your doctor") |
| **Urgent** | Acute anomaly (e.g., SpO2 < 90%, HR > 150 at rest, suspected AFib) | Immediate notification, emergency guidance, option to call emergency services |

**Guardian Principles (aligned with LETHE):**
- **Report data, never judge the person.** Say "resting HR elevated +2σ for 48h" — never "you're unhealthy" or "you should exercise more"
- **Silence is a feature.** No daily "streak" notifications, no gamification, no engagement farming. Bios speaks when something matters, not to fill a feed
- **The owner is final.** Every alert includes what changed, how it compares to baseline, and what the owner can do — but the owner decides whether to act
- **No behavioral nudges.** No "you haven't walked today", no guilt mechanics, no wellness scores designed to drive app opens
- **No medical diagnoses.** Always framed as patterns worth discussing with a healthcare provider
- Owner can tune sensitivity per condition category
- Cool-down periods to prevent alert fatigue
- On LETHE: alerts surface through the agent mascot in the Void launcher, matching LETHE's calm, factual communication style

### 5. Backend Services (Optional, Opt-In)

The backend exists only for features that genuinely require it. It never stores raw health data.

**Sync Gateway:**
- End-to-end encrypted sync for multi-device access
- User holds the encryption key; server sees only ciphertext
- Can be self-hosted for maximum control

**Model Update Service:**
- Distributes updated detection models OTA
- Receives federated learning gradients (not data) in Phase 3

**Aggregate Insights API:**
- Powers anonymized, population-level insights (e.g., "flu activity is elevated in your region")
- Built from differential-privacy-protected aggregates, never individual data

**Research Pipeline (opt-in only):**
- Users can voluntarily contribute de-identified data to medical research
- Requires explicit informed consent
- Full de-identification pipeline (k-anonymity, l-diversity)

### 6. API Layer

Internal API between app layers, and external API for future integrations.

**Internal (app modules):**
- Ingest -> Store (write normalized readings)
- Store -> Baseline Engine (read time-series for analysis)
- Baseline Engine -> Alert System (write anomaly detections)
- Alert System -> UI (push alerts, render insight cards)

**External (future):**
- REST API for third-party integrations (e.g., telehealth platforms, EHR export)
- FHIR-compatible export for healthcare interoperability
- Webhook support for custom automations

### 7. LETHE Integration Layer (lethe flavor only)

When running as an embedded system app on LETHE, Bios gains deep OS-level integration:

**Agent Integration:**
- Bios exposes a local API (`localhost:8080/health/`) that LETHE's on-device agent queries
- The agent can surface health insights in the Void launcher (LETHE's home screen)
- Example: agent mascot says "Your resting HR has been elevated for 48h — consider resting today"

**Launcher Health Cards:**
- Bios renders summary cards (daily score, active alerts, trends) in LETHE's WebView launcher
- Cards update on boot and on-demand via local IPC

**OTA Coordination:**
- LETHE's OTA update script (`lethe-ota-update.sh`) queries Bios state before rebooting
- Delays system updates during active workouts, sleep tracking, or critical health monitoring
- Installs during detected recovery/idle windows

**Privacy Stack Synergy:**
- LETHE's firewall rules block all outbound traffic by default — Bios's zero-network-required design fits natively
- LETHE's tracker-blocking hosts file provides defense-in-depth against any analytics leakage
- Burner mode (wipe-on-boot) clears Bios data too — appropriate for high-threat-model users
- Dead man's switch can trigger Bios data destruction alongside OS-level wipe

**Sensor Access:**
- As a system app, Bios can access sensors directly without Health Connect as an intermediary
- Enables continuous background monitoring with lower battery overhead than a third-party app

---

## Data Flow Summary

```
Wearable / Phone Sensor
        |
        v
   Ingest Adapter (normalize, dedupe, tag)
        |
        v
   Local Encrypted Store (append raw, compute aggregates)
        |
        v
   Baseline Engine (personal baseline, deviation detection, cross-correlation)
        |
        v
   Alert System (classify severity, generate human-readable insight)
        |
        v
   User Interface (daily summary, alert cards, trends, export)
        |
        v (opt-in only)
   E2E Encrypted Sync -> Backend (multi-device, model updates, anonymized aggregates)
```

---

## Non-Functional Requirements

| Concern | Target |
|---------|--------|
| **Latency** | Alert generation within 5 minutes of data availability |
| **Battery** | Baseline engine uses < 2% daily battery budget (< 1% on LETHE with system-level scheduling) |
| **Storage** | < 100 MB for 1 year of data at typical wearable sampling rates |
| **Offline** | Fully functional with no network connection (core value; LETHE default is network-off) |
| **Startup** | App usable within 7 days of data collection (minimum baseline period) |
| **Portability** | Single codebase runs on LETHE (embedded) and stock Android 9+ (standalone) |
| **No Play Services** | Must not depend on Google Play Services, Firebase, or GMS Core |
| **Min SDK** | API 28 (Android 9 Pie, 2018) — covers 95%+ of active devices including older degoogled phones |
