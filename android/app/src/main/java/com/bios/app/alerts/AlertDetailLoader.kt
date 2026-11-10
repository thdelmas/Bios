package com.bios.app.alerts

import com.bios.app.data.BiosDatabase
import com.bios.app.model.Anomaly
import com.bios.app.model.DataSource
import com.bios.app.model.ReadingKind

/** An alert plus the measured signals around it, ready for the detail screen. */
data class AlertDetail(
    val anomaly: Anomaly,
    /**
     * Every watched metric, deviating first: as stored at detection when the
     * alert carries a snapshot, else recomputed from today's readings.
     */
    val signals: List<MeasuredSignal>,
    /** The same windows recomputed from today's readings, by metric key. */
    val now: Map<String, MeasuredSignal> = emptyMap(),
)

/**
 * What the detector saw when an alert fired. Alerts raised since v37 carry a
 * snapshot of every watched signal (value, range, z, reading count) taken at
 * detection; that is what the screen shows. The same 24 h windows are also
 * recomputed from today's readings, with reading counts and source names, so
 * the screen can say "now …" when a later sync changed the window. Older
 * alerts have only the recomputation (criterion 6).
 */
class AlertDetailLoader(db: BiosDatabase) {
    private val anomalyDao = db.anomalyDao()
    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val sourceDao = db.dataSourceDao()

    suspend fun load(anomalyId: String): AlertDetail? {
        val anomaly = anomalyDao.fetchAll().firstOrNull { it.id == anomalyId } ?: return null
        val end = anomaly.detectedAt
        val start = end - AlertDetailContent.WINDOW_MILLIS
        // Same filter as HolisticSignals / fetchValues(kind = SENSOR): primary
        // rows from sources whose readingKind is SENSOR.
        val sensorSources = sourceDao.getAll()
            .filter { it.readingKind == ReadingKind.SENSOR.name }
            .associateBy { it.id }
        val live = AlertDetailContent.WATCHED_METRICS.mapNotNull { key ->
            val baseline = baselineDao.fetch(key) ?: return@mapNotNull null
            val readings = readingDao.fetch(key, start, end).filter { it.sourceId in sensorSources }
            if (readings.isEmpty()) return@mapNotNull null
            val names = readings.map { it.sourceId }.distinct().map { SourceLabel.of(sensorSources.getValue(it)) }
            AlertDetailContent.signal(
                key, readings.map { it.value }.average(), baseline, readings.size, names.distinct(),
            )
        }
        val stored = SignalSnapshot.decode(anomaly.signalSnapshot)
        val signals = stored.ifEmpty { live }.sortedWith(
            compareByDescending<MeasuredSignal> { it.deviating }.thenByDescending { kotlin.math.abs(it.zScore) },
        )
        return AlertDetail(anomaly, signals, live.associateBy { it.metricKey })
    }
}

/** Owner-facing name of a source on the alert screen; mirrors Known Sources. */
internal object SourceLabel {
    fun of(source: DataSource): String {
        val name = source.deviceName
        val model = source.deviceModel
        return when {
            // A model holding a package name is a key, not a label.
            !name.isNullOrBlank() && !model.isNullOrBlank() && model != name && !model.contains('.') ->
                "$name ($model)"
            !name.isNullOrBlank() -> name
            else -> com.bios.app.model.SourceType.entries.firstOrNull { it.key == source.sourceType }
                ?.let { com.bios.app.ui.settings.KnownSourcesViewModel.typeLabel(it) }
                ?: source.sourceType
        }
    }
}
