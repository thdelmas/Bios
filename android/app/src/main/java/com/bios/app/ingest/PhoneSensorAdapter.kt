package com.bios.app.ingest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Reads raw accelerometer and gyroscope data from the phone's built-in sensors.
 * Derives step-like activity metrics from accelerometer magnitude.
 *
 * Camera PPG and microphone adapters are planned as separate classes
 * due to their distinct permission and lifecycle requirements.
 */
class PhoneSensorAdapter(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val stepCounter: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val hasAccelerometer: Boolean get() = accelerometer != null
    val hasGyroscope: Boolean get() = gyroscope != null
    val hasStepCounter: Boolean get() = stepCounter != null

    /**
     * Collects accelerometer samples for [durationMs] and returns derived metrics.
     * Computes average magnitude and movement intensity as a proxy for activity level.
     */
    suspend fun sampleAccelerometer(
        durationMs: Long,
        sourceId: String
    ): List<MetricReading> {
        val sensor = accelerometer ?: return emptyList()
        val samples = collectSamples(sensor, durationMs)
        if (samples.isEmpty()) return emptyList()

        val timestamp = System.currentTimeMillis()
        val durationSec = (durationMs / 1000).toInt()

        // Compute magnitude of acceleration vectors (minus gravity ~9.81)
        val magnitudes = samples.map { (x, y, z) ->
            sqrt(x * x + y * y + z * z) - GRAVITY
        }

        val avgMovement = magnitudes.map { kotlin.math.abs(it) }.average()
        val peakMovement = magnitudes.maxOf { kotlin.math.abs(it) }

        val readings = mutableListOf<MetricReading>()

        // Activity intensity as active minutes proxy (high movement = active)
        if (avgMovement > ACTIVITY_THRESHOLD) {
            readings += MetricReading(
                metricType = MetricType.ACTIVE_MINUTES.key,
                value = durationSec.toDouble(),
                timestamp = timestamp,
                durationSec = durationSec,
                sourceId = sourceId,
                confidence = ConfidenceTier.LOW.level
            )
        }

        return readings
    }

    /**
     * Reads the hardware step counter and returns accumulated steps.
     * The step counter is cumulative since boot, so we return the raw value
     * and let IngestManager handle delta computation.
     */
    suspend fun readStepCounter(sourceId: String): MetricReading? {
        val sensor = stepCounter ?: return null
        val samples = collectSamples(sensor, STEP_SAMPLE_MS)
        if (samples.isEmpty()) return null

        val stepCount = samples.last().first // step counter uses values[0]
        return MetricReading(
            metricType = MetricType.STEPS.key,
            value = stepCount.toDouble(),
            timestamp = System.currentTimeMillis(),
            sourceId = sourceId,
            confidence = ConfidenceTier.LOW.level
        )
    }

    /**
     * Emits accelerometer readings as a continuous flow for real-time monitoring.
     */
    fun accelerometerFlow(): Flow<Triple<Float, Float, Float>> = callbackFlow {
        val sensor = accelerometer ?: run {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(Triple(event.values[0], event.values[1], event.values[2]))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener, sensor, SensorManager.SENSOR_DELAY_NORMAL
        )

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Collects sensor samples for the given duration and returns them.
     */
    private suspend fun collectSamples(
        sensor: Sensor,
        durationMs: Long
    ): List<Triple<Float, Float, Float>> = suspendCancellableCoroutine { cont ->
        val samples = mutableListOf<Triple<Float, Float, Float>>()
        val startTime = System.currentTimeMillis()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                samples.add(Triple(event.values[0], event.values[1], event.values[2]))
                if (System.currentTimeMillis() - startTime >= durationMs) {
                    sensorManager.unregisterListener(this)
                    if (cont.isActive) cont.resume(samples)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener, sensor, SensorManager.SENSOR_DELAY_NORMAL
        )

        cont.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
        }
    }

    companion object {
        private const val GRAVITY = 9.81f
        private const val ACTIVITY_THRESHOLD = 1.5 // m/s^2 above gravity
        private const val STEP_SAMPLE_MS = 500L
    }
}
