package com.bios.app.ingest

import android.util.Log

/**
 * Ingest observability. The pipeline ran mute for months — the worker never
 * bound sourceIds, every stage saw an empty list, every run reported success
 * and nobody could tell (2026-08-16). One line per adapter fetch and one
 * summary per sync is the floor that makes that class of failure visible.
 */
internal object IngestTelemetry {

    private const val TAG = "BiosIngest"

    fun log(message: String) {
        Log.i(TAG, message)
    }

    /** A fetch that never logs "in Nms" is the one hanging the awaitAll. */
    suspend fun <T> timedFetch(name: String, block: suspend () -> List<T>): List<T> {
        val t0 = System.currentTimeMillis()
        val result = block()
        log("fetch $name: ${result.size} readings in ${System.currentTimeMillis() - t0}ms")
        return result
    }

    fun syncSummary(
        label: String,
        hcBound: Boolean,
        fetched: Int,
        deduped: Int,
        quality: Int,
        derived: Int,
        written: Int
    ) {
        log(
            "$label: hcSource=$hcBound fetched=$fetched deduped=$deduped " +
                "quality=$quality derived=$derived written=$written"
        )
    }
}
