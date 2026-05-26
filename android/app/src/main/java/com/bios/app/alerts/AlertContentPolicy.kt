package com.bios.app.alerts

import android.content.Context

/**
 * Enforces the **push-side unsolicited-judgment ban** on alert text.
 *
 * This is the code-level enforcement point for Manifesto Principle 7
 * ("instrument, not coach"). Per the framing in [MANIFESTO.md] and
 * docs/PRIVACY_ARCHITECTURE.md "Alert Content Policy," Bios distinguishes:
 *
 *  - **Push side** — alerts and notifications Bios raises on its own,
 *    unsolicited. The push side splits into three sub-categories:
 *      1. **Person-evaluative push** — "your sleep is degrading,"
 *         "you're unhealthy." This is what the policy here constrains.
 *         Unsolicited judgment of the person is what the manifesto
 *         prohibits, and `ConditionPattern` text is exactly that surface.
 *      2. **Person-factual push** — "RHR +2σ for 48h," daily digest.
 *         Admissible — what the existing banlist explicitly allows.
 *      3. **Bios-state push** — "Oura hasn't synced in 5 days. Reconnect?"
 *         Admissible. Not about the owner at all; Bios reporting on its
 *         own plumbing. "Silence is a feature" was always about the
 *         owner — silent system failure isn't silence, it's failure.
 *         Bios-state pushes follow the same factual-only content rules
 *         as category 2, but the banlist below targets person-judgment
 *         patterns specifically and doesn't apply by construction.
 *         Shipped surface: [DisconnectDetector] / [DisconnectNotifier].
 *  - **Pull side** — screens the owner navigates into, biomarker context
 *    they annotate themselves, comparisons they explicitly ask for. The
 *    owner is doing the evaluation; Bios is the instrument they read.
 *    These surfaces are **not** constrained by this policy and may use
 *    language this policy bans.
 *
 * All condition pattern text (title, explanation, suggestedAction,
 * earlyDetection, prevention, healing, risks) must pass this policy.
 * Violations fail in CI tests. New push-side surfaces should pass
 * through `validate()` or its sibling check in tests.
 *
 * Prohibited on the push side:
 * - Second-person lifestyle judgments ("you should exercise", "you need to sleep more")
 * - Wellness scores or grade language ("your health score is", "grade: B")
 * - Guilt mechanics ("you haven't", "you missed", "you failed")
 * - Gamification ("streak", "achievement", "level up", "points")
 *
 * Allowed on the push side:
 * - Data statements ("resting HR +2σ", "sleep duration 5.2h")
 * - Questions ("have you felt unwell?")
 * - Professional referrals ("discuss with your healthcare provider")
 * - Factual risk information ("research shows elevated HR correlates with...")
 */
object AlertContentPolicy {

    private val PROHIBITED_PHRASES = listOf(
        // Lifestyle judgments
        "you should",
        "you need to",
        "you must",
        "try to",
        "consider exercising",
        "you ought to",
        "you have to",
        // Evaluative language
        "your health score",
        "grade:",
        "you're unhealthy",
        "you're doing great",
        "good job",
        "well done",
        "keep it up",
        // Guilt mechanics
        "you haven't",
        "you missed",
        "you failed",
        "you forgot",
        "you didn't",
        // Gamification
        "streak",
        "achievement",
        "level up",
        "points earned",
        "daily goal",
        "badge",
        "leaderboard",
        "ranking"
    )

    /**
     * Per-locale prohibited phrases. The English banlist above is always
     * applied; these supplemental lists catch register-equivalent push-side
     * judgments in non-English overlays (issue #210, requirement 6).
     *
     * Keyed by ISO 639-1 language code (lowercase), matched against the
     * resolved string's source locale at lookup time. Adding a locale to
     * the overlay set means adding its register-equivalent phrases here.
     *
     * Only phrases that occur in our own English banlist's register family
     * are included — direct judgments ("you should X") and gamification.
     * Polite suggestions phrased as conditional questions are intentionally
     * not banned in any language.
     */
    private val LOCALIZED_PROHIBITED_PHRASES: Map<String, List<String>> = mapOf(
        "es" to listOf(
            "deberías",          // "you should" (informal)
            "debería usted",     // "you should" (formal)
            "tú debes",          // "you must"
            "tienes que",        // "you have to"
            "has fallado",       // "you failed"
            "te perdiste",       // "you missed"
            "racha",             // "streak"
            "logro desbloqueado",
            "insignia",          // "badge"
        ),
        "pt" to listOf(
            "você deve",         // "you should/must"
            "você precisa",      // "you need to"
            "você tem que",      // "you have to"
            "você falhou",       // "you failed"
            "sequência",         // "streak"
            "conquista desbloqueada",
            "medalha",           // "badge"
        ),
        // Te Reo Māori: the translated overlay in this PR uses descriptive,
        // non-imperative forms (no "me…" / "kia…" command framings). Banlist
        // is intentionally empty until the translation set grows; revisit
        // with a fluent reviewer before adding entries — the imperative gate
        // forms are short enough that naive banning would catch false
        // positives in descriptive text.
        "mi" to emptyList(),
        // ʻŌlelo Hawaiʻi: same disposition as mi — the small translation
        // surface in this PR is descriptive only. Add specific banned forms
        // here when the translation set grows.
        "haw" to emptyList(),
    )

    data class Violation(
        val field: String,
        val pattern: ConditionPattern,
        val prohibitedPhrase: String,
        val context: String
    )

    /**
     * Validate a single condition pattern. Returns empty list if compliant.
     */
    fun validate(pattern: ConditionPattern): List<Violation> {
        val violations = mutableListOf<Violation>()

        val fieldsToCheck = mapOf(
            "title" to pattern.title,
            "explanation" to pattern.explanation,
            "suggestedAction" to (pattern.suggestedAction ?: ""),
            "earlyDetection" to pattern.earlyDetection,
            "prevention" to pattern.prevention,
            "healing" to pattern.healing,
            "risks" to pattern.risks
        )

        for ((fieldName, text) in fieldsToCheck) {
            val lower = text.lowercase()
            for (phrase in PROHIBITED_PHRASES) {
                if (lower.contains(phrase)) {
                    // Extract context around the violation
                    val idx = lower.indexOf(phrase)
                    val start = maxOf(0, idx - 20)
                    val end = minOf(text.length, idx + phrase.length + 20)
                    val context = "...${text.substring(start, end)}..."

                    violations += Violation(
                        field = fieldName,
                        pattern = pattern,
                        prohibitedPhrase = phrase,
                        context = context
                    )
                }
            }
        }

        return violations
    }

    /**
     * Validate all registered condition patterns. Returns empty list if all compliant.
     */
    fun validateAll(): List<Violation> {
        return ConditionPatterns.all.flatMap { validate(it) }
    }

    /**
     * Validate localized [text] against the English banlist plus the
     * locale-specific banlist for [languageCode] (ISO 639-1, lowercase).
     * Returns the prohibited phrase that matched, or null when compliant.
     *
     * Used by [validateLocaleOverlays] to enforce the policy across every
     * shipped locale overlay (issue #210, requirement 6 — CI gate).
     */
    fun phraseViolatingLocale(text: String, languageCode: String): String? {
        val lower = text.lowercase()
        for (phrase in PROHIBITED_PHRASES) {
            if (lower.contains(phrase)) return phrase
        }
        val localized = LOCALIZED_PROHIBITED_PHRASES[languageCode.lowercase()]
            ?: return null
        for (phrase in localized) {
            if (lower.contains(phrase)) return phrase
        }
        return null
    }

    /**
     * Validate every locale overlay's pattern strings against the banlist.
     * Walks [ConditionPatterns.all], resolves both explanation and
     * suggestedAction through [AlertTextResolver] using a [Context] whose
     * locale has been swapped to each [languageCodes] entry, and reports
     * any localized strings containing a prohibited phrase. Empty list ⇒
     * all overlays compliant.
     *
     * Drives the CI gate test [AlertContentPolicyTest] guards: a
     * translator who inadvertently introduces "tú debes" or "you should"
     * into an overlay fails the build at unit-test time.
     */
    fun validateLocaleOverlays(
        baseContext: Context,
        languageCodes: List<String>,
    ): List<LocaleViolation> {
        val violations = mutableListOf<LocaleViolation>()
        for (language in languageCodes) {
            val locale = java.util.Locale.forLanguageTag(language)
            val config = android.content.res.Configuration(
                baseContext.resources.configuration
            )
            config.setLocale(locale)
            val ctx = baseContext.createConfigurationContext(config)
            for (pattern in ConditionPatterns.all) {
                val explanation = AlertTextResolver.explanationFor(ctx, pattern)
                val suggested = AlertTextResolver.suggestedActionFor(ctx, pattern)
                phraseViolatingLocale(explanation, locale.language)?.let { phrase ->
                    violations += LocaleViolation(
                        languageTag = language,
                        patternId = pattern.id,
                        field = "explanation",
                        prohibitedPhrase = phrase,
                    )
                }
                if (suggested != null) {
                    phraseViolatingLocale(suggested, locale.language)?.let { phrase ->
                        violations += LocaleViolation(
                            languageTag = language,
                            patternId = pattern.id,
                            field = "suggestedAction",
                            prohibitedPhrase = phrase,
                        )
                    }
                }
            }
        }
        return violations
    }

    data class LocaleViolation(
        val languageTag: String,
        val patternId: String,
        val field: String,
        val prohibitedPhrase: String,
    )
}
