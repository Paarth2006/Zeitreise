package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.domain.SubdivisionName
import com.paarth.zeitreise.geo.GeoJsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import zeitreise.shared.generated.resources.Res

/** Path of the bundled state index. */
private const val INDEX_PATH = "files/states/index.json"

/** Loads the states and provinces of a country. */
public interface SubdivisionRepository {
    /** Reads the states and provinces of [country], largest first, or an empty list if it has none. */
    public suspend fun loadSubdivisions(country: Country): List<Subdivision>

    /** Reads every state's name and country for search, without loading outlines. */
    public suspend fun loadNames(): List<SubdivisionName>
}

/** One entry of the bundled state index. */
@Serializable
private data class SubdivisionIndexEntry(val name: String, val country: String)

/**
 * Reads the bundled state files. There is one file per country (by ISO code), so only the opened
 * country is parsed.
 *
 * @param dispatcher where the parsing runs, off the main thread.
 */
internal class BundledSubdivisionRepository(
    private val dispatcher: CoroutineDispatcher,
    private val parser: GeoJsonParser = GeoJsonParser(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SubdivisionRepository {

    private val cache = mutableMapOf<String, List<Subdivision>>()
    private var names: List<SubdivisionName>? = null

    // Protects the cache, since two loads can run on different threads.
    private val cacheLock = Mutex()

    override suspend fun loadSubdivisions(country: Country): List<Subdivision> {
        val code = country.isoA2 ?: return emptyList()
        return withContext(dispatcher) {
            cacheLock.withLock { cache.getOrPut(code) { readSubdivisions(code) } }
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun loadNames(): List<SubdivisionName> = withContext(dispatcher) {
        cacheLock.withLock {
            names ?: json.decodeFromString(
                ListSerializer(SubdivisionIndexEntry.serializer()),
                Res.readBytes(INDEX_PATH).decodeToString(),
            ).map { SubdivisionName(name = it.name, countryCode = it.country) }.also { names = it }
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun readSubdivisions(code: String): List<Subdivision> {
        // Some territories (e.g. disputed ones) have no file, so they have no states.
        val bytes = try {
            Res.readBytes("files/states/$code.json")
        } catch (_: MissingResourceException) {
            return emptyList()
        }
        return parser.parseSubdivisions(bytes.decodeToString())
    }
}
