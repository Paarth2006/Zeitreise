package com.paarth.zeitreise.globe

import kotlin.math.abs

/** Closest the camera may come to a pole, which keeps the graticule and drag feel stable. */
public const val MAX_PITCH_DEGREES: Double = 85.0

/** Zoom level at which the globe fills its default radius. */
public const val MIN_ZOOM: Double = 1.0

/** Closest the camera may zoom in. */
public const val MAX_ZOOM: Double = 6.0

/**
 * Orientation and magnification of the globe.
 *
 * The camera is described by the geographic point facing the viewer, so a country is centred by
 * pointing the camera at it.
 *
 * @property yaw longitude facing the viewer, in `-180..180`.
 * @property pitch latitude facing the viewer, clamped to [MAX_PITCH_DEGREES].
 * @property zoom magnification, clamped to `MIN_ZOOM..MAX_ZOOM`.
 */
public data class GlobeCamera(val yaw: Double = 0.0, val pitch: Double = 0.0, val zoom: Double = MIN_ZOOM) {
    /** The geographic point currently facing the viewer. */
    public val center: GeoPoint get() = GeoPoint(latitude = pitch, longitude = yaw)

    /** Returns a camera turned by [deltaYaw] and [deltaPitch] degrees, with both limits applied. */
    public fun rotatedBy(deltaYaw: Double, deltaPitch: Double): GlobeCamera = copy(
        yaw = normalizeLongitude(yaw + deltaYaw),
        pitch = (pitch + deltaPitch).coerceIn(-MAX_PITCH_DEGREES, MAX_PITCH_DEGREES),
    )

    /** Returns a camera magnified by [factor], with the zoom limits applied. */
    public fun zoomedBy(factor: Double): GlobeCamera = copy(zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM))

    /** Returns a camera pointing at [target], keeping the current zoom unless [zoom] is given. */
    public fun pointingAt(target: GeoPoint, zoom: Double = this.zoom): GlobeCamera = GlobeCamera(
        yaw = normalizeLongitude(target.longitude),
        pitch = target.latitude.coerceIn(-MAX_PITCH_DEGREES, MAX_PITCH_DEGREES),
        zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
    )

    /**
     * Interpolates towards [target] by [fraction], turning the shorter way around the globe so
     * that a camera at 170 degrees east reaches 170 degrees west by crossing the antimeridian.
     *
     * @param fraction `0.0` returns this camera and `1.0` returns [target].
     */
    public fun interpolatedTo(target: GlobeCamera, fraction: Double): GlobeCamera {
        val progress = fraction.coerceIn(0.0, 1.0)
        return GlobeCamera(
            yaw = normalizeLongitude(yaw + shortestTurn(yaw, target.yaw) * progress),
            pitch = pitch + (target.pitch - pitch) * progress,
            zoom = zoom + (target.zoom - zoom) * progress,
        )
    }

    public companion object {
        /**
         * The signed number of degrees to turn to get from [fromYaw] to [toYaw] the shorter way,
         * always in `-180..180`.
         */
        internal fun shortestTurn(fromYaw: Double, toYaw: Double): Double {
            val direct = normalizeLongitude(toYaw - fromYaw)
            return if (abs(direct) == MAX_LONGITUDE) MAX_LONGITUDE else direct
        }
    }
}
