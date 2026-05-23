# Subject Profile — Owner-as-Test-Subject

Living document. Tracks what Bios is actually ingesting on the owner's primary
device versus the full catalog the app supports, so the same person can serve
as (a) a dogfood subject for surfacing app bugs and (b) the subject of a
self-directed health-baseline / improvement plan.

**Status snapshot generated: 2026-05-23**
**Device:** Pixel 9a · Android 16 · `com.bios.app` UID u0a491
**DB:** `bios.db` 100 MB (active since 2026-05-14), `bios_repro.db` 36 KB
**App age on device:** ~9 days of continuous use

## Subject Demographics (from `bios_screening_demographics`)

- **Birth year:** 1997 (age 28)
- **Presents as:** male
- **Reproductive DB:** isolated, present but minimal data
- Self-reported anthropometry, lifestyle, history → **not yet entered.**

## Companion Apps Installed on Same Phone

### Bios companions (writers via `BiosHealthProvider`)

| Package | Role | Status |
|---|---|---|
| `com.w2f.app` | Mood / cognitive (typing cadence, mood drift, PVT) | Installed |
| `com.virgil.app` | Safety (fall, near-miss, check-in) | Installed |
| `com.smokless.smokeless` | Substance-use events (tobacco/cannabis) | Installed |

Companion grants in Bios: confirm via Bios → Settings → Companion Apps. The
provider permission is `signature`-protected, so `adb` cannot query the
allowlist directly from outside the app.

### Third-party health apps (data sources or adjacent context)

| Package | Role | Bios relevance |
|---|---|---|
| `com.fitbit.FitbitMobile` | Wearable hub | **Primary upstream** — feeds HC, which Bios reads |
| `com.google.android.apps.fitness` | Google Fit | Secondary HC writer (steps, activity); usually redundant with Fitbit |
| `com.google.android.apps.healthdata` | Health Connect | The HC platform itself — Bios's main read surface |
| `com.strava` | Activity (run/ride) | Writes workouts to HC if granted; feeds `exercise_session`, `active_minutes` |
| `com.yazio.android` | Nutrition tracking | No HC bridge today; reference for `caffeine_intake`, `alcohol_intake` manual entry (will feed FuelLog once W2F integration ships) |
| `com.technogym.mywellness.metropolitanwellness` | Gym equipment / strength workouts | No direct integration; reference for manual exercise logging |
| `org.bryanjohnson.blueprint` | Blueprint protocol (Bryan Johnson) | **Not a Bios data source.** Triggered the [Blueprint protocol audit](audits/BLUEPRINT_PROTOCOL_AUDIT.md) that's shaping Bios's lab-panel roadmap. Bios's posture (instrument, owner-baseline) differs from Blueprint's (coach, population percentiles) — see audit for the philosophical delta |

## Active Ingestion Routes

### Health Connect (Bios ↔ HC permissions granted)

15 read permissions granted; HC is the **primary live route** today, fed
upstream by Fitbit. These keys can land in `metric_readings` whenever Fitbit
syncs to HC and `SyncWorker` runs (~15 min cadence).

| HC permission | MetricType key | Notes |
|---|---|---|
| `READ_HEART_RATE` | `heart_rate` | continuous |
| `READ_RESTING_HEART_RATE` | `resting_heart_rate` | daily |
| `READ_HEART_RATE_VARIABILITY` | `heart_rate_variability` | nightly |
| `READ_OXYGEN_SATURATION` | `blood_oxygen` | nightly |
| `READ_RESPIRATORY_RATE` | `respiratory_rate` | nightly |
| `READ_SKIN_TEMPERATURE` | `skin_temperature` + `skin_temperature_deviation` | nightly |
| `READ_VO2_MAX` | `vo2_max` | weekly |
| `READ_SLEEP` | sleep_duration, latency, efficiency, fragmentation_index, WASO, sleep_score, sleep_stage | nightly |
| `READ_STEPS` | `steps` | continuous |
| `READ_EXERCISE` | `exercise_session`, `active_minutes` | per session |
| `READ_ACTIVE_CALORIES_BURNED` | `active_calories` | continuous |
| `READ_WEIGHT` | `body_mass` | if Fitbit Aria or manual |
| `READ_BODY_FAT` | `body_fat_pct` | if Fitbit Aria |
| `READ_LEAN_BODY_MASS` | `lean_body_mass_kg` (+ legacy `lean_mass`) | if Fitbit Aria |
| `READ_HEALTH_DATA_IN_BACKGROUND` | — | enables passive sync |

### Phone-sensor adapter (granted)

- `ACTIVITY_RECOGNITION` granted → `STEPS` fallback when HC empty
- `CAMERA` granted → `CameraPpgAdapter` ready (manual PPG sessions yield HR,
  HRV, parasympathetic_tone, stress_score, LF/HF, irregular_rhythm_burden,
  6× PPG-morphology keys, ECG-strip placeholder)
- `AMBIENT_LIGHT` → phone light sensor (no extra perm needed)

### API adapters (configured?)

| Adapter | Credentials file | Status |
|---|---|---|
| Oura | `bios_oura_credentials.xml` (1144 B) | File exists; verify token validity in app |
| Generic API auth | `bios_api_credentials.xml` (1144 B) | File exists |
| Garmin / WHOOP / Withings / Dexcom / Polar | none on disk | **Not configured** |

### Disabled / blocked routes

- **POST_NOTIFICATIONS:** denied → alerts cannot reach the notification
  shade. **Major test-blocker.** Acknowledge prompts, urgent timeout pings,
  daily digests will be silent. **Action: grant in app settings.**
- **BLUETOOTH_CONNECT / BLUETOOTH_SCAN:** denied → `BleAirQualityAdapter`
  and `DirectSensorAdapter` are inert. `bios_ble_devices.xml` exists (1136 B,
  last touched 2026-05-19) so devices may have been paired previously, but
  the runtime can no longer connect. **Action: grant if any BLE peripheral
  is in use (Polar H10, Aranet4, etc.).**
- **Gadgetbridge:** not installed → no Pebble/Amazfit/Bangle.js path.

## Background Workers Scheduled (JobScheduler dump)

Active periodic workers confirmed on device:

- `SyncWorker` — HC/API pull (~15 min)
- `PhoneSleepWorker` — passive sleep detection
- `DailyDigestWorker` — daily summary alert (blocked by POST_NOTIFICATIONS)
- `ContributionWorker` — opt-in federated/research contribution
- `P2PSyncWorker` — peer sync (Willow)

## Biomarker Inventory — Present / Partial / Missing

Catalog source: [`android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt`](../../../android/bios-contracts/src/main/kotlin/com/bios/contracts/MetricType.kt).
Total `MetricType` keys: **~156** across 16 domains (post-expansion: PR #252 +36, PR #255 +11, PR #256 +1).

### Cardiovascular

| Key | State | How to fill |
|---|---|---|
| heart_rate, resting_heart_rate, heart_rate_variability, blood_oxygen | LIVE | Fitbit → HC |
| vo2_max | LIVE (weekly) | Fitbit → HC |
| respiratory_rate | LIVE (nightly) | Fitbit → HC |
| blood_pressure_systolic / diastolic | MISSING | manual cuff entry; Omron / Withings BPM adapter not configured |
| parasympathetic_tone, stress_score, lf_hf_ratio, hrv_lf_power, hrv_hf_power | DERIVED-on-demand | Run camera PPG session (the app supports `bios://capture/ppg`) |
| irregular_rhythm_burden | DERIVED | Same — camera PPG → RhythmClassifier |
| ecg_strip_available | MISSING | HC ECG record; Fitbit Sense / Apple Watch / KardiaMobile import |
| augmentation_index_ppg | DERIVED-on-demand | Camera PPG capture; written when a diastolic rebound is detectable (PR #256) |
| ppg_peak_amplitude_mean/cov, ppg_rise_time_mean/cov, ppg_decay_asymmetry_index, ppg_dichrotic_notch_position | COMPUTED-NOT-PERSISTED | `PpgSignalProcessor` produces these but `CameraPpgAdapter` does not yet write them as `MetricReadings` — follow-up to PR #256 |

### Respiratory

| Key | State |
|---|---|
| respiratory_rate | LIVE |
| oxygen_flow_rate | manual only — not relevant unless on supplemental O2 |
| sleep_apnea_event, ahi | MISSING — needs FDA-cleared wearable or PSG/HSAT import |
| peak_expiratory_flow_lmin, forced_expiratory_volume_1_liters | MISSING — needs peak-flow meter / spirometer (only relevant if asthma history) |

### Temperature / Sleep / Activity

- Temperature & sleep keys: **LIVE via Fitbit → HC.** Sleep stage data is the
  full set (duration, latency, efficiency, fragmentation, WASO, score).
- `sleep_regularity`, `circadian_phase_shift`: derived by Bios engines from
  sleep onset times — should populate within 7 days of continuous sleep data.
- Activity: `steps`, `active_minutes`, `active_calories`, `exercise_session`
  all LIVE.

### Metabolic

| Key | State | How to fill |
|---|---|---|
| body_mass, body_fat_pct, lean_body_mass_kg, lean_mass | depends on whether Fitbit Aria (smart scale) is paired; otherwise MISSING |
| body_water_pct, bone_mass | MISSING — Withings adapter (not configured) is the canonical path |
| blood_glucose | MISSING — no CGM linked. Dexcom adapter present but unconfigured |
| glucose_cv, glucose_mage, glucose_time_in_range, glucose_peak_24h | DERIVED — populate automatically once `blood_glucose` arrives |

### Anthropometry

| Key | State |
|---|---|
| height_cm | **MISSING — set once via manual entry** |
| head_circumference_cm | n/a (adult subject) |
| bmi_kg_per_m2 | derives once height + weight present |
| fat_mass_kg | derives once `body_mass` + `body_fat_pct` present |

### Recovery / Environment

- `recovery_score`: MISSING — Oura / WHOOP / Garmin owns this key; verify
  Oura credentials by opening Bios → Sources → Oura.
- `ambient_light`: should be LIVE via phone light sensor; verify on Coverage screen.
- `air_pm25 / voc / co2`: MISSING — needs BLE air-quality sensor + Bluetooth perms.
- `elevation_m`, `ambient_temperature_c`, `ambient_humidity_pct`,
  `daylight_hours`: most are manual or derived from RegionConfig — verify
  region is set correctly for the owner's geography.

### Women's health

Out of scope for this subject (male). `bios_repro.db` will remain mostly empty.

### Neurology / Pain

All manual-entry only. Nothing logged yet — will fill when the subject
actually has a headache / pain event to log.

### Intake (caffeine, alcohol, medication, tobacco/cannabis)

- Tobacco / cannabis use + craving: written by Smokeless companion if/when
  events occur.
- `caffeine_intake`, `alcohol_intake`, `medication_intake`: manual entry OR
  W2F FuelLog (caffeine) once that companion ships the integration.

### Safety

`fall_event`, `near_miss_fall`, `check_in_miss`: Virgil-owned, fires only on
real events.

### Mental Health

`typing_cadence`, `mood_drift_score`, `reaction_time_ms`: W2F-owned.

### Lab biomarkers — **NO DATA YET (catalog covers ~87 lab keys)**

Bios supports ~87 lab keys after the Wave-1 + Wave-2 expansions (PR #252
and PR #255, [Blueprint audit §3.1 + §3.2](../../audits/BLUEPRINT_PROTOCOL_AUDIT.md));
the subject has none of them in the DB yet. Each accepts manual entry
from a paper printout via Bios → Biomarkers, and FHIR import populates
them at scale through the LOINC table.

Wave-1 + Wave-2 panel (post-expansion — what to order):

- **Lipid + independent ASCVD risk:** total_cholesterol, ldl_cholesterol, hdl_cholesterol, triglycerides, **apo_b**, **lipoprotein_a**, **homocysteine**, ***coronary_calcium_score*** (CT-derived, once-per-decade)
- **Glycemic:** hba1c, fasting_glucose, fasting_insulin, homa_ir
- **Thyroid + autoimmunity:** tsh, free_t4, free_t3, ***thyroid_peroxidase_ab***, ***thyroglobulin_ab***
- **Inflammation:** hscrp
- **CBC + indices + 5-cell differential:** hemoglobin, hematocrit, wbc, rbc, platelets, **mcv, mch, mchc, rdw, mpv**, **neutrophils_pct, lymphocytes_pct, monocytes_pct, eosinophils_pct, basophils_pct**
- **Iron panel:** ferritin, **iron_serum, iron_saturation_pct, tibc**
- **CMP (electrolytes + minerals):** **sodium, potassium, chloride, carbon_dioxide, calcium_serum, phosphate**
- **Renal:** egfr, creatinine, **bun, uric_acid**
- **Hepatic + pancreatic:** alt, ast, ggt, **albumin, alkaline_phosphatase, bilirubin_total, total_protein, amylase, lipase**
- **Vitamins & Minerals:** vitamin_d_25oh, vitamin_b12, folate, magnesium, ***vitamin_k2***, ***vitamin_a_retinol***, ***vitamin_e_alpha_tocopherol***
- **Endocrine (male-relevant):** testosterone_total, cortisol, igf_1
- **Reproductive endocrine:** **fsh, lh, shbg, amh, testosterone_free, prolactin, dhea_sulfate** (sex- and cycle-phase-specific reference ranges; provider's range travels with the FHIR import)
- **Prostate screening (men 50+):** ***psa_total***, ***psa_free***
- **Skeletal:** ***bone_density_t_score*** (DEXA-derived)
- **Neurology:** ***ptau_217*** (emerging Alzheimer's plasma biomarker)
- **Cardio-oncology:** troponin_ng_per_l, nt_pro_bnp_pg_per_ml, absolute_neutrophil_count (only relevant if undergoing cardiotoxic therapy)
- **Aging biomarkers:** epigenetic_age_dunedin_pace, grim, pheno, horvath (optional, TruDiagnostic-style import), ***telomere_length*** (TeloYears / SpectraCell)

**Bold** = Wave-1 additions (PR #252). ***Bold italic*** = Wave-2
additions (PR #255). All are first-class manual-entry + FHIR-importable
keys (a handful — telomere length, bone density, vitamin K2 — stay
manual-only because no stable canonical LOINC exists).

## Gaps Ranked by Action Cost

### Free, no-purchase fixes (do today)

1. **Grant POST_NOTIFICATIONS** in Bios app settings. Without this, no alert
   testing is meaningful.
2. **Set `height_cm`** via manual entry → unlocks BMI derivation and
   sarcopenia / cachexia screening thresholds.
3. **Confirm Oura credentials** if an Oura ring is in use; otherwise
   disconnect to keep the Coverage view honest.
4. **Run a camera PPG capture** (`bios://capture/ppg`) → populates HRV,
   parasympathetic_tone, stress_score, LF/HF, PPG-morphology keys,
   irregular_rhythm_burden. Lets us exercise the on-device DSP pipeline.
5. **Verify region config** matches the owner's actual elevation /
   latitude — affects SpO2 thresholds (altitude correction) and
   daylight_hours derivation.
6. **Enable Bluetooth perms** if any BLE peripheral is paired (Polar H10
   for direct ECG-grade HR, Aranet4 / Awair Element for air quality).

### Already-owned but unwired (do this week)

- If a smart scale (Fitbit Aria, Withings, Garmin Index) is in use → ensure
  measurements are flowing into Fitbit/HC so `body_mass`, `body_fat_pct`,
  `lean_body_mass_kg` populate.
- If a CGM is in use → connect Dexcom adapter (OAuth flow inside Bios).

### Lab work (next clinical visit)

Order a single comprehensive panel covering the Wave-1 + Wave-2 list
above — practically: lipid + ASCVD (incl. Lp(a) + homocysteine) + CMP +
CBC w/ differential + iron studies + thyroid w/ TPO/Tg Ab + HbA1c +
hsCRP + vitamin D / B12 / folate (plus K2 / A / E if the lab offers
them). Manually enter the values into Bios as soon as the lab report
arrives. With those in place, every preventive alert pattern
(`alerts/BiomarkerReference.kt`, `alerts/CardioOncologyPatterns.kt`,
NAFLD / CKD / insulin-resistance screens) will have ground truth to
anchor against instead of relying on wearable proxies alone.

PSA Total/Free, Coronary Calcium Score (CT), DEXA bone density T-score,
pTau-217, and telomere length are once-per-window tests (annual through
once-per-decade) — order opportunistically rather than alongside the
routine panel.

The dashboard now has matching panels for every result
(Cardiometabolic, Glycemic, Inflammation & Iron, Thyroid, Vitamins &
Minerals, Hematology, Electrolytes & Minerals, Renal, Hepatic &
Pancreatic, Endocrine, Reproductive Endocrine, Prostate Screening, Bone
Health, Neurology, Cardio-Oncology, Aging Biomarkers) — each tile fills
the moment a value lands.

### Hardware purchases (only if relevant)

- BP cuff (Omron M3 Comfort + manual entry, OR Withings BPM Core with
  Withings adapter)
- BLE air-quality monitor (Aranet4 CO₂; Awair Element for PM₂.₅ + VOC)
- Polar H10 chest strap (direct ECG-grade RR intervals → cleanest HRV
  signal Bios can ingest)
- Peak-flow meter — **skip unless asthma history**

## Testing Surface Coverage

Each "fill the gap" action also exercises an app surface:

| Action | Surfaces tested |
|---|---|
| Grant POST_NOTIFICATIONS | DailyDigestWorker, UrgentAckTimeoutWorker, FollowUpWorker, AlertActionReceiver |
| Manual height entry | Self-reported biomarker entry flow, growth-tracking surface (#199) |
| Camera PPG capture | CameraPpgAdapter, PpgSignalProcessor, RhythmClassifier, HRV power spectral decomposition |
| Lab panel entry | Biomarker entry flow, AnomalyDetector against biomarker reference ranges, BaselineEngine seeding |
| BLE pairing | BleAirQualityAdapter, DirectSensorAdapter, BLE device persistence in `bios_ble_devices.xml` |
| Companion approval flows | CompanionGate, CompanionContract per-package allowlist, the dangerous-permission grant prompt for READ_HEALTH / WRITE_COMPANION |
| P2P sync | WillowSyncAdapter, P2PSyncWorker (needs a second Bios install on another device) |

## Open Questions

- Is the Fitbit account actively syncing to Health Connect on this device?
  (Verify with: HC app → Data and access → Apps → Fitbit.)
- Is the `bios_oura_credentials.xml` token still valid, or expired? Last
  modified 2026-05-14 (same day as install — probably stale).
- The 100 MB `bios.db` size after 9 days suggests substantial ingestion is
  happening — most likely high-frequency HR + step samples from Fitbit. A
  per-table row-count dump would confirm, but requires unlocking SQLCipher
  from inside the app (e.g. a debug screen that prints `SELECT metric_type,
  COUNT(*) FROM metric_readings GROUP BY metric_type`).

## Next Update Triggers

Refresh this doc whenever any of the following changes:

- A new ingestion route is connected (smart scale, CGM, BLE peripheral, OAuth source)
- Lab biomarkers are imported / manually entered
- Permission grants change (notifications, Bluetooth)
- Region config changes (travel above sea level affects threshold math)
- A companion completes its approval flow in Bios → Companion Apps
