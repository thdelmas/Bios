import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
}

// Detekt static analysis, applied uniformly to every module.
// Lightweight (no type-resolution): fast, no compilation needed — a guardrail
// against complexity creep, duplication, and size drift, not a deep checker.
// Each module carries its own detekt-baseline.xml so the existing tree never
// blocks the build; only NEW violations fail. Regenerate a baseline after an
// intentional large change with `./gradlew detektBaseline`.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
}
