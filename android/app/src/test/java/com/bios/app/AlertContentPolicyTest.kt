package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AlertContentPolicyTest {

    @Test
    fun `every text Bios pushes is content-policy compliant`() {
        val texts = AlertContentPolicy.pushTexts()
        assertTrue("alert title is covered", texts.containsKey("alert.title"))
        assertTrue("every tier line is covered", texts.keys.count { it.startsWith("tier.") } == 5)
        val violations = AlertContentPolicy.validateAll()
        assertTrue(
            violations.joinToString("\n") { "${it.field} contains '${it.prohibitedPhrase}': ${it.context}" },
            violations.isEmpty(),
        )
    }

    @Test
    fun `a violation names the field, the phrase and its surroundings`() {
        val v = AlertContentPolicy.validate("alert.title", "Resting heart rate is up, you should rest more.").single()
        assertEquals("alert.title", v.field)
        assertEquals("you should", v.prohibitedPhrase)
        assertTrue(v.context, v.context.contains("you should rest"))
    }

    @Test
    fun `a measured line passes`() {
        val line = "Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm."
        assertTrue(AlertContentPolicy.validate("line", line).isEmpty())
    }

    @Test
    fun `judgment, guilt, gamification and prognosis are rejected`() {
        val banned = listOf(
            "You need to sleep more.", "Your health score is 71.", "You missed a day.", "A 7-day streak!",
            "This says Parkinson's risk", "you may be developing something", "you may develop this", "You're at risk of a fall",
        )
        for (text in banned) assertFalse(text, AlertContentPolicy.validate("t", text).isEmpty())
    }

    @Test
    fun `locale banlists add to the English one`() {
        assertEquals("deberías", AlertContentPolicy.phraseViolatingLocale("Deberías dormir más.", "es"))
        assertEquals("você precisa", AlertContentPolicy.phraseViolatingLocale("Você precisa descansar.", "pt"))
        assertEquals("you should", AlertContentPolicy.phraseViolatingLocale("you should rest", "es"))
        assertNull(AlertContentPolicy.phraseViolatingLocale("Frecuencia cardíaca en reposo 66 lpm.", "es"))
        assertNull(AlertContentPolicy.phraseViolatingLocale("Deberías dormir más.", "sw"))
    }

    @Test
    fun `an overlay violation names the locale and the resource`() {
        val v = AlertContentPolicy.validateLocaleOverlay("es-MX", mapOf("ok" to "Idioma", "bad" to "Tienes que caminar.")).single()
        assertEquals("es-MX", v.languageTag)
        assertEquals("bad", v.resourceName)
        assertEquals("tienes que", v.prohibitedPhrase)
    }

    @Test
    fun `every shipped string resource is content-policy compliant in its locale`() {
        val res = File("src/main/res")
        val dirs = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        assertTrue("string resources found from ${res.absolutePath}", dirs.isNotEmpty())
        val violations = dirs.flatMap { dir ->
            // "values-es-rMX" -> "es-MX"; the default folder is English.
            val tag = dir.name.removePrefix("values").removePrefix("-").replace("-r", "-").ifEmpty { "en" }
            AlertContentPolicy.validateLocaleOverlay(tag, strings(dir))
        }
        assertTrue(
            violations.joinToString("\n") { "${it.languageTag}::${it.resourceName} contains '${it.prohibitedPhrase}'" },
            violations.isEmpty(),
        )
    }

    private fun strings(dir: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        return dir.listFiles { f -> f.name.endsWith("strings.xml") }.orEmpty().flatMap { file ->
            val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).mapNotNull { i ->
                val node = nodes.item(i)
                val name = node.attributes.getNamedItem("name")?.nodeValue ?: return@mapNotNull null
                name to (node.textContent ?: "")
            }
        }.toMap()
    }
}
