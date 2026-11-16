package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.Landmark
import com.paarth.zeitreise.globe.GlobeProjection

/** Apple's system red, the colour of a dropped pin in Apple Maps. */
internal val PIN_COLOR = Color(0xFFFF3B30)

/** Radius of a pin's round head. */
private val PIN_HEAD_RADIUS = 9.dp

/** Width of the white ring round a pin's head. */
private val PIN_RING = 2.dp

/** Height a pin falls from as it drops onto the globe. */
private val PIN_DROP = 48.dp

/** How far a finger may land from a pin's head and still open it: a 44-point target. */
private val PIN_TOUCH_RADIUS = 22.dp

/** How much larger the open place's pin is drawn. */
private const val SELECTED_PIN_SCALE = 1.35f

/** How far above the place a pin's head floats, as multiples of its radius: the length of its point. */
private const val PIN_HEAD_LIFT = 1.9f

/** Half the width of a pin's point where it meets the head, as a share of the head's radius. */
private const val PIN_POINT_HALF_WIDTH = 0.55f

/** How far below the head's centre its point begins, as a share of the head's radius. */
private const val PIN_POINT_TOP = 0.6f

/** Radius of the white dot in the middle of a pin's head, as a share of the head's radius. */
private const val PIN_DOT_SHARE = 0.36f

/** Width and height of the shadow a pin casts on the globe, as shares of the head's radius. */
private const val SHADOW_WIDTH = 1.2f
private const val SHADOW_HEIGHT = 0.4f

/** Opacity of the soft shadow a pin casts on the globe. */
private const val PIN_SHADOW_ALPHA = 0.28f

/** Share of the drop each pin waits for after the one before it, so they land one by one. */
private const val PIN_STAGGER = 0.08f

/** Overshoot of the drop's landing: a little bounce, as pins have when they land in Apple Maps. */
private const val DROP_OVERSHOOT = 1.7f

/**
 * Draws a pin for each of [landmarks] that faces the viewer, dropping them in one after another as
 * [drop] runs from 0 to 1.
 *
 * @param selected the landmark whose card is open, whose pin is drawn larger and on top.
 */
internal fun DrawScope.drawLandmarkPins(
    landmarks: List<Landmark>,
    projection: GlobeProjection,
    selected: Landmark?,
    drop: Float,
) {
    landmarks.forEachIndexed { index, landmark ->
        if (landmark != selected) drawLandmarkPin(landmark, projection, pinProgress(drop, index, landmarks.size))
    }
    selected?.let { drawLandmarkPin(it, projection, progress = 1f, scale = SELECTED_PIN_SCALE) }
}

/**
 * The landmark whose pin head is nearest [position], or `null` when none is within a finger's
 * reach of it.
 */
internal fun landmarkAt(
    position: Offset,
    landmarks: List<Landmark>,
    projection: GlobeProjection,
    density: Density,
): Landmark? = with(density) {
    landmarks
        .mapNotNull { landmark ->
            projection.project(landmark.location)?.let { point ->
                val head = Offset(point.x.toFloat(), point.y.toFloat() - PIN_HEAD_RADIUS.toPx() * PIN_HEAD_LIFT)
                landmark to (head - position).getDistance()
            }
        }
        .filter { (_, distance) -> distance <= PIN_TOUCH_RADIUS.toPx() }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

/**
 * How far pin [index] of [count] has dropped when the whole drop is at [drop]: 0 before it starts
 * falling, a little past 1 as it bounces on landing, and 1 once it rests.
 */
internal fun pinProgress(drop: Float, index: Int, count: Int): Float {
    val start = index * PIN_STAGGER
    val span = (1f - PIN_STAGGER * (count - 1)).coerceAtLeast(PIN_STAGGER)
    val t = ((drop - start) / span).coerceIn(0f, 1f)
    // An ease-out that overshoots ("back out"): it passes 1, then settles back onto it.
    val u = t - 1f
    return if (t <= 0f) 0f else 1f + (DROP_OVERSHOOT + 1f) * u * u * u + DROP_OVERSHOOT * u * u
}

private fun DrawScope.drawLandmarkPin(
    landmark: Landmark,
    projection: GlobeProjection,
    progress: Float,
    scale: Float = 1f,
) {
    val point = projection.project(landmark.location) ?: return
    if (progress <= 0f) return
    drawPin(
        tip = Offset(point.x.toFloat(), point.y.toFloat()),
        radius = PIN_HEAD_RADIUS.toPx() * scale,
        fall = (1f - progress) * PIN_DROP.toPx(),
        alpha = progress.coerceIn(0f, 1f),
    )
}

/** A teardrop pin whose point touches [tip], with its head [fall] pixels higher while it drops. */
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
