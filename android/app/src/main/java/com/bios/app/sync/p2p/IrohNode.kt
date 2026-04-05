package com.bios.app.sync.p2p

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages an embedded Iroh node for P2P sync via the Willow protocol.
 *
 * The node identity (Ed25519 keypair) is stored in the Iroh data directory
 * under the app's internal storage. The keypair is generated on first start
 * and persists across app restarts.
 *
 * On LETHE, the Iroh relay connection routes through the system Tor SOCKS proxy
 * at 127.0.0.1:9050 to prevent IP leakage.
 *
 * Lifecycle:
 * - Created in BiosApplication.onCreate()
 * - start() initializes the Iroh runtime
 * - stop() shuts down the node gracefully
 * - destroyAll() wipes identity, documents, and local storage (emergency wipe)
 *
 * TODO: Replace stub implementations with real Iroh FFI calls when
 * computer.iroh:iroh-ffi-android is published to Maven Central.
 * Track: https://github.com/n0-computer/iroh-ffi
 */
class IrohNode(private val context: Context) {

    private var running = false
    private val dataDir: File get() = File(context.filesDir, IROH_DATA_DIR)

    val isRunning: Boolean get() = running

    /**
     * Start the embedded Iroh node.
     * Creates the data directory and generates a new identity if none exists.
     */
    suspend fun start() {
        if (running) return

        try {
            dataDir.mkdirs()
            // TODO: node = Iroh.persistent(dataDir.absolutePath)
            running = true
            Log.i(TAG, "Iroh node started (stub — FFI not yet available)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Iroh node", e)
            running = false
        }
    }

    /**
     * Stop the Iroh node gracefully.
     */
    fun stop() {
        running = false
        Log.i(TAG, "Iroh node stopped")
    }

    /**
     * Returns this node's public key (peer ID).
     */
    fun getNodeId(): String? {
        // TODO: return node?.nodeId()?.toString()
        return null
    }

    /**
     * Create a new Willow document (namespace).
     * Returns the document ID as a string, or null on failure.
     */
    suspend fun createDocument(): String? {
        if (!running) return null
        // TODO: val doc = node.docCreate(); return doc.id().toString()
        Log.d(TAG, "createDocument stub — Iroh FFI not yet available")
        return null
    }

    /**
     * Join a shared document using a ticket from another device.
     * Returns the document ID, or null on failure.
     */
    suspend fun joinDocument(ticket: String): String? {
        if (!running) return null
        // TODO: val docTicket = DocTicket.fromString(ticket); return node.docJoin(docTicket).id().toString()
        Log.d(TAG, "joinDocument stub — Iroh FFI not yet available")
        return null
    }

    /**
     * Generate a share ticket for a document.
     * The ticket encodes the document namespace, write capability, and relay info.
     */
    fun generateShareTicket(docId: String, mode: String = "WRITE"): String? {
        if (!running) return null
        // TODO: doc.share(mode, AddrInfoOptions.RELAY_AND_ADDRESSES).toString()
        Log.d(TAG, "generateShareTicket stub — Iroh FFI not yet available")
        return null
    }

    /**
     * Set an entry in a document.
     * Key is a UTF-8 path, value is arbitrary bytes.
     */
    suspend fun setEntry(docId: String, key: String, value: ByteArray) {
        if (!running) return
        // TODO: doc.setBytes(author, key.toByteArray(), value)
        Log.d(TAG, "setEntry stub — Iroh FFI not yet available")
    }

    /**
     * Get an entry from a document.
     * Returns null if the key doesn't exist or on error.
     */
    suspend fun getEntry(docId: String, key: String): ByteArray? {
        if (!running) return null
        // TODO: entry.contentBytes(doc)
        return null
    }

    /**
     * List all keys in a document matching a prefix.
     * Returns key-value pairs.
     */
    suspend fun listEntries(docId: String, prefix: String): Map<String, ByteArray> {
        if (!running) return emptyMap()
        // TODO: iterate doc.getMany(Query.all(null)) and filter by prefix
        return emptyMap()
    }

    /**
     * Destroy all Iroh data: node identity, documents, and local storage.
     * Called during emergency wipe. Does not require the node to be running.
     */
    fun destroyAll() {
        stop()
        try {
            dataDir.deleteRecursively()
            Log.w(TAG, "Iroh data directory destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy Iroh data", e)
        }
    }

    companion object {
        private const val TAG = "BiosIrohNode"
        private const val IROH_DATA_DIR = "iroh"
    }
}
