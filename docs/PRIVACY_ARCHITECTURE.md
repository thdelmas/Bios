# Bios Privacy Architecture

## Core Principle

**Bios exists to protect its owner. Their health. Their data. Their autonomy over both.**

This is not a privacy policy — it is an architectural constraint. Every design decision flows from one question: *does this protect the person holding the device?*

- Health data stays on-device because that protects the owner from data brokers, insurers, advertisers, and legal compulsion
- Detection runs on-device because that protects the owner from cloud outages, surveillance, and vendor lock-in
- Alerts are factual, never judgmental, because that protects the owner's autonomy and mental health
- The owner decides what leaves the device — nothing does by default

### Alignment with LETHE

On LETHE, Bios is not an app running on a phone — it is part of the phone's immune system. LETHE protects the owner's digital life; Bios protects the owner's physical life. Same guardian philosophy, same threat model, same answer to conflicting interests: **the owner wins.**

| LETHE Principle | Bios Implementation |
|---|---|
| The user is final | Owner controls alert thresholds, data retention, what to share, and when to delete — Bios advises, never overrides |
| Defense by encryption and erasure | SQLCipher AES-256, hardware-backed keystore, instant key destruction, LETHE burner/dead-man's-switch integration |
| Never evaluate the person | Report deviations from baseline ("resting HR +2σ for 48h"), never lifestyle judgments ("you're unhealthy") |
| Silence is a feature | Bios speaks when something matters, not to fill a feed or drive engagement |
| No hidden agendas | No behavioral nudges, no engagement metrics, no data sold to anyone, no dark patterns |
| Each instance is sovereign | Health data belongs to this device and this owner — no cross-device aggregation without explicit opt-in |
| Presence, not performance | Guardian at rest, watching for patterns the owner might miss — not a productivity tool |

### Privacy Tiers

Every user gets the same health intelligence. How your data contributes back is your choice.

- **Private tier:** Your health data never leaves your device. Period. Zero-knowledge architecture. This is the same model a traditional privacy-first app would offer.
- **Community tier:** Your health data is processed entirely on-device. Before anything is transmitted, it is aggregated into anonymized statistical patterns using differential privacy. The server never sees raw readings, never sees anything tied to your identity. What it receives are pre-anonymized population-level signals that help improve detection for all users.

Both tiers run the same app, the same detection engine, the same features. The difference is whether your anonymized patterns help improve the commons.

---

## Threat Model

### What we protect

- Raw health readings (HR, HRV, SpO2, sleep, glucose, etc.)
- Personal baselines and computed aggregates
- Anomaly detections and alert history
- Source device information
- Reproductive health data (cycle tracking, fertility indicators)
- Any data that could reveal health status, behavior patterns, or identity
- The owner's autonomy over their health information

### Who we protect against

| Threat Actor | Attack Vector | Bios Defense | + LETHE Defense |
|---|---|---|---|
| **Bios (ourselves)** | Server-side data access | Zero-knowledge backend; server only sees ciphertext or differentially private aggregates | N/A — same |
| **Data brokers / insurers** | Purchasing health data from app vendors | No data to sell — everything is on-device | OS-level tracker blocking, no Play Services telemetry |
| **Advertisers / SDKs** | Analytics tools exfiltrating health data | No third-party analytics SDKs with health data access; self-hosted tooling | System-wide firewall blocks all outbound by default; hosts-file tracker blocking |
| **Device theft / seizure** | Physical access to phone | SQLCipher AES-256 encryption keyed to device credentials | Burner mode wipes on boot; panic button (5x power press); dead man's switch escalation |
| **Forensic examination** | Recovering data from seized device | Encrypted DB; key destruction on "Delete all data" | dm-default-key metadata encryption; burner mode prevents data accumulation; Stage 3 brick option |
| **Network interception** | MITM, ISP surveillance | E2E encryption on sync; TLS defense-in-depth | All traffic through Tor; per-app circuit isolation; no cleartext DNS |
| **Legal compulsion** | Subpoena for user data | We cannot produce what we do not have (zero-knowledge server) | No server data + device encryption + duress PIN triggers silent wipe |
| **Coercion** | Forced to unlock device and reveal health data | Encrypted DB requires device credentials | Duress PIN shows working home screen while silently wiping; dead man's switch if owner incapacitated |
| **Reproductive prosecution** | Period/fertility data used as evidence post-Dobbs | On-device only, no server-side data to subpoena, no third-party SDK access | OS-level wipe features; Tor hides any network activity; no metadata trail |
| **Employer / insurer surveillance** | Health data used for discrimination | No data sharing with third parties; no cloud processing | Degoogled ROM eliminates Play Services reporting; firewall blocks corporate MDM telemetry |
| **Malicious insider** | Backend access by Bios employee | No raw health data on servers; encrypted sync blobs are opaque | N/A — same |
| **App Store / OS-level** | Backup extraction, cloud sync | `allowBackup=false`; exclude health DB from cloud backups | LETHE disables cloud backup OS-wide; no Google account on device |
| **Google Play Services** | Silent telemetry, health data correlation | No Play Services dependency; tolerates but doesn't require GMS | Absent entirely (degoogled ROM) |

---

## Data Residency

### On-Device (default, always)

```
+---------------------------------------------------+
|                    USER'S DEVICE                   |
|                                                    |
|  +---------------------------------------------+  |
|  |          SQLCipher Encrypted Database         |  |
|  |                                               |  |
|  |  - Raw MetricReadings                        |  |
|  |  - ComputedAggregates                        |  |
|  |  - PersonalBaselines                         |  |
|  |  - Anomalies & Alerts                        |  |
|  |  - ConditionPatterns (reference data)        |  |
|  |  - User Preferences                          |  |
|  +---------------------------------------------+  |
|                                                    |
|  +---------------------------------------------+  |
|  |          Platform Keychain                    |  |
|  |                                               |  |
|  |  - Database encryption key                   |  |
|  |  - OAuth tokens for wearable APIs            |  |
|  |  - Sync encryption key (if sync enabled)     |  |
|  +---------------------------------------------+  |
+---------------------------------------------------+
```

**All health data processing happens on-device:**
- Baseline computation
- Anomaly detection
- Alert generation
- Trend analysis
- Data export

The app is fully functional with no network connection whatsoever.

### On-Server (opt-in only)

If the user enables multi-device sync, the following lives on the server:

```
+---------------------------------------------------+
|                    BIOS SERVER                     |
|                                                    |
|  +---------------------------------------------+  |
|  |     User Account (minimal)                   |  |
|  |  - account ID (UUID)                         |  |
|  |  - passkey credential                        |  |
|  |  - email (for magic link fallback)           |  |
|  |  - created_at, last_login_at                 |  |
|  +---------------------------------------------+  |
|                                                    |
|  +---------------------------------------------+  |
|  |     Encrypted Sync Blobs                     |  |
|  |  - opaque ciphertext (server cannot read)    |  |
|  |  - blob ID, timestamp, size                  |  |
|  |  - account ID (for routing only)             |  |
|  +---------------------------------------------+  |
|                                                    |
|  NOT ON SERVER:                                    |
|  - No raw health data                             |
|  - No baselines or aggregates                     |
|  - No alert history                               |
|  - No device names or types                       |
|  - No encryption keys                             |
+---------------------------------------------------+
```

### Community Tier: On-Device Aggregation Pipeline

Community users contribute anonymized patterns. The critical design constraint is that **anonymization happens on-device, before transmission** -- not on the server after the fact.

```
+---------------------------------------------------+
|                    USER'S DEVICE                   |
|                                                    |
|  +---------------------------------------------+  |
|  |          Local Health Database               |  |
|  |  (same as Private tier -- fully encrypted)   |  |
|  +---------------------------------------------+  |
|              |                                     |
|              v                                     |
|  +---------------------------------------------+  |
|  |     On-Device Aggregation Engine             |  |
|  |                                               |  |
|  |  1. Compute statistical summaries from       |  |
|  |     local data (means, distributions,        |  |
|  |     pattern signatures)                      |  |
|  |  2. Strip all identifiers, timestamps,       |  |
|  |     device info, and raw values              |  |
|  |  3. Apply differential privacy noise         |  |
|  |     (calibrated epsilon)                     |  |
|  |  4. Output: anonymous statistical vector     |  |
|  +---------------------------------------------+  |
|              |                                     |
|              v                                     |
|  +---------------------------------------------+  |
|  |     Transmission (TLS 1.3)                   |  |
|  |  - Pre-anonymized aggregate only             |  |
|  |  - No account ID attached                    |  |
|  |  - No device fingerprint                     |  |
|  |  - Cannot be linked back to this device      |  |
|  +---------------------------------------------+  |
+---------------------------------------------------+
              |
              v
+---------------------------------------------------+
|                    BIOS SERVER                     |
|                                                    |
|  +---------------------------------------------+  |
|  |     Aggregate Collection Pool                |  |
|  |  - Receives anonymous statistical vectors    |  |
|  |  - Cannot identify source user or device     |  |
|  |  - Cannot reconstruct individual readings    |  |
|  |  - Used to train and improve detection       |  |
|  |    models across the population              |  |
|  +---------------------------------------------+  |
+---------------------------------------------------+
```

**Key properties:**
- The server cannot distinguish Community contributions from each other
- Even if the server is compromised, individual health data cannot be reconstructed
- Contribution frequency is randomized (not on a schedule tied to usage patterns)
- Users can inspect exactly what is being sent before it leaves (transparency dashboard)
- Aggregation code is open-source and auditable

### What Community Contributions Include

| Included | Example | Why |
|---|---|---|
| Statistical distributions | "HRV mean in range 40-50ms" | Improves baseline models across demographics |
| Pattern signatures | "Sleep pattern type C detected" | Trains condition detection algorithms |
| Alert feedback | "Alert dismissed" / "Alert confirmed" | Improves alert accuracy for everyone |
| Device type class | "Wrist wearable" (not brand/model) | Accounts for sensor differences |
| Age bracket | "30-39" (if user provides age) | Enables age-appropriate baselines |

### What Community Contributions Never Include

- Raw sensor readings (HR, HRV, SpO2, temp, glucose values)
- Timestamps or time-series data
- Account ID, device ID, or any persistent identifier
- Location, IP address, or network information
- Anything that could single out an individual

---

## Encryption Design

### At Rest (on-device)

| Layer | LETHE (embedded) | Stock Android (standalone) |
|---|---|---|
| Database | SQLCipher AES-256-CBC with HMAC-SHA512 | Same |
| DB key storage | LETHE hardware-backed keystore (no GMS dependency) | Android Keystore (hardware-backed where available) |
| DB key generation | Random 256-bit key generated on first launch | Same |
| Backup exclusion | LETHE disables cloud backup OS-wide | `android:allowBackup="false"` per-app |
| Additional protections | LETHE firewall blocks all outbound by default; tracker-blocking hosts file; burner mode wipes DB on reboot if enabled; dead man's switch triggers key destruction | Standard Android sandbox isolation |

The database key is:
- Generated once, stored in the platform's hardware-backed keychain
- Protected by the device's passcode / biometric
- Never transmitted anywhere
- Destroyed if the user chooses "Delete all data"
- On LETHE: also destroyed by dead man's switch or burner mode reboot

### In Transit (sync)

```
User device                         Bios server
    |                                    |
    |  1. Derive sync key from passkey   |
    |     (HKDF-SHA256)                  |
    |                                    |
    |  2. Encrypt data blob              |
    |     (XChaCha20-Poly1305)           |
    |                                    |
    |  3. Upload ciphertext over TLS  -->|
    |                                    |  4. Store opaque blob
    |                                    |     (cannot decrypt)
    |                                    |
    |  5. Other device downloads blob <--|
    |                                    |
    |  6. Decrypt with same sync key     |
    |     (derived from same passkey)    |
```

**Key properties:**
- Sync key is derived client-side from the user's passkey using HKDF
- Server never sees the sync key or any material to derive it
- Each blob is encrypted with a unique nonce
- TLS 1.3 minimum for transport (defense-in-depth)
- If the user loses their passkey, sync data is irrecoverable (by design)

### Key Rotation

- Sync key rotates when the user resets their passkey
- Old blobs are re-encrypted with the new key during the next full sync
- DB encryption key can be rotated via Settings (re-encrypts database in place)

---

## Data Minimization

### What we collect (on-device)

Only what is needed for health analysis:
- Sensor readings with timestamps
- Source device identifiers (for deduplication)
- Computed baselines and aggregates
- Alert records

### What we never collect

- Location data (GPS is not used, even if available)
- Contact lists, call logs, messages
- App usage patterns outside Bios
- Photos, files, or media
- Advertising identifiers
- IP addresses (not logged on backend)

### Retention

- User controls retention period (default: 1 year of raw readings)
- Computed aggregates retained longer (space-efficient, useful for long-term trends)
- Automatic pruning runs weekly
- User can delete all data instantly ("nuclear option" in Settings)

---

## Third-Party Data Flow

### Wearable API connections (HealthKit, Oura, WHOOP, etc.)

```
Wearable API  --(OAuth 2.0)-->  User's device  --(local only)-->  Bios DB

NOT:
Wearable API  -->  Bios server  -->  User's device
```

- All API calls go directly from the user's device to the wearable API
- OAuth tokens stored in the device keychain, never on our server
- We are a client of these APIs, not a proxy

### Analytics (PostHog, self-hosted)

Events sent to analytics contain:
- Feature usage (screens viewed, features used) -- opt-in
- App performance metrics (launch time, memory)
- Crash metadata (stack traces, device model)

Events never contain:
- Health readings or derived values
- Alert content or anomaly details
- User identity (anonymous device ID only)
- Anything from the encrypted database

**Enforcement:** Analytics calls go through a sanitization layer that strips any field matching health data patterns before transmission.

---

## User Rights & Controls

| Right | Implementation |
|---|---|
| **Access** | Full data export (JSON + FHIR) at any time |
| **Portability** | Export format is open and documented |
| **Deletion** | "Delete all data" destroys the encryption key, rendering all data irrecoverable |
| **Correction** | Users can delete individual readings or time ranges |
| **Objection** | Any optional feature (sync, analytics, research) can be disabled independently |
| **Transparency** | Privacy dashboard shows: what data exists, where it lives, what's connected |

### The "Delete All Data" flow

1. User confirms deletion (double confirmation required)
2. App destroys the database encryption key from the keychain
3. App deletes the encrypted database file
4. If sync was enabled: app sends a deletion request to the server
5. Server deletes all encrypted blobs for that account within 24 hours
6. Server deletes the account record within 24 hours
7. App resets to fresh install state

Without the encryption key, even if the database file were forensically recovered, the data is computationally irrecoverable.

---

## Compliance Considerations

| Regulation | How Bios Aligns |
|---|---|
| **GDPR** | Data minimization, right to access/delete/port, no cross-border health data transfer (data stays on-device), privacy by design |
| **HIPAA** | Bios is not a covered entity (we don't store PHI on our servers). If future partnerships require it, the zero-knowledge architecture means minimal BAA scope |
| **CCPA/CPRA** | No sale of personal information, user deletion rights, transparency |
| **Apple App Store health data rules** | No health data shared with third parties, no advertising use |
| **Google Play health data policy** | Prominent disclosure, no undisclosed data use, encryption required |
| **Post-Dobbs reproductive data** | On LETHE: data is unreachable by design (no cloud, no Play Services, firewall-blocked, encryption key in hardware keystore). On stock Android: data stays on-device with no third-party SDK access. No server-side data means nothing to subpoena. |

### LETHE vs Stock Android: Privacy Comparison

| Protection | LETHE (embedded) | Stock Android (standalone) |
|---|---|---|
| Network isolation | OS-level firewall blocks all outbound by default | App-level: Bios makes no network calls unless user opts in |
| Tracker blocking | System-wide hosts file blocks known trackers | App-level: no analytics SDKs that phone home |
| Play Services telemetry | Absent (degoogled ROM) | Present unless manually disabled |
| Backup exfiltration | Disabled OS-wide | Disabled per-app (`allowBackup=false`) |
| Forensic resistance | Burner mode wipes on boot; dead man's switch | Standard Android FDE/FBE |
| Legal compulsion | No server data + device-level encryption + LETHE wipe features | No server data + device-level encryption |

On LETHE, Bios inherits OS-level privacy hardening that is impossible to replicate at the app level on stock Android. The standalone flavor still provides strong privacy through on-device-only architecture, but LETHE provides defense-in-depth at every layer.

---

## Forensic Risk Awareness

The `ForensicRiskMonitor` helps the owner understand their data footprint:
- Total readings stored, data age, database size on disk
- Whether burner mode is active (LETHE)
- Whether dead man's switch is armed (LETHE)
- Flags when data accumulates beyond 30 days with burner mode off (LETHE)
- Quick-wipe controls: wipe last 1/7/30 days without destroying baselines

The owner should know what they're accumulating. On a device that could be seized, health data history is a liability. Bios surfaces this information without nagging — available when the owner looks for it.

## Alert Content Policy

The `AlertContentPolicy` enforces the "never evaluate the person" principle at the code level:
- Prohibited: second-person lifestyle judgments ("you should", "you need to"), guilt mechanics ("you haven't", "you missed"), gamification ("streak", "achievement", "leaderboard")
- Allowed: data statements ("resting HR +2σ"), questions ("have you felt unwell?"), professional referrals ("discuss with your healthcare provider")
- All 12 condition patterns are validated against this policy
- Future patterns must pass the same check in CI

## Coercion Resistance

The `SafeMode` provides protection under duress:
- On LETHE: duress PIN triggers data destruction + fresh-install appearance
- On stock Android: owner can set a separate "safe PIN" that triggers the same behavior
- After activation: app shows onboarding screen — visually indistinguishable from a fresh install
- No trace that data ever existed

---

## Security Practices

| Practice | Detail |
|---|---|
| Dependency auditing | Automated vulnerability scanning in CI (Dependabot / Snyk) |
| Code review | All changes reviewed; health data handling changes require 2 reviewers |
| Penetration testing | Annual third-party pentest of backend and sync protocol |
| Encryption library | Use platform-provided crypto (Apple CryptoKit, Android Jetpack Security) -- no custom crypto |
| Secret management | No hardcoded secrets; all keys in platform keychain or CI secrets |
| Incident response | Documented runbook; users notified within 72 hours of any confirmed breach |

---

## Summary

```
+------------------------------------------------------+
|           BIOS PROTECTION MODEL                       |
|     "Protect the person holding the device."          |
|                                                       |
|  ON-DEVICE (always, both tiers):                      |
|    - All health data                                  |
|    - All processing & detection                       |
|    - All encryption keys                              |
|    - Fully functional offline                         |
|    - Same features, same intelligence                 |
|                                                       |
|  ON-SERVER (Private tier, opt-in sync):               |
|    - Opaque encrypted blobs (zero-knowledge)          |
|    - Minimal account info (UUID, passkey, email)      |
|                                                       |
|  ON-SERVER (Community tier):                          |
|    - Same as Private if sync enabled                  |
|    - PLUS: anonymous statistical aggregates           |
|      (differentially private, pre-anonymized          |
|       on-device, no identifiers, no raw data)         |
|                                                       |
|  ON LETHE (embedded flavor):                          |
|    - OS-level firewall, tracker blocking, Tor         |
|    - Burner mode: health data wiped on boot           |
|    - Dead man's switch: data destroyed if             |
|      owner incapacitated or coerced                   |
|    - Duress PIN: silent wipe under coercion           |
|    - No Google Play Services, no telemetry            |
|                                                       |
|  NOWHERE (any platform, any tier):                    |
|    - Plaintext health data on any server              |
|    - Encryption keys outside the owner's device       |
|    - Health data in analytics or crash reports         |
|    - Individual data sold to anyone                   |
|    - Data tied to an identity shared with partners    |
|    - Lifestyle judgments, wellness scores, or          |
|      behavioral nudges targeting the owner             |
+------------------------------------------------------+
```
