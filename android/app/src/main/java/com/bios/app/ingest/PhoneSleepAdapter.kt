package com.bios.app.ingest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.view.Display
import com.bios.app.engine.PhoneSleepInference
import com.bios.app.model.MetricReading
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.sqrt

/**
 * Phone-only sleep derivation (issue #134). Fuses screen-off, charging,
 * ambient-light and accelerometer-variance samples over a nightly window
 * and hands the trace to [PhoneSleepInference], which produces the actual
 * `SLEEP_DURATION` + `SLEEP_STAGE` readings.
 *
 * The adapter exists separately from [PhoneSensorAdapter] because the
 * lifecycle is different: PhoneSensorAdapter does instantaneous reads on
 * each sync, while sleep inference needs a continuous low-rate trace
 * across the overnight window. Keeping the two apart also keeps the
 * scalar phone-sensor sync path (steps, ambient light snapshot, active
 * minutes) unchanged.
 *
 * The actual nightly scheduler (which calls [sample] periodically across
 * the window and invokes [infer] at the close of the window) is a follow-up
 * to issue #134: the orchestration belongs in the ingest layer alongside
 * a registered `PHONE_SENSOR_DERIVED` data source row, but IngestManager
 * is at its line-length cap so that wiring lands in a separate change.
 */
class PhoneSleepAdapter(private val context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    /**
     * Returns true when the device has the minimum sensor surface needed
     * to run the inference. Accelerometer is mandatory (movement bouts);
     * the others are signal boosters.
     */
    val isAvailable: Boolean get() = accelerometer != null

    /**
     * Collects one [PhoneSleepInference.Sample] by sampling the
     * accelerometer for [accelWindowMs] and reading current screen,
     * charging, and ambient-light state. Suitable for calling on a
     * periodic schedule (e.g. every minute) across the nightly window.
     */
    suspend fun sample(accelWindowMs: Long = DEFAULT_ACCEL_WINDOW_MS): PhoneSleepInference.Sample {
        val now = System.currentTimeMillis()
        return PhoneSleepInference.Sample(
            timestamp = now,
            screenOff = isScreenInactive(),
            charging = batteryManager.isCharging,
            ambientLightLux = readOnce(lightSensor, AMBIENT_LIGHT_TIMEOUT_MS),
            accelMagnitudeVar = sampleAccelVariance(accelWindowMs),
        )
    }

    /**
     * Phones with always-on display keep `PowerManager.isInteractive()`
     * true through the night, which collapsed the inference's screen-off
     * stretch to a few minutes between unlocks. Read the actual display
     * state instead: OFF / DOZE / DOZE_SUSPEND / ON_SUSPEND are all
     * "owner isn't actively using the device" — only [Display.STATE_ON]
     * (and the unknown fallback) count as truly interactive.
     */
    private fun isScreenInactive(): Boolean {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
        return when (display.state) {
            Display.STATE_OFF,
            Display.STATE_DOZE,
            Display.STATE_DOZE_SUSPEND,
            Display.STATE_ON_SUSPEND -> true
            else -> false
        }
    }

    /**
     * Hand the collected samples to the pure inference layer and emit
     * `SLEEP_DURATION` (+ optional `SLEEP_STAGE`) readings.
     */
    fun infer(samples: List<PhoneSleepInference.Sample>, sourceId: String): List<MetricReading> =
        PhoneSleepInference.infer(samples, sourceId)

    private suspend fun sampleAccelVariance(durationMs: Long): Float? {
        val sensor = accelerometer ?: return null
        val magnitudes = mutableListOf<Float>()
        suspendCancellableCoroutine<Unit> { cont ->
            val listener = object : SensorEventListener {
                val start = System.currentTimeMillis()
                override fun onSensorChanged(event: SensorEvent) {
                    val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                    magnitudes += sqrt(x * x + y * y + z * z) - GRAVITY
                    if (System.currentTimeMillis() - start >= durationMs) {
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resumeWith(Result.success(Unit))
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
        }
        if (magnitudes.size < 2) return null
        val mean = magnitudes.average().toFloat()
        var sq = 0.0
        for (m in magnitudes) { val d = m - mean; sq += d * d }
        return (sq / magnitudes.size).toFloat()
    }

    private suspend fun readOnce(sensor: Sensor?, timeoutMs: Long): Float? {
        if (sensor == null) return null
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Float> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resumeWith(Result.success(event.values[0]))
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
            }
        }
    }

    companion object {
        private const val GRAVITY = 9.81f
        private const val DEFAULT_ACCEL_WINDOW_MS = 5_000L
        private const val AMBIENT_LIGHT_TIMEOUT_MS = 1_000L
    }
}
