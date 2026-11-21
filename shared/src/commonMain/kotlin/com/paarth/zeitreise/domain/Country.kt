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

/** Tolerance for the coarse copy drawn at the widest zoom: about 20 km, a pixel or two on screen. */
internal const val COARSE_TOLERANCE_DEGREES = 0.2

/** Caps the coarse tolerance relative to a ring's size, so small islands keep their shape. */
private const val COARSE_SHARE_OF_EXTENT = 0.1

/** How far from the mainland, in degrees, an island is still framed with it (Okinawa yes, Hawaii no). */
private const val NEARBY_LAND_DEGREES = 10.0

/**
 * Minimum area of a country's largest landmass, in square degrees (about 9,000 km², roughly
 * Jamaica). Anything smaller, like Luxembourg or Singapore, is too small to tap on the whole globe.
 */
private const val MIN_PROMINENT_AREA = 0.75

/** If the coarse copy has fewer vertices than this, the full ring is used instead. */
private const val MIN_COARSE_VERTICES = 4

/**
 * One closed outline of a coastline or border.
 *
 * It is stored twice: in degrees for hit testing, and as unit-sphere vectors for drawing. Both are
 * flat arrays, so turning the globe doesn't allocate anything.
 *
 * @param geographic packed `longitude, latitude` pairs.
 */
public class CountryRing internal constructor(internal val geographic: DoubleArray) {
    /** Packed `x, y, z` unit-sphere triples, converted once at load time. */
    internal val sphere: DoubleArray = geographic.toSphereTriples()

    /** A simplified copy of [sphere], drawn when the whole globe is in view. */
    internal val coarseSphere: DoubleArray = simplifyRing(
        geographic,
        COARSE_TOLERANCE_DEGREES.coerceAtMost(geographic.ringExtent() * COARSE_SHARE_OF_EXTENT),
    ).let { coarse -> if (coarse.size / PAIR_STRIDE >= MIN_COARSE_VERTICES) coarse.toSphereTriples() else sphere }

    /** The ring's area, used to sort landmasses by size. */
    internal val area: Double = geographic.ringArea()

    /** A circle around the whole ring, used to skip it when it is off screen. */
    internal val bounds: GeoCap = boundingCap(sphere)

    /** Centre of [bounds] as a unit-sphere vector. */
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
 * One landmass of a territory: its outline and any holes cut out of it.
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
 * A country on the globe.
 *
 * @property name the English name, shown in the UI and sent to the guide.
 * @property isoA2 two-letter ISO 3166-1 code, or `null` if it has none. Used to find its states file.
 * @property continent the continent it belongs to.
 * @property outlines its landmasses, largest first.
 * @property wikidataId its Wikidata item, used to find the right Wikipedia article.
 * @property facts its capitals and flag.
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
     * The region the camera frames for this country: the mainland and nearby islands. This way
     * Japan includes Okinawa, but France isn't zoomed out to include French Guiana.
     */
    public val focus: GeoCap by lazy { outlines.mainlandFocus() }

    /**
     * Whether the country is big enough to tap on the whole globe and has an ISO code. Used to pick
     * quiz questions and the country of the day.
     */
    internal val isProminent: Boolean get() = isoA2 != null && outlines.first().exterior.area >= MIN_PROMINENT_AREA

    /** This country with its [facts]. */
    internal fun withFacts(facts: CountryFacts): Country = Country(name, isoA2, continent, outlines, wikidataId, facts)

    override fun toString(): String = "Country($name)"
}

/**
 * Extra facts about a country.
 *
 * @property capitals its current capitals, usually one, or empty (Antarctica has none).
 * @property flagUrl address of its flag image, if it has one.
 */
public data class CountryFacts(val capitals: List<String> = emptyList(), val flagUrl: String? = null)

/** The region covering the largest landmass and the ones near it. Expects landmasses largest first. */
internal fun List<CountryOutline>.mainlandFocus(): GeoCap {
    val mainland = first().exterior
    val reach = mainland.bounds.radiusDegrees + NEARBY_LAND_DEGREES
    val framed = map { it.exterior }.filter { mainland.bounds.center.angleTo(it.bounds.center) <= reach }
    return boundingCap(framed.flatMap { it.sphere.asList() }.toDoubleArray())
}
