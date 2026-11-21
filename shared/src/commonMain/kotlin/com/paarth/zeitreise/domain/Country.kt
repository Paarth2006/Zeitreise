package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoCap
import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.PAIR_STRIDE
import com.paarth.zeitreise.globe.SPHERE_STRIDE
import com.paarth.zeitreise.globe.SpherePoint
import com.paarth.zeitreise.globe.angleTo
import com.paarth.zeitreise.globe.boundingCap
import com.paarth.zeitreise.globe.isPointInRing
import com.paarth.zeitreise.globe.ringArea
import com.paarth.zeitreise.globe.ringExtent
import com.paarth.zeitreise.globe.simplifyRing
import com.paarth.zeitreise.globe.toSpherePoint

/**
 * Tolerance, in degrees, of the coarse copy of each ring drawn at the widest zoom: about 20 km, a
 * pixel or two when the whole globe fills the screen.
 */
internal const val COARSE_TOLERANCE_DEGREES = 0.2

/** Largest share of a ring's own extent the coarse tolerance may reach, so islands keep a shape. */
private const val COARSE_SHARE_OF_EXTENT = 0.1

/**
 * How far beyond the mainland's own edge, in degrees, an island still counts as part of the view
 * of a country: about a thousand kilometres, enough for Okinawa or Corsica but not Hawaii.
 */
private const val NEARBY_LAND_DEGREES = 10.0

/**
 * The smallest a country's largest landmass may be, in square degrees (about 9,000 km², the size
 * of Jamaica), to stand out on the whole globe. Anything smaller is a speck that no finger can be
 * expected to hit, such as Luxembourg, Singapore or a chain of Pacific islands.
 */
private const val MIN_PROMINENT_AREA = 0.75

/** Fewest vertices the coarse copy needs; below this the full ring is used instead. */
private const val MIN_COARSE_VERTICES = 4

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
    internal val sphere: DoubleArray = geographic.toSphereTriples()

    /**
     * A coarser copy of [sphere], simplified to [COARSE_TOLERANCE_DEGREES], drawn when the whole
     * globe is in view and the dropped detail would be smaller than a pixel or two. A ring that
     * small already is kept as it is.
     */
    internal val coarseSphere: DoubleArray = simplifyRing(
        geographic,
        COARSE_TOLERANCE_DEGREES.coerceAtMost(geographic.ringExtent() * COARSE_SHARE_OF_EXTENT),
    ).let { coarse -> if (coarse.size / PAIR_STRIDE >= MIN_COARSE_VERTICES) coarse.toSphereTriples() else sphere }

    /** The ring's area, for ranking a country's landmasses by size. */
    internal val area: Double = geographic.ringArea()

    /** The circle on the sphere that holds the whole ring, for skipping it when it is off screen. */
    internal val bounds: GeoCap = boundingCap(sphere)

    /** Centre of [bounds] as a unit-sphere vector, which the visibility check works with. */
    internal val boundsCenter: SpherePoint = bounds.center.toSpherePoint()

    /** How many vertices the ring has. */
    public val vertexCount: Int get() = geographic.size / PAIR_STRIDE
}

/** Converts packed `longitude, latitude` pairs to packed unit-sphere triples. */
private fun DoubleArray.toSphereTriples(): DoubleArray =
    DoubleArray(size / PAIR_STRIDE * SPHERE_STRIDE).also { target ->
        var source = 0
        var index = 0
        while (source < size) {
            val point = GeoPoint(latitude = this[source + 1], longitude = this[source]).toSpherePoint()
            target[index] = point.x
            target[index + 1] = point.y
            target[index + 2] = point.z
            source += PAIR_STRIDE
            index += SPHERE_STRIDE
        }
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
 * @property continent the continent the country is counted under.
 * @property outlines the landmasses the country is drawn from, largest first.
 * @property wikidataId the Wikidata item, which leads to the country's exact Wikipedia article.
 * @property facts the country's capitals and flag, which are bundled apart from its outlines.
 */
public class Country internal constructor(
    override val name: String,
    public val isoA2: String?,
    public val continent: String,
    override val outlines: List<CountryOutline>,
    public val wikidataId: String? = null,
    public val facts: CountryFacts = CountryFacts(),
) : Territory {
    init {
        require(outlines.isNotEmpty()) { "Country $name has no outlines" }
    }

    /**
     * The region the camera frames when the country is selected: its largest landmass and the
     * islands close to it, so Japan is framed from Hokkaido to Okinawa, while a far-off territory
     * such as French Guiana does not pull the view away from France.
     */
    public val focus: GeoCap by lazy { outlines.mainlandFocus() }

    /**
     * Whether the country stands out on the whole globe, for the quiz to ask about and to be a
     * country of the day: large enough to tap, and with an ISO code, which leaves out territories
     * such as Kosovo or Somaliland whose status the standard does not settle.
     */
    internal val isProminent: Boolean get() = isoA2 != null && outlines.first().exterior.area >= MIN_PROMINENT_AREA

    /** This country with its [facts]. */
    internal fun withFacts(facts: CountryFacts): Country = Country(name, isoA2, continent, outlines, wikidataId, facts)

    override fun toString(): String = "Country($name)"
}

/**
 * What there is to know about a country besides its shape.
 *
 * @property capitals the country's capitals today, usually one; empty where there is none, as for
 *   Antarctica.
 * @property flagUrl where a picture of the country's flag can be downloaded, if it has one.
 */
public data class CountryFacts(val capitals: List<String> = emptyList(), val flagUrl: String? = null)

/**
 * The region to frame for landmasses listed largest first: the largest and those close to it, so
 * a far-off island does not pull the view away from the mainland.
 */
internal fun List<CountryOutline>.mainlandFocus(): GeoCap {
    val mainland = first().exterior
    val reach = mainland.bounds.radiusDegrees + NEARBY_LAND_DEGREES
    val framed = map { it.exterior }.filter { mainland.bounds.center.angleTo(it.bounds.center) <= reach }
    return boundingCap(framed.flatMap { it.sphere.asList() }.toDoubleArray())
}
