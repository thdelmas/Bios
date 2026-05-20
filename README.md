# Bios

Health guardian that detects early signs of illness using wearable sensor data. All processing on-device — the owner decides what leaves it, and nothing does by default. Instrument, not coach. Evaluation belongs to the owner.

Hub of the Bios ecosystem (Bios, Fil, W2F, Smokeless, Virgil, SoulRadio) — exposes `BiosHealthProvider` for companion apps to write events and read metrics.

For full context: [CLAUDE.md](CLAUDE.md), [MANIFESTO.md](MANIFESTO.md), [docs/](docs/README.md).

## Install

### Option 1: Direct download

Grab the latest signed APK from [Releases](https://github.com/thdelmas/Bios/releases/latest). Public releases ship the **standalone** flavor — portable APK for any Android 9+ device. The `lethe` flavor is for OS-embedded use on LETHE and is not distributed here.

Verify SHA-256 against the published `.sha256` file, then sideload. "Install unknown apps" must be enabled.

### Option 2: Obtainium (recommended for updates)

[Obtainium](https://github.com/ImranR98/Obtainium) tracks GitHub Releases and auto-updates without any store. Add this repo URL:

```
https://github.com/thdelmas/Bios
```

### Signing identity

All apps in the Bios ecosystem share one signing key — that's what lets sister apps grant each other `signature`-level permissions for the `BiosHealthProvider` ContentProvider. Cert SHA-256:

```
D4:18:F5:1B:E9:D0:28:5D:0B:A8:27:4B:0E:E9:67:8F:F9:DB:DC:1D:32:D5:97:3C:ED:F3:23:59:3F:55:46:33
```

Compare against `apksigner verify --print-certs Bios-standalone-vX.Y.Z.apk` before trusting an install.

## Release flow

Push a tag `vX.Y.Z` from `main` → GitHub Actions builds and signs the standalone APK, publishes a Release with auto-generated notes. See [.github/workflows/release.yml](.github/workflows/release.yml).

The `lethe` flavor is built locally as part of LETHE OS integration — not part of public releases.

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — system design, platform abstraction, build flavors
- [docs/CONSUMER_API.md](docs/CONSUMER_API.md) — `BiosHealthProvider` contract for companions
- [docs/PRIVACY_ARCHITECTURE.md](docs/PRIVACY_ARCHITECTURE.md) — on-device processing, encryption, threat model
- [docs/ROADMAP.md](docs/ROADMAP.md) — owner-protection roadmap
- Full index: [docs/README.md](docs/README.md)
