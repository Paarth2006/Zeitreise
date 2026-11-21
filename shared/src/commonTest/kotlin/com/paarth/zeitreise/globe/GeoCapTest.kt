package com.paarth.zeitreise.globe

import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val TOLERANCE = 1e-6

/** Packs geographic points into the unit-sphere triples a [boundingCap] reads. */
private fun sphereOf(vararg points: GeoPoint): DoubleArray = points
    .flatMap { point -> point.toSpherePoint().let { listOf(it.x, it.y, it.z) } }
    .toDoubleArray()

class GeoCapTest {

    @Test
    fun centresASymmetricSquareOnItsMiddle() {
        val cap = boundingCap(
            sphereOf(
                GeoPoint(latitude = -5.0, longitude = 15.0),
                GeoPoint(latitude = -5.0, longitude = 25.0),
                GeoPoint(latitude = 5.0, longitude = 25.0),
                GeoPoint(latitude = 5.0, longitude = 15.0),
            ),
        )

        assertEquals(0.0, cap.center.latitude, TOLERANCE)
        assertEquals(20.0, cap.center.longitude, TOLERANCE)
    }

    @Test
    fun reachesExactlyTheFarthestVertex() {
        val cap = boundingCap(sphereOf(GeoPoint(0.0, -10.0), GeoPoint(0.0, 10.0)))

        assertEquals(10.0, cap.radiusDegrees, TOLERANCE)
    }

    @Test
    fun centresARegionAcrossTheAntimeridianOnTheAntimeridian() {
        val cap = boundingCap(sphereOf(GeoPoint(0.0, 170.0), GeoPoint(0.0, -170.0)))

        assertEquals(180.0, kotlin.math.abs(cap.center.longitude), TOLERANCE)
        assertEquals(10.0, cap.radiusDegrees, TOLERANCE)
    }

    @Test
    fun fallsBackToTheFirstVertexWhenTheVerticesCancelOut() {
        val cap = boundingCap(sphereOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0)))

        assertEquals(0.0, cap.center.longitude, TOLERANCE)
        assertEquals(180.0, cap.radiusDegrees, TOLERANCE)
    }

    @Test
    fun rejectsAnEmptyRing() {
        assertFailsWith<IllegalArgumentException> { boundingCap(DoubleArray(0)) }
    }

    @Test
    fun framesARegionSoItsEdgeReachesTheRequestedFill() {
        val cap = GeoCap(center = GeoPoint(0.0, 0.0), radiusDegrees = 30.0)

        assertEquals(0.9 / sin(30.0.toRadians()), cap.framingZoom(fill = 0.9), TOLERANCE)
    }

    @Test
    fun framesAHemisphereOrMoreAtTheWidestZoom() {
        assertEquals(MIN_ZOOM, GeoCap(GeoPoint(0.0, 0.0), radiusDegrees = 120.0).framingZoom(fill = 0.9), TOLERANCE)
    }

    @Test
    fun clampsATinyRegionToTheClosestZoom() {
        assertEquals(MAX_ZOOM, GeoCap(GeoPoint(0.0, 0.0), radiusDegrees = 0.00001).framingZoom(fill = 0.9), TOLERANCE)
    }

    @Test
    fun joinsTwoCapsIntoOneSpanningBoth() {
        val west = GeoCap(GeoPoint(latitude = 0.0, longitude = 0.0), radiusDegrees = 5.0)
        val east = GeoCap(GeoPoint(latitude = 0.0, longitude = 30.0), radiusDegrees = 5.0)

        val both = west union east

        assertEquals(0.0, both.center.latitude, TOLERANCE)
        assertEquals(15.0, both.center.longitude, TOLERANCE)
        assertEquals(20.0, both.radiusDegrees, TOLERANCE)
    }

    @Test
    fun keepsTheLargerCapWhenItHoldsTheOther() {
        val large = GeoCap(GeoPoint(latitude = 10.0, longitude = 10.0), radiusDegrees = 30.0)
        val inside = GeoCap(GeoPoint(latitude = 12.0, longitude = 14.0), radiusDegrees = 2.0)

        assertEquals(large, large union inside)
        assertEquals(large, inside union large)
    }
}
