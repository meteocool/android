package com.meteocool.notifications

import com.meteocool.weather.RainForecast
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AlertParityTest {
    @Test fun `a day mute preserves the wall clock across daylight saving`() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"))
        calendar.set(2026, Calendar.MARCH, 28, 12, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        assertEquals(23 * 3600L, AlertMute.timed(1, calendar).until - calendar.timeInMillis / 1000)
    }

    @Test fun `rain mute follows one spell and keeps unknown forecasts muted`() {
        val now = 1_800_000_000L
        val rain = RainForecast(now, 300, listOf(25.0, 0.0, 0.0), 1, 20.0)
        val mute = AlertMute.rain(rain, now)!!
        assertEquals(now + 300, mute.until)
        assertTrue(mute.rainBegan)
        assertNull(mute.following(rain.copy(dbz = listOf(0.0, 25.0, 0.0)), now))
        assertEquals(mute, mute.following(rain.copy(dbz = listOf(null, null, null)), now))
    }

    @Test fun `coverage does not turn failures into an outside warning`() {
        assertEquals(true, NotificationCoverage.outside("""{"domain":null}"""))
        assertEquals(false, NotificationCoverage.outside("""{"domain":"dwd"}"""))
        assertNull(NotificationCoverage.outside("{}"))
        assertNull(NotificationCoverage.outside("""{"domain":false}"""))
        assertNull(NotificationCoverage.outside("not JSON"))
    }

    @Test fun `history deduplicates delivered recovery and bounds retention`() {
        val now = 1_800_000_000_000L
        val alert = ReceivedAlert("fcm", "Rain", "Soon", now)
        assertEquals(1, AlertHistory.merged(listOf(alert), listOf(alert.copy(request = "system", received = now - 1000)), now).size)
        assertTrue(AlertHistory.merged(listOf(alert.copy(received = now - 15 * 86400_000L)), emptyList(), now).isEmpty())
        val many = (0..220).map { ReceivedAlert("$it", "$it", "soon", now - it * 300_000) }
        assertEquals(200, AlertHistory.merged(many, emptyList(), now).size)
        assertEquals("0", AlertHistory.merged(many, emptyList(), now).first().request)
    }
}
