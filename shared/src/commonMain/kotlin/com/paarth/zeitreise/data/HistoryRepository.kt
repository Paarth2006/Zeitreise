package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.geo.GeoJsonParser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The historical maps come from the historical-basemaps project, pinned to one commit so they don't
 * change. They are GPL-3.0, so they're downloaded at runtime instead of bundled, which keeps this
 * app MIT licensed.
 */
private const val MAPS_COMMIT = "4b5039e50d8d2464b065bd76ad6086cd4597108d"
private const val MAPS_PROJECT = "https://raw.githubusercontent.com/aourednik/historical-basemaps"
private const val MAPS_BASE_URL = "$MAPS_PROJECT/$MAPS_COMMIT/geojson/"

/** How many maps to keep in memory. */
private const val MAPS_KEPT = 4

/** Loads the world map for a given year. */
public interface HistoryRepository {
    /**
     * The world map for [year], which must be one of [com.paarth.zeitreise.domain.MAP_YEARS].
     *
     * @throws Exception if the map can't be downloaded or read.
     */
    public suspend fun mapOf(year: Int): HistoricalMap
}

/**
 * Downloads each year's map when it's first needed and keeps the last few in memory, so going back
 * to a recent year is instant.
 *
 * @param dispatcher where downloading and parsing run, off the main thread.
 */
internal class KtorHistoryRepository(
    private val client: HttpClient,
    private val dispatcher: CoroutineDispatcher,
    private val parser: GeoJsonParser = GeoJsonParser(),
) : HistoryRepository {
    private val lock = Mutex()
    private val kept = LinkedHashMap<Int, HistoricalMap>()

    override suspend fun mapOf(year: Int): HistoricalMap = withContext(dispatcher) {
        lock.withLock { kept[year] }?.let { return@withContext it }

        val response = reaching { client.get(MAPS_BASE_URL + mapFileName(year)) }
        check(response.status.isSuccess()) { "The map of $year answered ${response.status.value}" }
        val map = parser.parseHistoricalMap(year, response.bodyAsText())
        lock.withLock {
            kept[year] = map
            while (kept.size > MAPS_KEPT) kept.remove(kept.keys.first())
        }
        map
    }
}

/** The file name for [year], like `world_1492.geojson` or `world_bc500.geojson` for 500 BC. */
internal fun mapFileName(year: Int): String = if (year < 0) "world_bc${-year}.geojson" else "world_$year.geojson"
