package com.bios.app.ingest

import kotlin.math.sqrt

/**
 * Pure-logic rolling buffer for the wearable seizure detector
 * (#269 Cut 3b). Holds the most-recent [capacitySec] seconds of
 * accelerometer-norm-minus-gravity values at a fixed [sampleRateHz].
 * Extracted from [SeizureDetectionService] so the buffer / trim / FFT-
 * input slicing logic is exercisable on the JVM without an Android
 * Sensor stack.
 *
 * Magnitude convention matches [com.bios.app.engine.SeizureDetector]'s
 * input contract: `sqrt(x² + y² + z²) - g`, so DC gravity is already
 * removed and quiet seconds read near zero.
 */
internal class SeizureDetectionBuffer(
    val sampleRateHz: Double,
    capacitySec: Int,
) {

    private val capacity: Int = (sampleRateHz * capacitySec).toInt().coerceAtLeast(1)
    private val magnitudes = ArrayDeque<Double>(capacity)

    /** Epoch-ms of the oldest sample currently in the buffer, or null
     *  when [isEmpty]. */
    @Volatile var startTimestampMs: Long? = null
        private set

    fun isEmpty(): Boolean = magnitudes.isEmpty()
    fun size(): Int = magnitudes.size

    /**
     * Append one accelerometer sample. [nowMs] is the epoch-ms of this
     * sample. The buffer evicts the oldest sample when full; on first
     * insert it records [startTimestampMs].
     */
    @Synchronized
    fun append(x: Float, y: Float, z: Float, nowMs: Long) {
        val mag = sqrt((x * x + y * y + z * z).toDouble()) - GRAVITY_M_PER_S2
        if (magnitudes.isEmpty()) startTimestampMs = nowMs
        magnitudes.addLast(mag)
        if (magnitudes.size > capacity) {
            magnitudes.removeFirst()
            // Advance the timestamp by one sample period when we evict.
            startTimestampMs = startTimestampMs?.plus((1_000.0 / sampleRateHz).toLong())
        }
    }

    /**
     * Snapshot the current buffer contents as a defensive copy. The
     * detector consumes a `List<Double>` and reads it concurrently with
     * further appends; returning a copy avoids ConcurrentModification
     * pitfalls without taking a long lock.
     */
    @Synchronized
    fun snapshot(): List<Double> = magnitudes.toList()

    companion object {
        // Same convention as PhoneSensorAdapter and PhoneSleepAdapter.
        private const val GRAVITY_M_PER_S2 = 9.81
    }
}
