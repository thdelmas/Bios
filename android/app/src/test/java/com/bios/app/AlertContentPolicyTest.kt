package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPattern
import com.bios.app.model.ConditionCategory
import org.junit.Assert.assertFalse
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

    // #206 prognostic-push banlist additions. NEUROLOGY_POV §3.3 forbids
    // push-side communication of synucleinopathy / neurodegenerative risk.
    // The RBD screen is pull-side only; these tests pin representative
    // banned phrases against the validator so a future contributor
    // accidentally landing one in alert text fails CI.

    private fun patternWith(text: String): ConditionPattern = ConditionPattern(
        id = "test_pattern",
        title = "test",
        category = ConditionCategory.NEUROLOGICAL,
        signalRules = emptyList(),
        minActiveSignals = 1,
        explanation = text,
        suggestedAction = null,
    )

    @Test
    fun `Parkinson's risk is rejected`() {
        val violations = AlertContentPolicy.validate(patternWith("This says Parkinson's risk"))
        assertFalse("Parkinson's risk must be rejected", violations.isEmpty())
    }

    @Test
    fun `synucleinopathy is rejected`() {
        val violations = AlertContentPolicy.validate(patternWith("Risk of synucleinopathy."))
        assertFalse("synucleinopathy must be rejected", violations.isEmpty())
    }

    @Test
    fun `you may be developing is rejected`() {
        val violations = AlertContentPolicy.validate(patternWith("you may be developing Parkinson's."))
        assertFalse("you may be developing must be rejected", violations.isEmpty())
    }

    @Test
    fun `you may develop is rejected`() {
        val violations = AlertContentPolicy.validate(patternWith("you may develop this condition."))
        assertFalse("you may develop must be rejected", violations.isEmpty())
    }
}
