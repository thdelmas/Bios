package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Owner-recorded treatment course (#192). A bookkeeping wrapper around a
 * multi-session intervention series — Panchakarma 14-day course, cardiac
 * rehab phase II, a 12-session chiropractic block, a hospital-supervised
 * pulmonary rehab block. Individual sessions live in [InterventionEvent]
 * and reference a course via `treatmentCourseId`.
 *
 * **The course is metadata, not a plan.** Bios does not track adherence,
 * does not remind the owner of remaining sessions, does not surface
 * "you're behind on your course". It holds what the owner said about
 * the course — start date, expected end, goal, free-text notes — and
 * lets sessions point at it. The manifesto stance is identical to
 * [InterventionEvent]: recording is not endorsement; evaluation
 * belongs to the owner.
 *
 * **End-of-course is owner-controlled.** [actualEndAt] is set by the
 * owner via the UI when they consider the course done. There is no
 * automatic completion. There is no "expired" state. A course with
 * [expectedEndAt] in the past and [actualEndAt] still null just means
 * the owner hasn't closed it — Bios doesn't push them to.
 *
 * **No FHIR mapping shipped today.** FHIR R4 `CarePlan` is the closest
 * analogue but bringing it in requires mapping the owner's free-text
 * [goal] to a `goal` resource and a coding system — work that
 * dovetails with #226's tradition enum and the broader audit of how
 * FHIR `CarePlan` semantics interact with the Bios manifesto. See
 * the TODO in the FHIR exporter for the deferred mapping.
 *
 * **AlertContentPolicy compliance:** any future surface that mentions a
 * course (e.g. the alert-explanation enhancer noting "during your
 * recorded course X") must phrase it as a data statement, never as
 * judgement or advice. Today no surface auto-mentions a course at all.
 */
@Entity(
    tableName = "treatment_courses",
    indices = [Index("startedAt"), Index("actualEndAt")],
)
data class TreatmentCourse(
    /** Stable id used by [InterventionEvent.treatmentCourseId] as FK. */
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /**
     * Owner-supplied label — appears in the list UI verbatim. Examples:
     * "Panchakarma — Spring 2026", "Cardiac rehab phase II",
     * "Chiropractic block (12 sessions, Dr. K)".
     */
    val label: String,
    /**
     * Optional tradition the course belongs to. Owner-declared.
     * Reconcile with PR #226's MedicalTradition enum on merge.
     */
    val tradition: MedicalTradition? = null,
    /** Epoch millis the owner says the course began. */
    val startedAt: Long,
    /**
     * Optional epoch millis the owner expects the course to end —
     * what they were told or what they planned. Null when not
     * specified. Bios never compares this to "now" to push reminders.
     */
    val expectedEndAt: Long? = null,
    /**
     * Owner-set epoch millis when the course actually ended. Null
     * while the course is still considered ongoing by the owner.
     * Setting this is the only way a course becomes "closed".
     */
    val actualEndAt: Long? = null,
    /**
     * Owner free-text goal — "improve lumbar mobility", "complete
     * vamana protocol", "30% increase in 6-min walk". Never parsed;
     * surfaced verbatim on the course detail screen.
     */
    val goal: String? = null,
    /** Owner free-text notes. Never parsed. */
    val notes: String? = null,
)
