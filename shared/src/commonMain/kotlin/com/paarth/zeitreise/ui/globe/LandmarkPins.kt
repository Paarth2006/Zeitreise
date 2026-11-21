package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.Pinned
import com.paarth.zeitreise.globe.GlobeProjection

/** Pin colour (iOS system red). */
internal val PIN_COLOR = Color(0xFFFF3B30)

/** Radius of a pin's round head. */
private val PIN_HEAD_RADIUS = 9.dp

/** Width of the white ring round a pin's head. */
private val PIN_RING = 2.dp

/** Height a pin falls from as it drops onto the globe. */
private val PIN_DROP = 48.dp

/** Tap radius around a pin's head, for a 44-point touch target. */
private val PIN_TOUCH_RADIUS = 22.dp

/** Scale of the open place's pin. */
private const val SELECTED_PIN_SCALE = 1.35f

/** Height of the pin's head above the place, in head radii. */
private const val PIN_HEAD_LIFT = 1.9f

/** Half the width of the pin's point where it meets the head. */
private const val PIN_POINT_HALF_WIDTH = 0.55f

/** Where the point starts below the head's centre. */
private const val PIN_POINT_TOP = 0.6f

/** Radius of the white dot in the head. */
private const val PIN_DOT_SHARE = 0.36f

/** Size of the pin's shadow. */
private const val SHADOW_WIDTH = 1.2f
private const val SHADOW_HEIGHT = 0.4f

/** Opacity of the pin's shadow. */
private const val PIN_SHADOW_ALPHA = 0.28f

/** Delay between pins, so they land one after another. */
private const val PIN_STAGGER = 0.08f

/** Most of the drop that can be spent waiting, so even the last of many pins has time to land. */
private const val MAX_TOTAL_STAGGER = 0.6f

/** Minimum gap between two pin heads, in head radii. Closer pins are hidden until zoomed in. */
private const val PIN_SPACING = 2.4f

/** Overshoot when a pin lands, for a small bounce. */
private const val DROP_OVERSHOOT = 1.7f

/**
 * A pin placed on screen.
 *
 * @property pin the place it marks.
 * @property index its position in the list of pins, used for the drop animation.
 * @property tip where its point touches the globe, in pixels.
 */
internal class PlacedPin(val pin: Pinned, val index: Int, val tip: Offset)

/**
 * Works out which pins to show and where. Pins on the far side are skipped, and so is any pin that
 * would overlap one placed before it, so every visible pin is exactly one place. Hidden pins show up
 * once the user zooms in. [selected] is placed first so it is never hidden.
 *
 * @param minGap the smallest allowed distance between two pins, in pixels.
 */
internal fun placePins(
    pins: List<Pinned>,
    projection: GlobeProjection,
    selected: Pinned?,
    minGap: Float,
): List<PlacedPin> {
    val placed = mutableListOf<PlacedPin>()
    val order = pins.indices.sortedBy { if (pins[it] == selected) -1 else it }
    for (index in order) {
        val point = projection.project(pins[index].location) ?: continue
        val tip = Offset(point.x.toFloat(), point.y.toFloat())
        if (placed.none { (it.tip - tip).getDistance() < minGap }) placed += PlacedPin(pins[index], index, tip)
    }
    return placed
}

/**
 * Draws the pins that [placePins] lets through. Pins drop in one by one as [drop] goes from 0 to 1.
 *
 * @param selected the landmark whose card is open. Its pin is drawn bigger and on top.
 */
internal fun DrawScope.drawLandmarkPins(
    landmarks: List<Pinned>,
    projection: GlobeProjection,
    selected: Pinned?,
    drop: Float,
) {
    val placed = placePins(landmarks, projection, selected, minGap = PIN_HEAD_RADIUS.toPx() * PIN_SPACING)
    placed.forEach { pin ->
        if (pin.pin != selected) drawLandmarkPin(pin.tip, pinProgress(drop, pin.index, landmarks.size))
    }
    placed.firstOrNull { it.pin == selected }?.let { drawLandmarkPin(it.tip, progress = 1f, SELECTED_PIN_SCALE) }
}

/**
 * The pin closest to [position], or `null` if none is close enough. Only pins that are actually
 * drawn can be tapped.
 */
internal fun landmarkAt(
    position: Offset,
    landmarks: List<Pinned>,
    projection: GlobeProjection,
    selected: Pinned?,
    density: Density,
): Pinned? = with(density) {
    val lift = PIN_HEAD_RADIUS.toPx() * PIN_HEAD_LIFT
    placePins(landmarks, projection, selected, minGap = PIN_HEAD_RADIUS.toPx() * PIN_SPACING)
        .map { it.pin to (Offset(it.tip.x, it.tip.y - lift) - position).getDistance() }
        .filter { (_, distance) -> distance <= PIN_TOUCH_RADIUS.toPx() }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

/** How far pin [index] has dropped: 0 before it starts, slightly over 1 while bouncing, then 1. */
internal fun pinProgress(drop: Float, index: Int, count: Int): Float {
    // With many pins the delay shrinks, so the last one still lands before the drop ends.
    val stagger = if (count > 1) minOf(PIN_STAGGER, MAX_TOTAL_STAGGER / (count - 1)) else 0f
    val start = index * stagger
    val span = 1f - stagger * (count - 1)
    val t = ((drop - start) / span).coerceIn(0f, 1f)
    // "Back out" easing: overshoots 1 and then settles.
    val u = t - 1f
    return if (t <= 0f) 0f else 1f + (DROP_OVERSHOOT + 1f) * u * u * u + DROP_OVERSHOOT * u * u
}

private fun DrawScope.drawLandmarkPin(tip: Offset, progress: Float, scale: Float = 1f) {
    if (progress <= 0f) return
    drawPin(
        tip = tip,
        radius = PIN_HEAD_RADIUS.toPx() * scale,
        fall = (1f - progress) * PIN_DROP.toPx(),
        alpha = progress.coerceIn(0f, 1f),
    )
}

/** Draws a pin with its point at [tip], raised by [fall] pixels while dropping. */
private fun DrawScope.drawPin(tip: Offset, radius: Float, fall: Float, alpha: Float) {
    val head = Offset(tip.x, tip.y - radius * PIN_HEAD_LIFT - fall)
    drawOval(
        color = Color.Black,
        alpha = PIN_SHADOW_ALPHA * alpha,
        topLeft = Offset(tip.x - radius * SHADOW_WIDTH / 2, tip.y - radius * SHADOW_HEIGHT / 2),
        size = Size(radius * SHADOW_WIDTH, radius * SHADOW_HEIGHT),
    )
    val pointPath = Path().apply {
        moveTo(head.x - radius * PIN_POINT_HALF_WIDTH, head.y + radius * PIN_POINT_TOP)
        lineTo(tip.x, tip.y - fall)
        lineTo(head.x + radius * PIN_POINT_HALF_WIDTH, head.y + radius * PIN_POINT_TOP)
        close()
    }
    drawPath(pointPath, color = Color.White, alpha = alpha)
    drawCircle(Color.White, radius = radius + PIN_RING.toPx(), center = head, alpha = alpha)
    drawCircle(PIN_COLOR, radius = radius, center = head, alpha = alpha)
    drawCircle(Color.White, radius = radius * PIN_DOT_SHARE, center = head, alpha = alpha)
}
