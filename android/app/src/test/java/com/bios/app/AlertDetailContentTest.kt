package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.AlertDetailContent
import com.bios.app.model.PersonalBaseline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/specs/alert-detail.md — goal 1 (real units) and the limits on copy. */
class AlertDetailContentTest {

    private val rhrBaseline = PersonalBaseline(
        metricType = "resting_heart_rate", mean = 56.5, stdDev = 2.6, p5 = 53.0, p95 = 60.0,
    )

    @Test
    fun `signal carries value and usual range in bpm, not only sigma`() {
        val s = AlertDetailContent.signal("resting_heart_rate", 66.0, rhrBaseline)
        assertTrue(s.deviating)
        assertTrue(s.above)
        assertEquals("66 bpm, usually 53–60 bpm", AlertDetailContent.valueVersusUsual(s))
        assertEquals(
            "Resting heart rate 66 bpm over the last 24 h, above your usual 53–60 bpm.",
            AlertDetailContent.notificationLine(s),
        )
    }

    @Test
    fun `in-range value is not deviating`() {
        val s = AlertDetailContent.signal("resting_heart_rate", 57.0, rhrBaseline)
        assertFalse(s.deviating)
    }

    @Test
    fun `every watched metric has causes in both directions`() {
        for (key in AlertDetailContent.WATCHED_METRICS) {
            assertTrue(key, AlertDetailContent.commonCauses(key, above = true).isNotEmpty())
            assertTrue(key, AlertDetailContent.commonCauses(key, above = false).isNotEmpty())
        }
    }

    @Test
    fun `detail copy stays clear of push-side banned phrases`() {
        val copy = AlertDetailContent.WATCHED_METRICS.flatMap { key ->
            AlertDetailContent.commonCauses(key, true) + AlertDetailContent.commonCauses(key, false)
        } + AlertDetailContent.WORTH_WATCHING + AlertDetailContent.SEEK_CARE_NOW +
            AlertDetailContent.ONE_ALERT_NOTE +
            AlertDetailContent.emergencyLine("112") + AlertDetailContent.emergencyLine(null)
        for (text in copy) {
            assertNull(text, AlertContentPolicy.phraseViolatingLocale(text, "en"))
        }
    }

    @Test
    fun `copy never claims a diagnosis`() {
        val copy = (AlertDetailContent.WORTH_WATCHING + AlertDetailContent.ONE_ALERT_NOTE).joinToString(" ")
        for (word in listOf("diagnos", "you have", "you are sick", "detected infection")) {
            assertFalse(word, copy.lowercase().contains(word))
        }
    }

    @Test
    fun `emergency line uses the regional number when known`() {
        assertTrue(AlertDetailContent.emergencyLine("112").contains("call 112"))
        assertTrue(AlertDetailContent.emergencyLine(null).contains("local emergency services"))
    }
}
