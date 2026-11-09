package com.paarth.zeitreise.ui.globe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Velocity
import com.paarth.zeitreise.globe.DEGREES_PER_HALF_TURN
import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.GlobeCamera
import com.paarth.zeitreise.globe.GlobeProjection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/** Share of the smaller viewport edge the globe takes up at [GlobeCamera.zoom] of one. */
private const val RADIUS_FRACTION = 0.46f

/**
 * Degrees per second the globe drifts at when the user has left it alone. Slow enough that a
 * region stays readable for a while: a full turn takes four minutes.
 */
private const val IDLE_SPIN_DEGREES_PER_SECOND = 1.5

/** Seconds of stillness before the idle drift starts again. */
private const val IDLE_DELAY_SECONDS = 4.0f

/** Seconds the idle drift takes to reach full speed, so that it starts without a jolt. */
private const val IDLE_RAMP_SECONDS = 2.0f

/** Share of the fling speed that remains after a second, which is how quickly inertia dies away. */
private const val FLING_RETENTION_PER_SECOND = 0.03f

/** Speed, in degrees per second, below which a fling is treated as finished. */
private const val FLING_STOP_DEGREES_PER_SECOND = 1.0

/** How much of a mouse wheel notch turns into zoom. */
private const val SCROLL_ZOOM_SENSITIVITY = 0.12f

/**
 * Camera, inertia and idle drift of the globe.
 *
 * Held across recompositions by [rememberGlobeInteractionState]. The camera is Compose state read
 * inside the draw lambda, so a new frame redraws the globe without recomposing anything.
 */
internal class GlobeInteractionState {

    /** Orientation and zoom the globe is currently drawn with. */
    var camera: GlobeCamera by mutableStateOf(GlobeCamera())
        private set

    /** Size of the area the globe is drawn in, needed to turn a drag in pixels into degrees. */
    var viewport: Size by mutableStateOf(Size.Zero)

    private var flingYaw = 0.0
    private var flingPitch = 0.0
    private var stillSeconds = 0f

    /** Radius of the globe at the current zoom, in pixels. */
    val radius: Float get() = baseRadius * camera.zoom.toFloat()

    private val baseRadius: Float get() = min(viewport.width, viewport.height) * RADIUS_FRACTION

    /** The projection matching what is currently on screen. */
    fun projection(): GlobeProjection = GlobeProjection(
        camera = camera,
        centerX = (viewport.width / 2f).toDouble(),
        centerY = (viewport.height / 2f).toDouble(),
        baseRadius = baseRadius.toDouble(),
    )

    /** Stops every automatic movement, because the user has taken hold of the globe. */
    fun onPressed() {
        flingYaw = 0.0
        flingPitch = 0.0
        stillSeconds = 0f
    }

    /** Turns the globe by a drag of [delta] pixels. */
    fun onDragged(delta: Offset) {
        stillSeconds = 0f
        camera = camera.rotatedBy(
            deltaYaw = -degreesPerPixel() * delta.x,
            deltaPitch = degreesPerPixel() * delta.y,
        )
    }

    /** Magnifies the globe by [factor], within the zoom limits. */
    fun onZoomed(factor: Float) {
        stillSeconds = 0f
        camera = camera.zoomedBy(factor.toDouble())
    }

    /** Magnifies the globe in response to a mouse wheel, where [scrollDelta] is in wheel notches. */
    fun onScrolled(scrollDelta: Float) {
        onZoomed(exp(-scrollDelta * SCROLL_ZOOM_SENSITIVITY))
    }

    /** Lets the globe keep spinning after a drag ends at [velocity] pixels per second. */
    fun onReleased(velocity: Velocity) {
        flingYaw = -degreesPerPixel() * velocity.x
        flingPitch = degreesPerPixel() * velocity.y
    }

    /**
     * Advances inertia and idle drift by [seconds].
     *
     * Called once per frame, so it must stay free of allocation and of trigonometry.
     *
     * @param idle whether the globe may drift on its own. It may not while a country is selected,
     *   since drifting would carry that country out of view while its sheet is still open.
     */
    fun advance(seconds: Float, idle: Boolean) {
        val decay = FLING_RETENTION_PER_SECOND.toDouble().pow(seconds.toDouble())
        flingYaw *= decay
        flingPitch *= decay
        if (abs(flingYaw) < FLING_STOP_DEGREES_PER_SECOND && abs(flingPitch) < FLING_STOP_DEGREES_PER_SECOND) {
            flingYaw = 0.0
            flingPitch = 0.0
            stillSeconds += seconds
        }

        val idleSpin = if (idle) IDLE_SPIN_DEGREES_PER_SECOND * idleRamp() else 0.0
        val deltaYaw = (flingYaw + idleSpin) * seconds
        val deltaPitch = flingPitch * seconds
        if (deltaYaw != 0.0 || deltaPitch != 0.0) {
            camera = camera.rotatedBy(deltaYaw = deltaYaw, deltaPitch = deltaPitch)
        }
    }

    /** Points the camera at [target], used when a country is selected. */
    fun pointAt(target: GeoPoint) {
        onPressed()
        camera = camera.pointingAt(target)
    }

    /** Fades the idle drift in over [IDLE_RAMP_SECONDS] once the globe has been still long enough. */
    private fun idleRamp(): Double {
        val elapsed = stillSeconds - IDLE_DELAY_SECONDS
        if (elapsed <= 0f) return 0.0
        return min(1f, elapsed / IDLE_RAMP_SECONDS).toDouble()
    }

    /**
     * Degrees the globe turns per pixel of drag.
     *
     * A drag at the centre of the globe moves the surface under the finger: the surface arc is the
     * radius times the angle, so the angle is the distance divided by the radius.
     */
    private fun degreesPerPixel(): Double {
        val pixels = radius.toDouble()
        if (pixels <= 0.0) return 0.0
        return DEGREES_PER_HALF_TURN / PI / pixels
    }
}
