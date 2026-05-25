package com.bios.app.ingest

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.lifecycle.LifecycleOwner
import android.os.Build
import com.bios.app.engine.CaptureQuality
import com.bios.app.engine.EctopyDetector
import com.bios.app.engine.HrvAnalyzer
import com.bios.app.engine.PpgCalibrationLogger
import com.bios.app.engine.PpgDeviceProfiles
import com.bios.app.engine.PpgResult
import com.bios.app.engine.PpgSignalProcessor
import com.bios.app.engine.RejectionReason
import com.bios.app.engine.RhythmClassifier
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * User-initiated fingertip-PPG capture via the rear camera + flash.
 *
 * Pipeline: CameraX ImageAnalysis streams YUV frames → Y-plane mean per
 * frame → [PpgSignalProcessor] → [HrvAnalyzer] → [MetricReading]s.
 *
 * The caller is responsible for (a) holding CAMERA permission, (b) passing
 * a [LifecycleOwner] so CameraX can bind, and (c) providing a [sourceId]
 * pointing at a DataSource row with source_type = "camera_ppg". The adapter
 * never writes to the DB itself — it returns readings; the ViewModel/DAO
 * layer persists them.
 *
 * See docs/ARCHITECTURE.md §1 for the adapter contract. Deliberate design
 * choices: no streaming, no live HR during capture (minimise engagement-loop
 * feel — see GH-13 non-goals).
 */
class CameraPpgAdapter(private val context: Context) {

    /**
     * Capture a fingertip-PPG snapshot for [durationSec] seconds. Blocks the
     * calling coroutine until capture completes. Returns a [CaptureResult]
     * with either a HR + HRV reading pair or a rejection reason.
     *
     * Optional real-time feedback channels:
     *  - [surfaceProvider] — when non-null, a `Preview` use case is bound to
     *    it so the UI can show the live lens view. This is placement-only
     *    feedback (a finger fully on the lens looks dark red); no metric
     *    values are streamed, per the no-streaming design rule.
     *  - [qualityFlow] — when non-null, [CaptureQuality] classifications
     *    are pushed during capture so the UI can coach the owner if the
     *    finger drifts or motion is detected. Updates ~2 Hz.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun capture(
        lifecycleOwner: LifecycleOwner,
        durationSec: Int,
        sourceId: String,
        surfaceProvider: Preview.SurfaceProvider? = null,
        qualityFlow: MutableStateFlow<CaptureQuality>? = null
    ): CaptureResult = withContext(Dispatchers.Main) {
        val provider = try {
            ProcessCameraProvider.getInstance(context).await()
        } catch (e: Exception) {
            return@withContext CaptureResult.error(RejectionReason.HARDWARE_UNAVAILABLE)
        }

        // #266 Cut 2: per-device motion-rejection thresholds. Unknown
        // models fall back to PpgDeviceProfiles.DEFAULT which preserves
        // pre-Cut-2 behaviour — no regression on the device set that
        // currently works. As calibration-log distribution data lands
        // (Cut 1 toggle), specific models can be added to
        // PpgDeviceProfiles.PROFILES.
        val deviceProfile = PpgDeviceProfiles.forDevice(Build.MODEL)

        val luminance = Collections.synchronizedList(mutableListOf<Double>())
        val executor = Executors.newSingleThreadExecutor()
        val frameCounter = java.util.concurrent.atomic.AtomicInteger(0)
        // Pin AE to TARGET_FPS so a dark scene + torch can't push the
        // sensor onto a long exposure and drop the frame rate to ~17 fps.
        // At 17 fps each frame is ~60 ms apart but a systolic upstroke is
        // ~80–120 ms, so peaks get sampled at random phase — amplitudes
        // swing wildly (peakAmpCov ≈ 1.6, observed on Pixel 9a) and the
        // adaptive-threshold detector misses alternating beats, producing
        // a half-rate HR. Forcing a faster AE keeps exposure short enough
        // that frame rate stays at TARGET_FPS; the torch is what lights
        // the finger anyway, AE has nothing useful to optimise here.
        val analysisBuilder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        Camera2Interop.Extender(analysisBuilder)
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(TARGET_FPS, TARGET_FPS),
            )
        val analysis = analysisBuilder
            .build()
            .also {
                it.setAnalyzer(executor) { image ->
                    luminance.add(yPlaneMean(image))
                    image.close()

                    val n = frameCounter.incrementAndGet()
                    if (qualityFlow != null && n % QUALITY_UPDATE_EVERY_N_FRAMES == 0) {
                        val window = synchronized(luminance) {
                            luminance.takeLast(QUALITY_WINDOW_FRAMES)
                        }
                        qualityFlow.value = CaptureQuality.classify(
                            window, ASSUMED_SAMPLING_HZ, deviceProfile,
                        )
                    }
                }
            }

        val preview = surfaceProvider?.let { sp ->
            Preview.Builder().build().also { it.setSurfaceProvider(sp) }
        }

        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val startMs = System.currentTimeMillis()
        val useCases: Array<UseCase> = listOfNotNull(analysis, preview).toTypedArray()

        try {
            val camera = provider.bindToLifecycle(lifecycleOwner, selector, *useCases)
            if (camera.cameraInfo.hasFlashUnit()) {
                camera.cameraControl.enableTorch(true)
            }
            delay(durationSec * 1000L)
        } catch (e: Exception) {
            return@withContext CaptureResult.error(RejectionReason.HARDWARE_UNAVAILABLE)
        } finally {
            provider.unbindAll()
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }

        val actualDurationSec = (System.currentTimeMillis() - startMs) / 1000.0
        val snapshot = synchronized(luminance) { luminance.toList() }
        if (actualDurationSec <= 0.0 || snapshot.isEmpty()) {
            return@withContext CaptureResult.error(RejectionReason.HARDWARE_UNAVAILABLE)
        }

        val samplingRateHz = snapshot.size / actualDurationSec
        val ppg = PpgSignalProcessor.extract(snapshot, samplingRateHz, deviceProfile)
        val hrv = if (ppg.accepted) HrvAnalyzer.analyze(ppg.rrIntervalsMs) else null

        if (PpgCalibrationLogger.isEnabled(context)) {
            // Diagnostic log opted in by the owner. Computed here (not inside
            // the processor) because saturationRatio is the only field we
            // can derive without re-running peak detection, and the
            // median-jump distribution is intrinsically a property of the
            // raw luminance series.
            val row = PpgCalibrationLogger.rowFor(
                timestampMs = System.currentTimeMillis(),
                samplingRateHz = samplingRateHz,
                jumpStats = CaptureQuality.medianJumpPercentiles(snapshot, samplingRateHz),
                peakAmplitudeCov = ppg.peakAmplitudeCov,
                saturationRatio = PpgSignalProcessor.saturationRatio(snapshot),
                peakCount = ppg.peakCount,
                durationSec = actualDurationSec,
                rejectionReason = ppg.rejectionReason,
            )
            withContext(Dispatchers.IO) { PpgCalibrationLogger(context).log(row) }
        }

        toResult(ppg, hrv, sourceId, System.currentTimeMillis())
    }

    companion object {
        /** AE target frame rate (fps). Pinned via [Camera2Interop] on the
         *  ImageAnalysis use case so a dark scene + torch can't push the
         *  sensor onto a long exposure and drop the effective rate to the
         *  point where systolic upstrokes alias between frames. 30 is a
         *  range every CameraX-supported device advertises; the HAL may
         *  still round to the closest supported pair if (30, 30) is
         *  unsupported on a given device. */
        private const val TARGET_FPS = 30

        /** Approximate analyzer frame rate, used by the real-time quality
         *  classifier. Actual rate is measured offline; this is just the
         *  scale for the motion-window heuristic. */
        private const val ASSUMED_SAMPLING_HZ = 20.0

        /** Push a quality update every N frames (~2 Hz at 20 fps). */
        private const val QUALITY_UPDATE_EVERY_N_FRAMES = 10

        /** Window of recent frames the classifier inspects (~2 sec). Sized
         *  above [CaptureQuality.MIN_FRAMES_FOR_CLASSIFICATION] with margin
         *  so the median-diff motion check has enough samples to stabilise. */
        private const val QUALITY_WINDOW_FRAMES = 40

        /**
         * Pure mapping from processor + HRV output to a [CaptureResult]. On
         * the companion so tests can exercise it without instantiating the
         * adapter (which would require an Android Context).
         */
        internal fun toResult(
            ppg: PpgResult,
            hrv: HrvAnalyzer.HrvResult?,
            sourceId: String,
            timestamp: Long
        ): CaptureResult {
            if (!ppg.accepted) {
                return CaptureResult(
                    readings = emptyList(),
                    rejectionReason = ppg.rejectionReason,
                    sqiScore = ppg.sqiScore,
                    peakCount = ppg.peakCount,
                    durationSec = ppg.durationSec
                )
            }

            if (hrv == null) {
                // Peaks passed SQI but HRV analyzer discarded too many artifacts.
                return CaptureResult(
                    readings = emptyList(),
                    rejectionReason = RejectionReason.IRREGULAR_RHYTHM,
                    sqiScore = ppg.sqiScore,
                    peakCount = ppg.peakCount,
                    durationSec = ppg.durationSec
                )
            }

            val durSec = ppg.durationSec.toInt().coerceAtLeast(1)
            // PPG-derived AFib screening burden (#180, cardiology audit §2.1).
            // Per-session verdict 0.0 (regular) / 100.0 (irregularly irregular)
            // — the pattern's rolling median over 7 days decides whether to fire.
            // INSUFFICIENT_DATA windows are skipped so the burden estimate
            // isn't biased by short captures.
            val rhythmVerdict = RhythmClassifier.classify(ppg.rrIntervalsMs)
            val burdenReading = if (rhythmVerdict.verdict == RhythmClassifier.WindowVerdict.INSUFFICIENT_DATA) {
                null
            } else {
                MetricReading(
                    metricType = MetricType.IRREGULAR_RHYTHM_BURDEN.key,
                    value = if (rhythmVerdict.irregular) 100.0 else 0.0,
                    timestamp = timestamp,
                    durationSec = durSec,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.LOW.level
                )
            }
            // PVC-burden estimate (#216 / Triage #26). Skipped on rhythms
            // the classifier scores irregularly-irregular — AFib's
            // randomness inflates the short-long pair count and would
            // confound the PVC interpretation.
            val ectopyReading = if (rhythmVerdict.verdict != RhythmClassifier.WindowVerdict.REGULAR) {
                null
            } else {
                EctopyDetector.analyse(ppg.rrIntervalsMs)?.let { result ->
                    MetricReading(
                        metricType = MetricType.ECTOPY_BURDEN_ESTIMATE.key,
                        value = result.burdenPercent,
                        timestamp = timestamp,
                        durationSec = durSec,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.LOW.level,
                    )
                }
            }
            val readings = listOfNotNull(
                MetricReading(
                    metricType = MetricType.HEART_RATE.key,
                    value = hrv.meanHrBpm,
                    timestamp = timestamp,
                    durationSec = durSec,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.LOW.level
                ),
                MetricReading(
                    metricType = MetricType.HEART_RATE_VARIABILITY.key,
                    value = hrv.rmssd,
                    timestamp = timestamp,
                    durationSec = durSec,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.LOW.level
                ),
                MetricReading(
                    metricType = MetricType.PARASYMPATHETIC_TONE.key,
                    value = hrv.lnRmssd,
                    timestamp = timestamp,
                    durationSec = durSec,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.LOW.level
                ),
                MetricReading(
                    metricType = MetricType.STRESS_SCORE.key,
                    value = hrv.stressIndex,
                    timestamp = timestamp,
                    durationSec = durSec,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.LOW.level
                ),
                MetricReading(
                    metricType = MetricType.LF_HF_RATIO.key,
                    value = hrv.lfHfRatio,
                    timestamp = timestamp,
                    durationSec = durSec,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.LOW.level
                ),
                MetricReading(
                    metricType = MetricType.HRV_LF_POWER.key,
                    value = hrv.lfPowerMs2,
                    timestamp = timestamp,
                    durationSec = durSec,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.LOW.level
                ),
                MetricReading(
                    metricType = MetricType.HRV_HF_POWER.key,
                    value = hrv.hfPowerMs2,
                    timestamp = timestamp,
                    durationSec = durSec,
                    sourceId = sourceId,
                    confidence = ConfidenceTier.LOW.level
                ),
                burdenReading,
                ectopyReading,
                // PPG pulse-wave morphology (#181, CARDIOLOGY_POV.md §2.2).
                // Statistical summaries that PpgSignalProcessor surfaces
                // alongside HRV. Written only when waveformFeatures is
                // populated (rejected recordings + sessions with too few
                // clean beats skip every key in this block).
                ppg.waveformFeatures?.let { feat ->
                    MetricReading(
                        metricType = MetricType.PPG_PEAK_AMPLITUDE_MEAN.key,
                        value = feat.peakAmplitudeTrimmedMean,
                        timestamp = timestamp,
                        durationSec = durSec,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.LOW.level,
                    )
                },
                ppg.waveformFeatures?.let { feat ->
                    MetricReading(
                        metricType = MetricType.PPG_PEAK_AMPLITUDE_COV.key,
                        value = feat.peakAmplitudeCov,
                        timestamp = timestamp,
                        durationSec = durSec,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.LOW.level,
                    )
                },
                ppg.waveformFeatures?.let { feat ->
                    MetricReading(
                        metricType = MetricType.PPG_RISE_TIME_MEAN.key,
                        value = feat.riseTimeMeanSec,
                        timestamp = timestamp,
                        durationSec = durSec,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.LOW.level,
                    )
                },
                ppg.waveformFeatures?.let { feat ->
                    MetricReading(
                        metricType = MetricType.PPG_RISE_TIME_COV.key,
                        value = feat.riseTimeCov,
                        timestamp = timestamp,
                        durationSec = durSec,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.LOW.level,
                    )
                },
                ppg.waveformFeatures?.let { feat ->
                    MetricReading(
                        metricType = MetricType.PPG_DECAY_ASYMMETRY_INDEX.key,
                        value = feat.decayAsymmetryIndex,
                        timestamp = timestamp,
                        durationSec = durSec,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.LOW.level,
                    )
                },
                // Dichrotic-notch position is nullable in PulseWaveformFeatures
                // — only surfaced when enough beats yield a detectable notch.
                ppg.waveformFeatures?.dichroticNotchRelativePosition?.let { pos ->
                    MetricReading(
                        metricType = MetricType.PPG_DICHROTIC_NOTCH_POSITION.key,
                        value = pos,
                        timestamp = timestamp,
                        durationSec = durSec,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.LOW.level,
                    )
                },
                // PPG-derived augmentation index (Blueprint audit §3.1 #8).
                // Computed when a diastolic rebound is detectable on enough
                // beats; null on short or noisy recordings.
                ppg.waveformFeatures?.augmentationIndexPercent?.let { aix ->
                    MetricReading(
                        metricType = MetricType.AUGMENTATION_INDEX_PPG.key,
                        value = aix,
                        timestamp = timestamp,
                        durationSec = durSec,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.LOW.level,
                    )
                },
            )
            return CaptureResult(
                readings = readings,
                rejectionReason = null,
                sqiScore = ppg.sqiScore,
                peakCount = ppg.peakCount,
                durationSec = ppg.durationSec
            )
        }

        /**
         * Compute the mean Y (luminance) value of an [ImageProxy] in
         * YUV_420_888 format. Handles rowStride padding correctly.
         */
        fun yPlaneMean(image: ImageProxy): Double {
            val plane = image.planes[0]
            return yPlaneMean(
                buffer = plane.buffer,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                width = image.width,
                height = image.height
            )
        }

        /** Buffer-level variant, testable without an ImageProxy. */
        internal fun yPlaneMean(
            buffer: ByteBuffer,
            rowStride: Int,
            pixelStride: Int,
            width: Int,
            height: Int
        ): Double {
            if (width <= 0 || height <= 0) return 0.0
            val row = ByteArray(rowStride)
            var sum = 0L
            var count = 0
            for (y in 0 until height) {
                val rowStart = y * rowStride
                if (rowStart >= buffer.capacity()) break
                buffer.position(rowStart)
                val available = buffer.remaining().coerceAtMost(rowStride)
                buffer.get(row, 0, available)
                var x = 0
                val pixelsInRow = ((available - 1) / pixelStride + 1).coerceAtMost(width)
                while (x < pixelsInRow) {
                    sum += (row[x * pixelStride].toInt() and 0xff)
                    count++
                    x++
                }
            }
            return if (count == 0) 0.0 else sum.toDouble() / count
        }
    }
}

/** Outcome of a camera-PPG capture, surfaced directly to the UI. */
data class CaptureResult(
    /** HR + HRV readings (empty when rejected). */
    val readings: List<MetricReading>,
    /** Non-null when the snapshot was rejected. */
    val rejectionReason: RejectionReason?,
    /** 0–100 composite signal quality; 0 when rejected. */
    val sqiScore: Int,
    /** Detected peaks (informational, even on rejection). */
    val peakCount: Int,
    /** Recording length actually observed. */
    val durationSec: Double
) {
    val accepted: Boolean get() = rejectionReason == null

    companion object {
        fun error(reason: RejectionReason) = CaptureResult(
            readings = emptyList(),
            rejectionReason = reason,
            sqiScore = 0,
            peakCount = 0,
            durationSec = 0.0
        )
    }
}
