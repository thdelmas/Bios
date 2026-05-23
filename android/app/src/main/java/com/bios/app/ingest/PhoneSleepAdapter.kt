package com.bios.app.ingest

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
    // #243 Cut 1: read-only access to the corroborator signals. Each
    // accessor is null-safe — adapters on devices that lack the
    // service (rare) or with permissions revoked emit a null on the
    // matching Sample field and the inference ignores it.
    private val notificationManager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val stepDelta = StepCounterDelta(context)

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val stepCounter: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

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
            charging = isPluggedIn(),
            ambientLightLux = readOnce(lightSensor, AMBIENT_LIGHT_TIMEOUT_MS),
            accelMagnitudeVar = sampleAccelVariance(accelWindowMs),
            stepDelta = readStepDelta(),
            dndEnabled = readDndEnabled(),
            pairedBluetoothConnected = readPairedBluetoothConnected(),
        )
    }

    /**
     * Step delta since the previous successful read on this device. Uses
     * the shared [StepCounterDelta] so the sampler shares its cumulative
     * checkpoint with [PhoneSensorAdapter] — reading the hardware
     * counter twice would double-count walking. Returns null when the
     * sensor is absent (most non-Pixel/-Samsung mid-tier phones) or
     * when the listener never fires within the timeout.
     */
    private suspend fun readStepDelta(): Int? {
        val sensor = stepCounter ?: return null
        // Single-shot read of the cumulative counter. The OS treats it
        // as a wake-up sensor on most devices, so the listener fires
        // almost immediately; STEP_COUNTER_TIMEOUT_MS is a guard against
        // silent failures (rare).
        val cumulative = readOnce(sensor, STEP_COUNTER_TIMEOUT_MS)?.toLong() ?: return null
        val delta = stepDelta.computeDelta(cumulative, sourceId = STEP_DELTA_SOURCE_ID)
            ?: return 0
        return delta.toInt()
    }

    /**
     * True when the owner has any non-ALL interruption filter — DND
     * scheduled, manually enabled, or in alarms-only / priority-only
     * mode. A strong sleep-intent corroborator that survives the AOD-
     * display-state ambiguity that confuses [isScreenInactive].
     */
    private fun readDndEnabled(): Boolean? {
        val manager = notificationManager ?: return null
        return manager.currentInterruptionFilter !=
            NotificationManager.INTERRUPTION_FILTER_ALL
    }

    /**
     * True when at least one paired Bluetooth peripheral is currently
     * connected on the HEADSET or A2DP profile. The two profiles cover
     * the home-life signal we care about: wearables, smart speakers,
     * sleep-headphones, car systems. Returns null when the device
     * lacks Bluetooth, has BT off, or the BLUETOOTH_CONNECT permission
     * is missing (the manifest declares it, but the OS may revoke).
     */
    private fun readPairedBluetoothConnected(): Boolean? {
        val manager = bluetoothManager ?: return null
        val adapter: BluetoothAdapter = manager.adapter ?: return null
        if (!adapter.isEnabled) return false
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        return try {
            val connected = manager.getConnectedDevices(BluetoothProfile.HEADSET) +
                manager.getConnectedDevices(BluetoothProfile.A2DP)
            connected.isNotEmpty()
        } catch (_: SecurityException) {
            null
        }
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
     * `BatteryManager.isCharging()` returns false the moment a topped-off
     * battery stops actively drawing current — and most phones reach 100%
     * within a few hours of plug-in, so a 6h "is charging" signal would
     * miss the entire second half of the night. The right "phone is on a
     * nightstand dock" signal is plug state, not current draw.
     *
     * Reads the sticky [Intent.ACTION_BATTERY_CHANGED] (null receiver
     * returns the cached value, no broadcast registration cost) and treats
     * any non-zero plugged source (AC / USB / wireless / dock) as plugged.
     */
    private fun isPluggedIn(): Boolean {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return false
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return plugged != 0
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
        /** Wake-up step-counter listener timeout; matches the ambient-
         *  light read pattern. Single-shot read of the cumulative
         *  counter. */
        private const val STEP_COUNTER_TIMEOUT_MS = 1_500L
        /** Source id for the shared [StepCounterDelta] checkpoint. Kept
         *  distinct from PhoneSensorAdapter's so the two readers don't
         *  cannibalise each other's last-seen cumulative across
         *  back-to-back fires. */
        private const val STEP_DELTA_SOURCE_ID = "phone_sleep_step_delta"
    }
}
