package com.bios.app.platform

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for the local Kubo IPFS daemon running on LETHE.
 *
 * On LETHE, Kubo runs as a system service (UID 9051) with its API
 * on localhost:5001. All swarm traffic is Tor-routed via SOCKS proxy.
 *
 * On stock Android without IPFS, all methods return null/false gracefully.
 * This keeps Bios functional as a standalone app — decentralized features
 * are available only when IPFS infrastructure exists.
 */
class IpfsClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Check if the local IPFS daemon is reachable.
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_BASE/api/v0/id")
                .post(EMPTY_BODY)
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Add content to IPFS. Returns the CID, or null on failure.
     */
    suspend fun add(data: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "data", data.toRequestBody(OCTET_STREAM))
                .build()
            val request = Request.Builder()
                .url("$API_BASE/api/v0/add?pin=false&quieter=true")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body!!.string())
                json.getString("Hash")
            }
        } catch (e: Exception) {
            Log.w(TAG, "IPFS add failed: ${e.message}")
            null
        }
    }

    /**
     * Fetch content by CID. Returns bytes, or null on failure.
     */
    suspend fun cat(cid: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_BASE/api/v0/cat?arg=$cid")
                .post(EMPTY_BODY)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "IPFS cat failed for $cid: ${e.message}")
            null
        }
    }

    /**
     * Resolve an IPNS name to its current CID.
     */
    suspend fun nameResolve(name: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_BASE/api/v0/name/resolve?arg=$name&nocache=true")
                .post(EMPTY_BODY)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body!!.string())
                json.getString("Path").removePrefix("/ipfs/")
            }
        } catch (e: Exception) {
            Log.w(TAG, "IPNS resolve failed for $name: ${e.message}")
            null
        }
    }

    /**
     * Publish data to an IPFS PubSub topic.
     */
    suspend fun pubsubPublish(topic: String, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "msg", data.toRequestBody(OCTET_STREAM))
                    .build()
                val request = Request.Builder()
                    .url("$API_BASE/api/v0/pubsub/pub?arg=$topic")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                Log.w(TAG, "PubSub publish failed on $topic: ${e.message}")
                false
            }
        }

    /**
     * Subscribe to a PubSub topic and return the next message.
     * This is a blocking call — use with a timeout or in a coroutine.
     */
    suspend fun pubsubNext(topic: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val longPollClient = client.newBuilder()
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url("$API_BASE/api/v0/pubsub/sub?arg=$topic")
                .post(EMPTY_BODY)
                .build()
            longPollClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                // PubSub returns newline-delimited JSON; read the first message
                val line = response.body?.source()?.readUtf8Line() ?: return@withContext null
                val json = JSONObject(line)
                val dataB64 = json.getString("data")
                android.util.Base64.decode(dataB64, android.util.Base64.DEFAULT)
            }
        } catch (e: Exception) {
            Log.d(TAG, "PubSub sub on $topic: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "BiosIpfs"
        const val API_BASE = "http://127.0.0.1:5001"
        private val EMPTY_BODY = ByteArray(0).toRequestBody()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
