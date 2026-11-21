package com.paarth.zeitreise.data

import com.paarth.zeitreise.geo.GeoJsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A square country tied to the Wikidata item [wikidataId], or to none. */
private fun factsCountry(name: String, wikidataId: String?, origin: Int): String {
    val id = wikidataId?.let { "\"$it\"" } ?: "null"
    val far = origin + 4
    return """
        {
          "type": "Feature",
          "properties": { "name": "$name", "isoA2": "XX", "continent": "Atlantis", "wikidataId": $id },
          "geometry": { "type": "Polygon", "coordinates": [[
            [$origin, $origin], [$far, $origin], [$far, $far], [$origin, $far], [$origin, $origin]
          ]] }
        }
    """.trimIndent()
}

private val FACT_COUNTRIES = GeoJsonParser().parseCountries(
    """{ "type": "FeatureCollection", "features": [
        ${factsCountry("Westland", "Q1", origin = 0)},
        ${factsCountry("Eastland", "Q2", origin = 10)},
        ${factsCountry("Nowhere", wikidataId = null, origin = 20)}
    ]}""",
)

private const val FACTS = """
    [
      {"wikidataId": "Q1", "capitals": ["West City"], "flag": "https://example.org/west.png"},
      {"wikidataId": "Q2", "capitals": ["Old Port", "New Port"]},
      {"wikidataId": "Q99", "capitals": ["Elsewhere"]}
    ]
"""

class BundledCountryFactsTest {

    private val countries = FACT_COUNTRIES.withFacts(FACTS).associateBy { it.name }

    @Test
    fun givesEachCountryItsCapitalsAndFlag() {
        val westland = countries.getValue("Westland")

        assertEquals(listOf("West City"), westland.facts.capitals)
        assertEquals("https://example.org/west.png", westland.facts.flagUrl)
    }

    @Test
    fun keepsEveryCapitalOfACountryWithSeveral() {
        val eastland = countries.getValue("Eastland")

        assertEquals(listOf("Old Port", "New Port"), eastland.facts.capitals)
        assertNull(eastland.facts.flagUrl)
    }

    @Test
    fun leavesACountryWithNoFactsAsItIs() {
        val nowhere = countries.getValue("Nowhere")

        assertEquals(emptyList(), nowhere.facts.capitals)
        assertNull(nowhere.facts.flagUrl)
    }
}
