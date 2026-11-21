# Bios

Your body's measurements, on your device. Bios collects readings from wearables and phone sensors, keeps them encrypted on the phone, and shows them next to your own usual range. It measures and shows; it does not interpret. All processing on-device — the owner decides what leaves it, and nothing does by default. Evaluation belongs to the owner.

Bios is not a medical device. It does not diagnose, screen for, or predict any condition.

> **Builds are paused (21 November 2026).** Bios is being reduced to a measuring instrument. Earlier versions carried condition patterns, possible causes and care advice; those are removed in the next version, and the APKs of v0.1.0 to v0.3.2 have been withdrawn. That code is no longer in the source tree; the removal lands with the next version.

Hub of the Bios ecosystem (Bios, W2F, Smokeless, Virgil, SoulRadio) — exposes `BiosHealthProvider` for companion apps to write events and read metrics.

For full context: [CLAUDE.md](CLAUDE.md), [docs/](docs/README.md).

## Community

Questions, feedback, bug reports and ideas: join the **[Bios Discord](https://discord.gg/cEdE8yYHkh)**.
Please keep personal health data (values, screenshots, symptoms) out of public channels.
Bugs with a clear reproduction can also go straight to [GitHub Issues](https://github.com/thdelmas/Bios/issues/new).

## Install

No build is published at the moment (see the notice above). The steps below apply again from the next release.

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

Versions follow [Conventional Commits](https://www.conventionalcommits.org) (`feat` → minor, `fix` → patch, `!` → major; enforced by `.githooks/commit-msg`, enable with `./scripts/install-hooks.sh`). On each push to `main`, [release-please](.github/workflows/release-please.yml) keeps a release PR open with the next version, the `CHANGELOG.md` entry and the bump in `android/app/build.gradle.kts` (`versionCode` is derived from the version). Merging that PR tags `vX.Y.Z`, publishes the Release with the changelog as notes, and triggers [release.yml](.github/workflows/release.yml), which builds and signs the standalone APK and attaches it. A tag pushed by hand still builds, without notes.

The `lethe` flavor is built locally as part of LETHE OS integration — not part of public releases.

## Documentation

- [docs/specs/instrument-only.md](docs/specs/instrument-only.md): what Bios does and does not do
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): components, build flavors, data flow
- [docs/CONSUMER_API.md](docs/CONSUMER_API.md): `BiosHealthProvider` contract for companions
- [docs/PRIVACY_ARCHITECTURE.md](docs/PRIVACY_ARCHITECTURE.md): where data lives and every way it can leave the phone
- [docs/ROADMAP.md](docs/ROADMAP.md): current state and open product questions
- Full index: [docs/README.md](docs/README.md)

## License

Bios is free software under the [GNU Affero General Public License v3.0](LICENSE) (SPDX: `AGPL-3.0-only`). You can read, run, change and redistribute it; anyone who distributes a modified version, or runs one as a service for others, must publish their changes under the same terms. Copyright holder: Théophile Delmas Leguery. Commercial licensing for use inside closed products is available from the copyright holder.
