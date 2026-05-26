package com.bios.app.alerts

import com.bios.app.data.BiosDatabase
import com.bios.app.data.dao.MetricReadingDao
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
 * Trigger contract:
 *
 *  - **Previously active**: source has ≥ [MIN_READINGS_FOR_ACTIVE]
 *    historical readings. One-off connection blips don't count.
 *  - **Stale**: source's most recent reading is older than the
 *    source-type-specific threshold. OAuth-backed sources (Oura,
 *    Withings, WHOOP, Garmin, Dexcom) get a tighter 5-day threshold —
 *    token expiry is the dominant failure mode. Passive sources
 *    (Health Connect, Gadgetbridge) get 7 days; their failure modes
 *    are slower and less owner-actionable.
 *  - **Cool-down**: at most one push per source per 7 days. Owner
 *    dismissal isn't tracked separately — staleness persisting past
 *    the next cool-down window pushes again.
 *
 * Reproductive sources are excluded by construction — reproductive
 * readings live in [com.bios.app.data.ReproductiveDatabase] and the
 * push path must never reveal their existence.
 */
class DisconnectDetector(
    db: BiosDatabase,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val readingDao: MetricReadingDao = db.metricReadingDao()
    private val sourceDao = db.dataSourceDao()

    /**
     * Returns the list of stale-enough sources that earn a push *now*
     * given the supplied last-pushed-at lookup. Pure-function helper
     * [decidePush] is what tests exercise; this orchestrator combines it
     * with DB state.
     */
    suspend fun findSourcesToPush(
        lastPushedAtFor: (SourceType) -> Long,
        ownerEnabled: Boolean,
    ): List<DisconnectAlert> {
        if (!ownerEnabled) return emptyList()
        val freshnessBySourceId =
            readingDao.sourceFreshness().associateBy { it.sourceId }
        val currentTime = now()
        return sourceDao.getAll().mapNotNull { source ->
            val sourceType = SourceType.entries.firstOrNull { it.key == source.sourceType }
                ?: return@mapNotNull null
            if (sourceType !in PUSHABLE_SOURCE_TYPES) return@mapNotNull null
            val freshness = freshnessBySourceId[source.id] ?: return@mapNotNull null
            val decision = decidePush(
                sourceType = sourceType,
                readingCount = freshness.readingCount,
                lastReadingAt = freshness.lastTimestamp,
                lastPushedAt = lastPushedAtFor(sourceType),
                now = currentTime,
            )
            if (decision.push) {
                DisconnectAlert(
                    sourceType = sourceType,
                    displayName = source.deviceName ?: sourceType.label,
                    lastSyncAt = freshness.lastTimestamp,
                )
            } else null
        }
    }

    companion object {
        const val MIN_READINGS_FOR_ACTIVE = 50
        val OAUTH_STALE_THRESHOLD_MILLIS: Long = TimeUnit.DAYS.toMillis(5)
        val PASSIVE_STALE_THRESHOLD_MILLIS: Long = TimeUnit.DAYS.toMillis(7)
        val COOLDOWN_MILLIS: Long = TimeUnit.DAYS.toMillis(7)

        /**
         * OAuth-backed source types — failure mode is dominated by token
         * expiry. Tighter staleness threshold so the owner sees the gap
         * before three weeks of silent degradation pile up.
         */
        private val OAUTH_SOURCE_TYPES: Set<SourceType> = setOf(
            SourceType.OURA_API,
            SourceType.WITHINGS_API,
            SourceType.WHOOP_API,
            SourceType.GARMIN_API,
            SourceType.DEXCOM_API,
            SourceType.POLAR_API,
        )

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
        )

        /**
         * Stale threshold for [sourceType]. OAuth-backed gets the tighter
         * window; passive gets the looser one. Unknown types (caller
         * already filtered, but defensive) get the looser default.
         */
        internal fun staleThreshold(sourceType: SourceType): Long =
            if (sourceType in OAUTH_SOURCE_TYPES) OAUTH_STALE_THRESHOLD_MILLIS
            else PASSIVE_STALE_THRESHOLD_MILLIS
    }
}

/**
 * Pure-function trigger: given a source's state, decide whether *now* is
 * a moment to push. No DB, no Context — exposed so unit tests can pin
 * every edge of the trigger contract without Room or notification
 * scaffolding.
 *
 * Returns [PushDecision] with [PushDecision.push] = true exactly when:
 *   1. [readingCount] ≥ [DisconnectDetector.MIN_READINGS_FOR_ACTIVE]
 *      (the source was previously active, not a connection blip), AND
 *   2. ([now] − [lastReadingAt]) ≥ source-type-specific staleness
 *      threshold (the source is currently stale), AND
 *   3. ([now] − [lastPushedAt]) ≥ [DisconnectDetector.COOLDOWN_MILLIS]
 *      (we haven't pushed about this source recently).
 *
 * [lastPushedAt] = 0 means "never pushed about this source," which always
 * clears the cool-down gate. Negative values are clamped to 0.
 */
internal fun decidePush(
    sourceType: SourceType,
    readingCount: Int,
    lastReadingAt: Long,
    lastPushedAt: Long,
    now: Long,
): PushDecision {
    if (readingCount < DisconnectDetector.MIN_READINGS_FOR_ACTIVE) {
        return PushDecision(push = false, reason = SkipReason.NEVER_ACTIVE)
    }
    val staleMillis = now - lastReadingAt
    if (staleMillis < DisconnectDetector.staleThreshold(sourceType)) {
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
 * the source-type label (e.g. "Oura").
 */
data class DisconnectAlert(
    val sourceType: SourceType,
    val displayName: String,
    val lastSyncAt: Long,
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
        SourceType.DIRECT_SENSOR -> "Direct sensor"
        SourceType.PHONE_SENSOR -> "Phone sensor"
        SourceType.PHONE_SENSOR_DERIVED -> "Phone sleep fusion"
        SourceType.BIOS_INFERRED -> "Bios inference"
        SourceType.CAMERA_PPG -> "Camera PPG"
        SourceType.BLE_PERIPHERAL -> "Air-quality sensor"
        SourceType.SELF_REPORTED -> "Self-reported"
    }
