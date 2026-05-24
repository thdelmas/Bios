package com.bios.app

import com.bios.app.engine.PhoneSleepInference
import java.io.File
import kotlin.math.sqrt

/**
 * Fixture reader + variance translator for the Walch 2019 PhysioNet
 * `sleep-accel` dataset (#244 Cut 3). Test-only.
 *
 * Dataset layout (after running scripts/download-physionet-sleep-accel.sh):
 *
 * ```
 * sleep-accel/
 *     <subject_id>_acceleration.txt
 *     <subject_id>_labeled_sleep.txt
 *     ...
 * ```
 *
 * Or (alternate layout some PhysioNet packagings use):
 *
 * ```
 * sleep-accel/
 *     motion/<subject_id>_acceleration.txt
 *     labels/<subject_id>_labeled_sleep.txt
 * ```
 *
 * The enumerator walks recursively so both layouts work.
 *
 * ## File formats
 *
 *  - `*_acceleration.txt` — whitespace-separated rows of
 *    `secondsFromStart x y z` (m/s²). Sample rate varies per subject
 *    in the published dataset; this reader is rate-agnostic and
 *    groups by 60 s windows for variance computation.
 *  - `*_labeled_sleep.txt` — whitespace-separated rows of
 *    `secondsFromStart stageLabel` per 30 s PSG epoch. Stage 0 is
 *    wake; stages 1–5 are sleep (N1, N2, N3, N3, REM per Walch); -1
 *    is unscored.
 *
 * Lifted out so unit tests can pin the parser / translator / metrics
 * against small inline fixtures without ever needing the real
 * dataset.
 */
internal object PhysionetSleepAccelReader {

    /** Subscripts of PSG stage labels that count as sleep (Walch
     *  2019 convention). Stage 0 (wake) and -1 (unscored) are
     *  excluded from the ground-truth sleep-time sum. */
    val SLEEP_STAGE_LABELS: Set<Int> = setOf(1, 2, 3, 4, 5)

    /** PSG epoch length in seconds — fixed at 30 s by the Walch dataset. */
    const val PSG_EPOCH_SECONDS: Int = 30

    /** Variance-bucket width for the translator — matches the
     *  per-minute cadence [PhoneSleepInference.Sample] expects. */
    const val BUCKET_SECONDS: Int = 60

    /** Gravity-subtraction constant used by Bios's accel path
     *  ([com.bios.app.ingest.PhoneSleepAdapter]). The variance the
     *  inference reads is over `sqrt(x²+y²+z²) - g`. */
    const val GRAVITY_M_PER_S2: Float = 9.81f

    data class AccelSample(
        val secondsFromStart: Double,
        val x: Float,
        val y: Float,
        val z: Float,
    )

    data class LabelEpoch(
        val secondsFromStart: Long,
        val stageLabel: Int,
    )

    /** Parse one row of an `*_acceleration.txt` file. Returns null
     *  for malformed lines (blank, fewer than 4 columns, etc.). */
    fun parseAccelLine(line: String): AccelSample? {
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 4) return null
        val s = parts[0].toDoubleOrNull() ?: return null
        val x = parts[1].toFloatOrNull() ?: return null
        val y = parts[2].toFloatOrNull() ?: return null
        val z = parts[3].toFloatOrNull() ?: return null
        return AccelSample(s, x, y, z)
    }

    /** Parse one row of a `*_labeled_sleep.txt` file. Returns null
     *  for malformed lines. */
    fun parseLabelLine(line: String): LabelEpoch? {
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 2) return null
        val s = parts[0].toDoubleOrNull()?.toLong() ?: return null
        val stage = parts[1].toIntOrNull() ?: return null
        return LabelEpoch(s, stage)
    }

    /**
     * Translate a raw accelerometer trace into the per-minute
     * variance samples [PhoneSleepInference.infer] consumes. Groups
     * samples by [BUCKET_SECONDS]-second windows, computes the
     * variance of `sqrt(x²+y²+z²) - g` per window, and synthesizes
     * a [PhoneSleepInference.Sample] for each bucket.
     *
     * `screenOff = true` and `charging = true` are appropriate for
     * the PSG study setting (subjects are in bed, attended, with
     * the device assumed quiescent). `ambientLightLux = 0f` for the
     * same reason. The bucket's midpoint is the [PhoneSleepInference.Sample.timestamp].
     */
    fun toBiosSamples(
        accel: List<AccelSample>,
        sessionStartMs: Long,
    ): List<PhoneSleepInference.Sample> {
        if (accel.isEmpty()) return emptyList()
        val durationSec = accel.last().secondsFromStart.toInt() + 1
        val bucketCount = (durationSec + BUCKET_SECONDS - 1) / BUCKET_SECONDS
        val bucketed = Array(bucketCount) { mutableListOf<Float>() }
        for (s in accel) {
            val bucket = (s.secondsFromStart / BUCKET_SECONDS).toInt()
            if (bucket in 0 until bucketCount) {
                val magnitude = sqrt(s.x * s.x + s.y * s.y + s.z * s.z) - GRAVITY_M_PER_S2
                bucketed[bucket] += magnitude
            }
        }
        val samples = mutableListOf<PhoneSleepInference.Sample>()
        for (bucketIdx in 0 until bucketCount) {
            val bucket = bucketed[bucketIdx]
            val variance: Float? = if (bucket.size < 2) {
                null
            } else {
                val mean = bucket.average().toFloat()
                var sq = 0.0
                for (m in bucket) { val d = m - mean; sq += d * d }
                (sq / bucket.size).toFloat()
            }
            val midpointMs = sessionStartMs +
                ((bucketIdx * BUCKET_SECONDS + BUCKET_SECONDS / 2) * 1_000L)
            samples += PhoneSleepInference.Sample(
                timestamp = midpointMs,
                screenOff = true,
                charging = true,
                ambientLightLux = 0f,
                accelMagnitudeVar = variance,
            )
        }
        return samples
    }

    /**
     * Sum the PSG-labelled sleep epochs into total sleep time in
     * seconds. Stages in [SLEEP_STAGE_LABELS] contribute one
     * [PSG_EPOCH_SECONDS] each; wake (0) and unscored (-1) are
     * excluded.
     */
    fun groundTruthSleepSeconds(labels: List<LabelEpoch>): Long {
        return labels.count { it.stageLabel in SLEEP_STAGE_LABELS }
            .toLong() * PSG_EPOCH_SECONDS
    }

    /** Enumerate `(subjectId, accelFile, labelFile)` triples under
     *  [root]. Recursive — handles both flat and `motion/`/`labels/`
     *  layouts. */
    data class SubjectFiles(val id: String, val accel: File, val labels: File)

    fun enumerateSubjects(root: File): List<SubjectFiles> {
        if (!root.isDirectory) return emptyList()
        val accelFiles = mutableMapOf<String, File>()
        val labelFiles = mutableMapOf<String, File>()
        root.walkTopDown().forEach { f ->
            if (!f.isFile) return@forEach
            val name = f.name
            when {
                name.endsWith("_acceleration.txt") ->
                    accelFiles[name.removeSuffix("_acceleration.txt")] = f
                name.endsWith("_labeled_sleep.txt") ->
                    labelFiles[name.removeSuffix("_labeled_sleep.txt")] = f
            }
        }
        return accelFiles.keys.intersect(labelFiles.keys).sorted().map { id ->
            SubjectFiles(id, accelFiles.getValue(id), labelFiles.getValue(id))
        }
    }

    /** Read every parseable accel row from [file]. */
    fun readAccel(file: File): List<AccelSample> =
        file.useLines { it.mapNotNull(::parseAccelLine).toList() }

    /** Read every parseable label row from [file]. */
    fun readLabels(file: File): List<LabelEpoch> =
        file.useLines { it.mapNotNull(::parseLabelLine).toList() }
}

/**
 * Per-subject + aggregate metrics for the PhysioNet validation
 * harness (#244 Cut 3). Pure — no Android, no Room.
 */
internal object SleepValidationMetrics {

    data class SubjectResult(
        val subjectId: String,
        val predictedSleepSec: Long,
        val groundTruthSleepSec: Long,
    ) {
        val absoluteErrorSec: Long get() = kotlin.math.abs(predictedSleepSec - groundTruthSleepSec)
    }

    /** Total-sleep-time RMSE in minutes across [results]. Standard
     *  RMSE: sqrt(mean(squared_errors)). */
    fun rmseMinutes(results: List<SubjectResult>): Double {
        if (results.isEmpty()) return 0.0
        val sumSquaredSec = results.sumOf {
            val e = it.absoluteErrorSec.toDouble()
            e * e
        }
        val meanSquared = sumSquaredSec / results.size
        return sqrt(meanSquared) / 60.0
    }

    /** Human-readable per-subject + aggregate summary for the test
     *  output. */
    fun summary(results: List<SubjectResult>): String {
        if (results.isEmpty()) return "No subjects to summarise."
        val lines = mutableListOf<String>()
        lines += "Subject       predicted (min)   truth (min)   abs err (min)"
        for (r in results) {
            lines += "%-12s  %14d  %12d  %14d".format(
                r.subjectId,
                r.predictedSleepSec / 60,
                r.groundTruthSleepSec / 60,
                r.absoluteErrorSec / 60,
            )
        }
        lines += "RMSE (min): %.2f over %d subjects".format(rmseMinutes(results), results.size)
        return lines.joinToString("\n")
    }
}
