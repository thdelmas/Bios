# Maintainability Audit — Bios Android Codebase

**Scope:** A code-maintainability review of the Bios Android app (~110K lines of Kotlin across 683 files) and its supporting scripts. This is an *engineering* audit — file organisation, coupling, duplication, test quality, and tooling — not a clinical evaluation. The clinical/domain audits in this directory (cardiology, oncology, the traditional-medicine POVs, Blueprint coverage) cover correctness-of-medicine; this one covers correctness-of-maintenance: how safely the code can keep changing.
**Date:** 2026-05-30
**Branch:** `docs/maintainability-audit`
**Lens:** Maintainability = how cheaply and safely the next change lands. Findings are ordered by leverage (impact on future change × likelihood of being hit), not by severity of any single bug.
**Auditor:** Claude (Opus 4.8)
**Method:** Direct metric collection (file/line counts, marker scans, complexity proxies) + three fan-out deep-dives (duplication, structure, test coverage). **Every headline claim was verified against the source** before inclusion — the test-coverage pass in particular produced several false positives that were corrected (see [§8](#8-method--what-was-corrected)).

Files reviewed: [scripts/code-quality-check.sh](../../scripts/code-quality-check.sh), [android/app/src/main/java/com/bios/app/](../../android/app/src/main/java/com/bios/app/) (engine, ingest, ui, data, alerts), [android/app/src/test/java/com/bios/app/](../../android/app/src/test/java/com/bios/app/), [CLAUDE.md](../../CLAUDE.md), [android/app/build.gradle.kts](../../android/app/build.gradle.kts).

---

## Executive summary

**Bios is a well-tended codebase.** The hygiene metrics most audits flag are already in good shape, and that should be stated plainly before the findings:

- **0 of 683 Kotlin files exceed the 500-line limit** from [CLAUDE.md](../../CLAUDE.md). The rule is genuinely enforced, not aspirational.
- **~40% test-to-source ratio** — 195 test files against 479 source files, high for an Android app.
- **32 TODOs, zero FIXME / HACK / XXX.** The TODOs are disciplined: most cite an issue number and explain the deferral (e.g. [SepsisScreenPattern.kt:137](../../android/app/src/main/java/com/bios/app/alerts/SepsisScreenPattern.kt#L137), the [IrohNode.kt](../../android/app/src/main/java/com/bios/app/sync/p2p/IrohNode.kt) FFI stubs). This is *documented deferred work*, not rot.
- **17 suppressions, every one justified inline**; only 3 `@Deprecated`.
- Clean package layering (`engine/`, `data/`, `ui/`, `alerts/`, `model/`) and a real CI quality gate ([code-quality-check.sh](../../scripts/code-quality-check.sh)).

The findings below are refinements, not alarms. They cluster around **one structural theme** — the 500-line limit is increasingly being satisfied by arbitrary slicing rather than genuine decomposition — plus **one tooling gap** (no Kotlin static analysis) and **one test-quality pattern** (tests that mirror production logic instead of invoking it) that matters more than raw coverage percentage.

| # | Finding | Leverage | Effort |
|---|---|---|---|
| 1 | 500-line rule met by salami-slicing, not decomposition | High | Medium |
| 2 | `AppViewModel` is a service locator that can't be unit-tested | High | Medium |
| 3 | Duplicated HTTP plumbing across 6 API adapters (already drifting) | Medium | Low |
| 4 | Tests that mirror production logic instead of calling it | High | Low |
| 5 | Thin coverage on two safety-critical math units | Medium | Low |
| 6 | No Kotlin static analysis (detekt/ktlint); Android lint is non-blocking | High | Low |

---

## 1. The 500-line rule is met by salami-slicing, not decomposition

The file-length rule is enforced (0 violations), but the most important files sit pinned against the ceiling **while still growing**, which converts a healthy constraint into pressure for arbitrary splits.

| File | Lines | Signal |
|---|---|---|
| [IngestManager.kt](../../android/app/src/main/java/com/bios/app/ingest/IngestManager.kt) | 500 | 56 functions, orchestrates 11 adapters |
| [SettingsScreen.kt](../../android/app/src/main/java/com/bios/app/ui/settings/SettingsScreen.kt) | 500 | — |
| [MainActivity.kt](../../android/app/src/main/java/com/bios/app/ui/MainActivity.kt) | 499 | navigation host |
| [AppViewModel.kt](../../android/app/src/main/java/com/bios/app/ui/AppViewModel.kt) | 499 | 51 functions |
| [BiosDatabase.kt](../../android/app/src/main/java/com/bios/app/data/BiosDatabase.kt) | 498 | 34 DAOs + ~300 lines of inline migrations |

Roughly 35 files sit in the 400–500 band overall. The tell is at [MainActivity.kt:343](../../android/app/src/main/java/com/bios/app/ui/MainActivity.kt#L343), whose own comment says routes were *"extracted to IdentityRoutes.kt to keep this file under the 500-line cap."* That is extraction-to-satisfy-a-number, not extraction-by-responsibility — the next feature has nowhere to go, and the next slice gets carved off wherever the line count forces it.

**Recommendation.** Treat 450+ lines on a *core* file as a design signal, not a lint number, and prefer responsibility-based splits:

- **[BiosDatabase.kt](../../android/app/src/main/java/com/bios/app/data/BiosDatabase.kt):** the inline `MIGRATION_x_y` blocks are ~300 of its 498 lines. Move them to a `BiosDatabaseMigrations.kt` (the pattern is already half-started — some migrations live in `ProfessionalReviewMigrations`/`NeurologyMigrations`); leave the `@Database` annotation + DAO accessors behind.
- **[MainActivity.kt](../../android/app/src/main/java/com/bios/app/ui/MainActivity.kt):** split the `NavHost` graph by domain (e.g. `ClinicalRoutes`, `ReproductiveRoutes`) instead of by line-count pressure, leaving the activity to own lifecycle + permissions only.

## 2. `AppViewModel` is a service locator that can't be unit-tested

[AppViewModel.kt:51-79](../../android/app/src/main/java/com/bios/app/ui/AppViewModel.kt#L51-L79) instantiates ~15 collaborators directly in field initialisers — `BiosDatabase.getInstance()`, every ingest adapter, `BaselineEngine`, `TFLiteAnomalyModel.load()`, `AnomalyDetector`, `AlertManager`. There is no injection seam, so the class cannot be constructed in a unit test without a real `Application`, a real database, and a loaded TFLite model. This is why none of its 51 functions are unit-tested — and the orchestration it performs (sync → baseline → detect → alert) is exactly the sequencing logic most worth testing.

**Recommendation.** Hoist construction into an app-level factory or simple service locator and pass collaborators in via constructor. The ViewModel becomes a thin proxy over injected dependencies. This is the single change that most improves testability of the UI-orchestration layer, and it unblocks Finding 4 (several mirror-tests exist precisely because the real logic is unreachable from a test).

## 3. Duplicated HTTP plumbing across six API adapters — already drifting

All six vendor adapters — [OuraApiAdapter](../../android/app/src/main/java/com/bios/app/ingest/OuraApiAdapter.kt), [WithingsApiAdapter](../../android/app/src/main/java/com/bios/app/ingest/WithingsApiAdapter.kt), [WhoopApiAdapter](../../android/app/src/main/java/com/bios/app/ingest/WhoopApiAdapter.kt), [GarminApiAdapter](../../android/app/src/main/java/com/bios/app/ingest/GarminApiAdapter.kt), [PolarApiAdapter](../../android/app/src/main/java/com/bios/app/ingest/PolarApiAdapter.kt), [DexcomApiAdapter](../../android/app/src/main/java/com/bios/app/ingest/DexcomApiAdapter.kt) — repeat the same three blocks:

- `OkHttpClient.Builder().connectTimeout(30, SECONDS).readTimeout(30, SECONDS).build()` (verified identical in all 6),
- a bearer-token `apiGet()` with the same null-coalescing error handling,
- ISO-8601 timestamp parsing.

The first two are benign-but-costly duplication (a timeout or retry-policy change is six edits). The timestamp parsing is the **harmful** kind: the copies have already diverged in robustness — Polar guards against blank strings, Whoop swallows no exceptions, Dexcom has a custom UTC fallback chain. Divergent copies of "the same" logic are how silent per-vendor bugs appear.

**Recommendation.** Extract a small `ApiHttpClient` (shared client + `get()` + one retry/error policy) and a single `parseIsoTimestamp()` helper. Each adapter collapses to its vendor-specific request shape and response mapping; cross-cutting policy becomes one edit.

## 4. Tests that mirror production logic instead of calling it

This is more important than any coverage-percentage number. Several tests re-implement the function under test as a **private copy inside the test file** and then assert against the copy. Confirmed instances:

- [AnomalyDetectorTest.kt:19](../../android/app/src/test/java/com/bios/app/AnomalyDetectorTest.kt#L19) — *"Mirror of AnomalyDetector.classifySeverity"*, with the `3.0 / 0.8 / 2.0 / 0.5` tier thresholds re-typed locally.
- [BaselineEngineTest.kt:27](../../android/app/src/test/java/com/bios/app/BaselineEngineTest.kt#L27) — *"Mirror of BaselineEngine.computeTrend"*.
- [AlertManagerTest.kt:26](../../android/app/src/test/java/com/bios/app/AlertManagerTest.kt#L26) — mirror of the notification filtering / channel-selection logic.
- [FhirExporterTest.kt:359](../../android/app/src/test/java/com/bios/app/FhirExporterTest.kt#L359) — mirror of the exporter's metric-iteration loop.

A mirror-test **cannot catch a regression in the function it appears to cover.** If `classifySeverity`'s `3.0` threshold drifts to `3.1` in production, every baseline-deviation alert's severity shifts system-wide and the test stays green, because it is asserting against its own parallel copy. This is worse than a missing test: it reads as "covered" in any coverage report. The root cause is usually that the real function is private or buried in a hard-to-construct class — which ties directly back to Finding 2.

**Recommendation.** Make these units directly invocable — extract pure functions like `classifySeverity` to top-level or an `object` — and have the tests call the real implementation. Higher ROI than writing net-new tests, because it converts false confidence into real confidence on logic that already looked covered.

## 5. Thin coverage on two safety-critical math units

After correcting the coverage pass's overstatements ([§8](#8-method--what-was-corrected)), two genuine gaps remain — both in numerically sensitive, clinically consequential code:

- **[Bandpass.kt](../../android/app/src/main/java/com/bios/app/engine/Bandpass.kt)** — the zero-phase Butterworth biquad filter that feeds *all* PPG-derived HR/HRV. It has no direct test; it is only exercised indirectly through `PpgSignalProcessorTest`. The coefficient math (a0 normalisation, Nyquist clamping, forward-reverse zero-phase application) is exactly the kind of DSP that warrants a direct numerical test against a known input/output.
- **[PaediatricVitalRanges.kt](../../android/app/src/main/java/com/bios/app/physiology/PaediatricVitalRanges.kt)** — PALS age-band HR/RR emergency cutoffs, untested. A single decimal slip here mis-flags a normal toddler as URGENT, or misses real tachycardia. A table-comparison test against the published PALS 2020 values would pin it permanently.

## 6. No Kotlin static analysis; Android lint is non-blocking

There is no `detekt`, `ktlint`, or `spotless` configured anywhere in the build, and Android `lint` is explicitly **non-blocking** — [code-quality-check.sh](../../scripts/code-quality-check.sh) prints `WARN: Android lint failed (non-blocking until CI is set up)` and continues. For 110K lines of Kotlin this is the missing automated guardrail: a static-analysis pass would surface the complexity creep behind Finding 1, the duplication in Finding 3, and the 450+-line drift, *before* human review rather than during an audit.

**Recommendation.** Add `detekt` with a generated baseline (so the existing tree doesn't block the first run) and wire it into the existing [code-quality-check.sh](../../scripts/code-quality-check.sh) alongside the file-length and secret checks. Low effort, high ongoing leverage — it makes Findings 1 and 3 self-policing.

---

## 7. What is already good (calibration)

So the findings above are read in proportion:

- **File discipline works.** Zero 500-line violations across 683 files is rare and reflects real attention.
- **TODO hygiene is exemplary.** Issue-linked, explained, no `FIXME`/`HACK`. The deferred-work trail is auditable.
- **Test breadth is strong** where it counts — `SeizureDetector`, `HrvAnalyzer`, `EctopyDetector`, `TremorAnalyzer`, the major alert patterns, and the FHIR export/import round-trip all have dedicated suites. Findings 4 and 5 are about *depth and wiring* on a handful of units, not a coverage desert.
- **Layering is mostly honoured.** Business logic lives in `engine/`. The one notable leak — [IngestManager.kt:57](../../android/app/src/main/java/com/bios/app/ingest/IngestManager.kt#L57) instantiating `CircadianEngine`/`HrbsEngine` inside the ingest layer — is pragmatic and low-harm, worth noting but not urgent.

## 8. Method — what was corrected

Maintainability audits are only useful if their numbers are trustworthy, so each "untested" claim was checked against the test sources rather than inferred from file names. The automated coverage pass produced **several false positives that were removed** from this report:

- `ConcentrationCalculator` — *is* tested ([ConcentrationMathTest.kt](../../android/app/src/test/java/com/bios/app/ConcentrationMathTest.kt)).
- `ScreeningCatalog` — *is* tested ([ScreeningCadenceEngineTest.kt](../../android/app/src/test/java/com/bios/app/ScreeningCadenceEngineTest.kt), [AnatomyRoutingTest.kt](../../android/app/src/test/java/com/bios/app/AnatomyRoutingTest.kt)).
- `FhirMetricCodings` — exercised via [FhirExporterTest.kt](../../android/app/src/test/java/com/bios/app/FhirExporterTest.kt) / [FhirImporterTest.kt](../../android/app/src/test/java/com/bios/app/FhirImporterTest.kt).
- `AnomalySeverity.classifySeverity` — has boundary tests, but they mirror the logic (Finding 4) rather than missing entirely.

Only [Bandpass.kt](../../android/app/src/main/java/com/bios/app/engine/Bandpass.kt) and [PaediatricVitalRanges.kt](../../android/app/src/main/java/com/bios/app/physiology/PaediatricVitalRanges.kt) survived verification as genuinely uncovered (Finding 5). The structural and duplication findings (1, 2, 3, 6) all verified cleanly against source.

---

## Recommended sequence

Ordered to front-load the cheapest guardrails and the changes that unblock others:

1. **Add `detekt` + baseline** to [code-quality-check.sh](../../scripts/code-quality-check.sh) — cheap, stops future drift, makes Findings 1 & 3 self-policing. *(Finding 6)*
2. **Fix the mirror-tests** for `classifySeverity` / `computeTrend` / `sendNotification` — converts false confidence into real coverage on already-"covered" logic. *(Finding 4)*
3. **Extract `AppViewModel`'s construction** into a factory/locator — unlocks UI-layer testing and the mirror-test fixes above. *(Finding 2)*
4. **Extract a shared `ApiHttpClient` + `parseIsoTimestamp`** — removes the already-drifting per-vendor timestamp duplication. *(Finding 3)*
5. **Split `BiosDatabase` migrations and the `MainActivity` nav graph** by responsibility — relieves the two files closest to a forced split. *(Finding 1)*
6. **Add direct tests** for [Bandpass.kt](../../android/app/src/main/java/com/bios/app/engine/Bandpass.kt) and [PaediatricVitalRanges.kt](../../android/app/src/main/java/com/bios/app/physiology/PaediatricVitalRanges.kt). *(Finding 5)*
