# Ecosystem Boundaries

Defines what belongs to Bios and what belongs to its companion apps (Fil,
Virgil, W2F, SoulRadio, Smokeless). The goal is to keep Bios a clean,
domain-neutral backbone and push domain specialization to companions — so
each app stays sharp, and none grows into an everything-app.

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

### Producer is determined by capture surface, not by consumer

When a derived metric *could* be computed by more than one party, the
producer is whoever owns the unique capture surface that produces its
inputs — not whoever happens to consume the score first. A signal that any
party with Bios's canonical inputs could compute belongs to **Bios**, even
if today only one companion consumes it. A signal that requires an
AccessibilityService, foreground sensor service, or active test surface
belongs to that **companion**.

Applied:

| Metric | Inputs | Producer | Why |
|---|---|---|---|
| `typing_cadence` | per-keystroke timing | **W2F** | requires AccessibilityService |
| `gait_asymmetry` | raw phone accel at sample rate | **Fil** | requires foreground accel service |
| `mood_drift_score` | ADA-1/HDA-1 composite, mood-specific | **W2F** | domain-specific detection model |
| `motor_score`, `relapse_risk` | MS-specific composites | **Fil** | domain-specific detection model |
| `tobacco_use`, `fall_event`, etc. | discrete user actions | **companion** | requires tap-to-log / fall-detection surface |
| `reaction_time_ms` | active test result | **Fil / W2F** | requires active-test surface |
| `circadian_phase_shift` | sleep-onset times | **Bios** | inputs are canonical (sleep timing from 9 adapters); no companion-specific surface |
| `cognitive_speed` (planned) | TBD | **decide at landing** | if active test output → Fil; if composite over canonical inputs → Bios |

The corollary: if a companion has built logic that turns canonical Bios
inputs into a score, that logic should be hoisted to Bios when feasible.
The companion keeps the *consumption* of the score (its domain-specific
interpretation), not the *production* of it.

Bios is intentionally silent about categories it can't measure objectively
from wearable data alone: neurological fine-motor state, mood/mania, fall
response, cognitive processing speed. Those live in companions, and
companions push computed scores back into Bios's metric bus so the
cross-correlation engine can use them.

The same rule governs *capture surfaces* (not just derived scores): a
capture surface lives wherever its produced key lives. See the "No
domain-specific active tests in Bios" rule below for the worked
application to camera PPG, manual sleep entry, and the Data Coverage
screen — all of which are correctly placed in Bios because they produce
Bios-owned keys.

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
| **Fil** | Neurological / MS | **Built:** gait analysis (phone accel), drift z-score engine, fall detection. **Planned:** keystroke analysis, active cognitive micro-tests (SDMT, tapping, contrast), MS-specific composite, fall auto-answer | HRV, sleep, steps, activity | `gait_asymmetry`, `cognitive_speed`, `motor_score`, `relapse_risk` (future keys) |
| **W2F** | Mood / bipolar | ADA-1, HDA-1, Friction Vault, SOS Mechanical Restart, typing cadence capture | sleep, HRV, activity, `circadian_phase_shift` | `typing_cadence`, `mood_drift_score` |
| **Virgil** | Solitary-living safety | Fall detection, check-in timer, SMS + GPS alerts, emergency call | *nothing — standalone* | `fall_event`, `near_miss_fall`, `check_in_miss` (opt-in, future) |
| **SoulRadio** | Ambient sound / nervous-system rest | 24-hour Solfeggio + Schumann auto-loop, dial, listener library, frequency-band catalogue | *nothing — standalone* | *nothing* |
| **Smokeless** | Substance-use tracking / cessation | Use + craving event capture, per-substance history, widget, cessation UI | *nothing — standalone* (Phase 3 plan: RHR/HRV/sleep/SpO2 reads for recovery trajectory) | `tobacco_use`, `tobacco_craving`, `cannabis_use`, `cannabis_craving` (shipped Phase 2.1); `caffeine_use`, `caffeine_craving`, `alcohol_use`, `alcohol_craving` (reserved — Phase 2.4, paired with W2F FuelLog hoist) |

### Fil — the nervous-system specialist

Fil's domain is neurology, specifically MS relapse prediction. It captures
signals Bios doesn't (gait from phone accelerometer, keystroke dynamics from
AccessibilityService, active 30-second micro-tests), runs a MS-specific drift
engine over them plus the generic biometrics Bios already provides, and
pushes computed neurological scores back.

**Status (2026-05):** the gait pipeline (stride time, variability, asymmetry,
cadence, step count, walking segments), the per-axis + composite drift
z-score engine, and on-device fall detection are implemented and stored
locally. Keystroke analysis (AccessibilityService), the SDMT/tapping/contrast
micro-tests, and the MS-specific composite are documented as core
capabilities but not yet present in source. No Bios writes are wired today;
the four reserved keys above remain future work.

### W2F — the mood/bipolar navigator

W2F's domain is mood state detection and mechanical intervention (friction
for surge, restart for stasis). It owns the typing-cadence capture surface
(AccessibilityService), the ADA-1/HDA-1 detection models, and the
intervention protocols. It writes two MENTAL_HEALTH keys
(`typing_cadence`, `mood_drift_score`) and reads `circadian_phase_shift`
from Bios.

**Note on circadian phase shift:** W2F today computes phase shift locally
via cosinor + DLMO algorithms from sleep-onset timing. The math is
universal — sleep onset is canonical Bios data, and the computation is not
mood-specific — so the producer-by-capture-surface rule says this belongs
in Bios. Migration: Bios builds its own circadian engine; W2F reads
`circadian_phase_shift` instead of computing it locally; W2F's write
permission is revoked.

### Virgil — the solitary-living safety net

Virgil's user may not own a wearable, may not have Bios installed, and
often just wants fall detection and a dead-man check-in with nothing
else. Virgil shares the ecosystem's *principles* (local-only, no
accounts, honest framing) and remains fully functional standalone.

Where Virgil **does** belong on the metric bus is outbound: discrete fall
and check-in events. Recurrent falls are a clinically significant signal
for gait instability, syncope, orthostatic hypotension, neuropathy,
hypoglycemia, MS relapse, and medication side effects — exactly the
cross-system patterns Bios's condition engine and Fil's neurological
engine exist to detect. Virgil's own
[`docs/ECOSYSTEM.md`](../../Virgil/docs/ECOSYSTEM.md) reserves three
metric keys (`FALL_EVENT`, `NEAR_MISS_FALL`, `CHECK_IN_MISS`), all opt-in
and timestamp-only — no GPS, no SMS contents, no contact identity. None
of these keys yet exist in `MetricType`; adding them is a Bios-side
change that pairs with a companion-write URI extension.

Virgil also names a small set of admissible inbound integrations
(suppress check-in expiry during detected exercise; treat SoulRadio
playback as a sign of life) — all opt-in, all bounded, none currently
wired.

### Smokeless — the substance-use ledger

Smokeless owns discrete substance-use and craving event capture: tap-to-log
UI, per-substance history, widget for one-tap logging, and the cessation
UX (streak/abstinence framing is owned by Smokeless and intentionally kept
*out* of Bios — Bios is silent about behavioral judgments).

Where Smokeless belongs on the metric bus is outbound: timestamp-only
events that the Bios cross-correlation engine consumes against RHR, HRV,
SpO2, sleep latency, and skin temperature trajectories. Tobacco affects
all five; cravings cluster around sleep debt and autonomic stress —
exactly the cross-system patterns Bios is built to surface.

Smokeless's [`docs/ECOSYSTEM.md`](../../Smokeless/docs/ECOSYSTEM.md)
defines four metric keys: `TOBACCO_USE`, `TOBACCO_CRAVING`,
`CANNABIS_USE`, `CANNABIS_CRAVING`. All four are timestamp + opaque
event-id only — no dose, no brand, no location, no method. All four are
whitelisted on the companion-write URI as of Smokeless Phase 2.1, which
landed the `Substance` enum (`TOBACCO`, `CANNABIS`) on the session
entity and routes each event to its substance-matched key.

A new `MetricDomain` (`INTAKE`) and `MetricUnit.EVENT` are introduced for
this purpose. They are the natural home for any future discrete-intake
events (caffeine, meals, supplements) that hoist up from W2F when a
second consumer appears — see the "case study: nutrition in W2F" rule
below.

**Caffeine + alcohol expansion (reserved, paired with Smokeless Phase 2.4):**
the audit identified asymmetric substance coverage — tobacco/cannabis on
the bus but caffeine/alcohol still W2F-local-only. The second-consumer
trigger has fired (Bios cessation patterns + Smokeless as canonical
ledger), so the four-key extension (`caffeine_use`, `caffeine_craving`,
`alcohol_use`, `alcohol_craving`) is reserved on `INTAKE` and whitelisted
for `com.smokless.smokeless`. W2F drops its caffeine write and reads
from Bios.

### SoulRadio — the ambient surface

SoulRadio is a sibling, not a companion in the data-bus sense. It does
not read Bios and does not write to the metric bus. Its role in the
ecosystem is the room's air: a 24-hour frequency-band loop that recedes
into the background. The manifesto rule "wallpaper, not wallpaper-paste"
makes auto-switching by biometric a violation by construction.

SoulRadio's own [`docs/ECOSYSTEM.md`](../../SoulRadio/docs/ECOSYSTEM.md)
defines the narrow inbound surface it would accept: `ACTION_SUGGEST_BAND`
(non-modal hint, 5-minute hysteresis) and `ACTION_REQUEST_STOP` (used by
Virgil during an emergency, by W2F during SOS state). No `PLAY_BAND`, no
override of the auto-loop, no biometric reads. Bios may, in the future,
emit suggestions from HRV or arousal state — but only as hints the
listener must reach for.

## Consequences for Bios design

- **No diagnostic verticals in Bios.** If a feature needs a
  specialized model (MS, bipolar, Parkinson's, postpartum, etc.), it belongs
  in a companion. Bios exposes the primitives; companions compose them.
- **No domain-specific active tests in Bios.** Bios *may* host capture
  surfaces for its own sensor adapters (camera PPG, phone-mic respiration),
  import surfaces for slow-moving manual data (manual sleep duration,
  biomarker values, epigenetic-age reports), and read-only awareness
  surfaces (the Data Coverage screen and its "fix this" CTAs). What Bios
  may *not* host is a domain-specific active test whose output is a
  companion-owned metric — keystroke dynamics for mood live in W2F,
  SDMT/tapping for MS live in Fil, grip-strength dynamometers live in a
  physical-tests companion. The test is the producer-by-capture-surface
  principle above: **whose canonical metric does this surface produce?**
  Bios-owned key (camera HR via PPG, manual sleep duration, a logged
  biomarker) → surface can live in Bios. Companion-owned key (typing
  cadence, gait asymmetry, reaction time, SDMT score) → surface lives in
  that companion. The *results* of an active test, wherever it runs, are
  still data; once a second app consumes them, the canonical key belongs
  in Bios's metric bus per the second-consumer rule. See
  [SELF_REPORTED_DATA_HOME.md §5](SELF_REPORTED_DATA_HOME.md). Worked
  examples already on `main`: PPG capture deep link + live preview /
  steadiness coaching (#87, #82), manual sleep duration entry (#86), Data
  Coverage screen with metric-fix CTAs (#83).
- **No capture surfaces beyond sensors.** AccessibilityService, foreground
  fall-detection services, SMS/call handling, call-answering — all companion
  concerns.
- **No social or relational features.** Emergency contacts, message sending,
  location broadcast, shared dashboards — companion concerns.
- **New metric keys require a home.** Before adding a key to `MetricType`,
  identify which companion (or Bios's own engine) produces it and which
  consumes it. If it's a computed score owned by one companion, it still
  needs the canonical key in Bios for the cross-correlation engine.

## Case study: nutrition in W2F

A worked example of how to apply the rule when a companion grows a feature
that sounds out-of-scope. Nutrition *seems* like it belongs in Bios (it
affects physiology) or in a dedicated nutrition app — yet W2F has a
nutrition surface. Why is that correct?

### What W2F actually ships

Three things, under one "nutrition" umbrella:

1. **`FuelLog` entity** — logs `CAFFEINE`, `MEAL`, `SUPPLEMENT` events with
   timestamp and dose. The drift engine consumes a single derived feature:
   **fuel gap** — hours between caffeine and a meal (empty-stomach caffeine
   is a hypomania amplifier). Used as one of seven factors in the
   `InfluencesAnalyzer`.
2. **Supplement adherence** — Magnesium and Riboflavin, tracked because
   they're migraine prophylactics.
3. **`GuideNutritionScreen`** — a static reference page with 22 foods and
   supplements and their rationale for mood/cognitive/physical stability.
   No logging, no tracker — pure education.

### Why each piece is correctly placed

| Piece | Verdict | Reasoning |
|---|---|---|
| FuelLog (caffeine, meal, fuel gap) | ✅ Correct in W2F | Intake logging *because it's a mood signal* — same pattern as typing cadence. Not general nutrition tracking. |
| Static nutrition reference page | ✅ Correct in W2F | Content is framed for mood stability. No capture, no data bus involvement — just in-app education. W2F's own [SCOPE.md](../../W2F/docs/SCOPE.md) explicitly chose this compromise. |
| Supplement adherence (Mg, B2) | ⚠️ Scope-adjacent | Migraine prevention, not mood detection. Tolerable today because it reuses `FuelLog`; should move to a migraine companion if one ever ships. |

### What would make this *wrong*

If W2F grew any of these, it would be drifting into an everything-app:

- Calorie or macro tracking
- Meal planning, recipes, grocery lists
- Weight-loss goals
- Hydration tracking untied to mood
- Nutritional completeness (vitamins, fiber, etc.) as a goal in itself
- Food database lookups or barcode scanning

### The architectural observation

The mood-relevant intake signals (`caffeine_intake`, `meal_timing_variance`,
`fuel_gap_hours`) live only in W2F's local `fuel_logs` table. They are
**not** pushed to Bios. That is fine today — no second consumer exists.

If/when a second app needs intake data (e.g., Fil wants caffeine as an HRV
confounder, or a migraine companion ships), the right move is:

1. Add the keys to Bios's canonical `MetricType` vocabulary
2. Extend the companion-write URI to accept them by adding the keys to the
   appropriate package's entry in `provider.CompanionContract.WHITELIST_BY_PACKAGE`
   — see [CONSUMER_API.md](CONSUMER_API.md). Each key belongs to exactly one
   companion to prevent cross-package spoofing.
3. W2F writes to Bios; the second app reads from Bios

Do not pre-emptively hoist the schema before a second consumer exists. YAGNI
applies to metric keys too — each key is a commitment to stability.

### Rule of thumb distilled from this case

> A companion may capture data *outside* its strict domain **if and only if**
> that data is consumed as a signal *inside* its domain. The moment a second
> app needs to consume it, the data belongs in Bios's metric bus — not in
> two parallel private tables.

## Scope decisions log

A running record of "is X in scope for Bios or a companion?" questions, with
the verdict and the principle that drove it. Append new decisions; do not
rewrite history.

### 2026-05 — Blueprint / Don't Die audit triage

Following the Blueprint and Don't Die investigations (issues #36–#43), the
following scope calls were made. The first three of the manifesto's
prohibitions (never evaluate the person; no behavioral nudges; silence is a
feature) and the second-consumer rule were the load-bearing principles.

| Topic | Verdict | Principle |
|---|---|---|
| Food / macro / calorie log (#36) | ❌ Out of scope — permanently | Inherently evaluative (deficit/surplus, "good food"). Violates "never evaluate the person" + "no behavioral nudges". *(Reasoning revised 2026-05 manifesto-frame revisit — verdict stands, but on operational not philosophical grounds. See subsection below.)* |
| Intervention log — sauna / NIR / HBOT / cold (#39) | ❌ Out of scope | Quantified-self self-experimentation framing ("did X improve my recovery?") is exactly the optimization framing the manifesto rejects. *(Superseded by 2026-05 manifesto-frame revisit — verdict now ✅ in scope as a pull-side event log. See subsection below.)* |
| Self-exam / fertility self-report (#42) | ❌ Out of scope | Self-exam reminders are habit-tracker territory, not health-guardian. Involuntary fertility signal (BBT) already covered by #32. Privacy bar too high for marginal pattern benefit. |
| Hydration logging (`HYDRATION_ML`) | ❌ Out of scope | Manual entry drifts into "are you drinking enough?" behavioral judgment. |
| Water quality / TDS / mineralization | ❌ Out of scope | Blueprint-style optimization, no Bios pattern consumes it. |
| Supplement / medication adherence (#37) | ⏸ Deferred — second-consumer rule | No Bios pattern needs adherence input today. Re-evaluate when a concrete consumer emerges (likely: future migraine / chronic-condition companion). If reserved, mirror Smokeless posture exactly (timestamp + opaque event-id, no substance names). *(Superseded by 2026-05 manifesto-frame revisit — verdict now ✅ in scope, pull-side only. See subsection below.)* |
| Grip strength `GRIP_STRENGTH_KG` (#41) | ⏸ Deferred — second-consumer rule | Re-evaluate when Fil (or a physical-tests companion) ships an active grip test AND a second Bios-side consumer exists. |
| Additional cognitive keys — `N_BACK`, `STROOP`, `DIGIT_SPAN_*`, `PROCESSING_SPEED_SCORE` (#40) | ⏸ Deferred — second-consumer rule | Unlike `REACTION_TIME_MS`, these have only Fil as a consumer. Reserve when Fil's active-test surface ships. |
| `REACTION_TIME_MS` (#40) | ✅ Keep reserved | Two named consumers across two domains: Fil produces (active micro-tests); W2F reads as a cross-check on its passive psychomotor-acceleration signal. Documented producer + reader satisfies the second-consumer rule. |
| Exercise sessions — modality / duration / avg HR / RPE (#38) | ✅ In scope for Bios — auto-derive from adapters | This is passive sensor data (HC `ExerciseSessionRecord`, Garmin/WHOOP/Oura native session entities). No companion, no manual logging UI in Bios. Add `EXERCISE_SESSION` to `MetricType`. |
| Air quality — `AIR_PM25`, `AIR_VOC`, `AIR_CO2` (#43) | ✅ In scope for Bios — BLE adapter pattern | Sensor-grade, passive, confounds nearly every existing pattern (sleep, HRV, infection, respiratory). Fits the existing 9-adapter pattern as a 10th. No `Habitat` companion needed. |

### 2026-05 — Producer-by-capture-surface audit

After codifying the rule that producer is determined by the unique capture
surface (not by the score's consumer), audited every companion-injected
key against it. One key was found miscategorized.

| Key | Before | After | Reasoning |
|---|---|---|---|
| `CIRCADIAN_PHASE_SHIFT` | W2F-produced | **Bios-produced** | Inputs are sleep-onset times — canonical Bios data from all 9 adapters. Cosinor/DLMO math is universal, not mood-specific. W2F's existing `CircadianCalculator` should be hoisted into Bios; W2F reads the result instead of computing locally. |
| `typing_cadence`, `mood_drift_score` | W2F-produced | **W2F-produced** (affirmed) | Requires AccessibilityService surface / mood-specific composite. No alternative producer. |
| `gait_asymmetry`, `motor_score`, `relapse_risk` (planned) | Fil-produced | **Fil-produced** (affirmed) | Requires foreground accel service / MS-specific composites. No alternative producer. |
| `cognitive_speed` (planned) | Fil-produced | **Decide at landing** | If output of active SDMT/tapping test → Fil. If composite over canonical inputs (typing-cadence + reaction-time + HRV) → Bios. Do not reserve in `MetricType` until the producing surface is concrete. |
| Substance events, fall events, reaction-time, biomarkers, etc. | as-is | **affirmed** | Each requires a surface (tap-to-log, fall service, active test, lab draw) the producer uniquely owns. |

### 2026-05 — Active-capture-surface line for Bios

The "Bios is passive, no active tests" wording predated three Bios-side
surfaces that ask the user to *do* something: camera PPG capture (live
preview + steadiness coaching), manual sleep duration entry, and the Data
Coverage screen's metric-fix CTAs. Each is correctly placed — but the rule
as written would have predicted otherwise. The rule was tightened to
"no *domain-specific* active tests in Bios" with the producer-by-capture-
surface principle as the test: surfaces that produce Bios-owned keys are
in scope, surfaces that produce companion-owned keys are not.

| Surface | Verdict | Reasoning |
|---|---|---|
| Camera PPG (`bios://capture/ppg`, live preview, steadiness coach) | ✅ Bios | Produces `HEART_RATE_BPM` — a Bios-owned key. Camera is a Bios sensor adapter, not a companion-specific test. |
| Manual sleep duration entry | ✅ Bios | Produces `SLEEP_DURATION` — Bios-owned. Slow-moving, biomarker-style manual import, not a domain-specific active test. |
| Data Coverage screen + "fix this" CTAs | ✅ Bios | Read-only awareness over Bios's own metric inventory. No companion-owned data, no domain-specific judgment. |
| Hypothetical SDMT / tapping / contrast micro-test | ❌ Companion (Fil) | Produces Fil-owned cognitive scores. Domain-specific active test. |
| Hypothetical keystroke-cadence capture UI | ❌ Companion (W2F) | Produces `typing_cadence` — W2F-owned. AccessibilityService surface, mood-specific consumer. |
| Hypothetical grip-strength dynamometer test | ❌ Companion (physical-tests) | Produces an active-test key with no canonical Bios producer. |

### 2026-05 — Manifesto-frame revisit (post-PR #103)

[MANIFESTO.md](../MANIFESTO.md) Principle 7 ("instrument, not coach") replaced
the earlier "never evaluate the person" load-bearing constraint with a
push/pull distinction: **unsolicited push-side evaluation is prohibited;
owner-pulled / owner-driven surfaces are not.** The 2026-05 Blueprint audit
above made two rejections on grounds that no longer hold — both cited the
old framing's "the manifesto rejects optimization" reasoning, which the new
manifesto explicitly authorizes ("steer your own course over time — toward
maintaining what works and improving what doesn't").

Verdict revisions:

| Topic | Old verdict | New verdict | Reasoning |
|---|---|---|---|
| Food / macro / calorie log (#36) | ❌ "inherently evaluative" | ❌ unchanged — **reasoning rewritten** | The new frame doesn't disqualify "inherently evaluative" on its own. Real disqualifiers are operational: multi-meal-per-day capture surfaces have well-documented abandonment curves, and the sensor stack (glucose variability via Dexcom, post-meal HR/HRV) already produces the metabolic signal without owner typing. |
| Intervention log — sauna / NIR / HBOT / cold (#39) | ❌ "optimization framing the manifesto rejects" | ✅ **in scope as a pull-side event log** | The old rejection cited a frame the manifesto no longer holds. Surface is admissible under the new principle. Constraint rides on the design rule below. |
| Supplement / medication adherence (#37) | ⏸ deferred — second-consumer rule | ✅ **in scope, pull-side only** | The owner-pulled correlation IS the second consumer when the owner asks "did I take the magnesium on the nights I slept poorly?" The trigger fires as soon as the owner navigates into that comparison. Storage shape per the original Smokeless posture (timestamp + opaque event-id, no substance names) still right. |

Decisions the frame change does **not** move (unchanged from above): #41
Grip strength (still gated on the producer + second-consumer rule, both
independent of push/pull); #42 self-exam (habit-tracker territory
regardless of frame); hydration (the behavioral-judgment slippery slope is
a push-side concern, still applies); water quality (no Bios consumer in
either direction); the other "in scope / affirmed" rows.

### Design rule for owner-driven pull-side logs

When a manual logging surface is admitted under the new frame
(intervention log, supplement/medication adherence, future analogous
surfaces), the **firewall lives in the UI design line, not the feature
line.** The surface must not host engagement-app DNA. Specifically:

- **No streak counters, no completeness percentages, no daily-goal gauges.**
- **No "you haven't logged today" empty states or daily-reminder UI.**
- **No "log everything to see results" prompts.** Sparse logging is
  expected and treated as a first-class state.
- **Empty state reads what's true** — "no interventions logged in this
  window" — full stop, no nudge.
- **Correlation surfaces show what's there**, say nothing about what's
  missing. Sparse log → sparse chart → honest insight.
- **Bios does not ask for data.** It works with whatever the owner gives.

These rules apply to pull surfaces because pull-side absolutism alone
doesn't prevent engagement loops — empty boxes pull at people, "did X
improve my recovery?" charts re-create the optimization pathology even
without a single notification firing. The defence is structural: don't
build the pieces engagement apps use to drive sessions.

The rule does **not** apply to:
- Bios-state pushes (the third push category — "Oura hasn't synced in 5
  days") — those are about Bios's plumbing, not the owner.
- Pull surfaces that don't host owner-typed event data (Data Coverage,
  trends, alerts review) — these were never at risk of the loop.

- [ARCHITECTURE.md](ARCHITECTURE.md) — Bios system components
- [CONSUMER_API.md](CONSUMER_API.md) — `BiosHealthProvider` contract
- [DATA_MODEL.md](DATA_MODEL.md) — canonical metric schema
