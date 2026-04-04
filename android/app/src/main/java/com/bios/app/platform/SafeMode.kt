package com.bios.app.platform

import android.content.Context

/**
 * Coercion-resistant safe mode.
 *
 * When activated (via LETHE duress PIN or Bios's own safe PIN), Bios:
 * 1. Destroys all health data (main DB + reproductive DB + tokens + exports)
 * 2. Clears the "was initialized" flag
 * 3. App restarts into onboarding — visually indistinguishable from a fresh install
 *
 * On LETHE: triggered by duress PIN broadcast (handled in LetheCompatImpl)
 * On stock Android: owner can configure a "safe PIN" in Settings. Entering it
 * at the app lock screen triggers safe mode instead of normal unlock.
 *
 * The adversary sees: "Welcome to Bios — connect a wearable to get started."
 * No trace that data ever existed.
 */
object SafeMode {

    private const val PREFS_NAME = "bios_safe_mode"
    private const val KEY_SAFE_PIN_HASH = "safe_pin_hash"

    /**
     * Activate safe mode: destroy all data and reset to fresh-install state.
     */
    fun activate(context: Context) {
        // Destroy everything
        DataDestroyer.destroyAll(context)

        // Clear the initialization flag so the app shows onboarding
        context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)
            .edit()
            .remove("initialized")
            .remove("onboarding_complete")
            .commit()
    }

    /**
     * Set a safe PIN (stock Android only).
     * The PIN is stored as a SHA-256 hash — we never store the actual PIN.
     */
    fun setSafePin(context: Context, pin: String) {
        val hash = hashPin(pin)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAFE_PIN_HASH, hash)
            .apply()
    }

    /**
     * Check if a safe PIN is configured.
     */
    fun hasSafePin(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SAFE_PIN_HASH, null) != null
    }

    /**
     * Verify if the entered PIN matches the safe PIN.
     * If it matches, this is a duress situation — activate safe mode.
     * Returns true if the PIN was the safe PIN (and safe mode was activated).
     */
    fun checkAndActivate(context: Context, enteredPin: String): Boolean {
        val storedHash = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SAFE_PIN_HASH, null) ?: return false

        if (hashPin(enteredPin) == storedHash) {
            activate(context)
            return true
        }

        return false
    }

    /**
     * Remove the safe PIN.
     */
    fun clearSafePin(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
