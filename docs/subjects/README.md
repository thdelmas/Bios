# Subjects

Per-subject documentation: living profiles of what Bios is actually ingesting
on a given device, study writeups, baselines, observation logs.

## Structure

One directory per subject, keyed by a stable pseudonym (`owner`, `subject-002`,
etc.). Never use real names, exact birth dates, addresses, or any identifier
that ties to a person outside this repo. Year of birth and self-described
demographic context are fine if relevant to the study.

```
docs/subjects/
├── README.md                  (this file)
└── <subject>/
    ├── PROFILE.md             living snapshot of ingestion + gaps
    ├── studies/               individual intervention / study writeups
    ├── baselines/             periodic baseline snapshots
    └── observations.md        chronological event log
```

Only `PROFILE.md` is required. Add the other files / directories the first
time you actually have content for them — don't pre-create empty scaffolding.

## Current subjects

| Subject | Status | Notes |
|---|---|---|
| [`owner`](owner/PROFILE.md) | Active | The primary device owner. Dogfood + self-baseline subject. |

## Adding a new subject

1. Create `docs/subjects/<pseudonym>/PROFILE.md`. Start from
   [`owner/PROFILE.md`](owner/PROFILE.md) as a template.
2. Confirm consent and scope before adding anyone other than the device
   owner. Bios's manifesto puts owner autonomy first; that constraint applies
   doubly to anyone Bios is *measuring*.
3. Add an entry to the table above.

## What belongs here vs. in code

- **Here:** observations, hypotheses, intervention logs, lab-panel results
  (manually redacted as needed), per-subject action queues, "what's connected,
  what's missing" snapshots.
- **Not here:** raw sensor data, full lab CSVs, anything containing PII.
  Those live on-device in the encrypted Bios DB, never in the repo.
