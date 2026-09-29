package com.meteocool.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeteocoolEnvironmentTest {

    @Test
    fun `demo wins over experimental, which wins over app`() {
        assertEquals(MeteocoolEnvironment.APP, MeteocoolEnvironment.select(demoMode = false, experimentalFeatures = false))
        assertEquals(MeteocoolEnvironment.STAGING, MeteocoolEnvironment.select(demoMode = false, experimentalFeatures = true))
        assertEquals(MeteocoolEnvironment.DEMO, MeteocoolEnvironment.select(demoMode = true, experimentalFeatures = true))
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
