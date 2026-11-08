package com.prism.music

import org.junit.Assert.assertEquals
import org.junit.Test

class VelouraBrandingTest {
    private fun b(s: String) = VelouraBranding.apply(s)

    @Test
    fun theWordPrismBecomesVeloura() {
        assertEquals("Signing you in to Veloura…", b("Signing you in to Prism…"))
        assertEquals("Veloura's settings", b("Prism's settings"))
        assertEquals("Open Veloura", b("Open Prism"))
        assertEquals("Veloura · Cover", b("Prism · Cover"))
        assertEquals("Songs played in Veloura.", b("Songs played in Prism."))
        assertEquals("Veloura Connect, Veloura Account", b("Prism Connect, Prism Account"))
    }

    @Test
    fun fileNamesLinksAndIdentifiersAreLeftAlone() {
        assertEquals("Keep Prism-%1\$s.apk next to it", b("Keep Prism-%1\$s.apk next to it"))
        assertEquals("github.com/Yigder/Prism", b("github.com/Yigder/Prism"))
        assertEquals("com.prism.music", b("com.prism.music"))
        assertEquals("Prism.apk", b("Prism.apk"))
        assertEquals("PrismLibrary", b("PrismLibrary"))
        // Lowercase is code, never the product name.
        assertEquals("prism_i18n_table", b("prism_i18n_table"))
    }

    @Test
    fun theLicenseAndCreditAreKept() {
        assertEquals(setOf("prism_is_free_software_under_the", "prism_s_license"), VelouraBranding.KEEP)
    }
}
