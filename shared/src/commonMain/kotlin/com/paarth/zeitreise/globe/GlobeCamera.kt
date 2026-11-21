package com.paarth.zeitreise.globe

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** How close the camera may get to a pole, so dragging near the poles stays stable. */
public const val MAX_PITCH_DEGREES: Double = 85.0

/** Zoom level at which the globe fills its default radius. */
public const val MIN_ZOOM: Double = 1.0

/** Maximum zoom, enough to frame the smallest country (Vatican City) when it is opened from search. */
public const val MAX_ZOOM: Double = 8_000.0

/**
 * How much zoom a flight to the opposite side of the globe gives up at its midpoint. Long flights
 * pull back to show where they are going, short ones barely do.
 */
private const val FLIGHT_PULL_BACK = 0.6

/**
 * Orientation and zoom of the globe, described by the point that faces the viewer.
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
     * Interpolates towards [target] by [fraction], turning the shorter way round the globe
     * (from 170°E to 170°W it crosses the antimeridian).
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

    /**
     * The camera [fraction] of the way along a flight to [target]. Like [interpolatedTo], but it
     * also zooms out in the middle of the flight, more for longer flights.
     *
     * @param fraction `0.0` returns this camera and `1.0` returns [target].
     */
    public fun flightTo(target: GlobeCamera, fraction: Double): GlobeCamera {
        val progress = fraction.coerceIn(0.0, 1.0)
        val straight = interpolatedTo(target, progress)
        val distance = center.angleTo(target.center) / DEGREES_PER_HALF_TURN
        val pullBack = FLIGHT_PULL_BACK * distance * sin(PI * progress)
        return straight.copy(zoom = (straight.zoom * (1.0 - pullBack)).coerceIn(MIN_ZOOM, MAX_ZOOM))
    }

    public companion object {
        /** Signed degrees to turn from [fromYaw] to [toYaw] the shorter way, in `-180..180`. */
        internal fun shortestTurn(fromYaw: Double, toYaw: Double): Double {
            val direct = normalizeLongitude(toYaw - fromYaw)
            return if (abs(direct) == MAX_LONGITUDE) MAX_LONGITUDE else direct
        }
    }
}
