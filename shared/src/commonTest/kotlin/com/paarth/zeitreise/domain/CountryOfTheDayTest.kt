package com.paarth.zeitreise.domain

import com.paarth.zeitreise.geo.GeoJsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A row of square countries along the equator; every one but the last has a capital. */
private val CANDIDATES: List<Country> = GeoJsonParser().parseCountries(
    (0 until 12).joinToString(prefix = """{ "type": "FeatureCollection", "features": [""", postfix = "]}") {
        """
            { "type": "Feature",
              "properties": { "name": "Land $it", "isoA2": "L$it", "continent": "Atlantis", "wikidataId": "Q$it" },
              "geometry": { "type": "Polygon", "coordinates": [[
                [${it * 10}, 0], [${it * 10 + 4}, 0], [${it * 10 + 4}, 4], [${it * 10}, 4], [${it * 10}, 0]
              ]] } }
        """.trimIndent()
    },
).map { country ->
    if (country.name == "Land 11") country else country.withFacts(CountryFacts(capitals = listOf("Capital")))
}

class CountryOfTheDayTest {

    @Test
    fun picksTheSameCountryAllDayWhateverOrderTheCountriesCameIn() {
        val today = countryOfTheDay(CANDIDATES, day = 20_000)

        assertSame(today, countryOfTheDay(CANDIDATES.reversed(), day = 20_000))
    }

    @Test
    fun picksAnotherCountryOnOtherDays() {
        val week = (20_000L until 20_007L).map { countryOfTheDay(CANDIDATES, it) }.toSet()

        assertNotEquals(1, week.size)
    }

    @Test
    fun neverPicksALandWithNoCapital() {
        val picks = (0L until 200L).mapNotNull { countryOfTheDay(CANDIDATES, it)?.name }

        assertEquals(false, "Land 11" in picks)
    }

    @Test
    fun hasNoCountryOfTheDayWhenNoneHasACapital() {
        assertNull(countryOfTheDay(CANDIDATES.filter { it.facts.capitals.isEmpty() }, day = 1))
    }
}
