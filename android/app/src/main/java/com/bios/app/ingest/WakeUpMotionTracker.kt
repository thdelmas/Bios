package com.bios.app.ingest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.util.Log

/**
 * Process-scoped tracker for the two Tier-1 wake-up motion sensors used
 * by the phone-only sleep inference (#243 Cut 2):
 *
 * - [Sensor.TYPE_SIGNIFICANT_MOTION] — fires once when the device has
 *   moved significantly. The strongest single onset/offset hint we are
 *   not yet using; near-zero standby cost because it runs in the sensor
 *   hub.
 * - [Sensor.TYPE_STATIONARY_DETECT] — fires once when the device has
 *   been still for a few seconds. Hardware-confirmed stillness — much
 *   stronger than the accelerometer-variance proxy, and crucially
 *   bypasses the AOD-display-state ambiguity that confuses the existing
 *   `isQuietSample` fallback on always-on-display phones.
 *
 * Both sensors are documented one-shot trigger sensors: each fire
 * automatically unregisters the listener, and the tracker re-registers
 * to keep the next state-transition observable. Sensor-hub batching
 * means the standing wattage of holding these listeners overnight is
 * effectively zero on modern Android hardware.
 *
 * ## Lifecycle
 *
 * `attach(context)` is idempotent and meant to be called from
 * [com.bios.app.BiosApplication.onCreate] so listeners arm at app start
 * — the periodic worker creates short-lived adapters that wouldn't
 * otherwise give the sensors time to fire. Once attached, the tracker
 * lives for the process lifetime.
 *
 * `snapshot()` reads the most-recently-recorded fire timestamps and
 * returns the current state. Both fields are null when the sensor is
 * absent on the device or when neither has fired since attach.
 */
object WakeUpMotionTracker {

    private const val TAG = "WakeUpMotionTracker"

    @Volatile private var attached: Boolean = false
    @Volatile private var sensorManager: SensorManager? = null
    @Volatile private var sigMotionSensor: Sensor? = null
    @Volatile private var stationarySensor: Sensor? = null

    // System-clock millis of the most recent fire of each sensor.
    // 0L means the sensor has never fired since attach (or is absent).
    @Volatile private var lastSigMotionMs: Long = 0L
    @Volatile private var lastStationaryMs: Long = 0L

    private val sigMotionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            lastSigMotionMs = System.currentTimeMillis()
            // Re-arm: TriggerEvent sensors auto-unregister on each fire.
            val sm = sensorManager
            val sensor = sigMotionSensor
            if (sm != null && sensor != null) sm.requestTriggerSensor(this, sensor)
        }
    }

    private val stationaryListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lastStationaryMs = System.currentTimeMillis()
            // Re-arm: stationary-detect is documented one-shot; the OS
            // unregisters on fire and we must re-register to catch the
            // next stationary-onset transition.
            val sm = sensorManager
            val sensor = stationarySensor
            if (sm != null && sensor != null) {
                sm.unregisterListener(this)
                sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /**
     * Bind the tracker to the process-global [SensorManager] and arm
     * both wake-up listeners. Idempotent — subsequent calls no-op so
     * worker / service paths can call defensively. Safe on devices
     * missing either sensor: missing-sensor fields stay null and the
     * corresponding [snapshot] field remains null forever.
     */
    fun attach(context: Context) {
        if (attached) return
        synchronized(this) {
            if (attached) return
            val sm = context.applicationContext
                .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            if (sm == null) {
                Log.i(TAG, "no SensorManager — wake-up motion tracking disabled")
                attached = true
                return
            }
            sensorManager = sm
            sigMotionSensor = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
            stationarySensor = sm.getDefaultSensor(Sensor.TYPE_STATIONARY_DETECT)
            sigMotionSensor?.let { sm.requestTriggerSensor(sigMotionListener, it) }
            stationarySensor?.let {
                sm.registerListener(stationaryListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            Log.i(
                TAG,
                "attached — sigMotion=${sigMotionSensor != null} " +
                    "stationary=${stationarySensor != null}"
            )
            attached = true
        }
    }

    /** Result of [snapshot]. Both fields are null when the underlying
     *  sensor is absent on this device or when the tracker hasn't yet
     *  observed any fire (typical immediately after [attach]). */
    data class Snapshot(
        val significantMotionFired: Boolean?,
        val stationary: Boolean?,
    )

    /**
     * Read the current motion state. Semantics:
     *
     * - [Snapshot.significantMotionFired] is `true` when SIGNIFICANT_MOTION
     *   fired within [RECENT_FIRE_WINDOW_MS]; `false` when the sensor is
     *   present (and has fired at least once historically) but did not
     *   fire recently; `null` when the sensor is absent.
     * - [Snapshot.stationary] reflects the most-recent transition we
     *   captured: `true` when STATIONARY_DETECT fired more recently
     *   than SIGNIFICANT_MOTION; `false` when SIGNIFICANT_MOTION is
     *   the more recent of the two; `null` when neither has ever
     *   fired or the sensor is absent.
     */
    fun snapshot(nowMs: Long = System.currentTimeMillis()): Snapshot {
        val sm = sensorManager
        if (sm == null) return Snapshot(null, null)
        val sigPresent = sigMotionSensor != null
        val stationaryPresent = stationarySensor != null

        val sigFired = if (!sigPresent) {
            null
        } else if (lastSigMotionMs == 0L) {
            // Sensor armed but no historical fire — best we can say is
            // "no recent significant motion observed."
            false
        } else {
            (nowMs - lastSigMotionMs) < RECENT_FIRE_WINDOW_MS
        }

        val stationary: Boolean? = when {
            !stationaryPresent && !sigPresent -> null
            lastSigMotionMs == 0L && lastStationaryMs == 0L -> null
            else -> lastStationaryMs > lastSigMotionMs
        }

        return Snapshot(sigFired, stationary)
    }

    /** Testing-only reset. Lets unit tests recycle the singleton between
     *  cases. NOT for production callers. */
    internal fun resetForTest() {
        synchronized(this) {
            sensorManager?.let { sm ->
                sigMotionSensor?.let { sm.cancelTriggerSensor(sigMotionListener, it) }
                stationarySensor?.let { sm.unregisterListener(stationaryListener, it) }
            }
            sensorManager = null
            sigMotionSensor = null
            stationarySensor = null
            lastSigMotionMs = 0L
            lastStationaryMs = 0L
            attached = false
        }
    }

    /** Recency window for [Snapshot.significantMotionFired]. A
     *  significant-motion fire within the last 10 minutes is "recent"
     *  enough to mark the current sample as motion-positive; older
     *  fires read as "no recent motion." The stationary signal carries
     *  the longer-horizon stillness information separately. */
    internal const val RECENT_FIRE_WINDOW_MS: Long = 10L * 60L * 1000L
}
