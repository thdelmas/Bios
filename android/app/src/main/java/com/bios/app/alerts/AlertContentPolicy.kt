package com.bios.app.alerts

import com.bios.app.engine.AnomalyDetector
import com.bios.app.engine.HolisticSignals
import com.bios.app.model.AlertTier

/**
 * What the text Bios writes may not say (docs/specs/instrument-only.md).
 *
 * Two lists:
 * - [PROHIBITED_PHRASES]: judgment of the owner. Second-person lifestyle
 *   advice, grades, guilt, gamification, prognosis.
 * - [MEDICAL_VOCABULARY]: names of conditions and words of medical
 *   purpose. Bios measures and shows; it names no condition as something
 *   it detects, and attaches no meaning, advice or urgency to a number.
 *
 * Both are enforced in unit tests over every string resource and every
 * string literal of the user-facing packages. The legitimate uses of a
 * listed word (an owner naming their own record, a source quoted with
 * attribution, the disclaimer) are an explicit allowlist next to the test.
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
        "parkinson's risk",
        "parkinsons risk",
        "synucleinopathy",
        "you may be developing",
        "you may develop",
        "you're at risk of",
    )

    /**
     * Condition names and words of medical purpose, lowercase. Matched at
     * the start of a word, so "stroke" does not match "keystroke" and
     * "diagnos" matches "diagnosis" and "diagnostic".
     */
    val MEDICAL_VOCABULARY: List<String> = listOf(
        // Conditions the removed patterns and screens named.
        "sepsis", "septic", "infection", "dengue", "malaria", "typhoid", "ketoacidosis", "apnea", "apnoea",
        "fibrillation", "afib", "arrhythmia", "seizure", "convulsion", "epilep", "stroke", "hypertension",
        "hypotension", "hypoglyc", "hyperglyc", "anaphyla", "heart failure", "heart attack", "bipolar", "mania",
        "manic", "depression", "pre-eclampsia", "preeclampsia", "frailty", "tremor", "parkinson", "dementia",
        "diabet", "cancer", "pneumonia", "asthma", "copd", "anemia", "anaemia", "migraine", "overtraining",
        "exacerbation", "decompensation", "tachycard", "bradycard", "hypoxi", "fever", "palpitation",
        "inflammation",
        // Medical purpose: detecting, judging, advising.
        "diagnos", "screening", "screen for", "early detection", "early warning", "early sign", "illness",
        "disease", "disorder", "syndrome", "seek care", "seek medical", "medical advice", "emergency", "symptom",
        "clinical", "clinician", "physician", "at risk", "risk of", "risk factor", "consult", "your doctor",
        "a doctor", "the doctor", "healthcare provider", "health care provider", "treatment", "prognos",
        "abnormal",
        // Meaning attached to a number.
        "may indicate", "can indicate", "could indicate", "associated with", "linked to", "why this matters",
        "what this means", "anomal",
    )

    /** The same register in the languages of the shipped overlays that have reviewed lists. */
    private val LOCALIZED_MEDICAL_VOCABULARY: Map<String, List<String>> = mapOf(
        "es" to listOf(
            "diagnóst", "enfermedad", "síntoma", "emergencia", "urgencias", "consulte", "su médico",
            "tratamiento", "riesgo de", "infección", "trastorno",
        ),
        "pt" to listOf(
            "diagnóst", "doença", "sintoma", "emergência", "consulte", "seu médico", "tratamento",
            "risco de", "infecção", "transtorno",
        ),
    )

    private fun wordStart(term: String) =
        Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(term).replace(" ", "\\E\\s+\\Q"), RegexOption.IGNORE_CASE)

    private val medicalPatterns: Map<String, List<Pair<String, Regex>>> by lazy {
        val english = MEDICAL_VOCABULARY.map { it to wordStart(it) }
        LOCALIZED_MEDICAL_VOCABULARY.mapValues { (_, terms) -> english + terms.map { it to wordStart(it) } } +
            ("en" to english)
    }

    /**
     * Listed medical words found in [text], for [languageCode] (ISO 639-1).
     * The English list always applies; empty when the text is clean.
     */
    fun medicalVocabularyIn(text: String, languageCode: String = "en"): List<String> {
        val patterns = medicalPatterns[languageCode.lowercase()] ?: medicalPatterns.getValue("en")
        return patterns.filter { (_, pattern) -> pattern.containsMatchIn(text) }.map { it.first }
    }

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

    /** Every English text of an alert, keyed by where it appears. */
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
