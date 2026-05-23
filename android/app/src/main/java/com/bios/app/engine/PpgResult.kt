package com.bios.app.engine

/** Outcome of processing a PPG waveform. */
data class PpgResult(
    /** RR intervals in ms between successive peaks. Empty when rejected. */
    val rrIntervalsMs: List<Double>,
    /** 0–100 composite signal quality. 0 when rejected. */
    val sqiScore: Int,
    /** Non-null when the recording was rejected; carries the user-facing reason. */
    val rejectionReason: RejectionReason?,
    /** Number of detected peaks (informational, even when rejected). */
    val peakCount: Int,
    /** Recording length the processor saw. */
    val durationSec: Double,
    /**
     * Statistical summaries of pulse-wave morphology — peak-amplitude central
     * tendency / dispersion, rise-time central tendency / dispersion,
     * decay-asymmetry, dichrotic-notch position, PPG-derived augmentation
     * index. Null when the recording was rejected or had too few clean beats
     * to summarise. Never contains the raw waveform — only scalars suitable
     * for persistence as `MetricType.PPG_WAVEFORM_*` / `AUGMENTATION_INDEX_PPG`
     * derived metrics.
     *
     * Surfaced for downstream Western-cardiology arterial-stiffness views
     * (Vlachopoulos 2010; Townsend 2015 AHA stiffness statement; ESC 2018)
     * and traditional-medicine pulse-quality readers (TCM, Sowa Rigpa, Kampo,
     * Korean, Siddha, Unani, Ayurveda). See CARDIOLOGY_POV.md §2.2.
     */
    val waveformFeatures: PulseWaveformFeatures? = null,
    /**
     * Trimmed CoV of detected peak amplitudes — the same value compared
     * against [PpgSignalProcessor.MAX_PEAK_AMPLITUDE_COV]. Non-null whenever
     * the pipeline reached peak detection (accept + MOTION_ARTIFACT /
     * IRREGULAR_RHYTHM rejections); null on early rejections that bailed
     * before peak detection ran. Surfaced for [PpgCalibrationLogger] so the
     * offline log captures the value even when accept paths discard it.
     */
    val peakAmplitudeCov: Double? = null,
) {
    val accepted: Boolean get() = rejectionReason == null

    companion object {
        fun rejected(
            reason: RejectionReason,
            durationSec: Double,
            peakCount: Int = 0,
            sqiScore: Int = 0,
            peakAmplitudeCov: Double? = null,
        ) = PpgResult(
            rrIntervalsMs = emptyList(),
            sqiScore = sqiScore,
            rejectionReason = reason,
            peakCount = peakCount,
            durationSec = durationSec,
            waveformFeatures = null,
            peakAmplitudeCov = peakAmplitudeCov,
        )
    }
}
