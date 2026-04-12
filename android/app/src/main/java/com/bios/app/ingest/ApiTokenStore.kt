package com.bios.app.ingest

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Generic encrypted token store for third-party API adapters.
 * Each provider (WHOOP, Garmin, Withings, Dexcom) stores its OAuth token
 * under a unique key in a shared EncryptedSharedPreferences file.
 *
 * Tokens are encrypted with AES-256-GCM via Android Keystore.
 * Destroyed on LETHE wipe signals via [DataDestroyer].
 */
class ApiTokenStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(true)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(provider: String, token: String) {
        prefs.edit().putString(keyFor(provider), token).apply()
    }

    fun getToken(provider: String): String? =
        prefs.getString(keyFor(provider), null)

    fun hasToken(provider: String): Boolean =
        getToken(provider) != null

    fun clearToken(provider: String) {
        prefs.edit().remove(keyFor(provider)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun keyFor(provider: String) = "token_$provider"

    companion object {
        const val PREFS_NAME = "bios_api_credentials"
    }
}
