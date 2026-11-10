package com.bios.app.ingest

import com.bios.app.data.dao.DataSourceDao
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.MetricReading
import com.bios.app.model.SourceType
import java.util.concurrent.TimeUnit

/**
 * Settle rule for re-delivered day-values (docs/specs/alert-detail.md,
 * criterion 8). A vendor cloud source sends the same (source, metric, day)
 * reading on every sync and may revise it hours later; the 06-11 alert fired
 * on a resting heart rate the watch later revised from 69 to 63. Such a
 * reading is `provisional` until it has been seen unchanged across two syncs
 * at least [SETTLE_GAP_MILLIS] apart; a changed value restarts the count.
 * Engine queries skip provisional rows. Other sources are untouched.
 */
class ReadingRevisions(
    private val readingDao: MetricReadingDao,
    private val sourceDao: DataSourceDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun reconcile(incoming: List<MetricReading>): List<MetricReading> {
        val redelivering = sourceDao.getAll()
            .filter { s -> SourceType.entries.firstOrNull { it.key == s.sourceType }?.let(SourceCadence::redeliversDailyValues) == true }
            .map { it.id }
            .toSet()
        if (redelivering.isEmpty()) return incoming
        val (watched, rest) = incoming.partition { it.sourceId in redelivering }
        if (watched.isEmpty()) return incoming
        val existing = watched.groupBy { it.metricType }.flatMap { (metric, rows) ->
            readingDao.fetch(metric, rows.minOf { it.timestamp }, rows.maxOf { it.timestamp })
        }.associateBy { key(it) }
        val now = clock()
        return rest + watched.map { apply(it, existing[key(it)], now) }
    }

    private fun key(r: MetricReading) = Triple(r.sourceId, r.metricType, r.timestamp)

    companion object {
        /** Two sightings closer than this are the same sync for settling purposes. */
        val SETTLE_GAP_MILLIS: Long = TimeUnit.HOURS.toMillis(6)

        /** Pure step: the row to write for [incoming] given what is stored for the same key. */
        fun apply(incoming: MetricReading, existing: MetricReading?, now: Long): MetricReading {
            val unchanged = existing != null && existing.value == incoming.value
            val seenCount = if (unchanged) existing!!.seenCount + 1 else 1
            val firstSeenAt = if (unchanged) (existing!!.firstSeenAt ?: existing.createdAt) else now
            val settled = seenCount >= 2 && now - firstSeenAt >= SETTLE_GAP_MILLIS
            return incoming.copy(
                id = existing?.id ?: incoming.id,
                createdAt = existing?.createdAt ?: incoming.createdAt,
                seenCount = seenCount,
                firstSeenAt = firstSeenAt,
                provisional = !settled,
            )
        }
    }
}
