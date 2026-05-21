package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Owner-recorded current medication. Closes audit gap §2.5: a non-trivial
 * fraction of the cardiovascular / metabolic / mental-health patterns Bios
 * already fires will trip on owners *already on* beta-blockers, statins,
 * metformin, SSRIs, levothyroxine, or steroid courses — producing
 * systematically false-elevated severity. Annotating the current medication
 * list lets the alert-explanation surface append context so the owner (and
 * their clinician, via the FHIR share path in a future PR) sees the
 * medication backdrop alongside the pattern.
 *
 * **Not** a Posology-class adherence tracker — no reminders, no schedule,
 * no dose. v1 ships free-text [name] only; an RxNorm-coded follow-up can
 * land without schema change since [name] stays the canonical display
 * string. The full medications companion is explicitly deferred (Phase 9
 * of `docs/ROADMAP.md`).
 *
 * **Discontinued meds are kept**, not deleted: the [endDate] column flips
 * an active row to historical. Past medications are part of the owner's
 * own context — a sudden RHR jump three weeks after stopping a beta-
 * blocker is far less mysterious when the discontinuation date is on
 * record. Use [name] + [endDate] together to express that. Hard delete is
 * for typo correction only.
 */
@Entity(
    tableName = "medication_annotations",
    indices = [Index("startDate"), Index("endDate")],
)
data class MedicationAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Free-text medication name as the owner records it ("metoprolol",
     * "Lipitor 20mg", "vitamin D3"). v1 is intentionally non-coded —
     * RxNorm or ATC mapping is a future enhancement layered on top of
     * this column without migration.
     */
    val name: String,
    /** When the owner started taking this medication. Epoch millis. */
    val startDate: Long,
    /**
     * When the owner stopped, if discontinued. `null` means currently
     * active. The alert-explanation enhancer reads "active" as
     * `endDate IS NULL` (see [com.bios.app.data.dao.MedicationAnnotationDao.fetchActive]).
     */
    val endDate: Long? = null,
    /**
     * Optional owner free-text note (dose, indication, "morning only",
     * etc.). Never parsed by the engine; surfaced verbatim on the
     * medications screen and in the alert context line.
     */
    val note: String? = null,
)
