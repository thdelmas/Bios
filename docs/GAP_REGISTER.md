# Bios — Instrumentation Gap Register

Derived from an audit of the product (2026-06-29): a 5-week FHIR export from a Pixel 9a
was analysed, the symptoms traced back to code, and each finding classified as a
**defect**, an **activation/adherence gap**, or **by-design**.

Statuses are as recorded on the date shown in each row. They were not re-verified
when the app became instrument-only (2026-11-21), except where a row says so. Rows
about condition patterns were removed with the patterns.

Severity: **P0** corrupts data integrity / trust · **P1** loses data or coverage ·
**P2** friction / transparency · **P3** nice-to-have.

## Register

| # | Sev | Gap | Root cause (located) | Class | Status |
|---|-----|-----|----------------------|-------|--------|
| G1 | P0 | FHIR export silently truncated and biased to the **oldest** rows | `FhirExporter.kt` — `.take(500)` over a `timestamp ASC` query + 30-day window, no completeness signal | Defect | **FIXED** 2026-06-29 |
| G2 | P0 | Implausible phone-sleep values exported as fact | Confidence tier is computed (`PhoneSleepInference.confidenceFor`) but **never gates**; no plausibility bounds in `SleepDerivations.kt`; no cross-metric check in `IngestManager.deriveAll`; raw dump in `DataExporter`; LOW-confidence rows feed `BaselineEngine` | Defect | Open |
| G3 | P1 | Oura built but never activated | `OuraApiAdapter` (~440 lines) + `OuraTokenStore` + a paste-token row in `DataSourcesScreen` all exist; discovery is 4 taps deep, no onboarding prompt | Activation | Open |
| G4 | — | ~~Periodic workers die on reboot~~ | **Withdrawn.** WorkManager 2.10.0 ships its own boot receiver and reschedules persisted periodic work natively. No app-level `BootReceiver` needed. | Non-bug | Closed |
| G4b | P2 | Foreground services do not auto-restart after reboot | `PhoneSleepCollectionService` — no boot-time restart (rechecked 2026-11-21: the only foreground service left); starting a `health` FGS from `BOOT_COMPLETED` has Android restrictions, so this needs design, not a one-liner | Defect (nuanced) | Open |
| G5 | P2 | Coverage opacity | 190 contract keys (2026-11-21), **~25 with a wired producer** (2026-06-29); a 0-sample metric is indistinguishable from "your device can't measure this" | Mostly by-design | Open |
| G6 | P2 | Manual / companion streams lapse silently | Some entries are manual; other keys arrive from companion apps over the ContentProvider. By design there are no logging nudges; there is no companion-liveness check | Adherence | Open |
| G7 | P3 | Only 3 personal baselines | `BaselineEngine` gate: ≥10 SENSOR samples / 14-day window, manual entries excluded | By-design | Open (surface progress only) |
| G8 | P3 | No owner-authorised one-command read | ContentProvider catch-22: `adb` shell lacks `READ_HEALTH`; the app uid lacks the system `ACCESS_CONTENT_PROVIDERS_EXTERNALLY`. An authorised agent on the owner's own machine must tap through the export UI | Friction | Open |

## Detail & fix plans for the open items

### G2 — sleep plausibility (P0, the trust item)
Phone-on-nightstand is currently indistinguishable from a sleeping owner, and the
derived metrics have no bounds, so the pipeline emitted e.g. ~8 h latency, 8 %
efficiency, ~12 h WASO. Fixes, in order:
1. **Gate by confidence** — drop (or flag) LOW-confidence phone-sleep readings before
   export and before they reach `BaselineEngine`.
2. **Plausibility bounds** in `SleepDerivations.kt` — reject/flag latency, efficiency,
   WASO outside what a night can contain (for example latency longer than time in bed).
3. **Cross-metric checks** in `deriveAll` — e.g. `latency + duration ≤ time-in-bed`.
4. **Data-quality flag** on `MetricReading` surfaced in the export, so a reader
   sees "low_phone_accel" rather than a bare impossible number.

### G3 — Oura activation (P1, best coverage ROI)
Connecting Oura yields HRV, resting HR, skin-temperature deviation and the vendor's
recovery score independent of Health Connect: the metrics that were empty in the export. The code
is complete; only discovery is missing. Touch points:
- `DataSourcesScreen.kt` — add a prominent "Connect Oura" quick-action / empty-state.
- Onboarding flow — after the Health Connect permission grant, a one-time
  "Do you have an Oura Ring?" prompt linking to the paste-token dialog.
- Gate the prompt behind a `ouraOnboardingShown` flag so it never nags.
> Requires an in-app build + verify pass; not shippable blind.

### G4b — foreground-service boot restart (P2)
Decide policy first: silent auto-restart of a health foreground service on boot is
restricted on modern Android and is also a consent question. Likely answer is a
notification ("Phone sleep collection paused. Tap to resume.") instead of a silent
relaunch.

### G5 — coverage transparency (P2)
Add a coverage view grouping metrics into *measured · supported-but-no-data ·
not-supported-by-your-device*, using a runtime capability check rather than the
"0 samples" heuristic. Folds in G7 (show per-metric baseline progress).

## Notes on what is **not** broken

- **Health Connect is fully wired** — every declared read permission has ingestion.
  Missing HRV/SpO₂/RHR is a device-capability gap, not dead code.
- **~173 metrics with no producer** is intentional: they are manual / lab / derived.
- **Sparse baselines** are the strict-by-design gate, not a failure.
