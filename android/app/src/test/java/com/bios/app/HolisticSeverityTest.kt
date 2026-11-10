package com.bios.app

import com.bios.app.engine.classifyHolistic
import com.bios.app.model.AlertTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/specs/alert-detail.md — criterion 9: WARNING between ADVISORY and URGENT. */
class HolisticSeverityTest {

    @Test
    fun `advisory score with a three sigma signal is a warning`() {
        assertEquals(AlertTier.WARNING, classifyHolistic(0.9f, 3.2))
        assertEquals(AlertTier.ADVISORY, classifyHolistic(0.9f, 2.4))
        assertEquals(AlertTier.NOTICE, classifyHolistic(0.75f, 3.5))
        assertEquals(AlertTier.OBSERVATION, classifyHolistic(0.5f, 0.2))
    }

    @Test
    fun `tier order and stored codes rise together`() {
        assertTrue(AlertTier.ADVISORY < AlertTier.WARNING)
        assertTrue(AlertTier.WARNING < AlertTier.URGENT)
        assertEquals(3, AlertTier.WARNING.level)
        assertEquals(4, AlertTier.URGENT.level)
        assertEquals(AlertTier.URGENT, AlertTier.fromLevel(4))
    }
}
