# TimesFM 2.5 replay: a pretrained forecaster as the RHR baseline

**Produced:** 2026-09-29, Bios `main` at `ade599bd`, engine untouched.
**Harness:** `tools/validation/timesfm_replay.py` (Python, offline; see
`tools/validation/README.md`).
**Dataset and labels:** same as [mishra-2020-replay.md](mishra-2020-replay.md).
**Model:** TimesFM 2.5, 200M parameters, Apache-2.0 weights, CPU inference.

## Question

Gap G11 of the Mishra replay: the shipped resting-heart-rate rule finds 21 of
24 cases before symptom onset but raises 3.16 alarm days per person-month on
healthy days. Would replacing the rolling baseline with a TimesFM one-step
forecast and its quantile band give fewer false alarms for the same detection?

- **Intent:** earlier and quieter detection from the one signal every wearable has.
- **Goal:** at 21/24 detected, fewer alarm days than the shipped rule; or at
  0.66 alarm days per person-month, more cases detected.
- **Limit:** a 200M-parameter model does not run inside the app. A gain has to
  be large enough to justify a different architecture.

## Verdict

**TimesFM 2.5 does not fix G11.** At the shipped sensitivity it is noisier
than the shipped rule. At strict false-alarm budgets it detects a few more
cases, a difference of two to four participants out of 24, too small to
separate from chance. The forecast itself is only 7 to 12% more accurate than
a rolling mean on this series.

## Control

The shipped rule re-implemented in Python reproduces the Kotlin harness
exactly: 24 evaluable, 21 detected, median lead 9 days, 120 alarm days on
1,138 healthy person-days. All detectors below are scored on the same
monitored person-days.

## Results

Score for TimesFM: `(value - q50) / ((q90 - q10) / 2.563)`, a z-equivalent
built from the forecast band. The context is the n calendar days before the
scored day, gaps interpolated, at least 10 real readings.

### Same detection as the shipped rule (at least 21 of 24)

| Detector | Threshold | Detected | Median lead (d) | Alarm days / person-month, healthy windows of cases | Same, 73 healthy participants |
|---|---|---|---|---|---|
| Shipped rule, 14-day baseline | 1.5 | 21/24 | 9 | 3.16 | 2.66 |
| z-score, 28-day baseline | 1.4 | 22/24 | 7 | 3.30 | 2.83 |
| TimesFM, 14-day context | 1.3 | 21/24 | 11 | 3.85 | 3.93 |
| TimesFM, 28-day context | 1.4 | 22/24 | 10 | 3.48 | 3.46 |
| TimesFM, 64-day context | 1.4 | 21/24 | 9 | 3.48 | 3.35 |

### Same false-alarm budget as the paper (at most 0.66 alarm days / person-month)

| Detector | Threshold | Detected | Median lead (d) | Alarm days / person-month, healthy windows of cases | Same, 73 healthy participants |
|---|---|---|---|---|---|
| Shipped rule, 14-day baseline | 2.5 | 4/24 | 6 | 0.66 | 0.55 |
| z-score, 28-day baseline | 2.5 | 4/24 | 5 | 0.61 | 0.51 |
| TimesFM, 14-day context | 2.9 | 6/24 | 6 | 0.66 | 0.86 |
| TimesFM, 28-day context | 3.0 | 8/24 | 7 | 0.63 | 0.62 |
| TimesFM, 64-day context | 3.2 | 6/24 | 7 | 0.66 | 0.50 |

Reference: Mishra 2020 CuSum on the same participants, 62.5% on or before
onset at about 0.66 alarms per month. No detector here comes close to that
operating point; the best is 8/24 (33%).

### Cumulative detector over each score (CuSum, k 0.5, h 3, reset after alarm, untuned)

| Score fed to CuSum | Detected | Median lead (d) | Alarm days / person-month, healthy windows of cases | Same, 73 healthy participants |
|---|---|---|---|---|
| Shipped z, 14-day baseline | 10/24 | 8 | 0.95 | 0.73 |
| z-score, 28-day baseline | 8/24 | 5 | 0.87 | 0.72 |
| TimesFM, 14-day context | 6/24 | 9 | 0.76 | 0.82 |
| TimesFM, 28-day context | 10/24 | 7 | 1.00 | 0.76 |
| TimesFM, 64-day context | 10/24 | 6 | 0.98 | 0.72 |

Accumulating evidence moves every score to the same place. The baseline model
underneath makes no visible difference.

### Forecast accuracy (mean absolute error, bpm, one day ahead)

| Context | TimesFM median | Rolling mean | Yesterday's value |
|---|---|---|---|
| 14 days | 3.00 | 3.22 | 3.50 |
| 28 days | 3.03 | 3.34 | 3.57 |
| 64 days | 3.02 | 3.45 | 3.59 |

## What this says

1. The limit on this data is the signal, not the baseline model. Derived
   overnight resting heart rate moves about 3 bpm from one day to the next
   for reasons no history predicts.
2. The 73 healthy participants are a usable control group for any
   heart-rate-only detector. The first replay had none, because sleep files
   exist only for the positive cases. Shipped rule on them: 2.66 alarm days
   per person-month.
3. G11 stays what it was: a cumulative statistic with a per-person threshold,
   tuned on this harness. The untuned CuSum above is a starting point, not a
   result.

## Limits

- 24 evaluable cases. A difference of four participants is not evidence.
- Alarm days are counted, not alarm events; the paper counts events.
- The shipped baseline includes the scored day, the TimesFM context does not.
- TimesFM scored 95 to 99% of the monitored person-days; the rest count as
  no alarm for it.
- One signal only. Multivariate use (heart rate with steps and sleep) was not
  tested; TimesFM 2.5 takes covariates through a separate regression step.
