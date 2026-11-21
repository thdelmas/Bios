# Bios Unified Data Model

## Design Goals

1. **Source-agnostic** -- every wearable and phone sensor maps to the same schema
2. **Temporal** -- all data is time-series; every reading has a precise timestamp and duration
3. **Attributable** -- every reading tracks its source device, sensor type, and confidence
4. **Extensible** -- new metric types can be added without schema migrations
5. **Exportable** -- maps to FHIR Observation resources
6. **Platform-portable** -- schema is identical on LETHE (embedded) and stock Android (standalone); no platform-specific columns or tables. Works across Android API 28–35.

---

## Core Entities

### MetricReading

The atomic unit of data in Bios. One sensor measurement at one point in time.

```
MetricReading {
    id:             UUID
    metric_type:    String              -- MetricType key; the unit is the key's canonical unit
    value:          Float64
    timestamp:      epoch ms            -- when the measurement was taken
    duration_sec:   Int?                -- for readings that cover a span (a night, a session)
    source_id:      DataSource.id       -- which device, API or app produced this
    confidence:     ConfidenceTier
    is_primary:     Bool                -- selected as primary after deduplication
    created_at:     epoch ms
    seen_count:     Int                 -- revision tracking for sources that re-deliver a day value
    first_seen_at:  epoch ms?
    provisional:    Bool                -- true until a re-delivered value has settled; engines read settled rows only
    note:           String?             -- the owner's free text; never read by any engine
}
                                        -- unique: (source_id, metric_type, timestamp)
```

### MetricType (enum)

The canonical list is `MetricType` in
[`android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt`](../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt).
Each entry has a string key, a canonical unit (`MetricUnit`), a domain
(`MetricDomain`) and a flag saying whether the owner may enter it by hand.
The module holds 190 keys. About half are lab values (`BIOMARKER`); the
rest are spread over cardiovascular, respiratory, temperature, sleep,
activity, metabolic, recovery, cycle, intake, environment, body
measurements and the keys companion apps write.

A key in the contract does not mean Bios produces it. Many keys have a
producer only when the owner connects a given source, types the value, or
installs a companion. The Data Coverage screen shows which keys have data
on a given phone.

Keys are append-only: `MetricTypeKeysSnapshotTest` fails if one is removed
or renamed, because companion apps compile against them.

#### Legacy keys with no producer in Bios

Some keys remain in the contract from earlier versions and nothing in
Bios writes them any more, or ever did. Examples: `sleep_score`,
`sleep_apnea_event`, `seizure_event`, `ectopy_burden_estimate`,
`tremor_amplitude_g`, `tremor_frequency_hz`, `gait_variability_cov`. They
stay because removal would break the contract. Readings stored under such
a key by an earlier version are left in the database and appear in the
metric browser and the JSON export.

#### Epigenetic age clocks

`epigenetic_age_dunedin_pace`, `epigenetic_age_grim`, `epigenetic_age_pheno`
and `epigenetic_age_horvath` are values the owner types in from a test
report. Each is shown on its own, labelled with who produced it. Bios does
not combine them into a figure of its own.

### EventPayloadField

Sidecar to `MetricReading` for composite events that don't fit in a single
scalar. Each row is one named field of a structured event attached to a
parent reading. Scalar metrics never use this table — only multi-field
composites do.

```
EventPayloadField {
    reading_id:     UUID                -- FK to MetricReading.id; CASCADE on delete
    field_key:      String              -- field name within the event (e.g. "modality")
    string_value:   String?             -- exactly one of these three is non-null
    double_value:   Float64?
    long_value:     Int64?
}
                                        -- composite PK: (reading_id, field_key)
```

#### Field-key vocabulary

Field keys are part of the public contract — companions compile against
them. Renames are breaking changes. New field keys
land in this document alongside the metric type they describe; treat them
with the same stability commitment as `MetricType.key` strings.

| Metric type | Field key | Typed column | Notes |
|---|---|---|---|
| `EXERCISE_SESSION` | `modality` | `string_value` | enum: `CARDIO`, `STRENGTH`, `INTERVAL`, `MOBILITY`, `OTHER` |
| `EXERCISE_SESSION` | `start_utc` | `long_value` | epoch ms |
| `EXERCISE_SESSION` | `end_utc` | `long_value` | epoch ms |
| `EXERCISE_SESSION` | `avg_hr_bpm` | `double_value` | nullable — mean of HR samples that fall inside the session window; omitted entirely when no samples land in window (null ≠ 0) |
| `EXERCISE_SESSION` | `rpe` | `long_value` | 1–10, nullable — only populated if the source captured it |
| *(any `BIOMARKER`)* | `lab_name` | `string_value` | Free-text lab/facility name. Provenance only: the same assay can differ between labs. |
| *(any `BIOMARKER`)* | `fasting` | `long_value` | 0 = non-fasting, 1 = fasting. Omitted entirely when unknown (null ≠ 0). |
| *(any `BIOMARKER`)* | `specimen` | `string_value` | enum: `SERUM`, `PLASMA`, `WHOLE_BLOOD`, `OTHER`. Some assays differ measurably across specimen types. |
| *(any `BIOMARKER`)* | `source_uri` | `string_value` | Content URI to the original PDF/photo of the lab report — owner-attached, for retrieval; never parsed. |

#### When to use the payload table

Use a payload row when **all** of:
1. The metric needs more than one structured value per reading.
2. Bios code or a companion reader needs to query individual
   fields (`WHERE modality = 'CARDIO' AND avg_hr_bpm > 140`).
3. Stuffing the data into `MetricReading.value` + a convention would
   require ad-hoc parsing on every read.

Do **not** use it for:
- Owner-private free-text notes — those go in `LoggedEvent.note` (for
  event-shaped data) or `MetricReading.note` (for biomarker/manual-entry
  surfaces). The sidecar table is queryable by engines; owner-recall text
  isn't.
- Vendor-specific raw blobs — Bios does not store them.
- Re-encoding the scalar that already fits in `value`.

**Note on scalar metrics:** the "scalar metrics never use this table" rule
applies to the *value itself* (don't denormalize `value` into the sidecar).
A scalar metric *may* still attach structured provenance via the sidecar —
biomarker readings are the canonical example: the value is a scalar that
lives in `MetricReading.value`, but the lab/fasting/specimen context is
queryable structured data that lives here.

### DataSource

Tracks where a reading came from.

```
DataSource {
    id:             UUID
    source_type:    SourceType          -- enum: health_connect | gadgetbridge | direct_sensor | oura_api | whoop_api | garmin_api | withings_api | dexcom_api | polar_api | coros_api | phone_sensor | phone_sensor_derived | bios_inferred | camera_ppg | ble_peripheral | self_reported
    device_name:    String?             -- "Apple Watch Series 11", "Oura Ring Gen 4"
    device_model:   String?             -- vendor model identifier
    sensor_type:    SensorType          -- enum: optical_hr | ecg | accelerometer | thermistor | ppg_camera | cgm | ...
    connected_at:   DateTime (UTC)
    last_sync_at:   DateTime (UTC)
}
```

### ConfidenceTier (enum)

Used for deduplication. The tier describes the kind of sensor, as recorded by the adapter.

```
clinical        -- highest tier; the enum name is kept from earlier versions and no adapter assigns it by name
high            -- e.g. a watch ECG, a continuous glucose sensor
medium          -- e.g. optical wrist HR, ring SpO2
low             -- estimated or derived (e.g., phone camera PPG, mic-based respiratory rate)
vendor_derived  -- pre-computed by vendor algorithm (e.g., WHOOP strain, Oura readiness)
```

### ComputedAggregate

Pre-calculated rollups for fast querying and trend display.

```
ComputedAggregate {
    id:             UUID
    metric_type:    MetricType
    period:         Period              -- enum: hour | day | week | month
    period_start:   DateTime (UTC)
    mean:           Float64
    median:         Float64
    min:            Float64
    max:            Float64
    std_dev:        Float64
    p5:             Float64             -- 5th percentile
    p95:            Float64             -- 95th percentile
    sample_count:   Int
    primary_source: DataSource.id       -- dominant source for this period
}
```

### PersonalBaseline

The user's normal range for a given metric, computed by the Baseline Engine.

```
PersonalBaseline {
    id:             UUID
    metric_type:    MetricType
    context:        BaselineContext      -- enum: resting | active | sleeping | all
    window_days:    Int                 -- rolling window used (default: 14)
    computed_at:    DateTime (UTC)
    mean:           Float64
    std_dev:        Float64
    p5:             Float64
    p95:            Float64
    trend:          TrendDirection      -- enum: rising | stable | falling
    trend_slope:    Float64             -- rate of change per day
}
```

### Anomaly

One alert: watched metrics outside the owner's usual range. The table name
and several column names predate the instrument-only decision.

```
Anomaly {
    id:             UUID
    detected_at:    DateTime (UTC)
    metric_types:   MetricType[]        -- the metrics beyond the cut
    deviation_scores: Map<MetricType, Float64>  -- z-score per metric
    combined_score: Float64             -- DeviationScore
    pattern_id:     String?             -- "ml_holistic_anomaly" on every alert raised now (identifier kept from earlier versions);
                                        -- "earlier_alert" on rows raised by a removed pattern
    severity:       AlertTier           -- observation | notice | advisory | warning (urgent: stored rows of earlier versions only)
    title:          String              -- "Outside your usual range"
    explanation:    String              -- the measured lines, in units
    suggested_action: String?           -- always null now
    signal_snapshot: JSON?              -- watched metrics as they stood at detection
    acknowledged:   Bool
    acknowledged_at: DateTime?
    notes:          String?             -- the owner's note
    -- journal columns of earlier versions (felt_sick, visited_doctor, diagnosis,
    -- symptoms, outcome_accurate, feedback_at) remain in the table. Nothing
    -- writes them; the yes/no answers are no longer shown or exported.
}
```

---

## Vendor Mapping Examples

How vendor-specific data maps to Bios Unified Metrics:

| Vendor Field | Bios MetricType | Unit Conversion | Confidence |
|---|---|---|---|
| Health Connect `HeartRateRecord` | `heart_rate` | bpm (none needed) | medium |
| Health Connect `HeartRateVariabilityRmssdRecord` | `heart_rate_variability` | ms (none needed) | medium |
| Health Connect `SleepSessionRecord` stages | `sleep_stage` | map stages to awake/light/deep/rem | medium |
| Health Connect `OxygenSaturationRecord` | `blood_oxygen` | percentage (none needed) | medium |
| Health Connect `SkinTemperatureRecord` | `skin_temperature_deviation` | delta C (none needed) | medium |
| Oura `readiness.score` | `recovery_score` | normalize 0-100 | vendor_derived |
| Dexcom `egv.value` | `blood_glucose` | mg/dL (none needed) | high |
| Withings blood pressure measure | `blood_pressure_systolic` + `blood_pressure_diastolic` | mmHg (split into two readings) | medium |
| Phone camera PPG | `heart_rate` | bpm (extract from waveform) | low |

---

## Storage Estimates

| Data Type | Approx. Size Per Reading | Typical Daily Volume | Daily Storage |
|---|---|---|---|
| HR (5-min intervals) | 50 bytes | 288 readings | ~14 KB |
| HRV (hourly) | 50 bytes | 24 readings | ~1.2 KB |
| SpO2 (hourly) | 50 bytes | 24 readings | ~1.2 KB |
| Sleep stages (per stage) | 60 bytes | ~20 transitions | ~1.2 KB |
| Steps (hourly) | 50 bytes | 24 readings | ~1.2 KB |
| Temperature (hourly) | 50 bytes | 24 readings | ~1.2 KB |
| CGM (5-min intervals) | 50 bytes | 288 readings | ~14 KB |
| Computed aggregates | 100 bytes | ~50/day | ~5 KB |
| **Typical daily total (no CGM)** | | | **~25 KB** |
| **Typical daily total (with CGM)** | | | **~40 KB** |
| **1 year (no CGM)** | | | **~9 MB** |
| **1 year (with CGM)** | | | **~15 MB** |

Readings older than 90 days are deleted by the periodic sync, which bounds the size further.

---

## FHIR Mapping

| Bios Entity | FHIR Resource |
|---|---|
| MetricReading | Observation |
| DataSource | Device |
| PersonalBaseline | Observation |
| Intervention events | Procedure |
| Lab specimen context | Specimen |

Alerts are not part of the FHIR bundle. The JSON and CSV exports carry an
alert as what was measured (see [PRIVACY_ARCHITECTURE.md](PRIVACY_ARCHITECTURE.md)).

---

## Relation to HPI (audit 2026-05-16)

[karlicoss/HPI](https://github.com/karlicoss/HPI) (Human Programming Interface) is the most architecturally similar project in the personal-data ecosystem. The data model here is deliberately divergent on one dimension and aligned on the others.

**Aligned principles:**

- **Local-first / offline** — both work against data on-device, no cloud dependency
- **Hide source-specific gore** — each Bios adapter and each HPI module encapsulates the messy parsing/locating/caching for one data source
- **Typed outputs** — Bios uses `MetricReading` (Kotlin dataclass); HPI uses per-module `NamedTuple`s
- **Defensive parsing** — Bios isolates failures per adapter; HPI uses mypy-assisted error handling to surface partial failures without crashing
- **Extensible without forking** — Bios via new adapter, HPI via namespace packages

**Intentional divergence — source-agnostic vs. source-typed:**

HPI surfaces each source as its own typed function (`my.reddit.all.saved()` returns `Iterator[Saved]` specific to Reddit). The caller — a Python programmer — handles cross-source merging.

Bios is the opposite: **every source maps to the same `MetricReading` schema at ingest time**, with `DataSource` and `ConfidenceTier` carrying the attribution. End users (not programmers) need a single coherent view, normalization happens once, and FHIR export requires uniform units anyway. This is the right call for a consumer app on a battery-constrained device.

**Borrow from HPI:**

1. **Explicit per-metric merge layer.** HPI's `all.py` pattern (combine `google_takeout` + `gpslogger` + `via_ip` into `my.location`) is currently implicit in Bios via `ConfidenceTier` dedup. Consider an explicit per-`MetricType` merge specification so it's debuggable.
2. **Per-adapter failure isolation.** HPI's `@import_source` decorator catches per-source failures with a warning rather than crashing the whole package. Verify `IngestManager` has equivalent isolation per adapter (Oura down ≠ Health Connect broken).
3. **Directory-by-default for adapters.** HPI's lesson: convert `module.py` → `module/parser.py` *before* you need to add subfiles, because retrofit is painful. New ingest adapters should ship as `ingest/oura/` not `ingest/OuraAdapter.kt` if there's any chance they'll grow auth/sync/mapping subfiles.

**Future interop:** A Python HPI overlay module `my.bios` that reads Bios's exported data could expose the Bios corpus through HPI's entire ecosystem — `my.bios.heart_rate()` plugs into the same notebooks that work with Fitbit/Oura. Earmarked, not committed.
