package com.bios.app.data

import com.bios.app.model.DataSource
import com.bios.app.model.ReadingKind
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType

/**
 * Resolves (creating if absent) the single SELF_REPORTED row in the main
 * [BiosDatabase] that owns every manually entered reading. Shared by
 * [BiomarkerEntryRepo], [ManualReadingRepo], and [SleepEntryRepo] so they
 * all converge on one source id — without this, each repo would create its
 * own SELF_REPORTED row and the data-coverage view would double-count.
 *
 * The reproductive flow ([BbtEntryRepo], [PeriodEntryRepo]) has its own
 * helper because reproductive rows live in the isolated ReproductiveDatabase
 * (separate encryption key, independent wipe).
 */
internal suspend fun resolveSelfReportedSource(db: BiosDatabase): String {
    val dao = db.dataSourceDao()
    dao.findByType(SourceType.SELF_REPORTED.key)?.let { return it.id }
    val source = DataSource(
        sourceType = SourceType.SELF_REPORTED.key,
        deviceName = "Self-reported",
        sensorType = SensorType.DERIVED.name,
        readingKind = ReadingKind.SELF_REPORTED.name
    )
    dao.insert(source)
    return source.id
}
