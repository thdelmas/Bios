package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
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
}
