package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.PAIR_STRIDE
import com.paarth.zeitreise.globe.SPHERE_STRIDE
import com.paarth.zeitreise.globe.isPointInRing
import com.paarth.zeitreise.globe.toSpherePoint

/**
 * One closed outline of a country's coastline or border.
 *
 * Each ring is held twice: once in geographic degrees for hit testing, which the camera never
 * affects, and once as unit-sphere vectors for drawing, which the renderer only has to rotate.
 * Both are packed into flat arrays so that turning the globe allocates nothing.
 *
 * @param geographic packed `longitude, latitude` pairs.
 */
public class CountryRing internal constructor(internal val geographic: DoubleArray) {
    /** Packed `x, y, z` unit-sphere triples, converted once when the country is loaded. */
    internal val sphere: DoubleArray = DoubleArray(geographic.size / PAIR_STRIDE * SPHERE_STRIDE).also { target ->
        var source = 0
        var index = 0
        while (source < geographic.size) {
            val point = GeoPoint(latitude = geographic[source + 1], longitude = geographic[source]).toSpherePoint()
            target[index] = point.x
            target[index + 1] = point.y
            target[index + 2] = point.z
            source += PAIR_STRIDE
            index += SPHERE_STRIDE
        }
    }

    /** How many vertices the ring has. */
    public val vertexCount: Int get() = geographic.size / PAIR_STRIDE
}

/**
 * A single landmass of a country: its coastline, plus any rings cut out of it.
 *
 * @property exterior the outer boundary.
 * @property holes boundaries of enclaves that are not part of the country.
 */
public class CountryOutline internal constructor(
    public val exterior: CountryRing,
    public val holes: List<CountryRing>,
) {
    /** Whether [point] falls on this landmass and not inside one of its [holes]. */
    public fun contains(point: GeoPoint): Boolean =
        isPointInRing(point, exterior.geographic) && holes.none { isPointInRing(point, it.geographic) }
}

/**
 * A country as the app draws and talks about it.
 *
 * @property name the English name, as shown in the UI and sent to the AI guide.
 * @property isoA2 two-letter ISO 3166-1 code, or `null` for territories that have none. The flag
 *   emoji is derived from it.
 * @property isoA3 three-letter ISO 3166-1 code, or `null` for territories that have none.
 * @property continent the continent the country is counted under.
 * @property outlines the landmasses the country is drawn from, largest first.
 */
public class Country internal constructor(
    public val name: String,
    public val isoA2: String?,
    public val isoA3: String?,
    public val continent: String,
    public val outlines: List<CountryOutline>,
) {
    /**
     * Total number of outline vertices, used as a cheap stand-in for the country's size when two
     * countries both claim a tap.
     */
    internal val vertexCount: Int = outlines.sumOf { outline -> outline.exterior.vertexCount }

    /** Whether [point] falls on any of this country's landmasses. */
    public fun contains(point: GeoPoint): Boolean = outlines.any { it.contains(point) }

    override fun toString(): String = "Country($name)"
}

/**
 * The country [point] falls on, or `null` for open ocean.
 *
 * When two countries both claim the point, the smaller one wins, so that a country enclosed by
 * another, such as Lesotho inside South Africa, is selected rather than the country around it.
 */
public fun List<Country>.countryAt(point: GeoPoint): Country? {
    var hit: Country? = null
    forEach { country ->
        if (country.contains(point) && (hit == null || country.vertexCount < hit.vertexCount)) {
            hit = country
        }
    }
    return hit
}
