package com.bios.app

import com.bios.app.engine.HrvAnalyzer
import com.bios.app.engine.PpgResult
import com.bios.app.engine.RejectionReason
import com.bios.app.ingest.CameraPpgAdapter
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricType
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Unit tests for the pure mapping helpers in [CameraPpgAdapter]. The CameraX
 * capture loop itself is exercised by on-device paired-reading validation
 * (see docs/CAMERA_PPG.md once PR 5 lands).
 */
class CameraPpgAdapterTest {

    private val sourceId = "test-src"
    private val now = 1_700_000_000_000L

    // -- toResult mapping --

    @Test
    fun `accepted PPG with valid HRV produces HR and HRV readings`() {
        val ppg = PpgResult(
            rrIntervalsMs = listOf(820.0, 810.0, 830.0, 800.0, 815.0),
            sqiScore = 78,
            rejectionReason = null,
            peakCount = 60,
            durationSec = 60.0
        )
        val hrv = HrvAnalyzer.HrvResult(
            rmssd = 18.5,
            sdnn = 24.0,
            pnn50 = 10.0,
            meanIbiMs = 815.0,
            meanHrBpm = 73.6,
            cleanIbiCount = 5,
            artifactsRejected = 0
        )

        val result = CameraPpgAdapter.toResult(ppg, hrv, sourceId, now)

        assertTrue(result.accepted)
        assertEquals(2, result.readings.size)

        val hr = result.readings.single { it.metricType == MetricType.HEART_RATE.key }
        assertEquals(73.6, hr.value, 0.01)
        assertEquals(sourceId, hr.sourceId)
        assertEquals(ConfidenceTier.LOW.level, hr.confidence)
        assertEquals(60, hr.durationSec)
        assertEquals(now, hr.timestamp)

        val hrvR = result.readings.single { it.metricType == MetricType.HEART_RATE_VARIABILITY.key }
        assertEquals(18.5, hrvR.value, 0.01)
        assertEquals(ConfidenceTier.LOW.level, hrvR.confidence)
    }

    @Test
    fun `rejected PPG produces no readings and propagates reason`() {
        val ppg = PpgResult.rejected(
            reason = RejectionReason.MOTION_ARTIFACT,
            durationSec = 60.0,
            peakCount = 40,
            sqiScore = 28
        )

        val result = CameraPpgAdapter.toResult(ppg, hrv = null, sourceId = sourceId, timestamp = now)

        assertFalse(result.accepted)
        assertTrue(result.readings.isEmpty())
        assertEquals(RejectionReason.MOTION_ARTIFACT, result.rejectionReason)
        assertEquals(28, result.sqiScore)
        assertEquals(40, result.peakCount)
    }

    @Test
    fun `accepted PPG but null HRV downgrades to IRREGULAR_RHYTHM`() {
        val ppg = PpgResult(
            rrIntervalsMs = listOf(820.0),  // too few clean for HrvAnalyzer
            sqiScore = 70,
            rejectionReason = null,
            peakCount = 5,
            durationSec = 60.0
        )

        val result = CameraPpgAdapter.toResult(ppg, hrv = null, sourceId = sourceId, timestamp = now)

        assertFalse(result.accepted)
        assertEquals(RejectionReason.IRREGULAR_RHYTHM, result.rejectionReason)
        assertTrue(result.readings.isEmpty())
    }

    @Test
    fun `readings carry the timestamp and sourceId the caller supplied`() {
        val ppg = PpgResult(
            rrIntervalsMs = listOf(800.0, 810.0, 820.0, 790.0, 805.0),
            sqiScore = 80,
            rejectionReason = null,
            peakCount = 50,
            durationSec = 45.0
        )
        val hrv = HrvAnalyzer.HrvResult(
            rmssd = 15.0, sdnn = 22.0, pnn50 = 5.0,
            meanIbiMs = 805.0, meanHrBpm = 74.5,
            cleanIbiCount = 5, artifactsRejected = 0
        )

        val result = CameraPpgAdapter.toResult(ppg, hrv, sourceId = "src-xyz", timestamp = 42L)

        result.readings.forEach {
            assertEquals("src-xyz", it.sourceId)
            assertEquals(42L, it.timestamp)
            assertEquals(45, it.durationSec)
        }
    }

    // -- Y-plane luminance extraction --

    @Test
    fun `yPlaneMean averages every pixel when pixelStride is 1 and no row padding`() {
        // 4x2 image, pixelStride=1, rowStride=4 (no padding). Values: 100, 150, 50, 200 per row.
        val buf = ByteBuffer.allocate(8)
        byteArrayOf(100, (150).toByte(), 50, 200.toByte(),
            100, (150).toByte(), 50, 200.toByte()).forEach { buf.put(it) }
        buf.rewind()

        val mean = CameraPpgAdapter.yPlaneMean(
            buffer = buf,
            rowStride = 4,
            pixelStride = 1,
            width = 4,
            height = 2
        )
        assertEquals((100 + 150 + 50 + 200) / 4.0, mean, 0.01)
    }

    @Test
    fun `yPlaneMean skips row padding when rowStride greater than width`() {
        // 2x2 image, pixelStride=1, rowStride=4 (2 bytes padding per row).
        // Only the first 2 bytes of each row are real pixels.
        val buf = ByteBuffer.allocate(8)
        byteArrayOf(100, 100, 0, 0, 200.toByte(), 200.toByte(), 0, 0).forEach { buf.put(it) }
        buf.rewind()

        val mean = CameraPpgAdapter.yPlaneMean(
            buffer = buf,
            rowStride = 4,
            pixelStride = 1,
            width = 2,
            height = 2
        )
        // Real pixels: 100, 100, 200, 200 → mean 150, not 75 (which we'd get including padding).
        assertEquals(150.0, mean, 0.01)
    }

    @Test
    fun `yPlaneMean treats bytes as unsigned`() {
        // 0xFF = 255 in unsigned, -1 as signed byte. Correctness requires unsigned reading.
        val buf = ByteBuffer.allocate(2)
        buf.put(0xFF.toByte()); buf.put(0xFF.toByte()); buf.rewind()

        val mean = CameraPpgAdapter.yPlaneMean(
            buffer = buf,
            rowStride = 2,
            pixelStride = 1,
            width = 2,
            height = 1
        )
        assertEquals(255.0, mean, 0.01)
    }

    @Test
    fun `yPlaneMean returns 0 for zero-sized image`() {
        val buf = ByteBuffer.allocate(4)
        val mean = CameraPpgAdapter.yPlaneMean(
            buffer = buf,
            rowStride = 0,
            pixelStride = 1,
            width = 0,
            height = 0
        )
        assertEquals(0.0, mean, 0.01)
    }
}
