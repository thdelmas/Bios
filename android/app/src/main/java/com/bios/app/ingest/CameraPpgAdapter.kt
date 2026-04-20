package com.bios.app.ingest

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.lifecycle.LifecycleOwner
import com.bios.app.engine.HrvAnalyzer
import com.bios.app.engine.PpgResult
import com.bios.app.engine.PpgSignalProcessor
import com.bios.app.engine.RejectionReason
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
     */
    suspend fun capture(
        lifecycleOwner: LifecycleOwner,
        durationSec: Int,
        sourceId: String
    ): CaptureResult = withContext(Dispatchers.Main) {
        val provider = try {
            ProcessCameraProvider.getInstance(context).await()
        } catch (e: Exception) {
            return@withContext CaptureResult.error(RejectionReason.HARDWARE_UNAVAILABLE)
        }

        val luminance = Collections.synchronizedList(mutableListOf<Double>())
        val executor = Executors.newSingleThreadExecutor()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(executor) { image ->
                    luminance.add(yPlaneMean(image))
                    image.close()
                }
            }

        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val startMs = System.currentTimeMillis()

        try {
            val camera = provider.bindToLifecycle(lifecycleOwner, selector, analysis)
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
        val ppg = PpgSignalProcessor.extract(snapshot, samplingRateHz)
        val hrv = if (ppg.accepted) HrvAnalyzer.analyze(ppg.rrIntervalsMs) else null

        toResult(ppg, hrv, sourceId, System.currentTimeMillis())
    }

    companion object {
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
            val readings = listOf(
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
                )
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
