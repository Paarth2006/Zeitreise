package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.HistoricLandmark
import com.paarth.zeitreise.globe.GeoPoint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Knows which landmarks existed in a given year. */
public interface LandmarkHistory {
    /** The landmarks that existed in [year], oldest first. */
    public suspend fun standingIn(year: Int): List<HistoricLandmark>
}

/**
 * Reads the bundled landmarks, with the years they were built and destroyed (from Wikidata).
 *
 * @param readLandmarks reads `history/landmarks.json`.
 */
internal class BundledLandmarkHistory(
    private val readLandmarks: suspend () -> String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LandmarkHistory {
    private val lock = Mutex()
    private var all: List<HistoricLandmark>? = null

    override suspend fun standingIn(year: Int): List<HistoricLandmark> = load().filter { it.standsIn(year) }

    private suspend fun load(): List<HistoricLandmark> = lock.withLock {
        all ?: json.decodeFromString(ListSerializer(BundledLandmark.serializer()), readLandmarks())
            .map { it.toHistoricLandmark() }
            .also { all = it }
    }
}

/** One entry of `history/landmarks.json`. */
@Serializable
private data class BundledLandmark(
    val name: String,
    val wikipediaTitle: String,
    val latitude: Double,
    val longitude: Double,
    val built: Int,
    val until: Int? = null,
) {
    fun toHistoricLandmark() = HistoricLandmark(
        name = name,
        wikipediaTitle = wikipediaTitle,
        location = GeoPoint(latitude, longitude),
        built = built,
        until = until,
    )
}
