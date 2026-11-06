package com.meteocool.stormlog

import org.junit.Assert.*
import org.junit.Test

class StormLogRulesTest {
    private val entry = """{"id":"one","storm":"storm","lat":48.15,"lon":11.55,"cell_deg":0.1,
        "place_name":null,"started_at":"2026-06-01T12:00:00Z","ended_at":null,"closest_at":"2026-06-01T12:05:00.123456",
        "over":false,"closest_km":2.5,"peak_dbz":42.0,"top_km":8.2,"vil_kg_m2":null,
        "severity":2,"hail":false,"meso":true,"strikes":18,
        "track":{"coordinates":[[11.4,48.2],[11.5,48.15]],"times":[1,2]},
        "share_url":"https://next.meteocool.com/?layer=radar&latLonZ=48.2,11.4,9.00&overlays=cells,lightning&t=20260601T1205Z&shared=20260601T1205Z"}"""

    private fun passage() = StormLogRules.decode("{\"entries\":[$entry]}")!!.single()

    @Test fun decodesBackendFractionalAndNaiveUtcDates() {
        val p = passage()
        assertEquals(300_123L, p.closestAt - p.startedAt)
        assertEquals(2, p.track.size)
        assertNull(p.endedAt)
    }

    @Test fun malformedRecordsFailInsteadOfShowingAnEmptyLog() {
        for (broken in listOf(entry.replace("48.15", "999"), entry.replace("2.5", "\"NaN\""),
            entry.replace("2026-06-01T12:00:00Z", "2026-02-30T12:00:00Z"),
            entry.replace("\"strikes\":18", "\"strikes\":-1"), entry.replace("\"over\":false", "\"over\":\"false\""))) {
            assertNull(StormLogRules.decode("{\"entries\":[$broken]}"))
        }
        assertNull(StormLogRules.decode("{}"))
        assertEquals(emptyList<StormPassage>(), StormLogRules.decode("{\"entries\":[]}"))
    }

    @Test fun sortsAllOrdersWithDeterministicTies() {
        val a = passage().copy(id = "a", startedAt = 1000, endedAt = 2000, over = true, severity = 3, strikes = 1)
        val b = passage().copy(id = "b", startedAt = 2000, endedAt = null, severity = 1, strikes = 99)
        val c = passage().copy(id = "c", startedAt = 100, endedAt = 9000, severity = 2, strikes = 10)
        val input = listOf(c, a, b)
        assertEquals(listOf("b", "a", "c"), StormLogRules.sorted(input, StormSort.RECENT, 10_000).map { it.id })
        assertEquals(listOf("a", "c", "b"), StormLogRules.sorted(input, StormSort.SEVERITY, 10_000).map { it.id })
        assertEquals("a", StormLogRules.sorted(input, StormSort.CLOSEST, 10_000).first().id)
        assertEquals("c", StormLogRules.sorted(input, StormSort.DURATION, 10_000).first().id)
        assertEquals("b", StormLogRules.sorted(input, StormSort.LIGHTNING, 10_000).first().id)
        assertEquals(listOf("a", "z"), StormLogRules.sorted(listOf(a.copy(id = "z"), a), StormSort.RECENT).map { it.id })
    }

    @Test fun sharingRebuildsOnlyPublicStormParametersOnOwnDeployment() {
        val link = StormLogRules.shareUrl(passage().shareUrl + "&token=secret&place=home&redirect=evil", "https://demo.meteocool.com")!!
        assertTrue(link.startsWith("https://demo.meteocool.com/?layer=radar&latLonZ=48.2,11.4,9"))
        assertFalse(link.contains("secret"))
        assertFalse(link.contains("home"))
        assertFalse(link.contains("redirect"))
        assertNull(StormLogRules.shareUrl(passage().shareUrl.replace("48.2,11.4", "NaN,11.4"), "https://next.meteocool.com"))
        assertNull(StormLogRules.shareUrl(passage().shareUrl.replace("https:", "javascript:"), "https://next.meteocool.com"))
        assertNull(StormLogRules.shareUrl(passage().shareUrl, "https://attacker.example"))
    }

    @Test fun invalidTrackIsRejectedAndApproximateAreaRemainsAnArea() {
        assertNull(StormLogRules.decode("{\"entries\":[${entry.replace("[11.4,48.2]", "[11.4,999]")}]}"))
        assertTrue(StormLogRules.approximateRadius(passage()) > 5_000)
        assertEquals(StormLogRules.approximateRadius(passage()), StormLogRules.approximateRadius(passage().copy(cellDegrees = null)), 0.001)
    }
}
