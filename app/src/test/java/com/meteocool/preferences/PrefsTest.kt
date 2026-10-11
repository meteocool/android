package com.meteocool.preferences

import com.meteocool.testing.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `settings from before 4·0 are dropped, onboarding with them`() {
        val sp = FakeSharedPreferences("is_intro_completed" to true, "notification" to true, "map_rotate" to true)
        val legacy = FakeSharedPreferences("fb_token" to "old")
        val prefs = Prefs(sp)
        assertTrue(prefs.resetOldSettings(legacy))
        assertEquals(mapOf(Prefs.SETTINGS_VERSION to Prefs.CURRENT_SETTINGS_VERSION), sp.all)
        assertTrue(legacy.all.isEmpty())
        assertFalse(prefs.onboardingDone)
    }

    @Test
    fun `a fresh install is only stamped, a current one left alone`() {
        val fresh = FakeSharedPreferences()
        assertFalse(Prefs(fresh).resetOldSettings(FakeSharedPreferences()))
        assertEquals(Prefs.CURRENT_SETTINGS_VERSION, fresh.getInt(Prefs.SETTINGS_VERSION, 0))

        val current = FakeSharedPreferences(Prefs.SETTINGS_VERSION to 4, "onboarding_done" to true)
        assertFalse(Prefs(current).resetOldSettings(FakeSharedPreferences()))
        assertTrue(Prefs(current).onboardingDone)
    }
}
