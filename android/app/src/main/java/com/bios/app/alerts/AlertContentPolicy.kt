package com.bios.app.alerts

import com.bios.app.engine.AnomalyDetector
import com.bios.app.engine.HolisticSignals
import com.bios.app.model.AlertTier

/**
 * Banlist for the text Bios raises on its own (alert titles, alert lines,
 * tier descriptions, notification channels, the disclaimer). Those texts
 * state measurements; they never judge the owner, grade them, or turn the
 * numbers into a game.
 *
 * Prohibited:
 * - Second-person lifestyle judgments ("you should exercise")
 * - Scores or grade language about the person ("your health score is")
 * - Guilt mechanics ("you haven't", "you missed")
 * - Gamification ("streak", "badge")
 * - Prognostic or risk statements about the person
 *
 * Violations fail in unit tests. Bios-state notices (a source stopped
 * syncing, see [DisconnectDetector] / [DisconnectNotifier]) are about the
 * app's own plumbing and follow the same factual register.
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
        "ranking",
        // Prognostic statements about the person. Bios never classifies
        // the owner; these phrases would.
        "your memory is declining",
        "cognition declining",
        "your cognition is declining",
        "dementia risk",
        "you may have mci",
        "you may have dementia",
        "mci risk",
        "fall risk increased",
        "your fall risk",
        "elevated fall risk",
        "you are at risk of falling",
        "you are at risk of dementia",
        "capacity loss",
        "losing capacity",
        "cognitive decline detected",
        // Prognostic push-side language (#206, NEUROLOGY_POV §3.3). RBD has
        // a strong long-horizon conversion rate to synucleinopathies but
        // push-side communication of that horizon is explicitly forbidden.
        // The screen pattern is pull-side only; this banlist keeps any
        // future pattern from drifting into push-side prognostic framing.
        // "prodromal" and "neurodegenerative" intentionally NOT banned:
        // both are neutral clinical descriptors that legitimate existing
        // patterns use in earlyDetection / risks framing (e.g. NAFLD,
        // COPD, mania-prodrome citations).
        "parkinson's risk",
        "parkinsons risk",
        "synucleinopathy",
        "you may be developing",
        "you may develop",
        "you're at risk of",
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
        val prohibitedPhrase: String,
        val context: String,
    )

    /** Every English text Bios pushes on its own, keyed by where it appears. */
    fun pushTexts(): Map<String, String> = buildMap {
        put("alert.title", AnomalyDetector.TITLE)
        put("alert.fallback", HolisticSignals.FALLBACK)
        put("alert.legend", AlertDetailContent.STRIP_LEGEND)
        AlertTier.entries.forEach { put("tier.${it.name}", AlertDetailContent.tierMeaning(it)) }
    }

    /** Prohibited phrases found in [text]; empty when compliant. */
    fun validate(field: String, text: String): List<Violation> {
        val lower = text.lowercase()
        return PROHIBITED_PHRASES.filter { lower.contains(it) }.map { phrase ->
            val idx = lower.indexOf(phrase)
            val start = maxOf(0, idx - CONTEXT_CHARS)
            val end = minOf(text.length, idx + phrase.length + CONTEXT_CHARS)
            Violation(field, phrase, "...${text.substring(start, end)}...")
        }
    }

    /** Validates [pushTexts]. Empty when every text is compliant. */
    fun validateAll(): List<Violation> = pushTexts().flatMap { (field, text) -> validate(field, text) }

    private const val CONTEXT_CHARS = 20

    /**
     * Validate localized [text] against the English banlist plus the
     * locale-specific banlist for [languageCode] (ISO 639-1, lowercase).
     * Returns the prohibited phrase that matched, or null when compliant.
     *
     * Used by [validateLocaleOverlay] to enforce the policy across every
     * shipped locale overlay.
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
     * Validates one locale's strings ([texts]: resource name to text) against
     * the English banlist plus the banlist for [languageTag]'s language.
     * Empty when the overlay is compliant.
     */
    fun validateLocaleOverlay(languageTag: String, texts: Map<String, String>): List<LocaleViolation> {
        val language = java.util.Locale.forLanguageTag(languageTag).language
        return texts.mapNotNull { (name, text) ->
            phraseViolatingLocale(text, language)?.let { LocaleViolation(languageTag, name, it) }
        }
    }

    data class LocaleViolation(
        val languageTag: String,
        val resourceName: String,
        val prohibitedPhrase: String,
    )
}
