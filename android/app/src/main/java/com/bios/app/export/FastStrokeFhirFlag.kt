package com.bios.app.export

import com.bios.app.model.FastStrokeEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * FHIR R4 Flag emitter for owner-recorded FAST stroke-recognition
 * events (#207, neurology audit §2.15). Lives next to [FhirExporter]
 * but in its own file so the exporter stays under the 500-line cap.
 *
 * Mapping:
 *  - **resourceType** — `Flag` (the FHIR resource for "out-of-band
 *    information that should be presented to a clinician reading the
 *    record"). A FAST screen is a transient, action-oriented alert, not
 *    a longitudinal observation — `Flag` matches the shape.
 *  - **status** — `active` (the screen is meant to surface every time
 *    a clinician opens the record near the timestamped event).
 *  - **category** — `safety` from
 *    `http://terminology.hl7.org/CodeSystem/flag-category`.
 *  - **code** — SNOMED CT 2725007 "Cerebrovascular accident (disorder)"
 *    plus a free-text `text` summarising which FAST items were positive.
 *    The free-text is explicit that this is a bystander-observed screen,
 *    not a diagnosis (manifesto: data statement, never a diagnostic
 *    claim).
 *  - **period.start** — the event timestamp. Stroke care anchors to
 *    last-known-well time, and the timestamp here is what the assessing
 *    clinician needs.
 *  - **extension** — a Bios-local extension carrying the three FAST
 *    booleans so a downstream reader doesn't have to parse free text.
 */
internal fun buildFastStrokeFlagResource(event: FastStrokeEvent): JSONObject = JSONObject().apply {
    put("resourceType", "Flag")
    put("id", event.id)
    put("status", "active")
    put("category", JSONArray().put(JSONObject().apply {
        put("coding", JSONArray().put(JSONObject().apply {
            put("system", "http://terminology.hl7.org/CodeSystem/flag-category")
            put("code", "safety")
            put("display", "Safety")
        }))
    }))
    put("code", JSONObject().apply {
        put("coding", JSONArray().put(JSONObject().apply {
            put("system", "http://snomed.info/sct")
            put("code", "2725007")
            put("display", "Cerebrovascular accident (disorder)")
        }))
        put("text", buildFastFlagText(event))
    })
    put("period", JSONObject().apply {
        put("start", formatEpochMillis(event.timestamp))
    })
    put("extension", JSONArray().apply {
        put(JSONObject().apply {
            put("url", "https://bios.health/fhir/fast-screen")
            put("extension", JSONArray().apply {
                put(JSONObject().apply {
                    put("url", "facialDrooping")
                    put("valueBoolean", event.facialDrooping)
                })
                put(JSONObject().apply {
                    put("url", "armWeakness")
                    put("valueBoolean", event.armWeakness)
                })
                put(JSONObject().apply {
                    put("url", "speechDifficulty")
                    put("valueBoolean", event.speechDifficulty)
                })
            })
        })
    })
}

private fun buildFastFlagText(event: FastStrokeEvent): String {
    val items = buildList {
        if (event.facialDrooping) add("facial drooping")
        if (event.armWeakness) add("arm weakness")
        if (event.speechDifficulty) add("speech difficulty")
    }
    val itemsText = if (items.isEmpty()) "none" else items.joinToString(", ")
    return "Owner-recorded FAST stroke screen — positive items: $itemsText. Recorded via the Bios FAST screen; owner-input only, not an automated detection."
}
