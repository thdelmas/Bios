package com.bios.app.provider

/**
 * Companion-write contract: which packages may insert which metric keys via
 * [BiosHealthProvider]'s `/companion/{metricType}` URI.
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
 * Membership rules:
 * - W2F (com.w2f.app) writes MENTAL_HEALTH signals
 * - Smokeless (com.smokless.smokeless) writes INTAKE events
 * - CANNABIS_USE / CANNABIS_CRAVING are reserved in docs/ROADMAP §7.7 but stay
 *   off the whitelist until Smokeless actually emits them (YAGNI — see
 *   docs/ECOSYSTEM_BOUNDARIES.md).
 */
internal object CompanionContract {

    val WHITELIST_BY_PACKAGE: Map<String, Set<String>> = mapOf(
        "com.w2f.app" to setOf(
            "typing_cadence",
            "circadian_phase_shift",
            "mood_drift_score",
        ),
        "com.smokless.smokeless" to setOf(
            "tobacco_use",
            "tobacco_craving",
        ),
    )

    /** Union of every key any known companion may write. */
    val WRITABLE_METRICS: Set<String> = WHITELIST_BY_PACKAGE.values.flatten().toSet()

    /** True iff [pkg] is a known companion and may write [metric]. */
    fun canWrite(pkg: String?, metric: String): Boolean =
        pkg != null && (WHITELIST_BY_PACKAGE[pkg]?.contains(metric) == true)
}
