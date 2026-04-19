# Ecosystem Boundaries

Defines what belongs to Bios and what belongs to its companion apps (Fil,
Virgil, W2F). The goal is to keep Bios a clean, domain-neutral backbone and
push domain specialization to companions — so each app stays sharp, and none
grows into an everything-app.

## The rule

Ask three questions in order:

1. **Is it a raw sensor, personal baseline, or multi-system body signal?**
   → **Bios.**
2. **Does it require a specialized capture surface** (AccessibilityService for
   typing, foreground sensor analysis for gait, active micro-tests, SMS/call
   handling) **or a domain-specific detection model?** → **Companion.**
3. **Is the feature about the person's surroundings or relationships**
   (emergency contacts, call handling, location broadcast) **rather than their
   physiology?** → **Companion** — and likely standalone, not a Bios consumer
   at all.

Bios is intentionally silent about categories it can't measure objectively
from wearable data alone: neurological fine-motor state, mood/mania, fall
response, cognitive processing speed. Those live in companions, and
companions push computed scores back into Bios's metric bus so the
cross-correlation engine can use them.

## Bios owns — the sensor backbone and generic body guardian

Bios owns whatever is **domain-neutral** and shared by every companion:

- **Ingestion** — 9 adapters: Health Connect, Gadgetbridge, Oura, WHOOP,
  Garmin, Withings, Dexcom, Direct Sensors, Phone Sensors
  ([ARCHITECTURE.md §1](ARCHITECTURE.md))
- **Storage** — encrypted time-series (Room + SQLCipher), retention,
  export (FHIR/JSON), erasure
- **Personal baselines** — rolling stats, z-scores, trend slopes per metric
- **Generic multi-system detection** — the 12 condition patterns (infection,
  cardiovascular, sleep, metabolic, respiratory, AFib, cycle, etc.) in
  [ARCHITECTURE.md §3](ARCHITECTURE.md)
- **Canonical metric vocabulary** — `MetricType` in
  [Enums.kt](../android/app/src/main/java/com/bios/app/model/Enums.kt) is the
  single source of truth for metric keys
- **Consumer API** — `BiosHealthProvider` read URIs and the narrow
  MENTAL_HEALTH companion-write slot ([CONSUMER_API.md](CONSUMER_API.md))

## Companions — domain specialists that compose on top

Each companion owns a domain Bios cannot reasonably own because it requires
specialized sensing, active user interaction, or domain-specific detection
logic.

| App | Domain | Owns | Reads from Bios | Writes to Bios |
|---|---|---|---|---|
| **Fil** | Neurological / MS | Gait analysis (phone accel), keystroke analysis, active cognitive micro-tests (SDMT, tapping, contrast), MS drift engine, fall/auto-answer | HRV, sleep, steps, activity | `gait_asymmetry`, `cognitive_speed`, `motor_score`, `relapse_risk` (future keys) |
| **W2F** | Mood / bipolar | ADA-1, HDA-1, Friction Vault, SOS Mechanical Restart, typing cadence capture | sleep, HRV, activity | `typing_cadence`, `circadian_phase_shift`, `mood_drift_score` |
| **Virgil** | Solitary-living safety | Fall detection, check-in timer, SMS + GPS alerts, emergency call | *nothing — standalone* | *nothing* |

### Fil — the nervous-system specialist

Fil's domain is neurology, specifically MS relapse prediction. It captures
signals Bios doesn't (gait from phone accelerometer, keystroke dynamics from
AccessibilityService, active 30-second micro-tests), runs a MS-specific drift
engine over them plus the generic biometrics Bios already provides, and
pushes computed neurological scores back.

### W2F — the mood/bipolar navigator

W2F's domain is mood state detection and mechanical intervention (friction
for surge, restart for stasis). It owns the typing-cadence capture surface
(AccessibilityService), the ADA-1/HDA-1 detection models, and the
intervention protocols. It writes the three MENTAL_HEALTH keys already
reserved in the Bios schema.

### Virgil — the solitary-living safety net

Virgil is the outlier: it does not read from Bios. Its user may not own a
wearable, may not have Bios installed, and often just wants fall detection
and a dead-man check-in with nothing else. Virgil shares the ecosystem's
*principles* (local-only, no accounts, honest framing) but not its *data
bus*. "Ecosystem" ≠ "everyone reads Bios."

## Consequences for Bios design

- **No diagnostic verticals in Bios.** If a feature needs a
  specialized model (MS, bipolar, Parkinson's, postpartum, etc.), it belongs
  in a companion. Bios exposes the primitives; companions compose them.
- **No active tests in Bios.** Anything that asks the user to *do* something
  (tap, type, watch a screen) lives in a companion. Bios is passive.
- **No capture surfaces beyond sensors.** AccessibilityService, foreground
  fall-detection services, SMS/call handling, call-answering — all companion
  concerns.
- **No social or relational features.** Emergency contacts, message sending,
  location broadcast, shared dashboards — companion concerns.
- **New metric keys require a home.** Before adding a key to `MetricType`,
  identify which companion (or Bios's own engine) produces it and which
  consumes it. If it's a computed score owned by one companion, it still
  needs the canonical key in Bios for the cross-correlation engine.

## Cross-references

- [ARCHITECTURE.md](ARCHITECTURE.md) — Bios system components
- [CONSUMER_API.md](CONSUMER_API.md) — `BiosHealthProvider` contract
- [DATA_MODEL.md](DATA_MODEL.md) — canonical metric schema
