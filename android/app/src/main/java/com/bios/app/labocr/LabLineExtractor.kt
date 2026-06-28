package com.bios.app.labocr

import com.bios.app.export.CanonicalUnit
import com.bios.app.export.isKnownUnitToken
import com.bios.app.export.normalizeToCanonicalUnit

/**
 * Turns a single recognised report line into either an [ExtractedReading] or
 * a [SkippedLine] with a reason — never drops a line silently (docs
 * LAB_OCR_INGESTION.md §4.2).
 *
 * Lab lines share a stable shape across labs and languages:
 * ```
 * <analyte name> … <value> <unit> <ref-low> – <ref-high>
 * ```
 * The analyte is resolved first (longest leading-token alias), so digits that
 * are part of the *name* (`Vitamina B12`, `Omega 3 index`, `T4 lliure`) are
 * not mistaken for the value. The value is the first number after the name;
 * the unit gates acceptance; the printed reference range, if any, is used
 * only as a magnitude plausibility check and is never stored.
 */
object LabLineExtractor {

    /** Either an accepted reading or a reasoned skip — exhaustive, no third state. */
    sealed class LineOutcome {
        data class Reading(val reading: ExtractedReading) : LineOutcome()
        data class Skipped(val skipped: SkippedLine) : LineOutcome()
        /** No analyte and no number — structural line (section header, blank). Not surfaced. */
        object Ignored : LineOutcome()
    }

    /** Greatest number of leading whitespace tokens we'll try to fold into an analyte name. */
    private const val MAX_NAME_TOKENS = 6

    /** A number, optionally with a `<`/`>` qualifier or trailing flag char. */
    private val NUMBER = Regex("[0-9]+(?:[.,][0-9]+)*")

    fun extract(rawLine: String): LineOutcome {
        val line = rawLine.trim()
        if (line.isEmpty()) return LineOutcome.Ignored
        val tokens = line.split(Regex("\\s+"))

        val resolved = resolveLeadingAnalyte(tokens)
        if (resolved == null) {
            // No analyte. If the line has no numbers either it's structural;
            // otherwise it's a real value line we simply can't map — report it.
            return if (NUMBER.containsMatchIn(line)) {
                LineOutcome.Skipped(SkippedLine(line, "No analyte name matched"))
            } else {
                LineOutcome.Ignored
            }
        }

        val (match, remainder) = resolved
        val metric = match.metric

        val valueIdx = remainder.indexOfFirst { tokenNumber(it) != null }
        if (valueIdx < 0) {
            return LineOutcome.Skipped(SkippedLine(line, "${metric.readableName}: no numeric value on line"))
        }
        val value = tokenNumber(remainder[valueIdx])!!
        if (value <= 0.0) {
            return LineOutcome.Skipped(SkippedLine(line, "${metric.readableName}: non-positive value"))
        }

        val unit = remainder.drop(valueIdx + 1).firstOrNull { isKnownUnitToken(it) }
        val range = referenceRange(remainder.drop(valueIdx + 1))

        // Unit guard — the disambiguation gate. Reuses the shared FHIR/OCR
        // conversion table; an incompatible unit skips-and-reports, never guesses.
        val canonical = normalizeToCanonicalUnit(metric, unit, value)
        if (canonical is CanonicalUnit.Incompatible) {
            val target = metric.unit.symbol.ifEmpty { canonical.target }
            return LineOutcome.Skipped(
                SkippedLine(line, "${metric.readableName}: unit ${canonical.incoming} not compatible with $target")
            )
        }
        val storedValue = (canonical as CanonicalUnit.Ok).value

        val confidence = scoreConfidence(match.exact, unit, value, range)
        return LineOutcome.Reading(ExtractedReading(metric, storedValue, confidence, rawLine.trim()))
    }

    /**
     * Folds the longest leading run of tokens that resolves to a biomarker,
     * preferring an exact alias hit over a fuzzy one and a longer name over a
     * shorter (so `Colesterol HDL` beats bare `Colesterol`). Returns the match
     * plus the remaining tokens (where the value lives), or null.
     */
    private fun resolveLeadingAnalyte(
        tokens: List<String>,
    ): Pair<LabAnalyteAliases.AnalyteMatch, List<String>>? {
        val maxLen = minOf(MAX_NAME_TOKENS, tokens.size - 1).coerceAtLeast(1)
        // Exact first, longest prefix wins.
        for (len in maxLen downTo 1) {
            val prefix = tokens.take(len).joinToString(" ")
            val m = LabAnalyteAliases.resolve(prefix)
            if (m != null && m.exact) return m to tokens.drop(len)
        }
        // Then fuzzy, longest prefix wins.
        for (len in maxLen downTo 1) {
            val prefix = tokens.take(len).joinToString(" ")
            val m = LabAnalyteAliases.resolve(prefix)
            if (m != null) return m to tokens.drop(len)
        }
        return null
    }

    /**
     * HIGH only when the name was an exact alias hit, a real unit was present,
     * and the value sits within ~2 orders of magnitude of the printed
     * reference range. Anything softer is LOW — shown unchecked for the owner
     * to verify, never silently accepted (docs §4.4).
     */
    private fun scoreConfidence(
        exactName: Boolean,
        unit: String?,
        value: Double,
        range: Pair<Double, Double>?,
    ): Confidence {
        if (!exactName) return Confidence.LOW
        if (unit == null) return Confidence.LOW
        if (range != null) {
            val (low, high) = range
            // The magnitude trap: a value three orders outside the printed
            // range is a dropped decimal point, not a real result.
            if (high > 0 && value > high * 100) return Confidence.LOW
            if (low > 0 && value < low / 100) return Confidence.LOW
        }
        return Confidence.HIGH
    }

    /** First two numbers separated by a dash are the printed low–high reference range. */
    private fun referenceRange(afterValue: List<String>): Pair<Double, Double>? {
        val joined = afterValue.joinToString(" ")
        if (!joined.contains(Regex("[-–—]"))) return null
        val nums = NUMBER.findAll(joined).mapNotNull { parseNumber(it.value) }.toList()
        return if (nums.size >= 2) nums[0] to nums[1] else null
    }

    /** Parse the numeric part of a token ("<5", "5,2*", "190") to a Double, or null. */
    private fun tokenNumber(token: String): Double? {
        val m = NUMBER.find(token) ?: return null
        return parseNumber(m.value)
    }

    /**
     * Locale-aware decimal parse. Honours both `.` and `,` as decimal marks
     * and strips thousands separators — gluing `6.5` + `%` is the classic
     * strip-non-digits bug, so each printed number is parsed on its own.
     */
    private fun parseNumber(raw: String): Double? {
        val hasDot = raw.contains('.')
        val hasComma = raw.contains(',')
        val normalized = when {
            hasDot && hasComma ->
                if (raw.lastIndexOf(',') > raw.lastIndexOf('.')) {
                    raw.replace(".", "").replace(",", ".")
                } else {
                    raw.replace(",", "")
                }
            hasComma -> raw.replace(",", ".")
            else -> raw
        }
        return normalized.toDoubleOrNull()
    }
}
