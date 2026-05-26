package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * CI gate for the alert-content policy across **localization overlays**
 * (issue #210, requirement 6).
 *
 * Unit tests run without an Android Resources framework — we can't
 * exercise `Resources.getString()` directly. Instead, this test parses
 * the shipped `values-<locale>/pattern_strings.xml` files (the source of
 * truth Android compiles into the APK) and runs each translated string
 * through [AlertContentPolicy.phraseViolatingLocale]. A locale-specific
 * banned phrase ("deberías…", "você deve…") in any overlay fails the
 * build.
 *
 * Also enforces:
 *   * No empty `<string>` elements (would shadow the English default
 *     and produce blank notifications).
 *   * Every overlay's pattern key matches a registered pattern_id.
 */
class LocaleOverlayPolicyTest {

    private data class OverlayString(
        val locale: String,
        val key: String,
        val value: String,
    )

    /**
     * Locate `app/src/main/res/values-<locale>` directories relative to the
     * test working directory. Gradle launches tests from the
     * `android/app` module, so the path is the same as production.
     */
    private fun overlayFiles(): List<Pair<String, File>> {
        val resRoot = File("src/main/res")
        if (!resRoot.exists()) return emptyList()
        return resRoot.listFiles { f ->
            f.isDirectory && f.name.startsWith("values-")
        }?.mapNotNull { dir ->
            val locale = dir.name.removePrefix("values-")
            val xml = File(dir, "pattern_strings.xml")
            if (xml.exists()) locale to xml else null
        } ?: emptyList()
    }

    private fun parse(file: File, locale: String): List<OverlayString> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val doc = factory.newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val out = mutableListOf<OverlayString>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
            val text = node.textContent ?: ""
            out += OverlayString(locale, name, text)
        }
        return out
    }

    private fun localeLanguage(localeTag: String): String =
        localeTag.substringBefore("-r").substringBefore("-").lowercase()

    @Test
    fun `no overlay string contains banned push-side phrases`() {
        val violations = mutableListOf<String>()
        for ((locale, file) in overlayFiles()) {
            val lang = localeLanguage(locale)
            for (str in parse(file, locale)) {
                val phrase = AlertContentPolicy.phraseViolatingLocale(str.value, lang)
                if (phrase != null) {
                    violations += "${locale}::${str.key} contains '$phrase'"
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `no overlay ships an empty translation string`() {
        // Empty values would shadow the English default and produce blank
        // notifications. The translation policy in pattern_strings.xml
        // explicitly forbids this — absent keys fall back to English,
        // blank keys do not.
        val violations = mutableListOf<String>()
        for ((locale, file) in overlayFiles()) {
            for (str in parse(file, locale)) {
                if (str.value.isBlank()) {
                    violations += "${locale}::${str.key} is blank"
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `Spanish base overlay translates the four Phase 1 core patterns`() {
        // Sanity check that the es overlay actually ships the patterns
        // we promised in the issue scope, not just the language-selector
        // strings. (mi-rNZ and haw intentionally ship only the selector
        // — those have their own tests below.)
        val esFile = overlayFiles().firstOrNull { it.first == "es" }?.second
            ?: error("values-es/pattern_strings.xml missing — Tier-A locale")
        val keys = parse(esFile, "es").map { it.key }.toSet()
        listOf(
            "pattern_infection_onset_explanation",
            "pattern_sleep_disruption_explanation",
            "pattern_cardiovascular_stress_explanation",
            "pattern_overtraining_explanation",
        ).forEach { required ->
            assertTrue("es overlay missing $required", required in keys)
        }
    }

    @Test
    fun `Te Reo Maori overlay ships only the language-selector strings`() {
        // Honest scope: this PR ships clinical patterns in es and pt-BR
        // only. mi-rNZ intentionally ships only the UI selector strings
        // — translating clinical thresholds without iwi review would
        // violate the manifesto's "never colonising" framing.
        val miFile = overlayFiles().firstOrNull { it.first == "mi-rNZ" }?.second
            ?: error("values-mi-rNZ/pattern_strings.xml missing")
        val keys = parse(miFile, "mi-rNZ").map { it.key }.toSet()
        val patternKeys = keys.filter { it.startsWith("pattern_") }
        assertTrue(
            "mi-rNZ unexpectedly ships pattern translations: $patternKeys " +
                "— remove them or commission iwi review (see overlay comments)",
            patternKeys.isEmpty(),
        )
        assertTrue("settings_language_title" in keys)
    }

    @Test
    fun `Hawaiian overlay ships only the language-selector strings`() {
        val hawFile = overlayFiles().firstOrNull { it.first == "haw" }?.second
            ?: error("values-haw/pattern_strings.xml missing")
        val keys = parse(hawFile, "haw").map { it.key }.toSet()
        val patternKeys = keys.filter { it.startsWith("pattern_") }
        assertTrue(
            "haw unexpectedly ships pattern translations: $patternKeys",
            patternKeys.isEmpty(),
        )
        assertTrue("settings_language_title" in keys)
    }

    @Test
    fun `every overlay pattern key follows the pattern_id convention`() {
        // Localization keys must use lowercase pattern ids per the
        // AlertTextResolver lookup convention. Catches typos that
        // would silently fall through to English at runtime.
        val keyPattern = Regex(
            "^(?:pattern_[a-z0-9_]+_(?:explanation|suggested_action)" +
                "|alert_disclaimer_[a-z]{2}" +
                "|settings_language_[a-z_]+)$"
        )
        val violations = mutableListOf<String>()
        for ((locale, file) in overlayFiles()) {
            for (str in parse(file, locale)) {
                if (!keyPattern.matches(str.key)) {
                    violations += "${locale}::${str.key} does not match localization key convention"
                }
            }
        }
        assertEquals(violations.joinToString("\n"), 0, violations.size)
    }
}
