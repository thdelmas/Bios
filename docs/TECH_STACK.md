# Bios Tech Stack

## Constraints

1. **On-device.** All processing and storage happen on the phone. There is no Bios server.
2. **LETHE first, Android portable.** Built for LETHE (LineageOS-based overlay), runs on any Android 9+.
3. **No Play Services.** Must work on degoogled ROMs.
4. **Battery.** Background work is periodic and batched.
5. **Small team.** One codebase, few dependencies.

---

## App

**Kotlin + Jetpack Compose**, native Android. `compileSdk` and `targetSdk` 35, `minSdk` 28.

Why native: Health Connect, sensors, WorkManager, foreground services, a ContentProvider with its own permissions and LETHE integration are all platform APIs.

**Modules** (`android/settings.gradle.kts`):

| Module | Content |
|---|---|
| `:app` | The application |
| `:bios-contracts` | Metric keys, units, domains, the provider contract, permissions and intent actions. Companion apps compile against it. A snapshot test guards against removing a key. |

**Build flavors** (dimension `platform`): `standalone` (published) and `lethe` (application id suffix `.lethe`). They share all source; LETHE behaviour is selected at runtime by `PlatformDetector`.

## Libraries

| Concern | Library |
|---|---|
| UI | Jetpack Compose (Material 3), Navigation Compose, Lifecycle ViewModel |
| Database | Room 2.6 over SQLCipher (`net.zetetic:sqlcipher-android`) |
| Key storage | AndroidX Security Crypto (`EncryptedSharedPreferences`, Keystore-backed master key) |
| Background work | WorkManager |
| Wearable data | Health Connect client |
| HTTP (vendor APIs only) | OkHttp |
| Camera capture | CameraX |
| Lab report OCR | Tesseract (`tesseract4android`), on-device |
| Export encryption | Zip4j (AES-256 zip) |
| Serialization | kotlinx.serialization, `org.json` |
| Async | Kotlin coroutines and Flow |

No analytics SDK, no crash reporter, no advertising library, no Google Play Services.

## Local Data Store

Room with SQLCipher: AES-256 full-database encryption, compile-time checked queries, versioned migrations run at startup. Two databases (main and reproductive), each with its own passphrase held in `EncryptedSharedPreferences`. See [DATA_MODEL.md](DATA_MODEL.md) and [PRIVACY_ARCHITECTURE.md](PRIVACY_ARCHITECTURE.md).

## Computation

Plain Kotlin in `engine/`: rolling mean, standard deviation, percentiles, z-scores, a fixed deviation-score table, band-pass filtering and peak detection for camera PPG, actigraphy rules for phone sleep. No ML framework and no trained model ship with the app.

## Networking

- **Vendor APIs** (Oura, WHOOP, Garmin, Withings, Dexcom, Polar, COROS): HTTPS from the phone to the vendor, with the token or sign-in the owner provided, stored in `EncryptedSharedPreferences`.
- **Loopback** (LETHE only): an HTTP server on `127.0.0.1:8080` for the LETHE agent, and a status post to the agent on loopback.
- **Device-to-device sync**: code in `sync/` with a local-network transport and a stub for an Iroh node. Off; no screen enables it.

Nothing else opens a connection.

## Possible later: raw biosensor boards

If Bios adds direct support for raw biosensor boards (OpenBCI Ganglion/Cyton, Muse, dry-ECG rigs), the path is **brainflow** ([brainflow-dev/brainflow](https://github.com/brainflow-dev/brainflow)) through its Java binding instead of a custom protocol implementation. It is MIT-licensed, has Android NDK CI and a uniform SDK across boards. The NDK wiring is not done and nothing depends on it today.

## Testing

| Layer | Tool |
|-------|------|
| Unit tests | JUnit 4 |
| Tests that need Room or Android resources | Robolectric |
| FHIR output | HAPI FHIR R4 parser and JSON Schema validation (test-only dependencies) |
| Shown text | `VocabularyGateTest` over string resources and owner-facing string literals |
| Contract | `MetricTypeKeysSnapshotTest` in `:bios-contracts` |
| Static analysis | detekt with a per-module baseline; Android lint |

## CI/CD

| Workflow | Does |
|---|---|
| `ci.yml` | `scripts/code-quality-check.sh` (file length, secrets, lint, detekt, build, tests), debug build, dependency vulnerability scan, gitleaks |
| `leakcheck.yml` | Fails on a reference to anything outside the repository |
| `cross-repo-contracts.yml` | Builds companion apps against the current `bios-contracts` |
| `release-please.yml` | Keeps the release PR, version bump and changelog |
| `release.yml` | Builds, signs and attaches the standalone APK on a version tag |

The same checks run locally from `.githooks/` (install with `./scripts/install-hooks.sh`).

## Repository Structure

```
android/
  app/src/main/java/com/bios/app/
    model/      -- Room entities and enums
    data/       -- databases, migrations, repositories, dao/
    ingest/     -- source adapters, deduplication, sync worker, phone sleep collection
    engine/     -- baselines, deviation score, derivations, PPG signal processing
    alerts/     -- the deviation alert, wording, notifications, source liveness
    export/     -- JSON, CSV, FHIR, PDF export; FHIR import
    labocr/     -- lab report OCR
    provider/   -- BiosHealthProvider and the companion gate
    platform/   -- LETHE integration, wipe, loopback API
    sync/       -- device-to-device sync (off)
    config/     -- region configuration (unit display, data-handling rules)
    i18n/       -- language preference
    ui/         -- Compose screens, components, theme
  app/src/test/ -- unit tests
  bios-contracts/ -- shared contract module
docs/           -- architecture, data model, specs, design system
scripts/        -- quality checks, hook installer, a FHIR export report script
.githooks/      -- pre-commit, commit-msg, pre-push, leak check
```
