package com.bios.app.alerts

import com.bios.app.data.BiosDatabase
import com.bios.app.ingest.OwnerAction
import com.bios.app.ingest.SourceCadence
import com.bios.app.ingest.SourceHealth
import com.bios.app.ingest.SourceHealthStore
import com.bios.app.model.SourceType

/**
 * Consumer-side view of whether a source is still delivering.
 *
 * NEVER_DELIVERED: registered, no primary reading yet.
 * HEALTHY: delivering within its cadence, no open refusal.
 * STALE: last reading older than [SourceCadence.STALE_INTERVALS] intervals.
 * ATTENTION: the adapter recorded a refusal newer than its last success.
 */
enum class LivenessState { NEVER_DELIVERED, HEALTHY, STALE, ATTENTION }

data class SourceLivenessRow(
    val sourceType: SourceType,
    val label: String,
    val state: LivenessState,
    /** When the current state began (last delivery for STALE, refusal time for ATTENTION), 0 if unknown. */
    val since: Long,
    val ownerAction: OwnerAction,
    val message: String?,
    val lastDeliveredAt: Long,
    val firstDeliveredAt: Long,
    val metricTypes: List<String>,
) {
    /** Delivered across at least [SourceCadence.ACTIVE_SPAN_INTERVALS] of its own intervals. */
    val previouslyActive: Boolean
        get() {
            val interval = SourceCadence.intervalMillis(sourceType) ?: return lastDeliveredAt > 0L
            return lastDeliveredAt > 0L &&
                lastDeliveredAt - firstDeliveredAt >= SourceCadence.ACTIVE_SPAN_INTERVALS * interval
        }

    /** Missed intervals since the last delivery, 0 when the source has no cadence. */
    fun missedIntervals(now: Long): Long {
        val interval = SourceCadence.intervalMillis(sourceType) ?: return 0L
        if (lastDeliveredAt <= 0L) return 0L
        return (now - lastDeliveredAt) / interval
    }
}

/**
 * Pure state derivation. The adapter's word ([health]) wins only when it is
 * newer than the last delivery; otherwise what landed in the database
 * decides. No DB, no clock of its own: tests pin every edge.
 */
internal fun assessLiveness(
    sourceType: SourceType,
    label: String,
    firstDeliveredAt: Long,
    lastDeliveredAt: Long,
    metricTypes: List<String>,
    health: SourceHealth,
    now: Long,
): SourceLivenessRow {
    fun row(state: LivenessState, since: Long, action: OwnerAction, message: String?) =
        SourceLivenessRow(
            sourceType, label, state, since, action, message,
            lastDeliveredAt, firstDeliveredAt, metricTypes,
        )

    if (health.hasOpenError && health.ownerAction != OwnerAction.NONE &&
        health.lastErrorAt > lastDeliveredAt
    ) {
        return row(LivenessState.ATTENTION, health.lastErrorAt, health.ownerAction, health.lastError)
    }
    if (lastDeliveredAt <= 0L) {
        return row(LivenessState.NEVER_DELIVERED, 0L, OwnerAction.NONE, health.lastError)
    }
    val interval = SourceCadence.intervalMillis(sourceType)
        ?: return row(LivenessState.HEALTHY, lastDeliveredAt, OwnerAction.NONE, null)
    val missed = (now - lastDeliveredAt) / interval
    if (missed >= SourceCadence.STALE_INTERVALS) {
        return row(LivenessState.STALE, lastDeliveredAt, OwnerAction.SYNC_DEVICE, health.lastError)
    }
    return row(LivenessState.HEALTHY, lastDeliveredAt, OwnerAction.NONE, null)
}

/**
 * Joins registered sources, their delivery span and metric coverage in the
 * database, and the adapters' recorded health into one liveness row per
 * source. Read by the disconnect push, the provider's `/sources` and the
 * settings rows, so all three say the same thing.
 */
class SourceLivenessReporter(
    db: BiosDatabase,
    private val healthStore: SourceHealthStore,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val readingDao = db.metricReadingDao()
    private val sourceDao = db.dataSourceDao()

    suspend fun report(): List<SourceLivenessRow> {
        val freshness = readingDao.sourceFreshness().associateBy { it.sourceId }
        val metricsBySource = readingDao.sourceMetricTypes().groupBy({ it.sourceId }, { it.metricType })
        val currentTime = now()
        return sourceDao.getAll().mapNotNull { source ->
            val type = SourceType.entries.firstOrNull { it.key == source.sourceType }
                ?: return@mapNotNull null
            val fresh = freshness[source.id]
            assessLiveness(
                sourceType = type,
                label = source.deviceName ?: type.label,
                firstDeliveredAt = fresh?.firstTimestamp ?: 0L,
                lastDeliveredAt = fresh?.lastTimestamp ?: 0L,
                metricTypes = metricsBySource[source.id].orEmpty().sorted(),
                health = healthStore.get(type),
                now = currentTime,
            )
        }
    }
}
