package com.meteocool.weather

import org.junit.Assert.*
import org.junit.Test

class RainForecastTest {
    private val now = 1_800_000_000L
    private fun forecast(vararg values: Double?) = RainForecast(now, 300, values.toList(), 1, 20.0)

    @Test fun `missing and stale observations never mean dry`() {
        assertEquals(RainForecast.Phase.Unavailable, forecast(null, 0.0, 0.0).phase(now))
        assertEquals(RainForecast.Phase.Unavailable, forecast(0.0, null, 0.0).phase(now))
        assertEquals(RainForecast.Phase.Unavailable, forecast(0.0, 0.0, 0.0).phase(now + 3600))
        assertEquals(RainForecast.Phase.Unavailable, forecast(0.0, 0.0).copy(observed = 0).phase(now))
        assertEquals(RainForecast.Phase.Unavailable, forecast(0.0, Double.NaN).phase(now))
        assertEquals(RainForecast.Phase.Unavailable, forecast(0.0, 0.0).phase(now - 300))
    }

    @Test fun `a drizzle dip does not split one rain`() {
        val f = forecast(24.0, 13.0, 24.0, 0.0, 0.0)
        assertEquals(RainForecast.Phase.Raining(now + 900), f.phase(now))
        assertEquals(RainForecast.Phase.Approaching(now + 300, now + 600), forecast(0.0, 24.0, 0.0, 0.0).phase(now))
        assertEquals(RainForecast.Phase.Dry, forecast(0.0, 0.0, 0.0).phase(now))
    }

    @Test fun `continuing rain has no invented end`() {
        assertEquals(RainForecast.Phase.Raining(null), forecast(25.0, 24.0, 24.0).phase(now))
    }

    @Test fun `parser sorts times and rejects irregular or malformed frames`() {
        val good = """{"frames":{"1800000300":{"dbz":0,"source":"forecast"},"1800000000":{"dbz":24,"source":"observation"}}}"""
        val parsed = RainForecast.fromTimeseries(good, 20.0)!!
        assertEquals(listOf(24.0, 0.0), parsed.dbz)
        assertEquals(1, parsed.observed)
        assertNull(RainForecast.fromTimeseries("{}", 20.0))
        assertNull(RainForecast.fromTimeseries(good.replace("1800000300", "wrong"), 20.0))
        assertNull(RainForecast.fromTimeseries(good.replace("\"dbz\":24", "\"dbz\":\"dry\""), 20.0))
    }
}
