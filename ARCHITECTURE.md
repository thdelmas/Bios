# Bios Technical Architecture

## System Overview

```
+------------------------------------------------------------------+
|                        MOBILE APP (Android)                       |
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

**Adapters** (one per data source):
- **Health Connect Adapter** (Android) -- Samsung Galaxy Watch, Pixel Watch, Fitbit, other Android wearables
- **Direct API Adapters** -- Oura, WHOOP, Garmin, Withings, Dexcom, Polar (REST/OAuth2)
- **Phone Sensor Adapter** -- raw accelerometer, gyroscope, microphone, camera PPG

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

**Model approach:**
- **Phase 1:** Statistical methods (rolling z-scores, exponential moving averages, percentile-based thresholds). Simple, interpretable, low compute.
- **Phase 2:** Lightweight on-device ML (TensorFlow Lite). Anomaly detection models (isolation forest, autoencoder). Shipped as frozen models, updated OTA.
- **Phase 3:** Federated learning. Models improve across the user base without raw data ever leaving devices.

### 4. Insight & Alert System

Translates detection engine output into human-readable, actionable information.

**Alert tiers:**
| Tier | Trigger | User experience |
|------|---------|-----------------|
| **Observation** | Single metric mild deviation (1-2 sigma) | Visible in daily summary, no push notification |
| **Notice** | Sustained deviation (2+ sigma for 48h+) or multi-signal mild correlation | Push notification, detail card in app |
| **Advisory** | Strong multi-signal correlation matching a known condition pattern | Prominent alert, explanation, suggested action (e.g., "consider talking to your doctor") |
| **Urgent** | Acute anomaly (e.g., SpO2 < 90%, HR > 150 at rest, suspected AFib) | Immediate notification, emergency guidance, option to call emergency services |

**Principles:**
- Every alert includes: what changed, how it compares to your baseline, why it matters, and what you can do
- No medical diagnoses -- always framed as patterns worth discussing with a healthcare provider
- User can tune sensitivity per condition category
- Cool-down periods to prevent alert fatigue

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
| **Battery** | Baseline engine uses < 2% daily battery budget |
| **Storage** | < 100 MB for 1 year of data at typical wearable sampling rates |
| **Offline** | Fully functional with no network connection (core value) |
| **Startup** | App usable within 7 days of data collection (minimum baseline period) |
