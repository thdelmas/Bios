# Bios Privacy Architecture

## Principle

The owner's measurements stay on the owner's phone. Bios has no server, no account, no analytics and no crash reporting. Nothing leaves the phone unless the owner sends it.

This document states where data lives, how it is protected, and every way it can leave the phone in the code as it is.

## What Bios Holds

- Readings from sensors, wearables and vendor APIs (heart rate, HRV, SpO2, sleep, steps, glucose and others)
- The owner's usual range per metric and computed aggregates
- Alerts: which metrics were outside the usual range, by how much, and the owner's note
- The owner's own records: logged events, diaries, medication list, immunisations, lab values, body measurements, ECG strips
- Cycle readings and contraception records, in a separate database
- Source names and sync state
- Tokens or sign-ins for the vendor services the owner connected

## Where It Lives

```
+---------------------------------------------------+
|                   OWNER'S PHONE                    |
|                                                    |
|  Main database (SQLCipher)                         |
|    readings, aggregates, baselines, alerts,        |
|    records, sources                                |
|                                                    |
|  Reproductive database (SQLCipher, separate file,  |
|    separate passphrase)                            |
|                                                    |
|  EncryptedSharedPreferences (Keystore-backed)      |
|    database passphrases, vendor tokens             |
|                                                    |
|  App-private files                                 |
|    export files awaiting delivery, lab report      |
|    scans the owner attached                        |
+---------------------------------------------------+
```

All computation (baselines, deviation score, derivations, OCR of lab reports, camera PPG processing) runs on the phone. The app works with no network connection.

## What Can Leave the Phone

This is the complete list. The privacy dashboard in the app shows the same list in short form.

### 1. Vendor APIs the owner signs into

Oura, WHOOP, Garmin, Withings, Dexcom, Polar, COROS. When the owner connects one, Bios calls that vendor's API from the phone to fetch the owner's readings.

- What is sent: the owner's token or sign-in for that service, and the request for a date range.
- What is not sent: any reading, baseline or record held by Bios.
- The vendor sees the request and the phone's IP address, as it would from its own app.
- Tokens are stored in `EncryptedSharedPreferences` and removed on disconnect and on full wipe.

```
Vendor API  <--(owner's token, HTTPS)--  phone  -->  Bios database (local)
```

There is no Bios server between the phone and the vendor.

### 2. Export and share, started by the owner

JSON, CSV, FHIR bundle, PDF summary. A file is written only when the owner asks for one, to app-private cache, and is delivered through the system file picker ("Save to device") or the share sheet. It goes where the owner sends it.

- **Plaintext** by default.
- **Password-protected** when the owner turns it on: a standard AES-256 zip (`EncryptedZipExporter`, Zip4j; passphrase entered twice, not recoverable if lost). The container opens outside Bios with common tools. The plaintext cache copy is deleted once the zip is produced.

An exported alert carries what was measured: time, tier, the metrics with their value and the usual range at detection, acknowledgement and the owner's note. Alerts are not included in the FHIR bundle.

### 3. Companion apps on the same phone

`BiosHealthProvider` lets another app on the phone read metrics, baselines and source liveness, and write the metric keys assigned to it. Gates: an Android runtime permission the OS asks the owner to grant (`READ_HEALTH`, `WRITE_COMPANION`); a per-app approval inside Bios, where a first call is recorded as pending and denied until the owner approves it; and, for writes, a per-package list of allowed keys with an optional pin of the companion's signing certificate (no pin is set for any companion today). Data read this way stays on the phone but leaves Bios's database; what the companion does with it is that app's responsibility. Contract: [CONSUMER_API.md](CONSUMER_API.md).

### 4. Loopback API, on LETHE only

When Bios detects LETHE at runtime it starts an HTTP server bound to `127.0.0.1:8080` for the LETHE agent, and posts a status summary to the agent on loopback. Loopback traffic does not leave the phone, but any process on the phone that can open a loopback connection can read these endpoints: alert status, a 24-hour summary per metric, the alert list as measured lines, and baselines. On stock Android the server is never started.

### 5. Device-to-device sync: code present, not enabled

`sync/` contains a payload codec, a local-network transport that opens a listener and announces itself, a stub for an Iroh node, and functions that push and pull encrypted blobs to a relay URL passed in by the caller. None of it runs in the app as built:

- the transport starts only when the preference `bios_p2p_settings/enabled` is true, and no screen sets it;
- the periodic sync worker returns immediately while that preference is false;
- nothing constructs `SyncManager`, so the relay functions and the local IPFS client are never called, and no relay URL exists in the code.

The privacy dashboard mentions sync between the owner's own devices as a way data could leave; today there is no switch to turn it on.

### 6. Other inputs that are not outputs

Health Connect, Gadgetbridge broadcasts, Bluetooth peripherals and the phone's sensors deliver data to Bios. Bios does not write back to them.

### Not present

- No Bios server, account or cloud backup (`android:allowBackup="false"`)
- No analytics, telemetry or crash reporting
- No advertising identifiers
- No contribution of data, aggregated or not, to anyone
- No push messaging from a server
- No model download or update channel
- No location data: Bios reads no position. Bluetooth scanning is declared `neverForLocation`; on Android 11 and below the location permission is declared only because the OS requires it for a Bluetooth scan.

## Encryption at Rest

| Layer | Implementation |
|---|---|
| Databases | SQLCipher, AES-256, one passphrase per database |
| Passphrases | Random, generated on first launch, stored in `EncryptedSharedPreferences` under a Keystore-backed master key (hardware-backed where the device provides it) |
| Vendor tokens | `EncryptedSharedPreferences` |
| Backups | Excluded: `allowBackup=false` |

The passphrases are never transmitted.

## Deletion

**Delete all data** (`DataDestroyer.destroyAll`):

1. destroys the database passphrase, which makes the database file unreadable;
2. deletes the database files, vendor tokens, pending export files and attached lab scans;
3. cancels scheduled work, clears notifications, releases file permissions, clears preferences and any sync state.

The reproductive database can be destroyed on its own (`destroyReproductiveData`).

**Partial wipe.** The privacy dashboard shows the data footprint (number of readings, age of the oldest data, database size) and can delete the last 1, 7 or 30 days.

**Retention.** The periodic sync deletes readings older than 90 days.

**On LETHE.** The OS broadcasts for burner mode, dead man's switch, panic and duress wipe trigger the same full destruction. The dashboard shows whether burner mode and the dead man's switch are active.

**Wipe PIN.** `SafeMode` can wipe everything and return the app to its first-run screen when a separate PIN is entered. No screen sets such a PIN today.

## Threats and What Answers Them

| Threat | In Bios | Added by LETHE (as LETHE documents itself; not verified in this repository) |
|---|---|---|
| A vendor or data broker obtaining readings from Bios | There is no Bios server and no SDK that reports out | OS firewall, tracker blocking |
| Device theft or seizure | Encrypted databases; key destruction on wipe | Burner mode, panic and duress wipe, dead man's switch |
| Backup extraction | `allowBackup=false` | Cloud backup disabled OS-wide |
| Network interception | The only traffic is HTTPS to vendors the owner connected | Traffic routed through Tor |
| Legal demand addressed to the project | The project holds no user data | Same |
| Another app on the phone | Runtime permission and owner approval on the provider; loopback API not started on stock Android | Loopback API is readable by local processes |
| Play Services telemetry | No dependency on Play Services | Absent from the ROM |

Limits to state plainly: an unlocked phone with Bios open shows everything; a vendor the owner connected keeps its own copy of the owner's data under its own terms; an exported plaintext file is unprotected once it leaves the app.

## Text Policy

`AlertContentPolicy` lists what Bios's own text may not say: condition names and words of medical purpose (detecting, judging, advising, attaching a meaning to a number). `VocabularyGateTest` scans every string resource and the string literals of the owner-facing packages and of the notification code. Uses that are legitimate (the owner's own record kinds, vaccine names, standard code descriptions in the FHIR export) sit in `VocabularyGateAllowlist`, one entry per file and text, each with a reason.

## Owner Controls

| Control | Where |
|---|---|
| See what exists and how old it is | Privacy dashboard |
| Export everything | Settings, export (JSON, CSV, FHIR, PDF) |
| Delete everything, or the last days | Privacy dashboard, settings |
| Connect and disconnect each vendor | Data sources |
| Approve or revoke each companion app | Companion apps |
| Turn each source's metrics on or off | Metric sources |

## Security Practices

| Practice | Detail |
|---|---|
| Dependency audit | OWASP dependency check in CI (non-blocking) |
| Secret scanning | gitleaks in CI, `scripts/check-secrets.sh` in the pre-commit hook |
| Outside references | `.githooks/leakcheck.py` in hooks and CI |
| Crypto | Platform and library crypto only (SQLCipher, AndroidX Security, Zip4j). No custom cipher. |
