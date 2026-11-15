package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoCap
import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.PAIR_STRIDE
import com.paarth.zeitreise.globe.SPHERE_STRIDE
import com.paarth.zeitreise.globe.boundingCap
import com.paarth.zeitreise.globe.isPointInRing
import com.paarth.zeitreise.globe.toSpherePoint

/**
 * One closed outline of a territory's coastline or border.
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
 * A single landmass of a territory: its coastline, plus any rings cut out of it.
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
 *   emoji and the file of the country's states are found through it.
 * @property isoA3 three-letter ISO 3166-1 code, or `null` for territories that have none.
 * @property continent the continent the country is counted under.
 * @property outlines the landmasses the country is drawn from, largest first.
 */
public class Country internal constructor(
    override val name: String,
    public val isoA2: String?,
    public val isoA3: String?,
    public val continent: String,
    override val outlines: List<CountryOutline>,
) : Territory {
    init {
        require(outlines.isNotEmpty()) { "Country $name has no outlines" }
    }

    /**
     * The region the camera frames when the country is selected: its largest landmass, so that
     * an outlying island does not pull the view away from the mainland.
     */
    public val focus: GeoCap by lazy { boundingCap(outlines.first().exterior.sphere) }

    override fun toString(): String = "Country($name)"
}
