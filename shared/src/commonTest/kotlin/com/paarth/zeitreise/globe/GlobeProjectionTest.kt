package com.paarth.zeitreise.globe

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TOLERANCE = 1e-6

/** A 400 by 400 viewport with a globe of radius 100 at its centre. */
private fun projection(camera: GlobeCamera = GlobeCamera()) = GlobeProjection(
    camera = camera,
    centerX = 200.0,
    centerY = 200.0,
    baseRadius = 100.0,
)

class GlobeProjectionTest {

    @Test
    fun scalesTheRadiusWithZoom() {
        assertEquals(100.0, projection().scaledRadius, TOLERANCE)
        assertEquals(300.0, projection(GlobeCamera(zoom = 3.0)).scaledRadius, TOLERANCE)
    }

    @Test
    fun projectsTheCameraCenterToTheCenterOfTheScreen() {
        val projected = assertNotNull(projection().project(GeoPoint(latitude = 0.0, longitude = 0.0)))
        assertEquals(200.0, projected.x, TOLERANCE)
        assertEquals(200.0, projected.y, TOLERANCE)
    }

    @Test
    fun projectsEastToTheRightAndNorthUpwards() {
        val east = assertNotNull(projection().project(GeoPoint(latitude = 0.0, longitude = 90.0)))
        assertEquals(300.0, east.x, TOLERANCE)
        assertEquals(200.0, east.y, TOLERANCE)

        val north = assertNotNull(projection().project(GeoPoint(latitude = 90.0, longitude = 0.0)))
        assertEquals(200.0, north.x, TOLERANCE)
        assertEquals(100.0, north.y, TOLERANCE)
    }

    @Test
    fun hidesPointsOnTheFarSideOfTheGlobe() {
        assertNull(projection().project(GeoPoint(latitude = 0.0, longitude = 180.0)))
        assertNull(projection().project(GeoPoint(latitude = 0.0, longitude = -120.0)))
    }

    @Test
    fun treatsTheHorizonAsVisible() {
        assertTrue(projection().isVisible(GeoPoint(latitude = 0.0, longitude = 90.0)))
        assertTrue(projection().isVisible(GeoPoint(latitude = 0.0, longitude = -90.0)))
    }

    @Test
    fun followsTheCameraWhenItTurns() {
        val turned = projection(GlobeCamera(yaw = 90.0))
        val centered = assertNotNull(turned.project(GeoPoint(latitude = 0.0, longitude = 90.0)))
        assertEquals(200.0, centered.x, TOLERANCE)
        assertEquals(200.0, centered.y, TOLERANCE)
        assertNull(turned.project(GeoPoint(latitude = 0.0, longitude = 0.0 - 91.0)))
    }

    @Test
    fun unprojectsTheCenterOfTheScreenToTheCameraCenter() {
        val camera = GlobeCamera(yaw = 25.0, pitch = -15.0)
        val point = assertNotNull(projection(camera).unproject(200.0, 200.0))
        assertEquals(-15.0, point.latitude, TOLERANCE)
        assertEquals(25.0, point.longitude, TOLERANCE)
    }

    @Test
    fun unprojectsNothingOutsideTheGlobe() {
        assertNull(projection().unproject(200.0, 60.0))
        assertNull(projection().unproject(0.0, 0.0))
    }

    @Test
    fun unprojectIsTheInverseOfProject() {
        val camera = GlobeCamera(yaw = 42.0, pitch = 18.0, zoom = 2.5)
        val projection = projection(camera)
        val points = listOf(
            GeoPoint(latitude = 18.0, longitude = 42.0),
            GeoPoint(latitude = 51.5, longitude = -0.12),
            GeoPoint(latitude = -10.0, longitude = 70.0),
            GeoPoint(latitude = 60.0, longitude = 20.0),
        )
        points.forEach { point ->
            val projected = assertNotNull(projection.project(point), "$point should be visible")
            val roundTripped = assertNotNull(projection.unproject(projected.x, projected.y))
            assertTrue(abs(roundTripped.latitude - point.latitude) < 1e-4, "$point -> $roundTripped")
            assertTrue(abs(roundTripped.longitude - point.longitude) < 1e-4, "$point -> $roundTripped")
        }
    }

    @Test
    fun projectsAWholeOutlineAndCountsTheVisibleVertices() {
        // Two vertices in front of the camera, one behind it.
        val sphere = sphereOf(
            GeoPoint(latitude = 0.0, longitude = 0.0),
            GeoPoint(latitude = 0.0, longitude = 45.0),
            GeoPoint(latitude = 0.0, longitude = 180.0),
        )
        val screen = FloatArray(3 * PAIR_STRIDE)
        assertEquals(2, projection().projectOutline(sphere, screen))
        assertEquals(200f, screen[0], 1e-3f)
        assertEquals(200f, screen[1], 1e-3f)
    }

    @Test
    fun slidesHiddenVerticesOntoTheLimb() {
        val sphere = sphereOf(GeoPoint(latitude = 0.0, longitude = 150.0))
        val screen = FloatArray(PAIR_STRIDE)
        assertEquals(0, projection().projectOutline(sphere, screen))

        val distance = kotlin.math.hypot(screen[0] - 200f, screen[1] - 200f)
        assertEquals(100f, distance, 1e-3f)
    }

    @Test
    fun recordsWhichVerticesFaceTheViewer() {
        val sphere = sphereOf(
            GeoPoint(latitude = 0.0, longitude = 0.0),
            GeoPoint(latitude = 0.0, longitude = 170.0),
        )
        val screen = FloatArray(2 * PAIR_STRIDE)
        val facing = BooleanArray(2)
        projection().projectOutline(sphere, screen, facing)
        assertTrue(facing[0])
        assertTrue(!facing[1])
    }

    @Test
    fun refusesAScreenBufferThatIsTooSmall() {
        val sphere = sphereOf(GeoPoint(latitude = 0.0, longitude = 0.0))
        assertFailsWith<IllegalArgumentException> { projection().projectOutline(sphere, FloatArray(1)) }
    }
}

private fun sphereOf(vararg points: GeoPoint): DoubleArray {
    val packed = DoubleArray(points.size * SPHERE_STRIDE)
    points.forEachIndexed { index, point ->
        val sphere = point.toSpherePoint()
        packed[index * SPHERE_STRIDE] = sphere.x
        packed[index * SPHERE_STRIDE + 1] = sphere.y
        packed[index * SPHERE_STRIDE + 2] = sphere.z
    }
    return packed
}
