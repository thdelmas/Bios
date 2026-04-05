package com.bios.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents an owner-initiated request for professional medical review
 * of a detected anomaly. The owner controls what is shared — nothing leaves
 * the device without explicit action.
 *
 * Flow:
 * 1. Bios detects anomaly → shows "Get professional eyes on this?" option
 * 2. Owner taps → sees exactly what will be shared (preview screen)
 * 3. Owner confirms → ProfessionalReview created with PENDING status
 * 4. Data shared via owner's chosen channel (export, telemedicine, QR code)
 * 5. Professional responds → owner records outcome
 *
 * Bios never sends data to any professional service automatically.
 * The owner is the intermediary in every step.
 */
@Entity(
    tableName = "professional_reviews",
    foreignKeys = [
        ForeignKey(
            entity = Anomaly::class,
            parentColumns = ["id"],
            childColumns = ["anomalyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("anomalyId"),
        Index("status"),
        Index("requestedAt")
    ]
)
data class ProfessionalReview(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    /** The anomaly this review is about. */
    val anomalyId: String,

    /** When the owner initiated the review request. */
    val requestedAt: Long = System.currentTimeMillis(),

    /** Current status of the review. */
    val status: Int = ReviewStatus.PENDING.level,

    /** How the owner chose to share the data. */
    val shareMethod: String? = null,

    /** What data the owner chose to include (JSON array of metric type keys). */
    val sharedMetrics: String? = null,

    /** Time window of data shared (days). */
    val sharedWindowDays: Int? = null,

    /** Whether the full anomaly explanation was included. */
    val sharedExplanation: Boolean = true,

    /** Whether baseline context was included. */
    val sharedBaselines: Boolean = false,

    // -- Professional response (recorded by owner) --

    /** When the professional responded (as recorded by owner). */
    val respondedAt: Long? = null,

    /** Professional's assessment summary (free text, entered by owner). */
    val professionalNotes: String? = null,

    /** Did the professional consider the alert clinically relevant? */
    val clinicallyRelevant: Boolean? = null,

    /** Professional's recommendation category. */
    val recommendation: String? = null,

    /** Whether the owner found the review process helpful. */
    val ownerFoundHelpful: Boolean? = null
)

enum class ReviewStatus(val level: Int, val label: String) {
    /** Owner initiated but hasn't shared yet. */
    PENDING(0, "Pending"),
    /** Owner shared data with a professional. */
    SHARED(1, "Shared"),
    /** Professional has responded (as recorded by owner). */
    REVIEWED(2, "Reviewed"),
    /** Owner dismissed without completing. */
    DISMISSED(3, "Dismissed");

    companion object {
        fun fromLevel(level: Int): ReviewStatus =
            entries.find { it.level == level } ?: PENDING
    }
}

enum class ShareMethod(val key: String, val label: String) {
    /** FHIR R4 Bundle export shared manually. */
    FHIR_EXPORT("fhir_export", "FHIR Export"),
    /** Encrypted Bios export file shared manually. */
    ENCRYPTED_EXPORT("encrypted_export", "Encrypted Export"),
    /** QR code for direct device-to-device transfer. */
    QR_CODE("qr_code", "QR Code"),
    /** Owner read the data to professional verbally or showed screen. */
    VERBAL("verbal", "Verbal / Screen Share"),
    /** Owner's own telemedicine platform (not Bios-operated). */
    TELEMEDICINE("telemedicine", "Telemedicine"),
    /** Screenshot or printout. */
    SCREENSHOT("screenshot", "Screenshot / Print")
}

enum class ProfessionalRecommendation(val key: String, val label: String) {
    /** No action needed — within normal variation. */
    NO_ACTION("no_action", "No action needed"),
    /** Continue monitoring — check again in X days. */
    MONITOR("monitor", "Continue monitoring"),
    /** Schedule a non-urgent appointment. */
    SCHEDULE_VISIT("schedule_visit", "Schedule appointment"),
    /** Get specific lab work or tests done. */
    LAB_WORK("lab_work", "Lab work recommended"),
    /** Seek prompt medical attention. */
    SEEK_CARE("seek_care", "Seek medical attention"),
    /** Professional suggested lifestyle adjustment. */
    LIFESTYLE("lifestyle", "Lifestyle adjustment")
}
