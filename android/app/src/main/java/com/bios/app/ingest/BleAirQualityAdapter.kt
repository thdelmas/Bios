package com.bios.app.ingest

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.MetricReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Runtime adapter for the Bluetooth-LE Environmental Sensing Service (ESS,
 * `0x181A`) — pairs an air-quality peripheral, then streams PM2.5 / CO2 /
 * VOC notifications through [EnvironmentalSensingParser] into the
 * [MetricReadingDao].
 *
 * Lifecycle:
 *  - The owner pairs a peripheral via Settings → Pair air-quality sensor.
 *  - On app start, if a device is paired, [connect] reopens the GATT
 *    connection and re-subscribes to the three characteristics.
 *  - When the activity dies (process killed, owner taps away), the GATT
 *    connection drops — by design (issue #119 "Out of scope" rejects a
 *    foreground service for v1). The connection comes back on next launch.
 *
 * Permissions are checked up-front: API 31+ uses `BLUETOOTH_SCAN` and
 * `BLUETOOTH_CONNECT`; older releases use the legacy `BLUETOOTH` /
 * `BLUETOOTH_ADMIN` install-time perms plus runtime `ACCESS_FINE_LOCATION`
 * (which the OS gates BLE scans on until Android 12). Missing permissions
 * make scans return empty and connections no-op rather than crashing —
 * the pair UI surfaces the gap to the owner.
 */
class BleAirQualityAdapter(
    private val context: Context,
    private val readingDao: MetricReadingDao,
    private val pairedStore: BlePairedDeviceStore,
) {

    /** True when at least one peripheral is paired in [pairedStore]. */
    val isPaired: Boolean
        get() = pairedStore.isPaired()

    /** The currently-paired device (address + display name), or null. */
    fun pairedDevice(): BlePairedDeviceStore.Paired? = pairedStore.get()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** Scan result handed to the pair UI on each fresh advertisement. */
    data class Discovered(val address: String, val name: String, val rssi: Int)

    private val bluetoothManager: BluetoothManager? =
        ContextCompat.getSystemService(context, BluetoothManager::class.java)

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private var connectedSourceId: String? = null
    private val pendingDescriptorWrites: ArrayDeque<BluetoothGattCharacteristic> = ArrayDeque()

    // Detached scope for the suspend DAO writes off the BLE callback thread.
    // Cancelled in [disconnect] so unpairing stops any in-flight writes.
    private var writerScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * True if BLE is usable right now — adapter present, enabled, and the
     * required runtime permissions are granted.
     */
    fun isReady(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled) return false
        return hasScanPermission() && hasConnectPermission()
    }

    fun hasScanPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        granted(Manifest.permission.BLUETOOTH_SCAN)
    } else {
        granted(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasConnectPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        granted(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        // Legacy BLUETOOTH / BLUETOOTH_ADMIN are install-time — the manifest
        // declares them, so they are always granted on API 28–30.
        true
    }

    /**
     * The runtime permissions the pairing UI must request from the OS. The
     * caller (Compose screen) hands this array to `rememberLauncher…`. Empty
     * on API 31+ if both perms are already granted, on older versions if the
     * single location perm is granted.
     */
    fun requiredRuntimePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun granted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    /**
     * Cold flow of devices advertising the ESS service. Cancels the
     * underlying BLE scan when the flow collector is cancelled.
     */
    @SuppressLint("MissingPermission")  // Guarded by hasScanPermission().
    fun scan(): Flow<Discovered> = callbackFlow {
        val scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null || !hasScanPermission()) {
            close()
            return@callbackFlow
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(ESS_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = result.scanRecord?.deviceName
                    ?: deviceNameOrNull(device)
                    ?: device.address
                trySend(Discovered(device.address, name, result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                close()
            }
        }

        scanner.startScan(listOf(filter), settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    /**
     * Persist this device as the paired peripheral. The GATT connection is
     * opened separately via [connect] — typically by `IngestManager` so the
     * `SourceType.BLE_PERIPHERAL` row exists before any reading lands.
     */
    fun pair(address: String, name: String) {
        pairedStore.save(address, name)
    }

    /** Drop the connection and forget the device. */
    fun unpair() {
        disconnect()
        pairedStore.clear()
    }

    /**
     * Connect (or reconnect) to the paired peripheral and subscribe to the
     * three ESS characteristics. No-op when nothing is paired or runtime
     * permissions are missing.
     */
    @SuppressLint("MissingPermission")  // Guarded by hasConnectPermission().
    fun connect(sourceId: String) {
        val paired = pairedStore.get() ?: return
        if (!hasConnectPermission()) return
        val adapter = bluetoothAdapter ?: return
        val device = runCatching { adapter.getRemoteDevice(paired.address) }.getOrNull()
            ?: return

        // Tear down any existing connection before starting a new one — the
        // owner may have re-paired between calls.
        disconnect()
        writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        connectedSourceId = sourceId
        gatt = device.connectGatt(context, /* autoConnect = */ true, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        connectedSourceId = null
        pendingDescriptorWrites.clear()
        _isConnected.value = false
        runCatching { writerScope.cancel() }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _isConnected.value = true
                    runCatching { g.discoverServices() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _isConnected.value = false
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service: BluetoothGattService = g.getService(ESS_SERVICE_UUID) ?: return

            // Queue characteristic subscriptions — descriptor writes can't run
            // concurrently on the Android BLE stack, so each waits for the
            // previous to ack via [onDescriptorWrite].
            for (uuid in listOf(PM25_UUID, CO2_UUID, VOC_UUID)) {
                val ch = service.getCharacteristic(uuid) ?: continue
                pendingDescriptorWrites.addLast(ch)
            }
            subscribeNext(g)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            subscribeNext(g)
        }

        // API ≤ 32 callback signature — still invoked by the framework on
        // older devices, so it's kept alongside the API 33+ variant below.
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleNotification(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(characteristic.uuid, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeNext(g: BluetoothGatt) {
        val ch = pendingDescriptorWrites.removeFirstOrNull() ?: return
        runCatching { g.setCharacteristicNotification(ch, true) }
        val descriptor = ch.getDescriptor(CCCD_UUID) ?: run {
            // No CCCD — move on to the next characteristic instead of stalling.
            subscribeNext(g)
            return
        }
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, enable)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = enable
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }
    }

    private fun handleNotification(charUuid: UUID, value: ByteArray?) {
        val bytes = value ?: return
        val sourceId = connectedSourceId ?: return
        val shortUuid = charUuid.toShort16() ?: return
        val reading = EnvironmentalSensingParser.parse(
            characteristicShortUuid = shortUuid,
            bytes = bytes,
            timestamp = System.currentTimeMillis(),
            sourceId = sourceId,
        ) ?: return
        persistAsync(reading)
    }

    private fun persistAsync(reading: MetricReading) {
        writerScope.launch {
            runCatching { readingDao.insert(reading) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun deviceNameOrNull(device: BluetoothDevice): String? =
        if (hasConnectPermission()) {
            runCatching { device.name }.getOrNull()
        } else null

    companion object {
        private fun u16(short: Int): UUID =
            UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", short))

        val ESS_SERVICE_UUID: UUID = u16(0x181A)
        val PM25_UUID: UUID = u16(EnvironmentalSensingParser.CHARACTERISTIC_PM25_SHORT)
        val CO2_UUID: UUID = u16(EnvironmentalSensingParser.CHARACTERISTIC_CO2_SHORT)
        val VOC_UUID: UUID = u16(EnvironmentalSensingParser.CHARACTERISTIC_VOC_SHORT)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Bluetooth Base UUID's LSB: 0x800000805F9B34FB as a signed long. */
        private const val BASE_UUID_LSB: Long = -0x7FFFFF7FA064CB05L

        /**
         * Return the 16-bit short form of a Bluetooth-SIG 128-bit UUID, or
         * `null` when the UUID is outside the SIG namespace. Matches UUIDs
         * of the form `0000xxxx-0000-1000-8000-00805f9b34fb`.
         */
        internal fun UUID.toShort16(): Int? {
            if (leastSignificantBits != BASE_UUID_LSB) return null
            val msb = mostSignificantBits
            // Low 32 bits of MSB must be 0x00001000 (i.e. "0000-1000").
            if ((msb and 0xFFFFFFFFL) != 0x1000L) return null
            // Top 16 bits of MSB must be 0 (the "0000" prefix).
            if ((msb ushr 48) != 0L) return null
            return ((msb ushr 32) and 0xFFFFL).toInt()
        }
    }
}
