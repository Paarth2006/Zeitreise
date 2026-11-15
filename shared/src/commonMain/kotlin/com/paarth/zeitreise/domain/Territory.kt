package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint

/** A named area drawn on the globe from one or more landmasses: a country, or one of its states. */
public interface Territory {
    /** The English name shown in the UI. */
    public val name: String

    /** The landmasses the territory is drawn from, largest first. */
    public val outlines: List<CountryOutline>
}

/** Whether [point] falls on any of this territory's landmasses. */
public fun Territory.contains(point: GeoPoint): Boolean = outlines.any { it.contains(point) }

/**
 * Total number of outline vertices, a cheap stand-in for the territory's size when two
 * territories both claim a tap.
 */
internal val Territory.vertexCount: Int get() = outlines.sumOf { outline -> outline.exterior.vertexCount }

/**
 * The territory [point] falls on, or `null` when it falls on none of them.
 *
 * When two territories both claim the point, the smaller one wins, so that one enclosed by
 * another, such as Lesotho inside South Africa, is chosen rather than the one around it.
 */
public fun <T : Territory> List<T>.territoryAt(point: GeoPoint): T? =
    filter { it.contains(point) }.minByOrNull { it.vertexCount }
