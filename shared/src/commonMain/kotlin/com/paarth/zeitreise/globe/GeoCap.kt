package com.paarth.zeitreise.globe

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

/** Below this length the average of a ring's vectors has no usable direction. */
private const val DEGENERATE_LENGTH = 1e-9

/** Radius at which a region covers the whole visible hemisphere. */
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
     * In an orthographic view a point at angle θ from the centre lands at `sin θ` of the radius, so
     * the zoom is `fill / sin θ`, clamped to the camera's limits.
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
 * It is centred on the average of the vertices, which is cheap and close enough for one landmass.
 * If the vertices cancel out, like a band around a pole, the first vertex is used as the centre.
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

/** The smallest cap that holds both this cap and [other], used to frame two regions at once. */
internal infix fun GeoCap.union(other: GeoCap): GeoCap {
    val apart = center.angleTo(other.center)
    return when {
        apart + other.radiusDegrees <= radiusDegrees -> this
        apart + radiusDegrees <= other.radiusDegrees -> other
        else -> {
            val radius = (apart + radiusDegrees + other.radiusDegrees) / 2
            GeoCap(
                center = center.towards(other.center, fraction = (radius - radiusDegrees) / apart),
                radiusDegrees = radius.coerceAtMost(DEGREES_PER_HALF_TURN),
            )
        }
    }
}

/**
 * The point [fraction] of the way along the great circle to [other]. For opposite points there is
 * no single great circle, so this point is returned.
 */
private fun GeoPoint.towards(other: GeoPoint, fraction: Double): GeoPoint {
    val from = toSpherePoint()
    val to = other.toSpherePoint()
    val angle = angleTo(other).toRadians()
    val sinAngle = sin(angle)
    if (sinAngle < DEGENERATE_LENGTH) return this
    val fromWeight = sin((1 - fraction) * angle) / sinAngle
    val toWeight = sin(fraction * angle) / sinAngle
    return SpherePoint(
        x = from.x * fromWeight + to.x * toWeight,
        y = from.y * fromWeight + to.y * toWeight,
        z = from.z * fromWeight + to.z * toWeight,
    ).toGeoPoint()
}

/** Converts a unit-sphere vector back to latitude and longitude. */
internal fun SpherePoint.toGeoPoint(): GeoPoint = GeoPoint(
    latitude = asin(y.coerceIn(-1.0, 1.0)).toDegrees(),
    longitude = atan2(x, z).toDegrees(),
)
