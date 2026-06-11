# Vessel Watch — Owner

**Status:** spec, 2026-06-04. The owner-subject readout that points Bios at the
one thing that can't be backed up: the body/brain as the substrate of continued
agency. This is the *long* dial (chronic substrate, aging), not an acute monitor.

**Frame source:** Miam KB `docs/life/the-vessel-watch.md`. In one line: the
projects back up the *value* (delegable, survives the owner); the body backs up
nothing — it's the single, non-delegable carrier of agency, and agency is what
dignity stands on. So its upkeep is the floor under everything. This file keeps
the floor in view.

## Manifesto compliance (non-negotiable, read before extending)

This watch obeys [MANIFESTO §7 — Instrument, not coach](../../../MANIFESTO.md) and
the [no-derived-score rule](../../DATA_MODEL.md):

- **No composite "vessel score."** Every dial stands alone with its own trend.
  Bios never sums them into a verdict. Interpretation is the owner's.
- **Pull, not push.** No nudges, no alerts, no "you should." The watch is a
  surface you *look at*; it stays silent otherwise. (POST_NOTIFICATIONS state is
  irrelevant to it by design.)
- **No shame-frame.** Per the [Smokeless](../../../README.md) rule — never
  "Day-1 / collapsed," never a streak you fail. Trends, not judgments. A vessel
  watch you can't fail at gracefully is pride, not protection.
- **Bios points; the clinic reads.** An out-of-range dial routes the owner to a
  professional. Bios does not diagnose. See the boundary below.

---

## 0. The boundary — what this watch does NOT see (read first)

The owner's current acute concerns (2026-06-04) are **fast-blurring vision,
worsening headaches, degraded thinking.** Bios has **no metric** for any of the
three — no vision acuity, no headache, no acute cognition key. They are out of
instrument range and route to a **clinician** (eye exam + blood-pressure check
first; red-flags → GP/urgent).

> **Anti-false-reassurance clause.** A wall of green vessel dials does **not**
> clear those symptoms. Different instrument. The benign cause and the rare one
> feel identical from the inside — that is the entire reason for an outside
> reading. Never let this surface imply "my Bios looks fine, so I'm fine."

Instrument-not-coach also means *instrument-knows-its-limits.* This is the most
important section in the file.

---

## The dials (curated from existing `MetricType` keys)

Grouped by what they read about the substrate. State = on the owner's device per
[PROFILE.md](./PROFILE.md).

### A. Vascular substrate — the circuit the acute symptoms sit on, and tobacco's footprint
| Dial | Key | State | Fill |
|---|---|---|---|
| **Blood pressure** | `blood_pressure_systolic/diastolic` | **MISSING — highest value to add** | manual cuff entry. Headache + vision + smoker → BP is the #1 missing dial. |
| Resting HR | `resting_heart_rate` | LIVE | Fitbit→HC |
| HRV (RMSSD) | `heart_rate_variability` | LIVE | nightly |
| SpO₂ | `blood_oxygen` | LIVE | nightly |
| Arterial stiffness | `augmentation_index_ppg`, ppg-morphology keys | DERIVED on-demand | run `bios://capture/ppg` — vascular-aging readout, tobacco-sensitive |
| Lab vascular risk | ApoB, Lp(a), homocysteine | MISSING | annual draw — see [LAB_PANEL_ORDER.md](./LAB_PANEL_ORDER.md) |

### B. Metabolic substrate
| Dial | Key | State |
|---|---|---|
| Glucose / HbA1c / fasting insulin | `blood_glucose`, lab keys | manual / lab |
| Body mass, body-fat % | `body_mass`, `body_fat_pct` | LIVE if Aria |

### C. Recovery & sleep — cognition and repair run on this (the chronic side of "harder to think")
| Dial | Key | State |
|---|---|---|
| Sleep duration + deep + REM | `sleep_duration`, `sleep_stage` | LIVE |
| Recovery | `recovery_score` | LIVE |
| Overnight RHR / respiratory rate | `resting_heart_rate`, `respiratory_rate` | LIVE |

### D. The aging dial (the one the owner named)
| Dial | Key | State |
|---|---|---|
| Pace of aging | `epigenetic_age_dunedin_pace` | implemented — manual entry |
| Biological age clocks | `epigenetic_age_grim/pheno/horvath` | implemented — manual entry; **displayed standalone, never composed** |
| VO₂max | `vo2_max` | LIVE — strongest functional-capacity / longevity correlate |

### E. Neuro-motor reserve — the agency edge (mostly not yet instrumented)
| Dial | Key | State |
|---|---|---|
| Gait symmetry | `gait_symmetry` | PLANNED |
| Tremor | `tremor_amplitude` | PLANNED |
| Cognitive throughput (SDMT / keystroke) | via Fil | PLANNED |

> **Honest gap:** the dials most directly about *agency* (motor + cognitive
> reserve) are the least instrumented today. The watch names this rather than
> hiding it behind the dials that happen to be live.

### F. Substance load — the degrading input (not a verdict)
| Dial | Source | State |
|---|---|---|
| Tobacco + cannabis | [Smokeless](../../../README.md) ledger → Bios | per Smokeless P2.1 writes |
| Caffeine / alcohol | manual | reference |

Surfaced to **correlate** against A and C — never to score or shame. Honest
reading: tobacco sits in the same vascular wiring as the acute cluster, so it's
upstream data, not a separate moral line.

---

## Next actions to make the watch real (owner's call, ranked by yield)

1. **Add blood pressure** — manual cuff entries. Highest-value missing dial given current symptoms; also the cheap clinical check the boundary section points to.
2. **Run a camera PPG** (`bios://capture/ppg`) — instantiates arterial-stiffness + autonomic dials with no new hardware.
3. **Enter the annual lab panel** (ApoB, Lp(a), homocysteine, HbA1c, fasting insulin) from the [LAB_PANEL_ORDER.md](./LAB_PANEL_ORDER.md) draw.
4. **Enter epigenetic clock values** at next test → the aging dial goes live.
5. *(Future)* neuro-motor reserve via Fil integration — the agency edge.

> Item 1 is also literally the acute next step. The vessel watch and the clinic
> visit point at the same first move: get a blood-pressure number.
