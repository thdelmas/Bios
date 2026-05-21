package com.bios.app.ingest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
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

    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val stepCounter: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    val hasAccelerometer: Boolean get() = accelerometer != null
    val hasGyroscope: Boolean get() = gyroscope != null
    val hasStepCounter: Boolean get() = stepCounter != null
    val hasAmbientLight: Boolean get() = lightSensor != null

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
     * Samples the ambient-light sensor once. Returns null when the sensor is
     * absent or the device is non-interactive (screen off / pocket); in those
     * states the lux reading would not reflect the owner's actual environment.
     */
    suspend fun sampleAmbientLight(sourceId: String): MetricReading? {
        val sensor = lightSensor ?: return null
        if (!powerManager.isInteractive) return null
        val lux = firstValue(sensor, AMBIENT_LIGHT_TIMEOUT_MS) ?: return null
        return MetricReading(
            metricType = MetricType.AMBIENT_LIGHT.key,
            value = lux.toDouble(),
            timestamp = System.currentTimeMillis(),
            sourceId = sourceId,
            confidence = ConfidenceTier.LOW.level
        )
    }

    /**
     * Registers a one-shot listener and resumes with the first event's values[0],
     * or null if the sensor doesn't fire within [timeoutMs].
     */
    private suspend fun firstValue(
        sensor: Sensor,
        timeoutMs: Long
    ): Float? = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<Float> { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    sensorManager.unregisterListener(this)
                    if (cont.isActive) cont.resume(event.values[0])
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
     *
     * Wrapped in a wall-clock timeout so a sensor that never fires (most
     * commonly: TYPE_STEP_COUNTER without ACTIVITY_RECOGNITION permission,
     * but also any silent sensor failure) can't hang the calling sync
     * forever. The timeout is twice the requested duration with a 5 s
     * floor — generous enough to absorb listener-registration latency
     * without blocking the periodic sync if the sensor is silent.
     */
    private suspend fun collectSamples(
        sensor: Sensor,
        durationMs: Long
    ): List<Triple<Float, Float, Float>> {
        val timeoutMs = (durationMs * 2L).coerceAtLeast(SAMPLE_TIMEOUT_FLOOR_MS)
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<List<Triple<Float, Float, Float>>> { cont ->
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
        } ?: emptyList()
    }

    companion object {
        private const val GRAVITY = 9.81f
        private const val ACTIVITY_THRESHOLD = 1.5 // m/s^2 above gravity
        private const val STEP_SAMPLE_MS = 500L
        private const val AMBIENT_LIGHT_TIMEOUT_MS = 1_000L
        /** Hard wall-clock cap for collectSamples so a silent sensor
         *  (e.g. TYPE_STEP_COUNTER without ACTIVITY_RECOGNITION) doesn't
         *  hang the calling sync. */
        private const val SAMPLE_TIMEOUT_FLOOR_MS = 5_000L
    }
}
