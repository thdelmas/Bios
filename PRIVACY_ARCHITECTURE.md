# Bios Privacy Architecture

## Core Principle

**Every user gets the same health intelligence. How your data contributes back is your choice.**

Bios operates two privacy models based on the user's chosen tier:

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
- Any data that could reveal health status, behavior patterns, or identity

### Who we protect against

| Threat Actor | Attack Vector | Our Defense |
|---|---|---|
| **Bios (ourselves)** | Server-side data access | Private: zero-knowledge backend, server only sees ciphertext. Community: server receives only differentially private aggregates, never raw data or identifiers |
| **Device theft** | Physical access to phone | Full-database encryption keyed to device credentials |
| **Network interception** | MITM on sync traffic | E2E encryption; TLS is defense-in-depth, not primary |
| **Third-party SDKs** | Analytics/crash tools leaking data | No health data in analytics; self-hosted tooling; SDK audit |
| **App Store / OS-level** | Backup extraction | Exclude health DB from iCloud/Google backups |
| **Legal compulsion** | Subpoena for user data | We cannot produce what we do not have (zero-knowledge) |
| **Malicious insider** | Backend access by employee | No raw health data on servers; encrypted sync blobs are opaque |

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

| Layer | Mechanism |
|---|---|
| Database | SQLCipher AES-256-CBC with HMAC-SHA512 |
| DB key storage | iOS Keychain (kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) / Android Keystore |
| DB key generation | Random 256-bit key generated on first launch |
| Backup exclusion | NSURLIsExcludedFromBackupKey (iOS) / android:allowBackup="false" |

The database key is:
- Generated once, stored in the platform's hardware-backed keychain
- Protected by the device's passcode / biometric
- Never transmitted anywhere
- Destroyed if the user chooses "Delete all data"

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
|                  BIOS PRIVACY MODEL                   |
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
|  NOWHERE (either tier):                               |
|    - Plaintext health data on any server              |
|    - Encryption keys outside the user's device        |
|    - Health data in analytics or crash reports         |
|    - Individual data sold to anyone                   |
|    - Data tied to an identity shared with partners    |
+------------------------------------------------------+
```
