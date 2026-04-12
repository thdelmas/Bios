package com.bios.app.push

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.unifiedpush.android.connector.UnifiedPush

/**
 * Manages UnifiedPush registration lifecycle.
 *
 * Push is off by default. The owner must explicitly enable it in Settings.
 * The push endpoint URL is stored in EncryptedSharedPreferences because
 * it could be used to send messages to this device.
 *
 * The enabled boolean is in plain bios_settings prefs (not sensitive).
 */
object PushRegistrationManager {

    private const val TAG = "BiosPush"
    private const val SETTINGS_PREFS = "bios_settings"
    private const val ENCRYPTED_PREFS_NAME = "bios_push_secure"
    private const val PREF_KEY_ENABLED = "push_enabled"
    private const val PREF_KEY_ENDPOINT = "push_endpoint"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_KEY_ENABLED, enabled)
            .apply()
    }

    /**
     * Request push registration via the user's chosen UnifiedPush distributor.
     * If no distributor is installed, the library triggers onRegistrationFailed.
     */
    fun register(context: Context) {
        UnifiedPush.register(context)
        Log.d(TAG, "Push registration requested")
    }

    /**
     * Unregister from push and clear stored endpoint.
     */
    fun unregister(context: Context) {
        UnifiedPush.unregister(context)
        clearEndpoint(context)
        setEnabled(context, false)
        Log.d(TAG, "Push unregistered")
    }

    /**
     * Called by BiosPushReceiver when the distributor assigns an endpoint.
     */
    fun onEndpointReceived(context: Context, endpoint: String) {
        getEncryptedPrefs(context).edit()
            .putString(PREF_KEY_ENDPOINT, endpoint)
            .apply()
        Log.d(TAG, "Push endpoint stored")
        // TODO: POST endpoint to backend sync gateway for server-initiated pushes
    }

    fun getEndpoint(context: Context): String? {
        return getEncryptedPrefs(context).getString(PREF_KEY_ENDPOINT, null)
    }

    fun isRegistered(context: Context): Boolean {
        return getEndpoint(context) != null
    }

    fun getDistributorName(context: Context): String? {
        return UnifiedPush.getSavedDistributor(context)
    }

    /**
     * Called by BiosPushReceiver on registration failure.
     * Resets enabled state so the UI toggle reflects reality.
     */
    fun onRegistrationFailed(context: Context) {
        clearEndpoint(context)
        setEnabled(context, false)
        Log.w(TAG, "Push registration failed — no distributor installed?")
    }

    private fun clearEndpoint(context: Context) {
        getEncryptedPrefs(context).edit()
            .remove(PREF_KEY_ENDPOINT)
            .apply()
    }

    /**
     * Destroy all push state. Called by DataDestroyer during emergency wipe.
     */
    fun destroyAll(context: Context) {
        try {
            UnifiedPush.unregister(context)
        } catch (_: Exception) {
            // Distributor may not be available during wipe
        }
        getEncryptedPrefs(context).edit().clear().commit()
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().remove(PREF_KEY_ENABLED).apply()
    }

    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
