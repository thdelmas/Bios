package com.bios.app.provider

/**
 * Companion-write contract: the set of metric keys that companion apps are allowed
 * to insert via [BiosHealthProvider]'s `/companion/{metricType}` URI.
 *
 * Lives in its own file (free of Android framework dependencies) so contract tests
 * can verify the surface without initializing [BiosHealthProvider], which touches
 * UriMatcher and Uri — both stubbed-out in plain JVM unit tests.
 *
 * Membership rules:
 * - W2F injects MENTAL_HEALTH signals (typing_cadence, circadian_phase_shift, mood_drift_score)
 * - Smokeless injects INTAKE events (tobacco_use, tobacco_craving)
 * - CANNABIS_USE / CANNABIS_CRAVING are reserved in docs/ROADMAP §7.7 but stay
 *   off the whitelist until Smokeless actually emits them (YAGNI — see
 *   docs/ECOSYSTEM_BOUNDARIES.md).
 */
internal object CompanionContract {
    val WRITABLE_METRICS: Set<String> = setOf(
        "typing_cadence",
        "circadian_phase_shift",
        "mood_drift_score",
        "tobacco_use",
        "tobacco_craving",
    )
}
