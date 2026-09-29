package com.meteocool.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefsTest {

    @Test
    fun `ahead stays within what the backend accepts`() {
        assertEquals(15, Prefs.clampAhead(null))
        assertEquals(5, Prefs.clampAhead(0))
        assertEquals(45, Prefs.clampAhead(60))
        assertEquals(25, Prefs.clampAhead(25))
        assertEquals(20, Prefs.clampAhead(23))
    }

    @Test
    fun `intensity is one of the offered thresholds`() {
        assertEquals(20, Prefs.clampIntensity(10))
        assertEquals(20, Prefs.clampIntensity(null))
        assertEquals(41, Prefs.clampIntensity(41))
        assertEquals(14, Prefs.clampIntensity(14))
    }
}
