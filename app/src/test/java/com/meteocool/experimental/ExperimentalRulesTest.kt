package com.meteocool.experimental

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream

class ExperimentalRulesTest {
    private val origin = GeoPoint(48.0, 11.0, 25.0)
    @Test fun debounceTimeoutAndSearchMatchIos() {
        val session = FlashToBangRules.Session()
        assertEquals(FlashToBangRules.Session.Tap.FLASH, session.tap(1_000.0, origin))
        assertEquals(FlashToBangRules.Session.Tap.IGNORED, session.tap(1_000.1, origin))
        assertEquals(FlashToBangRules.Session.Tap.BANG, session.tap(1_005.0, origin))
        assertEquals(1_715.0, session.measurement!!.estimate, 0.001)
        val strike = Strike(GeoMath.destination(origin, 1_715.0, 270.0), 999.5)
        session.update(1_010.0, listOf(strike))
        assertEquals(strike, session.measurement!!.match!!.strike)
        assertTrue(session.measurement!!.searching)
        session.update(1_025.0, emptyList())
        assertFalse(session.measurement!!.searching)
        assertEquals(strike, session.measurement!!.match!!.strike)
        assertTrue(session.measurement!!.withinStrikingRange)
        session.tap(2_000.0, origin)
        session.update(2_060.0, emptyList())
        assertEquals(FlashToBangRules.Session.Phase.COUNTING, session.phase)
        session.update(2_060.01, emptyList())
        assertEquals(FlashToBangRules.Session.Phase.TOO_FAR, session.phase)
    }
    @Test fun maximumGapAndInvalidInputDoNotInventMeasurement() {
        val session = FlashToBangRules.Session()
        assertEquals(FlashToBangRules.Session.Tap.IGNORED, session.tap(Double.NaN, origin))
        assertEquals(FlashToBangRules.Session.Tap.IGNORED, session.tap(1.0, GeoPoint(Double.NaN, 0.0)))
        session.tap(1_000.0, origin)
        assertEquals(FlashToBangRules.Session.Tap.BANG, session.tap(1_060.0, origin))
        assertEquals(20_580.0, session.measurement!!.estimate, 0.001)
        assertFalse(session.measurement!!.withinStrikingRange)
    }
    @Test fun matchingUsesExactTimeDistanceAndAmbiguity() {
        val east = Strike(GeoMath.destination(origin, 2_000.0, 90.0), 999.5)
        val west = Strike(GeoMath.destination(origin, 2_000.0, 270.0), 999.5)
        assertEquals(FlashToBangRules.Quality.CLOSE, FlashToBangRules.match(1_000.0, 2_000.0, origin, listOf(east))!!.quality)
        assertEquals(FlashToBangRules.Quality.FAIR, FlashToBangRules.match(1_000.0, 2_000.0, origin, listOf(east, west))!!.quality)
        assertNull(FlashToBangRules.match(1_000.0, 2_000.0, origin, listOf(east.copy(time = 997.49))))
        assertNull(FlashToBangRules.match(1_000.0, 2_000.0, origin, listOf(east.copy(time = 1_000.51))))
        assertNull(FlashToBangRules.match(1_000.0, Double.POSITIVE_INFINITY, origin, listOf(east)))
        assertNull(FlashToBangRules.match(1_000.0, 9_000.0, origin, listOf(east)))
    }
    @Test fun geographyHandlesAntimeridianAndCurvature() {
        val a = GeoPoint(0.0, 179.9)
        val b = GeoMath.destination(a, 30_000.0, 90.0)
        assertTrue(b.longitude < 0)
        assertEquals(30_000.0, GeoMath.distance(a, b), 0.001)
        assertEquals(90.0, GeoMath.bearing(a, b), 0.001)
        assertNull(GeoMath.mercator(Double.NaN, 0.0))
        assertNull(GeoMath.mercator(0.0, Double.POSITIVE_INFINITY))
        val frame = GeoFrame(origin, 0.0)
        val far = frame.at(GeoMath.destination(origin, 100_000.0, 90.0), 0.0)
        assertTrue(far.z < -750 && far.z > -850)
        assertEquals(100_000.0, far.x, 400.0)
    }
    @Test fun lightningParserKeepsMillisecondsAndRejectsMalformedCoordinates() {
        val json = """{"strikes":[{"lat":0,"lon":0,"time_wall":999123.5},{"lat":0,"lon":"NaN","time_wall":999123.5},{"lat":0,"lon":0,"time_wall":1e999},{"lat":0,"lon":0,"time_wall":998000}]}"""
        val parsed = StormData.strikes(json, 1_000.0, GeoPoint(0.0, 0.0))!!
        assertEquals(2, parsed.size)
        assertEquals(999.1235, parsed.first().time, 0.00000001)
        assertNull(StormData.strikes("{", 1_000.0, origin))
        assertNull(StormData.strikes("{\"strikes\":{}}", 1_000.0, origin))
    }
    @Test fun timestampsAcceptBackendUtcFractionAndOffsets() {
        val utc = StormData.timestamp("2026-11-05T12:30:00Z")!!
        assertEquals(utc, StormData.timestamp("2026-11-05T13:30:00+01:00")!!, 0.001)
        assertEquals(utc, StormData.timestamp("2026-11-05T12:30:00")!!, 0.001)
        assertEquals(utc + 0.123, StormData.timestamp("2026-11-05T12:30:00.123456+00:00")!!, 0.001)
        assertNull(StormData.timestamp("2026-02-30T12:00:00Z"))
        assertNull(StormData.timestamp("2026-11-05T12:30:00Ztrailing"))
        assertFalse(StormData.fresh(utc - 1_201, utc))
        assertFalse(StormData.fresh(utc + 61, utc))
    }
    @Test fun mapTargetsAreNotArbitraryUrls() {
        assertTrue(StormData.validTarget("meteoradar/volumes/20261105T123000/de-T100053300344.mcvx"))
        assertTrue(StormData.validTarget("1234567890123456789012"))
        assertFalse(StormData.validTarget("https://other.example/volume"))
        assertFalse(StormData.validTarget("meteoradar/volumes/../../etc/passwd"))
        assertFalse(StormData.validTarget("1234567890123456789012\n"))
    }
    @Test fun staleOrMalformedStormDataIsUnavailable() {
        val now = StormData.timestamp("2026-11-05T12:30:00Z")!!
        assertEquals(emptyList<StormData.Storm>(), StormData.storms("""{"reference_time":"2026-11-05T12:30:00Z","volumes":[]}""", now))
        assertNull(StormData.storms("""{"reference_time":"2026-11-05T12:00:00Z","volumes":[]}""", now))
        assertNull(StormData.storms("""{"reference_time":"2026-11-05T12:30:00Z","volumes":[{"lat":1e999}]}""", now))
        val valid = """{"reference_time":"2026-11-05T12:30:00Z","volumes":[{"code":"T100053300344","path":"meteoradar/volumes/20261105T123000/de-T100053300344.mcvx","lat":48,"lon":11,"tier":2}]}"""
        val storm = StormData.storms(valid, now)!!.single()
        assertEquals("?layer=cells3d&cloud=20261105T123000/de-T100053300344", StormData.mapSearch(storm))
    }
    @Test fun volumeModesCutMeasuredGeometryAtDocumentedBoundaries() {
        fun visible(mode: StormCut.Mode, east: Double = 0.0, north: Double = 0.0, height: Double = 5_000.0, dbz: Double = 40.0, amount: Double = 0.5, heading: Double? = 90.0) =
            StormCut.visible(mode, amount, east, north, height, dbz, 0.0, heading, 500.0, 45.0)
        assertTrue(visible(StormCut.Mode.LIVE))
        assertFalse(visible(StormCut.Mode.LIVE, dbz = Double.NaN))
        assertFalse(visible(StormCut.Mode.PEEL, dbz = 30.0, amount = 1.0))
        assertTrue(visible(StormCut.Mode.SLICE, north = 2_000.0))
        assertFalse(visible(StormCut.Mode.SLICE, north = -2_000.0))
        assertTrue(visible(StormCut.Mode.TURN, north = 2_000.0))
        assertFalse(visible(StormCut.Mode.TRACK, heading = null))
        assertTrue(visible(StormCut.Mode.FLOOR, height = 7_000.0))
        assertFalse(visible(StormCut.Mode.FLOOR, height = 6_000.0))
        assertTrue(visible(StormCut.Mode.SHELLS, dbz = 35.0))
        assertFalse(visible(StormCut.Mode.SHELLS, dbz = 40.0))
        assertTrue(visible(StormCut.Mode.SHAFT, height = 2_000.0))
        assertFalse(visible(StormCut.Mode.SHAFT, height = 2_001.0))
        assertTrue(visible(StormCut.Mode.TOPS, height = 8_000.0))
        assertFalse(visible(StormCut.Mode.TOPS, height = 7_999.0))
    }
    @Test fun historicalScansUseNetworkClocksAndValidTileBoundaries() {
        val newest = 1_759_702_200L
        assertEquals(24, StormHistory.earlier(newest).size)
        assertEquals(newest - 7_200, StormHistory.earlier(newest).last())
        assertEquals(newest - 960, StormHistory.onClock(newest - 60, newest - 900))
        val path = "meteoradar/volumes/20251005T221000/de-T100053300344.mcvx"
        assertEquals(newest, StormHistory.scan(path))
        assertNull(StormHistory.scan("meteoradar/volumes/20260230T221000/de-T100053300344.mcvx"))
        assertEquals(StormHistory.Tile("de", 10, 533, 344), StormHistory.tile(path))
        assertEquals("meteoradar/volumes/20251005T220500/fr-T090026800175.mcvx", StormHistory.path(StormHistory.Tile("fr", 9, 268, 175), newest - 300))
        assertNull(StormHistory.path(StormHistory.Tile("de", 9, -1, 0), newest))
        val quiet = StormHistory.Quarter(StormHistory.Tile("de", 10, 4, 6), 40.0, 1)
        assertTrue(StormHistory.asks(quiet).first.isEmpty())
        assertEquals(4, StormHistory.asks(quiet.copy(peak = 55.0, columns = 2)).first.size)
        assertEquals(4, StormHistory.asks(quiet.copy(columns = 2)).second.size)
    }
    @Test fun historicQuarterDiscoveryUsesConfidenceAndNorthFacingRows() {
        val decoded = StormVolume.decode(volume())!!
        val quarters = StormHistory.quarters(decoded, StormHistory.Tile("de", 9, 268, 175))
        assertEquals(4, quarters.size)
        assertEquals(StormHistory.Tile("de", 10, 536, 350), quarters.first().tile)
        assertTrue(quarters.all { it.peak == 40.0 && it.columns == 1 })
    }
    @Test fun motionIsBoundedAndMissingMotionStaysMeasured() {
        val moved = StormMotion.advance(origin, 90.0, 60.0, 10.0)
        assertEquals(10_000.0, GeoMath.distance(origin, moved), 0.01)
        assertEquals(90.0, GeoMath.bearing(origin, moved), 0.01)
        assertEquals(origin, StormMotion.advance(origin, null, 60.0, 10.0))
        assertEquals(origin, StormMotion.advance(origin, 90.0, Double.NaN, 10.0))
        assertEquals(30.0, StormMotion.minutes(1_000.0, 10_000.0), 0.0)
        assertEquals(0.0, StormMotion.minutes(10_000.0, 1_000.0), 0.0)
    }
    @Test fun solarPositionMatchesIosSolsticeReferenceAndRejectsWrongTaps() {
        val day = java.time.Instant.parse("2026-06-21T00:00:00Z").epochSecond.toDouble()
        val munich = GeoPoint(48.14, 11.58)
        val peak = (0 until 1440).map { minute -> minute to SunAlignment.at(day + minute * 60, munich)!! }.maxBy { it.second.elevation }
        assertEquals(65.3, peak.second.elevation, 0.1)
        assertEquals(180.0, peak.second.azimuth, 1.5)
        assertTrue(kotlin.math.abs(peak.first - 676) < 4)
        assertTrue(SunAlignment.at(day + 23 * 3600, munich)!!.elevation < -10)
        val sun = SunAlignment.Position(180.0, 45.0)
        assertEquals(10.0, SunAlignment.fix(170.0, 46.0, sun).correction!!, 0.0001)
        assertEquals(SunAlignment.Failure.WRONG_ELEVATION, SunAlignment.fix(180.0, 52.0, sun).failure)
        assertEquals(SunAlignment.Failure.BELOW_HORIZON, SunAlignment.fix(180.0, 0.0, sun.copy(elevation = -1.0)).failure)
        assertNull(SunAlignment.fix(Double.NaN, 45.0, sun).correction)
    }
    @Test fun overlaysRejectStaleMalformedOrNonFiniteDetections() {
        val meso = StormData.mesocyclones("""[{"lat":0,"lon":0,"time":999000,"diameter":4000,"intensity":3},{"lat":0,"lon":0,"time":-300001},{"lat":1e999,"lon":0,"time":999000}]""", 1_000.0)!!
        assertEquals(1, meso.size)
        assertEquals(4_000.0, meso.single().diameter, 0.01)
        assertEquals(3, meso.single().intensity)
        val strikes = StormData.recentLightning("""[{"lat":0,"lon":0,"time":999000},{"lat":0,"lon":0,"time":1000}]""", 1_000.0)!!
        assertEquals(1, strikes.size)
        assertNull(StormData.mesocyclones("{}", 1_000.0))
        assertNull(StormData.recentLightning("not json", 1_000.0))
    }
    @Test fun markerSeparatesEchoAssumedBaseGroundAndUnknownHorizon() {
        val frame = GeoFrame(origin, 500.0)
        val zero = GeoFrame.Vector(0.0, 0.0, 0.0)
        val up = SkyMarkerRules.Ray(zero, GeoFrame.Vector(0.0, 0.0, 1.0))
        val assumed = SkyMarkerRules.mark(up, frame, 500.0, emptySequence())!!
        assertEquals(SkyMarker.Source.ASSUMED_BASE, assumed.source)
        assertEquals(1_998.4, assumed.altitude!!, 0.05)
        assertTrue(assumed.mapSearch.contains("&point="))
        val echo = SkyMarkerRules.mark(up, frame, 500.0, sequenceOf(
            SkyMarkerRules.Echo(GeoFrame.Vector(0.0, 0.0, 1_000.0), 50.0),
            SkyMarkerRules.Echo(GeoFrame.Vector(0.0, 0.0, 500.0), 25.0),
        ))!!
        assertEquals(SkyMarker.Source.ECHO, echo.source)
        assertEquals(975.0, echo.altitude!!, 0.05)
        val ground = SkyMarkerRules.mark(SkyMarkerRules.Ray(zero, GeoFrame.Vector(0.0, 0.0, -1.0)), frame, 500.0, emptySequence())!!
        assertEquals(SkyMarker.Source.GROUND, ground.source)
        assertNull(ground.altitude)
        assertNull(SkyMarkerRules.mark(SkyMarkerRules.Ray(zero, GeoFrame.Vector(0.0, 1.0, 0.0)), frame, 500.0, emptySequence()))
        assertNull(SkyMarkerRules.mark(SkyMarkerRules.Ray(zero, zero), frame, 500.0, emptySequence()))
    }
    @Test fun groundMarkerStaysBelowEyeAtSeaLevelAndNegativeAltitudes() {
        val zero = GeoFrame.Vector(0.0, 0.0, 0.0)
        for (altitude in listOf(0.0, 0.1, 1.59, -10.0, -430.0, 500.0)) {
            val frame = GeoFrame(origin, altitude)
            val down = SkyMarkerRules.mark(SkyMarkerRules.Ray(zero, GeoFrame.Vector(0.0, 0.0, -1.0)), frame, altitude, emptySequence())
            assertNotNull("Vertical ground tap at eye altitude $altitude", down)
            assertEquals(SkyMarker.Source.GROUND, down!!.source)
            assertEquals(0.0, GeoMath.distance(origin, down.point), 0.01)
            val diagonal = SkyMarkerRules.mark(SkyMarkerRules.Ray(zero, GeoFrame.Vector(0.0, 1.0, -1.0)), frame, altitude, emptySequence())
            assertNotNull("Diagonal ground tap at eye altitude $altitude", diagonal)
            assertEquals(SkyMarker.Source.GROUND, diagonal!!.source)
            assertEquals(1.6, GeoMath.distance(origin, diagonal.point), 0.02)
            val up = SkyMarkerRules.mark(SkyMarkerRules.Ray(zero, GeoFrame.Vector(0.0, 0.0, 1.0)), frame, altitude, emptySequence())!!
            assertEquals(SkyMarker.Source.ASSUMED_BASE, up.source)
            assertEquals(altitude + 1_498.4, up.altitude!!, 0.05)
        }
    }
    @Test fun shallowGroundTapUsesNearIntersectionInsteadOfFarSideOfEarth() {
        val elevation = Math.toRadians(-0.1)
        val ray = SkyMarkerRules.Ray(GeoFrame.Vector(0.0, 0.0, 0.0), GeoFrame.Vector(0.0, kotlin.math.cos(elevation), kotlin.math.sin(elevation)))
        for (altitude in listOf(0.0, 0.1, -430.0)) {
            val marker = SkyMarkerRules.mark(ray, GeoFrame(origin, altitude), altitude, emptySequence())
            assertNotNull("Shallow ground tap at eye altitude $altitude", marker)
            assertEquals(SkyMarker.Source.GROUND, marker!!.source)
            assertTrue("Local ground intersection at eye altitude $altitude", GeoMath.distance(origin, marker.point) in 800.0..1_100.0)
        }
    }
    @Test fun geodeticRoundTripKeepsMarkerLocationAndAltitude() {
        val frame = GeoFrame(origin, 500.0)
        val target = GeoMath.destination(origin, 80_000.0, 60.0)
        val recovered = frame.geodetic(frame.at(target, 7_500.0))!!
        assertEquals(target.latitude, recovered.first.latitude, 0.000001)
        assertEquals(target.longitude, recovered.first.longitude, 0.000001)
        assertEquals(7_500.0, recovered.second, 0.01)
    }
    @Test fun trackParserHonorsLonLatAndRejectsMalformedLines() {
        val code = "1234567890123456789012"
        val json = """{"type":"Feature","properties":{"code":"1234567890123456789012","placement":{"place":"Munich"}},"geometry":{"type":"LineString","coordinates":[[11,48],[11.1,48.1]]}}"""
        val track = StormData.track(json, code)!!
        assertEquals(GeoPoint(48.0, 11.0), track.points.first())
        assertEquals("Munich", track.place)
        assertNull(StormData.track(json, "9999999999999999999999"))
        assertNull(StormData.track(json.replace("[11.1,48.1]", "[11.1,1e999]"), code))
        assertNull(StormData.track(json.replace("LineString", "Polygon"), code))
    }
    @Test fun forecastParserKeepsUncertaintyAndHazardsWithoutInventingMissingCoordinates() {
        val now = StormData.timestamp("2026-11-05T12:30:00Z")!!
        val json = """{"reference_time":"2026-11-05T12:30:00Z","cells":[{"code":"1234567890123456789012","lat":48,"lon":11,"hail_flag":1,"gust_flag":1,"heavy_rain_flag":1,"meso_severity":2,"lightning_rate":17,"forecast":[{"t":"2026-11-05T12:35:00Z","lat":48.1,"lon":11.1,"major_km":5,"minor_km":2,"angle_deg":45},{"t":"2026-11-05T12:40:00Z","lat":48.2}]}]}"""
        val cell = StormData.cells(json, now)!!.single()
        assertTrue(cell.hail && cell.gust && cell.heavyRain && cell.mesocyclone)
        assertEquals(17, cell.lightningRate)
        assertEquals(1, cell.forecast.size)
        assertEquals(5.0, cell.forecast.single().majorKm, 0.0)
        assertEquals(45.0, cell.forecast.single().angle, 0.0)
    }
    @Test fun independentlyPolledCellsUseContainingTileAndStrongestCell() {
        val path = "meteoradar/volumes/20261105T123000/de-T100051200512.mcvx"
        val point = GeoPoint(-0.1, 0.1)
        val storm = StormData.Storm("tile", point, path, "system", 1.0, 2, 50.0)
        val cell = StormData.Cell("1234567890123456789012", point, path.replace("123000", "123500"), null, 45.0, 90.0, 60.0)
        val stronger = cell.copy(code = "1234567890123456789013", peakDbz = 55.0)
        assertEquals(stronger, StormData.linkCells(listOf(storm), listOf(cell, stronger))[path])
        assertEquals(stronger, StormData.strongestCell(storm, listOf(storm), listOf(cell, stronger)))
        val elsewhere = storm.copy(path = path.replace("0051200512", "0051300512"), system = "other")
        assertNull(StormData.linkCells(listOf(storm, elsewhere), listOf(cell.copy(path = elsewhere.path)))[path])
        assertFalse(StormHistory.tile(path)!!.contains(GeoPoint(90.0, 0.1)))
        assertFalse(StormHistory.tile(path)!!.contains(GeoPoint(Double.NaN, 0.1)))
    }
    @Test fun uncertaintyEllipseAndMotionArrowUseGroundDistances() {
        val ellipse = StormTracks.ellipse(origin, 10_000.0, 5_000.0, 90.0)
        assertEquals(33, ellipse.size)
        assertEquals(10_000.0, GeoMath.distance(origin, ellipse.first()), 0.01)
        assertEquals(90.0, GeoMath.bearing(origin, ellipse.first()), 0.01)
        assertEquals(5_000.0, GeoMath.distance(origin, ellipse[8]), 0.01)
        assertEquals(0.0, GeoMath.distance(ellipse.first(), ellipse.last()), 0.01)
        assertTrue(StormTracks.ellipse(origin, Double.NaN, 5_000.0, 90.0).isEmpty())
        val arrow = StormTracks.motionArrow(origin, 90.0, 60.0)
        assertEquals(3, arrow.size)
        assertEquals(15_000.0, GeoMath.distance(origin, arrow.first().last()), 0.01)
        assertTrue(StormTracks.motionArrow(origin, null, 60.0).isEmpty())
    }
    private fun volume(header: String = """{"lat":48,"lon":11,"nx":2,"ny":2,"nz":2,"step_m":[250,250,500],"origin_m":[-250,-250,0],"dbz_floor":-32,"dbz_scale":2}""", voxels: ByteArray = ByteArray(16) { if (it % 2 == 0) 144.toByte() else 255.toByte() }): ByteArray {
        val json = header.toByteArray()
        return ByteBuffer.allocate(12 + json.size + voxels.size).order(ByteOrder.LITTLE_ENDIAN).put("MCVX".toByteArray()).putInt(1).putInt(json.size).put(json).put(voxels).array()
    }
    @Test fun volumeReadsRealVoxelsAndGzip() {
        val bytes = volume()
        val decoded = StormVolume.decode(bytes)!!
        val sample = decoded.sample(0, 0, 0)!!
        assertEquals(-125.0, sample.east, 0.01)
        assertEquals(250.0, sample.altitude, 0.01)
        assertEquals(40.0, sample.dbz, 0.01)
        assertNull(decoded.sample(-1, 0, 0))
        assertEquals(8, decoded.points().size)
        val compressed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
        assertEquals(sample, StormVolume.decode(compressed)!!.sample(0, 0, 0))
    }
    @Test fun malformedVolumesAreBoundedAndRejected() {
        val bytes = volume()
        assertNull(StormVolume.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(StormVolume.decode(bytes.copyOf().also { it[4] = 2 }))
        assertNull(StormVolume.decode(bytes.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(8, Int.MAX_VALUE) }))
        assertNull(StormVolume.decode(volume("""{"lat":48,"lon":11,"nx":2147483647,"ny":2147483647,"nz":2147483647}""")))
        assertNull(StormVolume.decode(volume("""{"lat":48,"lon":11,"nx":2,"ny":2,"nz":2,"step_m":[250,250,1e999],"origin_m":[0,0,0],"dbz_floor":-32,"dbz_scale":2}""")))
        assertNull(StormVolume.decode(byteArrayOf(0x1f, 0x8b.toByte(), 1)))
    }
    @Test fun absentConfidenceDoesNotBecomeMeasuredRain() {
        val voxels = ByteArray(16) { if (it % 2 == 0) 255.toByte() else 0 }
        assertTrue(StormVolume.decode(volume(voxels = voxels))!!.points().isEmpty())
    }
}
