package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPattern
import com.bios.app.model.ConditionCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
