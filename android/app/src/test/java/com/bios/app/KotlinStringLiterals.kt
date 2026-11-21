package com.bios.app

/**
 * Pulls the string literals out of Kotlin source for the vocabulary gate.
 * Comments and character literals are skipped, a template expression is
 * replaced by " … ", and literals joined with `+` come back as one text so
 * a phrase split across two lines is still seen whole.
 */
object KotlinStringLiterals {

    data class Literal(val line: Int, val text: String)

    fun of(source: String): List<Literal> = Scanner(source).run()

    private class Scanner(private val src: String) {
        private var i = 0
        private var line = 1
        private val out = mutableListOf<Literal>()
        private var pending: Literal? = null
        private var joining = false

        fun run(): List<Literal> {
            while (i < src.length) step()
            flush()
            return out
        }

        private fun step() {
            val c = src[i]
            when {
                src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') i++
                src.startsWith("/*", i) -> skipTo(src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 })
                c == '\'' -> skipTo(charLiteralEnd())
                c == '"' -> literal()
                else -> {
                    if (c == '\n') line++
                    if (c == '+') joining = pending != null else if (!c.isWhitespace()) flush()
                    i++
                }
            }
        }

        private fun literal() {
            val startLine = line
            val start = i
            val text = if (src.startsWith("\"\"\"", i)) {
                i += RAW_QUOTE
                read(raw = true)
            } else {
                i++
                read(raw = false)
            }
            line += src.substring(start, i).count { it == '\n' }
            val before = pending
            if (before != null && joining) {
                pending = Literal(before.line, before.text + text)
            } else {
                flush()
                pending = Literal(startLine, text)
            }
            joining = false
        }

        /** Reads from just after the opening quote to just after the closing one. */
        private fun read(raw: Boolean): String {
            val buf = StringBuilder()
            while (i < src.length) {
                val c = src[i]
                when {
                    raw && src.startsWith("\"\"\"", i) -> {
                        i += RAW_QUOTE
                        while (i < src.length && src[i] == '"') i++
                        return buf.toString()
                    }
                    !raw && c == '"' -> {
                        i++
                        return buf.toString()
                    }
                    !raw && c == '\\' -> {
                        buf.append(if (src[i + 1] == 'n' || src[i + 1] == 't') ' ' else src[i + 1])
                        i += 2
                    }
                    c == '$' && skipTemplate() -> buf.append(TEMPLATE)
                    else -> {
                        buf.append(c)
                        i++
                    }
                }
            }
            return buf.toString()
        }

        /** At a `$`: skips `${ … }` or `$name` and returns true; false for a plain dollar sign. */
        private fun skipTemplate(): Boolean {
            val next = src.getOrNull(i + 1) ?: return false
            if (next == '{') {
                i += 2
                skipExpression()
                return true
            }
            if (!next.isLetter() && next != '_') return false
            i++
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
            return true
        }

        /** Skips a `${ … }` expression, strings nested in it included. */
        private fun skipExpression() {
            var depth = 1
            while (i < src.length && depth > 0) {
                when {
                    src[i] == '{' -> { depth++; i++ }
                    src[i] == '}' -> { depth--; i++ }
                    src.startsWith("\"\"\"", i) -> { i += RAW_QUOTE; read(raw = true) }
                    src[i] == '"' -> { i++; read(raw = false) }
                    else -> i++
                }
            }
        }

        private fun charLiteralEnd(): Int {
            var j = i + 1
            j += if (src.getOrNull(j) == '\\') 2 else 1
            while (j < src.length && src[j] != '\'' && j - i < MAX_CHAR_LITERAL) j++
            return j + 1
        }

        private fun skipTo(end: Int) {
            val stop = end.coerceAtMost(src.length)
            line += src.substring(i, stop).count { it == '\n' }
            i = stop
        }

        private fun flush() {
            pending?.let { out += it }
            pending = null
            joining = false
        }
    }

    private const val RAW_QUOTE = 3
    private const val MAX_CHAR_LITERAL = 8
    private const val TEMPLATE = " … "
}
