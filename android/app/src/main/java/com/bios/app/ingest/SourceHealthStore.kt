package com.bios.app.ingest

import android.content.Context
import android.content.SharedPreferences
import com.bios.app.model.SourceType

/** What the owner can do about a source that stopped delivering. */
enum class OwnerAction { NONE, REAUTH, SYNC_DEVICE }

/**
 * The last thing an adapter said about its own source: when it last
 * delivered, and the last refusal it met. Held outside the encrypted token
 * store because nothing here is a secret and the settings row, the provider
 * and the disconnect push all read it.
 */
data class SourceHealth(
    val lastOkAt: Long = 0L,
    val lastErrorAt: Long = 0L,
    val lastError: String? = null,
    val ownerAction: OwnerAction = OwnerAction.NONE,
) {
    /** A refusal newer than the last successful use. */
    val hasOpenError: Boolean get() = lastError != null && lastErrorAt > lastOkAt
}

/**
 * Per-source-type health as reported by the adapters. Consumer-side liveness
 * ([com.bios.app.alerts.SourceLiveness]) combines this with what actually
 * landed in the database; the store alone never decides a state.
 */
class SourceHealthStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(type: SourceType): SourceHealth = SourceHealth(
        lastOkAt = prefs.getLong(key(type, "ok_at"), 0L),
        lastErrorAt = prefs.getLong(key(type, "error_at"), 0L),
        lastError = prefs.getString(key(type, "error"), null),
        ownerAction = prefs.getString(key(type, "action"), null)
            ?.let { runCatching { OwnerAction.valueOf(it) }.getOrNull() }
            ?: OwnerAction.NONE,
    )

    /** The adapter delivered, or the owner re-authenticated: clears any open error. */
    fun recordOk(type: SourceType, now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(key(type, "ok_at"), now)
            .remove(key(type, "error"))
            .remove(key(type, "error_at"))
            .remove(key(type, "action"))
            .apply()
    }

    /**
     * The vendor refused, or the transport failed. [action] says whether the
     * owner can do something about it; [OwnerAction.NONE] records the message
     * without flipping the source to "needs attention".
     */
    fun recordError(
        type: SourceType,
        message: String,
        action: OwnerAction,
        now: Long = System.currentTimeMillis(),
    ) {
        prefs.edit()
            .putString(key(type, "error"), message.take(MAX_MESSAGE))
            .putLong(key(type, "error_at"), now)
            .putString(key(type, "action"), action.name)
            .apply()
    }

    fun clear(type: SourceType) {
        prefs.edit()
            .remove(key(type, "ok_at"))
            .remove(key(type, "error"))
            .remove(key(type, "error_at"))
            .remove(key(type, "action"))
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun key(type: SourceType, field: String) = "${type.key}:$field"

    companion object {
        const val PREFS_NAME = "bios_source_health"
        private const val MAX_MESSAGE = 200
    }
}
