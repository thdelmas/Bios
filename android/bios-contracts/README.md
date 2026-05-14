# bios-contracts

Shared inter-app contract for the Bios suite. Companions (W2F, Smokeless,
Virgil, future) consume this artifact instead of redeclaring `MetricType`
keys, ContentProvider URIs, intent action strings, or permission names. One
source of truth — rename here, every consumer recompiles against the change.

## What's in here

| File | Surface |
| --- | --- |
| `MetricType.kt` | Canonical metric keys + `MetricUnit`, `MetricDomain` |
| `BiosHealthContract.kt` | `AUTHORITY`, URI path constants, column-name arrays, `CompanionInsert` keys |
| `BiosPermissions.kt` | `READ_HEALTH`, `WRITE_COMPANION` permission names |
| `BiosIntentActions.kt` | Reserved inter-app intent actions (`ACTION_SUGGEST_BAND`, `ACTION_REQUEST_STOP`) |

The module is Kotlin-only with a stub Android manifest — it ships as an AAR
purely so companions get a familiar Gradle dependency. No Android framework
classes are imported; the artifact stays usable from plain JVM contexts
(contract tests, server tooling).

## Consuming from a companion

Until the artifact is published to a hosted Maven, depend on `mavenLocal()`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.bios:bios-contracts:0.1.0")
}
```

To produce/refresh the local artifact, from this repo:

```sh
cd android && ./gradlew :bios-contracts:publishToMavenLocal
```

## Example: writing a substance-use event (Smokeless)

```kotlin
import com.bios.contracts.BiosHealthContract
import com.bios.contracts.MetricType

val uri = Uri.parse(
    "content://${BiosHealthContract.AUTHORITY}/${BiosHealthContract.PATH_COMPANION}/" +
        MetricType.TOBACCO_USE.key
)
val values = ContentValues().apply {
    put(BiosHealthContract.CompanionInsert.VALUE, 1.0)
    put(BiosHealthContract.CompanionInsert.TIMESTAMP, System.currentTimeMillis())
}
contentResolver.insert(uri, values)
```

Manifest:

```xml
<uses-permission android:name="com.bios.app.permission.WRITE_COMPANION" />
```

## Example: reading recent readings (any companion)

```kotlin
val start = System.currentTimeMillis() - 24L * 3600 * 1000
val uri = Uri.parse(
    "content://${BiosHealthContract.AUTHORITY}/${BiosHealthContract.PATH_READINGS}/" +
        "${MetricType.HEART_RATE_VARIABILITY.key}?start=$start"
)
contentResolver.query(uri, BiosHealthContract.READING_COLUMNS, null, null, null)
```

## Versioning

- Add-only changes (new `MetricType` entry, new permission constant) bump the
  patch version.
- Rename or remove of any key bumps the major version and requires every
  companion to publish a paired release.
- Phase 7.6 will add a CI workflow that verifies companion consumers compile
  against `main`'s artifact before either side merges.
