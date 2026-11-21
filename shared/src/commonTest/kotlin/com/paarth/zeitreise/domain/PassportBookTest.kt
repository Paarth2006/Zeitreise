package com.paarth.zeitreise.domain

import com.paarth.zeitreise.geo.GeoJsonParser
import kotlin.test.Test
import kotlin.test.assertEquals

/** A square country on [continent], tied to the Wikidata item [wikidataId]. */
private fun bookCountry(name: String, wikidataId: String, continent: String, origin: Int): String = """
    { "type": "Feature",
      "properties": { "name": "$name", "isoA2": "XX", "continent": "$continent", "wikidataId": "$wikidataId" },
      "geometry": { "type": "Polygon", "coordinates": [[
        [$origin, 0], [${origin + 4}, 0], [${origin + 4}, 4], [$origin, 4], [$origin, 0]
      ]] } }
"""

private val WORLD = GeoJsonParser().parseCountries(
    """{ "type": "FeatureCollection", "features": [
        ${bookCountry("Westland", "Q1", "Atlantis", origin = 0)},
        ${bookCountry("Eastland", "Q2", "Atlantis", origin = 10)},
        ${bookCountry("Farland", "Q3", "Lemuria", origin = 20)}
    ]}""",
)

class PassportBookTest {

    @Test
    fun showsTheLatestStampFirst() {
        val book = passportBook(WORLD, stamps = listOf("Q3", "Q1"))

        assertEquals(listOf("Westland", "Farland"), book.stamped.map { it.name })
        assertEquals(3, book.total)
    }

    @Test
    fun countsTheStampsOnEachContinentBestCoveredFirst() {
        val book = passportBook(WORLD, stamps = listOf("Q3"))

        assertEquals(
            listOf(ContinentProgress("Lemuria", stamped = 1, total = 1), ContinentProgress("Atlantis", 0, 2)),
            book.continents,
        )
    }

    @Test
    fun leavesOutStampsForCountriesNoLongerKnown() {
        val book = passportBook(WORLD, stamps = listOf("Q1", "Q404"))

        assertEquals(listOf("Westland"), book.stamped.map { it.name })
    }

    @Test
    fun filesAStampUnderTheWikidataItemOrElseTheName() {
        assertEquals("Q1", WORLD.first { it.name == "Westland" }.passportKey)
    }

    @Test
    fun carriesTheHolderAndLooksUpTheirHomeCountry() {
        val book = passportBook(WORLD, stamps = emptyList(), holder = "Ada", home = "Q2")

        assertEquals("Ada", book.holder)
        assertEquals("Eastland", book.home?.name)
    }

    @Test
    fun hasNoHomeWhenTheHomeCountryIsNoLongerKnown() {
        assertEquals(null, passportBook(WORLD, stamps = emptyList(), home = "Q404").home)
    }
}
