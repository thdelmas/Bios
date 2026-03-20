# Bios - Project Conventions

## Project Overview
Bios is a privacy-first health monitoring app that detects early signs of illness using wearable sensor data. All health data processing happens on-device. Backend is opt-in only.

## Tech Stack
- **Android**: Kotlin + Jetpack Compose, Room with SQLCipher
- **iOS** (planned): Swift + SwiftUI
- **Backend** (future): Go + PostgreSQL
- **On-device ML**: TensorFlow Lite (Android), Core ML (iOS)

## Architecture
- See ARCHITECTURE.md for system diagrams
- See DATA_MODEL.md for unified metric schema
- See PRIVACY_ARCHITECTURE.md for privacy/encryption details

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
4. Build verification
5. Unit tests (when Kotlin files are staged)

## AI-Assisted Development
- One logical change per prompt
- Reference specific files and existing patterns
- Verify imports/APIs exist before committing AI-generated code
- Run tests between steps
- Commit after each logical step
