package com.bios.app.data

/**
 * Input scales accepted for [com.bios.contracts.MetricType.CONSCIOUSNESS_LEVEL].
 * GCS is canonical (3–15 total); AVPU is a lossless input shortcut that
 * snaps onto representative GCS totals (A→15, V→13, P→8, U→3). When AVPU
 * is used at entry, the original scale + value are preserved in the
 * `event_payloads` sidecar via
 * [ManualReadingPayloadKeys.ORIGINAL_SCALE] / [ManualReadingPayloadKeys.ORIGINAL_VALUE]
 * so the provenance survives the lossy axis.
 */
enum class AvpuLevel(val gcsTotal: Int, val label: String) {
    ALERT(15, "A — Alert"),
    VOICE(13, "V — Responds to voice"),
    PAIN(8, "P — Responds to pain"),
    UNRESPONSIVE(3, "U — Unresponsive"),
}

object GcsBounds {
    const val MIN = 3
    const val MAX = 15
}
