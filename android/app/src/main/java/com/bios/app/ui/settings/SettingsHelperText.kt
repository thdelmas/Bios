package com.bios.app.ui.settings

/** Helper-text strings for the [PasteTokenDialog]. Extracted so the call
 *  sites in [SettingsScreen] stay compact and the strings have one home. */
internal object SettingsHelperText {
    const val OURA = "Paste an Oura personal-access token from cloud.ouraring.com. Bios uses it as a bearer token; refresh-aware OAuth lands when a Bios Oura app is registered."
    const val WITHINGS = "Paste a Withings API access token. Obtain one through a Withings developer-account OAuth exchange against developer.withings.com — Bios does not perform the OAuth dance itself yet."
    const val WHOOP = "Paste a WHOOP API access token. Obtain one through a WHOOP developer-account OAuth exchange against developer.whoop.com — Bios does not perform the OAuth dance itself yet."
    const val GARMIN = "Paste a Garmin Wellness API access token (or a pre-signed session token from a Garmin proxy). Bios does not perform the OAuth 1.0a dance itself yet — see the connection notes for what works today."
    const val POLAR = "Paste a Polar AccessLink API access token from admin.polaraccesslink.com. Bios uses it as a bearer; refresh-aware OAuth lands when a Bios Polar app is registered."
}
