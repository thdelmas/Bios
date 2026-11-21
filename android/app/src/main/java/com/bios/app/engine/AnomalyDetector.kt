package com.bios.app.engine

import com.bios.app.alerts.AlertDetailContent
import com.bios.app.alerts.SignalSnapshot
import com.bios.app.data.BiosDatabase
import com.bios.app.model.Anomaly
import kotlin.math.abs

/**
 * Raises one kind of alert: watched metrics outside the owner's usual range.
 * Each watched metric's last 24 h of sensor readings is compared with the
 * owner's own baseline; [DeviationScore] turns the count and size of the
 * deviations into a score, and the alert states the measured values. Nothing
 * here names a cause or reads a meaning into the numbers.
 */
class AnomalyDetector(
    db: BiosDatabase,
    private val latencyTracker: DetectionLatencyTracker? = null,
    /** Engine clock; injectable so replays can step through historical days. */
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val anomalyDao = db.anomalyDao()

    suspend fun runDetection(): List<Anomaly> {
        val start = System.currentTimeMillis()
        val anomaly = if (latencyTracker != null) {
            latencyTracker.track(PipelineStage.DEVIATION_SCORING) { detectDeviation() }
        } else {
            detectDeviation()
        } ?: return emptyList()
        anomalyDao.insert(anomaly)
        // Notification delivery is tracked separately.
        latencyTracker?.record(PipelineStage.END_TO_END, System.currentTimeMillis() - start)
        return listOf(anomaly)
    }

    private suspend fun detectDeviation(): Anomaly? {
        val now = clock()
        val timed = HolisticSignals.current(readingDao, baselineDao, now)
        val signals = timed.mapValues { it.value.first }
        val zScores = signals.mapValues { it.value.zScore }
        if (zScores.isEmpty()) return null

        val score = DeviationScore.of(zScores)
        if (score < DeviationScore.THRESHOLD) return null

        // Cool-down: one alert per 24 hours, and never on evidence an earlier alert already used.
        val recent = anomalyDao.fetchRecent(RECENT_LOOKBACK)
        if (recent.any { it.patternId == ML_PATTERN_ID && now - it.detectedAt < COOLDOWN_MILLIS }) return null
        val deviating = zScores.filter { (_, z) -> abs(z) > AlertDetailContent.DEVIATION_Z }
        val newest = deviating.keys.mapNotNull { timed[it]?.second }.maxOrNull()
        if (newest != null && WindowCoverage.evidenceAlreadyAlerted(deviating.keys, newest, recent)) return null

        return Anomaly(
            detectedAt = now,
            metricTypes = "[${deviating.keys.joinToString(",") { "\"$it\"" }}]",
            deviationScores = "{${deviating.entries.joinToString(",") { "\"${it.key}\":${it.value}" }}}",
            combinedScore = score.toDouble(),
            patternId = ML_PATTERN_ID,
            severity = classifyHolistic(score, deviating.values.maxOfOrNull { abs(it) } ?: 0.0).level,
            title = TITLE,
            explanation = HolisticSignals.explanation(deviating.keys.mapNotNull { signals[it] }),
            suggestedAction = null,
            signalSnapshot = SignalSnapshot.encode(signals.values),
        )
    }

    companion object {
        /** Stored on every alert row; the identifier predates the removal of the trained model. */
        const val ML_PATTERN_ID = "ml_holistic_anomaly"

        const val TITLE = "Outside your usual range"

        private const val COOLDOWN_MILLIS = 24 * 3600 * 1000L
        private const val RECENT_LOOKBACK = 10
    }
}
