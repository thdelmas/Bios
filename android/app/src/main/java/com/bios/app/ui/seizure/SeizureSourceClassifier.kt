package com.bios.app.ui.seizure

import com.bios.app.model.EventPayloadField
import com.bios.app.model.SeizureEventFields

/**
 * Pure classifier for SEIZURE_EVENT provenance (#269 Cut 3c). Reads
 * the `detection_source` field from a reading's `event_payloads`
 * sidecar and produces the [DetectionSource] the timeline UI renders.
 *
 * Three categories:
 *  - [DetectionSource.WearableInferred] — payload explicitly carries
 *    `detection_source = "wearable_inferred"`. Written by
 *    [com.bios.app.engine.SeizureEventFactory] from
 *    [com.bios.app.ingest.SeizureDetectionService].
 *  - [DetectionSource.OwnerLogged] — payload explicitly carries
 *    `detection_source = "owner_logged"`. Reserved for the future
 *    owner-logging entry surface.
 *  - [DetectionSource.OwnerLoggedImplicit] — no `detection_source`
 *    field at all. Historical owner-logged shape (the journal UI
 *    predates the payload surface). Treated as owner-logged for
 *    rendering purposes — same trust profile.
 *
 * The wearable-inferred branch also exposes the peri-event HR
 * substrate ([WearableInferredSubstrate]) so the renderer can show
 * the actual ratio behind the detection without re-querying.
 *
 * Lifted to its own file so JVM tests pin the classification rules
 * without touching Compose or Room.
 */
internal object SeizureSourceClassifier {

    fun classify(payload: List<EventPayloadField>): DetectionSource {
        val source = payload.firstOrNull {
            it.fieldKey == SeizureEventFields.DETECTION_SOURCE
        }?.stringValue
        return when (source) {
            SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED ->
                DetectionSource.WearableInferred(extractSubstrate(payload))
            SeizureEventFields.DETECTION_SOURCE_OWNER_LOGGED ->
                DetectionSource.OwnerLogged
            null -> DetectionSource.OwnerLoggedImplicit
            else -> DetectionSource.OwnerLoggedImplicit
        }
    }

    private fun extractSubstrate(payload: List<EventPayloadField>): WearableInferredSubstrate {
        val median = payload.firstOrNull {
            it.fieldKey == SeizureEventFields.MEDIAN_HR_BPM
        }?.doubleValue
        val baseline = payload.firstOrNull {
            it.fieldKey == SeizureEventFields.BASELINE_HR_BPM
        }?.doubleValue
        return WearableInferredSubstrate(
            medianHrBpm = median,
            baselineHrBpm = baseline,
        )
    }
}

internal sealed interface DetectionSource {
    data class WearableInferred(val substrate: WearableInferredSubstrate) : DetectionSource
    data object OwnerLogged : DetectionSource
    /** No `detection_source` payload field present — historical
     *  owner-logged shape. Treated identically to [OwnerLogged] by the
     *  UI; the distinction is preserved so future analytics can see
     *  which rows pre-dated the explicit payload surface. */
    data object OwnerLoggedImplicit : DetectionSource
}

internal data class WearableInferredSubstrate(
    val medianHrBpm: Double?,
    val baselineHrBpm: Double?,
) {
    /** Percentage rise vs baseline, rounded to whole-percent for the
     *  pull-side renderer. Null when either value is missing. */
    val risePercent: Int?
        get() {
            val m = medianHrBpm ?: return null
            val b = baselineHrBpm ?: return null
            if (b <= 0.0) return null
            return (((m - b) / b) * 100.0).toInt()
        }
}
