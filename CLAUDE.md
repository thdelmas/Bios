# Bios - Project Conventions

## Project Overview
Bios is a measuring instrument. It collects readings from wearables and phone sensors, keeps them encrypted on the phone, and shows them next to the owner's own usual range. It never says what a measurement means: no named condition, no cause, no advice. The product decision is recorded in [docs/specs/instrument-only.md](docs/specs/instrument-only.md).

Bios is not a medical device.

All processing happens on the device. Bios has no server of its own. The owner decides what leaves the device, and nothing does by default. **Evaluation belongs to the owner.**

**Primary platform:** embedded in LETHE (privacy-hardened Android overlay based on LineageOS).
**Portable to:** any Android device running API 28+ (Android 9 through Android 15).

## Product rules
- **Measure, compare, show.** A reading is shown with its unit, date and source, next to the owner's usual range when one exists.
- **One kind of alert.** Watched metrics outside the owner's usual range, stated in units ("Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm"). No cause, no next step.
- **No interpretation in text.** `VocabularyGateTest` scans string resources and the string literals of the owner-facing packages. A listed word fails the unit suite unless it has an entry with a reason in `VocabularyGateAllowlist`.
- **The owner's records are shown back as written.** Diaries, events, medication list, lab values: stored and displayed, never classified.
- **Silence by default.** No streaks, no engagement notifications, no nudges.
- **Erasure by design.** "Delete all data" destroys the database key. On LETHE, the OS wipe signals (burner mode, dead man's switch, panic, duress) trigger the same destruction.

## Tech Stack
- **Android**: Kotlin + Jetpack Compose, Room with SQLCipher, WorkManager, OkHttp
- **Modules**: `android/app` (the app) and `android/bios-contracts` (metric keys, provider contract and permissions that companion apps compile against)
- **Primary target**: LETHE (LineageOS 22.1 overlay)
- **Portable to**: stock Android and other AOSP-based ROMs (API 28–35)
- **Data sources**: Health Connect, Gadgetbridge, direct sensors, phone sensors (including sleep inferred from the phone), camera PPG, a BLE air-quality peripheral, vendor APIs the owner signs into (Oura, WHOOP, Garmin, Withings, Dexcom, Polar, COROS), manual entry, lab report OCR (Tesseract, on-device), FHIR and Apple Health ECG import
- **Alerting**: `DeviationScore`, a fixed table over z-scores against the owner's baseline. No trained model.
- **No backend.** Nothing in this repository runs on a server.

## Platform Strategy
- LETHE-specific behaviour (agent status, wipe signals, OTA coordination) lives behind the `LetheCompat` interface; on stock Android a no-op implementation is used
- LETHE is detected at runtime (`PlatformDetector`); the loopback API for the LETHE agent starts only there
- Health Connect is used where available; otherwise Bios reads direct sensors and Gadgetbridge
- No Google Play Services dependency. Bios must work on degoogled ROMs.
- Build flavors: `lethe` (application id suffix `.lethe`) and `standalone` (portable APK, the one published)

## Architecture
- See docs/ARCHITECTURE.md for components and data flow
- See docs/DATA_MODEL.md for the unified metric schema
- See [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) — **canonical visual & design system for the entire Bios ecosystem** (Bios + Smokeless / W2F / Virgil / SoulRadio / Idun). When changing visual treatment, palette roles, typography, component anatomy, or the Bios-integration UI block, edit the canonical doc here first; specialists re-sync. Token files in [docs/design-tokens/](docs/design-tokens/).
- See docs/PRIVACY_ARCHITECTURE.md for encryption and for every way data can leave the phone
- See docs/CONSUMER_API.md for the `BiosHealthProvider` ContentProvider contract (read by W2F, future companions)
- See docs/ECOSYSTEM_BOUNDARIES.md for what belongs in Bios vs. companion apps
- See docs/ROADMAP.md for the current state and the open product questions
- See docs/specs/ for the specs behind individual features

## Code Conventions

### Kotlin (Android)
- Follow Kotlin official coding conventions
- Use Jetpack Compose for all new UI
- Room DAOs go in `data/dao/`, entities in `model/`
- Business logic lives in `engine/`, not in UI or data layers
- Prefer `suspend` functions and `Flow` for async operations
- No hardcoded secrets or PII in source code

### File Organization
- Max 500 lines per file. Split before adding features to a file near the limit.
- One class/interface per file (data classes in model/ may share a file if tightly coupled)
- Tests in the matching test source set: `*Test.kt`

### Naming
- Packages: lowercase, no underscores (`com.bios.app.engine`)
- Classes: PascalCase (`BaselineEngine`, `MetricReading`)
- Functions: camelCase (`computeBaseline`, `runDetection`)
- Constants: SCREAMING_SNAKE_CASE

### Testing
- Unit tests: JUnit 4, with Robolectric where a test needs Room or Android resources
- Run `./scripts/code-quality-check.sh` before committing

### Security
- Never paste API keys, tokens, or real user data in prompts
- Readings stay on the device; there is no Bios server to send them to
- AES-256 encryption at rest via SQLCipher

## Pre-commit Checks
The pre-commit hook runs the leak check (`.githooks/leakcheck.py`: no reference to anything outside the repository), then `./scripts/code-quality-check.sh`:
1. File length validation (500-line max for code)
2. No hardcoded secrets
3. Android lint (non-blocking)
4. Detekt static analysis (blocking on new findings)
5. Build verification (`assembleDebug`, both flavors)
6. Unit tests of `:app` and `:bios-contracts` (when Kotlin files are staged)

Commit messages must use conventional format: `type: Description` or `type(scope): Description`
Valid prefixes: `feat`, `fix`, `refactor`, `docs`, `test`, `ci`, `build`, `chore`, `perf`, `style`

A removal is named as a removal in the commit body, so the changelog says so.

## AI-Assisted Development
- Request a plan/summary before implementation on non-trivial changes
- One logical change per prompt
- Reference specific files and existing patterns
- Verify imports/APIs exist before committing AI-generated code
- Run tests between steps
- Never use `--no-verify` to skip pre-commit hooks — fix the issue and recommit
- After completing a task: commit, push, and deploy (don't wait to be asked)
