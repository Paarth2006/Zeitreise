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
 * The result of one press on the globe.
 *
 * @property travelled whether it moved far enough to be a drag or pinch instead of a tap.
 * @property velocity the finger's speed when it lifted, in pixels per second. Only one-finger drags
 *   get momentum, since pinches would fling the globe sideways.
 */
private data class PressOutcome(val travelled: Boolean, val velocity: Velocity)

/**
 * Handles drag, pinch, mouse wheel and tap on the globe.
 *
 * It's one gesture loop instead of several detectors, so it can see when a drag ends (for the fling)
 * and treat a press that didn't move as a tap.
 *
 * @param onPressed the globe was grabbed, so automatic movement should stop.
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
 * Tracks one press until all fingers lift, reporting drags and pinches as they happen. Small
 * movements under the touch slop are ignored, so a slightly shaky tap still counts as a tap.
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

/** Whether the press moved or pinched enough to be a drag instead of a tap. */
private fun PointerEvent.hasTravelledFrom(origin: Offset, touchSlop: Float): Boolean =
    (calculateCentroid() - origin).getDistance() > touchSlop || calculateZoom() != 1f

/** Reports the pinch and pan of this event and tracks the pan for the fling velocity. */
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
