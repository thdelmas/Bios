package com.bios.app

import com.bios.app.alerts.HeadachePatterns
import com.bios.app.alerts.MedicationOveruseHeadacheEvaluator
import com.bios.app.model.HeadacheLog
import com.bios.app.model.HeadacheType
import com.bios.app.model.MigraineAttack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Unit tests for [MedicationOveruseHeadacheEvaluator] (issue #207,
 * IHS ICHD-3 §8.2).
 *
 * The evaluator counts acute-headache-treatment days per calendar
 * month across [MigraineAttack] and [HeadacheLog] entries; the
 * verdict fires when the most-recent 3 calendar months each have
 * at least 10 such days. Multi-entry same-day collapses to 1.
 */
class MedicationOveruseHeadacheEvaluatorTest {

    private val UTC = ZoneId.of("UTC")

    @Test
    fun `evaluator does not fire on empty diary`() {
        val verdict = MedicationOveruseHeadacheEvaluator.evaluate(
            migraineAttacks = emptyList(),
            headacheLogs = emptyList(),
            nowMillis = millisAt(2026, 5, 15),
            zoneId = UTC,
        )
        assertFalse(verdict.meetsScreeningThreshold)
        assertEquals(
            HeadachePatterns.MOH_CONSECUTIVE_MONTHS_REQUIRED,
            verdict.perMonthCounts.size,
        )
        verdict.perMonthCounts.forEach { assertEquals(0, it.treatmentDays) }
    }

    @Test
    fun `evaluator fires when 10 acute-treatment days per month across 3 months`() {
        val attacks = buildList {
            for (month in listOf(3, 4, 5)) {
                for (day in 1..10) {
                    add(
                        MigraineAttack(
                            id = "m-$month-$day",
                            onsetTimestamp = millisAt(2026, month, day),
                            peakIntensity = 6,
                            medicationTaken = "sumatriptan 50mg",
                        )
                    )
                }
            }
        }

        val verdict = MedicationOveruseHeadacheEvaluator.evaluate(
            migraineAttacks = attacks,
            headacheLogs = emptyList(),
            nowMillis = millisAt(2026, 5, 15),
            zoneId = UTC,
        )

        assertTrue(verdict.meetsScreeningThreshold)
        assertEquals(3, verdict.perMonthCounts.size)
        // newest-first ordering: May 2026, April 2026, March 2026.
        assertEquals(5, verdict.perMonthCounts[0].month)
        assertEquals(10, verdict.perMonthCounts[0].treatmentDays)
        assertEquals(4, verdict.perMonthCounts[1].month)
        assertEquals(10, verdict.perMonthCounts[1].treatmentDays)
        assertEquals(3, verdict.perMonthCounts[2].month)
        assertEquals(10, verdict.perMonthCounts[2].treatmentDays)
    }

    @Test
    fun `evaluator does not fire when one month falls short`() {
        val attacks = buildList {
            // 10 days/month in May + April.
            for (month in listOf(4, 5)) {
                for (day in 1..10) {
                    add(
                        MigraineAttack(
                            id = "m-$month-$day",
                            onsetTimestamp = millisAt(2026, month, day),
                            peakIntensity = 6,
                            medicationTaken = "ibuprofen 600mg",
                        )
                    )
                }
            }
            // March: only 9 days.
            for (day in 1..9) {
                add(
                    MigraineAttack(
                        id = "m-3-$day",
                        onsetTimestamp = millisAt(2026, 3, day),
                        peakIntensity = 6,
                        medicationTaken = "ibuprofen 600mg",
                    )
                )
            }
        }

        val verdict = MedicationOveruseHeadacheEvaluator.evaluate(
            migraineAttacks = attacks,
            headacheLogs = emptyList(),
            nowMillis = millisAt(2026, 5, 15),
            zoneId = UTC,
        )

        assertFalse(verdict.meetsScreeningThreshold)
        assertEquals(9, verdict.perMonthCounts[2].treatmentDays)
    }

    @Test
    fun `evaluator combines migraine and headache log treatment days`() {
        val attacks = (1..5).map { day ->
            MigraineAttack(
                id = "m-$day",
                onsetTimestamp = millisAt(2026, 5, day),
                peakIntensity = 6,
                medicationTaken = "sumatriptan",
            )
        }
        val logs = (6..10).map { day ->
            HeadacheLog(
                id = "h-$day",
                timestamp = millisAt(2026, 5, day),
                intensity = 4,
                type = HeadacheType.TENSION,
                medicationTaken = "paracetamol 1g",
            )
        }
        val priorMonthsAttacks = buildList {
            for (month in listOf(3, 4)) {
                for (day in 1..10) {
                    add(
                        MigraineAttack(
                            id = "p-$month-$day",
                            onsetTimestamp = millisAt(2026, month, day),
                            peakIntensity = 6,
                            medicationTaken = "sumatriptan",
                        )
                    )
                }
            }
        }

        val verdict = MedicationOveruseHeadacheEvaluator.evaluate(
            migraineAttacks = attacks + priorMonthsAttacks,
            headacheLogs = logs,
            nowMillis = millisAt(2026, 5, 15),
            zoneId = UTC,
        )

        // May = 5 (migraine) + 5 (headache) = 10 distinct days.
        assertTrue(verdict.meetsScreeningThreshold)
        assertEquals(10, verdict.perMonthCounts[0].treatmentDays)
    }

    @Test
    fun `evaluator ignores entries with blank medication`() {
        val attacks = (1..15).map { day ->
            MigraineAttack(
                id = "m-$day",
                onsetTimestamp = millisAt(2026, 5, day),
                peakIntensity = 6,
                medicationTaken = null,
            )
        }

        val verdict = MedicationOveruseHeadacheEvaluator.evaluate(
            migraineAttacks = attacks,
            headacheLogs = emptyList(),
            nowMillis = millisAt(2026, 5, 15),
            zoneId = UTC,
        )

        assertFalse(verdict.meetsScreeningThreshold)
        assertEquals(0, verdict.perMonthCounts[0].treatmentDays)
    }

    @Test
    fun `evaluator collapses multiple same-day entries to one treatment day`() {
        val attacks = listOf(8, 12, 18).map { hour ->
            MigraineAttack(
                id = "a-$hour",
                onsetTimestamp = millisAt(2026, 5, 1, hour = hour),
                peakIntensity = 5,
                medicationTaken = "sumatriptan",
            )
        }

        val verdict = MedicationOveruseHeadacheEvaluator.evaluate(
            migraineAttacks = attacks,
            headacheLogs = emptyList(),
            nowMillis = millisAt(2026, 5, 15),
            zoneId = UTC,
        )

        assertEquals(1, verdict.perMonthCounts[0].treatmentDays)
    }

    @Test
    fun `describeVerdict emits all three months in label form`() {
        val verdict = MedicationOveruseHeadacheEvaluator.Verdict(
            meetsScreeningThreshold = true,
            perMonthCounts = listOf(
                MedicationOveruseHeadacheEvaluator.MonthCount(2026, 5, 12),
                MedicationOveruseHeadacheEvaluator.MonthCount(2026, 4, 11),
                MedicationOveruseHeadacheEvaluator.MonthCount(2026, 3, 10),
            ),
        )

        val text = MedicationOveruseHeadacheEvaluator.describeVerdict(verdict)
        assertTrue(text.contains("May 2026"))
        assertTrue(text.contains("Apr 2026"))
        assertTrue(text.contains("Mar 2026"))
        assertTrue(text.contains("12 acute-treatment days"))
    }

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, UTC).toInstant().toEpochMilli()
}
