package com.paarth.zeitreise.globe

import kotlin.test.Test
import kotlin.test.assertEquals

private const val TOLERANCE = 1e-9

class GlobeCameraTest {

    @Test
    fun startsLookingAtTheOriginFullyZoomedOut() {
        val camera = GlobeCamera()
        assertEquals(0.0, camera.yaw, TOLERANCE)
        assertEquals(0.0, camera.pitch, TOLERANCE)
        assertEquals(MIN_ZOOM, camera.zoom, TOLERANCE)
    }

    @Test
    fun wrapsYawPastTheAntimeridianWhenRotating() {
        val camera = GlobeCamera(yaw = 170.0).rotatedBy(deltaYaw = 20.0, deltaPitch = 0.0)
        assertEquals(-170.0, camera.yaw, TOLERANCE)
    }

    @Test
    fun clampsPitchShortOfThePoles() {
        assertEquals(MAX_PITCH_DEGREES, GlobeCamera().rotatedBy(0.0, 200.0).pitch, TOLERANCE)
        assertEquals(-MAX_PITCH_DEGREES, GlobeCamera().rotatedBy(0.0, -200.0).pitch, TOLERANCE)
    }

    @Test
    fun clampsZoomToItsLimits() {
        assertEquals(MAX_ZOOM, GlobeCamera().zoomedBy(100.0).zoom, TOLERANCE)
        assertEquals(MIN_ZOOM, GlobeCamera().zoomedBy(0.001).zoom, TOLERANCE)
    }

    @Test
    fun multipliesSuccessiveZooms() {
        assertEquals(4.0, GlobeCamera().zoomedBy(2.0).zoomedBy(2.0).zoom, TOLERANCE)
    }

    @Test
    fun reportsTheCenterItLooksAt() {
        val center = GlobeCamera(yaw = 12.5, pitch = -40.0).center
        assertEquals(-40.0, center.latitude, TOLERANCE)
        assertEquals(12.5, center.longitude, TOLERANCE)
    }

    @Test
    fun pointsAtATargetAndClampsItsLatitude() {
        val camera = GlobeCamera(zoom = 3.0).pointingAt(GeoPoint(latitude = 89.0, longitude = -5.0))
        assertEquals(-5.0, camera.yaw, TOLERANCE)
        assertEquals(MAX_PITCH_DEGREES, camera.pitch, TOLERANCE)
        assertEquals(3.0, camera.zoom, TOLERANCE)
    }

    @Test
    fun turnsTheShorterWayAcrossTheAntimeridian() {
        assertEquals(20.0, GlobeCamera.shortestTurn(fromYaw = 170.0, toYaw = -170.0), TOLERANCE)
        assertEquals(-20.0, GlobeCamera.shortestTurn(fromYaw = -170.0, toYaw = 170.0), TOLERANCE)
        assertEquals(90.0, GlobeCamera.shortestTurn(fromYaw = 0.0, toYaw = 90.0), TOLERANCE)
        assertEquals(0.0, GlobeCamera.shortestTurn(fromYaw = 45.0, toYaw = 45.0), TOLERANCE)
    }

    @Test
    fun interpolatesAcrossTheAntimeridianRatherThanAroundTheGlobe() {
        val start = GlobeCamera(yaw = 170.0)
        val target = GlobeCamera(yaw = -170.0)
        assertEquals(180.0, start.interpolatedTo(target, fraction = 0.5).yaw, TOLERANCE)
    }

    @Test
    fun interpolatesPitchAndZoomTowardsTheTarget() {
        val start = GlobeCamera(pitch = 0.0, zoom = 1.0)
        val target = GlobeCamera(pitch = 40.0, zoom = 3.0)
        val halfway = start.interpolatedTo(target, fraction = 0.5)
        assertEquals(20.0, halfway.pitch, TOLERANCE)
        assertEquals(2.0, halfway.zoom, TOLERANCE)
    }

    @Test
    fun clampsTheInterpolationFractionToItsEnds() {
        val start = GlobeCamera(yaw = 10.0)
        val target = GlobeCamera(yaw = 50.0)
        assertEquals(10.0, start.interpolatedTo(target, fraction = -1.0).yaw, TOLERANCE)
        assertEquals(50.0, start.interpolatedTo(target, fraction = 2.0).yaw, TOLERANCE)
    }
}
