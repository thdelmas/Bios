package com.bios.app.validation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.AnomalyDetector
import com.bios.app.engine.BaselineEngine
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.ReadingKind
import com.bios.contracts.MetricType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Validation replay: runs the shipped BaselineEngine + AnomalyDetector day by
 * day over the Mishra et al. (2020) Fitbit dataset (32 COVID-19-positive
 * participants with symptom-onset dates) and scores `infection_onset`
 * alerts against onset. Produces the first measured sensitivity /
 * false-alarm / lead-time figures for the pattern.
 *
 * Skipped unless `BIOS_MISHRA_DIR` points at the output of
 * `tools/validation/mishra_prep.py` (readings.csv + labels.csv). The raw
 * dataset is 3.4 GB and is not vendored.
 *
 * Optional: `BIOS_MISHRA_USERS` = comma-separated participant ids to limit the run.
 * Report: app/build/reports/validation/mishra-replay.md
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MishraReplayTest {

    private data class Row(val metric: String, val ts: Long, val value: Double, val durationSec: Int)
    private data class Label(
        val user: String,
        val category: String,
        val onset: LocalDate?,
        val diagnosis: LocalDate?,
        val recovery: LocalDate?,
    )
    private data class Alert(val day: LocalDate, val patternId: String, val severity: Int, val score: Double)
    private data class DayZ(val day: LocalDate, val rhr: Double?, val steps: Double?, val sleep: Double?)
    private data class UserResult(
        val label: Label,
        val monitoredDays: Int,
        val alerts: List<Alert>,
        val z: List<DayZ>,
    )

    private val infectionId = "infection_onset"
    private val preWindowDays = 14L   // Mishra 2020: detection window starts 14 d before onset
    private val postWindowDays = 7L
    private val healthyGapDays = 21L  // healthy window = > 21 d before onset or > 7 d after recovery
    /** Baseline window in days; `BIOS_MISHRA_WINDOW_DAYS` overrides the engine default (14) for what-if runs. */
    private val windowDays = System.getenv("BIOS_MISHRA_WINDOW_DAYS")?.toIntOrNull() ?: BaselineEngine.DEFAULT_WINDOW_DAYS

    @Test
    fun replayMishra2020() = runBlocking {
        val dir = System.getenv("BIOS_MISHRA_DIR")
        val hasData = !dir.isNullOrBlank() && File(dir, "readings.csv").exists()
        assumeTrue("BIOS_MISHRA_DIR not set; replay skipped", hasData)
        val onlyUsers = System.getenv("BIOS_MISHRA_USERS")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()

        val labels = readLabels(File(dir, "labels.csv"))
            .filter { it.category == "COVID-19" && it.onset != null }
            .filter { onlyUsers == null || it.user in onlyUsers }
        assumeTrue("no COVID-19 labels found", labels.isNotEmpty())

        val readings = readReadings(File(dir, "readings.csv"), labels.map { it.user }.toSet())
        val results = labels.mapNotNull { label ->
            val rows = readings[label.user] ?: return@mapNotNull null
            val r = replayUser(label, rows)
            val infectionAlerts = r.alerts.count { it.patternId == infectionId }
            println("replay ${label.user}: days=${r.monitoredDays} alerts=${r.alerts.size} infection=$infectionAlerts")
            r
        }
        assertTrue("no participant had usable readings", results.isNotEmpty())

        val report = buildReport(results)
        val out = File("build/reports/validation/mishra-replay.md")
        out.parentFile.mkdirs()
        out.writeText(report)
        println(report)
    }

    // MARK: - Replay

    private suspend fun replayUser(label: Label, rows: List<Row>): UserResult {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, BiosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val sourceId = "fitbit-replay"
            db.dataSourceDao().insert(
                DataSource(
                    id = sourceId,
                    sourceType = "health_connect",
                    deviceName = "Fitbit (Mishra 2020 replay)",
                    sensorType = "WEARABLE",
                    readingKind = ReadingKind.SENSOR.name,
                )
            )
            rows.chunked(5000).forEach { chunk ->
                db.metricReadingDao().insertAll(chunk.map {
                    MetricReading(
                        metricType = it.metric,
                        value = it.value,
                        timestamp = it.ts,
                        durationSec = if (it.durationSec > 0) it.durationSec else null,
                        sourceId = sourceId,
                        confidence = ConfidenceTier.MEDIUM.level,
                    )
                })
            }

            var now = 0L
            val clock: () -> Long = { now }
            val baselines = BaselineEngine(db, clock = clock)
            val detector = AnomalyDetector(
                db = db,
                mlModel = null,
                latencyTracker = null,
                acuteWindowDetector = null,
                clock = clock,
            )
            val tracked = listOf(MetricType.RESTING_HEART_RATE, MetricType.STEPS, MetricType.SLEEP_STAGE)

            val rhrDays = rows.filter { it.metric == MetricType.RESTING_HEART_RATE.key }
                .map { LocalDate.ofEpochDay(it.ts / 86_400_000L) }
            val first = rhrDays.min()
            val last = rhrDays.max()

            val alerts = mutableListOf<Alert>()
            val zs = mutableListOf<DayZ>()
            var monitored = 0
            var day = first
            while (!day.isAfter(last)) {
                now = day.atTime(23, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
                for (m in tracked) baselines.computeBaseline(m, windowDays = windowDays)
                val fired = detector.runDetection()
                fired.forEach { a ->
                    alerts += Alert(day, a.patternId ?: "ml", a.severity, a.combinedScore)
                }
                val z = dayZ(db, day, now)
                zs += z
                if (z.rhr != null) monitored++
                day = day.plusDays(1)
            }
            return UserResult(label, monitored, alerts, zs)
        } finally {
            db.close()
        }
    }

    /** Mirrors evaluatePattern's z computation: mean of the last 24 h vs. the stored baseline. */
    private suspend fun dayZ(db: BiosDatabase, day: LocalDate, now: Long): DayZ {
        suspend fun z(m: MetricType): Double? {
            val b = db.personalBaselineDao().fetch(m.key) ?: return null
            val v = db.metricReadingDao().fetchValues(m.key, now - 24L * 3600_000, now, ReadingKind.SENSOR.name)
            if (v.isEmpty()) return null
            return b.zScore(v.average())
        }
        return DayZ(day, z(MetricType.RESTING_HEART_RATE), z(MetricType.STEPS), z(MetricType.SLEEP_STAGE))
    }

    // MARK: - Scoring

    private data class Score(
        val user: String,
        val onset: LocalDate,
        val monitoredDays: Int,
        val preWindowMonitored: Int,
        val firstPreAlert: LocalDate?,
        val firstPostAlert: LocalDate?,
        val healthyDays: Int,
        val healthyInfectionAlerts: Int,
        val healthyAnyAlerts: Int,
        val rhrRuleHitPre: Boolean,
        val stepsRuleHitPre: Boolean,
        val sleepRuleHitPre: Boolean,
        val maxActivePre: Int,
        val firstRhrHitPre: LocalDate?,
        val healthyRhrHits: Int,
    )

    private class Windows(
        label: Label,
        private val postWindowDays: Long,
        private val preWindowDays: Long,
        private val healthyGapDays: Long,
    ) {
        val onset: LocalDate = label.onset!!
        private val preStart = onset.minusDays(preWindowDays)
        private val postEnd = onset.plusDays(postWindowDays)
        private val healthyEnd = label.recovery?.plusDays(postWindowDays) ?: postEnd
        fun isHealthy(d: LocalDate) =
            d.isBefore(onset.minusDays(healthyGapDays)) || d.isAfter(healthyEnd)
        fun inPre(d: LocalDate) = !d.isBefore(preStart) && !d.isAfter(onset)
        fun inPost(d: LocalDate) = d.isAfter(onset) && !d.isAfter(postEnd)
    }

    // Shipped rule thresholds (BaselineDeviationPatterns.infectionOnset):
    // RHR > 1.5σ, STEPS < -1.0σ, SLEEP_STAGE < -1.0σ
    private fun rhrHit(z: DayZ) = (z.rhr ?: 0.0) > 1.5
    private fun stepsHit(z: DayZ) = (z.steps ?: 0.0) < -1.0
    private fun sleepHit(z: DayZ) = (z.sleep ?: 0.0) < -1.0
    private fun activeRules(z: DayZ) = listOf(rhrHit(z), stepsHit(z), sleepHit(z)).count { it }

    private fun score(r: UserResult): Score {
        val w = Windows(r.label, postWindowDays, preWindowDays, healthyGapDays)
        val infection = r.alerts.filter { it.patternId == infectionId }
        val monitoredDays = r.z.filter { it.rhr != null }.map { it.day }
        val healthyDays = monitoredDays.filter(w::isHealthy)
        val preZ = r.z.filter { w.inPre(it.day) }
        return Score(
            user = r.label.user,
            onset = w.onset,
            monitoredDays = monitoredDays.size,
            preWindowMonitored = monitoredDays.count(w::inPre),
            firstPreAlert = infection.filter { w.inPre(it.day) }.minOfOrNull { it.day },
            firstPostAlert = infection.filter { w.inPost(it.day) }.minOfOrNull { it.day },
            healthyDays = healthyDays.size,
            healthyInfectionAlerts = infection.count { w.isHealthy(it.day) },
            healthyAnyAlerts = r.alerts.count { w.isHealthy(it.day) },
            rhrRuleHitPre = preZ.any(::rhrHit),
            stepsRuleHitPre = preZ.any(::stepsHit),
            sleepRuleHitPre = preZ.any(::sleepHit),
            maxActivePre = preZ.maxOfOrNull(::activeRules) ?: 0,
            firstRhrHitPre = preZ.filter(::rhrHit).minOfOrNull { it.day },
            healthyRhrHits = r.z.count { w.isHealthy(it.day) && rhrHit(it) },
        )
    }

    private fun row(vararg cells: Any?) =
        cells.joinToString(" | ", prefix = "| ", postfix = " |") { it?.toString() ?: "-" }

    /** Aggregates computed once from the per-participant scores. */
    private inner class Stats(val results: List<UserResult>) {
        val scores = results.map(::score)
        val evaluable = scores.filter { it.preWindowMonitored > 0 }
        val detectedPre = evaluable.filter { it.firstPreAlert != null }
        val detectedPost = evaluable.filter { it.firstPreAlert == null && it.firstPostAlert != null }
        val leads = detectedPre.map { ChronoUnit.DAYS.between(it.firstPreAlert, it.onset) }.sorted()
        val healthyDays = scores.sumOf { it.healthyDays }
        val healthyInfection = scores.sumOf { it.healthyInfectionAlerts }
        val healthyAny = scores.sumOf { it.healthyAnyAlerts }
        val rhrDetected = evaluable.filter { it.firstRhrHitPre != null }
        val rhrLeads = rhrDetected.map { ChronoUnit.DAYS.between(it.firstRhrHitPre, it.onset) }.sorted()
        val rhrHealthy = scores.sumOf { it.healthyRhrHits }
        fun perMonth(n: Int) = if (healthyDays == 0) "n/a" else "%.2f".format(n * 30.0 / healthyDays)
        fun pct(n: Int, d: Int) = if (d == 0) "n/a" else "%d/%d (%.0f%%)".format(n, d, 100.0 * n / d)
        fun median(xs: List<Long>) = if (xs.isEmpty()) "n/a" else xs[xs.size / 2].toString()
        fun range(xs: List<Long>) = if (xs.isEmpty()) "n/a" else "${xs.first()}–${xs.last()}"
        fun hits(sel: (Score) -> Boolean) = pct(evaluable.count(sel), evaluable.size)
    }

    private fun buildReport(results: List<UserResult>): String {
        val st = Stats(results)
        val sb = StringBuilder()
        sb.appendLine("# Mishra 2020 replay — `infection_onset` pattern (baseline window $windowDays d)")
        sb.appendLine()
        sb.appendLine(
            "Shipped engine (BaselineEngine $windowDays-day window, AnomalyDetector, all applicable patterns) replayed " +
                "day by day, clock at 23:00 UTC, over Fitbit data of COVID-19-positive participants from " +
                "Mishra et al. (2020), Nat Biomed Eng. Inputs: daily resting HR (mean of 00:00–07:00 HR samples " +
                "with zero steps in the preceding 12 min), hourly steps, sleep-stage segments. No HRV, skin " +
                "temperature or respiratory rate exist in this dataset, so at most 3 of the pattern's 6 signals " +
                "can ever be active; the pattern requires 3."
        )
        sb.appendLine()
        summarySection(sb, st)
        ruleSection(sb, st)
        rhrSection(sb, st)
        participantSection(sb, st)
        patternSection(sb, results)
        return sb.toString()
    }

    private fun summarySection(sb: StringBuilder, st: Stats) = with(st) {
        sb.appendLine(row("Figure", "Value"))
        sb.appendLine("|---|---|")
        sb.appendLine(row("Participants replayed", scores.size))
        sb.appendLine(row("Participants with monitored days in the pre-symptomatic window (onset−14 d … onset)", evaluable.size))
        sb.appendLine(row("Sensitivity, pre-symptomatic (`infection_onset` alert in window)", pct(detectedPre.size, evaluable.size)))
        sb.appendLine(row("Late detection only (onset+1 … onset+7)", pct(detectedPost.size, evaluable.size)))
        sb.appendLine(row("Median lead time, days before onset (detected cases)", median(leads)))
        sb.appendLine(row("Healthy person-days (>21 d before onset or >7 d after recovery)", healthyDays))
        val fa = "${perMonth(healthyInfection)} ($healthyInfection alerts)"
        sb.appendLine(row("`infection_onset` false alarms per person-month, healthy days", fa))
        sb.appendLine(row("Any-pattern alerts per person-month, healthy days", "${perMonth(healthyAny)} ($healthyAny alerts)"))
        sb.appendLine()
    }

    private fun ruleSection(sb: StringBuilder, st: Stats) = with(st) {
        sb.appendLine("## Rule-level diagnostic (pre-symptomatic window, shipped thresholds)")
        sb.appendLine()
        sb.appendLine(row("Rule", "Participants with ≥1 hit"))
        sb.appendLine("|---|---|")
        sb.appendLine(row("RHR > +1.5σ (24 h mean vs $windowDays d baseline)", hits { it.rhrRuleHitPre }))
        sb.appendLine(row("Steps < −1.0σ", hits { it.stepsRuleHitPre }))
        sb.appendLine(row("Sleep-stage mean < −1.0σ", hits { it.sleepRuleHitPre }))
        sb.appendLine(row("≥3 rules active on the same day (the pattern's gate)", hits { it.maxActivePre >= 3 }))
        sb.appendLine(row("≥2 rules active on the same day", hits { it.maxActivePre >= 2 }))
        sb.appendLine()
    }

    private fun rhrSection(sb: StringBuilder, st: Stats) = with(st) {
        sb.appendLine("## RHR rule alone (what an RHR-led gate would score on this data)")
        sb.appendLine()
        sb.appendLine(row("Figure", "Value"))
        sb.appendLine("|---|---|")
        val sens = pct(rhrDetected.size, evaluable.size)
        sb.appendLine(row("Sensitivity, pre-symptomatic (RHR > +1.5σ on ≥1 day in onset−14 d … onset)", sens))
        sb.appendLine(row("Median lead time, days before onset", median(rhrLeads)))
        sb.appendLine(row("Lead-time range", range(rhrLeads)))
        val fa = "${perMonth(rhrHealthy)} ($rhrHealthy days)"
        sb.appendLine(row("False alarms per person-month, healthy days (days with RHR > +1.5σ)", fa))
        sb.appendLine(row("Reference: Mishra 2020 CuSum, same data", "62.5% on or before onset; ~0.66 alarms/month healthy"))
        sb.appendLine()
    }

    private fun participantSection(sb: StringBuilder, st: Stats) {
        sb.appendLine("## Per participant")
        sb.appendLine()
        sb.appendLine(
            row(
                "User", "Onset", "Monitored d", "Pre-window d", "First pre alert", "First post alert", "Healthy d",
                "Healthy infection alerts", "Healthy any alerts", "RHR hit", "Steps hit", "Sleep hit", "Max active",
            )
        )
        sb.appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        for (s in st.scores) {
            sb.appendLine(
                row(
                    s.user, s.onset, s.monitoredDays, s.preWindowMonitored, s.firstPreAlert, s.firstPostAlert,
                    s.healthyDays, s.healthyInfectionAlerts, s.healthyAnyAlerts,
                    s.rhrRuleHitPre, s.stepsRuleHitPre, s.sleepRuleHitPre, s.maxActivePre,
                )
            )
        }
        sb.appendLine()
    }

    private fun patternSection(sb: StringBuilder, results: List<UserResult>) {
        sb.appendLine("## Other patterns fired (all days, all participants)")
        sb.appendLine()
        val byPattern = results.flatMap { it.alerts }.groupBy { it.patternId }
            .mapValues { it.value.size }.toList().sortedByDescending { it.second }
        sb.appendLine(row("Pattern", "Alerts"))
        sb.appendLine("|---|---|")
        for ((p, n) in byPattern) sb.appendLine(row(p, n))
    }

    // MARK: - IO

    private fun readLabels(f: File): List<Label> {
        fun d(s: String) = s.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        return f.readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val c = line.split(",")
            Label(c[0], c[1], d(c[2]), d(c[3]), d(c[4]))
        }
    }

    private fun readReadings(f: File, users: Set<String>): Map<String, List<Row>> {
        val out = HashMap<String, MutableList<Row>>()
        f.bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                val c = line.split(",")
                if (c.size < 5 || c[0] !in users) return@forEach
                out.getOrPut(c[0]) { mutableListOf() } += Row(c[1], c[2].toLong(), c[3].toDouble(), c[4].toInt())
            }
        }
        return out
    }
}
