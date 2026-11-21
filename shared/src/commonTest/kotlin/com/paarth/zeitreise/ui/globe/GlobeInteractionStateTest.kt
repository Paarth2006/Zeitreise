package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Velocity
import com.paarth.zeitreise.globe.GeoCap
import com.paarth.zeitreise.globe.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A short moment, in seconds, during which the rewind spin is still fast. */
private const val MOMENT = 0.05f

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

    @Test
    fun rewindSpinsTheGlobeBackwardsAndThenSettles() {
        state.rewind()
        assertTrue(state.isMoving)

        state.advance(seconds = MOMENT, idle = false)
        // The drift turns the yaw up from 0, so a rewind turns it below 0.
        assertTrue(state.camera.yaw < 0.0)

        state.advance(seconds = LONG_ENOUGH, idle = false)
        assertFalse(state.isMoving)
    }

    @Test
    fun rewindWaitsForAFlightToFinish() {
        state.flyToFrame(PERU)
        state.rewind()
        state.advance(seconds = LONG_ENOUGH, idle = false)
        // The flight has landed on Peru, and the spin is still to come.
        assertTrue(state.isMoving)

        state.advance(seconds = MOMENT, idle = false)
        assertNotEquals(PERU.center.longitude, state.camera.yaw)
    }

    @Test
    fun startsFacingTheGivenPoint() {
        val home = GeoPoint(latitude = 20.0, longitude = 78.0)

        val camera = GlobeInteractionState(start = home).camera

        assertEquals(home, camera.center)
    }

    @Test
    fun zoomsOutTurnedTowardsTheGivenPoint() {
        val home = GeoPoint(latitude = 52.0, longitude = 10.0)
        state.flyToFrame(PERU)
        state.advance(seconds = LONG_ENOUGH, idle = false)

        state.flyToWholeGlobe(facing = home)
        state.advance(seconds = LONG_ENOUGH, idle = false)

        val wholeGlobe = GlobeInteractionState(start = home).camera
        assertEquals(home, state.camera.center)
        assertEquals(wholeGlobe.zoom, state.camera.zoom)
    }
}
