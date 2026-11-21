# Bios Technical Architecture

Bios is a measuring instrument. It collects readings, stores them encrypted on the phone, compares each metric with the owner's own usual range, and shows the result. It does not interpret. The product decision is in [specs/instrument-only.md](specs/instrument-only.md).

Everything described here runs on the phone. Bios has no server.

## Platform Model

One Android codebase (API 28 to 35), two build flavors:

| Flavor | Context | Notes |
|---|---|---|
| `standalone` | Portable APK for any Android 9+ device | The published flavor. Standard app permissions, Health Connect where available. |
| `lethe` | Built for LETHE (LineageOS-based overlay) | Application id suffix `.lethe`, version suffix `-lethe`. Not distributed from this repository. |

The two flavors share all source. LETHE-specific behaviour is chosen at runtime, not at build time: `PlatformDetector` looks for the LETHE system property or the LETHE agent package and returns `LETHE` or `STOCK_ANDROID`.

```
platform/
  PlatformDetector.kt     -- LETHE vs stock Android, capability flags
  LetheCompat.kt          -- interface: wipe signals, agent status, reboot safety
  LetheCompatImpl.kt      -- used when LETHE is detected
  LetheCompatNoop.kt      -- used everywhere else
  HealthApiServer.kt      -- loopback API for the LETHE agent (LETHE only)
  OtaCoordinator.kt       -- answers "is it safe to reboot now?"
  DataDestroyer.kt        -- full wipe: key, database files, tokens, exports, preferences
  ForensicRiskMonitor.kt  -- data footprint figures and partial wipes for the privacy dashboard
  SafeMode.kt             -- wipe on entry of a separate PIN (no screen sets one today)
  IpfsClient.kt           -- client for a local IPFS daemon, used only by the sync code
```

**No Google Play Services dependency.** Bios runs on degoogled ROMs.

| API Level | Android Version | Support |
|---|---|---|
| 35 | Android 15 | Full (targetSdk) |
| 34 | Android 14 | Full (Health Connect built in) |
| 28–33 | Android 9–13 | Health Connect if its APK is installed; otherwise direct sensors, Gadgetbridge and vendor APIs |
| < 28 | Android 8.1 and below | Not supported |

## Scope: Bios vs. Companion Apps

Bios owns ingestion, encrypted storage, usual-range baselines, the one deviation alert, the owner's records, export, and the provider that companion apps read from and write to. Companion apps are separate APKs. See [ECOSYSTEM_BOUNDARIES.md](ECOSYSTEM_BOUNDARIES.md) and [CONSUMER_API.md](CONSUMER_API.md).

## System Overview

```
+------------------------------------------------------------------+
|                        PHONE (Android 9+)                         |
|                                                                   |
|  +----------------+   +------------------+   +-----------------+  |
|  | Ingest         |   | Engine           |   | UI              |  |
|  | (adapters,     |-->| (baselines,      |-->| (home, trends,  |  |
|  |  dedup)        |   |  deviation score)|   |  alerts, log,   |  |
|  +-------+--------+   +---------+--------+   |  records)       |  |
|          |                      |            +--------+--------+  |
|  +-------v----------------------v---------------------v--------+  |
|  |          Local store (Room + SQLCipher, two databases)      |  |
|  +------+--------------------+---------------------+-----------+  |
|         |                    |                     |              |
|  +------v-------+   +--------v---------+   +-------v----------+   |
|  | Export       |   | BiosHealthProvider|  | Loopback API     |   |
|  | (owner asks) |   | (companion apps,  |  | (LETHE agent,    |   |
|  |              |   |  owner-approved)  |  |  LETHE only)     |   |
|  +--------------+   +------------------+   +------------------+   |
+------------------------------------------------------------------+
```

The only outbound network traffic is the fetch from a device maker's API the owner has signed into. See [PRIVACY_ARCHITECTURE.md](PRIVACY_ARCHITECTURE.md) for the complete list of ways data can leave the phone.

## Core Components

### 1. Sensor Ingest Layer

Pulls data from each connected source and normalizes it into `MetricReading` rows (see [DATA_MODEL.md](DATA_MODEL.md)).

**Adapters** (`ingest/`):

- **Health Connect** (`HealthConnectAdapter`): whatever other apps write there (watch companions, the handset pedometer). Each count record keeps the app that wrote it as its source.
- **Gadgetbridge** (`GadgetbridgeAdapter`, `GadgetbridgeReceiver`): broadcast intake from the open-source companion for bands and watches.
- **Direct sensors** (`DirectSensorAdapter`): heart rate, heart beat, step counter and accelerometer sensors exposed through Android's `SensorManager`.
- **Phone sensors** (`PhoneSensorAdapter`): the phone's own accelerometer and gyroscope.
- **Phone sleep** (`PhoneSleepAdapter`, `PhoneSleepCollectionService`): accelerometer, screen and charging samples overnight, turned into a sleep window by `PhoneSleepInference`.
- **Camera PPG** (`CameraPpgAdapter`): an owner-triggered fingertip capture. See [CAMERA_PPG.md](CAMERA_PPG.md).
- **BLE air-quality peripheral** (`BleAirQualityAdapter`): Environmental Sensing readings from a paired device.
- **Vendor APIs** the owner signs into or pastes a token for: Oura, WHOOP, Garmin, Withings, Dexcom, Polar, COROS. Each call goes from the phone straight to the vendor.
- **Imports**: FHIR bundles (`export/FhirImporter`), Apple Health ECG files (`AppleHealthEcgImporter`), lab reports read by on-device OCR (`labocr/`, see [LAB_OCR_INGESTION.md](LAB_OCR_INGESTION.md)).
- **Manual entry**: readings and records the owner types in (see [SELF_REPORTED_DATA_HOME.md](SELF_REPORTED_DATA_HOME.md)).

**Adapter contract.** An adapter fetches from one source, converts to `MetricReading` with a `sourceId` and a `ConfidenceTier`, and hands the batch to `IngestManager`. `IngestManager` isolates failures per adapter, records source health (`SourceHealthStore`, see [specs/source-liveness.md](specs/source-liveness.md)), runs deduplication, then the derivations (sleep components, resting heart rate, heart-rate recovery, circadian figures).

**Deduplication** (`ingest/Deduplicator.kt`), two rules:

1. **Point-in-time metrics (HR, HRV, RHR, SpO2, sleep stages, lab values).** Readings with identical `(metricType, timestamp)` collapse to the highest-confidence one, across sources.
2. **Count metrics (`STEPS`, `ACTIVE_CALORIES`, `ACTIVE_MINUTES`).** One row per `(metricType, timestamp, sourceId)`: every source's count is stored. The day total picks **one source per calendar day** at read time by the owner's order (`data/CountSourceOrder.kt`, `data/CountTotals.kt`, spec [specs/source-preference.md](specs/source-preference.md)).

`SyncWorker` runs ingestion periodically through WorkManager, then deletes readings older than the retention window (90 days).

### 2. Local Data Store

Two SQLCipher-encrypted Room databases:

- **Main database** (`data/BiosDatabase.kt`): readings, payload fields, sources, aggregates, baselines, alerts, and the owner's records (logged events, health events, headache diary, medication list, immunisations, allergies, encounters, imaging studies, intervention events and treatment courses, ECG strips, body measurements).
- **Reproductive database** (`data/ReproductiveDatabase.kt`): cycle readings and contraception records, in a separate file with its own passphrase so it can be destroyed on its own.

Tables of features removed from the app are left in the file with their rows; nothing reads them (see the instrument-only spec).

### 3. Engine

`engine/` holds all computation. Nothing in it names a cause or attaches a meaning to a number.

- **`BaselineEngine`**: per metric, mean, standard deviation, 5th and 95th percentile and trend over a rolling 14-day window. A baseline needs at least 10 sensor samples in the window, and detection starts once 7 days of data exist. Only sensor readings feed baselines. Manual entries never do.
- **`HolisticSignals`**: for each watched metric, the mean of the last 24 hours of sensor readings against the owner's baseline, as a z-score. A metric with no reading in the window or no baseline is left out.
- **`DeviationScore`**: a fixed table that turns the count and mean size of the z-scores beyond 1.5 into one score. An alert is raised at 0.65 (three watched metrics beyond the cut). Every alert can be re-derived by hand from its stored snapshot.
- **`AnomalyDetector`**: applies the score, a 24-hour cool-down, and the rule that evidence already used by an earlier alert does not raise a second one ([specs/rule-window-coverage.md](specs/rule-window-coverage.md), rule 4).
- **Derivations**: sleep components and regularity (`SleepDerivations`, `SleepRegularityCalculator`), cycle statistics (`CycleInference`, `CycleStats`), glucose variability, circadian figures, HRV measures, camera PPG signal processing (`PpgSignalProcessor`, `HrvAnalyzer`, `PulseWaveformFeatures`), estimated amount of a logged substance over time (`ConcentrationCalculator`).

Watched metrics: heart rate, heart rate variability, resting heart rate, blood oxygen, respiratory rate, skin temperature deviation, sleep duration, steps, active calories.

### 4. Alerts

One kind of alert: watched metrics outside the owner's usual range, stated in units. Title "Outside your usual range". The text is the measured lines. No cause, no next step.

| Tier | When | Delivery |
|------|------|----------|
| Observation | Score at the threshold | Stored, no notification |
| Notice | Score above 0.7 | Low-priority notification |
| Advisory | Score above 0.85 | Default-priority notification |
| Warning | Advisory-level score and at least one metric 3 standard deviations or more from baseline | High-priority notification |

The tier describes the size of the deviation and nothing else. A fifth tier value, `URGENT`, remains in the enum and in the companion contract for rows stored by earlier versions; nothing emits it.

The alert detail screen shows the tier meter, a range strip per watched metric, the readings and sources behind them, the snapshot taken at detection, acknowledge, and the owner's note ([specs/alert-detail.md](specs/alert-detail.md)).

Other notifications: an optional daily digest of yesterday's figures (`DailyDigestWorker`) and a notice when a connected source stops delivering (`DisconnectDetector`, [specs/source-liveness.md](specs/source-liveness.md)).

`AlertContentPolicy` lists the words Bios's own text may not use. `VocabularyGateTest` enforces it over string resources and owner-facing string literals.

### 5. Export and Import

`export/`: JSON (`DataExporter`), CSV zip, FHIR R4 bundle (`FhirExporter`: Observation, Device, Procedure, Specimen), a one-page PDF summary (`PdfReportExporter`), optional AES-256 zip wrapping (`EncryptedZipExporter`). An alert leaves the app as what was measured: time, tier, the metrics with their value and the usual range at detection, acknowledgement and the owner's note. Alerts are not part of the FHIR bundle.

### 6. Companion Provider

`provider/BiosHealthProvider` is a ContentProvider guarded by an Android runtime permission and a per-app approval the owner gives inside Bios; writes are limited to a per-package list of keys. Companions read metrics, baselines and source liveness, and write the metric keys assigned to them. Contract: [CONSUMER_API.md](CONSUMER_API.md); shared constants: the `android/bios-contracts` module.

### 7. LETHE Integration (only when LETHE is detected)

- **Loopback API** (`HealthApiServer`): bound to `127.0.0.1:8080`, not reachable from the network. Endpoints: `GET /health/status` (highest unacknowledged tier and alert count), `GET /health/summary` (last 24 hours per metric), `GET /health/alerts` (id, tier label, title, measured lines, detection time), `GET /health/baseline/{metric}`, `POST /health/acknowledge`.
- **Agent status** (`LetheCompatImpl`): posts the status summary to the LETHE agent on loopback when it changes.
- **Wipe signals**: LETHE broadcasts for burner mode, dead man's switch, panic and duress trigger `DataDestroyer.destroyAll`.
- **OTA coordination** (`OtaCoordinator`): answers whether a reboot is safe now.

### 8. Device-to-Device Sync (present, not enabled)

`sync/` contains a payload codec, a local-network transport and a stub for an Iroh node. The transport starts only when a preference is set, and no screen sets it. See [PRIVACY_ARCHITECTURE.md](PRIVACY_ARCHITECTURE.md).

## Data Flow Summary

```
Wearable / phone sensor / vendor API / manual entry
        |
        v
   Ingest adapter (normalize, tag source and confidence)
        |
        v
   Deduplicator -> local encrypted store -> derivations
        |
        v
   BaselineEngine (owner's usual range per metric)
        |
        v
   AnomalyDetector (DeviationScore over the last 24 h)
        |
        v
   AlertManager (store, notify from Notice upward)
        |
        v
   UI (home, trends, alert detail, records)      Export (when the owner asks)
```

## Non-Functional Requirements

| Concern | Target |
|---------|--------|
| **Offline** | Fully functional with no network connection |
| **Portability** | One codebase for LETHE and stock Android 9+ |
| **No Play Services** | No dependency on Google Play Services, Firebase or GMS Core |
| **Min SDK** | API 28 (Android 9) |
| **Baseline start** | A metric gets a usual range after 10 sensor samples within 14 days |
