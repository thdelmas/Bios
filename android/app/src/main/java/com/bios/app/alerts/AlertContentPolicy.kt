package com.bios.app.alerts

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
}
