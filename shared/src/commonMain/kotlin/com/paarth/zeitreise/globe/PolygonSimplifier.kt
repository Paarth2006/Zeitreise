package com.paarth.zeitreise.globe

import kotlin.math.abs

/**
 * Drops vertices from the ring in [vertices] that sit closer than [tolerance] degrees to the line
 * their neighbours already describe.
 *
 * This is the Ramer-Douglas-Peucker algorithm, run once per outline at load time. The bundled
 * outlines are drawn at full detail when zoomed in, so simplification trades a little fidelity for
 * a much shorter draw loop at the default zoom.
 *
 * @param vertices packed `longitude, latitude` pairs.
 * @param tolerance greatest distance, in degrees, a dropped vertex may sit from the kept line.
 * @return packed pairs with the first and last vertex always kept.
 */
internal fun simplifyRing(vertices: DoubleArray, tolerance: Double): DoubleArray {
    require(tolerance >= 0.0) { "Tolerance $tolerance is negative" }
    require(vertices.size % PAIR_STRIDE == 0) {
        "A ring needs an even number of values, but got ${vertices.size}"
    }
    val count = vertices.size / PAIR_STRIDE
    if (count <= 2 || tolerance == 0.0) return vertices.copyOf()

    val keep = BooleanArray(count)
    keep[0] = true
    keep[count - 1] = true
    simplifySegment(vertices, first = 0, last = count - 1, tolerance = tolerance, keep = keep)

    val kept = keep.count { it }
    val result = DoubleArray(kept * PAIR_STRIDE)
    var target = 0
    for (index in 0 until count) {
        if (!keep[index]) continue
        result[target] = vertices[index * PAIR_STRIDE]
        result[target + 1] = vertices[index * PAIR_STRIDE + 1]
        target += PAIR_STRIDE
    }
    return result
}

/**
 * Marks the vertex furthest from the line between [first] and [last] for keeping, then recurses
 * into the two halves it creates, until no vertex is further away than [tolerance].
 */
private fun simplifySegment(vertices: DoubleArray, first: Int, last: Int, tolerance: Double, keep: BooleanArray) {
    if (last <= first + 1) return

    val startX = vertices[first * PAIR_STRIDE]
    val startY = vertices[first * PAIR_STRIDE + 1]
    val endX = vertices[last * PAIR_STRIDE]
    val endY = vertices[last * PAIR_STRIDE + 1]

    var furthest = -1
    var furthestDistance = 0.0
    for (index in first + 1 until last) {
        val distance = perpendicularDistance(
            pointX = vertices[index * PAIR_STRIDE],
            pointY = vertices[index * PAIR_STRIDE + 1],
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
        )
        if (distance > furthestDistance) {
            furthestDistance = distance
            furthest = index
        }
    }

    if (furthest < 0 || furthestDistance <= tolerance) return
    keep[furthest] = true
    simplifySegment(vertices, first = first, last = furthest, tolerance = tolerance, keep = keep)
    simplifySegment(vertices, first = furthest, last = last, tolerance = tolerance, keep = keep)
}

/** Distance from a point to the line segment between two other points. */
private fun perpendicularDistance(
    pointX: Double,
    pointY: Double,
    startX: Double,
    startY: Double,
    endX: Double,
    endY: Double,
): Double {
    val deltaX = endX - startX
    val deltaY = endY - startY
    if (deltaX == 0.0 && deltaY == 0.0) {
        return kotlin.math.hypot(pointX - startX, pointY - startY)
    }
    val area = abs(deltaX * (startY - pointY) - (startX - pointX) * deltaY)
    return area / kotlin.math.hypot(deltaX, deltaY)
}
