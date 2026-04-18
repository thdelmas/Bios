# Bios Consumer API

Interface for companion apps (e.g. W2F) that consume Bios health data via
`BiosHealthProvider`.

**Authority:** `com.bios.app.health`
**Access:** signature-level permission — only apps signed with the same key can
read or write.

## Contract

- The provider is **passive**: it serves whatever `SyncWorker` has already
  written to the local database. Queries do **not** trigger ingestion.
- `SyncWorker` runs on a periodic schedule (default ~15 minutes) when
  Health Connect permissions are granted and a data source is available.
- If Bios is installed but its permissions are missing or no source is active,
  every `readings/*` query returns an empty cursor. Use `/status` to
  disambiguate "empty" from "lagging."

## Read URIs

### `content://com.bios.app.health/readings/{metric_type}`

Returns raw `MetricReading` rows for one metric within a time window.

Query parameters:
- `start` (Long, epoch ms) — inclusive, defaults to `0`
- `end` (Long, epoch ms) — inclusive, defaults to `now`

Cursor columns:

| column | type | notes |
|---|---|---|
| `id` | String | stable reading id |
| `metric_type` | String | see metric keys below |
| `value` | Double | |
| `timestamp` | Long | epoch ms |
| `duration_sec` | Long | 0 for instantaneous samples |
| `source_id` | String | which `DataSource` wrote this |
| `confidence` | Int | `ConfidenceTier.level` |
| `is_primary` | Int | 1 for primary observations, 0 for derived |

### `content://com.bios.app.health/baselines[/{metric_type}]`

Returns `PersonalBaseline` rows — either all baselines, or just the one for a
given metric.

Cursor columns: `metric_type`, `context`, `window_days`, `computed_at`,
`mean`, `std_dev`, `p5`, `p95`, `trend`, `trend_slope`.

### `content://com.bios.app.health/status[/{metric_type}]`

Per-metric ingestion state. Lets consumers decide per-metric whether to use
Bios or fall back to another source.

- `GET /status` always returns one row per known metric type, even when that
  metric has no readings (columns report zeros). The row set is therefore
  stable across devices.
- `GET /status/{metric_type}` returns one row, or an empty cursor if the key
  is unknown to Bios.

Cursor columns:

| column | type | notes |
|---|---|---|
| `metric_type` | String | |
| `last_ingested_at` | Long | epoch ms, `0` if never ingested |
| `reading_count_24h` | Int | rows with `timestamp >= now - 24h` |
| `reading_count_total` | Int | lifetime count in Bios' local DB |

Suggested consumer logic:

```kotlin
val cursor = resolver.query(Uri.parse("content://com.bios.app.health/status/steps"), …)
cursor.moveToFirst()
val last = cursor.getLong(cursor.getColumnIndexOrThrow("last_ingested_at"))
val fresh24h = cursor.getInt(cursor.getColumnIndexOrThrow("reading_count_24h"))

val useBios = last != 0L && fresh24h > 0
```

## Write URI (companion signals)

### `content://com.bios.app.health/companion/{metric_type}`

Accepts only the three MENTAL_HEALTH-domain signals:
`typing_cadence`, `circadian_phase_shift`, `mood_drift_score`. Any other
metric key throws `SecurityException`.

`ContentValues`:
- `value` (Double) — required
- `timestamp` (Long, epoch ms) — optional, defaults to `now`

## Metric keys

The canonical string keys Bios stores and accepts. All are lowercase with
underscores, case-sensitive.

**Cardiovascular**: `heart_rate`, `heart_rate_variability`, `resting_heart_rate`,
`blood_pressure_systolic`, `blood_pressure_diastolic`, `blood_oxygen`

**Respiratory**: `respiratory_rate`

**Temperature**: `skin_temperature`, `skin_temperature_deviation`

**Sleep**: `sleep_stage`, `sleep_duration`

**Activity**: `steps`, `active_calories`, `active_minutes`

**Metabolic**: `blood_glucose`

**Recovery**: `recovery_score`

**Women's Health**: `basal_body_temperature`

**Mental Health (companion-writable)**: `typing_cadence`,
`circadian_phase_shift`, `mood_drift_score`

The source of truth is `MetricType` in
[`android/app/src/main/java/com/bios/app/model/Enums.kt`](../android/app/src/main/java/com/bios/app/model/Enums.kt).
