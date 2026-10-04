# Spec: rule window coverage

cost_class: reversible

Origin: 2026-10-04. The owner drank on Friday night (RHR 66 vs usual 53–60,
HRV 33 vs 37–49) and Bios alerted on Saturday. On Saturday night the watch
was not worn. At 06:14 on Sunday "Elevated cardiovascular strain" fired:
its 48 h window no longer held Thursday's normal night and nothing replaced
it, so the average of the window was Friday's night alone. The rule cites
"HRV depression >2σ for 48h+" but fired on one night, once nothing else
was in the window. A missing watch made an old night count twice.

## Intent

A baseline-deviation alert speaks only about data that exists, is recent,
and covers the span the rule claims. Missing data is reported as missing,
never read as a deviation, and one night's readings raise at most one
alert.

## Goal

Four rules, agreed by the owner 2026-10-04:

1. **Coverage.** A deviation rule with a window of N hours is evaluable only
   when its sensor readings fall on at least `ceil(0.6 × ceil(N/24))` of the
   window's 24 h slots (counted back from now). 24 h → 1 slot, 48 h → 2,
   72 h → 2, 7 d → 5.
2. **Freshness.** The newest reading in the window is at most 30 h old.
3. **Gap = no data.** A rule failing 1 or 2 is not active, contributes no
   score, and shows as "no recent data" on the pattern screen.
4. **Evidence used once.** A pattern or holistic alert is suppressed when an
   earlier alert sharing one of its active metrics was raised at or after
   the newest reading now in play (no new reading since that alert).

ABSENT rules (absence is the signal) and absolute-threshold rules are out of
scope: their semantics already handle missing data.

Verified when: `WindowCoverageTest` reproduces the Sunday case (one Saturday
06:00 reading, evaluated Sunday 06:14 over 48 h → not evaluable) and the
Saturday case (Friday + Saturday readings → evaluable); the existing
detector suites pass; the Mishra replay is re-run and its numbers recorded
against the 2026-09-27 baseline (21/24 RHR rule, 3.2 false alarms per
person-month).

## Limits

| Cost | Bearer (seat) | Seat | Balance as the bearer sees it | Fires when |
| --- | --- | --- | --- | --- |
| Real deviations missed when the watch is worn irregularly | owner | filled 2026-10-04 ("agree for the 4 fixes") | A rule that cannot see the span it claims stays silent; the source-liveness surface already reports the gap. | Replay sensitivity drops by more than 2 of 24 participants. |
| A new deviation on a night already alerted goes unreported | owner | filled by draft | Suppression ends as soon as any active metric gets a newer reading. | An alert is suppressed although a newer reading of an active metric exists. |

## Tested

- 2026-10-04: `WindowCoverageTest` (9 cases) passes, including the Sunday
  and Saturday cases from the owner's own timestamps. Detector, HRBS,
  circadian, HR-recovery and wipe suites unchanged and passing.
- 2026-10-04: Mishra replay re-run before and after on the same data. The
  two reports are byte-identical, and that is uninformative: the engine
  raises 0 alerts of any pattern on this dataset either way (no HRV, so no
  two-signal pattern can fire). The replay cannot price the sensitivity
  limit for this change; the owner's alert ledger is the instrument.

## Open

- 0.6 slot coverage and 30 h freshness are first values, to be tuned on the
  owner's alert ledger, not on priors.
- A replay dataset with HRV (or the owner's own COROS history) is needed to
  measure what the coverage rule costs in missed detections.
- Pattern alerts still state deviations in standard deviations, not units.
