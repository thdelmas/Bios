# Bios Tech Stack

## Decision Framework

Choices are driven by Bios's core constraints:
1. **Privacy-first** -- on-device processing, encrypted local storage
2. **Android-first** -- Health Connect ecosystem, broadest device reach
3. **Battery-efficient** -- background health processing must be lightweight
4. **Small team** -- maximize code sharing, minimize maintenance surface

---

## Mobile App

### Approach: Android-native

**Android: Kotlin + Jetpack Compose**

**Why native over cross-platform (React Native / Flutter):**
- Health Connect has deep, platform-specific APIs that are painful to bridge
- Background processing (WorkManager), sensor access, and notifications work best natively
- On-device ML via TensorFlow Lite integrates natively
- Health apps demand the performance and battery efficiency of native code

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

**Encryption key:** Stored in Android Keystore, protected by device credentials.

**Schema migrations:** Room versioned migrations, run at app startup.

---

## On-Device ML

### Phase 1: Pure statistics (no ML framework needed)

Rolling mean, standard deviation, z-scores, exponential moving averages. Implemented in plain Swift/Kotlin. No dependencies.

### Phase 2: TensorFlow Lite

- Anomaly detection models (isolation forest, autoencoder) trained offline
- Exported as TFLite (.tflite) bundles
- Shipped with the app, updated via OTA model delivery
- Inference runs on-device, no network required

### Model training (offline, not on-device)

- Python + scikit-learn + PyTorch for model development
- Training on synthetic and anonymized research datasets
- Export pipeline: PyTorch -> ONNX -> TFLite

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
      model/              -- Data classes and enums
      data/               -- Room database, DAOs
      ingest/             -- Health Connect adapter, IngestManager, SyncWorker
      engine/             -- Baseline calculator, anomaly detector, statistics
      alerts/             -- Condition patterns, AlertManager
      privacy/            -- Community aggregator, ContributionWorker
      export/             -- JSON data export
      ui/                 -- Jetpack Compose screens
        home/
        trends/
        alerts/
        settings/
        components/
        theme/
    app/src/test/         -- Unit tests
  backend/                -- Go backend (Phase 2+)
    cmd/server/
    internal/
      sync/
      models/
      auth/
      aggregates/
  ml/                     -- Model training and export
    notebooks/
    training/
    export/
  docs/                   -- Architecture, data model, etc. (current files)
  scripts/                -- Synthetic data generators, tooling
```

---

## Summary

| Layer | Choice | Key Reason |
|-------|--------|------------|
| Android app | Kotlin + Jetpack Compose | Best Health Connect integration, broadest reach |
| Local DB | Room + SQLCipher | Encrypted, compile-time verified, fast time-series |
| On-device ML | TensorFlow Lite | Native acceleration, no network needed |
| Backend | Go + PostgreSQL | Simple, fast, small surface area |
| Auth | Passkeys + magic link | No passwords, privacy-first |
| CI/CD | GitHub Actions + Xcode Cloud | Standard, reliable |
| Analytics | PostHog (self-hosted) | Privacy-friendly, no data leakage |
