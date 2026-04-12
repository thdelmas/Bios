package com.bios.app.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.bios.app.data.BiosDatabase
import com.bios.app.model.DataSource
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Receives event broadcasts from Gadgetbridge's Intent API and responds
 * by pulling fresh data from the ContentProvider.
 *
 * Gadgetbridge does NOT broadcast raw sensor data to third-party apps.
 * Real-time HR is exposed as a BLE GATT Heart Rate Service (0x180D),
 * and historical data via ContentProvider. This receiver listens for
 * event intents and triggers data pulls at the right moments:
 *
 *   - BLUETOOTH_CONNECTED: a wearable just connected → pull recent data
 *   - DATABASE_EXPORT_SUCCESS: user/automation exported data → pull it
 *
 * Additionally, Bios can REQUEST a sync from Gadgetbridge:
 *   - ACTIVITY_SYNC command with optional dataTypesHex filter
 *
 * User must enable: Gadgetbridge → Settings → Intent API
 *
 * Docs: https://codeberg.org/Freeyourgadget/Gadgetbridge/wiki/Intent-API
 */
class GadgetbridgeReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received Gadgetbridge intent: ${intent.action}")

        when (intent.action) {
            ACTION_BLUETOOTH_CONNECTED -> onDeviceConnected(context, intent)
            ACTION_EXPORT_SUCCESS -> onExportSuccess(context)
            ACTION_EXPORT_FAIL -> Log.w(TAG, "Gadgetbridge database export failed")
            else -> Log.d(TAG, "Unhandled Gadgetbridge intent: ${intent.action}")
        }
    }

    /**
     * A wearable device just connected. Pull the last 2 hours of data
     * to catch up on anything that accumulated while disconnected.
     */
    private fun onDeviceConnected(context: Context, intent: Intent) {
        val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        Log.d(TAG, "Gadgetbridge device connected: $deviceAddress")

        // Request Gadgetbridge to sync the device's activity data
        requestActivitySync(context)

        // Pull recent data from the ContentProvider
        pullRecentData(context, hoursBack = 2)
    }

    /**
     * Gadgetbridge completed a database export. Pull data now while it's fresh.
     */
    private fun onExportSuccess(context: Context) {
        Log.d(TAG, "Gadgetbridge export completed, pulling fresh data")
        pullRecentData(context, hoursBack = 24)
    }

    private fun pullRecentData(context: Context, hoursBack: Int) {
        scope.launch {
            try {
                val db = BiosDatabase.getInstance(context)
                val adapter = GadgetbridgeAdapter(context)
                if (!adapter.isAvailable) {
                    Log.d(TAG, "Gadgetbridge ContentProvider not available")
                    return@launch
                }

                val end = Instant.now()
                val start = end.minus(hoursBack.toLong(), ChronoUnit.HOURS)

                // Ensure source exists
                val sourceDao = db.dataSourceDao()
                if (sourceDao.findByType(SourceType.GADGETBRIDGE.key) == null) {
                    sourceDao.insert(DataSource(
                        sourceType = SourceType.GADGETBRIDGE.key,
                        deviceName = "Gadgetbridge Device",
                        sensorType = SensorType.OPTICAL_HR.name
                    ))
                }
                val source = sourceDao.findByType(SourceType.GADGETBRIDGE.key)!!

                val readings = adapter.fetchReadings(start, end, source.id)
                if (readings.isNotEmpty()) {
                    db.metricReadingDao().insertAll(readings)
                    Log.d(TAG, "Pulled ${readings.size} readings from Gadgetbridge")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pull data from Gadgetbridge", e)
            }
        }
    }

    companion object {
        private const val TAG = "GadgetbridgeReceiver"

        // Event intents FROM Gadgetbridge
        private const val ACTION_BLUETOOTH_CONNECTED =
            "nodomain.freeyourgadget.gadgetbridge.BLUETOOTH_CONNECTED"
        private const val ACTION_EXPORT_SUCCESS =
            "nodomain.freeyourgadget.gadgetbridge.action.DATABASE_EXPORT_SUCCESS"
        private const val ACTION_EXPORT_FAIL =
            "nodomain.freeyourgadget.gadgetbridge.action.DATABASE_EXPORT_FAIL"

        // Command intents TO Gadgetbridge
        private const val ACTION_ACTIVITY_SYNC =
            "nodomain.freeyourgadget.gadgetbridge.command.ACTIVITY_SYNC"

        // Extras
        private const val EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS"
        private const val EXTRA_DATA_TYPES_HEX = "dataTypesHex"

        // RecordedDataTypes bitmask values
        private const val TYPE_ACTIVITY = "0x00000001"
        private const val TYPE_HEART_RATE = "0x00000080"
        private const val TYPE_SPO2 = "0x00000020"
        private const val TYPE_TEMPERATURE = "0x00000008"

        /** IntentFilter for dynamic registration in BiosApplication. */
        fun intentFilter(): IntentFilter = IntentFilter().apply {
            addAction(ACTION_BLUETOOTH_CONNECTED)
            addAction(ACTION_EXPORT_SUCCESS)
            addAction(ACTION_EXPORT_FAIL)
        }

        /**
         * Request Gadgetbridge to sync activity, HR, SpO2, and temperature
         * data from the connected device.
         */
        fun requestActivitySync(context: Context) {
            val intent = Intent(ACTION_ACTIVITY_SYNC).apply {
                setPackage("nodomain.freeyourgadget.gadgetbridge")
                // Bitmask: activity | HR | SpO2 | temperature
                val mask = 0x00000001 or 0x00000080 or 0x00000020 or 0x00000008
                putExtra(EXTRA_DATA_TYPES_HEX, "0x${Integer.toHexString(mask)}")
            }
            context.sendBroadcast(intent)
        }
    }
}
