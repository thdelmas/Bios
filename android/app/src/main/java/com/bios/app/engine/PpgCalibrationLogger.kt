package com.bios.app.engine

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Opt-in diagnostic logger that captures per-session signal-quality numbers
 * from camera-PPG recordings so device-specific motion thresholds can be set
 * from real distributions (#266 Cut 1) rather than eyeballed.
 *
 * Every accepted *or* rejected capture appends one row to a CSV file in the
 * app's private storage when [isEnabled] returns true. The default is `off`;
 * the toggle lives in `Settings → Diagnostics`. Rows contain only signal
 * characteristics, the device model, and the API level — no health data,
 * timestamps, or anything that could re-identify the owner beyond what
 * `Build.MODEL` already exposes to every app.
 *
 * Cut 2 (a device-keyed threshold table that consumes these rows) lives in
 * a follow-up issue; the logger itself does not interpret what it writes.
 *
 * The logger lives entirely in app-private storage (`filesDir`); the owner
 * exports it via standard share / file-manager flows when they want to send
 * a calibration sample upstream.
 */
class PpgCalibrationLogger(context: Context) {

    private val context = context.applicationContext

    /** Append [row] to the calibration log. No-op when [isEnabled] is false. */
    fun log(row: PpgCalibrationRow) {
        if (!isEnabled(context)) return
        appendTo(logFile(context), row)
    }

    companion object {
        /** Header line written once when the log file is first created. */
        const val HEADER = "timestamp_ms,device_model,android_api,sampling_rate_hz," +
            "recent_median_jump_y_p50,recent_median_jump_y_p90," +
            "peak_amplitude_cov,saturation_ratio,peak_count,duration_sec,rejection_reason"

        private const val PREFS_NAME = "bios_ppg_calibration"
        private const val KEY_ENABLED = "enabled"
        private const val LOG_FILENAME = "ppg_calibration.csv"

        /** Off by default — owner must explicitly enable from Settings. */
        fun isEnabled(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
        }

        /** Resolved path the in-process logger writes to. Exposed so the
         *  Settings screen can show "log size" / offer share. */
        fun logFile(context: Context): File =
            File(context.applicationContext.filesDir, LOG_FILENAME)

        /**
         * Append [row] to [file], writing [HEADER] first when the file is
         * being created. Pure I/O — exposed so tests can exercise it with a
         * temp file without an Android Context.
         */
        internal fun appendTo(file: File, row: PpgCalibrationRow) {
            val needsHeader = !file.exists()
            file.parentFile?.mkdirs()
            file.appendText(buildString {
                if (needsHeader) append(HEADER).append('\n')
                append(formatRow(row)).append('\n')
            })
        }

        /**
         * CSV serialisation of a single row. Stable column order matches
         * [HEADER]; null [PpgCalibrationRow.peakAmplitudeCov] /
         * [PpgCalibrationRow.rejectionReason] render as empty fields so the
         * file parses with the standard CSV `null = ""` convention. Numbers
         * use a fixed decimal locale so analysis tools see `0.42` regardless
         * of the device's locale (which would otherwise emit `0,42` in fr-FR
         * and break downstream parsers).
         */
        internal fun formatRow(row: PpgCalibrationRow): String = listOf(
            row.timestampMs.toString(),
            csvEscape(row.deviceModel),
            row.androidApi.toString(),
            fmt(row.samplingRateHz),
            fmt(row.recentMedianJumpYP50),
            fmt(row.recentMedianJumpYP90),
            row.peakAmplitudeCov?.let { fmt(it) } ?: "",
            fmt(row.saturationRatio),
            row.peakCount.toString(),
            fmt(row.durationSec),
            row.rejectionReason ?: "",
        ).joinToString(",")

        private fun fmt(d: Double): String = "%.4f".format(java.util.Locale.ROOT, d)

        private fun csvEscape(s: String): String =
            if (s.contains(',') || s.contains('"') || s.contains('\n')) {
                "\"" + s.replace("\"", "\"\"") + "\""
            } else {
                s
            }

        /** Convenience constructor for the adapter's hot path. */
        fun rowFor(
            timestampMs: Long,
            samplingRateHz: Double,
            jumpStats: MedianJumpStats?,
            peakAmplitudeCov: Double?,
            saturationRatio: Double,
            peakCount: Int,
            durationSec: Double,
            rejectionReason: RejectionReason?,
        ): PpgCalibrationRow = PpgCalibrationRow(
            timestampMs = timestampMs,
            deviceModel = Build.MODEL ?: "unknown",
            androidApi = Build.VERSION.SDK_INT,
            samplingRateHz = samplingRateHz,
            recentMedianJumpYP50 = jumpStats?.p50 ?: Double.NaN,
            recentMedianJumpYP90 = jumpStats?.p90 ?: Double.NaN,
            peakAmplitudeCov = peakAmplitudeCov,
            saturationRatio = saturationRatio,
            peakCount = peakCount,
            durationSec = durationSec,
            rejectionReason = rejectionReason?.name,
        )
    }
}

/**
 * One PPG-capture diagnostic row. Field order is independent of the CSV
 * column order; [PpgCalibrationLogger.formatRow] is the source of truth for
 * the written representation.
 */
data class PpgCalibrationRow(
    val timestampMs: Long,
    val deviceModel: String,
    val androidApi: Int,
    val samplingRateHz: Double,
    val recentMedianJumpYP50: Double,
    val recentMedianJumpYP90: Double,
    val peakAmplitudeCov: Double?,
    val saturationRatio: Double,
    val peakCount: Int,
    val durationSec: Double,
    val rejectionReason: String?,
)
