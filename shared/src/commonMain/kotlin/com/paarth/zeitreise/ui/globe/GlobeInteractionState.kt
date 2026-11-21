package com.paarth.zeitreise.ui.globe

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Velocity
import com.paarth.zeitreise.globe.DEGREES_PER_HALF_TURN
import com.paarth.zeitreise.globe.GeoCap
import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.GlobeCamera
import com.paarth.zeitreise.globe.GlobeProjection
import com.paarth.zeitreise.globe.MIN_ZOOM
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/** Globe radius as a share of the smaller screen side, at zoom 1. */
private const val RADIUS_FRACTION = 0.46f

/** Idle drift speed in degrees per second. A full turn takes four minutes. */
private const val IDLE_SPIN_DEGREES_PER_SECOND = 1.5

/** Seconds without input before the idle drift starts. */
private const val IDLE_DELAY_SECONDS = 4.0f

/** Seconds for the drift to speed up smoothly. */
private const val IDLE_RAMP_SECONDS = 2.0f

/** Starting speed of the backwards spin that acts out Time Travel in the tour, in degrees per second. */
private const val REWIND_DEGREES_PER_SECOND = -720.0

/** Share of the fling speed left after one second. */
private const val FLING_RETENTION_PER_SECOND = 0.03f

/** Below this speed (degrees per second) a fling stops. */
private const val FLING_STOP_DEGREES_PER_SECOND = 1.0

/** How much one mouse wheel notch zooms. */
private const val SCROLL_ZOOM_SENSITIVITY = 0.12f

/** The globe's default vertical position, in the middle of the screen. */
private const val HALF = 0.5f

/** How far a framed country is moved up, so the sheet doesn't cover it. */
private const val FRAMED_LIFT = 0.16f

/** How far a framed country is moved down instead, when a card covers the top of the screen. */
private const val LOWERED_LIFT = -0.15f

/** Duration of a fly-to animation in seconds. */
private const val FLIGHT_SECONDS = 1.4f

/** How much of the globe's radius a framed country fills, leaving room for the sheet. */
private const val FRAME_FILL = 0.7

/**
 * The globe's camera, inertia, idle drift and fly-to animations.
 *
 * @param start the point the globe faces at first, such as the user's home country.
 *
 * The camera is Compose state read only while drawing, so a camera change redraws the globe
 * without any recomposition.
 */
internal class GlobeInteractionState(start: GeoPoint? = null) {

    /** The current camera. It starts facing [start], or the default spot if there is none. */
    var camera: GlobeCamera by mutableStateOf(
        start?.let { GlobeCamera().pointingAt(it, zoom = MIN_ZOOM) } ?: GlobeCamera(),
    )
        private set

    /** Size of the drawing area, used to convert drag pixels into degrees. */
    var viewport: Size by mutableStateOf(Size.Zero)

    /** How far the globe is moved up, as a share of the screen height, while a country is framed. */
    private var lift by mutableFloatStateOf(0f)

    /**
     * Whether a flight or fling is still moving the camera. It's Compose state, so the frame loop can
     * wait for it instead of running every frame.
     */
    var isMoving: Boolean by mutableStateOf(false)
        private set

    private var flingYaw = 0.0
    private var flingPitch = 0.0
    private var stillSeconds = 0f
    private var flight: Flight? = null

    /** Radius of the globe at the current zoom, in pixels. */
    val radius: Float get() = baseRadius * camera.zoom.toFloat()

    private val baseRadius: Float get() = min(viewport.width, viewport.height) * RADIUS_FRACTION

    /** The projection for what is currently on screen. */
    fun projection(): GlobeProjection = GlobeProjection(
        camera = camera,
        centerX = (viewport.width / 2f).toDouble(),
        centerY = (viewport.height * (HALF - lift)).toDouble(),
        baseRadius = baseRadius.toDouble(),
    )

    /** Stops all automatic movement because the user grabbed the globe. */
    fun onPressed() {
        flight = null
        flingYaw = 0.0
        flingPitch = 0.0
        stillSeconds = 0f
        isMoving = false
    }

    /** Turns the globe by a drag of [delta] pixels. */
    fun onDragged(delta: Offset) {
        stillSeconds = 0f
        camera = camera.rotatedBy(
            deltaYaw = -degreesPerPixel(radius) * delta.x,
            deltaPitch = degreesPerPixel(radius) * delta.y,
        )
    }

    /** Zooms by [factor], within the zoom limits. */
    fun onZoomed(factor: Float) {
        stillSeconds = 0f
        camera = camera.zoomedBy(factor.toDouble())
    }

    /** Zooms in response to the mouse wheel. [scrollDelta] is in wheel notches. */
    fun onScrolled(scrollDelta: Float) {
        onZoomed(exp(-scrollDelta * SCROLL_ZOOM_SENSITIVITY))
    }

    /** Keeps the globe spinning after a drag that ended at [velocity] pixels per second. */
    fun onReleased(velocity: Velocity) {
        flingYaw = -degreesPerPixel(radius) * velocity.x
        flingPitch = degreesPerPixel(radius) * velocity.y
        isMoving = flingYaw != 0.0 || flingPitch != 0.0
    }

    /**
     * Advances inertia and idle drift by [seconds]. Called every frame, so it avoids allocations.
     *
     * @param idle whether the globe may drift. It doesn't while a country is selected, or the
     *   country would drift out of view.
     */
    fun advance(seconds: Float, idle: Boolean) {
        flight?.let { current ->
            camera = current.advance(seconds)
            lift = current.lift
            if (current.isFinished) {
                flight = null
                // A rewind spin may be waiting for the flight to land.
                isMoving = flingYaw != 0.0 || flingPitch != 0.0
            }
            return
        }

        val decay = FLING_RETENTION_PER_SECOND.toDouble().pow(seconds.toDouble())
        flingYaw *= decay
        flingPitch *= decay
        if (abs(flingYaw) < FLING_STOP_DEGREES_PER_SECOND && abs(flingPitch) < FLING_STOP_DEGREES_PER_SECOND) {
            flingYaw = 0.0
            flingPitch = 0.0
            stillSeconds += seconds
            isMoving = false
        }

        val idleSpin = if (idle) IDLE_SPIN_DEGREES_PER_SECOND * idleRamp() else 0.0
        val deltaYaw = (flingYaw + idleSpin) * seconds
        val deltaPitch = flingPitch * seconds
        if (deltaYaw != 0.0 || deltaPitch != 0.0) {
            camera = camera.rotatedBy(deltaYaw = deltaYaw, deltaPitch = deltaPitch)
        }
    }

    /**
     * Flies the camera to frame [region]: above the middle of the screen, out of the sheet's way, or
     * below it if [lowered], out of the way of a card at the top.
     */
    fun flyToFrame(region: GeoCap, lowered: Boolean = false) {
        onPressed()
        flight = Flight(
            from = camera,
            to = camera.pointingAt(region.center, zoom = region.framingZoom(FRAME_FILL)),
            fromLift = lift,
            toLift = if (lowered) LOWERED_LIFT else FRAMED_LIFT,
        )
        isMoving = true
    }

    /** Flies back out to show the whole globe. */
    fun flyToWholeGlobe() {
        if (camera.zoom == MIN_ZOOM) return
        onPressed()
        flight = Flight(from = camera, to = camera.copy(zoom = MIN_ZOOM), fromLift = lift, toLift = 0f)
        isMoving = true
    }

    /** Drift speed factor from 0 to 1, ramping up once the globe has been still long enough. */
    private fun idleRamp(): Double {
        val elapsed = stillSeconds - IDLE_DELAY_SECONDS
        if (elapsed <= 0f) return 0.0
        return min(1f, elapsed / IDLE_RAMP_SECONDS).toDouble()
    }
}

/**
 * Spins the globe quickly backwards, against its usual drift, like a clock turned back. It's a
 * fling, so it slows down on its own and waits for any flight to finish first.
 */
internal fun GlobeInteractionState.rewind() {
    val degrees = degreesPerPixel(radius)
    if (degrees == 0.0) return
    // A fling turns by minus the drag speed, so this drag speed gives the rewind speed.
    onReleased(Velocity(x = (-REWIND_DEGREES_PER_SECOND / degrees).toFloat(), y = 0f))
}

/**
 * Degrees to turn per pixel of drag, for a globe of [radius] pixels. Since arc length is radius
 * times angle, the angle is the drag distance divided by the radius.
 */
private fun degreesPerPixel(radius: Float): Double = if (radius <= 0f) 0.0 else DEGREES_PER_HALF_TURN / PI / radius

/** One eased fly-to animation from [from] to [to]. */
private class Flight(
    private val from: GlobeCamera,
    private val to: GlobeCamera,
    private val fromLift: Float,
    private val toLift: Float,
) {
    private var elapsed = 0f
    private var eased = 0f

    /** Whether the flight has reached [to]. */
    val isFinished: Boolean get() = elapsed >= FLIGHT_SECONDS

    /** How far the globe is raised at this point of the flight. */
    val lift: Float get() = fromLift + (toLift - fromLift) * eased

    /** Advances the flight by [seconds] and returns the camera at that point. */
    fun advance(seconds: Float): GlobeCamera {
        elapsed = min(elapsed + seconds, FLIGHT_SECONDS)
        eased = FastOutSlowInEasing.transform(elapsed / FLIGHT_SECONDS)
        return from.flightTo(to, eased.toDouble())
    }
}
