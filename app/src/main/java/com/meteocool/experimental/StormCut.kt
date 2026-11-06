package com.meteocool.experimental

import kotlin.math.*

/** Cross sections use the measured altitude and the same thresholds and ranges as iOS ARViewMode. */
object StormCut {
    enum class Mode { LIVE, PEEL, SLICE, TURN, TRACK, FLOOR, SHELLS, SHAFT, TOPS }
    fun visible(mode: Mode, amount: Double, east: Double, north: Double, altitude: Double, dbz: Double, awayBearing: Double, heading: Double?, ground: Double, peelFloor: Double): Boolean {
        if (!amount.isFinite() || !east.isFinite() || !north.isFinite() || !altitude.isFinite() || !dbz.isFinite() || !awayBearing.isFinite() || !ground.isFinite() || !peelFloor.isFinite()) return false
        if (dbz < 20) return false
        val value = amount.coerceIn(0.0, 1.0)
        val a = Math.toRadians(awayBearing)
        return when (mode) {
            Mode.LIVE -> true
            Mode.PEEL -> dbz >= 20 + value * (peelFloor - 20).coerceAtLeast(0.0)
            Mode.SLICE -> east * sin(a) + north * cos(a) >= (value * 30 - 15) * 1_000
            Mode.TURN -> { val cut = a + Math.toRadians(value * 160 - 80); east * sin(cut) + north * cos(cut) >= 0 }
            Mode.TRACK -> {
                if (heading == null || !heading.isFinite()) false
                else {
                    val h = Math.toRadians(heading)
                    val towardViewer = -cos(h) * sin(a) + sin(h) * cos(a)
                    val sign = if (towardViewer < 0) -1 else 1
                    sign * (east * cos(h) - north * sin(h)) <= (value * 20 - 10) * 1_000
                }
            }
            Mode.FLOOR -> altitude >= (1 + value * 11) * 1_000
            Mode.SHELLS -> abs(dbz - 35) <= 1.6 || abs(dbz - 45) <= 1.6 || abs(dbz - 55) <= 1.6
            Mode.SHAFT -> altitude <= ground + 1_500
            Mode.TOPS -> altitude >= 8_000
        }
    }
}
