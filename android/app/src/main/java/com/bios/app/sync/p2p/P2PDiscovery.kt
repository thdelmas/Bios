package com.bios.app.sync.p2p

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device discovery and pairing for P2P sync via Iroh/Willow.
 *
 * Two discovery mechanisms:
 * 1. Local network (mDNS/DNS-SD): discovers Bios peers on the same WiFi
 * 2. Iroh relay: cross-network sync via Iroh's public relay infrastructure
 *
 * Pairing flow:
 * 1. Device A creates a shared document and generates a share ticket
 * 2. Owner transfers the ticket to Device B (QR code, manual entry)
 * 3. Device B calls acceptPairing(ticket)
 * 4. Both devices now share a Willow namespace and can delta-sync
 *
 * No data leaves the device during discovery. The pairing ticket contains
 * only the document namespace, write capability, and relay endpoint — no
 * health data.
 */
class P2PDiscovery(
    private val context: Context,
    private val irohNode: IrohNode
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredPeers = mutableListOf<DiscoveredPeer>()
    val discoveredPeers: List<DiscoveredPeer> get() = _discoveredPeers.toList()

    // MARK: - Pairing

    /**
     * Create a shared document and generate a pairing ticket.
     * The owner transfers this ticket to their other device.
     * Returns null if P2P runtime is not available (Iroh FFI not yet shipped).
     */
    suspend fun generatePairingTicket(deviceName: String): PairingResult? {
        if (!irohNode.isAvailable) {
            Log.w(TAG, "P2P sync not yet available — Iroh FFI dependency pending")
            return null
        }
        val documentId = irohNode.createDocument() ?: return null
        val ticket = irohNode.generateShareTicket(documentId) ?: return null

        // Save this document as our sync document
        val device = PairedDevice(
            nodeId = irohNode.getNodeId() ?: "self",
            displayName = deviceName,
            pairedAt = System.currentTimeMillis(),
            documentId = documentId
        )
        addPairedDevice(device)

        return PairingResult(ticket = ticket, documentId = documentId)
    }

    /**
     * Accept a pairing ticket from another device.
     * Joins the shared Willow namespace.
     * Returns null if P2P runtime is not available (Iroh FFI not yet shipped).
     */
    suspend fun acceptPairing(ticket: String, deviceName: String): PairedDevice? {
        if (!irohNode.isAvailable) {
            Log.w(TAG, "P2P sync not yet available — Iroh FFI dependency pending")
            return null
        }
        val documentId = irohNode.joinDocument(ticket) ?: return null

        val device = PairedDevice(
            nodeId = "peer", // will be discovered during sync
            displayName = deviceName,
            pairedAt = System.currentTimeMillis(),
            documentId = documentId
        )
        addPairedDevice(device)

        Log.i(TAG, "Paired with device: $deviceName (doc: $documentId)")
        return device
    }

    // MARK: - Local network discovery (mDNS)

    fun startDiscovery() {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager

        // Register this device
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Bios-${irohNode.getNodeId()?.take(8) ?: "unknown"}"
            serviceType = SERVICE_TYPE
            port = 0 // discovery only, no direct socket connection
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "mDNS service registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register mDNS service", e)
        }

        // Discover peers
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "mDNS discovery started")
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                val peerId = info.serviceName.removePrefix("Bios-")
                if (peerId != irohNode.getNodeId()?.take(8)) {
                    _discoveredPeers.add(DiscoveredPeer(
                        serviceName = info.serviceName,
                        nodeIdPrefix = peerId
                    ))
                    Log.d(TAG, "Discovered peer: ${info.serviceName}")
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _discoveredPeers.removeAll { it.serviceName == info.serviceName }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start mDNS discovery", e)
        }
    }

    fun stopDiscovery() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {}
        registrationListener = null
        discoveryListener = null
        _discoveredPeers.clear()
    }

    // MARK: - Paired devices persistence

    fun getPairedDevices(): List<PairedDevice> {
        val json = prefs.getString(KEY_PAIRED_DEVICES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PairedDevice(
                    nodeId = o.getString("nodeId"),
                    displayName = o.getString("displayName"),
                    pairedAt = o.getLong("pairedAt"),
                    documentId = o.getString("documentId")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun addPairedDevice(device: PairedDevice) {
        val devices = getPairedDevices().toMutableList()
        devices.removeAll { it.documentId == device.documentId }
        devices.add(device)
        savePairedDevices(devices)
    }

    fun removePairedDevice(documentId: String) {
        val devices = getPairedDevices().filter { it.documentId != documentId }
        savePairedDevices(devices)
    }

    private fun savePairedDevices(devices: List<PairedDevice>) {
        val arr = JSONArray()
        for (d in devices) {
            arr.put(JSONObject().apply {
                put("nodeId", d.nodeId)
                put("displayName", d.displayName)
                put("pairedAt", d.pairedAt)
                put("documentId", d.documentId)
            })
        }
        prefs.edit().putString(KEY_PAIRED_DEVICES, arr.toString()).apply()
    }

    /**
     * Destroy all pairing data. Called during emergency wipe.
     */
    fun destroyAll() {
        stopDiscovery()
        prefs.edit().clear().commit()
        Log.w(TAG, "P2P discovery data destroyed")
    }

    companion object {
        private const val TAG = "BiosP2PDiscovery"
        private const val SERVICE_TYPE = "_bios-p2p._tcp."
        private const val PREFS_NAME = "bios_p2p_devices"
        private const val KEY_PAIRED_DEVICES = "paired_devices"
    }
}

data class PairedDevice(
    val nodeId: String,
    val displayName: String,
    val pairedAt: Long,
    val documentId: String
)

data class PairingResult(
    val ticket: String,
    val documentId: String
)

data class DiscoveredPeer(
    val serviceName: String,
    val nodeIdPrefix: String
)
