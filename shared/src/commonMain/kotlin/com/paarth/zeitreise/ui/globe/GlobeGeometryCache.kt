package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import com.paarth.zeitreise.domain.COARSE_TOLERANCE_DEGREES
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.CountryOutline
import com.paarth.zeitreise.domain.CountryRing
import com.paarth.zeitreise.domain.Territory
import com.paarth.zeitreise.globe.GlobeProjection
import com.paarth.zeitreise.globe.PAIR_STRIDE
import com.paarth.zeitreise.globe.SPHERE_STRIDE

/** Outlines smaller than this on screen, in pixels, are skipped. */
private const val MIN_DRAWN_EXTENT_PIXELS = 1.5f

/** Minimum packed values for a triangle. */
private const val MIN_RING_VALUES = 6

/** Above this error in pixels, the full outline is drawn instead of the coarse copy. */
private const val MAX_COARSE_ERROR_PIXELS = 2.5

/**
 * Projects one landmass into a reusable [Path] every frame.
 *
 * All buffers are allocated once, so drawing a frame allocates nothing. Off-screen landmasses are
 * skipped early, and the coarse outline is used until the user zooms in.
 */
internal class OutlineGeometry(outline: CountryOutline) {
    private val rings: List<CountryRing> = buildList {
        add(outline.exterior)
        addAll(outline.holes)
    }
    private val fineBuffers: List<FloatArray> = rings.map { FloatArray(it.sphere.size / SPHERE_STRIDE * PAIR_STRIDE) }
    private val coarseBuffers: List<FloatArray> = rings.map {
        FloatArray(it.coarseSphere.size / SPHERE_STRIDE * PAIR_STRIDE)
    }

    /** The projected outline with its holes. Only valid after [update] returned `true`. */
    val path: Path = Path().apply { fillType = PathFillType.EvenOdd }

    /**
     * Re-projects the landmass for [projection].
     *
     * @return `false` if the landmass is off screen, on the far side or too small to see.
     */
    fun update(projection: GlobeProjection): Boolean {
        val exterior = rings[0]
        val coarse = COARSE_TOLERANCE_DEGREES * projection.pixelsPerDegree <= MAX_COARSE_ERROR_PIXELS
        val buffers = if (coarse) coarseBuffers else fineBuffers
        // Cheapest checks first, so projection only runs for landmasses that may be visible.
        val shown = projection.mayShow(exterior.boundsCenter, exterior.bounds.radiusDegrees) &&
            projection.projectOutline(exterior.vertices(coarse), buffers[0]) > 0 &&
            coversAPixel(buffers[0])
        if (!shown) return false

        for (index in 1 until rings.size) {
            projection.projectOutline(rings[index].vertices(coarse), buffers[index])
        }

        path.rewind()
        buffers.forEach { appendClosedRing(path, it) }
        return true
    }

    private fun coversAPixel(screen: FloatArray): Boolean {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var index = 0
        while (index < screen.size) {
            val x = screen[index]
            val y = screen[index + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            index += PAIR_STRIDE
        }
        return maxX - minX >= MIN_DRAWN_EXTENT_PIXELS || maxY - minY >= MIN_DRAWN_EXTENT_PIXELS
    }
}

/**
 * Greenland's ISO code. Greenland and Antarctica are drawn as ice. This is checked by code, not by
 * latitude, because Russia's centre is just as far north.
 */
private const val GREENLAND = "GL"

/** Continent drawn completely as ice. */
private const val ANTARCTICA = "Antarctica"

/**
 * Projects every landmass of one country or state each frame.
 *
 * @param isIce whether to draw it as ice instead of land.
 */
internal class TerritoryGeometry(val territory: Territory, val isIce: Boolean = false) {
    /** Which land shade to use, based on the name so it stays the same between launches. */
    val shade: Int = territory.name.hashCode()

    private val outlines: List<OutlineGeometry> = territory.outlines.map(::OutlineGeometry)
    private val drawable = ArrayList<Path>(territory.outlines.size)

    /**
     * Re-projects the territory for [projection].
     *
     * @return the paths to draw (the same list is reused every frame), or empty if nothing is visible.
     */
    fun update(projection: GlobeProjection): List<Path> {
        drawable.clear()
        outlines.forEach { outline -> if (outline.update(projection)) drawable.add(outline.path) }
        return drawable
    }
}

/** The projected geometry of the whole globe, reused across frames. */
internal class GlobeGeometryCache(countries: List<Country>) {
    /** Countries in draw order, largest first, so small ones are drawn on top. */
    val countries: List<TerritoryGeometry> = countries.map { country ->
        TerritoryGeometry(country, isIce = country.isPolar())
    }

    /** The graticule lines. */
    val graticule: GraticuleGeometry = GraticuleGeometry()
}

/** Appends a closed ring of packed `x, y` screen pairs to [path]. */
private fun appendClosedRing(path: Path, screen: FloatArray) {
    if (screen.size < MIN_RING_VALUES) return
    path.moveTo(screen[0], screen[1])
    var index = PAIR_STRIDE
    while (index < screen.size) {
        path.lineTo(screen[index], screen[index + 1])
        index += PAIR_STRIDE
    }
    path.close()
}

/** Whether the country should be drawn as ice. */
private fun Country.isPolar(): Boolean = continent == ANTARCTICA || isoA2 == GREENLAND

/** The vertices to draw: the coarse copy or the full outline. */
private fun CountryRing.vertices(coarse: Boolean): DoubleArray = if (coarse) coarseSphere else sphere
