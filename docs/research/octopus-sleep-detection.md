# Octopus Investigation: Sleep Detection

_Started 2026-05-23. Origin: Bios phone-only sleep inference (`PhoneSleepInference.kt`) underperforming in production._

## On-device diagnostic (2026-05-23, plugged-in phone)

- `PhoneSleepWorker` **is** scheduled and **is** firing. Latest run: 6m28s before snapshot, exited cleanly via `jobFinished` after ~2s. So the worker registration in `BiosApplication.kt:71` is intact.
- Other Bios workers (`ContributionWorker`, `DailyDigestWorker`, `SyncWorker`) also visible — WorkManager itself is healthy.
- DB is SQLCipher-encrypted; `adb run-as ... sqlite3` is unavailable, so the sample buffer count and stored sleep readings could not be inspected directly. To progress diagnosis we need either (a) a temporary `Log.i` of `PhoneSleepInference.signalBreakdown(samples)` at the end of every worker firing, or (b) a debug-only ContentProvider row that exposes counts. **First concrete action item**: instrument `PhoneSleepWorker.doWork()` with a one-shot signal-breakdown log per firing so we can see, on the real device, which signal (screenInactive / lowMotion / dark / charging) is starving the `isQuietSample` predicate.

## Cross-tentacle synthesis

Four tentacles converged on the same diagnosis with surprising consistency: **the algorithm is approximately fine; the collection lifecycle is the load-bearing failure.**

### Priority-ordered action plan (with sources)

| # | Action | Source tentacle | Cost | Risk |
|---|--------|-----------------|------|------|
| 1 | **Instrument `PhoneSleepWorker` with signal-breakdown logging** so the real failure mode on this phone is observable. Without this we are guessing. | Diagnostic | ~10 lines | None |
| 2 | **Replace 15-min WorkManager periodic with a charge-triggered foreground service** that streams sensors continuously while plugged in (model: `C4DMH/Sleep/AccGryLgt.java`). WorkManager periodic is deferred 30-90 min on Samsung / Xiaomi / OnePlus / Oppo Doze; this starves the buffer below the 4 h floor. | A | Medium | Med — needs `FOREGROUND_SERVICE` perm + `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` UX |
| 3 | **Add a manual "Going to bed" Quick Settings tile + Compose button**, mirroring `vmiklos/plees-tracker`. Emits `SLEEP_DURATION` at `ConfidenceTier.HIGH`. Owner override is manifesto-aligned and gives an immediate working path even before #2 lands. | A | Low (~50 lines) | None |
| 4 | **Replace 1-tap `stageAt()` with Cole-Kripke 7-tap FIR filter** + Webster rescoring. Same inputs Bios already has; ~20 lines of Kotlin; 30-year peer-reviewed baseline. | B | Low | Low |
| 5 | **Per-owner baselined activity threshold.** Replace the hardcoded `ACTIVITY_THRESHOLD = 0.5f` with 3× the owner's 25th-percentile-of-quiet-hours variance. Aligns with "instrument, not coach" philosophy. | B | Low | Low |
| 6 | **Surface a "phone-only is unreliable — pair a wearable" banner** after N consecutive LOW-confidence nights, with tiered hardware recommendation (Mi Band 7/8, Bangle.js 2, Garmin Instinct 3). All flow through the existing `GadgetbridgeAdapter`. | C | Low (UX only — adapter exists) | None |
| 7 | **Validation harness against PhysioNet sleep-accel** (Walch 2019, MIT, 31 subjects with PSG labels). Replay the dataset through `PhoneSleepInference` and get a real RMSE-vs-PSG number to replace the doc's hand-wave "~30 min". | B | Medium | None |
| 8 | **Ship Walch et al. classifier as `wearable_sleep_classifier.tflite`** behind a `SleepClassifier` interface, activating only when a wearable adapter provides accel + HR. | D | Medium-high | Medium |
| 9 | **Add `SleepApiAdapter` for the `standalone` build flavor** (Play Services `SleepSegmentEvent`). LETHE-flavor keeps the rule engine. ~150 lines. | A | Low | Low |
| 10 | **Adopt Flower for the federated-learning roadmap commitment.** Apache-2.0, TFLite training on Android, no GMS dependency — matches LETHE. | D | Strategic | High (server work) |

### Verdicts on phone-only ML

**Don't replace the rule engine with ML for phone-only.** (D) No model exists for Bios's feature set (screen/charging/lux/variance), Bios has no labels to train one, and Sleep As Android's decade of phone-only ML still catches only ~1/3 of awake periods. The ceiling is low. ML earns its keep the moment a wearable provides HR + accel — Walch et al. is the model to ship then.

### Items checked and dismissed

- ❌ **Tentacle C #4 "Verify `HealthConnectAdapter` reads `SleepSessionRecord`"** — already reads it end-to-end including all four `STAGE_TYPE_*` mappings + asleep-time computation (verified at `HealthConnectAdapter.kt:240-285`). Non-issue.
- ❌ **Tentacle A #6 "audit `PhoneSensorAdapter.kt:124` isInteractive use"** — verified harmless. That call gates the ambient-light snapshot in the sync path, not the sleep path; returning null when non-interactive is conservative-correct.
- ❌ **EEG-based SOTA** (DeepSleepNet, U-Sleep, YASA, Micro SleepNet) — Bios has no EEG-bearing source on any adapter. Document once, move on.
- ❌ **Camera-based detection** (e.g. `Ahwar/SleepDetection`) — wrong sensor for a phone on a nightstand; also privacy-hostile.
- ❌ **Sleep As Android's ultrasonic sonar** — mic-during-sleep is privacy-hostile and battery-expensive. Note as concept only.

### Unexplored leads queued for the next octopus pass

- **@vmiklos** (plees-tracker maintainer) — solo monthly cadence on a related Kotlin codebase; check other repos for patterns
- **C4DMH org** — digital mental health repos may align with Bios's owner-evaluation stance
- **OxWearables `asleep`** — npj Digital Medicine 2024, wrist-accel-only PyTorch model worth re-evaluating after we have a wearable validation pipeline
- **HypnosPy** — Python actigraphy lib with Cole-Kripke, Sadeh, Oakley implementations as cross-check
- **DPSleep** — longitudinal accel pipeline; cites actigraphy thresholds Bios could reuse
- **SleepTk (wasp-os)** — clever "turn-on-to-wake" cycle-position UX worth surfacing in Fil

---

## Tentacle A: FOSS Android Sleep Apps

### Discoveries

| Project | URL | Stars | License | Last activity | Phone-only? | Worth integrating? |
|---|---|---|---|---|---|---|
| vmiklos/plees-tracker | https://github.com/vmiklos/plees-tracker | 223 | MIT | Feb 2026 (v26.2) | Yes (manual only) | Patterns yes, code no |
| C4DMH/Sleep | https://github.com/C4DMH/Sleep | 3 | Apache-2.0 | Dec 2019 (stale) | Yes, accel+gyro+light+charging | Architecture reference only |
| josephbima/sleep-tracker | https://github.com/josephbima/sleep-tracker | 0 | none stated | 2020 (stale) | Yes, accel-only ML | Feature ideas only |
| Ahwar/SleepDetection | https://github.com/Ahwar/SleepDetection | low | none stated | stale | Camera-based | No — wrong sensor for nightstand |
| ChristopherBull/android-sleep-api | https://github.com/ChristopherBull/android-sleep-api | low | MIT | active | Sleep API wrapper | No — requires Play Services |
| Freeyourgadget/Gadgetbridge | https://codeberg.org/Freeyourgadget/Gadgetbridge | very active | AGPL-3.0 | active | No (wearable required) | Negative example: minimal post-processing |
| google/codelab-android-sleep | https://github.com/android/codelab-android-sleep | n/a | Apache-2.0 | active | Sleep API only | No — requires Play Services |

### Deep dives

#### `C4DMH/Sleep` (most architecturally relevant)
- **What it does**: Academic study app from a UK research org that records accel + gyro + light + charging continuously through the night, then uploads to S3 for offline analysis. Not consumer-facing.
- **Signals fused**: Exactly the same signals Bios uses (accel, light, charging), plus gyroscope.
- **Approach**: **Foreground service with `PARTIAL_WAKE_LOCK`**, registered `SensorManager.SENSOR_DELAY_NORMAL` listeners, buffered writes every 500 KB. Wakelock acquired in `onStartCommand`, released in `onDestroy`. Boot receiver re-arms the service. Manifest declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and `android:persistent="true"` on the application tag.
- **Solves Bios problem**: **YES — partially**. Their service is *always streaming* sensor events (no 15-min sampling), so they cannot miss sleep onset. Bios's `WorkManager` 15-min cadence is almost certainly a contributor to the production bug — a 15-min minPeriodic can be deferred by Doze for **hours** on aggressive OEMs (Samsung, Xiaomi, OnePlus). The signal buffer ends up sparse or empty.
- **Code we could borrow**:
  - `app/src/main/java/gwicks/com/sleep/AccGryLgt.java` — foreground-service skeleton with sensor registration, wakelock, and buffered IO
  - `app/src/main/java/gwicks/com/sleep/StartLogging.java` — boot-time service start with O+ guard
  - `app/src/main/java/gwicks/com/sleep/PowerConnectionReceiver.java` — charging-state edge trigger (pattern: start the sleep service when phone is plugged in at night, not on a fixed schedule)
- **Integration cost**: **medium** — Bios already has `PhoneSensorAdapter.kt` that can read these sensors. The change is the *trigger model*: move from `PhoneSleepWorker` periodic to a charge-triggered foreground service that runs sensor capture nonstop until unplug.
- **Contributors worth following**: C4DMH org (Centre for Digital Mental Health) — other repos may have sleep/wellbeing prior art

#### `vmiklos/plees-tracker` (most actively maintained FOSS sleep app)
- **What it does**: Pure manual sleep log — user taps "start sleep" / "stop sleep", app stores duration, exports CSV. Battery-zero.
- **Signals fused**: None. By design.
- **Approach**: Kotlin, Room DB, manual buttons + Quick Settings tile.
- **Solves Bios problem**: **NO** for inference, but **YES** for fallback UX. The fact that this app is the most-starred FOSS sleep tracker on F-Droid (223 stars, releases monthly since 2019) is itself a finding: **users prefer a reliable manual log over an unreliable automatic detector**. Bios should expose a manual "I'm going to bed now" affordance as a high-confidence override — owners who hate phone-only inference can use it and still get sleep duration data into the baseline engine.
- **Code we could borrow**: The Quick Settings tile pattern is exactly what Bios needs for the manual override.
- **Integration cost**: **low** — small Compose screen + tile service + one new HIGH-confidence row in `MetricReading`.
- **Contributors worth following**: @vmiklos (LibreOffice maintainer, ships sleep app reliably solo)

#### `josephbima/sleep-tracker`
- **What it does**: Research project — ML classifier (random forest, scikit-learn) on phone accelerometer alone to label 5-minute windows as sleep/wake.
- **Signals fused**: Accelerometer only (mean, std, peak length, magnitude, dominant frequency, entropy of accel magnitude over 5-min window).
- **Approach**: Off-device training in Python; classifier serialized to `classifier.pickle`. No working Android deployment in the repo.
- **Solves Bios problem**: **Concept yes, code no**. Confirms that **dominant frequency and entropy of accel magnitude** outperform plain variance for distinguishing "phone on chest of sleeping owner" from "phone on table next to TV watcher". Bios's current `accelMagnitudeVar` threshold is the simplest possible feature — adding entropy would help disambiguate `ACTIVITY_THRESHOLD = 0.5f` edge cases.
- **Code we could borrow**: `features.py` — feature extraction reference; would need a Kotlin port. Roughly 30 lines.
- **Integration cost**: **low-medium** — extend `PhoneSleepInference.Sample` with `accelEntropy` and `accelDominantFreq`, compute from raw samples in `PhoneSleepAdapter`.

#### `Freeyourgadget/Gadgetbridge` (negative example)
- **What it does**: Open-source companion app for ~40 wearables. Reads pre-computed sleep stages from the device firmware (Mi Band, Pinetime, Bangle.js, etc.).
- **Approach**: Gadgetbridge does **almost no sleep post-processing of its own**. From the wiki: "Gadgetbridge merely collects raw data (minute samples) from the device and puts them on graphs." It calls the deep-sleep classifications "not reliable" for most watches.
- **Lesson for Bios**: A mature, 10+ year FOSS sleep project chose **not** to build sleep inference because the cost/accuracy tradeoff doesn't work even with wearable-grade signals. This validates Bios's "MEDIUM confidence at best" posture but also suggests Bios should treat the phone-only inference as a **diagnostic fallback**, not the primary path. When a wearable adapter (Gadgetbridge, Health Connect) reports sleep, that should always win.

#### Android Sleep API (`ActivityRecognitionClient.requestSleepSegmentUpdates`)
- **What it does**: Google Play Services emits `SleepSegmentEvent` (daily sleep window with start/end/duration/confidence) and `SleepClassifyEvent` (every ~10 min, light/motion/confidence triplet). Uses on-device sensor fusion from GMS.
- **Approach**: Register a `BroadcastReceiver`, get push events. No foreground service needed.
- **Solves Bios problem**: **Yes on stock Android, no on LETHE/degoogled.** Requires `play-services-location:20.0.0+` and the `ACTIVITY_RECOGNITION` permission. microG has **not** implemented `requestSleepSegmentUpdates` as of mid-2026 (search of microG/GmsCore issue tracker returned no implementation).
- **Integration cost on standalone flavor**: **low** — a `SleepApiAdapter` behind the existing `LetheCompat`-style flavor split would be ~150 lines. Bios already gates other GMS features.
- **Integration cost on LETHE flavor**: **N/A** — not available.
- **Recommendation**: Add for the `standalone` flavor as a high-confidence source; keep the rule-based `PhoneSleepInference` as the LETHE-flavor primary.

### Unexplored leads (queued for other tentacles)

- **@vmiklos** (plees-tracker) — solo maintainer with monthly cadence; check other repos for Kotlin patterns that match Bios's style
- **C4DMH org** — other repos may have ecological-momentary-assessment code; their domain (digital mental health) overlaps Bios's evaluation-belongs-to-owner stance
- **HypnosPy** (https://github.com/HypnosPy/HypnosPy) — Python lib for actigraphy sleep analysis. Cole-Kripke and Sadeh algorithms implemented. (Tentacle B covers this thoroughly.)
- **DPSleep paper** (PMC8529474) — academic open-source pipeline; cites actigraphy ground-truth thresholds Bios could reuse
- **SleepTk** (Pinetime, wasp-os micropython) — their "wake when you turn the screen on, compute cycle position" UX is a clever insomnia-mode addition Bios could surface in Fil
- **Sleep As Android sonar** — proprietary but documented at sleep.urbandroid.org/docs/sleep/sensors.html. Ultrasonic chirp + microphone reflection for breath rate. Not portable to Bios (mic-during-sleep is privacy-hostile and battery-expensive) but the *concept* of mic-based stillness detection is a fallback when accel is unavailable.

### Recommendations for Bios

Priority-ordered, with concrete file changes:

1. **Replace `PhoneSleepWorker` 15-min periodic with a charge-triggered foreground service.** This is the most likely root cause of the in-production failure. WorkManager periodic minimum is 15 min but in practice gets deferred 30-90 min on Doze-aggressive OEMs (Samsung, Xiaomi, Oppo, OnePlus). The result is a sample buffer that's too sparse to cross the `MIN_SLEEP_WINDOW_MS = 4h` floor. Model after `C4DMH/Sleep/AccGryLgt.java`: `PowerConnectionReceiver` starts a foreground service on `ACTION_POWER_CONNECTED` after a quiet-hours check (e.g., 21:00–08:00), service streams `SENSOR_DELAY_NORMAL` accel/light, persists 1-min aggregated buckets, stops on `ACTION_POWER_DISCONNECTED`. Bios's existing `PhoneSleepInference.infer()` then runs on the buffered samples.

2. **Add a manual "Going to bed" Quick Settings tile + Compose button**, mirroring `plees-tracker`. Emits a `MetricType.SLEEP_DURATION` row at `ConfidenceTier.HIGH` (owner-asserted). This gives the owner a reliable escape hatch on nights the inference fails and is itself a strong training-signal baseline for tuning thresholds. Owner-asserted always beats heuristic. Aligns with the "owner is final" manifesto principle.

3. **Add `SleepApiAdapter` behind the standalone build flavor.** Roughly 150 lines. Use `SleepSegmentEvent` as a HIGH-confidence sleep source on devices that have GMS. Keep `PhoneSleepInference` as the LETHE-flavor primary and as a cross-check / disagreement diagnostic on standalone. Gate behind the existing flavor split — no GMS dependency leaks into the LETHE APK.

4. **Extend `PhoneSleepInference.Sample` with `accelEntropy` and `accelDominantFreq`** (from josephbima features list). Adds ~30 lines to `PhoneSensorAdapter` and a single new check in `stageAt()`. Helps disambiguate the "movie in a dark room while motionless" edge case that the current `ACTIVITY_THRESHOLD = 0.5f` variance gate cannot reliably catch. Note: Tentacle B's Cole-Kripke recommendation is a stronger primary win — adopt that first; entropy/dominant-freq is a follow-up only if Cole-Kripke alone is insufficient.

5. **Treat all wearable sleep as a hard override of phone-only inference.** Gadgetbridge's reluctance to post-process sleep is informative: phone-only data is inherently noisy. When the same night has both a Health Connect / Gadgetbridge sleep row and a phone-only row, the wearable wins and the phone row is dropped (or kept only as a disagreement flag for diagnostics). Likely already true in `BaselineEngine` but worth verifying.

6. **`Display.getState()` (not `isInteractive`) for AOD phones — already done.** `PhoneSleepAdapter.kt:74-87` already correctly handles `STATE_DOZE`, `STATE_DOZE_SUSPEND`, `STATE_ON_SUSPEND` as "screen off equivalent." Note: `PhoneSensorAdapter.kt:124` still uses `powerManager.isInteractive` — verify this isn't on the sleep code path; if so, switch to `Display.getState()`.

---

## Tentacle B: Sleep Detection Algorithms

### Discoveries

| Algorithm | Source (paper/repo) | Inputs | Validation | License | Portable to Kotlin? |
|---|---|---|---|---|---|
| Cole-Kripke (1992) | pyActigraphy, dipetkov/actigraph.sleepr, ActiLife | 1-min epoch activity counts (single axis, zero-crossing) | Original: vs PSG, agreement ~88% sleep / ~38% wake. Population/device dependent. | pyActigraphy = GPL-3, R port = GPL-2 | Trivial — pure 7-tap FIR filter, ~10 lines of Kotlin |
| Sadeh (1994) | pyActigraphy, actigraph.sleepr | 1-min epoch y-axis counts, 11-min sliding window (5 prev + 5 next + current) | ~91% PSG agreement in adolescents; degrades in older adults | GPL | Trivial — closed-form linear formula |
| Oakley (1997) | pyActigraphy | 1-min epoch counts, weighted 7-min window | Used by Philips Respironics Actiware (clinical) | GPL | Easy — simple weighted sum + threshold |
| van Hees HDCZA (2018) | wadpac/GGIR (R) | RAW triaxial accelerometer at >=5 Hz, no counts needed | Detects SPT-window with ~5-15 min mean error vs PSG; validated across Axivity/GENEActiv/ActiGraph | LGPL-2.1 | Moderate — needs raw 3-axis accel + 5s windowing |
| van Hees 2015 (5deg/5min) | GGIR | RAW triaxial accel, z-angle | Simpler precursor; ~30 min error overnight | LGPL-2.1 | Easy — single arctan + threshold |
| Walch et al. (2019) | ojwalch/sleep_classifiers | Apple Watch accel + PPG HR + circadian proxy | Sleep/wake AUC ~0.88, 3-class (W/REM/NREM) AUC ~0.73 vs PSG (N=31, U.Michigan) | MIT | Moderate (sklearn RF/MLP), but requires HR |
| DPSleep (2021) | harvard-nrg/dpsleep-extract | Raw accelerometer, longitudinal weeks | Spectral percentile + iterative sliding window; validated 200+ days/subject | Apache-2.0 | Hard — multi-stage Python pipeline |
| Toss-N-Turn (2014) | CMU CHIMPS Lab (paper only, no public code) | Phone screen, battery, accel, mic, light | 93% sleep state, ±35 min bedtime error vs diary (N=27) | N/A | N/A — would need reimplementation |
| YASA (2021) | raphaelvallat/yasa | EEG + EOG + EMG (PSG-grade) | 87.5% accuracy 5-stage on 3000+ recordings | BSD-3 | Not portable — PSG-only, not for phone |
| U-Sleep / DeepSleepNet / SleepEEGNet | various GitHub | EEG/EOG | ~85-87% 5-stage vs human scorers | mostly MIT | Not portable to phone — EEG-only |
| GGIR vanHees2015 (children PSG) | wadpac/GGIR | Raw wrist accel | Slightly worse than ActiLife proprietary but free + reproducible (Plekhanova 2023, Bourdier 2024) | LGPL-2.1 | Easy |

### Deep dives

#### Cole-Kripke (the canonical movement-based sleep/wake classifier)
- **What**: A 1-minute epoch FIR filter that scores sleep when the weighted sum of activity counts across a 7-minute centered window (4 prev + current + 2 future) falls below 1.0. Formula (UCSD/PIM variant): `SI = 0.001 * (106*A_-4 + 54*A_-3 + 58*A_-2 + 76*A_-1 + 230*A_0 + 74*A_+1 + 67*A_+2)`, with counts pre-clipped at 300. SI < 1 -> sleep, >= 1 -> wake.
- **Inputs**: A single scalar "activity count" per minute. Bios already computes `accelMagnitudeVar` which is the modern proxy. Counts were originally zero-crossings, but the literature (Plekhanova 2023, Bourdier 2024) shows ENMO or variance work fine after rescaling.
- **How accuracy is reported**: Per-minute sleep/wake vs concurrent PSG, reported as sensitivity (sleep) ~88-95% and specificity (wake) ~38-50%. The wake-detection weakness is a known and shared limitation of all actigraphy approaches — quiet wake looks like sleep.
- **Match for Bios**: Excellent. Bios already has minute-cadence quiet samples + variance. The current `stageAt()` is a 1-tap threshold; Cole-Kripke is the published, validated 7-tap version. Drop-in.
- **Adoption cost**: ~20 lines of Kotlin. Replace `stageAt(sample)` with `stageAt(samples, i)` that looks at neighbors. No new data, no ML, no training. Add Webster's 5 rescoring rules (e.g., "after >=4 min wake, first min of activity counts as wake") for free.

#### van Hees HDCZA (the modern open-source standard)
- **What**: A heuristic on the z-axis angle of a wrist accelerometer. Compute `anglez = atan2(z, sqrt(x^2+y^2)) * 180/pi` per 5 s; take a 5-min rolling median of absolute successive differences; threshold at the 10th percentile of the day x 15; blocks below threshold for >=30 min are sleep; longest daily block is the Sleep Period Time window.
- **Inputs**: Raw 3-axis accelerometer at >=5 Hz. Bios's `PhoneSleepAdapter` already samples accel but only stores variance — would need to retain a per-window z-angle aggregate.
- **How accuracy is reported**: SPT-window detection vs PSG with mean error 5-15 min for total sleep time across multiple device brands (Plekhanova 2023, Sci Reports 2018).
- **Match for Bios**: Strong, but requires a phone to be reasonably wrist-like (lying on its back on a nightstand, oriented stably). A phone in a pocket or under a pillow at random angles breaks the assumption. Worse fit than Cole-Kripke for phone-only.
- **Adoption cost**: Moderate. Need to expose z-axis (currently only magnitude variance is stored) and persist a per-day percentile.

#### Walch et al. / ojwalch/sleep_classifiers (modern ML benchmark)
- **What**: Random forest + neural net trained on Apple Watch accel + PPG HR + a clock-based circadian proxy. Three-class (Wake/REM/NREM) sleep staging.
- **Inputs**: Acceleration in g + heart rate in bpm + time-of-day. Bios has accel but not HR on phone-only path.
- **How accuracy is reported**: AUC ~0.88 for sleep/wake, ~0.73 for 3-class vs full PSG (N=31). MIT license, scikit-learn.
- **Match for Bios**: Not for phone-only — needs HR. But the data (PhysioNet sleep-accel) and the codebase are the gold standard for *validating* whatever Bios chooses, because each subject has raw accel + PSG labels.
- **Adoption cost**: As a validation harness: low (replay accel through the Bios pipeline and compute sensitivity/specificity vs the labels). As a runtime algorithm: high (needs HR, sklearn-equivalent inference on Android — feasible with LiteRT but not necessary).

#### Toss-N-Turn (the closest published analogue to what Bios does)
- **What**: A 2014 CMU CHIMPS paper that fuses screen state, battery, accel, microphone, and ambient light into a Random Forest classifier for sleep/wake. Reports 93% sleep state accuracy and ±35 min bedtime error vs diary.
- **Inputs**: Almost identical to Bios's 5 signals (Bios omits microphone — privacy correct).
- **How accuracy is reported**: vs self-report diary, not PSG. Diary is weaker ground truth.
- **Match for Bios**: Conceptually the closest fit, but no public code and the RF requires per-user training (3 days for sleep state, 3 weeks for sleep quality). Bios's no-training stance precludes adoption directly, but the *feature set* validates Bios's choices.
- **Adoption cost**: N/A (no code). Worth citing in Bios's docs as the published analogue.

### Recommendations for Bios

1. **Replace the 1-tap `stageAt()` with Cole-Kripke's 7-tap FIR filter immediately.** The current `if (variance > 0.5) AWAKE else LIGHT` is the simplest possible classifier and is provably worse than a 30-year-old, peer-reviewed alternative that uses the same input. The variance-to-counts scaling needs one calibration constant (recommendation: collect a week of `accelMagnitudeVar` distributions from a few volunteers and pick the multiplier that maps the 90th percentile to ~300, matching Cole-Kripke's clip). Then implement Webster's 5 rescoring rules (most importantly: "any minute that has >=10 surrounding minutes of activity is wake"), which together push reported accuracy from ~85% to ~88-92% on the same data.

2. **Adopt the van Hees HDCZA 5-degree/5-minute rule as the `longestScreenOffStretch` replacement** when ambient-light is unreliable but accelerometer is. Persist a per-window z-angle (in addition to variance) and use the 5-deg-in-5-min threshold to detect "phone stationary on a flat surface." That removes the dependency on `screenOff` (which is the very signal the user reports is unreliable on AOD-default phones) and replaces it with a physical posture detector. This is the most defensible single fix for "inference isn't working in practice."

3. **Validate against PhysioNet sleep-accel (Walch dataset, 31 subjects with PSG labels)**. The dataset is free; a JVM replay harness over the accelerometer column would give Bios a real RMSE-vs-PSG number to replace the doc's hand-wave "~30 min RMSE." If Bios's algorithm scores worse than ~45 min RMSE on this dataset, the user is right and the algorithm is broken. The harness is one-time work; the regression test it produces lasts forever.

4. **Do not pursue DeepSleepNet / U-Sleep / YASA / SleepEEGNet**. They require EEG and are inapplicable. Note them in the docs only to explain why phone-only sleep staging caps at sleep/wake + light/awake — REM and deep require neural signals Bios cannot see.

5. **Adopt the variance threshold of 0.5 (m/s^2)^2 as a *baseline* until calibrated.** The literature gives no direct guidance for this exact unit (most actigraphy is in counts/min), but the Cole-Kripke clip-at-300 norm + the Plekhanova 2023 paper's ENMO conversions both suggest the threshold should be tuned per-device, not hardcoded. Add a `BaselinedActivityThreshold` that learns the owner's 25th-percentile-of-quiet-hours variance and uses 3x that as the wake threshold. This is the single line that turns a population threshold into a personal one and is well within Bios's "instrument, not coach" philosophy.

### Key open-source repos to track
- `ghammad/pyActigraphy` (GPL-3) — reference Python for Cole-Kripke, Sadeh, Oakley, Scripps, Crespo, Roenneberg
- `wadpac/GGIR` (LGPL-2.1) — R, the de-facto standard for raw-accelerometer sleep
- `dipetkov/actigraph.sleepr` (GPL-2) — concise R ports
- `ojwalch/sleep_classifiers` (MIT) — Apple Watch ML reference + PhysioNet dataset
- `harvard-nrg/dpsleep-extract` (Apache-2.0) — longitudinal deep-phenotyping pipeline
- `raphaelvallat/yasa` (BSD-3) — PSG-only, useful only as comparison ceiling
- `actigraph/Sleep-Wake-Classification` — algorithm comparison harness from ActiGraph themselves

### Sources
- [pyActigraphy docs (algorithms)](https://ghammad.github.io/pyActigraphy/pyActigraphy-Sleep-Algorithms.html)
- [pyActigraphy Cole-Kripke API](https://ghammad.github.io/pyActigraphy/_autosummary/pyActigraphy.sleep.ScoringMixin.CK.html)
- [actigraph.sleepr GitHub](https://github.com/dipetkov/actigraph.sleepr)
- [ojwalch/sleep_classifiers GitHub](https://github.com/ojwalch/sleep_classifiers)
- [Walch et al. 2019 SLEEP paper](https://academic.oup.com/sleep/article/42/12/zsz180/5549536)
- [PhysioNet sleep-accel dataset](https://physionet.org/content/sleep-accel/1.0.0/)
- [van Hees 2018 HDCZA Sci Reports](https://www.nature.com/articles/s41598-018-31266-z)
- [GGIR HASPT docs](https://rdrr.io/cran/GGIR/man/HASPT.html)
- [Plekhanova 2023 multi-brand validation](https://onlinelibrary.wiley.com/doi/10.1111/jsr.13760)
- [DPSleep paper (JMIR mHealth)](https://mhealth.jmir.org/2021/10/e29849)
- [DPSleep GitHub](https://github.com/harvard-nrg/dpsleep-extract)
- [Toss-N-Turn paper (CMU CHIMPS)](http://cmuchimps.org/publications/toss_n_turn_smartphone_as_sleep_and_sleep_quality_detector_2014)
- [YASA paper (eLife 2021)](https://elifesciences.org/articles/70092)
- [Smart sleep tracking via phone vs PSG (2019 study)](https://pubmed.ncbi.nlm.nih.gov/31674096/)
- [Random forests on wrist accel vs PSG](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7794504/)
- [Cole-Kripke explainer (Condor Instruments)](https://condorinst.com/en/enhancing-sleep-detection-the-cole-kripke-algorithm-and-its-role-in-pim/)
- [Bourdier 2024 — open-source vs proprietary actigraphy](https://academic.oup.com/sleep/article/48/4/zsae267/7904637)

## Tentacle D: On-device ML for Sleep

### Discoveries

| Model / Framework | URL | Size | Inputs | Validation | License | TFLite-ready? |
|---|---|---|---|---|---|---|
| **lightCNNA** (Borghi et al., 2021) | https://pmc.ncbi.nlm.nih.gov/articles/PMC7801620/ | **1,361 params** (~5 KB), 2,727 FLOPs | Triaxial wrist accel ~100 Hz only | Cohen's kappa 0.78, sens 89.2%, spec 92.0% vs PSG | Paper open access; code not on a known repo | **Yes** — authors explicitly cite TFLite converter path; also exportable as C array |
| **Micro SleepNet** (Yang et al., Frontiers Neurosci 2023) | https://pmc.ncbi.nlm.nih.gov/articles/PMC10416229/ | 48,226 params, **~100 KB on-device**, 2.8 ms / epoch on Android | **Single-channel EEG** (30 s epochs) | 83.3% overall acc, kappa 0.77 | Open access paper; no public repo found | Yes, designed for mobile; **but EEG = unusable for Bios** |
| **DeepSleepNet** | arxiv 1703.04046 | ~21 M params (~80 MB) | Single-channel EEG | High acc but not mobile-suitable | Research | Conversion possible but too large; **EEG = unusable** |
| **DeepSleepNet-Lite** | arxiv 2108.10600 | Reduced variant, still EEG | EEG 90 s seq | With uncertainty estimates | Open paper | Same EEG blocker |
| **Walch `sleep_classifiers`** (Stanford / UMich, SLEEP 2019) | https://github.com/ojwalch/sleep_classifiers | sklearn NN/RF; small (KB-scale once exported) | Wrist accel + PPG HR + clock proxy (Apple Watch) | 90% sleep/wake epoch acc; 72% 3-class (wake/NREM/REM) | **MIT** | Not packaged for TFLite, but trivial to re-export RF/NN via sklearn-onnx / TF |
| **PhysioNet `sleep-accel` dataset** | https://physionet.org/content/sleep-accel/1.0.0/ | n/a (dataset) | 31 subjects, Apple Watch accel + HR + PSG labels | Gold-standard PSG ground truth | ODC-By | Training corpus, not a model |
| **bpine0 Sleep-Stages-Classification** | https://github.com/bpine0/Sleep-Stages-Classification | Random Forest | Android phone accel + Huawei Watch HR | Self-reported 98% (single-subject; suspect) | Unspecified | Easy to port via sklearn → TFLite/ONNX |
| **OxWearables `asleep`** (Yuan et al., npj Digital Medicine 2024) | https://github.com/OxWearables/asleep | Python pkg; size unspecified | Triaxial wrist accel only, 1000+ PSG-labelled nights | Best-in-class for wrist actigraphy 2024 | Per repo LICENSE | Python only; PyTorch model — convertible to ONNX |
| **HuggingFace `karnamgyal/sleep-stage-classifier`** | https://huggingface.co/karnamgyal/sleep-stage-classifier | CNN-LSTM | EEG + EOG + EMG | n/a | n/a | **EEG = unusable** |
| **LTA2V** (self-supervised long-term accel, 2025) | https://www.sciencedirect.com/science/article/pii/S0952197624019171 | Pretrained encoder | Wrist accel only | F1 73.8% sleep/wake (beats supervised baseline by 3.4 pp) | Paper only | Research stage |
| **Flower Framework** (FL) | https://flower.ai/ | n/a | n/a | Healthcare deployments documented | Apache 2.0 | **Works on Android without GPS**; protocol-level integration, ships TFLite training loop |
| **FedKit** (cross-platform FL) | https://arxiv.org/html/2402.10464v1 | n/a | n/a | Campus-scale health deployment | Open | Android + iOS, no Google Play Services dependency |

### Deep dives

#### lightCNNA — the most interesting candidate for an accel-only phone classifier
- **What**: 3-layer 1D CNN, 8 filters/layer, kernel 8. Inputs raw triaxial wrist accelerometer at ~100 Hz, outputs sleep/wake.
- **Inputs / outputs**: Raw triaxial accel windows; binary sleep/wake per epoch. **No HR needed.**
- **Size on device**: ~5 KB weights, 2.7 K FLOPs/inference. Fits anywhere, runs on the cheapest target.
- **Could replace Bios rule engine?** **Maybe — but with a major caveat.** The model is trained on *wrist-worn* accelerometer at 100 Hz. A phone on the nightstand or under the pillow is not wrist actigraphy: sensor placement, sampling continuity, and accel signature all differ. The 0.78 kappa applies in-distribution; phone placement is out-of-distribution. Plausibly works for the *wearable* path (Bios has Gadgetbridge, Garmin, etc. adapters that can expose accel), but for phone-only it's an open empirical question. The current rule engine already targets the same accel-variance signal — replacing the rule with a 1,361-param CNN trained on a different domain is unlikely to be a slam dunk.
- **Cost to integrate**: Low. Re-implement in Keras from the paper (architecture is trivially specified), train on PhysioNet `sleep-accel`, convert via TFLite, ship as `sleep_accel_classifier.tflite` (~5–20 KB). Reuse existing `TFLiteAnomalyModel.kt` loader pattern — copy ~30 LOC.

#### Walch et al. `sleep_classifiers` — best fit for wearable-augmented path
- **What**: scikit-learn pipeline (NN, RF, logistic) operating on motion + HRV + circadian clock proxy.
- **Inputs / outputs**: Wrist accel + PPG HR + time-of-day. Outputs sleep/wake (90% acc) or wake/NREM/REM (72% acc).
- **Size on device**: KB-scale once exported. RFs convert cleanly via `sklearn-onnx` → ONNX Runtime Mobile; NN via TFLite.
- **Could replace Bios rule engine?** **No for phone-only** (needs HR), **yes for any wearable-connected setup**. This is the model to ship the day a watch is in the loop. Paper, dataset and reference impl are all public — Bios could realistically have a v1 in a couple of weeks.
- **Cost to integrate**: Medium. Re-train against unified Bios metric schema; build the feature extractor (motion accel band-energy + HR + HR local SD + circadian phase). Ship `wearable_sleep_classifier.tflite`. The clock-proxy feature alone (sin/cos of hour) typically buys ~5 pp accuracy and Bios already has timestamps.

#### Micro SleepNet — academic interest only for Bios
- **What**: Mobile-tuned 1D CNN for EEG sleep staging. Notable because it explicitly proves that <100 KB / <3 ms inference is achievable on Android — the architecture story is transferable even though the input modality isn't.
- **Inputs / outputs**: Single-channel EEG, 30 s epochs.
- **Could replace Bios rule engine?** **No.** Bios has no EEG input on any supported source (Health Connect, Gadgetbridge, Oura, WHOOP, Garmin, Withings, Dexcom, phone). Listed for completeness — confirms the EEG-grade SOTA is irrelevant to Bios's actual signal set.

#### Flower / FedKit (federated learning frameworks)
- **What**: Apache-2.0 framework for federated training across heterogeneous clients. Android client uses TFLite training loop (LiteRT now supports on-device training for a defined set of ops). No Google Play Services dependency.
- **Could power Bios's roadmap commitment to federated learning?** **Yes — directly.** Flower's Android example already shows TFLite on-device training + parameter exchange via the Flower protocol. Works on LineageOS / LETHE. Bios would only need to operate (or have a partner operate) the aggregation server.
- **Cost**: Non-trivial — requires a server, a stable cross-owner training loop, and a privacy budget (differential-privacy or secure-agg). Unblocks the existing "retrain with federated learning later" comment in `TFLiteAnomalyModel.kt`.

### Honest assessment of the phone-only ML hypothesis

Specific question from the brief: **is there a small TFLite model Bios could ship that takes (accel variance, screen state, charging, lux) as input and outputs sleep/wake, that would beat the current rule-based approach?**

**Probably not by a meaningful margin, for two reasons:**

1. **No model trained on that exact feature set exists.** Every model surveyed trains on either raw wrist-accel waveforms, or wrist-accel + HR, or EEG. None use screen-state / charging / lux. Bios would need to train its own model on its own data, which (a) requires labelled ground truth Bios doesn't have, and (b) the published phone-only literature (iSenseSleep, Sleep as Android's own self-evaluation) shows ~30 min RMSE for sleep duration vs PSG — the same ballpark the rule engine already targets per the existing comment in `PhoneSleepInference.kt`.
2. **Sleep as Android's published self-evaluation** (most mature phone-only product on market) reports their app catches "only about 1/3 of actual awake periods, with about half of detected awake events being false positives". They've been at this for a decade with ML. The ceiling on phone-only is low and the rule engine isn't far below it.

The likely real causes of failure-in-practice (left to other tentacles to confirm) are more mundane than "we need ML":
- Doze / battery optimisation killing the foreground service that collects samples;
- Phone-not-near-the-bed nights producing empty windows that the algo treats as "no sleep" rather than "no data";
- `MIN_SLEEP_WINDOW_MS` / `MIN_SLEEP_DURATION_MS` thresholds excluding short or interrupted sleep;
- Screen-on events from notifications splitting one window into two.

ML cannot fix any of those — they are collection-layer bugs, not classifier bugs.

### Recommendations for Bios

1. **Don't replace the rule engine with ML for phone-only.** The ceiling is too low, the training data doesn't exist, and the most likely failure modes are upstream of the classifier. Fix collection lifecycle first (Tentacles B / C territory) and adopt Cole-Kripke (per Tentacle B) before adding any model.
2. **Do prepare a TFLite sleep model for the wearable path.** Re-implement Walch et al. against `sleep_classifiers` + PhysioNet `sleep-accel`, train, quantize, ship as `wearable_sleep_classifier.tflite` (target <100 KB). Wire it behind a `SleepClassifier` interface so the rule engine remains the phone-only fallback. Once any wearable adapter (Gadgetbridge / Oura / WHOOP / Garmin) provides accel + HR, the ML path activates automatically.
3. **If a phone-only ML experiment is wanted anyway**, the lowest-cost play is a tiny logistic / GBM trained on `(accel variance bucket, screen-off, charging, lux band, hour-of-day-sin, hour-of-day-cos)` against the owner's own confirmed sleep windows (self-labelled). Ship as <10 KB TFLite. Treat the rule engine output as an additional feature, not a replacement. Personal-baseline > population model for this feature set, which matches Bios's "instrument, not coach" philosophy.
4. **Adopt Flower for the federated path** committed to in the roadmap. Apache-2.0, working Android TFLite training client, no Google Play Services — matches Bios's LETHE / degoogled posture. Build the FL server on top of the planned Bios sync-gateway.
5. **EEG-based SOTA (DeepSleepNet, Micro SleepNet, ZleepAnlystNet, YASA) is irrelevant to Bios.** Document this once in `docs/ARCHITECTURE.md` and stop chasing it — none of Bios's sources expose EEG.

### Sources
- [Borghi et al. 2021 — lightCNNA / efficient embedded sleep wake classification](https://pmc.ncbi.nlm.nih.gov/articles/PMC7801620/)
- [Yang et al. 2023 — Micro SleepNet](https://pmc.ncbi.nlm.nih.gov/articles/PMC10416229/)
- [DeepSleepNet (arXiv 1703.04046)](https://arxiv.org/pdf/1703.04046)
- [DeepSleepNet-Lite (arXiv 2108.10600)](https://arxiv.org/pdf/2108.10600)
- [ojwalch/sleep_classifiers (GitHub)](https://github.com/ojwalch/sleep_classifiers)
- [Walch et al. 2019 SLEEP](https://academic.oup.com/sleep/article/42/12/zsz180/5549536)
- [PhysioNet sleep-accel dataset](https://physionet.org/content/sleep-accel/1.0.0/)
- [bpine0/Sleep-Stages-Classification (GitHub)](https://github.com/bpine0/Sleep-Stages-Classification)
- [OxWearables/asleep (GitHub)](https://github.com/OxWearables/asleep)
- [Yuan et al. 2024 npj Digital Medicine — asleep validation](https://www.nature.com/articles/s41746-024-01016-9)
- [HuggingFace karnamgyal/sleep-stage-classifier](https://huggingface.co/karnamgyal/sleep-stage-classifier)
- [LTA2V self-supervised accel sleep-wake](https://www.sciencedirect.com/science/article/pii/S0952197624019171)
- [Flower Framework](https://flower.ai/)
- [Flower on Android blog (2021)](https://flower.ai/blog/2021-12-15-federated-learning-on-android-devices-with-flower/)
- [On-device Federated Learning with Flower (arXiv 2104.03042)](https://arxiv.org/pdf/2104.03042)
- [FedKit cross-platform FL (arXiv 2402.10464)](https://arxiv.org/html/2402.10464v1)
- [Sleep as Android — automatic tracking docs](https://sleep.urbandroid.org/docs/sleep/automatic_sleep_tracking.html)
- [Sleep as Android — sleep-lab comparison (self-eval)](https://sleep.urbandroid.org/sleep-lab-comparison/)
- [iSenseSleep validation (PMC6547769)](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6547769/)
- [Unobtrusive Sleep Monitoring using Smartphones (Cornell)](https://pac.cs.cornell.edu/pubs/Unobtrusive_Sleep_2013.pdf)

## Tentacle C: Wearables & Health Connect

Investigation date: 2026-05-23. Theme: FOSS wearable -> sleep pipelines that Bios can plug into when phone-only inference is too noisy. The premise: if Tentacle B's algorithmic improvements and Tentacle D's ML still leave phone-only too unreliable, what hardware should Bios formally recommend, and what FOSS sleep pipelines feed it?

### Discoveries

| Project / Device | URL | What it provides | License | FOSS-compatible? | Already in Bios? |
|---|---|---|---|---|---|
| Gadgetbridge | https://codeberg.org/Freeyourgadget/Gadgetbridge | Companion app + ContentProvider for 40+ wearables; stores raw activity samples (HR, steps, intensity, raw_kind sleep stage) | AGPL-3.0 | Yes (F-Droid, no GMS) | Yes - `GadgetbridgeAdapter.kt` reads ACTIVITY_URI |
| Xiaomi Mi Band 6/7/8 | https://gadgetbridge.org/gadgets/wearables/xiaomi/ | Continuous HR, steps, motion; device-reported total sleep time. Sleep stages weak on Huami; better on Mi Band 8 (xiaomi-protobuf, experimental) | Closed firmware, but Gadgetbridge speaks the protocol | Yes via Gadgetbridge | Indirect (via GB adapter) |
| Amazfit (Zepp OS 3.5+, GTR 4, Active 2) | https://gadgetbridge.org/gadgets/wearables/ | HR, steps, motion + device-reported sleep stages incl. deep sleep | Closed firmware | Yes via Gadgetbridge (token fetch needs root on newer Huami) | Indirect |
| Bangle.js 2 | https://banglejs.com / https://github.com/espruino/BangleApps | Fully hackable JS smartwatch; `sleeplog` app derives sleep from accel + HR on-watch; syncs to Gadgetbridge | MIT (firmware), apps mostly MIT | Yes - first-class | Indirect (via GB adapter) |
| PineTime + InfiniTime | https://github.com/InfiniTimeOrg/InfiniTime | nRF52 smartwatch, HR + accel + 24/7 HR since PR #2322. **No native sleep tracking yet** (issue #307, bounty PR #2174 in flight) | GPL-3.0 firmware, open hardware | Yes | Indirect when sleep ships |
| PineTime + wasp-os + SleepTk | https://github.com/thiswillbeyourgithub/SleepTk_pinetime_sleep_tracker | MicroPython fork with on-watch sleep tracker + smart alarm; manual start | GPL-3.0 | Yes, but niche | No |
| Pebble (revived) | https://pebble.dev (Rebble / Core Devices) | Month battery, accel + HR (Pebble 2), GB support since day one | Pebble OS partly open; firmware partly closed | Yes via Gadgetbridge | Indirect |
| Garmin Instinct 3 / Fenix / Venu | https://gadgetbridge.org | Native on-watch sleep staging, syncs over BLE to GB without Garmin app | Closed firmware | Yes via Gadgetbridge nightly | Yes (also direct via `GarminApiAdapter`) |
| AsteroidOS 2.0 | https://asteroidos.org | Linux for Fossil Gen 4-6, Huawei Watch, Ticwatch, Moto 360 (2015) | LGPL/GPL | Yes | No - no sleep tracking yet ("palm gesture for sleep mode" only) |
| Health Connect (`SleepSessionRecord`) | https://developer.android.com/reference/androidx/health/connect/client/records/SleepSessionRecord | Read sleep sessions + per-stage breakdowns aggregated across any provider (Samsung Health, Sleep as Android, Fitbit HC bridge, Withings, Oura via vendor bridges) | API is open, providers vary | Available on Android 14+ (and as APK on 9-13). Requires GMS-free Health Connect build for degoogled ROMs | Yes - `HealthConnectAdapter.kt` |
| Sleep as Android | https://sleep.urbandroid.org/docs/services/health_connect.html | Phone+wearable sleep with motion/HR/SpO2/snoring, writes to Health Connect, integrates Bangle.js & PineTime as data sources | Closed source, on F-Droid (IzzyOnDroid) | Partial (proprietary) | Indirect via HC if user installs |
| plees-tracker | https://f-droid.org/en/packages/hu.vmiklos.plees_tracker/ | Manual sleep logger | MIT | Yes | No - too simple to be useful as inference source |

### Deep dives

#### Gadgetbridge (already integrated)
- **What**: AGPL companion app on F-Droid; reverse-engineers BLE protocols for 40+ wearable lines. Stores everything in local SQLite; exposes a `content://nodomain.freeyourgadget.gadgetbridge.database/activity` ContentProvider with timestamp / heart_rate / steps / raw_intensity / raw_kind columns.
- **Sleep approach**: Two paths. (1) Device-reported stages: Zepp OS 3.5+, Garmin, Huawei (no TruSleep), Nothing CMF, and xiaomi-protobuf watches push their own light/deep/REM classifications via `raw_kind` (1=light, 2=deep, 4=awake, 5=REM). Bios already maps these in `GadgetbridgeAdapter.fetchSleepSamples`. (2) Heuristic from HR + motion: for everything else, GB just shows HR pattern and intensity spikes - no algorithmic stage classifier. The docs explicitly admit "for most watches, the deep sleep detection is not reliable."
- **Signal quality vs phone-only**: A wrist-worn device with continuous HR + accelerometer is dramatically better than phone-only at the binary asleep/awake decision (the only thing baseline math really needs per the brief's +/-30 min RMSE target). Even on a Mi Band where stages are noisy, total sleep time and onset/offset are solid. Phone-only struggles with the basics: device on a nightstand has no idea if the owner is in bed or watching TV.
- **Bios integration path**: **Already done**. The gap isn't code; it's UX - users on phone-only mode need a prompt: "Bios works much better with a wearable. Install Gadgetbridge and pair any supported device."

#### Bangle.js 2 + `sleeplog`
- **What**: Open-hardware JS smartwatch (~$89, hackable, 1-2 week battery). Apps are MIT-licensed JS in a GitHub repo (`espruino/BangleApps`). `sleeplog` runs on-watch using accelerometer + optional HR; syncs back via Gadgetbridge which recognizes Bangle as a device.
- **Sleep approach**: Accel-based actigraphy with HR confirmation; classifies awake/light/deep/REM heuristically (no clinical claim). The on-watch algorithm is essentially Cole-Kripke variants (see Tentacle B) executed in JS.
- **Signal quality vs phone-only**: Strictly better - it's actually on the wrist all night. Stage classification is rough but total sleep time is reliable.
- **Bios integration path**: Already works through `GadgetbridgeAdapter`. Worth naming explicitly in the recommended-hardware doc.

#### PineTime (InfiniTime + bounty PR #2174)
- **What**: ~$27 nRF52 smartwatch from Pine64, open hardware, GPL firmware.
- **Sleep approach**: Today, **none in InfiniTime**. 24/7 HR landed via PR #2322 in 2025; sleep tracking PR #2174 is bountied but not merged as of investigation date. wasp-os + `SleepTk` works today but requires manual "I'm going to bed" gesture - not autonomous.
- **Signal quality vs phone-only**: When the InfiniTime PR lands, comparable to Bangle.js. Today, **worse than phone-only** for inferred sleep because tracking is manual.
- **Bios integration path**: Same as Bangle - readings flow through Gadgetbridge once InfiniTime publishes them. **Do not recommend PineTime for sleep yet.**

#### Health Connect (`SleepSessionRecord`) as universal hub
- **What**: Android 14+ system API (also installable on 9-13). Apps read/write sleep sessions with per-stage breakdowns; Health Connect de-duplicates across providers.
- **Sleep approach**: Not an inference engine - just a typed store. Real value: if the user has Samsung Health, Fitbit (via HC bridge), Withings, Oura (via vendor bridge), or Sleep as Android, Bios reads it for free without writing a new adapter.
- **Signal quality vs phone-only**: Depends on the provider, but virtually any wearable provider beats phone-only.
- **Bios integration path**: Already integrated (`HealthConnectAdapter.kt`). **Verify it reads `SleepSessionRecord` + `SleepSessionRecord.Stage`, not just HR/steps** - this is the most likely gap and the cheapest one-day unlock.

#### AsteroidOS 2.0
- **What**: Linux-based smartwatch OS for ~30 devices (Fossil Gen 4-6, Huawei Watch, Ticwatch, OPPO Watch, Moto 360 2015). Active again in 2026.
- **Sleep approach**: None. 2.0 added HR + step counting + "palm gesture for sleep mode" (DND, not tracking). Sleep tracking would have to be built upstream.
- **Bios integration path**: Skip for now. Worth revisiting if AsteroidOS adds a sync target.

### Recommendations for Bios

1. **Make Gadgetbridge the headline recommendation.** When `PhoneSleepWorker` produces low-confidence sleep for N nights running, surface an in-app banner: "Phone-only sleep detection is unreliable. For accurate baselines, pair any supported wearable through Gadgetbridge." Link to a curated list of cheap supported devices. The adapter is already shipped; the missing piece is owner education. (Pairs naturally with Tentacle D's conclusion that ML can't rescue the phone-only path.)

2. **Tiered hardware recommendation (cheapest-first, all FOSS-pipeline-friendly):**
   - **Tier 1 (best $/signal): Xiaomi Mi Band 7/8 (~$40-50).** Excellent HR + motion, decent battery, well-supported in Gadgetbridge. Mi Band 8 even pushes raw sleep stages. Caveat: pairing token on newer Huami firmware sometimes needs root or a one-time vendor-app handshake - document this.
   - **Tier 2 (best FOSS philosophy): Bangle.js 2 (~$89).** Fully open hardware + firmware, MIT app ecosystem, integrates via Gadgetbridge. Recommend to owners whose supply-chain ethics match Bios's manifesto.
   - **Tier 3 (premium): Garmin Instinct 3 / Fenix.** On-watch sleep staging, multi-week battery, syncs to Gadgetbridge without the Garmin app. Bios also has `GarminApiAdapter` as fallback.
   - **Skip (for now): PineTime** - sleep tracking PR not merged; **AsteroidOS** - no sleep tracking; **Pebble** - check Core Devices ship date before recommending.

3. **Mine Gadgetbridge's code? Partially yes, with sharp scope.** Gadgetbridge does **not** contain a phone-side sleep inference algorithm worth lifting - its "algorithm" for non-reporting devices is essentially "show HR + intensity, let the user squint." What *is* worth mining: (a) the BLE handshake/protocol code for specific devices if Bios ever wants to bypass Gadgetbridge for a direct adapter (AGPL contagion - probably not worth it given Bios's app license), and (b) the `raw_kind` enum mapping (already lifted into our adapter). **Net: keep Gadgetbridge as a separate process, read its ContentProvider, do not fork its algorithms.** For the algorithm itself, use Tentacle B's Cole-Kripke recommendation.

4. **Verify `HealthConnectAdapter` reads `SleepSessionRecord` end-to-end.** If it currently only ingests HR/steps/etc. and ignores sleep sessions, that's a one-day fix that unlocks any user who already runs Samsung Health, Fitbit-via-HC, Withings, Sleep as Android, etc. - no new adapter work.

5. **Document the LETHE caveat.** Health Connect requires either AOSP 14+ with HC preinstalled or the standalone Health Connect APK. The LETHE flavor should prefer Gadgetbridge when HC is absent. The current ingest stack already supports this (both adapters are independent), but it should be explicit in onboarding.

### Sources (Tentacle C)
- [Gadgetbridge sleep tracking docs](https://gadgetbridge.org/basics/features/activities/)
- [Gadgetbridge best device FAQ](https://gadgetbridge.org/faq/best-device/)
- [Gadgetbridge PineTime wiki](https://codeberg.org/Freeyourgadget/Gadgetbridge/wiki/PineTime)
- [Sleep as Android + Health Connect](https://sleep.urbandroid.org/docs/services/health_connect.html)
- [Health Connect SleepSessionRecord](https://developer.android.com/reference/androidx/health/connect/client/records/SleepSessionRecord)
- [InfiniTime sleep tracking issue #307](https://github.com/InfiniTimeOrg/InfiniTime/issues/307)
- [SleepTk (wasp-os)](https://github.com/thiswillbeyourgithub/SleepTk_pinetime_sleep_tracker)
- [BangleApps sleeplog issue #277](https://github.com/espruino/BangleApps/issues/277)
- [Privacy Guides community thread](https://discuss.privacyguides.net/t/gadgetbridge-compatible-smartwatch-with-good-sleep-and-health-tracking/32737)
- [AsteroidOS 2.0 release notes](https://www.cnx-software.com/2026/02/18/asteroidos-2-0-open-source-smartwatch-os-released-now-supports-around-30-devices/)
- [plees-tracker on F-Droid](https://f-droid.org/en/packages/hu.vmiklos.plees_tracker/)
