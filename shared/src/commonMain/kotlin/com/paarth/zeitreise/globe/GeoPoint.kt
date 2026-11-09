package com.paarth.zeitreise.globe

import kotlin.math.cos
import kotlin.math.sin

/** Degrees of latitude at the north pole; latitude is clamped to this range. */
internal const val MAX_LATITUDE: Double = 90.0

/** Degrees of longitude at the antimeridian. */
internal const val MAX_LONGITUDE: Double = 180.0

/** Degrees in half a turn, the factor between degrees and radians. */
internal const val DEGREES_PER_HALF_TURN: Double = 180.0

/**
 * A point on the surface of the globe in degrees.
 *
 * @property latitude degrees north of the equator, in `-90..90`.
 * @property longitude degrees east of the prime meridian, in `-180..180`.
 */
public data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -MAX_LATITUDE..MAX_LATITUDE) { "Latitude $latitude is outside -90..90" }
        require(longitude in -MAX_LONGITUDE..MAX_LONGITUDE) { "Longitude $longitude is outside -180..180" }
    }
}

/**
 * Wraps [degrees] into the `-180..180` range used for longitudes, so that rotating the globe past
 * the antimeridian stays in range.
 */
internal fun normalizeLongitude(degrees: Double): Double {
    val wrapped = degrees.mod(2.0 * MAX_LONGITUDE)
    return if (wrapped > MAX_LONGITUDE) wrapped - 2.0 * MAX_LONGITUDE else wrapped
}

/**
 * The unit-sphere position of a geographic point, with the camera looking down the negative z
 * axis, so that `(0, 0)` faces the viewer.
 *
 * The globe renderer converts each outline vertex once at load time and then only rotates these
 * vectors per frame, which keeps trigonometry out of the draw loop.
 *
 * @property x east-west axis, positive towards 90 degrees east.
 * @property y north-south axis, positive towards the north pole.
 * @property z depth axis, positive towards the viewer.
 */
internal data class SpherePoint(val x: Double, val y: Double, val z: Double)

/** Converts a geographic point to its position on the unit sphere. */
internal fun GeoPoint.toSpherePoint(): SpherePoint {
    val latitudeRadians = latitude.toRadians()
    val longitudeRadians = longitude.toRadians()
    val cosLatitude = cos(latitudeRadians)
    return SpherePoint(
        x = cosLatitude * sin(longitudeRadians),
        y = sin(latitudeRadians),
        z = cosLatitude * cos(longitudeRadians),
    )
}

internal fun Double.toRadians(): Double = this * kotlin.math.PI / DEGREES_PER_HALF_TURN

internal fun Double.toDegrees(): Double = this * DEGREES_PER_HALF_TURN / kotlin.math.PI
