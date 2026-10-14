package com.bios.app.ingest

import com.bios.app.data.dao.MetricReadingDao
import com.bios.contracts.MetricType
import java.util.concurrent.TimeUnit

/**
 * One-time, idempotent move of Health Connect count rows from the single
 * "Health Connect" source to one source per writing app
 * (docs/specs/source-preference.md, criterion 1).
 *
 * Step 1 re-keys the rows in place: `hc-steps-{hcSource}-{record}` becomes
 * `hc-steps-{record}`, the key the per-app read now produces, so the
 * re-read REPLACEs each row with its per-app source instead of adding a
 * twin. Step 2 reports whether rows inside the 30-day re-read window still
 * sit on the generic source, which is the signal to run the historical
 * sync. Nothing is deleted; rows older than the window keep the generic
 * source, which sits in the order like any other.
 */
internal object HealthConnectOriginSplit {

    private val KINDS = listOf(
        HealthConnectReads.STEPS_KIND to MetricType.STEPS.key,
        HealthConnectReads.ACTIVE_CALORIES_KIND to MetricType.ACTIVE_CALORIES.key,
    )
    private val WINDOW_MS = TimeUnit.DAYS.toMillis(30)

    /** Re-keys and returns true when a 30-day re-read is still owed. */
    suspend fun run(readingDao: MetricReadingDao, hcSourceId: String, now: Long): Boolean {
        var rekeyed = 0
        var pending = 0
        for ((kind, metric) in KINDS) {
            rekeyed += runCatching {
                readingDao.rekeyIds(
                    oldPrefix = HealthConnectStableIds.sourcedPrefix(kind, hcSourceId),
                    newPrefix = HealthConnectStableIds.countPrefix(kind),
                )
            }.getOrDefault(0)
            pending += readingDao.countBySourceInRange(hcSourceId, metric, now - WINDOW_MS, now)
        }
        if (rekeyed > 0 || pending > 0) {
            IngestTelemetry.log("origin split: rekeyed=$rekeyed genericRowsInWindow=$pending")
        }
        return pending > 0
    }
}
