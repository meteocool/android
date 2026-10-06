package com.meteocool.ui.map

import com.meteocool.ui.map.MapRecovery.Failure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapRecoveryTest {

    /** A map page that does what each test says, and counts what the recovery does to it. */
    private class Page(test: TestScope) {
        var loading = false
        var foreground = true
        /** What the page answers when asked whether it runs; null for a hung page. */
        var answer: Boolean? = true
        var reloads = 0
        var downs = 0
        var status = false

        val recovery = MapRecovery(
            scope = test.backgroundScope,
            reload = { reloads += 1 },
            wentDown = { downs += 1 },
            showStatus = { status = it },
            isLoading = { loading },
            isForeground = { foreground },
            probe = { reply -> answer?.let(reply) },
            now = { test.testScheduler.currentTime },
        )
    }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `retries back off from one to fifteen seconds`() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 15_000L), (1..6).map(MapRecovery::delayAfter))
    }

    @Test
    fun `a page that does not report in is loaded again`() = runTest {
        val page = Page(this)
        page.recovery.loadStarted()
        wait(MapRecovery.READY_TIMEOUT_MS - 1)
        assertEquals(0, page.downs)
        wait(1)
        assertEquals(1, page.downs)
        assertFalse("The first retry says nothing", page.status)
        wait(1_000)
        assertEquals(1, page.reloads)
    }

    @Test
    fun `a page still loading on a slow connection gets longer`() = runTest {
        val page = Page(this)
        page.loading = true
        page.recovery.loadStarted()
        wait(MapRecovery.MAX_LOAD_TIME_MS - 1)
        assertEquals(0, page.downs)
        wait(1)
        assertEquals(1, page.downs)
    }

    @Test
    fun `the status shows from the second failure until the page reports in`() = runTest {
        val page = Page(this)
        page.recovery.loadStarted()
        page.recovery.failed(Failure.NAVIGATION)
        assertFalse(page.status)
        wait(1_000)
        page.recovery.loadStarted()
        page.recovery.failed(Failure.NAVIGATION)
        assertTrue(page.status)
        wait(2_000)
        assertEquals(2, page.reloads)
        page.recovery.loadStarted()
        page.recovery.loadSucceeded()
        assertFalse(page.status)
    }

    @Test
    fun `two reports of one failure count once`() = runTest {
        val page = Page(this)
        page.recovery.loadStarted()
        page.recovery.failed(Failure.NAVIGATION)
        page.recovery.failed(Failure.CRASH)
        assertEquals(1, page.downs)
        wait(1_000)
        assertEquals(1, page.reloads)
    }

    @Test
    fun `a page that fails behind another screen is retried when the map is back`() = runTest {
        val page = Page(this)
        page.foreground = false
        page.recovery.loadStarted()
        page.recovery.loadSucceeded()
        page.recovery.failed(Failure.CRASH)
        wait(60_000)
        assertEquals(0, page.reloads)
        page.foreground = true
        page.recovery.becameActive()
        assertEquals(1, page.reloads)
    }

    @Test
    fun `a network coming up retries at once`() = runTest {
        val page = Page(this)
        page.recovery.networkAvailable()
        assertEquals("Nothing to retry", 0, page.reloads)
        page.recovery.loadStarted()
        page.recovery.failed(Failure.NAVIGATION)
        page.recovery.networkAvailable()
        assertEquals(1, page.reloads)
        wait(60_000)
        assertEquals("The backoff's retry was replaced, not added", 1, page.reloads)
    }

    @Test
    fun `a page that stayed up a while starts the backoff over`() = runTest {
        val page = Page(this)
        repeat(2) {
            page.recovery.loadStarted()
            page.recovery.failed(Failure.NAVIGATION)
            wait(MapRecovery.delayAfter(it + 1))
        }
        page.recovery.loadStarted()
        page.recovery.loadSucceeded()
        wait(MapRecovery.STABLE_AFTER_MS + 1)
        page.recovery.failed(Failure.CRASH)
        wait(1_000)
        assertEquals(3, page.reloads)
        assertFalse(page.status)
    }

    @Test
    fun `a page back in the foreground has to answer`() = runTest {
        val page = Page(this)
        page.recovery.loadStarted()
        page.recovery.loadSucceeded()

        page.recovery.becameActive()
        wait(MapRecovery.PROBE_TIMEOUT_MS)
        assertEquals("It answered", 0, page.downs)

        page.answer = false
        page.recovery.becameActive()
        assertEquals("Its functions are gone", 1, page.downs)

        page.recovery.loadStarted()
        page.recovery.loadSucceeded()
        page.answer = null
        page.recovery.becameActive()
        wait(MapRecovery.PROBE_TIMEOUT_MS)
        assertEquals("It hangs", 2, page.downs)
    }

    @Test
    fun `a late answer does not fail the page loaded since`() = runTest {
        val page = Page(this)
        page.recovery.loadStarted()
        page.recovery.loadSucceeded()
        page.answer = null
        page.recovery.becameActive()
        page.recovery.loadStarted()
        page.recovery.loadSucceeded()
        wait(MapRecovery.PROBE_TIMEOUT_MS)
        assertEquals(0, page.downs)
    }
}
