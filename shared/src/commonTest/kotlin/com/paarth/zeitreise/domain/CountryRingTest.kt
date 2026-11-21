package com.paarth.zeitreise.domain

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tolerance for the bounding circle, since the repeated closing vertex shifts the average slightly. */
private const val TOLERANCE = 0.2

/** A closed circle of [vertices] points, [radius] degrees across, around longitude and latitude zero. */
private fun circle(radius: Double, vertices: Int): DoubleArray {
    val points = (0..vertices).flatMap { step ->
        val angle = 2.0 * kotlin.math.PI * step / vertices
        listOf(radius * cos(angle), radius * sin(angle))
    }
    return points.toDoubleArray()
}

class CountryRingTest {

    @Test
    fun drawsADetailedRingFromFarFewerVerticesWhenCoarse() {
        val ring = CountryRing(circle(radius = 10.0, vertices = 720))

        assertTrue(ring.coarseSphere.size < ring.sphere.size / 4, "coarse ${ring.coarseSphere.size}")
    }

    @Test
    fun keepsATinyRingRecognisableRatherThanCollapsingIt() {
        val ring = CountryRing(circle(radius = 0.005, vertices = 12))

        // The normal coarse tolerance is bigger than this whole ring.
        assertTrue(ring.coarseSphere.size / 3 >= 8, "coarse vertices ${ring.coarseSphere.size / 3}")
    }

    @Test
    fun boundsTheRingWithACircleAsWideAsTheRing() {
        val ring = CountryRing(circle(radius = 10.0, vertices = 72))

        assertEquals(10.0, ring.bounds.radiusDegrees, TOLERANCE)
        assertEquals(0.0, ring.bounds.center.latitude, TOLERANCE)
        assertEquals(0.0, ring.bounds.center.longitude, TOLERANCE)
    }
}
