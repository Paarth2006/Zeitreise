package com.paarth.zeitreise.globe

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/** Latitude of the north pole; latitudes are clamped to this. */
internal const val MAX_LATITUDE: Double = 90.0

/** Degrees of longitude at the antimeridian. */
internal const val MAX_LONGITUDE: Double = 180.0

/** Degrees in half a turn, used to convert between degrees and radians. */
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

/** Wraps [degrees] into the `-180..180` longitude range. */
internal fun normalizeLongitude(degrees: Double): Double {
    val wrapped = degrees.mod(2.0 * MAX_LONGITUDE)
    return if (wrapped > MAX_LONGITUDE) wrapped - 2.0 * MAX_LONGITUDE else wrapped
}

/**
 * A position on the unit sphere, with `(0, 0)` facing the viewer along the z axis.
 *
 * Outline vertices are converted to these once at load time, so drawing a frame only has to rotate
 * them and needs no trigonometry.
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

/** The angle between this point and [other] as seen from the centre of the globe, in degrees. */
internal fun GeoPoint.angleTo(other: GeoPoint): Double {
    val a = toSpherePoint()
    val b = other.toSpherePoint()
    return acos((a.x * b.x + a.y * b.y + a.z * b.z).coerceIn(-1.0, 1.0)).toDegrees()
}
