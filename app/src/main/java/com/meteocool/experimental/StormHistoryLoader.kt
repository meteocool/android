package com.meteocool.experimental

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Archives have no index: locate measured coarse tiles, then follow their echo into fine tiles. */
internal class StormHistoryLoader(private val feed: StormFeed) {
    data class Result(val clouds: List<StormView.Cloud>, val available: Int)
    suspend fun load(picked: Long, listed: List<StormData.Storm>): Result = coroutineScope {
        val known = listed.mapNotNull { storm ->
            val tile = StormHistory.tile(storm.path) ?: return@mapNotNull null
            StormHistory.scan(storm.path)?.let { tile.network to it }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
        val semaphore = Semaphore(4)
        val groups = StormHistory.places(listed).map { coarse ->
            async {
                semaphore.withPermit {
                    val scan = known[coarse.network]?.let { StormHistory.onClock(it, picked) } ?: picked
                    val path = StormHistory.path(coarse, scan) ?: return@withPermit emptyList<StormView.Cloud>()
                    val volume = feed.volume(path) ?: return@withPermit emptyList<StormView.Cloud>()
                    val quarters = withContext(Dispatchers.Default) { StormHistory.quarters(volume, coarse) }
                    fun entry(tile: StormHistory.Tile, file: String, grid: StormVolume, samples: List<StormVolume.Voxel>) = StormView.Cloud(
                        StormData.Storm(tile.code, grid.point, file, coarse.network + "-" + coarse.code,
                            grid.scannedAt ?: scan.toDouble(), grid.tier, samples.maxOfOrNull { it.dbz }),
                        samples, grid.point, grid.coreDbz,
                    )
                    val sampleBudget = (3_000 / quarters.sumOf { quarter ->
                        val asks = StormHistory.asks(quarter)
                        maxOf(asks.first.size, asks.second.size)
                    }.coerceAtLeast(1)).coerceAtLeast(1)
                    val fine = mutableListOf<StormView.Cloud>()
                    var missingQuarter = false
                    for (quarter in quarters) {
                        val (first, fallback) = StormHistory.asks(quarter)
                        if (first.isEmpty()) continue
                        suspend fun ask(tiles: List<StormHistory.Tile>): List<StormView.Cloud> {
                            return tiles.mapNotNull { tile ->
                                val file = StormHistory.path(tile, scan) ?: return@mapNotNull null
                                val grid = feed.volume(file) ?: return@mapNotNull null
                                val samples = withContext(Dispatchers.Default) { grid.points(sampleBudget) }
                                entry(tile, file, grid, samples)
                            }
                        }
                        val found = ask(first).ifEmpty { ask(fallback) }
                        if (found.isEmpty()) missingQuarter = true
                        fine += found
                    }
                    if (fine.isNotEmpty() && !missingQuarter) fine
                    else {
                        val samples = withContext(Dispatchers.Default) { volume.points(3_000) }
                        if (samples.isEmpty()) emptyList() else listOf(entry(coarse, path, volume, samples))
                    }
                }
            }
        }.awaitAll().flatten()
        Result(groups.take(8), groups.size)
    }
}
