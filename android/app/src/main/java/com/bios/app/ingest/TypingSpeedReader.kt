package com.bios.app.ingest

import android.content.Context
import com.bios.app.alerts.GeriatricTrajectoryStore
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Owner-permission-gated typing-speed proxy for the cognitive-trajectory
 * pull-side view (#208, GERIATRICS_PALLIATIVE_POV §2.4).
 *
 * **Status: v1 scaffold.** The full implementation requires an
 * `InputMethodService` keyboard or an `AccessibilityService` capture
 * surface — both are heavy and have their own permission / privacy /
 * security review. v1 ships:
 *
 *  - The [MetricType.TYPING_SPEED_CHAR_PER_MIN] key.
 *  - This reader stub that holds the opt-in check and the canonical
 *    write path (`emit()` shape).
 *  - The geriatric trajectory advisory's SignalRule, which gracefully
 *    no-ops when no baseline exists (because no readings have ever been
 *    persisted).
 *
 * The actual capture path lands as a follow-up (TODO below).
 *
 * Manifesto guard: typing-speed data is *highly* sensitive — keyboard
 * inputs include passwords, messages, search queries. The capture
 * surface, when it ships, must record **only the cadence** — character-
 * count and elapsed-time aggregated into chars/min — never any character
 * identity, never any window context, never any keystroke timing more
 * granular than the per-session aggregate. The owner enables it
 * explicitly via [GeriatricTrajectoryStore.cognitiveTrackingEnabled].
 */
class TypingSpeedReader(private val context: Context) {

    private val store = GeriatricTrajectoryStore(context)

    /**
     * True when the owner has explicitly opted into cognitive trajectory
     * tracking. The reader does nothing — emits nothing, captures
     * nothing — until this flag is set.
     */
    fun isEnabled(): Boolean = store.cognitiveTrackingEnabled

    /**
     * Canonical write shape for a typing-speed sample.
     *
     * Takes the session-level aggregate (total chars, elapsed millis)
     * computed *outside* this reader by the IME / accessibility-service
     * capture surface, and returns the [MetricReading] for persistence.
     * Returns `null` when typing-speed tracking is not enabled or the
     * inputs are insufficient.
     *
     * Capture-surface contract:
     *  - [characters] is the count of *all* keystrokes (not just
     *    printable characters), including backspace — the cadence is
     *    what matters, not the content.
     *  - [elapsedMillis] covers the actively-typed window (the
     *    capture surface drops idle gaps longer than 2 s before
     *    aggregating).
     *  - [sourceId] identifies the capture surface so provenance is
     *    transparent in the data-coverage view.
     */
    fun toReading(
        characters: Int,
        elapsedMillis: Long,
        sourceId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): MetricReading? {
        if (!isEnabled()) return null
        val charsPerMin = charsPerMinute(characters, elapsedMillis) ?: return null
        return MetricReading(
            metricType = MetricType.TYPING_SPEED_CHAR_PER_MIN.key,
            value = charsPerMin,
            timestamp = nowMillis,
            sourceId = sourceId,
            confidence = ConfidenceTier.LOW.level,
        )
    }

    companion object {
        /**
         * Minimum keystroke count for a session aggregate to be
         * meaningful. Below this, short messages would skew the
         * trajectory toward whatever cadence happened in that
         * particular brief burst.
         */
        const val MIN_CHARACTERS: Int = 20

        /**
         * Minimum elapsed window for a session aggregate. Below this,
         * single-burst keystrokes inflate chars/min toward implausibly
         * high values.
         */
        const val MIN_ELAPSED_MS: Long = 2_000L

        /**
         * Pure-compute chars-per-minute from a session aggregate. Returns
         * null when the input fails the [MIN_CHARACTERS] / [MIN_ELAPSED_MS]
         * floors. Extracted so the cadence math can be unit-tested in the
         * JVM-only test source set (no Android Context required).
         */
        fun charsPerMinute(characters: Int, elapsedMillis: Long): Double? {
            if (characters < MIN_CHARACTERS) return null
            if (elapsedMillis < MIN_ELAPSED_MS) return null
            val elapsedMinutes = elapsedMillis / 60_000.0
            if (elapsedMinutes <= 0.0) return null
            return characters / elapsedMinutes
        }

        // TODO(#208 follow-up): wire an InputMethodService keyboard or
        // an opt-in AccessibilityService capture surface that streams
        // (keystroke timestamp) → (session aggregate) into this reader.
        // The capture surface must record only cadence — no character
        // identity, no window context, no per-key timing beyond what is
        // needed to compute the per-session chars/min aggregate.
    }
}
