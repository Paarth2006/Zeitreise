package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.geo.GeoJsonParser
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Two square countries, one tied to a Wikidata item and one known only by its name. */
private val STAMPED: List<Country> = GeoJsonParser().parseCountries(
    """{ "type": "FeatureCollection", "features": [
        { "type": "Feature",
          "properties": { "name": "Westland", "isoA2": "WL", "continent": "Atlantis", "wikidataId": "Q1" },
          "geometry": { "type": "Polygon", "coordinates": [[[0, 0], [4, 0], [4, 4], [0, 4], [0, 0]]] } },
        { "type": "Feature",
          "properties": { "name": "Eastland", "isoA2": "EL", "continent": "Atlantis" },
          "geometry": { "type": "Polygon", "coordinates": [[[10, 0], [14, 0], [14, 4], [10, 4], [10, 0]]] } }
    ]}""",
).sortedBy { it.name }

class SettingsPassportTest {

    private val eastland = STAMPED[0]
    private val westland = STAMPED[1]
    private val settings = MapSettings()

    private fun TestScope.passport() = SettingsPassport(settings, StandardTestDispatcher(testScheduler))

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptyList(), passport().stamps())
    }

    @Test
    fun stampsEachCountryOnceInTheOrderItWasFirstOpened() = runTest {
        val passport = passport()

        assertTrue(passport.stamp(westland))
        assertTrue(passport.stamp(eastland))
        assertFalse(passport.stamp(westland))

        assertEquals(listOf("Q1", "Eastland"), passport.stamps())
    }

    @Test
    fun keepsTheStampsAcrossPassportsSharingTheSameSettings() = runTest {
        passport().stamp(westland)

        assertEquals(listOf("Q1"), passport().stamps())
    }

    @Test
    fun hasNoHomeUntilTheUserGivesOne() = runTest {
        assertEquals(null, passport().home())
    }

    @Test
    fun remembersTheHomeCountryAndStampsIt() = runTest {
        passport().setHome(westland)

        assertEquals("Q1", passport().home())
        assertEquals(listOf("Q1"), passport().stamps())
    }

    @Test
    fun aHomeCountryOpenedBeforeIsNotStampedTwice() = runTest {
        passport().stamp(eastland)
        passport().stamp(westland)

        passport().setHome(westland)

        assertEquals(listOf("Eastland", "Q1"), passport().stamps())
    }

    @Test
    fun hasNoHolderUntilTheUserWritesTheirName() = runTest {
        assertEquals(null, passport().holder())
    }

    @Test
    fun remembersTheHolderTrimmedAndForgetsABlankName() = runTest {
        passport().setHolder("  Ada Lovelace ")
        assertEquals("Ada Lovelace", passport().holder())

        passport().setHolder("   ")
        assertEquals(null, passport().holder())
    }
}
