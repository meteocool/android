package com.meteocool.environment

import com.meteocool.preferences.Prefs
import com.meteocool.testing.FakeSharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MeteocoolEnvironmentTest {

    @After
    fun backToApp() = MeteocoolEnvironment.init(Prefs(FakeSharedPreferences("environment" to "app")))

    @Test
    fun `the stored mode is used`() {
        assertEquals(MeteocoolEnvironment.STAGING, MeteocoolEnvironment.stored(Prefs(FakeSharedPreferences("environment" to "staging"))))
        assertEquals(MeteocoolEnvironment.DEMO, MeteocoolEnvironment.stored(Prefs(FakeSharedPreferences("environment" to "demo"))))
        assertEquals(MeteocoolEnvironment.APP, MeteocoolEnvironment.stored(Prefs(FakeSharedPreferences())))
    }

    @Test
    fun `demo mode carries over to mode, experimental features go back to production`() {
        val demo = FakeSharedPreferences("demo_mode" to true, "experimental_features" to false)
        assertEquals(MeteocoolEnvironment.DEMO, MeteocoolEnvironment.stored(Prefs(demo)))
        assertEquals("demo", demo.getString("environment", null))
        assertFalse(demo.contains("demo_mode") || demo.contains("experimental_features"))

        val experimental = FakeSharedPreferences("experimental_features" to true)
        assertEquals(MeteocoolEnvironment.APP, MeteocoolEnvironment.stored(Prefs(experimental)))
        assertEquals("app", experimental.getString("environment", null))
        assertFalse(experimental.contains("experimental_features"))
    }

    @Test
    fun `selecting a mode stores it and tells its followers`() {
        val prefs = Prefs(FakeSharedPreferences())
        MeteocoolEnvironment.init(prefs)
        MeteocoolEnvironment.select(MeteocoolEnvironment.STAGING, prefs)
        assertEquals(MeteocoolEnvironment.STAGING, MeteocoolEnvironment.current)
        assertEquals(MeteocoolEnvironment.STAGING, MeteocoolEnvironment.changes.value)
        assertEquals("staging", prefs.environment)
    }

    @Test
    fun `map and api come from the same deployment`() {
        assertEquals("https://app.meteocool.com/", MeteocoolEnvironment.APP.apiBase)
        assertEquals("https://next.meteocool.com", MeteocoolEnvironment.STAGING.webHost)
        assertEquals("https://api-next.meteocool.com/", MeteocoolEnvironment.STAGING.apiBase)
        assertEquals("https://demo.meteocool.com", MeteocoolEnvironment.DEMO.webHost)
        assertEquals("https://api-demo.meteocool.com/", MeteocoolEnvironment.DEMO.apiBase)
    }

    @Test
    fun `map url names the app version`() {
        assertEquals(
            "https://app.meteocool.com/android.html?version=${com.meteocool.BuildConfig.VERSION_NAME}",
            MeteocoolEnvironment.APP.mapUrl,
        )
        assertEquals("app.meteocool.com", MeteocoolEnvironment.APP.webHostName)
    }

    @Test
    fun `stored origins map to their api`() {
        assertEquals("https://api-next.meteocool.com/", MeteocoolEnvironment.apiBaseFor("https://staging.meteocool.com/"))
        assertEquals("https://api-demo.meteocool.com/", MeteocoolEnvironment.apiBaseFor("https://api-demo.meteocool.com/"))
        assertNull(MeteocoolEnvironment.apiBaseFor("https://api.ng.meteocool.com/"))
    }
}
