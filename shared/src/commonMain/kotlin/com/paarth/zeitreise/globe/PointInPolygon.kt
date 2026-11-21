package com.paarth.zeitreise.globe

/**
 * Whether [point] falls inside the ring described by [vertices].
 *
 * Uses even-odd ray casting in longitude-latitude space: a ray goes east from the point and the
 * edges it crosses are counted. Working in degrees instead of screen pixels means the result does
 * not depend on the camera.
 *
 * The ring is treated as closed. Rings crossing the antimeridian have to be split first, which the
 * bundled Natural Earth outlines already are.
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

/** A ring needs at least three vertices (six packed values) to enclose an area. */
private const val MIN_RING_VALUES = 6
