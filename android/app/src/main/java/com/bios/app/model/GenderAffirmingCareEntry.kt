package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Hormone class for gender-affirming care (issue #209).
 *
 * The owner records what they're taking, not who they are. Bios stores no
 * gender identity label and renders no "transgender" / "cisgender"
 * categorisation. The Indigenous Americas audit (§2.4 two-spirit /
 * nádleehi / winkte / lhamana) names the imposition of a binary
 * gender model as itself a design defect. Bios honors that: gender-
 * affirming care here is *what hormones you're on and when*, the same
 * shape as the contraception annotation.
 *
 * Hormone classes affect baseline interpretation:
 *
 * - **ESTROGEN** — affects HRV (raises), body temperature pattern,
 *   skin temperature, weight distribution, bone density trend
 * - **TESTOSTERONE** — raises haematocrit, increases resting heart rate
 *   modestly, alters body-composition signals, can affect sleep
 *   architecture
 * - **ANTI_ANDROGEN** — paired with estrogen in feminising regimens
 * - **PROGESTERONE** — additive effect on body-temperature pattern
 *
 * Engines reading this entry only know the hormone class; they never read
 * [notes], they never infer gender, they only use the data to interpret
 * baselines correctly.
 *
 * Manifesto guard: owner-readable, owner-erasable, on-device only.
 * Persisted in [com.bios.app.data.ReproductiveDatabase] (separately
 * encrypted; despite the legacy "reproductive" name the DB is the right
 * encryption envelope for any sensitive reproductive / gender data).
 */
enum class GenderAffirmingHormoneType(val displayName: String) {
    ESTROGEN("Estrogen"),
    TESTOSTERONE("Testosterone"),
    ANTI_ANDROGEN("Anti-androgen (e.g. spironolactone, cyproterone)"),
    PROGESTERONE("Progesterone"),
    OTHER("Other / unlisted"),
}

/**
 * One owner-recorded gender-affirming care episode.
 *
 * No gender-identity field by design. The owner records what they are
 * taking; Bios uses that to interpret signals correctly. Identity is the
 * owner's, not Bios's.
 */
@Entity(
    tableName = "gender_affirming_care_entries",
    indices = [Index("startDate")],
)
data class GenderAffirmingCareEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Hormone class — stored as enum [GenderAffirmingHormoneType.name]. */
    val hormoneType: String,
    /** Epoch millis when the owner started this hormone. */
    val startDate: Long,
    /** Epoch millis when stopped, or null if ongoing. */
    val endDate: Long? = null,
    /** Owner-recall dose (e.g. "2 mg sublingual estradiol BID"). Free text. */
    val dose: String? = null,
    /** Owner-recall free text (route, brand, notes). Engines do not read. */
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
