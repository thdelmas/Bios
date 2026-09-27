# Validation replays

Offline harnesses that run the shipped detection engine over public, labeled
wearable datasets and report measured sensitivity, false-alarm rate and lead
time. These are the only numbers Bios may quote about its own detection;
everything else is "implements the published rule".

## Mishra 2020 (COVID-19, Fitbit)

Dataset: Mishra et al., *Pre-symptomatic detection of COVID-19 from smartwatch
data*, Nat Biomed Eng 2020. Raw data (3.4 GB, de-identified, dates shifted per
participant) is public at
`https://storage.googleapis.com/gbsc-gcp-project-ipop_public/COVID-19/COVID-19-Wearables.zip`.
Per-participant symptom-onset / diagnosis / recovery dates are the paper's
Supplementary Table 1; a CSV re-publication lives at
`AmirMage2020/vision` → `data/covid_symptom_dates_raw.csv` (GitHub).

```
unzip COVID-19-Wearables.zip -d raw
python3 tools/validation/mishra_prep.py raw/COVID-19-Wearables covid_symptom_dates_raw.csv derived
cd android
BIOS_MISHRA_DIR=$PWD/../derived ./gradlew :app:testStandaloneDebugUnitTest --rerun \
    --tests com.bios.app.validation.MishraReplayTest
cat app/build/reports/validation/mishra-replay.md
```

`--rerun` matters: Gradle does not track environment variables as test inputs,
so without it an up-to-date task silently skips the replay.
`BIOS_MISHRA_USERS=A0NVTRV,AJ7TSV9` limits the run. Without `BIOS_MISHRA_DIR`
the test is skipped, so CI is unaffected.

What the replay does: for each COVID-19-positive participant, a fresh in-memory
Room database is seeded with production-shaped readings (daily resting HR,
hourly steps, sleep-stage segments), then `BaselineEngine.computeBaseline` and
`AnomalyDetector.runDetection` run once per day with the engine clock set to
23:00 of that day. Alerts are scored against symptom onset. The engine code is
untouched; only its clock is injected.

Results are copied to `docs/validation/` with the date and commit they were
produced at.
