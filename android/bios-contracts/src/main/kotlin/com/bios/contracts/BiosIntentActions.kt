package com.bios.contracts

/**
 * Intent actions reserved for inter-app messaging across the Bios suite.
 *
 * **Reserved, not yet wired.** SoulRadio's inbound suggest API (Phase 7.5) is
 * the first planned consumer. Listed here so companions can target the action
 * names from the same artifact and the surface stays single-sourced when the
 * wiring lands.
 */
object BiosIntentActions {
    /**
     * Non-modal hint from Bios → SoulRadio suggesting a Solfeggio/Schumann
     * band based on autonomic state. Manifesto-bounded: 5-minute hysteresis,
     * SoulRadio is free to ignore. See `SoulRadio/docs/ECOSYSTEM.md`.
     */
    const val ACTION_SUGGEST_BAND = "com.bios.app.intent.action.SUGGEST_BAND"

    /**
     * Request from Bios → SoulRadio to stop playback. Used by Virgil during
     * an active fall/safety alert and by W2F during SOS Mechanical Restart.
     */
    const val ACTION_REQUEST_STOP = "com.bios.app.intent.action.REQUEST_STOP"

    /** Extras for [ACTION_SUGGEST_BAND]. */
    object SuggestBandExtras {
        /** String. Band identifier (e.g. "schumann_7_83", "solfeggio_528"). */
        const val BAND_ID = "band_id"

        /** String. Coarse rationale tag (e.g. "high_arousal", "low_hrv"). */
        const val RATIONALE = "rationale"
    }

    /** Extras for [ACTION_REQUEST_STOP]. */
    object RequestStopExtras {
        /** String. Reason tag (e.g. "virgil_alert", "w2f_sos"). */
        const val REASON = "reason"
    }
}
