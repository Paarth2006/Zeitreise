package com.paarth.zeitreise.ui.globe

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity

/**
 * What a press on the globe turned out to be.
 *
 * @property travelled whether the press moved far enough to count as a drag or pinch rather than
 *   a tap.
 * @property velocity how fast a single finger was moving when it lifted, in pixels per second. A
 *   pinch leaves the two fingers moving towards each other, which would fling the globe sideways,
 *   so only a one-finger drag carries momentum.
 */
private data class PressOutcome(val travelled: Boolean, val velocity: Velocity)

/**
 * Drag, pinch, mouse wheel and tap handling for the globe.
 *
 * Written as one gesture loop rather than several detectors, so that the end of a drag, and with
 * it the fling velocity, is observed, and so that a press that never travels is reported as a tap.
 *
 * @param onPressed the globe has been grabbed; automatic movement should stop.
 * @param onDragged the globe should turn by this many pixels.
 * @param onZoomed the globe should be magnified by this factor.
 * @param onScrolled the mouse wheel moved by this many notches.
 * @param onReleased a drag ended at this velocity, in pixels per second.
 * @param onTapped the globe was tapped at this position in the layout.
 */
internal fun Modifier.globeGestures(
    onPressed: () -> Unit,
    onDragged: (Offset) -> Unit,
    onZoomed: (Float) -> Unit,
    onScrolled: (Float) -> Unit,
    onReleased: (Velocity) -> Unit,
    onTapped: (Offset) -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onPressed()
            val outcome = trackPress(down, onDragged = onDragged, onZoomed = onZoomed)
            if (outcome.travelled) onReleased(outcome.velocity) else onTapped(down.position)
        }
    }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val notches = when (event.type) {
                    PointerEventType.Scroll ->
                        event.changes.fold(0f) { total, change -> total + change.scrollDelta.y }
                    else -> 0f
                }
                if (notches != 0f) {
                    onScrolled(notches)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

/**
 * Follows one press until every finger has lifted, reporting drags and pinches as they happen.
 *
 * Movement is ignored until it passes the touch slop, so that a tap with a shaky finger still
 * selects a country instead of nudging the globe.
 */
private suspend fun AwaitPointerEventScope.trackPress(
    down: PointerInputChange,
    onDragged: (Offset) -> Unit,
    onZoomed: (Float) -> Unit,
): PressOutcome {
    val velocity = VelocityTracker()
    velocity.addPosition(down.uptimeMillis, down.position)
    var travelled = false
    var multiTouch = false
    var held = true

    while (held) {
        val event = awaitPointerEvent()
        held = event.changes.any { it.pressed }
        if (!held) continue

        multiTouch = multiTouch || event.changes.count { it.pressed } > 1
        travelled = travelled || event.hasTravelledFrom(down.position, viewConfiguration.touchSlop)
        if (travelled) event.apply(velocity, onDragged = onDragged, onZoomed = onZoomed)
    }

    return PressOutcome(
        travelled = travelled,
        velocity = if (travelled && !multiTouch) velocity.calculateVelocity() else Velocity.Zero,
    )
}

/** Whether the press has moved far enough, or pinched at all, to be a drag rather than a tap. */
private fun PointerEvent.hasTravelledFrom(origin: Offset, touchSlop: Float): Boolean =
    (calculateCentroid() - origin).getDistance() > touchSlop || calculateZoom() != 1f

/** Reports this event's pinch and pan, and records the pan for the fling velocity. */
private fun PointerEvent.apply(velocity: VelocityTracker, onDragged: (Offset) -> Unit, onZoomed: (Float) -> Unit) {
    val zoom = calculateZoom()
    if (zoom != 1f) onZoomed(zoom)
    val pan = calculatePan()
    if (pan != Offset.Zero) {
        onDragged(pan)
        velocity.addPosition(changes.first().uptimeMillis, calculateCentroid())
    }
    changes.forEach { it.consume() }
}
