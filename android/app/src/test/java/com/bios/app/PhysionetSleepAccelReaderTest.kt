package com.bios.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pure-JVM coverage of [PhysionetSleepAccelReader] +
 * [SleepValidationMetrics] (#244 Cut 3). Exercises the parsers,
 * variance translator, ground-truth summation, subject enumeration,
 * and RMSE computation against small inline / temp-file fixtures
 * so the harness logic is locked down even without the real ~700
 * MB PhysioNet dataset.
 */
class PhysionetSleepAccelReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // -- parseAccelLine --

    @Test
    fun parseAccelLine_accepts_a_well_formed_row() {
        val sample = PhysionetSleepAccelReader.parseAccelLine("12.5 0.1 -9.81 0.02")
        assertNotNull(sample)
        assertEquals(12.5, sample!!.secondsFromStart, 1e-9)
        assertEquals(0.1f, sample.x, 1e-6f)
        assertEquals(-9.81f, sample.y, 1e-6f)
        assertEquals(0.02f, sample.z, 1e-6f)
    }

    @Test
    fun parseAccelLine_tolerates_multiple_whitespace_separators() {
        val sample = PhysionetSleepAccelReader.parseAccelLine("12.5\t0.1   -9.81  0.02")
        assertNotNull(sample)
    }

    @Test
    fun parseAccelLine_rejects_short_or_garbage_rows() {
        assertNull(PhysionetSleepAccelReader.parseAccelLine(""))
        assertNull(PhysionetSleepAccelReader.parseAccelLine("12.5 0.1"))
        assertNull(PhysionetSleepAccelReader.parseAccelLine("garbage"))
    }

    // -- parseLabelLine --

    @Test
    fun parseLabelLine_accepts_wake_and_sleep_stages() {
        val wake = PhysionetSleepAccelReader.parseLabelLine("0.0 0")
        val rem = PhysionetSleepAccelReader.parseLabelLine("90.0 5")
        assertEquals(0, wake!!.stageLabel)
        assertEquals(5, rem!!.stageLabel)
        assertEquals(90L, rem.secondsFromStart)
    }

    @Test
    fun parseLabelLine_accepts_unscored_minus_one() {
        val unscored = PhysionetSleepAccelReader.parseLabelLine("60 -1")
        assertEquals(-1, unscored!!.stageLabel)
    }

    // -- groundTruthSleepSeconds --

    @Test
    fun groundTruthSleepSeconds_excludes_wake_and_unscored() {
        val labels = listOf(
            PhysionetSleepAccelReader.LabelEpoch(0, 0),    // wake
            PhysionetSleepAccelReader.LabelEpoch(30, -1),  // unscored
            PhysionetSleepAccelReader.LabelEpoch(60, 2),   // sleep
            PhysionetSleepAccelReader.LabelEpoch(90, 3),   // sleep
            PhysionetSleepAccelReader.LabelEpoch(120, 5),  // sleep (REM)
            PhysionetSleepAccelReader.LabelEpoch(150, 0),  // wake
        )
        // Three sleep epochs × 30 s = 90 s.
        assertEquals(90L, PhysionetSleepAccelReader.groundTruthSleepSeconds(labels))
    }

    @Test
    fun groundTruthSleepSeconds_zero_for_all_wake() {
        val labels = (0..9).map {
            PhysionetSleepAccelReader.LabelEpoch((it * 30).toLong(), 0)
        }
        assertEquals(0L, PhysionetSleepAccelReader.groundTruthSleepSeconds(labels))
    }

    // -- toBiosSamples --

    @Test
    fun toBiosSamples_buckets_into_per_minute_variance() {
        // Quiet first 60s, then 60s with varying-magnitude motion.
        val accel = buildList {
            for (s in 0 until 60) add(
                PhysionetSleepAccelReader.AccelSample(s.toDouble(), 0f, 0f, 9.81f),
            )
            for (s in 60 until 120) add(
                PhysionetSleepAccelReader.AccelSample(
                    secondsFromStart = s.toDouble(),
                    // Magnitude alternates between sqrt(1 + 9.81²) ≈ 9.861
                    // and sqrt(9 + 9.81²) ≈ 10.241 → variance is non-zero
                    // because the magnitude itself varies, not just the
                    // axis (sign-flip preserves magnitude).
                    x = if (s % 2 == 0) 1f else 3f,
                    y = 0f,
                    z = 9.81f,
                ),
            )
        }
        val samples = PhysionetSleepAccelReader.toBiosSamples(accel, sessionStartMs = 0L)
        assertEquals(2, samples.size)
        // First bucket: every sample has magnitude exactly 9.81 - 9.81 = 0.
        assertEquals(0f, samples[0].accelMagnitudeVar!!, 1e-3f)
        // Second bucket: alternating magnitude → nonzero variance.
        assertTrue(samples[1].accelMagnitudeVar!! > 0f)
        // Synthesised flags: PSG-study assumptions baked in.
        assertTrue(samples.all { it.screenOff })
        assertTrue(samples.all { it.charging })
    }

    @Test
    fun toBiosSamples_returns_empty_on_empty_input() {
        assertEquals(emptyList<Any>(), PhysionetSleepAccelReader.toBiosSamples(emptyList(), 0L))
    }

    // -- enumerateSubjects --

    @Test
    fun enumerateSubjects_finds_paired_files_in_flat_layout() {
        val root = tmp.newFolder("sleep-accel")
        File(root, "s1_acceleration.txt").writeText("0 0 0 9.81")
        File(root, "s1_labeled_sleep.txt").writeText("0 0")
        File(root, "s2_acceleration.txt").writeText("0 0 0 9.81")
        File(root, "s2_labeled_sleep.txt").writeText("0 0")
        val subjects = PhysionetSleepAccelReader.enumerateSubjects(root)
        assertEquals(listOf("s1", "s2"), subjects.map { it.id })
    }

    @Test
    fun enumerateSubjects_finds_paired_files_in_subdir_layout() {
        val root = tmp.newFolder("sleep-accel")
        File(root, "motion").mkdir()
        File(root, "labels").mkdir()
        File(root, "motion/s1_acceleration.txt").writeText("0 0 0 9.81")
        File(root, "labels/s1_labeled_sleep.txt").writeText("0 0")
        val subjects = PhysionetSleepAccelReader.enumerateSubjects(root)
        assertEquals(listOf("s1"), subjects.map { it.id })
    }

    @Test
    fun enumerateSubjects_drops_unpaired_files() {
        val root = tmp.newFolder("sleep-accel")
        File(root, "s1_acceleration.txt").writeText("0 0 0 9.81")
        // No matching label file for s1.
        File(root, "s2_labeled_sleep.txt").writeText("0 0")
        val subjects = PhysionetSleepAccelReader.enumerateSubjects(root)
        assertTrue(subjects.isEmpty())
    }

    // -- SleepValidationMetrics --

    @Test
    fun rmseMinutes_returns_zero_for_perfect_predictions() {
        val results = listOf(
            SleepValidationMetrics.SubjectResult("a", 28_800L, 28_800L), // 8h
            SleepValidationMetrics.SubjectResult("b", 21_600L, 21_600L), // 6h
        )
        assertEquals(0.0, SleepValidationMetrics.rmseMinutes(results), 1e-6)
    }

    @Test
    fun rmseMinutes_computes_root_mean_squared_error_in_minutes() {
        // Errors of 600 s and 1200 s → squared 360000 + 1440000 = 1800000 → mean 900000 → sqrt = 948.68 sec.
        // In minutes: 948.68 / 60 ≈ 15.81.
        val results = listOf(
            SleepValidationMetrics.SubjectResult("a", 28_800L, 28_200L), // err 600
            SleepValidationMetrics.SubjectResult("b", 21_600L, 22_800L), // err 1200
        )
        assertEquals(15.81, SleepValidationMetrics.rmseMinutes(results), 0.01)
    }

    @Test
    fun rmseMinutes_returns_zero_for_empty_input() {
        assertEquals(0.0, SleepValidationMetrics.rmseMinutes(emptyList()), 1e-9)
    }

    @Test
    fun summary_includes_rmse_and_per_subject_rows() {
        val results = listOf(
            SleepValidationMetrics.SubjectResult("a", 28_800L, 28_800L),
        )
        val text = SleepValidationMetrics.summary(results)
        assertTrue(text.contains("RMSE"))
        assertTrue(text.contains("a"))
    }
}
