package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AlertContentPolicyTest {

    @Test
    fun `every registered ConditionPattern is content-policy compliant`() {
        val violations = AlertContentPolicy.validateAll()
        assertTrue(
            violations.joinToString("\n") {
                "${it.pattern.id}::${it.field} contains '${it.prohibitedPhrase}': ${it.context}"
            },
            violations.isEmpty()
        )
    }

    @Test
    fun `default English pattern strings xml is content-policy compliant`() {
        // The localization refactor in issue #210 moved user-facing alert
        // text into res/values/pattern_strings.xml. The compliance gate
        // must follow the text — a translator who reverts to a banned
        // "you should…" phrase in the default file would otherwise slip
        // past the in-Kotlin compliance check above.
        val file = File("src/main/res/values/pattern_strings.xml")
        if (!file.exists()) return  // skip on modules that don't ship strings
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false,
            )
        }
        val doc = factory.newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val violations = mutableListOf<String>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
            val text = node.textContent ?: continue
            val phrase = AlertContentPolicy.phraseViolatingLocale(text, "en")
            if (phrase != null) {
                violations += "values/pattern_strings.xml::$name contains '$phrase'"
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }
}
