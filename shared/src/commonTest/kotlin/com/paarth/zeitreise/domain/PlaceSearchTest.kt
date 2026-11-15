package com.paarth.zeitreise.domain

import com.paarth.zeitreise.geo.GeoJsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A minimal country feature with a square outline. */
private fun countryFeature(name: String, isoA2: String, origin: Int) = """
    {
      "type": "Feature",
      "properties": { "name": "$name", "isoA2": "$isoA2", "isoA3": null, "continent": "Atlantis" },
      "geometry": { "type": "Polygon", "coordinates": [[
        [$origin, $origin], [${origin + 5}, $origin], [${origin + 5}, ${origin + 5}], [$origin, ${origin + 5}],
        [$origin, $origin]
      ]] }
    }
""".trimIndent()

class PlaceSearchTest {

    private val countries = GeoJsonParser().parseCountries(
        """
        { "type": "FeatureCollection", "features": [
          ${countryFeature("India", "IN", 0)},
          ${countryFeature("Indonesia", "ID", 10)},
          ${countryFeature("Argentina", "AR", 20)},
          ${countryFeature("Germany", "DE", 30)}
        ] }
        """.trimIndent(),
    )

    private val states = listOf(
        SubdivisionName("Kerala", "IN"),
        SubdivisionName("Entre Ríos", "AR"),
        SubdivisionName("North Rhine-Westphalia", "DE"),
        SubdivisionName("Indiana", "US"),
    )

    private fun names(query: String, limit: Int = MAX_SEARCH_RESULTS) =
        searchPlaces(query, countries, states, limit).map { it.name }

    @Test
    fun findsNothingForABlankQuery() {
        assertTrue(names("   ").isEmpty())
    }

    @Test
    fun ranksAnExactNameAboveNamesThatOnlyStartWithTheQuery() {
        val states = listOf(SubdivisionName("Indiaville", "IN"))

        assertEquals(listOf("India", "Indiaville"), searchPlaces("india", countries, states).map { it.name })
    }

    @Test
    fun findsEveryNameStartingWithTheQuery() {
        assertEquals(setOf("India", "Indonesia"), names("ind").toSet())
    }

    @Test
    fun findsAStateAndKnowsItsCountry() {
        val result = searchPlaces("kerala", countries, states).single()

        assertTrue(result is SearchResult.SubdivisionMatch)
        assertEquals("India", result.country.name)
    }

    @Test
    fun ignoresCaseAndAccents() {
        assertEquals(listOf("Entre Ríos"), names("ENTRE RIOS"))
    }

    @Test
    fun matchesTheStartOfAnyWord() {
        assertEquals(listOf("North Rhine-Westphalia"), names("westph"))
    }

    @Test
    fun skipsStatesWhoseCountryIsNotOnTheGlobe() {
        assertTrue("Indiana" !in names("indiana"))
    }

    @Test
    fun ranksACountryAboveAStateOfTheSameQuality() {
        val all = names("in")

        assertTrue(all.indexOf("India") < all.indexOf("Kerala").let { if (it < 0) Int.MAX_VALUE else it })
    }

    @Test
    fun stopsAtTheLimit() {
        assertEquals(1, names("a", limit = 1).size)
    }
}
