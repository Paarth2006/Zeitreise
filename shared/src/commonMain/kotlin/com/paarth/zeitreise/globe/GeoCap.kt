package com.paarth.zeitreise.globe

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/** Below this length the mean of a ring's vectors has no meaningful direction. */
private const val DEGENERATE_LENGTH = 1e-9

/** Angular radius from which a region covers the whole visible hemisphere. */
private const val HEMISPHERE_DEGREES = 90.0

/**
 * A circular region on the sphere, used to frame a country on screen.
 *
 * @property center the point the region is centred on.
 * @property radiusDegrees the angle from [center] to the region's farthest point.
 */
public data class GeoCap(val center: GeoPoint, val radiusDegrees: Double) {
    /**
     * The zoom at which this region spans [fill] of the globe's radius at zoom one.
     *
     * In an orthographic view a point at angle θ from the centre lands at `sin θ` of the radius,
     * so the region's edge reaches `zoom · sin θ`. Solving for the zoom that puts the edge at
     * [fill] gives `fill / sin θ`, which the camera's limits then clamp.
     */
    public fun framingZoom(fill: Double): Double {
        val reach = sin(radiusDegrees.toRadians())
        return when {
            radiusDegrees >= HEMISPHERE_DEGREES -> MIN_ZOOM
            reach <= 0.0 -> MAX_ZOOM
            else -> (fill / reach).coerceIn(MIN_ZOOM, MAX_ZOOM)
        }
    }
}

/**
 * The cap that holds every vertex of packed `x, y, z` unit-sphere triples.
 *
 * Centred on the normalised mean of the vertices, which is cheap and, for a single landmass,
 * close to the smallest enclosing cap. A ring whose vertices cancel out, such as a band around a
 * pole, is centred on its first vertex instead.
 */
internal fun boundingCap(sphere: DoubleArray): GeoCap {
    require(sphere.size >= SPHERE_STRIDE) { "A cap needs at least one vertex, got ${sphere.size} values" }

    var sumX = 0.0
    var sumY = 0.0
    var sumZ = 0.0
    var index = 0
    while (index < sphere.size) {
        sumX += sphere[index]
        sumY += sphere[index + 1]
        sumZ += sphere[index + 2]
        index += SPHERE_STRIDE
    }
    val length = sqrt(sumX * sumX + sumY * sumY + sumZ * sumZ)
    val center = if (length > DEGENERATE_LENGTH) {
        SpherePoint(sumX / length, sumY / length, sumZ / length)
    } else {
        SpherePoint(sphere[0], sphere[1], sphere[2])
    }

    var widest = 0.0
    index = 0
    while (index < sphere.size) {
        val dot = center.x * sphere[index] + center.y * sphere[index + 1] + center.z * sphere[index + 2]
        widest = maxOf(widest, acos(dot.coerceIn(-1.0, 1.0)))
        index += SPHERE_STRIDE
    }
    return GeoCap(center = center.toGeoPoint(), radiusDegrees = widest.toDegrees())
}

/** Converts a unit-sphere vector back to latitude and longitude. */
internal fun SpherePoint.toGeoPoint(): GeoPoint = GeoPoint(
    latitude = asin(y.coerceIn(-1.0, 1.0)).toDegrees(),
    longitude = atan2(x, z).toDegrees(),
)
