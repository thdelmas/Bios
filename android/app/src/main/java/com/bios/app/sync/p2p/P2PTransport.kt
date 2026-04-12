package com.bios.app.sync.p2p

/**
 * Abstraction over the P2P transport layer for device-to-device sync.
 *
 * The transport provides a distributed key-value store API:
 * documents are shared namespaces, entries are key-value pairs.
 * All data written through this interface should already be encrypted
 * by SyncProtocol — the transport treats values as opaque bytes.
 *
 * Implementations:
 * - [LocalNetworkTransport]: direct TCP sync over local WiFi (mDNS discovery)
 * - [IrohNode]: P2P via Iroh relay + Willow protocol (pending FFI bindings)
 *
 * WillowSyncAdapter, P2PDiscovery, and P2PSyncWorker depend on this
 * interface, not on a concrete transport.
 */
interface P2PTransport {

    /** True when the transport is operational and can sync. */
    val isAvailable: Boolean

    /** True when the transport is actively running. */
    val isRunning: Boolean

    /** Start the transport runtime. */
    suspend fun start()

    /** Stop the transport gracefully. */
    fun stop()

    /** Returns this node's peer ID, or null if not running. */
    fun getNodeId(): String?

    /** Create a new shared document (namespace). Returns doc ID or null. */
    suspend fun createDocument(): String?

    /** Join a shared document via a pairing ticket. Returns doc ID or null. */
    suspend fun joinDocument(ticket: String): String?

    /** Generate a share ticket for a document. */
    fun generateShareTicket(docId: String, mode: String = "WRITE"): String?

    /** Write an entry to a document. */
    suspend fun setEntry(docId: String, key: String, value: ByteArray)

    /** Read an entry from a document. Returns null if not found. */
    suspend fun getEntry(docId: String, key: String): ByteArray?

    /** List all entries in a document matching a key prefix. */
    suspend fun listEntries(docId: String, prefix: String): Map<String, ByteArray>

    /** Destroy all transport data (identity, documents, local storage). */
    fun destroyAll()
}
