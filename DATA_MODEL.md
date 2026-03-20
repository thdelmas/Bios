# Bios Unified Data Model

## Design Goals

1. **Source-agnostic** -- every wearable and phone sensor maps to the same schema
2. **Temporal** -- all data is time-series; every reading has a precise timestamp and duration
3. **Attributable** -- every reading tracks its source device, sensor type, and confidence
4. **Extensible** -- new metric types can be added without schema migrations
5. **Exportable** -- maps cleanly to FHIR Observation resources for healthcare interoperability

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

Organized by physiological domain. Each type has a canonical unit.

```
// Cardiovascular
heart_rate                  -- bpm
heart_rate_variability      -- ms (RMSSD)
resting_heart_rate          -- bpm
ecg_waveform                -- mV[] (raw waveform array)
blood_pressure_systolic     -- mmHg
blood_pressure_diastolic    -- mmHg
blood_oxygen                -- % (SpO2)

// Respiratory
respiratory_rate            -- breaths/min
respiratory_event           -- apnea/hypopnea event flag
cough_count                 -- count per hour

// Temperature
skin_temperature            -- degrees C
skin_temperature_deviation  -- delta C from personal baseline

// Sleep
sleep_stage                 -- enum: awake | light | deep | rem
sleep_duration              -- seconds
sleep_latency               -- seconds (time to fall asleep)
sleep_score                 -- 0-100 (vendor-normalized)

// Activity
steps                       -- count
active_calories             -- kcal
active_minutes              -- minutes
distance                    -- meters
vo2_max                     -- mL/kg/min

// Motion & Gait
accelerometer_raw           -- m/s2 (x, y, z stored in raw_payload)
gait_symmetry               -- % (0 = perfectly asymmetric, 100 = symmetric)
tremor_amplitude            -- mm/s2

// Metabolic
blood_glucose               -- mg/dL
glucose_variability         -- coefficient of variation %
body_fat_percentage         -- %
body_mass                   -- kg

// Stress & Recovery
eda_level                   -- microsiemens (electrodermal activity)
stress_score                -- 0-100 (vendor-normalized)
recovery_score              -- 0-100 (vendor-normalized)
strain_score                -- 0-21 (WHOOP-style, normalized)

// Women's Health
basal_body_temperature      -- degrees C
cycle_day                   -- int (day of menstrual cycle)
cycle_phase                 -- enum: menstrual | follicular | ovulatory | luteal

// Environment
ambient_light               -- lux
ambient_noise               -- dB
```

### DataSource

Tracks where a reading came from.

```
DataSource {
    id:             UUID
    source_type:    SourceType          -- enum: healthkit | health_connect | oura_api | whoop_api | garmin_api | ...
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
