package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A square country from 10° to 20° north and east. */
private val SQUARELAND = Country(
    name = "Squareland",
    isoA2 = null,
    continent = "Atlantis",
    outlines = listOf(
        CountryOutline(
            exterior = CountryRing(doubleArrayOf(10.0, 10.0, 20.0, 10.0, 20.0, 20.0, 10.0, 20.0, 10.0, 10.0)),
            holes = emptyList(),
        ),
    ),
)

class LandmarkTest {

    @Test
    fun countsAPlaceInsideTheCountryAsNearIt() {
        assertTrue(SQUARELAND.isNear(GeoPoint(latitude = 15.0, longitude = 15.0)))
    }

    @Test
    fun countsAnIslandJustOffTheCoastAsNearIt() {
        assertTrue(SQUARELAND.isNear(GeoPoint(latitude = 15.0, longitude = 21.5)))
    }

    @Test
    fun doesNotCountAPlaceOnAnotherContinentAsNearIt() {
        assertFalse(SQUARELAND.isNear(GeoPoint(latitude = -40.0, longitude = 120.0)))
    }

    @Test
    fun locatesAnArticleOnlyWhenItHasBothCoordinates() {
        val article = WikiArticle(title = "Uluru", extract = "A rock.", pageUrl = "https://en.wikipedia.org/wiki/Uluru")

        assertNull(article.location)
        assertNull(article.copy(latitude = -25.3).location)
        assertEquals(GeoPoint(-25.3, 131.0), article.copy(latitude = -25.3, longitude = 131.0).location)
    }
}
