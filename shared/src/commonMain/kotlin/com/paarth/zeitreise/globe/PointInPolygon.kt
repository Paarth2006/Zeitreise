package com.paarth.zeitreise.globe

/**
 * Whether [point] falls inside the ring described by [vertices].
 *
 * Uses the even-odd ray casting rule in longitude-latitude space: a ray is cast east from the
 * point and the crossings of the ring's edges are counted. Testing in geographic space rather than
 * on screen keeps the result independent of the camera, so hit testing never has to be redone
 * when the globe turns.
 *
 * The ring is treated as closed, whether or not its last vertex repeats its first. Rings that
 * straddle the antimeridian must be split before they are tested; the Natural Earth outlines the
 * app bundles are already split.
 *
 * @param vertices packed `longitude, latitude` pairs.
 */
internal fun isPointInRing(point: GeoPoint, vertices: DoubleArray): Boolean {
    require(vertices.size % PAIR_STRIDE == 0) {
        "A ring needs an even number of values, but got ${vertices.size}"
    }
    if (vertices.size < MIN_RING_VALUES) return false

    val longitude = point.longitude
    val latitude = point.latitude
    var inside = false
    var previous = vertices.size - PAIR_STRIDE
    var current = 0
    while (current < vertices.size) {
        val currentLongitude = vertices[current]
        val currentLatitude = vertices[current + 1]
        val previousLongitude = vertices[previous]
        val previousLatitude = vertices[previous + 1]

        val straddles = (currentLatitude > latitude) != (previousLatitude > latitude)
        if (straddles) {
            val crossingLongitude = currentLongitude +
                (latitude - currentLatitude) / (previousLatitude - currentLatitude) *
                (previousLongitude - currentLongitude)
            if (longitude < crossingLongitude) inside = !inside
        }
        previous = current
        current += PAIR_STRIDE
    }
    return inside
}

/** A ring needs at least three vertices, so six packed values, to enclose anything. */
private const val MIN_RING_VALUES = 6
