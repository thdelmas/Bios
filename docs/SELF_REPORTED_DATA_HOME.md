# Self-Reported Data — Architectural Decisions

Bios was built around sensor data: continuous, probabilistic, dedup-by-timestamp.
A 2026-05 audit of the ecosystem (Bios, W2F, Smokeless, Virgil, SoulRadio,
Fil) surfaced ~80 distinct data points sitting in companion apps that never
reach Bios's metric bus — much of it self-reported by the owner (mood
ratings, symptom severity, intake events, active-test outcomes).

This doc records the architectural decisions for giving that data a home in
Bios without breaking the sensor-first model that already works. Companion
doc: [ECOSYSTEM_BOUNDARIES.md](ECOSYSTEM_BOUNDARIES.md) defines *who owns
what*. This doc defines *how the ecosystem represents data the owner enters
about themselves*.

## The principle

> Bios consumes self-reported data; Bios is never the entry point.

Logging surfaces belong in specialty apps (W2F for mood, Smokeless for
substances, …). Bios reads what flowed in via the companion-write
ContentProvider, baselines and correlates the parts that make sense, and
displays the rest with provenance. Adding "log X directly in Bios" UI
collapses Bios into a generic journal and is rejected by construction.

The existing `QuickSymptomCard` on Home is owner-protection scaffolding
(quick-jot when no companion is installed), not a primary entry surface.

## Decisions

### 1. `ReadingKind` enum on `DataSource`

Add an enum: `SENSOR`, `SELF_REPORTED`, `DERIVED`, `ACTIVE_TEST`. Lives on
`DataSource`, not on each `MetricReading` — it's a property of the source,
not of every row. Existing sources get tagged at migration; new sources
declare their kind at registration.

**Why:** The downstream engine policies (decision #3) need to filter on
this. Without a tag, `BaselineEngine` will silently corrupt sensor baselines
as soon as self-reports start flowing for the same metric.

**Alt rejected:** Per-row kind on `MetricReading` — denormalized,
forgettable on inserts, and the kind is effectively immutable per source
anyway.

### 2. `LoggedEvent` table parallel to `MetricReading`

Events (substance use, fall, acute symptom episode, check-in miss) are not
metrics. They have severity, duration, optional notes, and categorical
types — none of which fit `MetricReading(value: Double, timestamp: Long)`.

Schema (sketch — exact fields TBD in implementing PR):

- `id`, `eventType` (enum), `timestamp`
- `severity?`, `durationMs?`, `value?` (for events that carry a number)
- `sourceId`, `packageName` (provenance)
- `note?` (free-text, for owner recall — never analyzed)

**Migrates:**

- Smokeless events (today encoded awkwardly as `MetricReading.value = 1.0`)
- Bios's `HealthEvent` table (already separate, but inconsistent shape)
- Virgil's reserved keys when wired
- W2F's `acute_events` if/when hoisted

**Why:** Forcing events into `MetricReading` is already awkward (Smokeless)
and gets worse as more event-shaped data flows in. A parallel table keeps
both schemas clean and lets engine policies treat them differently without
per-row branching.

**Alt rejected:** Extend `MetricReading` with optional event columns —
schema bloat, every consumer must check `kind` to know which fields are
meaningful.

### 3. Engine isolation by kind

- `BaselineEngine`: skip `kind != SENSOR`.
- `SignalQualityFilter`: skip `kind != SENSOR` (rate-of-change checks are
  nonsense for journal entries).
- Pattern detection on self-reports and events lives in a **separate,
  gentler engine** — never the same anomaly-detection machinery as sensor
  data.

**Why:** A baseline of self-reported sleep is a baseline of *perception*,
not physiology — statistically meaningless to mix. Beyond statistics,
running anomaly detection on self-reports crosses the alignment principle:
"your reported mood is 1.5σ below baseline" *is* evaluating the person,
which Bios explicitly does not do.

Self-reports and events are: **displayable** (transparency),
**correlatable** as context for sensor-derived alerts (HRV down + user
reports poor sleep = stronger signal than HRV alone), and
**pattern-detectable** with descriptive language only ("you've logged poor
sleep 4 nights in a row" — never "your sleep is degrading").

### 4. Structured symptom taxonomy as Bios canon

Adopt W2F's severity scheme as the ecosystem-wide vocabulary: `SymptomKind`
enum (FOG, DIZZINESS, PHOTOPHOBIA, MIGRAINE, NAUSEA, …, plus OTHER for
free-text fallback) with 0–3 severity. Lives in `bios-contracts` so any
companion can produce conforming entries.

**Why:** Cross-app correlation requires a shared vocabulary. W2F's
photophobia + Fil's gait drift could be a migraine prodrome — but only if
both apps speak the same word. Today Bios's `QuickSymptomCard` is
free-text; nothing can correlate against it.

**Alt rejected:** Per-app taxonomies (chaos, no correlation) or free-text
only (Bios stays silent on symptom signals forever).

**Open:** Final enum membership and 0–3 vs 0–10 scale. Default to W2F's
existing scheme to avoid breaking already-shipped W2F UX.

### 5. Pre-reserve `reaction_time_ms` (and active-test keys generally)

Add `reaction_time_ms` to `MetricType` now, without whitelisting any
companion to write it yet. W2F has PVT data today (`cognitive_probes`
table); Fil plans SDMT. The second-consumer trigger is implicit but already
firing.

**Why:** Reserving an enum key is essentially free. Coordinating a
multi-app PR after Fil ships is not. Pre-reservation cuts future
coordination cost and forces an upfront commitment to the key shape before
two apps invent two different shapes for the same signal.

**Rule for active-test results generally:** ECOSYSTEM_BOUNDARIES.md
correctly says "no active tests in Bios — they belong in companions." That
applies to *executing* tests. *Results* are data, and data with multiple
consumers belongs on the bus. The boundaries doc gets a sentence to that
effect alongside this PR.

### 6. Bios is a consumer, never the entry point — period.

Codified above as the principle. Called out as a separate decision because
future asks of "let me log sleep directly in Bios" will be plausible and
worth refusing every time.

## Open questions (decide before implementing)

- **Per-metric consent within per-package grants.** Today `CompanionGrant`
  is binary per package. With self-reports flowing in, the owner may want
  W2F sleep but not W2F mood. Add a per-metric dimension to the grant
  model now, or defer until the asymmetry actually bites?
- **Migration of existing Smokeless writes.** Smokeless's tobacco/cannabis
  events live in `MetricReading.value=1.0` today. Migrate them to
  `LoggedEvent` (cleaner) or leave them on the legacy shape (less churn)?
- **Who owns the canonical `SymptomKind` enum.** `bios-contracts` is the
  natural home, but the enum needs real clinical input on membership.
  Defer until a clinician collaborator is in the loop?

## What this doc is NOT

- A full ecosystem inventory. Inventories rot fast (the audit that produced
  this doc found one already-rotted example in `ECOSYSTEM_BOUNDARIES.md`).
  Regenerate on demand from source.
- An implementation plan. Each decision spawns its own PR with its own test
  plan.
- A commitment to schema field names. The shapes are committed; column
  names will be settled in the implementing PRs.

## Cross-references

- [ECOSYSTEM_BOUNDARIES.md](ECOSYSTEM_BOUNDARIES.md) — who owns which
  domain, the second-consumer rule
- [DATA_MODEL.md](DATA_MODEL.md) — current `MetricReading` schema
- [CONSUMER_API.md](CONSUMER_API.md) — `BiosHealthProvider` and
  `CompanionContract`
