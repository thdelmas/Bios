package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.data.MedicationAnnotationRepo
import com.bios.app.model.MedicationAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the medication-context line that the alert-explanation surface
 * appends to anomaly text (audit gap §2.5). The formatter is a pure
 * `companion object` helper so the test exercises it without spinning up
 * Room.
 *
 * Two contracts to pin down:
 *
 *  - **Null when empty.** Owners with no active meds get a clean
 *    explanation (no trailing "Annotated current medications: ." artefact).
 *  - **Content-policy compliant.** The line lands in alert text that
 *    [AlertContentPolicy] validates at CI time. The formatter mustn't
 *    introduce banned phrases ("you should", "you need to", "your health
 *    score", gamification language).
 */
class MedicationAnnotationContextTest {

    private fun med(name: String, ended: Boolean = false): MedicationAnnotation =
        MedicationAnnotation(
            id = 0,
            name = name,
            startDate = 1_700_000_000_000L,
            endDate = if (ended) 1_700_500_000_000L else null,
        )

    @Test
    fun empty_list_returns_null_so_callers_can_skip_the_line() {
        assertNull(MedicationAnnotationRepo.formatMedicationContext(emptyList()))
    }

    @Test
    fun single_medication_renders_as_a_single_sentence() {
        val result = MedicationAnnotationRepo.formatMedicationContext(listOf(med("metoprolol")))
        assertEquals("Annotated current medications: metoprolol.", result)
    }

    @Test
    fun multiple_medications_join_with_commas_in_input_order() {
        // DAO already returns newest startDate first; the formatter preserves
        // that order so the most recent addition leads the list.
        val result = MedicationAnnotationRepo.formatMedicationContext(
            listOf(med("sertraline"), med("metoprolol"), med("atorvastatin"))
        )
        assertEquals(
            "Annotated current medications: sertraline, metoprolol, atorvastatin.",
            result
        )
    }

    @Test
    fun formatter_text_is_alert_content_policy_compliant() {
        val result = MedicationAnnotationRepo.formatMedicationContext(
            listOf(med("metoprolol"), med("atorvastatin"))
        ) ?: error("expected non-null formatter output")
        // Drive each prohibited phrase through the same path AlertContentPolicy
        // uses (lowercase substring). Failure here would mean the next alert
        // that carries a meds line could fail the policy gate in production.
        val lower = result.lowercase()
        val banned = listOf(
            "you should", "you need to", "you must", "try to",
            "your health score", "grade:", "you're unhealthy",
            "good job", "well done", "keep it up",
            "you haven't", "you missed", "you failed", "you forgot", "you didn't",
            "streak", "achievement", "level up", "points earned",
            "daily goal", "badge", "leaderboard", "ranking"
        )
        for (phrase in banned) {
            assertFalse(
                "Formatter output must not contain banned phrase '$phrase': $result",
                lower.contains(phrase)
            )
        }
        // And the actual AlertContentPolicy validator path is happy with the
        // existing pattern library — if the meds line ever lands in pattern
        // text via a different route, this catches it.
        assertTrue(AlertContentPolicy.validateAll().isEmpty())
    }

    // -- repo helpers --

    @Test
    fun discontinued_medications_serialize_the_same_as_active_for_a_given_input() {
        // Only the caller decides what list to pass — the formatter doesn't
        // filter. fetchActive() filters out endDate-set rows; the
        // historical fetchActiveOn() retains them when they were active at
        // the queried timestamp. Both still flow through the same formatter,
        // so the discontinued attribute doesn't change rendering.
        val active = MedicationAnnotationRepo.formatMedicationContext(listOf(med("metoprolol", ended = false)))
        val stopped = MedicationAnnotationRepo.formatMedicationContext(listOf(med("metoprolol", ended = true)))
        assertNotNull(active)
        assertNotNull(stopped)
        assertEquals(active, stopped)
    }
}
