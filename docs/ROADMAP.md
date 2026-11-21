# Bios Roadmap

## What Bios is now

A measuring instrument. It measures, compares each metric with the owner's own usual range, and shows the result. It never says what a measurement means. The decision and its scope are in [specs/instrument-only.md](specs/instrument-only.md).

Last released version: 0.3.2. Builds are paused; the APKs of v0.1.0 to v0.3.2 have been withdrawn. The next version carries the removals listed in the changelog.

**In the app on this branch:**

- Ingestion from Health Connect, Gadgetbridge, phone and direct sensors, camera PPG, a BLE air-quality peripheral, and the vendor APIs the owner signs into (Oura, WHOOP, Garmin, Withings, Dexcom, Polar, COROS)
- Two encrypted databases (main and reproductive), full wipe, partial wipe, 90-day retention of readings
- A usual range per metric over a rolling 14 days of the owner's own sensor readings
- One alert: watched metrics outside the usual range, stated in units, in four tiers by size of deviation
- Trends, day totals for count metrics with one source per day, source views and source liveness
- Sleep dashboard (durations, stages, efficiency, latency, spread of bedtime, midpoint and wake time) and cycle dashboard, as logged
- The owner's records shown back as written: logged events, headache diary, health events, medication list, immunisations, lab values (typed, imported from FHIR, or read from a report by on-device OCR), body measurements, ECG strips
- Export: JSON, CSV, FHIR bundle, PDF summary, optional AES-256 zip
- `BiosHealthProvider` for companion apps, with owner approval per app
- On LETHE: loopback API for the agent, OS wipe signals, reboot-safety answer
- A vocabulary gate test over every text Bios shows

**Not in the app:** named conditions, causes, advice, reference ranges, scores against an outside reference, a trained model, a server, any contribution of data, push from a server.

## Open product questions

From the instrument-only spec:

1. **Sleep regularity storage.** The reading is still stored as a 0–100 rescale of the midpoint spread between two fixed anchors (15 and 90 minutes). The dashboard shows the spreads in minutes. To decide: store the spread itself.
2. **Stored readings of removed derivations** (the sleep score among them) remain in the database, selectable in the metric browser and present in the JSON export. To decide: hide, delete, or leave.
3. **Telling existing installers.** The release notes and the community channel have to say plainly what was removed and where to speak up. The spec's "other installers" seat stays empty until that is done.

From the code:

4. **Device-to-device sync.** The code is present (`sync/`), the Iroh node is a stub, and no screen turns sync on. The privacy dashboard mentions sync. To decide: finish it, or remove the code and the mention.
5. **Legacy contract keys.** `bios-contracts` keeps keys that nothing in Bios produces (for example `sleep_apnea_event`, `seizure_event`, `sleep_score`) and the `URGENT` state value, because removing them breaks companions. To decide: a deprecation path for the contract.
6. **Companion certificate pins.** No companion has a signing-certificate pin yet; on the standalone flavor the package name stands alone for the per-package write list.
7. **`circadian_phase_shift` has two writers.** Bios computes it (`CircadianEngine`) and W2F still holds write permission for the key.
8. **Tables of removed features** stay in the database file with their rows, unread. To decide: whether a later version offers to delete them.
9. **Retention.** Readings older than 90 days are deleted by one rule on timestamp. To decide: whether owner-entered values (lab values, manual readings) should be exempt.
10. **Wipe PIN.** `SafeMode` exists with no screen to set the PIN.
11. **ECG from Health Connect.** `HealthConnectEcgAdapter` is a stub until Health Connect exposes ECG records; strips arrive by Apple Health import.
12. **Lab report OCR coverage.** Languages and multi-page tables: see [LAB_OCR_INGESTION.md](LAB_OCR_INGESTION.md).
13. **Instrumentation gaps** found by reading a real export: see [GAP_REGISTER.md](GAP_REGISTER.md).

## Constraints that do not change

1. **Measure, compare, show.** No interpretation.
2. **On-device.** No Bios server.
3. **Nothing leaves by default.** Every way out is listed in [PRIVACY_ARCHITECTURE.md](PRIVACY_ARCHITECTURE.md).
4. **Silence.** No streaks, no engagement notifications.
5. **Erasure.** Destroying the key destroys the data.
6. **No Play Services.**
7. **One codebase, two flavors.**
8. **The contract does not break silently.** `bios-contracts` keys and `HealthState` values are append-only.
