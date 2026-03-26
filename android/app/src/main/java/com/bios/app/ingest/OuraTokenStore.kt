package com.bios.app.ingest

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores Oura personal access token in EncryptedSharedPreferences,
 * backed by Android Keystore. The token never appears in plaintext at rest.
 */
class OuraTokenStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "bios_oura_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        prefs.edit().putString(PREF_OURA_CREDENTIAL, token).apply()
    }

    fun getToken(): String? = prefs.getString(PREF_OURA_CREDENTIAL, null)

    fun clearToken() {
        prefs.edit().remove(PREF_OURA_CREDENTIAL).apply()
    }

    fun hasToken(): Boolean = getToken() != null

    companion object {
        private const val PREF_OURA_CREDENTIAL = "oura_access_token"
    }
}
