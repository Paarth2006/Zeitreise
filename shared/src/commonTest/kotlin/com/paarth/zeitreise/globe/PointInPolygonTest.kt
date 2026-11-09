package com.paarth.zeitreise.globe

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A square from 0 to 10 degrees in both latitude and longitude, as packed longitude-latitude. */
private val SQUARE = doubleArrayOf(
    0.0,
    0.0,
    10.0,
    0.0,
    10.0,
    10.0,
    0.0,
    10.0,
)

/** An L shape, to show that the test is not just a bounding box check. */
private val L_SHAPE = doubleArrayOf(
    0.0, 0.0,
    10.0, 0.0,
    10.0, 4.0,
    4.0, 4.0,
    4.0, 10.0,
    0.0, 10.0,
)

class PointInPolygonTest {

    @Test
    fun findsAPointInTheMiddle() {
        assertTrue(isPointInRing(GeoPoint(latitude = 5.0, longitude = 5.0), SQUARE))
    }

    @Test
    fun rejectsPointsOutsideOnEverySide() {
        listOf(
            GeoPoint(latitude = 5.0, longitude = -1.0),
            GeoPoint(latitude = 5.0, longitude = 11.0),
            GeoPoint(latitude = -1.0, longitude = 5.0),
            GeoPoint(latitude = 11.0, longitude = 5.0),
        ).forEach { point ->
            assertFalse(isPointInRing(point, SQUARE), "$point should be outside")
        }
    }

    @Test
    fun treatsTheRingAsClosedWithoutARepeatedLastVertex() {
        assertTrue(isPointInRing(GeoPoint(latitude = 1.0, longitude = 1.0), SQUARE))
        assertTrue(isPointInRing(GeoPoint(latitude = 9.0, longitude = 9.0), SQUARE))
    }

    @Test
    fun handlesARingThatRepeatsItsFirstVertex() {
        val closed = SQUARE + doubleArrayOf(0.0, 0.0)
        assertTrue(isPointInRing(GeoPoint(latitude = 5.0, longitude = 5.0), closed))
        assertFalse(isPointInRing(GeoPoint(latitude = 15.0, longitude = 5.0), closed))
    }

    @Test
    fun excludesTheNotchOfAConcaveRing() {
        assertTrue(isPointInRing(GeoPoint(latitude = 2.0, longitude = 2.0), L_SHAPE))
        assertTrue(isPointInRing(GeoPoint(latitude = 2.0, longitude = 8.0), L_SHAPE))
        assertTrue(isPointInRing(GeoPoint(latitude = 8.0, longitude = 2.0), L_SHAPE))
        assertFalse(isPointInRing(GeoPoint(latitude = 8.0, longitude = 8.0), L_SHAPE))
    }

    @Test
    fun handlesNegativeCoordinates() {
        val southWest = doubleArrayOf(
            -20.0,
            -20.0,
            -10.0,
            -20.0,
            -10.0,
            -10.0,
            -20.0,
            -10.0,
        )
        assertTrue(isPointInRing(GeoPoint(latitude = -15.0, longitude = -15.0), southWest))
        assertFalse(isPointInRing(GeoPoint(latitude = 15.0, longitude = 15.0), southWest))
    }

    @Test
    fun treatsADegenerateRingAsEmpty() {
        assertFalse(isPointInRing(GeoPoint(latitude = 0.0, longitude = 0.0), doubleArrayOf()))
        assertFalse(isPointInRing(GeoPoint(latitude = 0.0, longitude = 0.0), doubleArrayOf(0.0, 0.0)))
        assertFalse(isPointInRing(GeoPoint(latitude = 0.0, longitude = 0.0), doubleArrayOf(0.0, 0.0, 1.0, 1.0)))
    }

    @Test
    fun refusesAnOddNumberOfValues() {
        assertFailsWith<IllegalArgumentException> {
            isPointInRing(GeoPoint(latitude = 0.0, longitude = 0.0), doubleArrayOf(1.0, 2.0, 3.0))
        }
    }
}
