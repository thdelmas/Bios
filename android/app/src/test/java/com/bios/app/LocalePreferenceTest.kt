package com.bios.app

import com.bios.app.i18n.LocalePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LocalePreference]'s static data shape. The Context-bound
 * persistence behaviour (SharedPreferences read/write, Configuration
 * wrapping) is exercised by instrumentation tests; these tests just
 * verify the supported-locale catalogue matches the shipped overlays so
 * the selector never offers a locale that has no string resources.
 */
class LocalePreferenceTest {

    @Test
    fun `Tier-A locales appear in the selector catalogue`() {
        val tags = LocalePreference.supported.map { it.tag }
        listOf("es", "es-MX", "es-AR", "pt-BR", "mi-NZ", "haw").forEach {
            assertTrue("LocalePreference missing $it option", it in tags)
        }
    }

    @Test
    fun `system-default option is exposed first`() {
        val first = LocalePreference.supported.first()
        assertEquals(LocalePreference.SYSTEM_DEFAULT_TAG, first.tag)
        assertNotNull(first.displayName)
    }

    @Test
    fun `english option exists for users overriding from another system default`() {
        // If the device's system language is e.g. Hindi (not yet supported)
        // and the owner wants to read Bios in English while keeping the
        // OS locale untouched, the selector must offer English explicitly.
        val tags = LocalePreference.supported.map { it.tag }
        assertTrue("en" in tags)
    }

    @Test
    fun `every locale option has a non-blank display name`() {
        for (option in LocalePreference.supported) {
            assertTrue(
                "Locale ${option.tag} has blank displayName",
                option.displayName.isNotBlank(),
            )
        }
    }
}
