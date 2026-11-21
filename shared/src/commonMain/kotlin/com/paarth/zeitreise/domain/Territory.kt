package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint

/** A named area on the globe, either a country or a state. */
public interface Territory {
    /** The English name. */
    public val name: String

    /** Its landmasses, largest first. */
    public val outlines: List<CountryOutline>
}

/** Whether [point] falls on any of this territory's landmasses. */
public fun Territory.contains(point: GeoPoint): Boolean = outlines.any { it.contains(point) }

/** Total number of vertices, used as a cheap measure of size. */
internal val Territory.vertexCount: Int get() = outlines.sumOf { outline -> outline.exterior.vertexCount }

/**
 * The territory that contains [point], or `null` if none does.
 *
 * If two territories contain it, the smaller wins, so Lesotho is picked instead of South Africa.
 */
public fun <T : Territory> List<T>.territoryAt(point: GeoPoint): T? =
    filter { it.contains(point) }.minByOrNull { it.vertexCount }
