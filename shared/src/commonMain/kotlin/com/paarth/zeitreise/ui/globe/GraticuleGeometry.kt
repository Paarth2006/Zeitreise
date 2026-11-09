package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.graphics.Path
import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.GlobeProjection
import com.paarth.zeitreise.globe.PAIR_STRIDE
import com.paarth.zeitreise.globe.SPHERE_STRIDE
import com.paarth.zeitreise.globe.toSpherePoint

/** Degrees between the meridians and parallels of the graticule. */
private const val GRID_SPACING_DEGREES = 30

/** Degrees between the vertices along one graticule line, fine enough to look like a curve. */
private const val GRID_STEP_DEGREES = 3

/** Latitude the meridians stop at, short of the poles where they would all converge. */
private const val GRID_MAX_LATITUDE = 84

/** Latitude the outermost parallel is drawn at. */
private const val GRID_MAX_PARALLEL = 60

/** Degrees of longitude from the prime meridian to the antimeridian. */
private const val HALF_TURN_DEGREES = 180

/**
 * The meridians and parallels drawn over the ocean.
 *
 * The lines are open rather than closed, so each one is broken wherever it crosses the horizon
 * instead of being dragged along the limb.
 */
internal class GraticuleGeometry {
    private val lines: List<DoubleArray> = buildGraticuleLines()
    private val screens: List<FloatArray> = lines.map { FloatArray(it.size / SPHERE_STRIDE * PAIR_STRIDE) }
    private val facings: List<BooleanArray> = lines.map { BooleanArray(it.size / SPHERE_STRIDE) }

    /** The projected graticule. Only valid after [update] has been called for the same frame. */
    val path: Path = Path()

    /** Re-projects the graticule for [projection]. */
    fun update(projection: GlobeProjection) {
        path.rewind()
        lines.forEachIndexed { index, line ->
            val screen = screens[index]
            val facing = facings[index]
            if (projection.projectOutline(line, screen, facing) == 0) return@forEachIndexed
            appendVisibleRuns(screen, facing)
        }
    }

    /** Appends each unbroken run of front-facing vertices as its own sub-path. */
    private fun appendVisibleRuns(screen: FloatArray, facing: BooleanArray) {
        var started = false
        facing.forEachIndexed { vertex, isFacing ->
            if (!isFacing) {
                started = false
                return@forEachIndexed
            }
            val x = screen[vertex * PAIR_STRIDE]
            val y = screen[vertex * PAIR_STRIDE + 1]
            if (started) path.lineTo(x, y) else path.moveTo(x, y)
            started = true
        }
    }
}

/** Builds the meridians and parallels once, as packed unit-sphere triples. */
private fun buildGraticuleLines(): List<DoubleArray> = buildList {
    for (longitude in -HALF_TURN_DEGREES until HALF_TURN_DEGREES step GRID_SPACING_DEGREES) {
        add(
            sphereLine(
                (-GRID_MAX_LATITUDE..GRID_MAX_LATITUDE step GRID_STEP_DEGREES).map { latitude ->
                    GeoPoint(latitude = latitude.toDouble(), longitude = longitude.toDouble())
                },
            ),
        )
    }
    for (latitude in -GRID_MAX_PARALLEL..GRID_MAX_PARALLEL step GRID_SPACING_DEGREES) {
        add(
            sphereLine(
                (-HALF_TURN_DEGREES..HALF_TURN_DEGREES step GRID_STEP_DEGREES).map { longitude ->
                    GeoPoint(latitude = latitude.toDouble(), longitude = longitude.toDouble())
                },
            ),
        )
    }
}

private fun sphereLine(points: List<GeoPoint>): DoubleArray {
    val packed = DoubleArray(points.size * SPHERE_STRIDE)
    points.forEachIndexed { index, point ->
        val sphere = point.toSpherePoint()
        packed[index * SPHERE_STRIDE] = sphere.x
        packed[index * SPHERE_STRIDE + 1] = sphere.y
        packed[index * SPHERE_STRIDE + 2] = sphere.z
    }
    return packed
}
