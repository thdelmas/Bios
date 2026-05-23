package com.bios.app.ui.sleep

/**
 * Pure-logic helper for owner-asserted sleep windows (#242).
 *
 * Validates a (bedtime, wake-time) pair before any side effects hit the
 * DB. Used by both the Quick Settings tile ([com.bios.app.ingest.SleepTileService])
 * and any future Compose-side editable surface so the two paths share
 * one source of truth on what counts as a usable manual sleep log.
 *
 * Owner-asserted always beats heuristic; per the project manifesto
 * "the owner is final." But a tile that lands a 12-second-long
 * "sleep" row (the owner tapping it twice by accident) would poison
 * the baseline engine, so the validator enforces a minimum session
 * length plus a physiological 24 h cap (mirrors [SleepEntryRepo]).
 */
object ManualSleepEntry {

    /**
     * Minimum session length the tile / dialog will accept. 15 min is
     * the threshold below which an accidental double-tap is far more
     * likely than a real sleep window; the cutoff matches the
     * [com.bios.app.engine.PhoneSleepInference] AWAKE-bout floor so
     * inferred and manual windows speak the same language.
     */
    const val MIN_DURATION_SECONDS: Long = 15L * 60L

    /** Physiological ceiling. Mirrors [com.bios.app.data.SleepEntryRepo]. */
    const val MAX_DURATION_SECONDS: Long = 24L * 60L * 60L

    /** Validated owner-asserted sleep window. */
    data class Range(
        val bedtimeMs: Long,
        val wakeMs: Long,
    ) {
        val durationSeconds: Long get() = (wakeMs - bedtimeMs) / 1000L
    }

    sealed interface Result {
        /** Tile has never been tapped, or the stored bedtime was cleared. */
        object NotStarted : Result

        /**
         * Pair is unusable. [reason] is plain English suitable for a
         * Toast / tile subtitle — no second-person judgment per the
         * manifesto framing.
         */
        data class Invalid(val reason: String) : Result

        /** Pair is good; caller can hand [range] to [SleepEntryRepo.add]. */
        data class Valid(val range: Range) : Result
    }

    /**
     * Validate a (bedtime, wake-time) pair from a tile double-tap or a
     * dialog submission. A `bedtimeMs <= 0` argument signals "the tile
     * hasn't been started yet" — distinct from "invalid pair."
     */
    fun validate(bedtimeMs: Long, wakeMs: Long): Result {
        if (bedtimeMs <= 0L) return Result.NotStarted
        if (wakeMs <= bedtimeMs) {
            return Result.Invalid("Wake time must be after bedtime.")
        }
        val durationSec = (wakeMs - bedtimeMs) / 1000L
        if (durationSec < MIN_DURATION_SECONDS) {
            return Result.Invalid(
                "Session is shorter than ${MIN_DURATION_SECONDS / 60} minutes; not logged."
            )
        }
        if (durationSec > MAX_DURATION_SECONDS) {
            return Result.Invalid(
                "Session is longer than ${MAX_DURATION_SECONDS / 3600} hours; not logged."
            )
        }
        return Result.Valid(Range(bedtimeMs = bedtimeMs, wakeMs = wakeMs))
    }
}
