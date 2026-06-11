package com.bios.app.ingest

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP plumbing for the vendor REST adapters (Oura, Withings, Whoop,
 * Garmin, Polar, Dexcom). Each adapter used to carry its own copy of the
 * OkHttp client, the request/response handling, and ISO-timestamp parsing —
 * the copies had drifted (see [parseIsoTimestampOrNull]). Centralising them
 * means a change to timeout, retry, or error policy is one edit, not six.
 */

/** OkHttp client for the vendor adapters: 30-second connect and read timeouts. */
internal fun defaultApiClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

/**
 * Executes [request] and parses a JSON object from the response.
 *
 * Returns null on a non-success status or an empty body — the uniform
 * soft-failure contract the adapters rely on to skip a source quietly. A
 * malformed body still throws (org.json.JSONException), matching the adapters'
 * prior behaviour.
 *
 * Blocking: call from a background dispatcher (the adapters wrap it in
 * `withContext(Dispatchers.IO)`).
 */
internal fun OkHttpClient.getJson(request: Request): JSONObject? {
    val response = newCall(request).execute()
    if (!response.isSuccessful) return null
    val body = response.body?.string() ?: return null
    return JSONObject(body)
}

/**
 * Parses an ISO-8601 timestamp to epoch millis, or null if [value] is blank or
 * unparseable.
 *
 * Accepts offset/zoned forms (`...Z`, `...+02:00`) directly, and zone-less
 * local forms (`2024-01-01T12:00:00`) interpreted as UTC. Replaces four
 * near-identical per-adapter parsers that had drifted — two of which had no
 * exception handling and so crashed the entire sync on a single malformed
 * timestamp. Callers apply their own fallback for the null case.
 */
internal fun parseIsoTimestampOrNull(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
