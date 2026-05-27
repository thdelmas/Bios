package com.bios.app.ingest

import com.bios.app.engine.EctopyDetector
import com.bios.app.engine.HrvAnalyzer
import com.bios.app.engine.PpgResult
import com.bios.app.engine.RejectionReason
import com.bios.app.engine.RhythmClassifier
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Pure mapping from [PpgResult] + [HrvAnalyzer.HrvResult] to a [CaptureResult].
 * Extracted from [CameraPpgAdapter] so the adapter file stays under the
 * project's 500-line ceiling.
 *
 * Lives on its own object (no Android Context) so the mapping logic can be
 * unit-tested directly. The adapter forwards to [toResult] and otherwise
 * holds the CameraX capture loop and frame-analyzer state.
 */
internal object CameraPpgResultMapper {

    /** Minimum fraction of raw IBIs the Malik artifact filter must keep before
     *  HR is sourced from its cleaned mean rather than the raw peak rate.
     *  Empirically: ≥ 0.5 → Malik mean tracks a wrist wearable within 1–2 bpm;
     *  below that the sequential filter's anchoring bias pulls the mean IBI
     *  long and we get cleaner numbers from peak rate. */
    private const val MALIK_RELIABLE_FRACTION = 0.5

    fun toResult(
        ppg: PpgResult,
        hrv: HrvAnalyzer.HrvResult?,
        sourceId: String,
        timestamp: Long,
    ): CaptureResult {
        if (!ppg.accepted) {
            return CaptureResult(
                readings = emptyList(),
                rejectionReason = ppg.rejectionReason,
                sqiScore = ppg.sqiScore,
                peakCount = ppg.peakCount,
                durationSec = ppg.durationSec,
            )
        }
        if (hrv == null) {
            // Peaks passed SQI but HRV analyzer discarded too many artifacts.
            return CaptureResult(
                readings = emptyList(),
                rejectionReason = RejectionReason.IRREGULAR_RHYTHM,
                sqiScore = ppg.sqiScore,
                peakCount = ppg.peakCount,
                durationSec = ppg.durationSec,
            )
        }

        val durSec = ppg.durationSec.toInt().coerceAtLeast(1)
        val rhythmVerdict = RhythmClassifier.classify(ppg.rrIntervalsMs)
        // PPG-derived AFib screening burden (#180, cardiology audit §2.1).
        // Per-session verdict 0.0 (regular) / 100.0 (irregularly irregular)
        // — the pattern's rolling median over 7 days decides whether to fire.
        // INSUFFICIENT_DATA windows are skipped so the burden estimate isn't
        // biased by short captures.
        val burdenReading = if (rhythmVerdict.verdict == RhythmClassifier.WindowVerdict.INSUFFICIENT_DATA) {
            null
        } else {
            reading(MetricType.IRREGULAR_RHYTHM_BURDEN, if (rhythmVerdict.irregular) 100.0 else 0.0,
                timestamp, durSec, sourceId)
        }
        // PVC-burden estimate (#216 / Triage #26). Skipped on rhythms the
        // classifier scores irregularly-irregular — AFib's randomness inflates
        // the short-long pair count and would confound the PVC interpretation.
        val ectopyReading = if (rhythmVerdict.verdict != RhythmClassifier.WindowVerdict.REGULAR) {
            null
        } else {
            EctopyDetector.analyse(ppg.rrIntervalsMs)?.let {
                reading(MetricType.ECTOPY_BURDEN_ESTIMATE, it.burdenPercent, timestamp, durSec, sourceId)
            }
        }

        // Pick the HR estimator that's more accurate for this signal's SNR.
        //  - High SNR (Malik retained ≥ MALIK_RELIABLE_FRACTION of IBIs):
        //    trust [HrvAnalyzer]'s mean — it filters out the few false peaks
        //    the detector accepts and gives sub-bpm agreement with a wrist
        //    wearable in practice.
        //  - Low SNR (Malik dropped most IBIs): the sequential Malik filter
        //    anchors on a non-representative subset and biases the mean IBI
        //    long; fall back to raw peak rate which doesn't care about
        //    interval cleanliness.
        // RMSSD and the other HRV metrics always come from the Malik-cleaned
        // set since they need interval cleanliness, not absolute rate.
        val rawIbis = ppg.peakCount - 1
        val malikSurvivalRate = if (rawIbis > 0) hrv.cleanIbiCount.toDouble() / rawIbis else 0.0
        val hrBpmFromPeakRate = 60.0 * ppg.peakCount / ppg.durationSec.coerceAtLeast(1e-3)
        val hrBpm = if (malikSurvivalRate >= MALIK_RELIABLE_FRACTION) hrv.meanHrBpm else hrBpmFromPeakRate

        val readings = listOfNotNull(
            reading(MetricType.HEART_RATE, hrBpm, timestamp, durSec, sourceId),
            reading(MetricType.HEART_RATE_VARIABILITY, hrv.rmssd, timestamp, durSec, sourceId),
            reading(MetricType.PARASYMPATHETIC_TONE, hrv.lnRmssd, timestamp, durSec, sourceId),
            reading(MetricType.STRESS_SCORE, hrv.stressIndex, timestamp, durSec, sourceId),
            reading(MetricType.LF_HF_RATIO, hrv.lfHfRatio, timestamp, durSec, sourceId),
            reading(MetricType.HRV_LF_POWER, hrv.lfPowerMs2, timestamp, durSec, sourceId),
            reading(MetricType.HRV_HF_POWER, hrv.hfPowerMs2, timestamp, durSec, sourceId),
            burdenReading,
            ectopyReading,
            // PPG pulse-wave morphology (#181, CARDIOLOGY_POV.md §2.2). Written
            // only when waveformFeatures is populated; rejected recordings and
            // sessions with too few clean beats skip every key in this block.
            ppg.waveformFeatures?.let {
                reading(MetricType.PPG_PEAK_AMPLITUDE_MEAN, it.peakAmplitudeTrimmedMean,
                    timestamp, durSec, sourceId)
            },
            ppg.waveformFeatures?.let {
                reading(MetricType.PPG_PEAK_AMPLITUDE_COV, it.peakAmplitudeCov,
                    timestamp, durSec, sourceId)
            },
            ppg.waveformFeatures?.let {
                reading(MetricType.PPG_RISE_TIME_MEAN, it.riseTimeMeanSec, timestamp, durSec, sourceId)
            },
            ppg.waveformFeatures?.let {
                reading(MetricType.PPG_RISE_TIME_COV, it.riseTimeCov, timestamp, durSec, sourceId)
            },
            ppg.waveformFeatures?.let {
                reading(MetricType.PPG_DECAY_ASYMMETRY_INDEX, it.decayAsymmetryIndex,
                    timestamp, durSec, sourceId)
            },
            // Dichrotic-notch position is nullable — only surfaced when enough
            // beats yield a detectable notch.
            ppg.waveformFeatures?.dichroticNotchRelativePosition?.let {
                reading(MetricType.PPG_DICHROTIC_NOTCH_POSITION, it, timestamp, durSec, sourceId)
            },
            // PPG-derived augmentation index (Blueprint audit §3.1 #8). Null on
            // short or noisy recordings where the diastolic rebound is not
            // detectable on enough beats.
            ppg.waveformFeatures?.augmentationIndexPercent?.let {
                reading(MetricType.AUGMENTATION_INDEX_PPG, it, timestamp, durSec, sourceId)
            },
        )
        return CaptureResult(
            readings = readings,
            rejectionReason = null,
            sqiScore = ppg.sqiScore,
            peakCount = ppg.peakCount,
            durationSec = ppg.durationSec,
        )
    }

    private fun reading(
        type: MetricType,
        value: Double,
        timestamp: Long,
        durSec: Int,
        sourceId: String,
    ): MetricReading = MetricReading(
        metricType = type.key,
        value = value,
        timestamp = timestamp,
        durationSec = durSec,
        sourceId = sourceId,
        confidence = ConfidenceTier.LOW.level,
    )
}
