# Bios Consumer API

Interface for companion apps (e.g. W2F) that consume Bios health data via
`BiosHealthProvider`.

**Authority:** `com.bios.app.health`

## Access

Two-layer gate (Phase 7, Option C — per-app keystores + owner consent):

1. **OS permission.** Companion app declares one or both of:
   - `com.bios.app.permission.READ_HEALTH` (dangerous)
   - `com.bios.app.permission.WRITE_COMPANION` (dangerous)

   These are `dangerous` permissions, so on first use Android shows a runtime
   prompt. The companion does **not** need to be signed by the Bios key.

2. **Owner allowlist.** On the first call from any package, Bios records the
   caller as `PENDING` and denies the request. The owner reviews and approves
   the companion in **Bios → Settings → Companion Apps**. Approved companions
   pass; revoked companions are denied with `SecurityException` on writes and
   `null` cursors on reads.

This design intentionally allows third-party companions (a shared signing key
is **not** required) while keeping the owner in final control of every
connection.

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

The writable keys are **per-package** — each companion may only write keys
allocated to its `applicationId`. Cross-package writes throw `SecurityException`
(an approved Smokeless install cannot write `mood_drift_score`, etc.). The
source of truth is `provider.CompanionContract.WHITELIST_BY_PACKAGE` in the
Android source.

Current allocations:

| Companion | `applicationId` | Writable keys |
|---|---|---|
| W2F | `com.w2f.app` | `typing_cadence`, `circadian_phase_shift`, `mood_drift_score` |
| Smokeless | `com.smokless.smokeless` | `tobacco_use`, `tobacco_craving`, `cannabis_use`, `cannabis_craving` |
| Virgil | `com.virgil.app` | `fall_event`, `near_miss_fall`, `check_in_miss` |

`ContentValues`:
- `value` (Double) — required
- `timestamp` (Long, epoch ms) — optional, defaults to `now`

### Signing-cert pin (standalone flavor)

On the standalone flavor, package-name equality is not enough — any sideloaded
APK can declare `<application android:packageName="com.w2f.app">`. Each
companion's release signing cert SHA-256 is pinned alongside its package name
in `provider.CompanionContract.PACKAGES.signingCertSha256`. Writes from a
matching package name with a non-matching cert are rejected with
`SecurityException`.

On the LETHE flavor the pin is bypassed: companions are pre-installed system
apps signed by the platform key, which is the trust anchor.

**Migration window.** Until a companion publishes a stable release-signing
key, its `signingCertSha256` is `emptySet()`. In that state the package-name
check stands alone (current behavior). Once populated, impersonators are
rejected at insert time.

#### Cert-rotation procedure

When a companion ships its first signed release, or rotates its key, the new
SHA-256 must be added to its `Companion` entry in `CompanionContract.kt`:

1. **Compute the SHA-256.** From the signed release APK:
   ```sh
   apksigner verify --print-certs companion-release.apk \
     | grep "SHA-256 digest" \
     | head -1 | awk '{print $NF}' | tr -d ':' | tr 'A-Z' 'a-z'
   ```
   Or from a keystore:
   ```sh
   keytool -list -keystore release.keystore -alias <alias> \
     | grep "SHA256" | awk '{print $2}' | tr -d ':' | tr 'A-Z' 'a-z'
   ```

2. **Add to the pinned set.** Edit the companion's `signingCertSha256` in
   `CompanionContract.kt`:
   ```kotlin
   signingCertSha256 = setOf(
       "abc123...new-release-key-sha-here...",
   )
   ```

3. **Key rotation.** When a companion rotates its release key, keep the *old*
   SHA in the set for one Bios release cycle. The set tolerates multiple SHAs
   so users running an older companion version don't lose write access while
   they update:
   ```kotlin
   signingCertSha256 = setOf(
       "old-key-sha-256",  // remove in Bios N+2
       "new-key-sha-256",
   )
   ```

4. **Ship Bios, then ship companion.** A Bios release with the new SHA must
   reach users before the companion release signed with the new key, otherwise
   the companion's writes will start failing on devices that haven't updated
   Bios yet.

## First-connection notification

The first time any unknown package calls the provider, Bios fires a
notification on the **Companion approvals** channel and records the package
as PENDING. Tapping the notification deep-links into Settings → Companion
Apps for review. Without this, an unapproved companion would sit silently
denied with no surface for the owner to act on.

## Metric keys

The canonical string keys Bios stores and accepts. All are lowercase with
underscores, case-sensitive.

**Cardiovascular**: `heart_rate`, `heart_rate_variability`, `resting_heart_rate`,
`heart_rate_before_sleep`, `blood_pressure_systolic`, `blood_pressure_diastolic`,
`blood_oxygen`

**Respiratory**: `respiratory_rate`

**Temperature**: `skin_temperature`, `skin_temperature_deviation`

**Sleep**: `sleep_stage`, `sleep_duration`

**Activity**: `steps`, `active_calories`, `active_minutes`

**Metabolic**: `blood_glucose`

**Recovery**: `recovery_score`

**Women's Health**: `basal_body_temperature`

**Mental Health (W2F-writable)**: `typing_cadence`,
`circadian_phase_shift`, `mood_drift_score`

**Intake (Smokeless-writable)**: `tobacco_use`, `tobacco_craving`,
`cannabis_use`, `cannabis_craving`

**Safety (Virgil-writable)**: `fall_event`, `near_miss_fall`, `check_in_miss`

The source of truth is `MetricType` in
[`android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt`](../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt).

## Contracts forward/backward compatibility

`MetricType`, `MetricUnit`, and `MetricDomain` ship from the `bios-contracts`
AAR. Companions (W2F, Smokeless, Virgil, Fil) pin a version of that AAR and
release on their own cadence, so at any given moment some companion will be
running against an older — or newer — contracts version than the installed
Bios. The data plane already tolerates this by accident
(`MetricReading.metricType` is a `String` end-to-end and `MetricType.fromKey`
is nullable), but the rules below make the contract explicit.

### Stability commitments (Bios side)

1. **Keys are forever.** Once a `MetricType.key` string ships in a released
   `bios-contracts` artifact, it is never deleted and never repurposed.
2. **Domain and unit on an existing key never change.** Renaming
   `blood_glucose` from `MG_PER_DL` to `MMOL_PER_L`, or moving `circadian_phase_shift`
   from `SLEEP` to `MENTAL_HEALTH`, would silently corrupt every downstream
   chart. Add a new key instead.
3. **Deprecation is in-place.** When a key falls out of favor, annotate the
   enum entry with `@Deprecated` and leave the entry in the enum so
   `fromKey()` keeps resolving it. Document the replacement in the
   `@Deprecated` message.
4. **Removal is a major-version break.** Actually removing a `MetricType`
   entry forces every companion to publish a paired release. Bump the
   `bios-contracts` major version and coordinate releases.

### Companion-side rules

1. **Never call `MetricType.valueOf(...)` on a string from the provider.**
   It throws `IllegalArgumentException` on any key your contracts version
   doesn't know about — including keys Bios shipped after your last build.
2. **Use `MetricType.fromKey(key)` and handle `null`.** The contract is that
   unknown keys return `null`. The correct response is "skip this row" — not
   crash, not surface as an error to the owner. Anything that reads the
   `metric_type` column must tolerate strings outside its enum.
3. **Never enumerate `MetricType.entries` and assume it covers the provider's
   output.** A newer Bios may emit keys your build has never heard of. Filter
   on the keys you actually care about; treat the rest as inert.
4. **Writes to unknown keys silently no-op.** `CompanionContract.canWrite`
   rejects keys it doesn't recognize, so a companion that ships a new write
   key before the matching Bios release will see its inserts return `null`
   URIs with no exception. Read `/status/{key}` first and gate the write
   path on a non-empty cursor.

### Rotation flow

A companion can release independently of Bios as long as it follows the
read-before-write rule and tolerates `fromKey() == null`. Concretely:

- **Reading new keys before Bios ships them.** `/status/{key}` returns an
  empty cursor for keys Bios doesn't know. Treat that as "this signal is not
  available on this Bios version yet" and hide the UI path.
- **Reading new keys after Bios shipped them.** If your contracts version is
  older than Bios, `fromKey()` returns `null` for the new strings. Same
  handling — skip the row.
- **Writing new keys.** Bios must ship support first (key added to
  `MetricType` and to `CompanionContract.WHITELIST_BY_PACKAGE`), then the
  companion may publish a release that writes that key. Reverse order leaves
  the companion writing to a closed door.

### Versioning hygiene

- The `bios-contracts` AAR is published with each Bios release. Add-only
  changes (new key, new permission constant) bump the patch version; renames
  or removals bump the major version (see "Stability commitments").
- Companions pin a minimum-supported `bios-contracts` version in their
  `build.gradle.kts`. The pinned version is the *contracts the companion
  was built against* — at runtime it may talk to a Bios that ships a newer
  contracts version, which is fine because of the rules above.
- The contracts module includes a snapshot test
  (`MetricTypeKeysSnapshotTest`) that fails when a previously-published key
  disappears from the enum, even if its enum constant was renamed. Adding
  `@Deprecated` to an entry still leaves the key resolvable and passes the
  test; deleting the entry fails it. Treat a snapshot-test failure as a
  required code review with the companion maintainers, not a test to fix
  by editing the snapshot.
