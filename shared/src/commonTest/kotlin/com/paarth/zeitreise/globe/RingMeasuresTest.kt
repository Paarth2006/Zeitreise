package com.paarth.zeitreise.globe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TOLERANCE = 1e-6

/** A closed square [size] degrees across, its south-west corner at [west], [south]. */
private fun square(west: Double, south: Double, size: Double) = doubleArrayOf(
    west, south,
    west + size, south,
    west + size, south + size,
    west, south + size,
    west, south,
)

class RingMeasuresTest {

    @Test
    fun measuresASquareAtTheEquator() {
        assertEquals(1.0, square(west = 0.0, south = -0.5, size = 1.0).ringArea(), 1e-4)
    }

    @Test
    fun shrinksTheSameSquareNearAPole() {
        assertTrue(square(west = 0.0, south = 59.5, size = 1.0).ringArea() < 0.6)
    }

    @Test
    fun measuresTheSameAreaWhicheverWayTheRingRuns() {
        val clockwise = square(west = 0.0, south = 0.0, size = 2.0).toList().chunked(2).reversed().flatten()

        assertEquals(square(0.0, 0.0, 2.0).ringArea(), clockwise.toDoubleArray().ringArea(), TOLERANCE)
    }

    @Test
    fun givesNoAreaToALine() {
        assertEquals(0.0, doubleArrayOf(0.0, 0.0, 1.0, 1.0).ringArea(), TOLERANCE)
    }

    @Test
    fun measuresTheWiderOfTheTwoSpans() {
        assertEquals(3.0, doubleArrayOf(0.0, 0.0, 3.0, 1.0, 0.0, 1.0).ringExtent(), TOLERANCE)
    }
}
