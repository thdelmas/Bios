# Bios Documentation

## What Bios is
- [specs/instrument-only.md](specs/instrument-only.md) — The product decision: Bios measures, compares with the owner's usual range, and shows
- [ROADMAP.md](ROADMAP.md) — Current state and open product questions

## Architecture
- [ARCHITECTURE.md](ARCHITECTURE.md) — Components, build flavors, data flow
- [DATA_MODEL.md](DATA_MODEL.md) — Unified metric schema, entities, FHIR mapping
- [PRIVACY_ARCHITECTURE.md](PRIVACY_ARCHITECTURE.md) — Where data lives, encryption, every way data can leave the phone
- [TECH_STACK.md](TECH_STACK.md) — Libraries, modules, tests, CI
- [ECOSYSTEM_BOUNDARIES.md](ECOSYSTEM_BOUNDARIES.md) — What belongs to Bios and what belongs to companion apps
- [CONSUMER_API.md](CONSUMER_API.md) — `BiosHealthProvider` contract for companion apps
- [SELF_REPORTED_DATA_HOME.md](SELF_REPORTED_DATA_HOME.md) — How owner-entered data is stored and kept apart from sensor data

## Features
- [CAMERA_PPG.md](CAMERA_PPG.md) — Fingertip heart rate and HRV capture with the camera
- [LAB_OCR_INGESTION.md](LAB_OCR_INGESTION.md) — Reading lab values off a report on-device
- [WEARABLES.md](WEARABLES.md) — Device and sensor matrix, and how readings reach Bios
- [GAP_REGISTER.md](GAP_REGISTER.md) — Instrumentation gaps found by reading a real export

## Specs
- [specs/alert-detail.md](specs/alert-detail.md) — The alert detail screen
- [specs/rule-window-coverage.md](specs/rule-window-coverage.md) — Window coverage and freshness before a comparison counts
- [specs/source-preference.md](specs/source-preference.md) — One source per day for count metrics
- [specs/source-liveness.md](specs/source-liveness.md) — Telling when a source has stopped delivering
- [specs/known-sources.md](specs/known-sources.md) — The Known Sources screen
- [specs/day-totals.md](specs/day-totals.md) — Day totals in trends
- [specs/coros-direct-ble.md](specs/coros-direct-ble.md) — Reading a COROS watch without the vendor app (stopped)

## Design
- [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) — Visual and design system for Bios and its companion apps
- [design-tokens/](design-tokens/) — Token files
- [brand/](brand/) — Icon

## Project (root level)
- [CLAUDE.md](../CLAUDE.md) — Project conventions
- [GOVERNANCE.md](../GOVERNANCE.md) — How decisions are made
