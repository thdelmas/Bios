package com.bios.app.ingest

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Pure pixel-arithmetic helpers consumed by [CameraPpgAdapter] to reduce each
 * camera frame to a single scalar per session sample. Lives in its own file so
 * the adapter stays under the project's 500-line ceiling.
 *
 * Both helpers operate on the [ImageProxy.planes] buffer for a single plane,
 * are size-validated, and handle `rowStride` end-of-row padding correctly.
 * They never write to or close the image.
 */
internal object ImagePlaneStats {

    /**
     * Mean Y (luminance) of an [ImageProxy] in YUV_420_888 format. Used by
     * the legacy Y-plane PPG capture path and retained for tests.
     */
    fun yPlaneMean(image: ImageProxy): Double {
        val plane = image.planes[0]
        return yPlaneMean(
            buffer = plane.buffer,
            rowStride = plane.rowStride,
            pixelStride = plane.pixelStride,
            width = image.width,
            height = image.height,
        )
    }

    /** Buffer-level variant of [yPlaneMean], testable without an ImageProxy. */
    fun yPlaneMean(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
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

    /**
     * Mean of the R channel of an [ImageProxy] delivered in RGBA_8888. Pixel
     * layout is R G B A per pixel (4 bytes); `rowStride` may exceed `width*4`
     * with end-of-row padding.
     *
     * Red is chosen over green for the camera-PPG capture path: it is
     * transmission-mode PPG (white-LED torch behind the finger, light passes
     * through tissue to the lens), which is the same geometry as pulse
     * oximetry — red has the highest transmission and the largest absolute
     * AC modulation. Green dominates only in reflectance-mode PPG (smartwatch
     * sensors on skin).
     */
    fun redChannelMean(image: ImageProxy): Double {
        val plane = image.planes[0]
        return redChannelMean(
            buffer = plane.buffer,
            rowStride = plane.rowStride,
            pixelStride = plane.pixelStride.coerceAtLeast(4),
            width = image.width,
            height = image.height,
        )
    }

    /** Buffer-level variant of [redChannelMean], testable without an ImageProxy. */
    fun redChannelMean(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
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
            val pixelsInRow = (available / pixelStride).coerceAtMost(width)
            while (x < pixelsInRow) {
                sum += (row[x * pixelStride].toInt() and 0xff) // R is index 0
                count++
                x++
            }
        }
        return if (count == 0) 0.0 else sum.toDouble() / count
    }
}
