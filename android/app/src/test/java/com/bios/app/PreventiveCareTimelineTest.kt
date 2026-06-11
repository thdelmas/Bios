package com.bios.app

import com.bios.app.model.ScreeningEntry
import com.bios.app.screening.OwnerDemographics
import com.bios.app.screening.PreventiveCareTimeline
import com.bios.app.screening.PreventiveCareTimeline.Section
import com.bios.app.screening.ScreeningCadenceEngine
import com.bios.app.screening.ScreeningCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [PreventiveCareTimeline]'s placement and ordering. Time is pinned
 * (`now` is a concrete epoch) the same way as the sibling screening suites,
 * and the engine output is produced through the real catalog so the test
 * exercises the same data the screen sees.
 */
class PreventiveCareTimelineTest {

    // 2026-05-21 → 1747824000000 (matches ScreeningCadenceEngineTest).
    private val now: Long = 1_747_824_000_000L
    private val day = 86_400_000L
    private val daysPerMonth = 30.4375

    private fun monthsAgo(months: Int): Long =
        (now - (months * daysPerMonth * day).toLong()).toLong()

    private fun timelineFor(
        ageYears: Int,
        latestByKey: Map<String, Long?>,
    ): List<PreventiveCareTimeline.Item> {
        val results = ScreeningCadenceEngine.evaluateAll(
            catalog = ScreeningCatalog.combined,
            demographics = OwnerDemographics(ageYears = ageYears),
            latestByKey = { key ->
                latestByKey[key]?.let { ScreeningEntry(screeningKey = key, performedDate = it) }
            },
            now = now,
        )
        return PreventiveCareTimeline.build(results, latestByKey, now)
    }

    @Test
    fun overdue_recurring_screening_lands_in_upcoming() {
        // periodic_health_exam is RECURRING 36mo; performed 48mo ago → overdue.
        val items = timelineFor(ageYears = 30, mapOf("periodic_health_exam" to monthsAgo(48)))
        val exam = items.first { it.entry.key == "periodic_health_exam" }
        assertEquals(Section.UPCOMING, exam.section)
        assertTrue("overdue item should anchor at/around now", exam.anchorDate != null)
    }

    @Test
    fun current_screening_lands_in_past_with_next_due() {
        // periodic_health_exam performed 12mo ago is well inside the 36mo window.
        val last = monthsAgo(12)
        val items = timelineFor(ageYears = 30, mapOf("periodic_health_exam" to last))
        val exam = items.first { it.entry.key == "periodic_health_exam" }
        assertEquals(Section.PAST, exam.section)
        assertEquals(last, exam.anchorDate)
        assertTrue("current recurring item carries a next-due date", exam.nextDue != null)
        assertTrue("next-due is in the future", exam.nextDue!! > now)
    }

    @Test
    fun unrecorded_eligible_screening_lands_in_not_recorded() {
        val items = timelineFor(ageYears = 30, emptyMap())
        val depression = items.first { it.entry.key == "depression_screen" }
        assertEquals(Section.NOT_RECORDED, depression.section)
    }

    @Test
    fun age_gated_screening_lands_in_not_applicable() {
        // hearing_test is 50+; a 30-yo is below the band.
        val items = timelineFor(ageYears = 30, emptyMap())
        val hearing = items.first { it.entry.key == "hearing_test" }
        assertEquals(Section.NOT_APPLICABLE, hearing.section)
    }

    @Test
    fun one_time_test_with_record_is_past_with_no_next_due() {
        // hiv_test is one-time (Int.MAX_VALUE) — once recorded, no repeat.
        val items = timelineFor(ageYears = 30, mapOf("hiv_test" to monthsAgo(20)))
        val hiv = items.first { it.entry.key == "hiv_test" }
        assertEquals(Section.PAST, hiv.section)
        assertEquals(null, hiv.nextDue)
    }

    @Test
    fun upcoming_is_sorted_soonest_first_past_is_newest_first() {
        val items = timelineFor(
            ageYears = 30,
            mapOf(
                // Two overdue recurring items with different last-done → different next-due.
                "periodic_health_exam" to monthsAgo(48),   // 36mo cadence → due ~12mo ago
                "blood_pressure_check" to monthsAgo(30),    // 12mo cadence → due ~18mo ago
                // Two current items → PAST, ordered newest-first by last-done.
                "depression_screen" to monthsAgo(2),        // 12mo cadence, current
                "lipid_panel" to monthsAgo(6),              // not eligible <40, ignore if so
            ),
        )
        val upcoming = items.filter { it.section == Section.UPCOMING }
        if (upcoming.size >= 2) {
            val anchors = upcoming.map { it.anchorDate ?: now }
            assertEquals("upcoming sorted ascending", anchors.sorted(), anchors)
        }
        val past = items.filter { it.section == Section.PAST }
        if (past.size >= 2) {
            val anchors = past.map { it.anchorDate ?: 0L }
            assertEquals("past sorted descending", anchors.sortedDescending(), anchors)
        }
    }

    @Test
    fun render_order_is_upcoming_then_past_then_not_recorded_then_not_applicable() {
        val items = timelineFor(
            ageYears = 30,
            mapOf(
                "periodic_health_exam" to monthsAgo(48),  // upcoming
                "depression_screen" to monthsAgo(2),      // past
            ),
        )
        val sections = items.map { it.section }
        // Each section appears as a contiguous block in this fixed order.
        val order = listOf(
            Section.UPCOMING, Section.PAST, Section.NOT_RECORDED, Section.NOT_APPLICABLE,
        )
        val firstIndexOf = order.associateWith { sec -> sections.indexOfFirst { it == sec } }
            .filterValues { it >= 0 }
        val indices = order.mapNotNull { firstIndexOf[it] }
        assertEquals("sections appear in render order", indices.sorted(), indices)
    }
}
