plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

android {
    namespace = "com.bios.contracts"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.bios"
                artifactId = "bios-contracts"
                version = "0.1.0"

                pom {
                    name.set("Bios Contracts")
                    description.set(
                        "Shared inter-app contract for Bios companions: MetricType keys, " +
                                "ContentProvider URIs, intent actions, permission names. " +
                                "Pure constants — no Android runtime dependencies beyond the " +
                                "framework permission strings themselves."
                    )
                    url.set("https://github.com/thdelmas/Bios")
                    licenses {
                        license {
                            name.set("MIT")
                        }
                    }
                }
            }
        }
    }
}
