package com.meteocool.stormlog

import com.meteocool.testing.FakeSharedPreferences
import org.junit.Assert.*
import org.junit.Test

class StormLogConsentTest {
    @Test fun collectingWaitsUntilWipeSuccessAndSurvivesRecreation() {
        val preferences = FakeSharedPreferences()
        val consent = StormLogConsent(preferences)
        assertFalse(consent.enabled("context"))
        consent.begin("context", true)
        assertTrue(consent.pending("context"))
        assertFalse(consent.enabled("context"))
        val recreated = StormLogConsent(preferences)
        assertTrue(recreated.pending("context"))
        assertTrue(recreated.enabling("context"))
        recreated.complete("context")
        assertTrue(recreated.enabled("context"))
        assertFalse(recreated.pending("context"))
    }

    @Test fun changedTokenOrEnvironmentNeverInheritsConsent() {
        val preferences = FakeSharedPreferences()
        val consent = StormLogConsent(preferences)
        val old = StormLogRules.context("https://api-next.meteocool.com/", "a".repeat(64))
        val other = StormLogRules.context("https://api-demo.meteocool.com/", "a".repeat(64))
        val refreshed = StormLogRules.context("https://api-next.meteocool.com/", "b".repeat(64))
        consent.begin(old, true)
        consent.complete(other)
        assertFalse(consent.enabled(old))
        consent.complete(old)
        assertTrue(consent.enabled(old))
        assertFalse(consent.enabled(other))
        assertFalse(consent.enabled(refreshed))
        assertFalse(preferences.all.values.any { it.toString().contains("a".repeat(64)) })
    }

    @Test fun disableFailureKeepsCollectionOffAndDeletionPending() {
        val consent = StormLogConsent(FakeSharedPreferences())
        consent.begin("context", true)
        consent.complete("context")
        consent.begin("context", false)
        assertFalse(consent.enabled("context"))
        assertTrue(consent.pending("context"))
        assertFalse(consent.enabling("context"))
        consent.complete("context")
        assertFalse(consent.enabled("context"))
        assertFalse(consent.pending("context"))
    }
}
