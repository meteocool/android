package com.meteocool.notifications

import com.meteocool.location.MeteocoolLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `post_location` contract of the v4 backend's legacy router. */
class RegistrationTest {

    private val second = 1_000_000_000L

    private fun fix(
        lat: Double = 48.14,
        lon: Double = 11.58,
        accuracy: Float = 20f,
        verticalAccuracy: Float = -1f,
        elapsed: Long = 1_000 * second,
    ) = MeteocoolLocation(lat, lon, 520.0, accuracy, verticalAccuracy, -1f, -1f, 1_790_000_000_123L, elapsed)

    private val settings = RegistrationSettings(
        token = "a".repeat(64),
        aheadMinutes = 45,
        intensityDbz = 41,
        withDbz = true,
        experimental = false,
        lang = "en",
    )

    @Test
    fun `payload carries every field the backend reads`() {
        val body = Registration.payload(fix(), 1013.2f, settings)
        assertEquals(48.14, body["lat"])
        assertEquals(11.58, body["lon"])
        assertEquals(45, body["ahead"])
        assertEquals(41, body["intensity"])
        assertEquals(true, body["withDBZ"])
        assertEquals(true, body["details"])
        assertEquals("android", body["source"])
        assertEquals("en", body["lang"])
        assertEquals("a".repeat(64), body["token"])
        assertEquals(false, body["experimental"])
        assertEquals(-1.0, body["speed"])
        assertEquals(-1.0, body["course"])
        assertEquals(1013.2f.toDouble(), body["pressure"] as Double, 0.0001)
    }

    @Test
    fun `timestamp is unix seconds`() {
        assertEquals(1_790_000_000.123, Registration.payload(fix(), -1f, settings)["timestamp"] as Double, 0.0001)
    }

    @Test
    fun `language is clamped to de or en`() {
        assertEquals("de", Registration.lang("de"))
        assertEquals("de", Registration.lang("DE"))
        assertEquals("en", Registration.lang("en"))
        assertEquals("en", Registration.lang("fr"))
        assertEquals("en", Registration.lang(""))
    }

    @Test
    fun `token must be 32 to 192 characters`() {
        assertFalse(Registration.isValidToken(null))
        assertFalse(Registration.isValidToken("no token"))
        assertFalse(Registration.isValidToken("a".repeat(31)))
        assertTrue(Registration.isValidToken("a".repeat(32)))
        assertTrue(Registration.isValidToken("a".repeat(192)))
        assertFalse(Registration.isValidToken("a".repeat(193)))
    }

    @Test
    fun `a fix older than five minutes is stale`() {
        val f = fix(elapsed = 1_000 * second)
        assertTrue(Registration.isFresh(f, 1_000 * second))
        assertTrue(Registration.isFresh(f, 1_299 * second))
        assertFalse(Registration.isFresh(f, 1_300 * second))
        assertFalse(Registration.isFresh(fix(accuracy = -1f), 1_000 * second))
    }

    @Test
    fun `the first fix is always significant`() {
        assertTrue(Registration.isSignificant(fix(), null))
    }

    @Test
    fun `a more accurate fix is significant`() {
        assertTrue(Registration.isSignificant(fix(accuracy = 10f), fix(accuracy = 20f)))
        assertFalse(Registration.isSignificant(fix(accuracy = 20f), fix(accuracy = 20f)))
    }

    @Test
    fun `vertical accuracy has to improve by more than a metre`() {
        assertFalse(Registration.isSignificant(fix(verticalAccuracy = 4.5f), fix(verticalAccuracy = 5f)))
        assertTrue(Registration.isSignificant(fix(verticalAccuracy = 3.5f), fix(verticalAccuracy = 5f)))
    }

    @Test
    fun `moving counts after 500 m and a minute`() {
        val last = fix(elapsed = 0)
        // About 1.1 km north.
        assertFalse(Registration.isSignificant(fix(lat = 48.15, elapsed = 30 * second), last))
        assertTrue(Registration.isSignificant(fix(lat = 48.15, elapsed = 60 * second), last))
        // About 110 m north, however long it took.
        assertFalse(Registration.isSignificant(fix(lat = 48.141, elapsed = 600 * second), last))
    }

    @Test
    fun `distance is haversine`() {
        val munich = fix(lat = 48.137, lon = 11.575)
        val berlin = fix(lat = 52.520, lon = 13.405)
        assertEquals(504_000.0, Registration.distanceMeters(munich, berlin), 2_000.0)
    }
}
