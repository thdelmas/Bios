# Bios Unified Data Model

## Design Goals

1. **Source-agnostic** -- every wearable and phone sensor maps to the same schema
2. **Temporal** -- all data is time-series; every reading has a precise timestamp and duration
3. **Attributable** -- every reading tracks its source device, sensor type, and confidence
4. **Extensible** -- new metric types can be added without schema migrations
5. **Exportable** -- maps cleanly to FHIR Observation resources for healthcare interoperability
6. **Platform-portable** -- schema is identical on LETHE (embedded) and stock Android (standalone); no platform-specific columns or tables. Works across Android API 28–35.

---

## Core Entities

### MetricReading

The atomic unit of data in Bios. One sensor measurement at one point in time.

```
MetricReading {
    id:             UUID
    metric_type:    MetricType          -- what was measured
    value:          Float64             -- the measurement value
    unit:           Unit                -- SI or medical-standard unit
    timestamp:      DateTime (UTC)      -- when the measurement was taken
    duration_sec:   Int?                -- for aggregated readings (e.g., "avg HR over 5 min")
    source:         DataSource          -- which device/API produced this
    confidence:     ConfidenceTier      -- how reliable is this reading
    is_primary:     Bool                -- selected as primary after deduplication
    raw_payload:    JSON?               -- original vendor-specific data (for debugging)
    created_at:     DateTime (UTC)
}
```

### MetricType (enum)

Organized by physiological domain. Each type has a canonical unit. Types marked **[implemented]** exist in `Enums.kt`; those marked **[planned]** are defined in this schema for future adapter expansion.

```
// Cardiovascular
heart_rate                  -- bpm                           [implemented]
heart_rate_variability      -- ms (RMSSD)                    [implemented]
resting_heart_rate          -- bpm                           [implemented]
ecg_waveform                -- mV[] (raw waveform array)     [planned — ECG-capable adapters]
blood_pressure_systolic     -- mmHg                          [implemented]
blood_pressure_diastolic    -- mmHg                          [implemented]
blood_oxygen                -- % (SpO2)                      [implemented]

// Respiratory
respiratory_rate            -- breaths/min                   [implemented]
respiratory_event           -- apnea/hypopnea event flag     [planned — sleep study adapters]
cough_count                 -- count per hour                [planned — microphone adapter]

// Temperature
skin_temperature            -- degrees C                     [implemented]
skin_temperature_deviation  -- delta C from personal baseline [implemented]

// Sleep
sleep_stage                 -- enum: awake | light | deep | rem [implemented]
sleep_duration              -- seconds                       [implemented]
sleep_latency               -- seconds (time to fall asleep) [planned — derived from sleep stage data]
sleep_score                 -- 0-100 (vendor-normalized)     [planned — vendor-specific]

// Activity
steps                       -- count                         [implemented]
active_calories             -- kcal                          [implemented]
active_minutes              -- minutes                       [implemented]
exercise_session            -- event (composite, payload below) [implemented — Health Connect]
distance                    -- meters                        [planned — GPS-capable adapters]
vo2_max                     -- mL/kg/min                     [planned — exercise-mode adapters]

// Motion & Gait
accelerometer_raw           -- m/s2 (x, y, z in raw_payload) [planned — motion analysis]
gait_symmetry               -- % (0 = asymmetric, 100 = sym) [planned — gait analysis]
tremor_amplitude            -- mm/s2                         [planned — neurological screening]

// Metabolic
blood_glucose               -- mg/dL                         [implemented]
glucose_variability         -- coefficient of variation %    [planned — derived from CGM data]
body_fat_pct                -- %                             [implemented — Withings scale]
body_mass                   -- kg                            [implemented — Withings scale]

// Stress & Recovery
eda_level                   -- microsiemens                  [planned — EDA-capable wearables]
stress_score                -- 0-100 (vendor-normalized)     [planned — vendor-specific]
recovery_score              -- 0-100 (vendor-normalized)     [implemented]
strain_score                -- 0-21 (WHOOP-style)            [planned — WHOOP adapter extension]

// Women's Health
basal_body_temperature      -- degrees C                     [implemented]
cycle_day                   -- int (day of menstrual cycle)  [planned — cycle tracking UI]
cycle_phase                 -- enum: menstrual | follicular | ovulatory | luteal [planned]

// Environment
ambient_light               -- lux                           [planned — phone sensor]
ambient_noise               -- dB                            [planned — microphone adapter]

// Epigenetic age clocks (slow-rolling, user-imported from labs like TruDiagnostic)
epigenetic_age_dunedin_pace -- ratio (1.0 = aging at normal rate)  [implemented — manual entry]
epigenetic_age_grim         -- years (biological age)              [implemented — manual entry]
epigenetic_age_pheno        -- years (biological age)              [implemented — manual entry]
epigenetic_age_horvath      -- years (biological age)              [implemented — manual entry]
```

#### Epigenetic age clocks — manifesto guard

The four clocks above are imported as standalone biomarkers and displayed
alongside HBA1C / ApoB / etc. in the lab-value surface. Bios **does not**
compose them into a derived "Bios biological age" or "pace of aging" score
— evaluating the person is explicitly out of scope. Owners see each clock's
reading on its own; interpretation is the lab's job, not Bios's.

**Summary:** 17 of 34 metric types are implemented. The remaining 17 are defined for future adapter expansion.

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

Field keys are part of the public contract — companions and the pattern
engine compile against them. Renames are breaking changes. New field keys
land in this document alongside the metric type they describe; treat them
with the same stability commitment as `MetricType.key` strings.

| Metric type | Field key | Typed column | Notes |
|---|---|---|---|
| `EXERCISE_SESSION` | `modality` | `string_value` | enum: `CARDIO`, `STRENGTH`, `INTERVAL`, `MOBILITY`, `OTHER` |
| `EXERCISE_SESSION` | `start_utc` | `long_value` | epoch ms |
| `EXERCISE_SESSION` | `end_utc` | `long_value` | epoch ms |
| `EXERCISE_SESSION` | `avg_hr_bpm` | `double_value` | nullable — mean of HR samples that fall inside the session window; omitted entirely when no samples land in window (null ≠ 0) |
| `EXERCISE_SESSION` | `rpe` | `long_value` | 1–10, nullable — only populated if the source captured it |

#### When to use the payload table

Use a payload row when **all** of:
1. The metric needs more than one structured value per reading.
2. Pattern-engine code or a companion reader needs to query individual
   fields (`WHERE modality = 'CARDIO' AND avg_hr_bpm > 140`).
3. Stuffing the data into `MetricReading.value` + a convention would
   require ad-hoc parsing on every read.

Do **not** use it for:
- Owner-private notes — those go in `LoggedEvent.note`.
- Vendor-specific raw blobs — those belong in the raw-payload field on
  `MetricReading` (debug surface, never read by the engine).
- Re-encoding the scalar that already fits in `value`.

### DataSource

Tracks where a reading came from.

```
DataSource {
    id:             UUID
    source_type:    SourceType          -- enum: health_connect | gadgetbridge | direct_sensor | oura_api | whoop_api | garmin_api | withings_api | dexcom_api | phone_sensor
    device_name:    String?             -- "Apple Watch Series 11", "Oura Ring Gen 4"
    device_model:   String?             -- vendor model identifier
    sensor_type:    SensorType          -- enum: optical_hr | ecg | accelerometer | thermistor | ppg_camera | cgm | ...
    connected_at:   DateTime (UTC)
    last_sync_at:   DateTime (UTC)
}
```

### ConfidenceTier (enum)

Used for deduplication and weighting in the Baseline Engine.

```
clinical        -- medical-grade device (e.g., chest-strap ECG, prescription CGM)
high            -- FDA-cleared consumer sensor (e.g., Apple Watch ECG, Dexcom Stelo)
medium          -- validated consumer sensor (e.g., optical wrist HR, ring SpO2)
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
    window_days:    Int                 -- rolling window used (default: 30)
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

Output of the Baseline Engine when a deviation is detected.

```
Anomaly {
    id:             UUID
    detected_at:    DateTime (UTC)
    metric_types:   MetricType[]        -- involved metrics (1 for single-signal, N for cross-correlation)
    deviation_scores: Map<MetricType, Float64>  -- z-score per metric
    combined_score: Float64             -- weighted multi-signal score
    pattern_match:  ConditionPattern?   -- matched known condition signature, if any
    severity:       AlertTier           -- enum: observation | notice | advisory | urgent
    title:          String              -- human-readable summary
    explanation:    String              -- what changed and why it matters
    suggested_action: String?           -- what the user can do
    readings:       MetricReading.id[]  -- linked raw readings
    acknowledged:   Bool
    acknowledged_at: DateTime?
}
```

### ConditionPattern

Reference definitions for known condition signatures (shipped with the app, updated OTA).

```
ConditionPattern {
    id:             UUID
    condition_name: String              -- e.g., "Potential infection onset"
    category:       ConditionCategory   -- enum: cardiovascular | respiratory | metabolic | sleep | mental_health | infectious | womens_health
    required_signals: SignalRule[]       -- what must be present
    confidence_level: String            -- from WEARABLES_AND_DETECTION.md research
    description:    String              -- medical context
    references:     String[]            -- published research citations
    version:        Int                 -- for OTA updates
}

SignalRule {
    metric_type:    MetricType
    direction:      enum: above | below | irregular
    threshold_sigma: Float64            -- deviation from baseline in std devs
    min_duration_hours: Int             -- how long must deviation persist
    weight:         Float64             -- contribution to combined score
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
| WHOOP `strain` | `strain_score` | 0-21 scale (none needed) | vendor_derived |
| Dexcom `egv.value` | `blood_glucose` | mg/dL (none needed) | high |
| Samsung Health `blood_pressure` | `blood_pressure_systolic` + `blood_pressure_diastolic` | mmHg (split into two readings) | medium |
| Phone accelerometer | `accelerometer_raw` | normalize to m/s2 | low |
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

Well within the 100 MB budget, leaving ample room for raw payloads and baselines.

---

## FHIR Mapping (for healthcare export)

| Bios Entity | FHIR Resource |
|---|---|
| MetricReading | Observation |
| DataSource | Device |
| PersonalBaseline | Observation (with baseline interpretation) |
| Anomaly | DetectedIssue / Flag |
| ConditionPattern | Library (definition) |

Export produces a FHIR Bundle that any EHR system can import.

---

## Relation to HPI (audit 2026-05-16)

[karlicoss/HPI](https://github.com/karlicoss/HPI) (Human Programming Interface) is the most architecturally similar project in the personal-data ecosystem. The data model here is deliberately divergent on one dimension and aligned on the others.

**Aligned principles:**

- **Local-first / offline** — both work against data on-device, no cloud dependency
- **Hide source-specific gore** — each Bios adapter and each HPI module encapsulates the messy parsing/locating/caching for one data source
- **Typed outputs** — Bios uses `MetricReading` (Kotlin dataclass); HPI uses per-module `NamedTuple`s
- **Defensive parsing** — Bios keeps `raw_payload` for debugging; HPI uses mypy-assisted error handling to surface partial failures without crashing
- **Extensible without forking** — Bios via new adapter, HPI via namespace packages

**Intentional divergence — source-agnostic vs. source-typed:**

HPI surfaces each source as its own typed function (`my.reddit.all.saved()` returns `Iterator[Saved]` specific to Reddit). The caller — a Python programmer — handles cross-source merging.

Bios is the opposite: **every source maps to the same `MetricReading` schema at ingest time**, with `DataSource` and `ConfidenceTier` carrying the attribution. End users (not programmers) need a single coherent view, normalization happens once, and FHIR export requires uniform units anyway. This is the right call for a consumer app on a battery-constrained device.

**Borrow from HPI:**

1. **Explicit per-metric merge layer.** HPI's `all.py` pattern (combine `google_takeout` + `gpslogger` + `via_ip` into `my.location`) is currently implicit in Bios via `ConfidenceTier` dedup. Consider an explicit per-`MetricType` merge specification so it's debuggable.
2. **Per-adapter failure isolation.** HPI's `@import_source` decorator catches per-source failures with a warning rather than crashing the whole package. Verify `IngestManager` has equivalent isolation per adapter (Oura down ≠ Health Connect broken).
3. **Directory-by-default for adapters.** HPI's lesson: convert `module.py` → `module/parser.py` *before* you need to add subfiles, because retrofit is painful. New ingest adapters should ship as `ingest/oura/` not `ingest/OuraAdapter.kt` if there's any chance they'll grow auth/sync/mapping subfiles.

**Future interop:** A Python HPI overlay module `my.bios` that reads Bios's exported data could expose the Bios corpus through HPI's entire ecosystem — `my.bios.heart_rate()` plugs into the same notebooks that work with Fitbit/Oura. Earmarked, not committed.
