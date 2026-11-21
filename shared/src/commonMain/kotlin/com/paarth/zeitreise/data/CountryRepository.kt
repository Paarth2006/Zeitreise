package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.geo.GeoJsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import zeitreise.shared.generated.resources.Res

/** Path of the bundled country outlines inside the compose resources. */
private const val COUNTRIES_RESOURCE = "files/countries.json"

/** Path of the bundled capitals and flags, kept apart from the outlines they belong to. */
private const val FACTS_RESOURCE = "files/country_facts.json"

/** Loads the country outlines the globe is drawn from. */
public interface CountryRepository {
    /**
     * Reads every country the app knows about, largest first.
     *
     * @throws IllegalStateException if the bundled outlines cannot be read or parsed.
     */
    public suspend fun loadCountries(): List<Country>
}

/**
 * Reads the Natural Earth outlines that the data preparation tool committed into the compose
 * resources, with each country's capitals and flag, so the globe works offline and needs no
 * network on first run.
 *
 * @param dispatcher dispatcher the parsing runs on, keeping the main thread free.
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
