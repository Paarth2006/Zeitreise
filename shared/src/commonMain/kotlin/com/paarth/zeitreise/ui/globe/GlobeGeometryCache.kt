package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.CountryOutline
import com.paarth.zeitreise.domain.CountryRing
import com.paarth.zeitreise.domain.Territory
import com.paarth.zeitreise.globe.GlobeProjection
import com.paarth.zeitreise.globe.PAIR_STRIDE

/**
 * Smallest screen extent, in pixels, an outline must cover before it is worth drawing. Below this
 * an island is a sub-pixel speck, and skipping it removes most of the vertices at the widest zoom.
 */
private const val MIN_DRAWN_EXTENT_PIXELS = 1.5f

/** Fewest packed values a ring needs to describe a triangle. */
private const val MIN_RING_VALUES = 6

/**
 * Re-projects one landmass each frame into a reusable [Path].
 *
 * Every buffer and the path itself are allocated once, when the globe loads. Turning the globe
 * then only rewrites their contents, so a frame allocates nothing.
 */
internal class OutlineGeometry(outline: CountryOutline) {
    private val rings: List<CountryRing> = buildList {
        add(outline.exterior)
        addAll(outline.holes)
    }
    private val buffers: List<FloatArray> = rings.map { FloatArray(it.vertexCount * PAIR_STRIDE) }

    /** The projected outline, with holes cut out of it. Only valid after [update] returned `true`. */
    val path: Path = Path().apply { fillType = PathFillType.EvenOdd }

    /**
     * Re-projects the landmass for [projection].
     *
     * @return whether the result is worth drawing, which it is not when the landmass faces away
     *   from the viewer or is smaller than [MIN_DRAWN_EXTENT_PIXELS] on screen.
     */
    fun update(projection: GlobeProjection): Boolean {
        val exterior = buffers[0]
        val facesViewer = projection.projectOutline(rings[0].sphere, exterior) > 0
        if (!facesViewer || !coversAPixel(exterior)) return false

        for (index in 1 until rings.size) {
            projection.projectOutline(rings[index].sphere, buffers[index])
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

/** Latitude past which a country's main landmass is drawn as ice: Greenland, not Russia. */
private const val ICE_LATITUDE = 70.0

/** Continent whose every landmass is drawn as ice. */
private const val ANTARCTICA = "Antarctica"

/**
 * Re-projects every landmass of one territory, a country or a state, each frame.
 *
 * @param isIce whether the territory is polar and is drawn as ice rather than land.
 */
internal class TerritoryGeometry(val territory: Territory, val isIce: Boolean = false) {
    /**
     * Which of the palette's land shades the territory is filled with. Taken from its name, so it
     * stays the same between launches and neighbours rarely share one.
     */
    val shade: Int = territory.name.hashCode()

    private val outlines: List<OutlineGeometry> = territory.outlines.map(::OutlineGeometry)
    private val drawable = ArrayList<Path>(territory.outlines.size)

    /**
     * Re-projects the territory for [projection].
     *
     * @return the paths to draw, reusing the same list every frame. Empty when nothing of the
     *   territory is visible.
     */
    fun update(projection: GlobeProjection): List<Path> {
        drawable.clear()
        outlines.forEach { outline -> if (outline.update(projection)) drawable.add(outline.path) }
        return drawable
    }
}

/**
 * The projected geometry of the whole globe, held across frames.
 *
 * Remember one of these per country list; it owns every buffer the draw loop writes into.
 */
internal class GlobeGeometryCache(countries: List<Country>) {
    /** Countries in draw order, largest first, so small neighbours end up on top. */
    val countries: List<TerritoryGeometry> = countries.map { country ->
        TerritoryGeometry(country, isIce = country.isPolar())
    }

    /** The graticule, re-projected alongside the countries. */
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

/** Whether the country is polar enough to be drawn as ice. */
private fun Country.isPolar(): Boolean = continent == ANTARCTICA || focus.center.latitude > ICE_LATITUDE
