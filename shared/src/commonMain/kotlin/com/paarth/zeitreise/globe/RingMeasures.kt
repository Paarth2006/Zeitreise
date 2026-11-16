package com.paarth.zeitreise.globe

import kotlin.math.abs
import kotlin.math.cos

/** The larger of the ring's east-west and north-south spans, in degrees, from packed pairs. */
internal fun DoubleArray.ringExtent(): Double {
    var minLongitude = Double.MAX_VALUE
    var maxLongitude = -Double.MAX_VALUE
    var minLatitude = Double.MAX_VALUE
    var maxLatitude = -Double.MAX_VALUE
    var index = 0
    while (index < size) {
        minLongitude = minOf(minLongitude, this[index])
        maxLongitude = maxOf(maxLongitude, this[index])
        minLatitude = minOf(minLatitude, this[index + 1])
        maxLatitude = maxOf(maxLatitude, this[index + 1])
        index += PAIR_STRIDE
    }
    return maxOf(maxLongitude - minLongitude, maxLatitude - minLatitude)
}

/**
 * The ring's area in square degrees, measured with longitudes shrunk by the cosine of the ring's
 * mean latitude, so that a landmass near a pole does not look larger than it is. Close enough to
 * rank landmasses by size, which is all it is used for.
 *
 * @receiver packed `longitude, latitude` pairs.
 */
internal fun DoubleArray.ringArea(): Double {
    val count = size / PAIR_STRIDE
    if (count < MIN_AREA_VERTICES) return 0.0
    var latitudeSum = 0.0
    for (vertex in 0 until count) latitudeSum += this[vertex * PAIR_STRIDE + 1]
    val shrink = cos((latitudeSum / count).toRadians())

    var twiceArea = 0.0
    for (vertex in 0 until count) {
        val next = (vertex + 1) % count
        val x = this[vertex * PAIR_STRIDE] * shrink
        val y = this[vertex * PAIR_STRIDE + 1]
        val nextX = this[next * PAIR_STRIDE] * shrink
        val nextY = this[next * PAIR_STRIDE + 1]
        twiceArea += x * nextY - nextX * y
    }
    return abs(twiceArea) / 2
}

/** Fewest vertices that can enclose any area. */
private const val MIN_AREA_VERTICES = 3
