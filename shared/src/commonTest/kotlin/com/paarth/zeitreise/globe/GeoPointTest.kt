package com.paarth.zeitreise.globe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val TOLERANCE = 1e-9

class GeoPointTest {

    @Test
    fun rejectsLatitudeBeyondThePoles() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(latitude = 90.1, longitude = 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(latitude = -90.1, longitude = 0.0) }
    }

    @Test
    fun rejectsLongitudeBeyondTheAntimeridian() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(latitude = 0.0, longitude = 180.1) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(latitude = 0.0, longitude = -180.1) }
    }

    @Test
    fun acceptsThePolesAndTheAntimeridian() {
        GeoPoint(latitude = 90.0, longitude = 180.0)
        GeoPoint(latitude = -90.0, longitude = -180.0)
    }

    @Test
    fun normalizesLongitudePastTheAntimeridian() {
        assertEquals(-170.0, normalizeLongitude(190.0), TOLERANCE)
        assertEquals(170.0, normalizeLongitude(-190.0), TOLERANCE)
        assertEquals(0.0, normalizeLongitude(360.0), TOLERANCE)
        assertEquals(10.0, normalizeLongitude(730.0), TOLERANCE)
    }

    @Test
    fun keepsLongitudeAlreadyInRange() {
        assertEquals(0.0, normalizeLongitude(0.0), TOLERANCE)
        assertEquals(-45.5, normalizeLongitude(-45.5), TOLERANCE)
        assertEquals(180.0, normalizeLongitude(180.0), TOLERANCE)
    }

    @Test
    fun placesTheOriginFacingTheViewer() {
        val sphere = GeoPoint(latitude = 0.0, longitude = 0.0).toSpherePoint()
        assertEquals(0.0, sphere.x, TOLERANCE)
        assertEquals(0.0, sphere.y, TOLERANCE)
        assertEquals(1.0, sphere.z, TOLERANCE)
    }

    @Test
    fun placesTheNorthPoleOnTheUpAxis() {
        val sphere = GeoPoint(latitude = 90.0, longitude = 0.0).toSpherePoint()
        assertEquals(0.0, sphere.x, TOLERANCE)
        assertEquals(1.0, sphere.y, TOLERANCE)
        assertEquals(0.0, sphere.z, TOLERANCE)
    }

    @Test
    fun placesTheEasternQuarterTurnOnThePositiveEastAxis() {
        val sphere = GeoPoint(latitude = 0.0, longitude = 90.0).toSpherePoint()
        assertEquals(1.0, sphere.x, TOLERANCE)
        assertEquals(0.0, sphere.y, TOLERANCE)
        assertEquals(0.0, sphere.z, TOLERANCE)
    }

    @Test
    fun keepsEveryPointOnTheUnitSphere() {
        val points = listOf(
            GeoPoint(latitude = 0.0, longitude = 0.0),
            GeoPoint(latitude = 51.5, longitude = -0.12),
            GeoPoint(latitude = -33.86, longitude = 151.21),
            GeoPoint(latitude = -90.0, longitude = 180.0),
        )
        points.forEach { point ->
            val sphere = point.toSpherePoint()
            val length = sphere.x * sphere.x + sphere.y * sphere.y + sphere.z * sphere.z
            assertTrue(kotlin.math.abs(length - 1.0) < TOLERANCE, "$point has length $length")
        }
    }
}
