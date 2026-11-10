package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.AlertDetailContent
import com.bios.app.model.AlertTier
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
    fun `evidence line counts readings and names sources`() {
        val one = AlertDetailContent.signal("resting_heart_rate", 69.0, rhrBaseline, 1, listOf("COROS"))
        assertEquals("1 reading · COROS", AlertDetailContent.evidenceLine(one))
        val mixed = AlertDetailContent.signal(
            "resting_heart_rate", 66.0, rhrBaseline, 3, listOf("COROS", "Bios derivation"),
        )
        assertEquals("3 readings · COROS, Bios derivation", AlertDetailContent.evidenceLine(mixed))
        assertEquals("", AlertDetailContent.evidenceLine(AlertDetailContent.signal("steps", 1.0, rhrBaseline)))
    }

    @Test
    fun `sleep duration reads as hours and minutes everywhere`() {
        val sleep = PersonalBaseline(
            metricType = "sleep_duration", mean = 28000.0, stdDev = 3000.0, p5 = 23616.0, p95 = 34113.0,
        )
        val s = AlertDetailContent.signal("sleep_duration", 30240.0, sleep)
        assertEquals("8h24", AlertDetailContent.formatValue("sleep_duration", 30240.0))
        assertEquals("8h24, usually 6h33–9h28", AlertDetailContent.valueVersusUsual(s))
    }

    @Test
    fun `now line appears only when the re-read window differs`() {
        val then = AlertDetailContent.signal("resting_heart_rate", 69.0, rhrBaseline, 1)
        val same = AlertDetailContent.signal("resting_heart_rate", 69.4, rhrBaseline, 1, listOf("COROS"))
        assertEquals("", AlertDetailContent.nowLine(then, same))
        assertEquals("", AlertDetailContent.nowLine(then, null))
        val changed = AlertDetailContent.signal("resting_heart_rate", 63.0, rhrBaseline, 1, listOf("COROS"))
        assertEquals("now 63 bpm from 1 reading · COROS", AlertDetailContent.nowLine(then, changed))
    }

    @Test
    fun `dot color band follows distance from the range`() {
        assertEquals(AlertDetailContent.DeviationBand.INSIDE, AlertDetailContent.deviationBand(0.4))
        assertEquals(AlertDetailContent.DeviationBand.EDGE, AlertDetailContent.deviationBand(-1.2))
        assertEquals(AlertDetailContent.DeviationBand.OUTSIDE, AlertDetailContent.deviationBand(2.3))
        assertEquals(AlertDetailContent.DeviationBand.FAR, AlertDetailContent.deviationBand(-3.6))
    }

    @Test
    fun `every tier has a meaning line`() {
        for (tier in AlertTier.entries) assertTrue(tier.name, AlertDetailContent.tierMeaning(tier).isNotBlank())
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
            AlertDetailContent.ONE_ALERT_NOTE + AlertDetailContent.STRIP_LEGEND +
            AlertTier.entries.map { AlertDetailContent.tierMeaning(it) } +
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
        assertTrue(AlertDetailContent.emergencyLine("112", "Spain").contains("call 112 (Spain)"))
        assertTrue(AlertDetailContent.emergencyLine(null).contains("local emergency services"))
        assertTrue(AlertDetailContent.emergencyLine("112").startsWith("Any one of them is enough"))
        assertTrue(AlertDetailContent.SEEK_CARE_HEADING.contains("any one"))
    }
}
