package com.paarth.zeitreise.ui.onboarding

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp

/** Corner radius that makes the spotlight a capsule, whatever the control's height. */
internal val CAPSULE_CORNER = Dp.Infinity

/**
 * Where a control sits on screen.
 *
 * @property bounds the control's bounds in root coordinates, in pixels.
 * @property corner the corner radius of its shape. It's capped at half the height, so
 *   [CAPSULE_CORNER] gives a capsule.
 */
internal data class TourSpot(val bounds: Rect, val corner: Dp)

/** Where each control the tour explains sits on screen, so the spotlight can find it. */
@Stable
internal class TourTargets {
    private val spots = mutableStateMapOf<TourStop, TourSpot>()

    /** Where the control for [stop] is, or `null` if it hasn't been laid out yet. */
    operator fun get(stop: TourStop): TourSpot? = spots[stop]

    /** Records where the control for [stop] is. */
    fun update(stop: TourStop, spot: TourSpot) {
        if (spots[stop] != spot) spots[stop] = spot
    }
}

/**
 * Reports where this control is to [targets], so the tour can shine its spotlight on it when it
 * reaches [stop]. Does nothing if [targets] is `null`.
 */
internal fun Modifier.tourTarget(targets: TourTargets?, stop: TourStop, corner: Dp = CAPSULE_CORNER): Modifier =
    if (targets == null) {
        this
    } else {
        onGloballyPositioned { coordinates -> targets.update(stop, TourSpot(coordinates.boundsInRoot(), corner)) }
    }
