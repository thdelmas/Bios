package com.bios.app

import com.bios.app.config.RegionConfigProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The region follows the phone's country, not its language. */
class RegionDeviceLocaleTest {

    @Test
    fun `network country beats SIM and locale`() {
        val locale = RegionConfigProvider.deviceLocale("es", "fr", Locale.US)
        assertEquals("ES", locale.country)
        assertEquals("EU", RegionConfigProvider.forLocale(locale).regionCode)
    }

    @Test
    fun `SIM country when no network, locale when neither`() {
        assertEquals("FR", RegionConfigProvider.deviceLocale("", "fr", Locale.US).country)
        assertEquals(Locale.US, RegionConfigProvider.deviceLocale(null, "", Locale.US))
    }
}
