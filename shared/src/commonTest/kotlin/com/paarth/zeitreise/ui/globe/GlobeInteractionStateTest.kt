package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Velocity
import com.paarth.zeitreise.globe.GeoCap
import com.paarth.zeitreise.globe.GeoPoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Longer than any flight or fling takes to settle, in seconds. */
private const val LONG_ENOUGH = 5f

private val PERU = GeoCap(GeoPoint(latitude = -9.0, longitude = -75.0), radiusDegrees = 10.0)

class GlobeInteractionStateTest {

    private val state = GlobeInteractionState().apply { viewport = Size(1_000f, 2_000f) }

    @Test
    fun startsStill() {
        assertFalse(state.isMoving)
    }

    @Test
    fun movesForAFlightAndThenSettles() {
        state.flyToFrame(PERU)
        assertTrue(state.isMoving)

        state.advance(seconds = LONG_ENOUGH, idle = false)

        assertFalse(state.isMoving)
    }

    @Test
    fun keepsSpinningAfterAFlingUntilItDiesDown() {
        state.onReleased(Velocity(x = 2_000f, y = 0f))
        assertTrue(state.isMoving)

        state.advance(seconds = LONG_ENOUGH, idle = false)

        assertFalse(state.isMoving)
    }

    @Test
    fun stopsAtOnceWhenTheUserTakesHold() {
        state.flyToFrame(PERU)

        state.onPressed()

        assertFalse(state.isMoving)
    }

    @Test
    fun aDragLetGoWithoutSpeedLeavesTheGlobeStill() {
        state.onReleased(Velocity.Zero)

        assertFalse(state.isMoving)
    }
}
