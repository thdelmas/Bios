package com.bios.app.ingest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.bios.app.engine.HrvAnalyzer
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads health-relevant data directly from Android sensor APIs,
 * bypassing Health Connect.
 *
 * This adapter is the primary data path on LETHE and degoogled devices
 * where Health Connect may not be available. It reads from:
 * - TYPE_HEART_RATE (optical HR sensor on wearables paired via Wear OS)
 * - TYPE_HEART_BEAT (inter-beat interval → HRV computation)
 * - TYPE_STEP_COUNTER (hardware pedometer)
 * - TYPE_ACCELEROMETER (activity intensity proxy)
 *
 * On devices without these sensors, methods return empty lists.
 */
class DirectSensorAdapter(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val heartRateSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    private val heartBeatSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_BEAT)

    private val stepCounter: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val hasHeartRate: Boolean get() = heartRateSensor != null
    val hasHeartBeat: Boolean get() = heartBeatSensor != null
    val hasStepCounter: Boolean get() = stepCounter != null
    val hasAnySensor: Boolean get() = hasHeartRate || hasHeartBeat || hasStepCounter

    /**
     * Sample heart rate for the given duration.
     * Returns the average HR reading over the sample window.
     */
    suspend fun sampleHeartRate(
        durationMs: Long,
        sourceId: String
    ): List<MetricReading> {
        val sensor = heartRateSensor ?: return emptyList()
        val samples = collectFloatSamples(sensor, durationMs)
        if (samples.isEmpty()) return emptyList()

        val avgHr = samples.average()
        if (avgHr < 30 || avgHr > 220) return emptyList() // Discard unreasonable values

        return listOf(
            MetricReading(
                metricType = MetricType.HEART_RATE.key,
                value = avgHr,
                timestamp = System.currentTimeMillis(),
                durationSec = (durationMs / 1000).toInt(),
                sourceId = sourceId,
                confidence = ConfidenceTier.MEDIUM.level
            )
        )
    }

    /**
     * Sample inter-beat intervals and compute HRV metrics.
     * Requires TYPE_HEART_BEAT sensor which reports individual heartbeat timestamps.
     *
     * Uses HrvAnalyzer (ported from hrv-analysis / HeartPy) for:
     * - Malik threshold artifact rejection
     * - RMSSD (primary), SDNN, pNN50 computation
     */
    suspend fun sampleHrv(
        durationMs: Long,
        sourceId: String
    ): List<MetricReading> {
        val sensor = heartBeatSensor ?: return emptyList()
        val beatTimestamps = collectTimestampSamples(sensor, durationMs)
        if (beatTimestamps.size < 3) return emptyList()

        // Compute inter-beat intervals (IBI) in milliseconds
        val ibis = beatTimestamps.zipWithNext { a, b -> b - a }
        val hrv = HrvAnalyzer.analyze(ibis) ?: return emptyList()

        val now = System.currentTimeMillis()
        val duration = (durationMs / 1000).toInt()

        return listOf(
            MetricReading(
                metricType = MetricType.HEART_RATE_VARIABILITY.key,
                value = hrv.rmssd,
                timestamp = now,
                durationSec = duration,
                sourceId = sourceId,
                confidence = ConfidenceTier.LOW.level
            )
        )
    }

    /**
     * Read cumulative step count from hardware pedometer.
     */
    suspend fun readSteps(sourceId: String): MetricReading? {
        val sensor = stepCounter ?: return null
        val samples = collectFloatSamples(sensor, STEP_SAMPLE_MS)
        if (samples.isEmpty()) return null

        return MetricReading(
            metricType = MetricType.STEPS.key,
            value = samples.last(),
            timestamp = System.currentTimeMillis(),
            sourceId = sourceId,
            confidence = ConfidenceTier.LOW.level
        )
    }

    private suspend fun collectFloatSamples(
        sensor: Sensor,
        durationMs: Long
    ): List<Double> = suspendCancellableCoroutine { cont ->
        val samples = mutableListOf<Double>()
        val startTime = System.currentTimeMillis()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                samples.add(event.values[0].toDouble())
                if (System.currentTimeMillis() - startTime >= durationMs) {
                    sensorManager.unregisterListener(this)
                    if (cont.isActive) cont.resume(samples)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
    }

    private suspend fun collectTimestampSamples(
        sensor: Sensor,
        durationMs: Long
    ): List<Double> = suspendCancellableCoroutine { cont ->
        val timestamps = mutableListOf<Double>()
        val startTime = System.currentTimeMillis()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // TYPE_HEART_BEAT reports confidence in values[0]; timestamp is event.timestamp (nanos)
                if (event.values[0] > 0) {
                    timestamps.add(event.timestamp / 1_000_000.0) // Convert nanos to millis
                }
                if (System.currentTimeMillis() - startTime >= durationMs) {
                    sensorManager.unregisterListener(this)
                    if (cont.isActive) cont.resume(timestamps)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
    }

    companion object {
        private const val STEP_SAMPLE_MS = 500L
    }
}
