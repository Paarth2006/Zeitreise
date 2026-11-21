package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.geo.GeoJsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import zeitreise.shared.generated.resources.Res

/** Path of the bundled country outlines. */
private const val COUNTRIES_RESOURCE = "files/countries.json"

/** Path of the bundled capitals and flags. */
private const val FACTS_RESOURCE = "files/country_facts.json"

/** Loads the countries shown on the globe. */
public interface CountryRepository {
    /**
     * Reads every country, largest first.
     *
     * @throws IllegalStateException if the bundled outlines cannot be read or parsed.
     */
    public suspend fun loadCountries(): List<Country>
}

/**
 * Reads the bundled Natural Earth outlines along with each country's capitals and flag. Everything
 * is bundled, so the globe works offline.
 *
 * @param dispatcher where the parsing runs, off the main thread.
 */
internal class BundledCountryRepository(
    private val dispatcher: CoroutineDispatcher,
    private val parser: GeoJsonParser = GeoJsonParser(),
) : CountryRepository {

    private var cached: List<Country>? = null

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun loadCountries(): List<Country> = withContext(dispatcher) {
        cached ?: runCatching {
            parser.parseCountries(Res.readBytes(COUNTRIES_RESOURCE).decodeToString())
                .withFacts(Res.readBytes(FACTS_RESOURCE).decodeToString())
        }
            .onSuccess { cached = it }
            .getOrElse { cause -> error("Could not read $COUNTRIES_RESOURCE: ${cause.message}") }
    }
}
