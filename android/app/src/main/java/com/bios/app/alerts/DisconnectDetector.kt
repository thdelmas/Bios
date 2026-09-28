package com.bios.app.alerts

import com.bios.app.data.BiosDatabase
import com.bios.app.ingest.OwnerAction
import com.bios.app.ingest.SourceCadence
import com.bios.app.ingest.SourceHealthStore
import com.bios.app.model.DataSource
import com.bios.app.model.SourceType
import java.util.concurrent.TimeUnit


/**
 * Detects when a previously-active ingest adapter has stopped syncing long
 * enough that Bios is meaningfully degraded, and decides whether the owner
 * deserves a push.
 *
 * This is Bios's **category-3 push** surface (see
 * [AlertContentPolicy] header for the three push categories). Bios is
 * reporting on Bios's own plumbing — Oura's OAuth token expired, the
 * Gadgetbridge bridge stopped relaying, etc. — not on the owner. The
 * AlertContentPolicy banlist doesn't constrain this surface by
 * construction; it targets person-judgment patterns, not system-state.
 *
 * Trigger contract (docs/specs/source-liveness.md):
 *
 *  - **Previously active**: the source delivered across at least
 *    [com.bios.app.ingest.SourceCadence.ACTIVE_SPAN_INTERVALS] of its own
 *    intervals. A one-off pairing the owner abandoned never qualifies; a
 *    two-rows-a-day nightly API qualifies after two nights.
 *  - **Needs attention**: the adapter recorded a vendor refusal with an
 *    owner action (re-authenticate) newer than its last delivery. Pushes
 *    on the next sync.
 *  - **Stale**: no delivery for [com.bios.app.ingest.SourceCadence.PUSH_STALE_INTERVALS]
 *    of the source's own intervals. In-app the row turns stale earlier
 *    (STALE_INTERVALS); the push waits longer so a watch left unsynced
 *    over a weekend stays quiet.
 *  - **Cool-down**: at most one push per source per 7 days. Owner
 *    dismissal isn't tracked separately — a state persisting past the
 *    next cool-down window pushes again.
 *
 * Reproductive sources are excluded by construction — reproductive
 * readings live in [com.bios.app.data.ReproductiveDatabase] and the
 * push path must never reveal their existence.
 */
class DisconnectDetector(
    db: BiosDatabase,
    healthStore: SourceHealthStore,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val reporter = SourceLivenessReporter(db, healthStore, now)

    /**
     * Returns the sources that earn a push *now* given the supplied
     * last-pushed-at lookup. Pure-function helper [decidePush] is what tests
     * exercise; this orchestrator feeds it liveness rows from the reporter.
     */
    suspend fun findSourcesToPush(
        lastPushedAtFor: (SourceType) -> Long,
        ownerEnabled: Boolean,
    ): List<DisconnectAlert> {
        if (!ownerEnabled) return emptyList()
        val currentTime = now()
        return reporter.report().mapNotNull { row ->
            if (row.sourceType !in PUSHABLE_SOURCE_TYPES) return@mapNotNull null
            val decision = decidePush(row, lastPushedAtFor(row.sourceType), currentTime)
            if (decision.push) {
                DisconnectAlert(
                    sourceType = row.sourceType,
                    displayName = row.label,
                    lastSyncAt = row.lastDeliveredAt,
                    state = row.state,
                    ownerAction = row.ownerAction,
                    message = row.message,
                )
            } else null
        }
    }

    companion object {
        val COOLDOWN_MILLIS: Long = TimeUnit.DAYS.toMillis(7)

        /**
         * Source types we push for. Excludes SELF_REPORTED (the owner
         * decides cadence, not Bios), CAMERA_PPG (one-shot capture, not
         * a continuous source), and DIRECT_SENSOR / PHONE_SENSOR (in-
         * process — when these stop, the whole app has stopped).
         */
        internal val PUSHABLE_SOURCE_TYPES: Set<SourceType> = setOf(
            SourceType.HEALTH_CONNECT,
            SourceType.GADGETBRIDGE,
            SourceType.OURA_API,
            SourceType.WHOOP_API,
            SourceType.GARMIN_API,
            SourceType.WITHINGS_API,
            SourceType.DEXCOM_API,
            SourceType.POLAR_API,
            SourceType.COROS_API,
        )
    }
}

/**
 * Pure-function trigger: given a source's liveness row, decide whether
 * *now* is a moment to push. No DB, no Context — exposed so unit tests can
 * pin every edge of the trigger contract without Room or notification
 * scaffolding.
 *
 * Returns [PushDecision] with [PushDecision.push] = true exactly when:
 *   1. [SourceLivenessRow.previouslyActive] (not a connection blip), AND
 *   2. the row is ATTENTION with an owner action, OR STALE for at least
 *      [SourceCadence.PUSH_STALE_INTERVALS] of its own intervals, AND
 *   3. ([now] − [lastPushedAt]) ≥ [DisconnectDetector.COOLDOWN_MILLIS].
 *
 * [lastPushedAt] = 0 means "never pushed about this source," which always
 * clears the cool-down gate. Negative values are clamped to 0.
 */
internal fun decidePush(
    row: SourceLivenessRow,
    lastPushedAt: Long,
    now: Long,
): PushDecision {
    if (!row.previouslyActive) {
        return PushDecision(push = false, reason = SkipReason.NEVER_ACTIVE)
    }
    val actionable = when (row.state) {
        LivenessState.ATTENTION -> row.ownerAction != OwnerAction.NONE
        LivenessState.STALE -> row.missedIntervals(now) >= SourceCadence.PUSH_STALE_INTERVALS
        LivenessState.HEALTHY, LivenessState.NEVER_DELIVERED -> false
    }
    if (!actionable) {
        return PushDecision(push = false, reason = SkipReason.NOT_STALE)
    }
    val sinceLastPush = now - maxOf(lastPushedAt, 0L)
    if (sinceLastPush < DisconnectDetector.COOLDOWN_MILLIS) {
        return PushDecision(push = false, reason = SkipReason.IN_COOLDOWN)
    }
    return PushDecision(push = true, reason = null)
}

data class PushDecision(val push: Boolean, val reason: SkipReason?)

enum class SkipReason { NEVER_ACTIVE, NOT_STALE, IN_COOLDOWN }

/**
 * One source's reason to push. [displayName] is what the owner sees in the
 * notification body — `DataSource.deviceName` when present, falls back to
 * the source-type label (e.g. "Oura"). [ownerAction] picks the wording:
 * a re-authentication is a different ask from "sync your watch".
 */
data class DisconnectAlert(
    val sourceType: SourceType,
    val displayName: String,
    val lastSyncAt: Long,
    val state: LivenessState = LivenessState.STALE,
    val ownerAction: OwnerAction = OwnerAction.SYNC_DEVICE,
    val message: String? = null,
)

/**
 * Owner-facing label for a [SourceType]. Picked separately from the
 * `key` strings (which are storage tokens) so the notification text reads
 * naturally without exposing internal identifiers.
 */
internal val SourceType.label: String
    get() = when (this) {
        SourceType.HEALTH_CONNECT -> "Health Connect"
        SourceType.GADGETBRIDGE -> "Gadgetbridge"
        SourceType.OURA_API -> "Oura"
        SourceType.WHOOP_API -> "WHOOP"
        SourceType.GARMIN_API -> "Garmin"
        SourceType.WITHINGS_API -> "Withings"
        SourceType.DEXCOM_API -> "Dexcom"
        SourceType.POLAR_API -> "Polar"
        SourceType.COROS_API -> "COROS"
        SourceType.DIRECT_SENSOR -> "Direct sensor"
        SourceType.PHONE_SENSOR -> "Phone sensor"
        SourceType.PHONE_SENSOR_DERIVED -> "Phone sleep fusion"
        SourceType.BIOS_INFERRED -> "Bios inference"
        SourceType.CAMERA_PPG -> "Camera PPG"
        SourceType.BLE_PERIPHERAL -> "Air-quality sensor"
        SourceType.SELF_REPORTED -> "Self-reported"
    }
