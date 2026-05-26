package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Owner-recorded contraception method (issue #209, OBGYN_POV §2.11).
 *
 * Hormonal methods modulate every reproductive signal Bios tracks — BBT
 * pattern, cycle phase, mood signature, HRV across cycle, even resting
 * heart rate when estrogen exposure changes. Annotation is the owner's
 * tool: when the cycle-anomaly pattern fires under combined-pill use,
 * the owner is the one who knows that's expected, not anomalous.
 *
 * **Storage isolation.** Persisted in [com.bios.app.data.ReproductiveDatabase]
 * (separately encrypted SQLCipher, independent key, priority destruction
 * on LETHE duress PIN). Post-Dobbs threat model: contraception history is
 * itself sensitive in jurisdictions that police it. It never enters the
 * main DB.
 *
 * Manifesto guard: owner-set, owner-readable, owner-erasable. Bios never
 * infers contraception from missing periods or BBT flattening.
 */
enum class ContraceptionMethod(val displayName: String) {
    HORMONAL_PILL("Hormonal pill (combined or progestin-only)"),
    HORMONAL_PATCH("Hormonal patch"),
    HORMONAL_RING("Hormonal vaginal ring"),
    IUD_COPPER("IUD — copper / non-hormonal"),
    IUD_HORMONAL("IUD — hormonal (levonorgestrel)"),
    IMPLANT("Subdermal implant"),
    INJECTION("Injection (e.g. DMPA)"),
    BARRIER("Barrier (condom, diaphragm, cervical cap)"),
    FERTILITY_AWARENESS("Fertility-awareness method"),
    STERILISATION("Sterilisation (tubal, vasectomy)"),
    NONE("None"),
    OTHER("Other / unlisted"),
}

/**
 * One owner-recorded contraception episode. Multiple rows let the owner
 * preserve their history (combined pill 2020-2023, copper IUD since 2023)
 * without overwriting — useful when reading cycle data from earlier in
 * the timeline. Latest row by [startDate] is the current method.
 *
 * No engine reads [notes]; the field exists for owner-recall context
 * (brand, dosage, side-effects). Engines only consult [method] when
 * deciding which patterns to suppress (cycle anomaly under hormonal use).
 */
@Entity(
    tableName = "contraception_entries",
    indices = [Index("startDate")],
)
data class ContraceptionEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Method name — stored as enum [ContraceptionMethod.name] for forward-compat. */
    val method: String,
    /** Epoch millis when the owner started this method. */
    val startDate: Long,
    /** Epoch millis when the owner stopped, or null if ongoing. */
    val endDate: Long? = null,
    /** Owner-recall free text (brand, dose, side-effects). Engines do not read. */
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
