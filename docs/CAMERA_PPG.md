# Camera-PPG HRV Snapshot

An on-demand fingertip-PPG reading captured through the rear camera and
flash. Use it as a **backup channel** when your wearable isn't reporting
HRV, or when you want a point-in-time snapshot after a specific event.

## When to use it

- **Your wearable doesn't stream HRV.** Some devices (e.g. Kiprun GPS 500)
  record HR but never expose HRV to Health Connect. Camera-PPG fills the
  gap.
- **Post-stressor snapshots.** After a nightmare, a confrontation, a bad
  night of travel — capture a 60-second reading to see whether your
  autonomic nervous system is still elevated.
- **Baseline building in the first 14 days.** Bios needs rolling data
  before it can detect drift. Manual snapshots seed the baseline faster.
- **Cross-checking a suspect wearable reading.** If your watch shows an
  unusually high or low HRV number, a camera snapshot is a second opinion
  from an independent sensor.

## When NOT to use it

- **As a replacement for continuous monitoring.** Camera-PPG only captures
  what you actively measure; trends require many readings. Keep your
  wearable as the primary source.
- **In any medical or emergency context.** This is not a medical device.
  It will not detect arrhythmia, cardiac events, or any specific
  condition. If something feels wrong, call a doctor — do not open this
  screen.
- **To diagnose yourself.** Bios reports numbers, not verdicts. A
  single-snapshot RMSSD outside your personal range means *one data
  point* worth discussing with a clinician — not a diagnosis.

## How to take a reading

1. Open Bios. On the **Home** tab, tap **Take HRV Snapshot**.
2. Grant camera access on first use. Frames never leave your device; only
   the extracted HR and HRV numbers are kept.
3. Place your **fingertip fully over the rear camera and flash**. Most
   phones have both near the top-back of the device; check the lens
   placement before starting.
4. Use **light pressure** — enough to cover the lens with skin, not so
   hard it cuts off circulation. If the reading is consistently rejected
   for "insufficient signal," you're probably pressing too hard.
5. Tap **Start capture** and hold still for the 60-second countdown. Rest
   your hand on a surface. Don't look at the screen — the flash will be on
   and the image is not useful to you.
6. After the countdown, Bios shows either a **saved reading** (HR +
   RMSSD + quality score) or a **rejection** with a specific reason. If
   rejected, tap **Retry** and address the reason.

## Reading the result

- **Heart rate (bpm).** Average over the capture window.
- **HRV (RMSSD, ms).** Root-mean-square of successive differences — the
  standard short-recording HRV metric, driven by parasympathetic
  (vagal) tone.
- **Quality (0–100).** Composite signal quality: luminance stability,
  saturation, peak-amplitude variability, and RR regularity. Above ~70
  is a clean reading; below ~40 you should retry.
- **Peaks.** Number of detected heartbeats. Fewer than ~40 peaks for a
  60-second recording means the signal was too weak to trust.

## Rejection reasons and what they mean

| Reason | What happened | What to do |
|---|---|---|
| Recording too short | The capture stopped before 30 s | Hold for the full countdown |
| Insufficient signal | No clear pulse wave in the Y-channel (finger not fully covering lens, or lens dirty) | Clean the lens, reposition the fingertip |
| Saturation | The image is too bright (usually overexposure from direct sunlight) | Move to a shadier spot or lighten pressure |
| Motion artifact | Peak amplitudes swung too much between beats | Rest your hand on a surface, take a breath, retry |
| Too few beats | Fewer than ~20 clean peaks extracted | Check finger placement and retry |
| Irregular rhythm | RR intervals passed peak detection but the HRV analyser rejected too many as artifacts | Retry in a calmer state; if this persists, consider a clinician visit — genuine ectopic beats look like this |
| Hardware unavailable | No rear camera or no flash on this device | This feature requires both — use your wearable |

## Limitations

- **Motion sensitivity.** Even sub-millimetre finger tremor corrupts the
  signal. Rest your hand on something solid.
- **Cold fingers.** Poor peripheral circulation reduces pulse amplitude.
  Warm your hands first.
- **Pigmentation & thick skin.** Heavily callused or darker-pigmented
  fingertips return weaker signals on most phone cameras. This is a known
  PPG limitation, not a Bios choice — if your readings consistently
  reject, rely on a contact sensor instead.
- **Phone variation.** Cameras with faster autoexposure (most modern
  devices) give cleaner signals. Older phones with aggressive gain control
  can fight the PPG signal.
- **Confidence tier: LOW.** Camera-PPG is tagged `LOW` in Bios's
  deduplication logic. If you have a chest strap or ring reading for the
  same window, that source wins.

## Privacy

- Camera frames live in a rolling in-memory buffer during capture and are
  **discarded** as soon as the 60-second window ends.
- Only the extracted numbers (HR, RMSSD, timestamp, source, confidence,
  quality, peak count, duration) are written to the local Room database.
- Nothing is uploaded. Nothing leaves the device unless you explicitly
  export your data from **Settings → Export**.
- Erasure via **Delete all data** destroys camera-PPG readings alongside
  every other source.

## Validation (for power users)

If you own a chest-strap ECG (Polar H10, Wahoo Tickr, etc.) and want to
audit your phone's camera-PPG accuracy:

1. Pair the strap with a reference HRV app (HRV4Training, EliteHRV, or the
   strap's own app).
2. Take 10 paired readings over a few days — same start time, same
   resting condition, same hand.
3. Record both RMSSD values.
4. Compute the mean absolute error: `MAE = mean(|camera_rmssd -
   strap_rmssd|)`.
5. Expectation: on a good phone in a well-controlled setting, MAE under
   ~8 ms is typical. MAE above ~15 ms suggests your phone's camera is
   poorly suited to PPG — the strap should be your primary source.

Bios ships a `PpgValidation` utility
([android/.../engine/PpgValidation.kt](../android/app/src/main/java/com/bios/app/engine/PpgValidation.kt))
for computing MAE, bias, and range from paired readings. See the unit test
for the shape of input it expects.

## Cross-references

- [ARCHITECTURE.md §1](ARCHITECTURE.md) — adapter list
- [DATA_MODEL.md](DATA_MODEL.md) — MetricReading schema
- [PRIVACY_ARCHITECTURE.md](PRIVACY_ARCHITECTURE.md) — on-device guarantees
- Tracking issue: [GH-13](https://github.com/thdelmas/Bios/issues/13)
