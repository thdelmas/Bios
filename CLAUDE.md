# Bios - Project Conventions

## Project Overview
Bios is a health guardian that detects early signs of illness using wearable sensor data. It exists to protect its owner — their health, their data, and their autonomy over both.

All health data processing happens on-device. The owner decides what leaves the device — nothing does by default. Bios watches, notices patterns the owner might miss, and speaks up when something matters. It never evaluates, scores, or nudges the person — only the data.

**Primary platform:** Embedded in [LETHE](~/OSmosis/lethe/) (OSmosis privacy-hardened Android overlay based on LineageOS).
**Portable to:** Any Android device running API 28+ (Android 9 Pie through Android 15).

## Core Alignment with LETHE
Bios shares LETHE's protection philosophy:
- **The owner is final.** Bios advises, warns, and surfaces patterns — but never overrides user autonomy
- **Defense only.** Health data is shielded by encryption, on-device processing, and erasure — never weaponized or monetized
- **No hidden agendas.** No behavioral nudges, no engagement optimization, no data sold to insurers or advertisers
- **Silence is a feature.** Bios speaks when something matters, not to fill a feed
- **Never evaluate the person.** Report "resting HR elevated 2 sigma for 48h" — never "you're unhealthy"
- **Erasure by design.** On LETHE: burner mode and dead man's switch destroy health data alongside the OS. Standalone: instant key destruction on "Delete all data"

## Tech Stack
- **Android**: Kotlin + Jetpack Compose, Room with SQLCipher
- **Primary target**: LETHE (LineageOS 22.1 overlay) — deep integration with LETHE agent, launcher, and privacy stack
- **Portable to**: Stock Android, Samsung One UI, Pixel, other AOSP-based ROMs (API 28–35)
- **Data sources**: Health Connect, Gadgetbridge, Direct Sensors, Oura, WHOOP, Garmin, Withings, Dexcom, Phone Sensors (9 adapters)
- **On-device ML**: LiteRT (Google's rebranded TensorFlow Lite), federated learning framework
- **Backend**: Go + PostgreSQL (sync gateway, community aggregation, model updates, research pipeline)
- **iOS** (planned): Swift + SwiftUI, Core ML

## Platform Strategy
- LETHE-specific features (agent integration, launcher cards, OTA coordination) live behind a `LetheCompat` interface
- On stock Android, Bios runs as a standalone app with no LETHE dependencies
- Health Connect is used on Android 14+; on older versions or LETHE builds without Health Connect, Bios falls back to direct sensor adapters
- No Google Play Services dependency — must work on degoogled ROMs
- Build flavors: `lethe` (embedded, system app) and `standalone` (portable APK)

## Architecture
- See ARCHITECTURE.md for system diagrams
- See DATA_MODEL.md for unified metric schema
- See PRIVACY_ARCHITECTURE.md for privacy/encryption details
- See docs/ROADMAP.md for the owner-protection roadmap (Phases 1-5 complete)

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
- Tests adjacent to code: `*Test.kt` in matching test source set

### Naming
- Packages: lowercase, no underscores (`com.bios.app.engine`)
- Classes: PascalCase (`BaselineEngine`, `MetricReading`)
- Functions: camelCase (`computeBaseline`, `detectAnomalies`)
- Constants: SCREAMING_SNAKE_CASE

### Testing
- Unit tests: JUnit 5 + Kotest for engine and model logic
- UI tests: Compose Testing / Espresso
- Run `./scripts/code-quality-check.sh` before committing

### Security
- Never paste API keys, tokens, or real user data in prompts
- Health data stays on-device; backend never sees raw readings
- AES-256 encryption at rest via SQLCipher

## Pre-commit Checks
All checks run via `./scripts/code-quality-check.sh`:
1. File length validation (500-line max)
2. No hardcoded secrets
3. Kotlin lint check
4. Build verification (`assembleDebug`)
5. Unit tests (when Kotlin files are staged)

Commit messages must use conventional format: `type: Description` or `type(scope): Description`
Valid prefixes: `feat`, `fix`, `refactor`, `docs`, `test`, `ci`, `build`, `chore`, `perf`, `style`

## AI-Assisted Development
- Request a plan/summary before implementation on non-trivial changes
- One logical change per prompt
- Reference specific files and existing patterns
- Verify imports/APIs exist before committing AI-generated code
- Run tests between steps
- Never use `--no-verify` to skip pre-commit hooks — fix the issue and recommit
- After completing a task: commit, push, and deploy (don't wait to be asked)
