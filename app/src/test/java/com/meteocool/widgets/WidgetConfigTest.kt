package com.meteocool.widgets

import com.meteocool.location.MeteocoolLocation
import org.junit.Assert.*
import org.junit.Test

class WidgetConfigTest {
    @Test fun `large text leaves space for the full forecast summary`() {
        assertEquals(3, visiblePlaceCount(280, 1f))
        assertEquals(6, visiblePlaceCount(464, 1f))
        assertEquals(4, visiblePlaceCount(464, 1.5f))
        assertEquals(1, visiblePlaceCount(200, 2f))
    }

    @Test fun `all widget options round trip`() {
        val config = WidgetConfig(listOf(WidgetPlace("Munich", 48.1, 11.5), WidgetPlace("current")),
            WidgetRange.ALL, 6.5, 2, 41, false, 2)
        assertEquals(config, WidgetConfig.decode(WidgetConfig.encode(config)))
        assertEquals(WidgetConfig(), WidgetConfig.decode(WidgetConfig.encode(WidgetConfig())))
    }

    @Test fun `bad configuration cannot make a network location`() {
        assertNull(WidgetConfig.decode(null))
        assertNull(WidgetConfig.decode("{}"))
        assertNull(WidgetConfig.decode("not json"))
        for (place in listOf(WidgetPlace("bad", 91.0, 0.0), WidgetPlace("bad", 0.0, -181.0),
            WidgetPlace("bad", 1.0, null), WidgetPlace("", 1.0, 1.0))) {
            assertNull(WidgetConfig.decode(WidgetConfig.encode(WidgetConfig(listOf(place)))))
        }
        assertNull(WidgetConfig.decode(WidgetConfig.encode(WidgetConfig(emptyList()))))
        assertNull(WidgetConfig.decode(WidgetConfig.encode(WidgetConfig(List(7) { WidgetPlace("$it", 1.0, 2.0) })) ))
        assertNull(WidgetConfig.decode(WidgetConfig.encode(WidgetConfig(threshold = 100))))
        assertNull(WidgetConfig.decode(WidgetConfig.encode(WidgetConfig(zoom = 99.0))))
        assertNull(WidgetConfig.decode(WidgetConfig.encode(WidgetConfig(dialHours = 3))))
    }

    @Test fun `location must be recent finite and accurate`() {
        val now = 1_800_000_000_000L
        val fix = MeteocoolLocation(48.1, 11.5, 0.0, 100f, -1f, -1f, -1f, now - 60_000, 0)
        assertTrue(WidgetData.usableLocation(fix, now, 60_000))
        assertFalse(WidgetData.usableLocation(fix.copy(timeMillis = now - 60_001), now, 60_000))
        assertFalse(WidgetData.usableLocation(fix.copy(timeMillis = now + 60_001), now, 60_000))
        assertFalse(WidgetData.usableLocation(fix.copy(accuracy = -1f), now, 60_000))
        assertFalse(WidgetData.usableLocation(fix.copy(accuracy = 1001f), now, 60_000))
        assertFalse(WidgetData.usableLocation(fix.copy(accuracy = Float.NaN), now, 60_000))
        assertFalse(WidgetData.usableLocation(fix.copy(latitude = Double.NaN), now, 60_000))
        assertTrue(WidgetData.usableLocation(fix.copy(timeMillis = now - 300_000), now, 300_000))
        assertFalse(WidgetData.usableLocation(fix.copy(timeMillis = now - 300_001), now, 300_000))
    }
}
