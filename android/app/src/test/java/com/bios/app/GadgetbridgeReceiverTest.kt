package com.bios.app

import com.bios.app.ingest.GadgetbridgeReceiver
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for GadgetbridgeReceiver intent filter configuration.
 */
class GadgetbridgeReceiverTest {

    @Test
    fun `intentFilter includes bluetooth connected action`() {
        val filter = GadgetbridgeReceiver.intentFilter()
        assertTrue(filter.hasAction(
            "nodomain.freeyourgadget.gadgetbridge.BLUETOOTH_CONNECTED"
        ))
    }

    @Test
    fun `intentFilter includes export success action`() {
        val filter = GadgetbridgeReceiver.intentFilter()
        assertTrue(filter.hasAction(
            "nodomain.freeyourgadget.gadgetbridge.action.DATABASE_EXPORT_SUCCESS"
        ))
    }

    @Test
    fun `intentFilter includes export fail action`() {
        val filter = GadgetbridgeReceiver.intentFilter()
        assertTrue(filter.hasAction(
            "nodomain.freeyourgadget.gadgetbridge.action.DATABASE_EXPORT_FAIL"
        ))
    }

    @Test
    fun `intentFilter has exactly three actions`() {
        val filter = GadgetbridgeReceiver.intentFilter()
        assertEquals(3, filter.countActions())
    }
}
