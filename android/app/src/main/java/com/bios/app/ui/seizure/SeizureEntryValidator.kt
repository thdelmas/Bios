package com.bios.app.ui.seizure

/**
 * Pure validator for the owner-logging seizure entry surface
 * (#269 follow-up). Bounds-checks the (timestamp, durationSec)
 * pair before [SeizureEntryRepo] writes it.
 *
 * Rationale for the bounds:
 *  - **Duration ≥ 1 s** — zero/negative durations are data-entry
 *    typos. The URGENT pattern's 5-min threshold is the meaningful
 *    one, but we accept short events too — an owner logging a brief
 *    focal seizure is still useful diary substrate.
 *  - **Duration ≤ 1 h** — anything longer is implausible for a
 *    single convulsive event (the ILAE definition of status
 *    epilepticus operationally caps at 30 min for refractory; one
 *    hour is a generous data-sanity ceiling, not a clinical one).
 *  - **Timestamp ≤ now + 5 min** — the owner cannot have logged a
 *    future event. The 5-minute future slop absorbs clock skew /
 *    timezone confusion at entry time.
 *  - **Timestamp ≥ now − 30 days** — older events should be entered
 *    via the FHIR import flow rather than the live entry surface.
 *    The diary substrate the URGENT / cluster patterns need is the
 *    last-90-days window; this surface is for fresh events.
 *
 * Lifted to its own file so JVM tests pin the bounds without touching
 * Compose or Room.
 */
internal object SeizureEntryValidator {

    /** Maximum plausible duration for a single seizure event entered
     *  via this surface, in seconds. Refractory status epilepticus is
     *  ≥ 30 min; one hour is the data-sanity ceiling. */
    const val MAX_DURATION_SEC: Int = 60 * 60

    /** Minimum accepted duration in seconds. Zero / negative durations
     *  are typos; a one-second floor lets the owner record any real
     *  event without forcing a minimum that excludes brief focal
     *  episodes. */
    const val MIN_DURATION_SEC: Int = 1

    /** Forward-slop for clock skew at entry time, in ms. */
    const val FUTURE_SLOP_MS: Long = 5L * 60 * 1000

    /** Lookback window for live entry, in ms. Older events go through
     *  the FHIR import flow instead. */
    const val LOOKBACK_MS: Long = 30L * 24 * 60 * 60 * 1000

    sealed interface Result {
        data class Valid(val timestampMs: Long, val durationSec: Int) : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(
        timestampMs: Long,
        durationSec: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Result {
        if (durationSec < MIN_DURATION_SEC) {
            return Result.Invalid("Duration must be at least $MIN_DURATION_SEC second.")
        }
        if (durationSec > MAX_DURATION_SEC) {
            return Result.Invalid("Duration cannot exceed ${MAX_DURATION_SEC / 60} minutes.")
        }
        if (timestampMs > nowMs + FUTURE_SLOP_MS) {
            return Result.Invalid("Start time cannot be in the future.")
        }
        if (timestampMs < nowMs - LOOKBACK_MS) {
            return Result.Invalid("Use the import flow for events older than 30 days.")
        }
        return Result.Valid(timestampMs = timestampMs, durationSec = durationSec)
    }
}
