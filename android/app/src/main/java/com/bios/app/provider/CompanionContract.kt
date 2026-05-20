package com.bios.app.provider

import com.bios.app.model.ReadingKind

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
 * Signing-cert pin: on the standalone flavor, package-name equality is not enough
 * — any sideloaded APK can declare `<application android:packageName="com.w2f.app">`.
 * [signingCertSha256] pins the trust to the SHA-256 of the companion's release
 * signing certificate. An empty set means "no pin published yet" (migration
 * window) and the package-name check stands alone; once the companion ships a
 * stable release-signing key, its SHA is added here and impersonators are
 * rejected at insert time. The check is bypassed on the LETHE flavor where the
 * platform itself is the trust anchor (companions are pre-installed system apps
 * signed by the platform key).
 *
 * Membership rules:
 * - W2F (com.w2f.app) writes MENTAL_HEALTH signals
 * - Smokeless (com.smokless.smokeless) writes INTAKE events (tobacco + cannabis,
 *   matching its Substance enum surface from Phase 2.1).
 * - Virgil (com.virgil.app) writes SAFETY events
 */
internal object CompanionContract {

    // `defaultReadingKind` is the dominant kind of data the companion writes.
    // Mixed-kind companions (e.g. W2F writing both DERIVED cadence and
    // SELF_REPORTED mood) must split into multiple sourceIds when that
    // ambiguity actually bites — tracked as an open question in
    // docs/SELF_REPORTED_DATA_HOME.md.
    //
    // `signingCertSha256`: hex (lowercase) SHA-256 of the companion's release
    // signing cert(s). Multiple values support a rotation window. Empty set =
    // not yet pinned — see the migration-window note on the surrounding object.
    data class Companion(
        val packageName: String,
        val displayName: String,
        val sourceId: String,
        val writableMetrics: Set<String>,
        val defaultReadingKind: ReadingKind,
        val signingCertSha256: Set<String> = emptySet(),
    ) {
        /**
         * Whether [actualCertSha256] (lowercase hex) matches this companion's pin.
         * Empty pin → true (migration window, falls back to package-name trust).
         * Non-empty pin → true iff the actual SHA is in the pinned set, case-
         * insensitively. A null actual SHA against a non-empty pin reads as
         * impersonation and returns false.
         */
        fun verifySigningCert(actualCertSha256: String?): Boolean {
            if (signingCertSha256.isEmpty()) return true
            val actual = actualCertSha256?.lowercase() ?: return false
            return signingCertSha256.any { it.lowercase() == actual }
        }
    }

    val PACKAGES: Map<String, Companion> = listOf(
        Companion(
            packageName = "com.w2f.app",
            displayName = "W2F",
            sourceId = "companion_w2f",
            writableMetrics = setOf(
                "typing_cadence",
                // Transition: W2F still writes circadian_phase_shift until
                // Bios ships its own cosinor/DLMO engine and W2F switches
                // to reading. See docs/ECOSYSTEM_BOUNDARIES.md
                // producer-by-capture-surface rule.
                "circadian_phase_shift",
                "mood_drift_score",
                // Producer-by-capture-surface: W2F's UsageStatsTracker derives
                // sleep from screen-off patterns — a surface no other adapter
                // has. Without this whitelist entry, LETHE owners with no
                // wearable get an empty sleep bus while W2F sits on the data.
                // Issue #133. Confidence stays LOW (proxy, not actigraphy).
                "sleep_duration",
            ),
            defaultReadingKind = ReadingKind.DERIVED,
            // TODO populate with W2F's release-signing cert SHA-256 before the
            // first standalone-flavor release. See docs/CONSUMER_API.md cert-
            // rotation procedure. Until then, the package-name check stands
            // alone.
            signingCertSha256 = emptySet(),
        ),
        Companion(
            packageName = "com.smokless.smokeless",
            displayName = "Smokeless",
            sourceId = "companion_smokeless",
            writableMetrics = setOf(
                "tobacco_use",
                "tobacco_craving",
                "cannabis_use",
                "cannabis_craving",
            ),
            defaultReadingKind = ReadingKind.SELF_REPORTED,
            signingCertSha256 = emptySet(),
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
            defaultReadingKind = ReadingKind.DERIVED,
            signingCertSha256 = emptySet(),
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

    /**
     * Decides whether to accept a write from [pkg] given the calling APK's
     * actual signing-cert SHA-256 ([actualCertSha256], lowercase hex).
     *
     * Unknown package → false. Known package → delegates to the per-companion
     * [Companion.verifySigningCert]. Callers running on the LETHE flavor should
     * skip this and trust the platform key instead.
     */
    fun verifySigningCert(pkg: String?, actualCertSha256: String?): Boolean {
        val companion = sourceFor(pkg) ?: return false
        return companion.verifySigningCert(actualCertSha256)
    }
}
