plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.bios.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bios.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "platform"
    productFlavors {
        create("lethe") {
            dimension = "platform"
            applicationIdSuffix = ".lethe"
            versionNameSuffix = "-lethe"
        }
        create("standalone") {
            dimension = "platform"
            // Default application ID and version name
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room + SQLCipher
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.14.0")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")

    // WorkManager (background sync)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // HTTP client (Oura, WHOOP, Garmin API adapters)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Encrypted preferences (API token storage)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // LiteRT (on-device ML) – rebranded TF Lite with 16KB page alignment
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("com.google.ai.edge.litert:litert-support-api:1.4.2")

    // Ed25519 signature verification (model updates) — needed for API < 33
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")

    // Iroh P2P sync (Willow protocol for delta sync between devices)
    // TODO: Add iroh-ffi-android when published to Maven Central
    // Track: https://github.com/n0-computer/iroh-ffi

    // Kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20231013")
}
