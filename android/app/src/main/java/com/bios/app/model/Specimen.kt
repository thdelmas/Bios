package com.bios.app.model

/**
 * Specimen the lab assay ran on. Matters for some biomarkers (serum vs
 * plasma vs whole blood gives different reference ranges and, for a few
 * assays, different absolute values). Persisted as the enum name in the
 * `event_payloads` sidecar.
 */
enum class Specimen(val readable: String) {
    SERUM("Serum"),
    PLASMA("Plasma"),
    WHOLE_BLOOD("Whole blood"),
    OTHER("Other");

    companion object {
        fun fromName(name: String?): Specimen? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
