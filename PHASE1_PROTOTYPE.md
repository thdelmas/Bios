# Phase 1 Prototype Plan

## Goal

Build a minimal end-to-end pipeline that proves the core Bios concept: ingest real wearable data, build a personal baseline, detect a meaningful anomaly, and surface it to the user.

**Scope:** one platform, two data sources, one condition family, working app.

---

## Prototype Scope

### Platform: Android

**Why Android first:**
- Samsung Galaxy Watch has the largest Android wearable ecosystem
- Health Connect provides a unified API across all Android wearables (Samsung, Pixel, Fitbit, Garmin)
- Broader device accessibility aligns with Bios's "accessible to all" principle
- Health Connect is the standard health data layer on Android, backed by Google

### Data Sources

1. **Android wearables via Health Connect** -- HR, HRV, resting HR, SpO2, respiratory rate, sleep stages, skin temperature
2. **Phone built-in sensors** -- steps, activity, sleep schedule (for users without a wearable)

### Target Condition: Infection / Illness Onset

**Why this condition:**
- High detection confidence (Medium-High per our research)
- Affects everyone (not age/gender/condition-specific)
- Multi-signal pattern is compelling and easy to understand
- Validated in published studies (Stanford/Scripps COVID detection research)
- Delivers clear value quickly ("Bios noticed something 2 days before you felt sick")

**Detection signals:**
| Signal | Source | Threshold |
|--------|--------|-----------|
| Resting HR elevation | Health Connect | > 1.5 sigma above 14-day baseline for 24h+ |
| HRV depression | Health Connect | > 1.5 sigma below 14-day baseline for 24h+ |
| Skin temperature rise | Health Connect | > 0.5 C above 7-day baseline for 12h+ |
| Respiratory rate increase | Health Connect | > 1.5 sigma above 14-day baseline for 12h+ |
| Sleep disruption | Health Connect sleep | Deep sleep drops > 25% from 14-day baseline |
| Activity reduction | Steps / active min | > 30% below 7-day baseline |

**Combined scoring:** weighted sum of active signal deviations. Alert triggered when combined score exceeds threshold with 3+ contributing signals.

---

## Milestone Plan

### M1: Data Ingest (Week 1-2)

**Deliverables:**
- Android app shell with Health Connect permissions flow
- Health Connect adapter that reads: HR, HRV, resting HR, SpO2, respiratory rate, skin temperature, sleep stages, steps
- Local Room + SQLCipher store implementing MetricReading and DataSource schemas
- WorkManager periodic sync so new readings are ingested automatically
- Raw data viewer screen (debug tool -- see your data flowing in)

**Done when:** App installed on a real device, wearable data flowing into local store within minutes of granting permissions.

### M2: Baseline Engine (Week 3-4)

**Deliverables:**
- Rolling statistics calculator (mean, std, percentiles) over configurable windows
- PersonalBaseline entity computed nightly for each metric type
- ComputedAggregate entity (hourly + daily rollups)
- Baseline stabilization check (require minimum 7 days of data before generating alerts)
- Trends screen showing each metric vs. personal baseline over time

**Done when:** After 7+ days of data, the app shows your personal baselines and current readings plotted against them.

### M3: Anomaly Detection (Week 5-6)

**Deliverables:**
- Deviation scorer (z-score per metric against personal baseline)
- Multi-signal correlator implementing the infection onset SignalRules
- Anomaly entity generation with severity classification
- ConditionPattern definition for infection onset (hardcoded for prototype)
- Background processing: detection runs on every new data batch

**Done when:** Simulated anomaly data (or real illness) triggers an Anomaly record with correct severity and contributing signals.

### M4: Alert & Insight UI (Week 7-8)

**Deliverables:**
- Home screen: today's health summary (key metrics vs. baseline, trend arrows)
- Alert cards: clear explanation of what changed, how it compares to baseline, why it matters
- Alert detail view: timeline showing each contributing signal's deviation
- Push notifications for Notice and Advisory tier alerts
- Settings: connected sources, alert sensitivity tuning, data retention

**Done when:** A user wearing an Android wearable for 2+ weeks gets a clear, understandable alert when their vitals deviate in a pattern consistent with illness onset.

### M5: Polish & Validate (Week 9-10)

**Deliverables:**
- Onboarding flow (explain what Bios does, request permissions, set expectations about 7-day baseline period)
- Data export (JSON + FHIR bundle)
- Crash-free, performant, battery budget < 2%
- Internal dogfooding with 5-10 team members for 2+ weeks
- Collect feedback, tune thresholds, fix false positive/negative issues

**Done when:** Multiple real users have run the app for 2+ weeks. At least one real anomaly detected and validated. False positive rate acceptable (< 1 spurious Notice per week).

---

## Success Criteria

| Metric | Target |
|--------|--------|
| Time to first data | < 2 minutes after granting Health Connect permissions |
| Baseline ready | 7 days after install |
| True positive rate | Detect simulated illness patterns > 90% of the time |
| False positive rate | < 1 Notice-tier alert per week during healthy baseline |
| Battery impact | < 2% daily |
| App size | < 30 MB |
| Crash-free rate | > 99.5% |

---

## What This Prototype Does NOT Include

- iOS support (Phase 1b)
- Third-party API integrations (Oura, WHOOP, Garmin -- Phase 2)
- Backend sync or multi-device support
- Conditions beyond infection onset
- Federated learning or OTA model updates
- Play Store submission (internal testing track only)

---

## Risk Register

| Risk | Impact | Mitigation |
|------|--------|------------|
| Health Connect sync gaps across OEMs | Data gaps degrade baseline | WorkManager catch-up sync; alert user if stale data |
| 7-day baseline period causes user drop-off | Users abandon before seeing value | Show real-time data immediately; explain baseline countdown |
| False positives erode trust | Users disable alerts | Conservative thresholds; require 3+ signals; cool-down periods |
| Wearable sensor accuracy varies across brands | Noisy baselines | Use confidence tiers; filter low-quality readings; wider baseline windows |
| Small dogfood group may not get sick | Can't validate real detection | Supplement with synthetic anomaly injection for testing |
