package com.bios.app

import com.bios.app.engine.PhoneSleepInference
import com.bios.contracts.MetricType
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * Validation harness that replays the Walch 2019 PhysioNet
 * `sleep-accel` dataset (31 subjects, raw accelerometer + PSG
 * labels) through [PhoneSleepInference] and asserts the total-
 * sleep-time RMSE stays under [RMSE_TARGET_MINUTES] (#244 Cut 3).
 *
 * Becomes a regression test once the dataset is downloaded — any
 * change to Cole-Kripke / Webster / the per-owner threshold that
 * meaningfully regresses overall accuracy fails CI.
 *
 * **Dataset acquisition** — the data is ~700 MB and intentionally
 * **not** checked into the repo. Fetch it once with
 * `scripts/download-physionet-sleep-accel.sh` (uses the standard
 * PhysioNet `wget` recipe; ODC-By license, no credentialing
 * required), then point the test at the fixture directory via
 * either the `bios.physionet.dir` JVM system property or the
 * `BIOS_PHYSIONET_DIR` environment variable.
 *
 * **Skip semantics** — when the fixture directory isn't set or
 * isn't a directory, the test is **skipped** (JUnit `Assume`), not
 * failed. CI runs and developer machines without the dataset
 * continue to pass; only contributors who have downloaded the
 * dataset measure regressions.
 */
class PhoneSleepValidationTest {

    /**
     * RMSE target in minutes. Initial bar from #244's acceptance:
     * 45 min. Lower is better; this test currently asserts the
     * upper bound to detect regressions, not to claim a specific
     * accuracy number. The actual number prints to stdout via the
     * test runner.
     */
    private val RMSE_TARGET_MINUTES: Double = 45.0

    @Test
    fun rmse_under_target_across_all_subjects() {
        val fixtureDir = resolveFixtureDir()
        Assume.assumeTrue(
            "PhysioNet sleep-accel fixture not present. Run " +
                "scripts/download-physionet-sleep-accel.sh and set " +
                "BIOS_PHYSIONET_DIR (or -Dbios.physionet.dir) to enable this test.",
            fixtureDir != null,
        )
        val subjects = PhysionetSleepAccelReader.enumerateSubjects(fixtureDir!!)
        Assume.assumeTrue(
            "No (accel, label) subject pairs found under $fixtureDir — " +
                "did the wget mirror complete?",
            subjects.isNotEmpty(),
        )

        val results = subjects.map { evaluateSubject(it) }
        val summary = SleepValidationMetrics.summary(results)
        // Surface the per-subject + aggregate metrics on stdout so
        // a curious contributor can see the actual numbers, not just
        // a pass/fail.
        println(summary)
        val rmse = SleepValidationMetrics.rmseMinutes(results)
        assertTrue(
            "RMSE %.2f min exceeds target %.2f min".format(rmse, RMSE_TARGET_MINUTES),
            rmse < RMSE_TARGET_MINUTES,
        )
    }

    private fun resolveFixtureDir(): File? {
        val candidate = System.getProperty(SYS_PROP)
            ?: System.getenv(ENV_VAR)
            ?: return null
        val f = File(candidate)
        return if (f.isDirectory) f else null
    }

    /**
     * Run one subject through the pipeline: read raw accel + labels,
     * translate accel into per-minute Bios samples, hand to
     * [PhoneSleepInference.infer], extract the predicted sleep
     * duration, compare to the PSG-labelled total sleep time.
     *
     * `sessionStartMs` is arbitrary — the inference reads relative
     * timestamps inside the sample list. We use a fixed epoch so
     * the test is reproducible.
     */
    private fun evaluateSubject(
        subject: PhysionetSleepAccelReader.SubjectFiles,
    ): SleepValidationMetrics.SubjectResult {
        val accel = PhysionetSleepAccelReader.readAccel(subject.accel)
        val labels = PhysionetSleepAccelReader.readLabels(subject.labels)
        val biosSamples = PhysionetSleepAccelReader.toBiosSamples(accel, SESSION_START_MS)
        val readings = PhoneSleepInference.infer(biosSamples, sourceId = "physionet")
        val predictedSec = readings
            .firstOrNull { it.metricType == MetricType.SLEEP_DURATION.key }
            ?.value
            ?.toLong()
            ?: 0L
        val truthSec = PhysionetSleepAccelReader.groundTruthSleepSeconds(labels)
        return SleepValidationMetrics.SubjectResult(
            subjectId = subject.id,
            predictedSleepSec = predictedSec,
            groundTruthSleepSec = truthSec,
        )
    }

    companion object {
        private const val SYS_PROP = "bios.physionet.dir"
        private const val ENV_VAR = "BIOS_PHYSIONET_DIR"
        // Arbitrary fixed epoch (Mon 14 Nov 2023 22:13:20 UTC). The
        // inference reads relative timestamps inside the sample list;
        // the absolute value just needs to be stable.
        private const val SESSION_START_MS: Long = 1_700_000_000_000L
    }
}
