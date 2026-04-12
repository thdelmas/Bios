package com.bios.app.sync.p2p

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * P2P transport over local network using mDNS discovery + direct TCP.
 *
 * How it works:
 * - Each device runs a TCP server on a random port
 * - The server is announced via mDNS (_bios-p2p._tcp.)
 * - Paired devices connect directly and exchange encrypted entries
 * - All values are opaque bytes (encrypted by SyncProtocol upstream)
 *
 * Sync protocol (JSON-over-TCP, one message per line):
 *   → {"action":"list","docId":"...","prefix":"readings/"}
 *   ← {"entries":{"readings/uuid1":"<base64>","readings/uuid2":"<base64>"}}
 *
 *   → {"action":"set","docId":"...","key":"readings/uuid3","value":"<base64>"}
 *   ← {"ok":true}
 *
 * This enables P2P sync for devices on the same WiFi without needing
 * Iroh relay infrastructure or NAT traversal.
 */
class LocalNetworkTransport(private val context: Context) : P2PTransport {

    private val nodeId: String = UUID.randomUUID().toString().take(16)
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var running = false

    // In-memory document store: docId → (key → value)
    private val documents = ConcurrentHashMap<String, ConcurrentHashMap<String, ByteArray>>()

    // Discovered peer addresses: nodeId → (host, port)
    private val peerAddresses = ConcurrentHashMap<String, Pair<InetAddress, Int>>()

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override val isAvailable: Boolean get() = true
    override val isRunning: Boolean get() = running

    override suspend fun start() = withContext(Dispatchers.IO) {
        if (running) return@withContext

        // Start TCP server on random port
        serverSocket = ServerSocket(0).also { ss ->
            serverThread = Thread({
                while (!ss.isClosed) {
                    try {
                        val client = ss.accept()
                        Thread { handleClient(client) }.start()
                    } catch (_: Exception) {
                        break
                    }
                }
            }, "bios-p2p-server").apply { isDaemon = true; start() }
        }

        running = true
        val port = serverSocket?.localPort ?: 0
        Log.i(TAG, "Local P2P transport started on port $port (node: $nodeId)")

        // Register via mDNS
        registerMdns(port)
        startMdnsDiscovery()
    }

    override fun stop() {
        running = false
        unregisterMdns()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverThread = null
        peerAddresses.clear()
        Log.i(TAG, "Local P2P transport stopped")
    }

    override fun getNodeId(): String = nodeId

    override suspend fun createDocument(): String {
        val docId = UUID.randomUUID().toString()
        documents[docId] = ConcurrentHashMap()
        Log.d(TAG, "Created document: $docId")
        return docId
    }

    override suspend fun joinDocument(ticket: String): String? {
        // Ticket format: "docId:peerNodeId"
        val parts = ticket.split(":", limit = 2)
        if (parts.size != 2) return null
        val docId = parts[0]
        documents.putIfAbsent(docId, ConcurrentHashMap())
        Log.d(TAG, "Joined document: $docId")
        return docId
    }

    override fun generateShareTicket(docId: String, mode: String): String {
        return "$docId:$nodeId"
    }

    override suspend fun setEntry(docId: String, key: String, value: ByteArray) {
        val doc = documents.getOrPut(docId) { ConcurrentHashMap() }
        doc[key] = value

        // Push to connected peers
        withContext(Dispatchers.IO) {
            for ((_, addr) in peerAddresses) {
                try {
                    sendToPeer(addr.first, addr.second, JSONObject().apply {
                        put("action", "set")
                        put("docId", docId)
                        put("key", key)
                        put("value", Base64.encodeToString(value, Base64.NO_WRAP))
                    })
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to push to peer: ${e.message}")
                }
            }
        }
    }

    override suspend fun getEntry(docId: String, key: String): ByteArray? {
        return documents[docId]?.get(key)
    }

    override suspend fun listEntries(docId: String, prefix: String): Map<String, ByteArray> {
        val local = documents[docId]?.filter { it.key.startsWith(prefix) } ?: emptyMap()

        // Also pull from connected peers
        val merged = HashMap(local)
        withContext(Dispatchers.IO) {
            for ((_, addr) in peerAddresses) {
                try {
                    val response = sendToPeer(addr.first, addr.second, JSONObject().apply {
                        put("action", "list")
                        put("docId", docId)
                        put("prefix", prefix)
                    })
                    val entries = response?.optJSONObject("entries")
                    if (entries != null) {
                        for (k in entries.keys()) {
                            if (!merged.containsKey(k)) {
                                merged[k] = Base64.decode(entries.getString(k), Base64.NO_WRAP)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to list from peer: ${e.message}")
                }
            }
        }

        // Store pulled entries locally
        val doc = documents.getOrPut(docId) { ConcurrentHashMap() }
        for ((k, v) in merged) {
            doc.putIfAbsent(k, v)
        }

        return merged
    }

    override fun destroyAll() {
        stop()
        documents.clear()
        Log.w(TAG, "Local P2P transport data destroyed")
    }

    // -- TCP server handler --

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = OutputStreamWriter(socket.getOutputStream())

            val line = reader.readLine() ?: return
            val request = JSONObject(line)
            val response = handleRequest(request)

            writer.write(response.toString())
            writer.write("\n")
            writer.flush()
        } catch (e: Exception) {
            Log.d(TAG, "Client handler error: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleRequest(request: JSONObject): JSONObject {
        val action = request.optString("action")
        val docId = request.optString("docId")
        val doc = documents.getOrPut(docId) { ConcurrentHashMap() }

        return when (action) {
            "list" -> {
                val prefix = request.optString("prefix", "")
                val entries = JSONObject()
                for ((k, v) in doc) {
                    if (k.startsWith(prefix)) {
                        entries.put(k, Base64.encodeToString(v, Base64.NO_WRAP))
                    }
                }
                JSONObject().put("entries", entries)
            }
            "set" -> {
                val key = request.getString("key")
                val value = Base64.decode(request.getString("value"), Base64.NO_WRAP)
                doc[key] = value
                JSONObject().put("ok", true)
            }
            else -> JSONObject().put("error", "unknown action: $action")
        }
    }

    // -- TCP client --

    private fun sendToPeer(host: InetAddress, port: Int, request: JSONObject): JSONObject? {
        return try {
            Socket(host, port).use { socket ->
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val writer = OutputStreamWriter(socket.getOutputStream())
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer.write(request.toString())
                writer.write("\n")
                writer.flush()
                val line = reader.readLine() ?: return null
                JSONObject(line)
            }
        } catch (e: Exception) {
            null
        }
    }

    // -- mDNS --

    private fun registerMdns(port: Int) {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Bios-$nodeId"
            serviceType = SERVICE_TYPE
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "mDNS registered: ${info.serviceName} on port $port")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "mDNS registration failed: $code")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }

        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register mDNS", e)
        }
    }

    private fun startMdnsDiscovery() {
        val manager = nsdManager ?: return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                val peerId = info.serviceName.removePrefix("Bios-")
                if (peerId != nodeId) {
                    // Resolve to get host + port
                    manager.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, code: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host ?: return
                            peerAddresses[peerId] = Pair(host, info.port)
                            Log.d(TAG, "Discovered peer: $peerId at ${host.hostAddress}:${info.port}")
                        }
                    })
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                val peerId = info.serviceName.removePrefix("Bios-")
                peerAddresses.remove(peerId)
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, code: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, code: Int) {}
        }

        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start mDNS discovery", e)
        }
    }

    private fun unregisterMdns() {
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {}
        registrationListener = null
        discoveryListener = null
    }

    companion object {
        private const val TAG = "LocalP2PTransport"
        private const val SERVICE_TYPE = "_bios-p2p._tcp."
        private const val SOCKET_TIMEOUT_MS = 10_000
    }
}
