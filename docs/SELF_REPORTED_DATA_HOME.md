# Self-Reported Data — Architectural Decisions

Bios was built around sensor data: continuous, probabilistic, dedup-by-timestamp.
A 2026-05 audit of the ecosystem (Bios, W2F, Smokeless, Virgil, SoulRadio) surfaced ~80 distinct data points sitting in companion apps that never
reach Bios's metric bus — much of it self-reported by the owner (mood
ratings, symptom severity, intake events, active-test outcomes).

This doc records the architectural decisions for giving that data a home in
Bios without breaking the sensor-first model that already works. Companion
doc: [ECOSYSTEM_BOUNDARIES.md](ECOSYSTEM_BOUNDARIES.md) defines *who owns
what*. This doc defines *how the ecosystem represents data the owner enters
about themselves*.

## The principle

> Bios consumes self-reported data; Bios is never the entry point.

This was the starting principle; decision 6 below narrows it. Logging
surfaces for data a companion owns belong in that companion. Bios stores
what flows in through the companion-write ContentProvider and displays it
with its source. It does not baseline, correlate or comment on it.

The quick entry card on the Log screen (`QuickSymptomCard`) stores a
free-text note as written.

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
- The deviation alert reads sensor readings only.

**Why:** A baseline of self-reported sleep is a baseline of *perception*,
not of a measurement. Mixing the two is statistically meaningless. And a
computed statement about what the owner typed ("your reported mood is
1.5σ below baseline") is an evaluation of the person, which Bios does not
make. The guarantees here (`BaselineEngine` and `SignalQualityFilter`
skip `kind != SENSOR`) hold that line in code.

Self-reports and events are **displayed** and **exported** as written,
with their source. Nothing in Bios correlates them with sensor data or
describes a pattern in them.

> Superseded by `specs/instrument-only.md`: an earlier version of this
> decision planned a separate engine for patterns in self-reports and the
> use of self-reports as context for alerts. Neither exists.

### 4. Structured symptom taxonomy as Bios canon

> Superseded by `specs/instrument-only.md`. This decision proposed a shared
> `SymptomKind` enum with a severity scale in `bios-contracts`, so that
> entries from different apps could be correlated. It was not implemented,
> and Bios does not correlate entries. The quick entry card stores free
> text.

### 5. Pre-reserve `reaction_time_ms` (and active-test keys generally)

Add `reaction_time_ms` to `MetricType` now, without whitelisting any
companion to write it yet. W2F has PVT data today (`cognitive_probes`
table); The second-consumer trigger is implicit but already
firing.

**Why:** Reserving an enum key is essentially free. Coordinating a
multi-app PR after a second producer ships is not. Pre-reservation cuts future
coordination cost and forces an upfront commitment to the key shape before
two apps invent two different shapes for the same signal.

**Rule for active-test results generally:** ECOSYSTEM_BOUNDARIES.md
correctly says "no active tests in Bios — they belong in companions." That
applies to *executing* tests. *Results* are data, and data with multiple
consumers belongs on the bus. The boundaries doc gets a sentence to that
effect alongside this PR.

### 6. Bios hosts only the entry points its own canonical data needs.

Originally framed as *"Bios is a consumer, never the entry point —
period."* That absolute is now wrong-as-written: since then, BBT manual
entry (`BbtEntryRepo`), biomarker entry (`BiomarkerEntryRepo`), manual
sleep duration, lab-report attachment (PR #106), and the general
entry surface for readings taken elsewhere (`ManualReadingRepo`, issue #131) have
all shipped as legitimate Bios-hosted entry surfaces.

The corrected rule — which all five shipped surfaces already satisfy —
is the same producer-by-capture-surface line codified in
[ECOSYSTEM_BOUNDARIES.md](ECOSYSTEM_BOUNDARIES.md) ("No domain-specific
active tests in Bios"). **Bios hosts an entry point if and only if the
data being entered is a canonical Bios-owned key with no other producer
available.** Biomarkers are typed because no sensor draws blood; BBT is
typed because no wearable on the owner's wrist measures basal body
temperature; manual sleep is typed when Health Connect / Oura / Gadgetbridge
are all silent; readings from a BP cuff, pulse oximeter or thermometer
are typed because the LETHE-correct case is a degoogled phone
with no Health Connect at all — the owner *is* the producer, not just
the reader. Bios is *not* the entry point for data a companion uniquely
owns — typing cadence belongs in W2F, fall-event in
Virgil.

The gate is now per-metric: [MetricType.allowsManualEntry] declares
which keys accept owner-as-source entry. Engine isolation still holds —
manual entries flow through SELF_REPORTED and skip the baseline and
the deviation alert per decision 3 above.

The "future asks of 'let me log sleep directly in Bios'" line from the
old wording was directionally right but the verdict's been wrong: the
manual-sleep-entry surface that landed in the meantime is the right
shape, and refusing it would have left a real gap. The rule above is
what we apply going forward.

## Open questions (decide before implementing)

- **Per-metric consent within per-package grants.** Today `CompanionGrant`
  is binary per package. With self-reports flowing in, the owner may want
  W2F sleep but not W2F mood. Add a per-metric dimension to the grant
  model now, or defer until the asymmetry actually bites?
- **Migration of existing Smokeless writes.** Smokeless's tobacco/cannabis
  events live in `MetricReading.value=1.0` today. Migrate them to
  `LoggedEvent` (cleaner) or leave them on the legacy shape (less churn)?

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
