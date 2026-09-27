# Mishra 2020 replay — first measured detection figures

**Produced:** 2026-09-27, Bios `main`, engine unchanged except for an injected
clock and the ABSENT-rule prior-presence gate landed in the same commit.
**Harness:** `android/app/src/test/java/com/bios/app/validation/MishraReplayTest.kt`
(see `tools/validation/README.md` to reproduce).
**Dataset:** Mishra et al. 2020, *Pre-symptomatic detection of COVID-19 from
smartwatch data*, Nat Biomed Eng. Fitbit heart rate (5 s), steps (1 min) and
sleep stages for 32 COVID-19-positive participants, 30 with a symptom-onset
date. Dates are shifted per participant by the study authors.

## What the numbers say

1. **`infection_onset` detected nobody: 0/24 pre-symptomatic, 0/24 late, 0
   false alarms.** The pattern requires 3 concurrent signals out of 6 (RHR,
   HRV, skin temperature, respiratory rate, sleep stage, steps). Fitbit-class
   data carries only RHR, steps and sleep stage, and on this data the steps
   and sleep rules essentially never trigger (0/24 and 1/24 participants with
   a single hit in the two weeks before onset). The gate is therefore
   unreachable for any owner whose wearable lacks HRV, temperature or
   respiratory rate. That covers COROS via Health Connect today.
2. **The RHR rule alone reproduces the literature's sensitivity but not its
   specificity.** 21/24 (88%) participants crossed +1.5σ at least once in the
   14 days before onset, median lead 9 days. The same threshold also fired on
   120 of 1,138 healthy person-days, 3.2 alarms per person-month, against
   0.66 for the paper's CuSum on the same participants. A daily z-score
   against a 14-day window is a noisy detector; the paper's method needs
   28 days of baseline and cumulative evidence, not one day.
3. **The steps rule scores the wrong unit.** The engine averages the last
   24 hours of hourly buckets and compares against the standard deviation of
   hourly buckets. Hourly variance is so wide that a day whose total fell
   from about 3,100 to about 700 steps (participant A0NVTRV, day after onset)
   does not reach −1σ. A daily-total baseline would.
4. **Sparse resting-HR days leave owners unmonitored.** Two participants
   (AJ7TSV9, A36HR6Y) never got an RHR baseline because fewer than 10 of any
   14 consecutive days had a qualifying overnight window; six more had no
   monitored day inside their pre-onset window. Production RHR from a
   vendor's daily value will be denser than this derivation, but the
   10-of-14 minimum is the same gate.
5. **`cessation_recovery_pattern` fired on every wearable-only participant
   in the first run.** Its required rule was "no tobacco readings for 72 h",
   which is true for anyone without a tobacco companion. Fixed in the same
   commit: ABSENT rules now require the metric to have been present in the
   preceding 90 days. Second run: 0 alerts on 1,138 healthy person-days.

## What Bios may say publicly, as of this run

- "Implements the Mishra 2020 resting-heart-rate rule": true.
- "Detects infection onset early": not supported. The shipped gate did not
  fire once on 30 confirmed cases.
- Any sensitivity or lead-time claim must cite this page and its date.

## Limits of this replay

- RHR was derived here (mean of 00:00–07:00 samples with zero steps in the
  preceding 12 min). Fitbit's own daily RHR, or COROS's, is smoother; the
  false-alarm figure for the RHR rule is therefore an upper bound.
- No control group: sleep files exist only for the 32 positives, so healthy
  windows of the same people stand in for controls.
- Baseline windows include the current day, as in production.
- Patterns other than `infection_onset` were evaluated but not scored against
  labels.

---

# Mishra 2020 replay — `infection_onset` pattern

Shipped engine (BaselineEngine 14-day window, AnomalyDetector, all applicable patterns) replayed day by day, clock at 23:00 UTC, over Fitbit data of COVID-19-positive participants from Mishra et al. (2020), Nat Biomed Eng. Inputs: daily resting HR (mean of 00:00–07:00 HR samples with zero steps in the preceding 12 min), hourly steps, sleep-stage segments. No HRV, skin temperature or respiratory rate exist in this dataset, so at most 3 of the pattern's 6 signals can ever be active; the pattern requires 3.

| Figure | Value |
|---|---|
| Participants replayed | 30 |
| Participants with monitored days in the pre-symptomatic window (onset−14 d … onset) | 24 |
| Sensitivity, pre-symptomatic (`infection_onset` alert in window) | 0/24 (0%) |
| Late detection only (onset+1 … onset+7) | 0/24 (0%) |
| Median lead time, days before onset (detected cases) | n/a |
| Healthy person-days (>21 d before onset or >7 d after recovery) | 1138 |
| `infection_onset` false alarms per person-month, healthy days | 0.00 (0 alerts) |
| Any-pattern alerts per person-month, healthy days | 0.00 (0 alerts) |

## Rule-level diagnostic (pre-symptomatic window, shipped thresholds)

| Rule | Participants with ≥1 hit |
|---|---|
| RHR > +1.5σ (24 h mean vs 14 d baseline) | 21/24 (88%) |
| Steps < −1.0σ | 0/24 (0%) |
| Sleep-stage mean < −1.0σ | 1/24 (4%) |
| ≥3 rules active on the same day (the pattern's gate) | 0/24 (0%) |
| ≥2 rules active on the same day | 0/24 (0%) |

## RHR rule alone (what an RHR-led gate would score on this data)

| Figure | Value |
|---|---|
| Sensitivity, pre-symptomatic (RHR > +1.5σ on ≥1 day in onset−14 d … onset) | 21/24 (88%) |
| Median lead time, days before onset | 9 |
| Lead-time range | 0–14 |
| False alarms per person-month, healthy days (days with RHR > +1.5σ) | 3.16 (120 days) |
| Reference: Mishra 2020 CuSum, same data | 62.5% on or before onset; ~0.66 alarms/month healthy |

## Per participant

| User | Onset | Monitored d | Pre-window d | First pre alert | First post alert | Healthy d | Healthy infection alerts | Healthy any alerts | RHR hit | Steps hit | Sleep hit | Max active |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A0NVTRV | 2023-12-06 | 56 | 13 | - | - | 22 | 0 | 0 | true | false | false | 1 |
| A0VFT1N | 2023-10-13 | 74 | 15 | - | - | 38 | 0 | 0 | true | false | false | 1 |
| A1K5DRI | 2028-01-21 | 74 | 0 | - | - | 74 | 0 | 0 | false | false | false | 0 |
| A1ZJ41O | 2027-08-06 | 77 | 1 | - | - | 61 | 0 | 0 | true | false | false | 1 |
| A36HR6Y | 2023-05-18 | 0 | 0 | - | - | 0 | 0 | 0 | false | false | false | 0 |
| A3OU183 | 2024-11-23 | 40 | 11 | - | - | 23 | 0 | 0 | true | false | false | 1 |
| A4E0D03 | 2028-03-13 | 61 | 0 | - | - | 0 | 0 | 0 | false | false | false | 0 |
| A4G0044 | 2027-03-04 | 81 | 15 | - | - | 33 | 0 | 0 | false | false | false | 0 |
| A7EM0B6 | 2023-12-26 | 54 | 13 | - | - | 16 | 0 | 0 | true | false | false | 1 |
| AA2KP1S | 2025-01-06 | 69 | 15 | - | - | 43 | 0 | 0 | true | false | false | 1 |
| AAXAA7Z | 2023-03-30 | 51 | 15 | - | - | 10 | 0 | 0 | true | false | true | 1 |
| AFPB8J2 | 2026-07-14 | 62 | 14 | - | - | 18 | 0 | 0 | true | false | false | 1 |
| AHYIJDV | 2025-01-16 | 66 | 15 | - | - | 29 | 0 | 0 | true | false | false | 1 |
| AIFDJZB | 2023-11-07 | 119 | 15 | - | - | 91 | 0 | 0 | true | false | false | 1 |
| AJ7TSV9 | 2025-01-28 | 0 | 0 | - | - | 0 | 0 | 0 | false | false | false | 0 |
| AJMQUVV | 2024-09-05 | 83 | 15 | - | - | 42 | 0 | 0 | true | false | false | 1 |
| AJWW3IY | 2024-08-09 | 63 | 15 | - | - | 28 | 0 | 0 | true | false | false | 1 |
| AKXN5ZZ | 2028-01-16 | 81 | 15 | - | - | 48 | 0 | 0 | true | false | false | 1 |
| AMV7EQF | 2027-06-09 | 76 | 15 | - | - | 47 | 0 | 0 | true | false | false | 1 |
| AOYM4KG | 2023-08-29 | 77 | 15 | - | - | 32 | 0 | 0 | true | false | false | 1 |
| APGIB2T | 2023-05-22 | 79 | 13 | - | - | 33 | 0 | 0 | true | false | false | 1 |
| AQC0L71 | 2028-06-17 | 42 | 15 | - | - | 42 | 0 | 0 | true | false | false | 1 |
| AS2MVDL | 2025-03-02 | 82 | 15 | - | - | 30 | 0 | 0 | false | false | false | 0 |
| ASFODQR | 2024-08-14 | 119 | 15 | - | - | 86 | 0 | 0 | true | false | false | 1 |
| ATHKM6V | 2024-03-06 | 39 | 7 | - | - | 31 | 0 | 0 | true | false | false | 1 |
| AV2GF3B | 2026-02-12 | 36 | 7 | - | - | 3 | 0 | 0 | true | false | false | 1 |
| AX6281V | 2024-02-08 | 56 | 0 | - | - | 56 | 0 | 0 | false | false | false | 0 |
| AYEFCWQ | 2025-07-07 | 118 | 6 | - | - | 105 | 0 | 0 | false | false | false | 0 |
| AYWIEKR | 2023-04-07 | 68 | 12 | - | - | 34 | 0 | 0 | true | false | false | 1 |
| AZIK4ZA | 2024-07-29 | 63 | 0 | - | - | 63 | 0 | 0 | false | false | false | 0 |

## Other patterns fired (all days, all participants)

| Pattern | Alerts |
|---|---|
