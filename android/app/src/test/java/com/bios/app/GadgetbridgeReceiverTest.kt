package com.bios.app

import com.bios.app.ingest.GadgetbridgeReceiver
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for GadgetbridgeReceiver action constants.
 *
 * IntentFilter is an Android framework class unavailable in JVM unit tests,
 * so we use reflection to verify the companion object constants directly.
 */
class GadgetbridgeReceiverTest {

    // Kotlin compiles companion `private const val` as static fields on the outer class.
    private fun readPrivateConst(name: String): String {
        val field = GadgetbridgeReceiver::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(null) as String
    }

    @Test
    fun `bluetooth connected action uses correct Gadgetbridge namespace`() {
        val action = readPrivateConst("ACTION_BLUETOOTH_CONNECTED")
        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge.BLUETOOTH_CONNECTED",
            action
        )
    }

    @Test
    fun `export success action uses correct Gadgetbridge namespace`() {
        val action = readPrivateConst("ACTION_EXPORT_SUCCESS")
        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge.action.DATABASE_EXPORT_SUCCESS",
            action
        )
    }

    @Test
    fun `export fail action uses correct Gadgetbridge namespace`() {
        val action = readPrivateConst("ACTION_EXPORT_FAIL")
        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge.action.DATABASE_EXPORT_FAIL",
            action
        )
    }

    @Test
    fun `activity sync action targets Gadgetbridge command namespace`() {
        val action = readPrivateConst("ACTION_ACTIVITY_SYNC")
        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge.command.ACTIVITY_SYNC",
            action
        )
    }

    @Test
    fun `data type bitmask constants are valid hex strings`() {
        val hexPattern = Regex("0x[0-9a-fA-F]+")
        for (name in listOf("TYPE_ACTIVITY", "TYPE_HEART_RATE", "TYPE_SPO2", "TYPE_TEMPERATURE")) {
            val value = readPrivateConst(name)
            assertTrue("$name should be hex, got: $value", hexPattern.matches(value))
        }
    }
}
