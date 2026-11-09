package com.paarth.zeitreise.globe

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PolygonSimplifierTest {

    @Test
    fun dropsVerticesThatSitOnAStraightLine() {
        val line = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 2.0, 0.0, 3.0, 0.0)
        assertContentEquals(doubleArrayOf(0.0, 0.0, 3.0, 0.0), simplifyRing(line, tolerance = 0.1))
    }

    @Test
    fun keepsAVertexFurtherAwayThanTheTolerance() {
        val bend = doubleArrayOf(0.0, 0.0, 1.0, 1.0, 2.0, 0.0)
        assertContentEquals(bend, simplifyRing(bend, tolerance = 0.5))
    }

    @Test
    fun dropsAVertexCloserThanTheTolerance() {
        val almostStraight = doubleArrayOf(0.0, 0.0, 1.0, 0.01, 2.0, 0.0)
        assertContentEquals(doubleArrayOf(0.0, 0.0, 2.0, 0.0), simplifyRing(almostStraight, tolerance = 0.5))
    }

    @Test
    fun alwaysKeepsTheFirstAndLastVertex() {
        val noisy = doubleArrayOf(5.0, 5.0, 5.1, 5.05, 5.2, 5.0, 5.3, 5.02, 9.0, 9.0)
        val simplified = simplifyRing(noisy, tolerance = 100.0)
        assertEquals(4, simplified.size)
        assertEquals(5.0, simplified[0])
        assertEquals(5.0, simplified[1])
        assertEquals(9.0, simplified[2])
        assertEquals(9.0, simplified[3])
    }

    @Test
    fun returnsShortRingsUntouched() {
        val pair = doubleArrayOf(0.0, 0.0, 1.0, 1.0)
        assertContentEquals(pair, simplifyRing(pair, tolerance = 10.0))
        assertContentEquals(doubleArrayOf(), simplifyRing(doubleArrayOf(), tolerance = 10.0))
    }

    @Test
    fun keepsEverythingWhenTheToleranceIsZero() {
        val line = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 2.0, 0.0)
        assertContentEquals(line, simplifyRing(line, tolerance = 0.0))
    }

    @Test
    fun neverGrowsARing() {
        val ring = DoubleArray(200) { index -> if (index % 2 == 0) index.toDouble() else (index % 7).toDouble() }
        val simplified = simplifyRing(ring, tolerance = 1.0)
        assertTrue(simplified.size <= ring.size, "${simplified.size} > ${ring.size}")
        assertTrue(simplified.size % PAIR_STRIDE == 0)
    }

    @Test
    fun doesNotModifyTheInput() {
        val line = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 2.0, 0.0)
        val copy = line.copyOf()
        simplifyRing(line, tolerance = 1.0)
        assertContentEquals(copy, line)
    }

    @Test
    fun refusesANegativeTolerance() {
        assertFailsWith<IllegalArgumentException> { simplifyRing(doubleArrayOf(0.0, 0.0), tolerance = -1.0) }
    }

    @Test
    fun refusesAnOddNumberOfValues() {
        assertFailsWith<IllegalArgumentException> { simplifyRing(doubleArrayOf(1.0, 2.0, 3.0), tolerance = 1.0) }
    }
}
