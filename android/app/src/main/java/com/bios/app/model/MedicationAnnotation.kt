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
 * no dose.
 *
 * **Traditional / functional pharmacopoeia support (issue #194).** Eleven
 * audit lenses (Ayurveda, TCM, Kampo, Korean, Siddha, Unani, Sowa Rigpa,
 * African Traditional, Indigenous Americas, Modern Non-Allopathic, Other
 * Asian Systems) converged on the same gap: traditional/folk substances
 * need a *vocabulary layer* so they are recordable without endorsement.
 * The free-text path is unchanged and remains the default; the structured
 * fields are opt-in. Recording is not endorsement — Bios stores what the
 * owner says they take, never validates whether a substance "works".
 *
 * **Discontinued meds are kept**, not deleted: the [endDate] column flips
 * an active row to historical. Past medications are part of the owner's
 * own context — a sudden RHR jump three weeks after stopping a beta-
 * blocker is far less mysterious when the discontinuation date is on
 * record. Hard delete is for typo correction only.
 */
@Entity(
    tableName = "medication_annotations",
    indices = [Index("startDate"), Index("endDate")],
)
data class MedicationAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Free-text medication name as the owner records it ("metoprolol",
     * "Lipitor 20mg", "vitamin D3", "Ashwagandha churna"). This stays the
     * canonical display string regardless of whether [substanceSource] is
     * set — owners record in whatever idiom matches their pharmacopoeia.
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
    /**
     * Optional vocabulary the [substanceCode] is drawn from. Defaults to
     * `null` so the legacy free-text path stays the default. When set,
     * lets companion surfaces (CredibleMeds QT context, AGS Beers
     * filtering) reason about the substance without parsing [name].
     * See [com.bios.app.data.SubstanceSource].
     */
    val substanceSource: String? = null,
    /**
     * Optional code drawn from [substanceSource]'s vocabulary. RxNorm CUI,
     * ATC code, AYUSH ICD-11 code, TCM formula code, Tsumura NHI number,
     * etc. Free text — Bios never validates against an external service.
     */
    val substanceCode: String? = null,
    /**
     * Optional traditional / vernacular name in the owner's preferred
     * script (Devanagari, Hanzi, Hangul, Tibetan, Arabic, Latin
     * transliteration). Recorded verbatim. Example: "अश्वगंधा" /
     * "Withania somnifera" / "Ashwagandha". Surfaced alongside [name] on
     * the medications screen.
     */
    val traditionalName: String? = null,
)
