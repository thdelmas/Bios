package com.bios.app.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bios.app.data.BiosDatabase
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import com.bios.contracts.MetricType
import java.util.concurrent.TimeUnit

/**
 * Daily worker that runs the chronic-migraine screen (#283 Cut 3 /
 * IHS ICHD-3 §1.3) over the rolling diary window.
 *
 * Mirrors [MohScreeningWorker] one-to-one — the chronic-migraine
 * pattern shares the same registration shape (empty `signalRules`,
 * ADVISORY severity, evaluated by a dedicated pure-Kotlin scorer)
 * and benefits from the same fetch → evaluate → dedup → insert
 * pipeline.
 *
 * Reads from the `metric_readings` view written by
 * [com.bios.app.data.HeadacheEventWriter]. The split between this
 * worker and [MohScreeningWorker] is deliberate: the MOH worker
 * reads `MigraineAttack` / `HeadacheLog` entity rows (which carry
 * the freetext `medicationTaken` field the existing evaluator
 * counts) while this worker reads the cross-cutting structured
 * headache-event rows the chronic-migraine evaluator's day-counting
 * logic needs.
 *
 * Dedup, cadence, and Anomaly construction all mirror MOH —
 * deliberate, so the two chronic-pattern alerts surface uniformly.
 */
class ChronicMigraineWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = BiosDatabase.getInstance(context)
        val readingDao = db.metricReadingDao()

        val now = System.currentTimeMillis()
        val windowStart = now - HeadachePatterns.MOH_WINDOW_DAYS * MS_PER_DAY
        // Three event types feed this evaluator; one fetch per metric is
        // cheaper than a JOIN-on-IN-clause for the volumes we see.
        val rows = buildList {
            addAll(readingDao.fetch(MetricType.MIGRAINE_ATTACK_EVENT.key, windowStart, now))
            addAll(readingDao.fetch(MetricType.HEADACHE_ATTACK_EVENT.key, windowStart, now))
            addAll(readingDao.fetch(MetricType.CLUSTER_HEADACHE_ATTACK_EVENT.key, windowStart, now))
        }

        val verdict = ChronicMigraineEvaluator.evaluate(rows, nowMillis = now)

        val decision = decideFireOrSkip(
            verdict = verdict,
            previousState = readPreviousState(context),
            previouslyAcked = wasPreviouslyAcked(context, db),
        )
        when (decision) {
            FireDecision.SuppressVerdictFalse -> {
                writePreviousState(context, verdictTrue = false, lastAnomalyId = null)
            }
            FireDecision.SuppressVerdictUnchanged -> {
                // Keep the existing state intact — the previously-fired
                // Anomaly is still the active surface.
            }
            FireDecision.Fire -> {
                val anomaly = buildAnomaly(verdict, now)
                db.anomalyDao().insert(anomaly)
                writePreviousState(context, verdictTrue = true, lastAnomalyId = anomaly.id)
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "bios_chronic_migraine_screening"
        private const val MS_PER_DAY: Long = 24L * 60L * 60L * 1000L

        private const val PREFS_NAME = "bios_chronic_migraine_state"
        private const val KEY_LAST_VERDICT_TRUE = "last_verdict_true"
        private const val KEY_LAST_ANOMALY_ID = "last_anomaly_id"

        /** Daily cadence. Idempotent via WorkManager unique-work policy.
         *  Mirrors [MohScreeningWorker.schedule]. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChronicMigraineWorker>(
                24, TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        internal data class PreviousState(
            val verdictTrue: Boolean,
            val lastAnomalyId: String?,
        )

        internal sealed interface FireDecision {
            object Fire : FireDecision
            object SuppressVerdictFalse : FireDecision
            object SuppressVerdictUnchanged : FireDecision
        }

        internal fun decideFireOrSkip(
            verdict: ChronicMigraineEvaluator.Verdict,
            previousState: PreviousState,
            previouslyAcked: Boolean,
        ): FireDecision {
            if (!verdict.meetsScreeningThreshold) return FireDecision.SuppressVerdictFalse
            if (!previousState.verdictTrue) return FireDecision.Fire
            if (previouslyAcked) return FireDecision.Fire
            return FireDecision.SuppressVerdictUnchanged
        }

        internal fun buildAnomaly(
            verdict: ChronicMigraineEvaluator.Verdict,
            nowMillis: Long,
        ): Anomaly {
            val pattern = HeadachePatterns.chronicMigraineThreshold
            val substrate = ChronicMigraineEvaluator.describeVerdict(verdict)
            return Anomaly(
                detectedAt = nowMillis,
                metricTypes = "[]",
                deviationScores = "{}",
                combinedScore = 1.0,
                patternId = pattern.id,
                severity = AlertTier.ADVISORY.level,
                title = pattern.title,
                explanation = "${pattern.explanation}\n\n$substrate",
                suggestedAction = pattern.suggestedAction,
            )
        }

        private fun readPreviousState(context: Context): PreviousState {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return PreviousState(
                verdictTrue = prefs.getBoolean(KEY_LAST_VERDICT_TRUE, false),
                lastAnomalyId = prefs.getString(KEY_LAST_ANOMALY_ID, null),
            )
        }

        private fun writePreviousState(
            context: Context,
            verdictTrue: Boolean,
            lastAnomalyId: String?,
        ) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LAST_VERDICT_TRUE, verdictTrue)
                .apply {
                    if (lastAnomalyId == null) remove(KEY_LAST_ANOMALY_ID)
                    else putString(KEY_LAST_ANOMALY_ID, lastAnomalyId)
                }
                .apply()
        }

        private suspend fun wasPreviouslyAcked(context: Context, db: BiosDatabase): Boolean {
            val state = readPreviousState(context)
            val id = state.lastAnomalyId ?: return false
            return db.anomalyDao().fetchRecent(50).firstOrNull { it.id == id }?.acknowledged == true
        }
    }
}
