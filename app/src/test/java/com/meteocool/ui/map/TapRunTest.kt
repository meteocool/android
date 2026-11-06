package com.meteocool.ui.map

import org.junit.Assert.*
import org.junit.Test

class TapRunTest {
    @Test fun `three taps in one second trigger once and old taps expire`() {
        val run = TapRun()
        assertFalse(run.tap(0))
        assertFalse(run.tap(500))
        assertTrue(run.tap(1000))
        assertFalse(run.tap(1001))
        assertFalse(run.tap(3000))
        assertFalse(run.tap(3100))
        assertTrue(run.tap(3200))
    }
}
