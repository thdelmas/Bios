pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (lab-report OCR) is published on JitPack only.
        // The repository is pinned to that single group so nothing else in
        // the build can ever resolve from it.
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io") } }
            filter { includeGroup("cz.adaptech.tesseract4android") }
        }
    }
}

rootProject.name = "Bios"
include(":app", ":bios-contracts")
