package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.geo.GeoJsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import zeitreise.shared.generated.resources.Res

/** Loads the states and provinces of a country. */
public interface SubdivisionRepository {
    /**
     * Reads the states and provinces of [country], largest first, or an empty list for a country
     * that has none on record.
     */
    public suspend fun loadSubdivisions(country: Country): List<Subdivision>
}

/**
 * Reads the per-country state files that the data preparation tool committed into the compose
 * resources, one file per ISO 3166-1 alpha-2 code, so only the opened country is parsed.
 *
 * @param dispatcher dispatcher the parsing runs on, keeping the main thread free.
 */
internal class BundledSubdivisionRepository(
    private val dispatcher: CoroutineDispatcher,
    private val parser: GeoJsonParser = GeoJsonParser(),
) : SubdivisionRepository {

    private val cache = mutableMapOf<String, List<Subdivision>>()

    // Guards the cache, since the dispatcher may run two loads on different threads.
    private val cacheLock = Mutex()

    override suspend fun loadSubdivisions(country: Country): List<Subdivision> {
        val code = country.isoA2 ?: return emptyList()
        return withContext(dispatcher) {
            cacheLock.withLock { cache.getOrPut(code) { readSubdivisions(code) } }
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun readSubdivisions(code: String): List<Subdivision> {
        // A territory without a file, such as a disputed one, simply has no states to show.
        val bytes = try {
            Res.readBytes("files/states/$code.json")
        } catch (_: MissingResourceException) {
            return emptyList()
        }
        return parser.parseSubdivisions(bytes.decodeToString())
    }
}
