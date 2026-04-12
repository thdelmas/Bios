package com.bios.app

import com.bios.app.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ProfessionalReview model, ReviewStatus, ShareMethod,
 * ProfessionalRecommendation enums, and AlertTier.fromLevel.
 */
class ProfessionalReviewTest {

    // --- ReviewStatus ---

    @Test
    fun `reviewStatus fromLevel roundtrips`() {
        for (status in ReviewStatus.entries) {
            assertEquals(status, ReviewStatus.fromLevel(status.level))
        }
    }

    @Test
    fun `reviewStatus fromLevel unknown returns PENDING`() {
        assertEquals(ReviewStatus.PENDING, ReviewStatus.fromLevel(99))
        assertEquals(ReviewStatus.PENDING, ReviewStatus.fromLevel(-1))
    }

    @Test
    fun `reviewStatus levels are ordered`() {
        assertTrue(ReviewStatus.PENDING.level < ReviewStatus.SHARED.level)
        assertTrue(ReviewStatus.SHARED.level < ReviewStatus.REVIEWED.level)
        assertTrue(ReviewStatus.REVIEWED.level < ReviewStatus.DISMISSED.level)
    }

    @Test
    fun `reviewStatus has 4 entries`() {
        assertEquals(4, ReviewStatus.entries.size)
    }

    @Test
    fun `reviewStatus labels are non-empty`() {
        for (status in ReviewStatus.entries) {
            assertTrue("${status.name} should have a label", status.label.isNotBlank())
        }
    }

    // --- ShareMethod ---

    @Test
    fun `shareMethod has 6 entries`() {
        assertEquals(6, ShareMethod.entries.size)
    }

    @Test
    fun `shareMethod keys are unique`() {
        val keys = ShareMethod.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `shareMethod labels are non-empty`() {
        for (method in ShareMethod.entries) {
            assertTrue("${method.name} should have a label", method.label.isNotBlank())
        }
    }

    // --- ProfessionalRecommendation ---

    @Test
    fun `professionalRecommendation has 6 entries`() {
        assertEquals(6, ProfessionalRecommendation.entries.size)
    }

    @Test
    fun `professionalRecommendation keys are unique`() {
        val keys = ProfessionalRecommendation.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `professionalRecommendation labels are non-empty`() {
        for (rec in ProfessionalRecommendation.entries) {
            assertTrue("${rec.name} should have a label", rec.label.isNotBlank())
        }
    }

    // --- AlertTier.fromLevel ---

    @Test
    fun `alertTier fromLevel roundtrips`() {
        for (tier in AlertTier.entries) {
            assertEquals(tier, AlertTier.fromLevel(tier.level))
        }
    }

    @Test
    fun `alertTier fromLevel unknown returns OBSERVATION`() {
        assertEquals(AlertTier.OBSERVATION, AlertTier.fromLevel(99))
        assertEquals(AlertTier.OBSERVATION, AlertTier.fromLevel(-1))
    }

    @Test
    fun `alertTier levels are ascending by severity`() {
        assertTrue(AlertTier.OBSERVATION.level < AlertTier.NOTICE.level)
        assertTrue(AlertTier.NOTICE.level < AlertTier.ADVISORY.level)
        assertTrue(AlertTier.ADVISORY.level < AlertTier.URGENT.level)
    }

    @Test
    fun `alertTier labels are non-empty`() {
        for (tier in AlertTier.entries) {
            assertTrue("${tier.name} should have a label", tier.label.isNotBlank())
        }
    }

    // --- ProfessionalReview defaults ---

    @Test
    fun `default status is PENDING`() {
        val review = ProfessionalReview(anomalyId = "a1")
        assertEquals(ReviewStatus.PENDING.level, review.status)
    }

    @Test
    fun `default sharing includes explanation but not baselines`() {
        val review = ProfessionalReview(anomalyId = "a1")
        assertTrue(review.sharedExplanation)
        assertFalse(review.sharedBaselines)
    }

    @Test
    fun `response fields default to null`() {
        val review = ProfessionalReview(anomalyId = "a1")
        assertNull(review.respondedAt)
        assertNull(review.professionalNotes)
        assertNull(review.clinicallyRelevant)
        assertNull(review.recommendation)
        assertNull(review.ownerFoundHelpful)
    }

    @Test
    fun `sharing fields default to null`() {
        val review = ProfessionalReview(anomalyId = "a1")
        assertNull(review.shareMethod)
        assertNull(review.sharedMetrics)
        assertNull(review.sharedWindowDays)
    }

    @Test
    fun `each review gets a unique ID`() {
        val r1 = ProfessionalReview(anomalyId = "a1")
        val r2 = ProfessionalReview(anomalyId = "a1")
        assertNotEquals(r1.id, r2.id)
    }
}
