package com.bios.app.ingest

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
    // androidx.annotation.OptIn (not kotlin.OptIn) — the camera interop
    // marker is a Java @Experimental annotation, so lint's
    // UnsafeOptInUsageError only honors the androidx form.
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
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
        // Output RGBA so we can pull the red channel directly. Transmission-mode
        // PPG (torch behind finger) has the same physics as pulse oximetry:
        // red transmits best through tissue and carries the largest absolute AC
        // modulation. YUV Y dilutes that into a weighted RGB sum and shrinks
        // the AC component below the noise floor — observed on Pixel 9a, 73%
        // of windows fell below the FINGER_OFF variance floor under torch+
        // finger with AE locked and Y plane averaging.
        val analysisBuilder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        Camera2Interop.Extender(analysisBuilder)
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(TARGET_FPS, TARGET_FPS),
            )
            // Disable in-HAL noise reduction and edge enhancement — both
            // adaptively smooth the small (~1–3 Y unit) PPG AC component
            // as if it were sensor noise.
            .setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_OFF,
            )
            .setCaptureRequestOption(
                CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_OFF,
            )
        val analysis = analysisBuilder
            .build()
            .also {
                it.setAnalyzer(executor) { image ->
                    luminance.add(ImagePlaneStats.redChannelMean(image))
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
            // Let AE find a workable exposure for torch+finger, then lock
            // AE/AWB. Without the lock, AE treats the systolic luminance
            // oscillation (~1-3 Y units on Pixel 9a) as drift and regulates
            // it away — the windowed variance crashes below the FINGER_OFF
            // floor and the offline path rejects as MOTION_ARTIFACT because
            // the surviving peak amplitudes are wildly normalised.
            val totalMs = durationSec * 1000L
            val warmupMs = AE_WARMUP_MS.coerceAtMost(totalMs)
            delay(warmupMs)
            Camera2CameraControl.from(camera.cameraControl)
                .setCaptureRequestOptions(
                    CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                        .build()
                )
            delay(totalMs - warmupMs)
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
        val hrv = if (ppg.accepted) {
            HrvAnalyzer.analyze(ppg.rrIntervalsMs, deviceProfile.artifactRejection)
        } else null
        val meanY = snapshot.average()
        val varY = snapshot.let { s ->
            if (s.size < 2) 0.0 else s.sumOf { (it - meanY) * (it - meanY) } / s.size
        }
        val satRatio = PpgSignalProcessor.saturationRatio(snapshot)
        val hrvCleanIbis = hrv?.cleanIbiCount ?: 0
        val hrvArtifacts = hrv?.artifactsRejected ?: -1
        val hrvOutcome = when {
            !ppg.accepted -> "n/a"
            hrv == null -> "REJECTED_AT_HRV"
            else -> "OK hr=%.0f rmssd=%.0f".format(hrv.meanHrBpm, hrv.rmssd)
        }
        Log.d(
            LOG_TAG,
            "capture done device=${Build.MODEL} samples=${snapshot.size} fps=%.2f dur=%.2fs meanR=%.2f var=%.2f sat=%.3f reason=${ppg.rejectionReason} sqi=${ppg.sqiScore} peaks=${ppg.peakCount} peakAmpCov=%.3f hrv=$hrvOutcome cleanIbi=$hrvCleanIbis artifacts=$hrvArtifacts"
                .format(samplingRateHz, actualDurationSec, meanY, varY, satRatio, ppg.peakAmplitudeCov ?: -1.0),
        )

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
        private const val LOG_TAG = "BiosPpg"

        /** Minimum fraction of raw IBIs the Malik artifact filter must keep
         *  before HR is sourced from its cleaned mean rather than the raw
         *  peak rate. Empirically: ≥ 0.5 → Malik mean tracks a wrist wearable
         *  within 1–2 bpm; below that the sequential filter's anchoring bias
         *  pulls the mean IBI long and we get cleaner numbers from peak rate.
         *  See toResult() for the full rationale. */
        private const val MALIK_RELIABLE_FRACTION = 0.5

        /** Warm-up before locking AE/AWB. Long enough for the AE servo to
         *  converge on a flash+finger exposure (observed ≈2 s on Pixel 9a:
         *  windowed variance settles by frame ~60 at 30 fps). */
        private const val AE_WARMUP_MS = 2_000L

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
         * Pure mapping from processor + HRV output to a [CaptureResult]. Kept
         * here as a thin pass-through to [CameraPpgResultMapper] so existing
         * call sites (tests + ViewModel) don't need to change.
         */
        internal fun toResult(
            ppg: PpgResult,
            hrv: HrvAnalyzer.HrvResult?,
            sourceId: String,
            timestamp: Long
        ): CaptureResult = CameraPpgResultMapper.toResult(ppg, hrv, sourceId, timestamp)


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
