# Spec: instrument only

cost_class: irreversible

Origin: 2026-11-21. Bios presented itself as a health guardian that detects
early signs of illness. It carried about 96 named condition patterns, clinical
tool screens (screening schedule, risk profile, assessments, a seizure
detector) and care advice on every alert. The repo's own replay
(`infection_onset`: 0 of 24) did not support the headline claim. Decision:
Bios is a measuring instrument. It measures, compares to the owner's usual
range, and shows. It never says what a measurement means.

Supersedes the parts of `alert-detail.md` that ask for causes, "what to
watch" and "when to seek care" (Goal items 3 and 4, Criterion 10).

## Intent

The owner reads their own body's measurements. What they mean is judged by
the owner and by whoever the owner chooses to show them to.

## Goal

The public build and the public repo contain:

1. no named condition, disease or clinical event, as a pattern, a detector,
   a screen, a declared state or a doc claim;
2. no screening or scoring of a person against a clinical reference
   (thresholds, percentiles, schedules, assessments);
3. no cause, no advice, no "seek care", no emergency number attached to data;
4. one kind of alert: one or more watched metrics are outside the owner's
   usual range, stated in units ("Resting heart rate 66 bpm over the last
   24 h, above your usual 53–60 bpm"), ranked by size of deviation only.

What stays: ingestion, encrypted storage, trends, day totals, usual-range
baselines, source views, sleep and cycle dashboards as logged, the camera
HRV measurement, the owner's own records shown back as written (headache
diary, health events, medication list), PDF and FHIR export.

Verified when: the vocabulary gate test passes (`VocabularyGateTest`: no
condition name and no medical-purpose vocabulary in the string resources or
in the string literals of the owner-facing packages, outside the reasoned
allowlist in `VocabularyGateAllowlist`); a walk through the installed
build shows no pattern list, no causes, no care card; the JSON and FHIR
exports of an account with stored alerts contain no condition name.

## Limits

| Cost | Bearer (seat) | Seat | Balance as the bearer sees it | Fires when |
| --- | --- | --- | --- | --- |
| Stored alerts lose their interpretation text | owner | filled | Measured values, the detection-time snapshot and the owner's journal entries are kept; only the copied pattern text is blanked. | A stored alert loses a measured value or a journal field after the migration. |
| Companion apps break on a changed contract | owner | filled | `bios-contracts` and the `HealthState` values do not change. | The cross-repo contract workflow fails, or a companion sees an unknown state. |
| A removal is passed off as a rewording | owner | filled | The changelog and release notes name each removed feature as removed. | A removed feature is absent from the release notes. |
| An alert someone relied on is gone (for example a low-glucose alert from a CGM) | other installers | empty | Not asked. No installer is known; the release notes and the community channel say plainly what was removed and where to speak up. | Someone reports relying on a removed alert. |

## Open

- Closed: glucose time-in-range used a clinical range and was removed;
  variability stays.
- Closed: the derived sleep score scored a night against duration, latency
  and awakening bands and was removed. Duration, time in bed, efficiency,
  latency, time awake after sleep onset, stage durations and the spread of
  bedtime, midpoint and wake time stay.
- The sleep regularity reading is still stored as a 0–100 rescale of the
  midpoint spread between two fixed anchors (15 and 90 minutes). The sleep
  dashboard shows the spreads in minutes and not that figure; the stored
  reading keeps its contract key. To decide: store the spread itself.
- Stored readings of removed derivations (the sleep score among them) are
  left in the database and remain selectable in the metric browser and
  present in the JSON export.
- The "other installers" seat is empty: it closes when the community channel
  has been told what is removed.
