package com.bios.app.ingest

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted persistence for the single paired BLE air-quality peripheral
 * (#43). Stored as the BluetoothDevice MAC address + a human-readable name
 * the owner can recognize on the Data Coverage / Settings screens.
 *
 * One device at a time is enough for the v1 — multi-sensor support is
 * deferred (see issue #119 "Out of scope"). The address is the canonical
 * key the runtime adapter uses to `connectGatt(...)`; the name is purely
 * for display.
 *
 * Uses the same AES-256-GCM EncryptedSharedPreferences pattern as
 * [ApiTokenStore] so the data sits behind Android Keystore and is wiped by
 * [com.bios.app.platform.DataDestroyer] alongside everything else on the
 * owner's "Delete all data" / LETHE wipe path.
 *
 * The store is intentionally tiny — no Room migration, no DAO surface —
 * because the only "rows" are at most one paired device.
 */
class BlePairedDeviceStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** A paired air-quality peripheral, or `null` if none is configured. */
    data class Paired(val address: String, val name: String)

    fun save(address: String, name: String) {
        prefs.edit()
            .putString(KEY_ADDRESS, address)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun get(): Paired? {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: address
        return Paired(address, name)
    }

    fun isPaired(): Boolean = prefs.getString(KEY_ADDRESS, null) != null

    fun clear() {
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_NAME).apply()
    }

    companion object {
        const val PREFS_NAME = "bios_ble_devices"
        private const val KEY_ADDRESS = "ble_air_address"
        private const val KEY_NAME = "ble_air_name"
    }
}
