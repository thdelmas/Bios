# Bios Tech Stack

## Decision Framework

Choices are driven by Bios's core constraints:
1. **Privacy-first** -- on-device processing, encrypted local storage
2. **LETHE-primary, Android-portable** -- embedded on LETHE (OSmosis privacy-hardened ROM), runs on any Android 9+
3. **No Play Services dependency** -- must work on degoogled ROMs (LETHE, LineageOS, CalyxOS, GrapheneOS)
4. **Battery-efficient** -- background health processing must be lightweight
5. **Small team** -- maximize code sharing, minimize maintenance surface

---

## Mobile App

### Approach: LETHE-primary, Android-portable

**Android: Kotlin + Jetpack Compose**

**Primary target: LETHE** (OSmosis privacy-hardened Android overlay, LineageOS 22.1 base):
- Embedded as a system app via OSmosis overlay (`apply-overlays.sh`)
- Deep integration with LETHE agent (local API on `localhost:8080`), Void launcher (health cards), and OTA system
- Inherits OS-level privacy hardening: firewall, tracker blocking, burner mode, dead man's switch
- No Play Services, no Google Analytics, no cloud dependency — matches LETHE's degoogled philosophy

**Portable to stock Android (API 28–35):**
- Runs as a standalone APK with no LETHE dependencies
- Platform-specific behavior abstracted via `platform/` interfaces (see ARCHITECTURE.md)
- Health Connect used on Android 14+; direct sensor adapters on older versions
- No Google Play Services required (but tolerates their presence on stock ROMs)

**Build flavors:**
- `lethe`: System app, LETHE agent IPC, launcher integration, system-level sensor access
- `standalone`: Standard APK, Health Connect or direct sensors, no LETHE dependencies

**Why native over cross-platform (React Native / Flutter):**
- Health Connect and direct sensor APIs are deeply platform-specific
- Background processing (WorkManager), sensor access, and notifications work best natively
- On-device ML via TensorFlow Lite integrates natively
- Health apps demand the performance and battery efficiency of native code
- LETHE system app integration requires native Android APIs

**iOS (future):** If/when iOS is needed, build with Swift + SwiftUI using the same architecture. Shared logic can be extracted via Kotlin Multiplatform (KMP).

---

## Local Data Store

### Room + SQLCipher

**Why Room:**
- Official Android persistence library with compile-time query verification
- Excellent coroutine/Flow support for reactive UI
- Built-in migration framework
- Battle-tested on billions of devices

**Why SQLCipher:**
- AES-256 full-database encryption at rest (privacy requirement)
- Drop-in replacement for Android SQLite via SupportFactory
- Open source, audited
- Compatible with standard SQLite tooling

**Encryption key:** Stored in Android Keystore (stock) or LETHE hardware-backed keystore (embedded). No GMS dependency for key storage.

**Schema migrations:** Room versioned migrations, run at app startup.

---

## On-Device ML

### Statistical detection (active)

Rolling mean, standard deviation, z-scores, exponential moving averages. 12 condition patterns with 33 signal rules (24 literature-backed). Implemented in Kotlin, no ML framework needed.

### LiteRT (Google's rebranded TensorFlow Lite)

- Anomaly detection model wrapper implemented (`TFLiteAnomalyModel.kt`)
- Heuristic fallback active when model asset not present
- Models shipped as .tflite bundles, updated OTA via `ModelUpdateManager`
- Inference runs on-device, no network required

### Federated learning (implemented)

- `FederatedTrainer` computes on-device gradients from owner's feedback-labeled anomalies
- Differential privacy noise (Laplace, epsilon=1.0) applied before export
- Encrypted gradient transmission to server for secure aggregation
- Owner opts in/out independently; opting out doesn't reduce features

### Model training (offline, not on-device)

- Python + scikit-learn + PyTorch for model development
- Training on synthetic and anonymized research datasets
- Export pipeline: PyTorch -> ONNX -> TFLite
- Model signing: Ed25519 signatures verified on-device before installation

---

## Backend (Opt-In Services)

Minimal backend, only for features that require a server.

### Runtime: Go

**Why Go:**
- Fast, small binaries, low memory footprint
- Excellent concurrency for handling sync connections
- Simple deployment (single static binary, no runtime)
- Strong standard library for HTTP, crypto, JSON
- Easy to hire for and maintain

### API Framework: Standard library `net/http` + chi router

No heavy frameworks. The backend is intentionally simple.

### Database: PostgreSQL

- For: user accounts, device registrations, anonymized aggregates, model version metadata
- NOT for: raw health data (that stays on-device)

### Object Storage: S3-compatible (model artifacts, encrypted sync blobs)

### Infrastructure: Containerized (Docker), deployable to any cloud or self-hosted

**Hosting (initial):** Fly.io or Railway for simplicity. Migrate to AWS/GCP if scale demands it.

---

## Authentication

### Device-level: Biometric / device credentials (app access)

### Account-level (for opt-in sync): Passkey-first

- WebAuthn / passkeys as primary auth (no passwords)
- Fallback: email magic link
- No OAuth social login (minimize third-party data exposure)
- JWTs for API session management, short-lived (15 min) with refresh tokens

---

## Networking

### Wearable API integrations: OAuth 2.0 + REST

Each third-party API (Oura, WHOOP, Garmin, Withings, Dexcom, Polar) uses OAuth 2.0 authorization code flow. Tokens stored in Android Keystore.

### Sync protocol: E2E encrypted, user-keyed

- Client encrypts data with a key derived from user's passkey
- Server stores and relays ciphertext only
- Protocol: HTTPS + chunked upload/download
- Conflict resolution: last-writer-wins per entity (health data is append-only, so conflicts are rare)

---

## Testing

| Layer | Tool |
|-------|------|
| Unit tests | JUnit 5 + Kotest |
| UI tests | Compose Testing + Espresso |
| Backend tests | Go `testing` package |
| Integration tests | Synthetic data generators that simulate wearable data patterns |
| Anomaly detection validation | Ground-truth dataset of labeled healthy/sick periods |

---

## CI/CD

| Concern | Tool |
|---------|------|
| Android builds | GitHub Actions |
| Backend builds | GitHub Actions |
| Internal testing | Firebase App Distribution |
| Model artifact delivery | GitHub Releases or S3 bucket with versioned manifests |

---

## Monitoring & Analytics

### App analytics: PostHog (self-hostable, privacy-friendly)

- Feature usage, retention, crash-free rate
- No third-party analytics SDKs that phone home (no Firebase Analytics, no Mixpanel)
- All analytics are opt-in and contain zero health data

### Backend monitoring: Prometheus + Grafana

- API latency, error rates, sync throughput
- Deployed alongside the backend

### Crash reporting: Sentry (self-hostable)

- Symbolicated crash reports
- No health data in crash payloads (enforced via scrubbing rules)

---

## Repository Structure

```
bios/
  android/                -- Android Studio project
    app/src/main/java/com/bios/app/
      model/              -- Data classes and enums (17 metric types, 9 source types)
      data/               -- Room databases (main + reproductive), DAOs
      platform/           -- LETHE integration (PlatformDetector, LetheCompat, DataDestroyer,
                             SafeMode, HealthApiServer, OtaCoordinator, ForensicRiskMonitor)
      ingest/             -- 9 adapters (HealthConnect, Gadgetbridge, DirectSensor, Oura,
                             WHOOP, Garmin, Withings, Dexcom, PhoneSensor), IngestManager, SyncWorker
      engine/             -- BaselineEngine, AnomalyDetector, TFLiteAnomalyModel, Statistics
      federated/          -- FederatedTrainer, ModelUpdateManager
      alerts/             -- 12 ConditionPatterns, AlertManager, AlertContentPolicy,
                             BiomarkerReference, DailyDigestWorker, FollowUpWorker
      privacy/            -- CommunityAggregator, ContributionWorker, PopulationHealthSignals,
                             ResearchPipeline
      export/             -- DataExporter, EncryptedExporter, FhirExporter
      sync/               -- SyncProtocol, SyncManager (E2E encrypted multi-device)
      ui/                 -- 11 Jetpack Compose screens
        home/ trends/ alerts/ settings/ onboarding/
        diagnostics/ timeline/ journal/ privacy/ reference/
        components/ theme/
    app/src/test/         -- Unit tests (~20 test files)
  backend/                -- Go backend (sync gateway, community, research, models)
    cmd/gateway/          -- Application entry point
    internal/
      api/                -- HTTP handlers + middleware
      auth/               -- Passkey/JWT authentication
      model/              -- Data structures
      store/              -- PostgreSQL (users, sync, contributions, signals, models)
  docs/                   -- Architecture, roadmap, investigation
  scripts/                -- Code quality checks, tooling
```

---

## Summary

| Layer | Choice | Key Reason |
|-------|--------|------------|
| Android app | Kotlin + Jetpack Compose | Best Health Connect integration, LETHE system app support, broadest reach |
| Primary platform | LETHE (LineageOS 22.1 overlay) | Privacy-hardened OS, degoogled, system-level integration |
| Portability | Stock Android API 28–35 | Covers 95%+ active devices, no Play Services required |
| Local DB | Room + SQLCipher | Encrypted, compile-time verified, fast time-series |
| On-device ML | LiteRT + federated learning | Native acceleration, on-device gradients, no network needed |
| Backend | Go + PostgreSQL | Simple, fast, small surface area |
| Auth | Passkeys + magic link | No passwords, privacy-first |
| CI/CD | GitHub Actions + Xcode Cloud | Standard, reliable |
| Analytics | PostHog (self-hosted) | Privacy-friendly, no data leakage |
