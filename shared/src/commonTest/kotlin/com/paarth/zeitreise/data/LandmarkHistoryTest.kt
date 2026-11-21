package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.HistoricLandmark
import com.paarth.zeitreise.globe.GeoPoint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val BUNDLED = """
    [
      {"name": "Great Pyramid of Giza", "wikipediaTitle": "Great Pyramid of Giza", "latitude": 29.979,
       "longitude": 31.134, "built": -2560},
      {"name": "Lighthouse of Alexandria", "wikipediaTitle": "Lighthouse of Alexandria", "latitude": 31.214,
       "longitude": 29.885, "built": -279, "until": 1480},
      {"name": "Eiffel Tower", "wikipediaTitle": "Eiffel Tower", "latitude": 48.858, "longitude": 2.295,
       "built": 1887}
    ]
"""

class LandmarkHistoryTest {

    private var reads = 0
    private val history = BundledLandmarkHistory(
        readLandmarks = {
            reads++
            BUNDLED
        },
    )

    @Test
    fun pinsOnlyTheLandmarksThatStoodThatYear() = runTest {
        val in1000 = history.standingIn(1000).map { it.name }
        val in1900 = history.standingIn(1900).map { it.name }

        assertEquals(listOf("Great Pyramid of Giza", "Lighthouse of Alexandria"), in1000)
        assertEquals(listOf("Great Pyramid of Giza", "Eiffel Tower"), in1900)
    }

    @Test
    fun readsTheBundledLandmarksOnce() = runTest {
        history.standingIn(1000)
        history.standingIn(1900)

        assertEquals(1, reads)
    }

    @Test
    fun placesALandmarkWhereItStands() = runTest {
        assertEquals(GeoPoint(48.858, 2.295), history.standingIn(2000).last().location)
    }

    @Test
    fun countsALandmarkAsStandingFromTheYearItWasBuiltUntilTheYearItFell() {
        val lighthouse = HistoricLandmark("Lighthouse", "Lighthouse", GeoPoint(31.2, 29.9), built = -279, until = 1480)

        assertFalse(lighthouse.standsIn(-280))
        assertTrue(lighthouse.standsIn(-279))
        assertTrue(lighthouse.standsIn(1479))
        assertFalse(lighthouse.standsIn(1480))
    }
}
