package com.bios.app.alerts

/**
 * Enforces the "never evaluate the person" principle on alert text.
 *
 * All condition pattern text (title, explanation, suggestedAction, earlyDetection,
 * prevention, healing, risks) must pass this policy. Violations fail in CI tests.
 *
 * Prohibited patterns:
 * - Second-person lifestyle judgments ("you should exercise", "you need to sleep more")
 * - Wellness scores or grade language ("your health score is", "grade: B")
 * - Guilt mechanics ("you haven't", "you missed", "you failed")
 * - Gamification ("streak", "achievement", "level up", "points")
 *
 * Allowed:
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
