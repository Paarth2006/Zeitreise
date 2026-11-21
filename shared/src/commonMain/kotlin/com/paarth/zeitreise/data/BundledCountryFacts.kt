package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.CountryFacts
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val FACTS_JSON = Json { ignoreUnknownKeys = true }

/**
 * A country's capitals and flag, as written by the data tool from Wikidata.
 *
 * @property wikidataId the country's Wikidata item, used to match it to its outline.
 * @property capitals its current capitals.
 * @property flag address of its flag image on Wikimedia Commons.
 */
@Serializable
internal data class BundledCountryFacts(
    val wikidataId: String,
    val capitals: List<String> = emptyList(),
    val flag: String? = null,
)

/**
 * Adds the capitals and flags from [source] to these countries, matched by Wikidata item.
 * Countries without an entry are returned unchanged.
 *
 * @throws kotlinx.serialization.SerializationException if [source] is not a list of facts.
 */
internal fun List<Country>.withFacts(source: String): List<Country> {
    val facts = FACTS_JSON.decodeFromString<List<BundledCountryFacts>>(source).associateBy { it.wikidataId }
    return map { country ->
        country.wikidataId?.let(facts::get)?.let { country.withFacts(CountryFacts(it.capitals, it.flag)) } ?: country
    }
}
