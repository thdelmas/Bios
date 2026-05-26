package com.bios.app.i18n

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Owner-selected UI locale. Stored in SharedPreferences so it survives
 * relaunch, applied via [wrap] in `Activity.attachBaseContext` or any
 * Compose surface that needs to render text under the override (issue #210).
 *
 * Empty / unset preference falls back to the Android system locale —
 * the override is opt-in, never automatic. Matches the manifesto's
 * "owner is final" principle: Bios respects the device default unless
 * the owner explicitly chooses otherwise.
 */
object LocalePreference {

    /** Shared-prefs file shared with [com.bios.app.ui.settings.SettingsScreen]. */
    private const val PREFS_NAME = "bios_settings"
    private const val KEY_LOCALE_TAG = "ui_locale_tag"

    /** Stable code used in the selector to mean "use the device default". */
    const val SYSTEM_DEFAULT_TAG = ""

    /**
     * Locales the language selector exposes. Order is the order shown in
     * the Settings UI. Adding a locale here also requires shipping a
     * `values-<locale>/strings.xml` overlay (see issue #210 Tier-A scope).
     */
    val supported: List<LocaleOption> = listOf(
        LocaleOption(SYSTEM_DEFAULT_TAG, "System default"),
        LocaleOption("en", "English"),
        LocaleOption("es", "Español"),
        LocaleOption("es-MX", "Español (México)"),
        LocaleOption("es-AR", "Español (Argentina)"),
        LocaleOption("pt-BR", "Português (Brasil)"),
        LocaleOption("mi-NZ", "Te Reo Māori"),
        LocaleOption("haw", "ʻŌlelo Hawaiʻi"),
    )

    data class LocaleOption(val tag: String, val displayName: String)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Currently stored override tag, or [SYSTEM_DEFAULT_TAG] when none set. */
    fun currentTag(context: Context): String =
        prefs(context).getString(KEY_LOCALE_TAG, SYSTEM_DEFAULT_TAG) ?: SYSTEM_DEFAULT_TAG

    /** Persists the owner's selection. Pass [SYSTEM_DEFAULT_TAG] to clear. */
    fun setTag(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_LOCALE_TAG, tag).apply()
    }

    /**
     * Returns the [Locale] currently in effect — either the owner's
     * override or the device default. Useful for non-UI surfaces (e.g.
     * Notification text built off the application context).
     */
    fun effectiveLocale(context: Context): Locale {
        val tag = currentTag(context)
        if (tag.isBlank()) return Locale.getDefault()
        return Locale.forLanguageTag(tag)
    }

    /**
     * Returns a context whose resources resolve strings in the
     * owner-selected locale. Falls through to the original context when no
     * override is set.
     */
    fun wrap(base: Context): Context {
        val tag = currentTag(base)
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
