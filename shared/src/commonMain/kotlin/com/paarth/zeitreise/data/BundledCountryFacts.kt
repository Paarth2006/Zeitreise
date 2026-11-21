package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.CountryFacts
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val FACTS_JSON = Json { ignoreUnknownKeys = true }

/**
 * A country's capitals and flag, as the data preparation tool bundles them from Wikidata.
 *
 * @property wikidataId the country's Wikidata item, which ties the facts to its outline.
 * @property capitals the country's capitals today.
 * @property flag where a picture of its flag can be downloaded from Wikimedia Commons.
 */
@Serializable
internal data class BundledCountryFacts(
    val wikidataId: String,
    val capitals: List<String> = emptyList(),
    val flag: String? = null,
)

/**
 * These countries with the capitals and flags read from [source], matched by Wikidata item;
 * a country the facts don't mention is kept as it is.
 *
 * @throws kotlinx.serialization.SerializationException if [source] is not a list of facts.
 */
internal fun List<Country>.withFacts(source: String): List<Country> {
    val facts = FACTS_JSON.decodeFromString<List<BundledCountryFacts>>(source).associateBy { it.wikidataId }
    return map { country ->
        country.wikidataId?.let(facts::get)?.let { country.withFacts(CountryFacts(it.capitals, it.flag)) } ?: country
    }
}
