package com.bios.app.sync

import com.bios.app.data.InstrumentOnlyMigrations
import com.bios.app.model.Anomaly
import org.json.JSONObject

/**
 * A stored alert as it travels between the owner's own devices.
 *
 * Sent: the measured columns, the tier, the acknowledgement, the
 * detection-time snapshot and the owner's note. The journal answers of
 * earlier versions are not sent; they stay on the device that holds them.
 *
 * Received: a peer may run an earlier version, so every incoming alert goes
 * through [neutralised] before it is stored, and a row this device already
 * holds keeps its own journal answers, note and snapshot when the peer sends
 * none.
 */
internal object AlertSyncCodec {

    fun toJson(alert: Anomaly): JSONObject = JSONObject().apply {
        put("id", alert.id)
        put("detectedAt", alert.detectedAt)
        put("metricTypes", alert.metricTypes)
        put("deviationScores", alert.deviationScores)
        put("combinedScore", alert.combinedScore)
        put("patternId", alert.patternId ?: JSONObject.NULL)
        put("severity", alert.severity)
        put("title", alert.title)
        put("explanation", alert.explanation)
        put("acknowledged", alert.acknowledged)
        put("acknowledgedAt", alert.acknowledgedAt ?: JSONObject.NULL)
        put("feedbackAt", alert.feedbackAt ?: JSONObject.NULL)
        put("notes", alert.notes ?: JSONObject.NULL)
        put("signalSnapshot", alert.signalSnapshot ?: JSONObject.NULL)
    }

    /** The alert to store for a peer's [o], given the row this device [existing]ly holds under the same id. */
    fun fromPeer(o: JSONObject, existing: Anomaly?): Anomaly = neutralised(
        Anomaly(
            id = o.getString("id"),
            detectedAt = o.getLong("detectedAt"),
            metricTypes = o.getString("metricTypes"),
            deviationScores = o.getString("deviationScores"),
            combinedScore = o.getDouble("combinedScore"),
            patternId = o.text("patternId"),
            severity = o.getInt("severity"),
            title = o.optString("title"),
            explanation = o.optString("explanation"),
            suggestedAction = o.text("suggestedAction"),
            acknowledged = o.optBoolean("acknowledged", false) || existing?.acknowledged == true,
            acknowledgedAt = o.long("acknowledgedAt") ?: existing?.acknowledgedAt,
            feedbackAt = o.long("feedbackAt") ?: existing?.feedbackAt,
            feltSick = o.flag("feltSick") ?: existing?.feltSick,
            visitedDoctor = o.flag("visitedDoctor") ?: existing?.visitedDoctor,
            diagnosis = o.text("diagnosis") ?: existing?.diagnosis,
            symptoms = o.text("symptoms") ?: existing?.symptoms,
            notes = o.text("notes") ?: existing?.notes,
            outcomeAccurate = o.flag("outcomeAccurate") ?: existing?.outcomeAccurate,
            signalSnapshot = o.text("signalSnapshot") ?: existing?.signalSnapshot,
        )
    )

    /**
     * The same rewrite the schema steps 39 and 41 apply to stored rows. A
     * deviation alert keeps its measured lines and takes the current title;
     * any other alert loses its title, its text and the name of the pattern
     * that raised it. The next-step text is cleared on both.
     */
    fun neutralised(alert: Anomaly): Anomaly =
        if (alert.patternId == InstrumentOnlyMigrations.DEVIATION_ALERT_ID) {
            alert.copy(title = InstrumentOnlyMigrations.DEVIATION_TITLE, suggestedAction = null)
        } else {
            alert.copy(
                patternId = InstrumentOnlyMigrations.EARLIER_ALERT_ID,
                title = InstrumentOnlyMigrations.EARLIER_TITLE,
                explanation = InstrumentOnlyMigrations.EARLIER_EXPLANATION,
                suggestedAction = null,
            )
        }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.long(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.flag(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)
}
