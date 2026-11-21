# Ecosystem Boundaries

Defines what belongs to Bios and what belongs to the apps around it. Bios is described here by what it does. Companion apps are described only by the data they read and write through `BiosHealthProvider`.

## Bios owns

- **Ingestion.** Adapters for Health Connect, Gadgetbridge, phone and direct sensors, camera PPG, a BLE air-quality peripheral and the vendor APIs the owner signs into ([ARCHITECTURE.md §1](ARCHITECTURE.md)).
- **Storage.** Encrypted time-series and records (Room + SQLCipher), retention, erasure.
- **Usual range.** Per-metric baselines from the owner's own sensor readings.
- **The one deviation alert.** Watched metrics outside the owner's usual range, stated in units.
- **The owner's records.** Logged events, diaries, medication list, lab values, body measurements, shown back as written.
- **Export.** JSON, CSV, FHIR, PDF.
- **The canonical metric vocabulary.** `MetricType` in the `bios-contracts` module is the single source of truth for metric keys.
- **The companion provider.** `BiosHealthProvider` read URIs and the per-package write slot ([CONSUMER_API.md](CONSUMER_API.md)).

Bios does not interpret what it stores, and it does not interpret what companions write. A companion-written reading is stored, shown with its source, exported, and served back through the provider. The one computation Bios runs on logged intakes is the estimated amount remaining over time (`ConcentrationCalculator`).

## The rule

Ask in order:

1. **Is it a raw sensor reading, a usual range, or a derivation any app with Bios's canonical inputs could compute?** It belongs in **Bios**.
2. **Does it need a capture surface Bios does not have** (an AccessibilityService, a foreground detection service, an active test, tap-to-log for a specific domain, SMS or call handling)? It belongs in a **companion**.
3. **Is it about the person's surroundings or contacts** (messages, calls, location broadcast) and not a measurement? It belongs in a **companion**, and probably not on the provider at all.

### Producer is determined by capture surface, not by consumer

When a derived metric could be computed by more than one party, the producer is whoever owns the capture surface that produces its inputs, not whoever reads the result first.

| Metric key | Inputs | Producer | Why |
|---|---|---|---|
| `typing_cadence` | per-keystroke timing | W2F | needs an AccessibilityService |
| `mood_drift_score` | W2F's own inputs | W2F | computed from data only W2F captures |
| `caffeine_intake` | tap-to-log in W2F | W2F | W2F owns the logging surface |
| `sleep_duration` from screen-off windows | usage statistics | W2F (LOW confidence) | W2F owns that surface; Bios accepts the write |
| `sleep_duration` from phone sensors | accelerometer, screen, charging | Bios | every phone has these sensors |
| `circadian_phase_shift` | sleep-onset times | Bios (`CircadianEngine`) | inputs are canonical. W2F still holds write permission for this key from the period before Bios computed it. |
| `heart_rate_before_sleep` | heart rate stream and sleep onset | Bios | inputs are canonical |
| `tobacco_use`, `tobacco_craving`, `cannabis_use`, `cannabis_craving` | tap-to-log | Smokeless | Smokeless owns the logging surface |
| `fall_event`, `near_miss_fall`, `check_in_miss` | foreground detection service, check-in timer | Virgil | Virgil owns those surfaces |
| `sedentary_bout`, `movement_break` | sit detector in a foreground service | Anastasis | Anastasis owns that surface |
| `reaction_time_ms` | an active test | reserved, no producer | needs an active-test surface no app has shipped |

The corollary: if a companion turns canonical Bios inputs into a figure, that computation should move to Bios when feasible, and the companion reads the result.

### Second-consumer rule

Data captured by one companion for its own use stays in that companion. When a second app needs it, the key moves to `MetricType` and the data flows through the provider, instead of living in two private tables.

## Companions

Each companion is a separate APK. Any approved companion can read the provider's read URIs (readings, baselines, status, sources). Writes are limited per package (`provider/CompanionContract.kt`):

| App | Package | Writes to Bios |
|---|---|---|
| W2F | `com.w2f.app` | `typing_cadence`, `circadian_phase_shift`, `mood_drift_score`, `sleep_duration`, `caffeine_intake` |
| Smokeless | `com.smokless.smokeless` | `tobacco_use`, `tobacco_craving`, `cannabis_use`, `cannabis_craving` |
| Virgil | `com.virgil.app` | `fall_event`, `near_miss_fall`, `check_in_miss` |
| Anastasis | `com.anastasis.app` | `sedentary_bout`, `movement_break` |
| SoulRadio | none | nothing. It neither reads nor writes the provider. |

The write URI accepts a value and a timestamp and nothing else: no free text, location or contact.

`BiosIntentActions` in `bios-contracts` reserves two intent actions addressed to SoulRadio (`SUGGEST_BAND`, `REQUEST_STOP`). They are names only. Bios sends neither.

Some keys in `MetricType` have no producer in any shipped app (for example `reaction_time_ms`, `sleep_apnea_event`, `seizure_event`). They stay because removing a key breaks the contract; see the legacy note in [DATA_MODEL.md](DATA_MODEL.md).

## Consequences for Bios design

- **No interpretation in Bios, of its own data or a companion's.** See [specs/instrument-only.md](specs/instrument-only.md).
- **No domain-specific active tests in Bios.** Bios may host capture surfaces for its own sensor adapters (camera PPG), entry surfaces for slow-moving manual data (sleep duration, lab values, body measurements), and read-only views over its own inventory (the Data Coverage screen). It does not host an active test whose output is a companion-owned key. The test is the producer rule above: whose key does this surface produce? See [SELF_REPORTED_DATA_HOME.md](SELF_REPORTED_DATA_HOME.md).
- **No capture surfaces beyond sensors and owner entry.** AccessibilityService, foreground fall detection, SMS and call handling are companion concerns.
- **No contact or messaging features.** Contacts, message sending, location broadcast and shared dashboards are companion concerns.
- **New metric keys need a home.** Before adding a key to `MetricType`, name the app that produces it and the app that reads it.

## Scope decisions

| Topic | Verdict | Reason |
|---|---|---|
| Food, macro and calorie log | Out of scope | Several entries a day by hand; the sensor stack already records glucose and heart rate without typing |
| Hydration log | Out of scope | Manual entry with no reader |
| Water quality | Out of scope | No reader in Bios |
| Intervention log (sauna, cold exposure and similar) | In scope | Owner-entered event log, shown back as written |
| Medication list | In scope | Owner-entered record, shown back as written |
| Grip strength, additional active-test keys | Deferred | No shipped app produces them |
| `reaction_time_ms` | Reserved | Key exists; no producer |
| Exercise sessions | In scope | Read from adapters (`EXERCISE_SESSION`) |
| Air quality | In scope | BLE Environmental Sensing adapter |
| Camera PPG capture | In scope | Produces Bios-owned keys from a phone sensor |
| Manual sleep duration entry | In scope | Produces `SLEEP_DURATION`, a Bios-owned key |
| Data Coverage screen | In scope | Read-only view over Bios's own metric inventory |

### Design rule for owner-entered logs

A manual logging surface in Bios must not carry the mechanics that drive repeat sessions:

- No streak counters, completeness percentages or daily-goal gauges.
- No "you haven't logged today" empty states and no reminders to log.
- No "log everything to see results" prompts. Sparse logging is a normal state.
- An empty state says what is true ("no entries in this window") and stops.
- A chart shows what is there and says nothing about what is missing.
- Bios does not ask for data. It works with what the owner gives.

This rule does not apply to notices about Bios's own plumbing (a source that stopped delivering).

## See also

- [ARCHITECTURE.md](ARCHITECTURE.md): components
- [CONSUMER_API.md](CONSUMER_API.md): `BiosHealthProvider` contract
- [DATA_MODEL.md](DATA_MODEL.md): metric schema
