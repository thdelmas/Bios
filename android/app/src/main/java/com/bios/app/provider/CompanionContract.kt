package com.bios.app.provider

/**
 * Companion-write contract: which packages may insert which metric keys via
 * [BiosHealthProvider]'s `/companion/{metricType}` URI, and how their writes
 * are attributed in the `data_sources` table.
 *
 * Lives in its own file (free of Android framework dependencies) so contract tests
 * can verify the surface without initializing [BiosHealthProvider], which touches
 * UriMatcher and Uri — both stubbed-out in plain JVM unit tests.
 *
 * Per-package isolation: a package can write only the keys allocated to it. This
 * prevents an approved Smokeless install from spoofing W2F's mood signal (or vice
 * versa). The owner allowlist (CompanionGate) is the coarse gate; this map is the
 * fine semantic gate.
 *
 * Per-package source attribution: each companion gets its own `sourceId` so the
 * owner can trace exactly which app wrote any given reading. Without this,
 * everything routes through one source and provenance is lost.
 *
 * Membership rules:
 * - W2F (com.w2f.app) writes MENTAL_HEALTH signals
 * - Smokeless (com.smokless.smokeless) writes INTAKE events
 * - Virgil (com.virgil.app) writes SAFETY events
 * - CANNABIS_USE / CANNABIS_CRAVING are reserved in docs/ROADMAP §7.7 but stay
 *   off the whitelist until Smokeless actually emits them (YAGNI — see
 *   docs/ECOSYSTEM_BOUNDARIES.md).
 */
internal object CompanionContract {

    data class Companion(
        val packageName: String,
        val displayName: String,
        val sourceId: String,
        val writableMetrics: Set<String>,
    )

    val PACKAGES: Map<String, Companion> = listOf(
        Companion(
            packageName = "com.w2f.app",
            displayName = "W2F",
            sourceId = "companion_w2f",
            writableMetrics = setOf(
                "typing_cadence",
                "circadian_phase_shift",
                "mood_drift_score",
            ),
        ),
        Companion(
            packageName = "com.smokless.smokeless",
            displayName = "Smokeless",
            sourceId = "companion_smokeless",
            writableMetrics = setOf(
                "tobacco_use",
                "tobacco_craving",
            ),
        ),
        Companion(
            packageName = "com.virgil.app",
            displayName = "Virgil",
            sourceId = "companion_virgil",
            writableMetrics = setOf(
                "fall_event",
                "near_miss_fall",
                "check_in_miss",
            ),
        ),
    ).associateBy { it.packageName }

    /** Union of every key any known companion may write. */
    val WRITABLE_METRICS: Set<String> = PACKAGES.values.flatMap { it.writableMetrics }.toSet()

    /** Per-package writable-key map (kept for callers reasoning about isolation). */
    val WHITELIST_BY_PACKAGE: Map<String, Set<String>> =
        PACKAGES.mapValues { (_, c) -> c.writableMetrics }

    /** True iff [pkg] is a known companion and may write [metric]. */
    fun canWrite(pkg: String?, metric: String): Boolean =
        pkg != null && (PACKAGES[pkg]?.writableMetrics?.contains(metric) == true)

    /** Source metadata for a known companion package, or `null` if unknown. */
    fun sourceFor(pkg: String?): Companion? = pkg?.let { PACKAGES[it] }
}
