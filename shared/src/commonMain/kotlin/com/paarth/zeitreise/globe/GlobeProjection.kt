package com.paarth.zeitreise.globe

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Number of components a [SpherePoint] occupies in a packed coordinate array. */
internal const val SPHERE_STRIDE: Int = 3

/** Number of components one coordinate pair occupies in a packed array. */
internal const val PAIR_STRIDE: Int = 2

/**
 * A point of the globe projected onto the screen.
 *
 * @property x horizontal screen position in pixels.
 * @property y vertical screen position in pixels.
 */
public data class ProjectedPoint(val x: Double, val y: Double)

/**
 * Orthographic projection of the globe for one camera and viewport.
 *
 * The visible hemisphere is drawn as a disc of [scaledRadius] pixels and the far side is hidden.
 * It is immutable and cheap to create, so a new one is made whenever the camera or viewport changes.
 *
 * @param camera orientation and magnification to project with.
 * @property centerX horizontal screen position of the centre of the globe, in pixels.
 * @property centerY vertical screen position of the centre of the globe, in pixels.
 * @property baseRadius radius of the globe at [MIN_ZOOM], in pixels.
 */
public class GlobeProjection(
    camera: GlobeCamera,
    public val centerX: Double,
    public val centerY: Double,
    public val baseRadius: Double,
) {
    /** Radius of the globe at the camera's zoom level, in pixels. */
    public val scaledRadius: Double = baseRadius * camera.zoom

    /** How many pixels one degree of arc covers at the centre of the view. */
    internal val pixelsPerDegree: Double = scaledRadius * PI / DEGREES_PER_HALF_TURN

    private val sinYaw = sin(camera.yaw.toRadians())
    private val cosYaw = cos(camera.yaw.toRadians())
    private val sinPitch = sin(camera.pitch.toRadians())
    private val cosPitch = cos(camera.pitch.toRadians())

    /** Angle in radians from the centre of the view to the farthest point still on screen. */
    private val visibleAngle: Double = run {
        val reach = sqrt(centerX * centerX + centerY * centerY) / scaledRadius
        if (reach >= 1.0) PI / 2 else asin(reach)
    }

    /**
     * Whether any part of a region within [radiusDegrees] of [center] could be on screen. Only the
     * bounding circle is checked, so off-screen outlines are skipped before projecting anything.
     */
    internal fun mayShow(center: SpherePoint, radiusDegrees: Double): Boolean {
        val angleFromView = acos(depthOf(center).coerceIn(-1.0, 1.0))
        return angleFromView <= visibleAngle + radiusDegrees.toRadians()
    }

    /** Whether [point] lies on the hemisphere facing the viewer. */
    public fun isVisible(point: GeoPoint): Boolean = depthOf(point.toSpherePoint()) >= 0.0

    /** Projects [point] onto the screen, or returns `null` if it is on the far side of the globe. */
    public fun project(point: GeoPoint): ProjectedPoint? {
        val sphere = point.toSpherePoint()
        if (depthOf(sphere) < 0.0) return null
        val rotatedX = sphere.x * cosYaw - sphere.z * sinYaw
        val forwardZ = sphere.x * sinYaw + sphere.z * cosYaw
        val rotatedY = sphere.y * cosPitch - forwardZ * sinPitch
        return ProjectedPoint(
            x = centerX + rotatedX * scaledRadius,
            y = centerY - rotatedY * scaledRadius,
        )
    }

    /**
     * Projects a whole outline in one pass, writing screen coordinates into [screen].
     *
     * Hidden vertices are moved onto the edge of the disc instead of being dropped, so an outline
     * that crosses the horizon still closes along the edge.
     *
     * @param sphere packed `x, y, z` triples on the unit sphere, as produced at load time.
     * @param screen packed `x, y` screen pairs to write; must hold `sphere.size / 3 * 2` values.
     * @param facing optional per-vertex flags, set when the vertex faces the viewer. Open lines like
     *   the graticule use them to stop at the horizon.
     * @return how many vertices face the viewer, so a fully hidden outline can be skipped.
     */
    internal fun projectOutline(sphere: DoubleArray, screen: FloatArray, facing: BooleanArray? = null): Int {
        require(screen.size >= sphere.size / SPHERE_STRIDE * PAIR_STRIDE) {
            "screen holds ${screen.size} values, which is too few for ${sphere.size} sphere values"
        }
        var visible = 0
        var source = 0
        var target = 0
        var vertex = 0
        while (source < sphere.size) {
            val x = sphere[source]
            val y = sphere[source + 1]
            val z = sphere[source + 2]

            val rotatedX = x * cosYaw - z * sinYaw
            val forwardZ = x * sinYaw + z * cosYaw
            val rotatedY = y * cosPitch - forwardZ * sinPitch
            val depth = y * sinPitch + forwardZ * cosPitch

            var planeX = rotatedX
            var planeY = rotatedY
            if (depth < 0.0) {
                // Move the hidden vertex onto the edge of the disc.
                val length = sqrt(planeX * planeX + planeY * planeY)
                if (length > 0.0) {
                    planeX /= length
                    planeY /= length
                } else {
                    planeX = 1.0
                }
            } else {
                visible++
            }
            facing?.set(vertex, depth >= 0.0)

            screen[target] = (centerX + planeX * scaledRadius).toFloat()
            screen[target + 1] = (centerY - planeY * scaledRadius).toFloat()
            source += SPHERE_STRIDE
            target += PAIR_STRIDE
            vertex++
        }
        return visible
    }

    /** Converts a screen position back to a point on the globe, or `null` if it misses the globe. */
    public fun unproject(screenX: Double, screenY: Double): GeoPoint? {
        val planeX = (screenX - centerX) / scaledRadius
        val planeY = (centerY - screenY) / scaledRadius
        val distance = sqrt(planeX * planeX + planeY * planeY)
        if (distance > 1.0) return null

        val angle = asin(distance)
        val sinAngle = sin(angle)
        val cosAngle = cos(angle)
        // At the exact centre distance is zero, so divide by one instead to avoid a NaN.
        val divisor = if (distance == 0.0) 1.0 else distance
        val latitude = asin(cosAngle * sinPitch + planeY * sinAngle * cosPitch / divisor)
        val longitude = yawDegrees().toRadians() + atan2(
            planeX * sinAngle,
            distance * cosAngle * cosPitch - planeY * sinAngle * sinPitch,
        )
        return GeoPoint(
            latitude = latitude.toDegrees().coerceIn(-MAX_LATITUDE, MAX_LATITUDE),
            longitude = normalizeLongitude(longitude.toDegrees()),
        )
    }

    private fun depthOf(sphere: SpherePoint): Double {
        val forwardZ = sphere.x * sinYaw + sphere.z * cosYaw
        return sphere.y * sinPitch + forwardZ * cosPitch
    }

    private fun yawDegrees(): Double = atan2(sinYaw, cosYaw).toDegrees()
}
