package com.paarth.zeitreise.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The dark layer over everything except the control being explained. */
private val SCRIM = Color.Black.copy(alpha = 0.6f)

/** Space between the control and the edge of the spotlight around it. */
private val SPOT_PADDING = 6.dp

/** Width of the pulsing ring around the spotlight, and how long one pulse takes. */
private val RING_WIDTH = 3.dp
private const val RING_PULSE_MILLIS = 800

/** The ring fades between this opacity and fully opaque while it pulses. */
private const val RING_MIN_ALPHA = 0.35f

/** Size of the arrow from the card to the control, and the gap between its tip and the spotlight. */
private val ARROW_WIDTH = 22.dp
private val ARROW_HEIGHT = 11.dp
private val ARROW_GAP = 6.dp

/** The arrow is drawn pointing up and turned this far to point down. */
private const val HALF_TURN_DEGREES = 180f

/** Close to the colour of the glass card, so the arrow looks like part of it. */
private val ARROW_COLOR = Color.White.copy(alpha = 0.85f)

/** Smallest gap between the card and the edges of the safe area, and the card's maximum width. */
private val SCREEN_MARGIN = 16.dp
private val CARD_MAX_WIDTH = 360.dp

/**
 * The guided tour: dims the screen except for the control being explained, and shows the tour card
 * next to it with an arrow pointing at it. It also blocks taps, so nothing opens over the tour.
 *
 * @param step the current tour step.
 * @param targets where the controls are on screen.
 * @param onNext called when the user taps Next.
 * @param onSkip called when the user taps Skip tour.
 * @param modifier applied to the root element.
 */
@Composable
internal fun TourOverlay(
    step: OnboardingStep.Tour,
    targets: TourTargets,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val padding = with(LocalDensity.current) { SPOT_PADDING.toPx() }
    val spot = targets[step.stop]
    val hole = spot?.bounds?.inflate(padding)
    // The spotlight glides from one control to the next.
    val movingHole by animateRectAsState(hole ?: Rect.Zero, spring(stiffness = Spring.StiffnessMediumLow))
    val corner = spot?.let {
        with(LocalDensity.current) { min(it.corner.toPx(), it.bounds.height / 2) + padding }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClick = {}),
    ) {
        Spotlight(hole = hole?.let { movingHole }, corner = corner ?: 0f)
        AnimatedContent(targetState = step, transitionSpec = { fadeIn() togetherWith fadeOut() }) { shown ->
            AnchoredCard(hole = targets[shown.stop]?.bounds?.inflate(padding)) {
                TourCard(step = shown, onNext = onNext, onSkip = onSkip)
            }
        }
    }
}

/** Dims the screen except for a rounded [hole], which gets a pulsing ring. With no hole it dims everything. */
@Composable
private fun Spotlight(hole: Rect?, corner: Float) {
    val pulse = rememberInfiniteTransition()
    val ringAlpha by pulse.animateFloat(
        initialValue = RING_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RING_PULSE_MILLIS), RepeatMode.Reverse),
    )
    val ringColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (hole == null) {
            drawRect(SCRIM)
            return@Canvas
        }
        val cutout = Path().apply { addRoundRect(RoundRect(hole, CornerRadius(corner))) }
        // Even-odd filling leaves the cutout uncovered.
        val scrim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(size.toRect())
            addPath(cutout)
        }
        drawPath(scrim, SCRIM)
        drawPath(cutout, ringColor.copy(alpha = ringAlpha), style = Stroke(RING_WIDTH.toPx()))
    }
}

/**
 * Places [card] next to [hole]: below it if the hole is in the top half of the screen, otherwise
 * above it, with an arrow pointing at it. With no hole the card is centred.
 */
@Composable
private fun AnchoredCard(hole: Rect?, card: @Composable () -> Unit) {
    val insets = WindowInsets.safeDrawing
    Layout(content = {
        card()
        Arrow()
    }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = SCREEN_MARGIN.roundToPx()
        val left = insets.getLeft(this, layoutDirection) + margin
        val right = constraints.maxWidth - insets.getRight(this, layoutDirection) - margin
        val top = insets.getTop(this) + margin
        val bottom = constraints.maxHeight - insets.getBottom(this) - margin
        val cardWidth = max(0, min(CARD_MAX_WIDTH.roundToPx(), right - left))
        val cardPlaceable = measurables[0].measure(Constraints(maxWidth = cardWidth, maxHeight = max(0, bottom - top)))
        val arrow = measurables[1].measure(Constraints.fixed(ARROW_WIDTH.roundToPx(), ARROW_HEIGHT.roundToPx()))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (hole == null) {
                cardPlaceable.place(
                    x = (constraints.maxWidth - cardPlaceable.width) / 2,
                    y = (constraints.maxHeight - cardPlaceable.height) / 2,
                )
                return@layout
            }
            val below = hole.center.y < constraints.maxHeight / 2f
            val reach = ARROW_GAP.roundToPx() + arrow.height
            val wantedY = if (below) {
                hole.bottom.roundToInt() + reach
            } else {
                hole.top.roundToInt() - reach - cardPlaceable.height
            }
            val centredX = hole.center.x.roundToInt() - cardPlaceable.width / 2
            val x = centredX.coerceIn(left, max(left, right - cardPlaceable.width))
            val y = wantedY.coerceIn(top, max(top, bottom - cardPlaceable.height))
            cardPlaceable.place(x, y)
            // The arrow overlaps the card by a pixel, so no seam shows between them.
            val arrowY = if (below) y - arrow.height + 1 else y + cardPlaceable.height - 1
            arrow.placeWithLayer(hole.center.x.roundToInt() - arrow.width / 2, arrowY) {
                rotationZ = if (below) 0f else HALF_TURN_DEGREES
            }
        }
    }
}

/** A small triangle pointing up. It's turned around when the card is above the control. */
@Composable
private fun Arrow() {
    Canvas(modifier = Modifier) {
        val triangle = Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width / 2, 0f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(triangle, ARROW_COLOR)
    }
}
