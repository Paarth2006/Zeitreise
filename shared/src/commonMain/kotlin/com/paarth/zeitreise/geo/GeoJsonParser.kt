package com.paarth.zeitreise.geo

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.CountryOutline
import com.paarth.zeitreise.domain.CountryRing
import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.domain.Realm
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.domain.vertexCount
import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.PAIR_STRIDE
import com.paarth.zeitreise.globe.ringExtent
import com.paarth.zeitreise.globe.simplifyRing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * How far, in degrees, a vertex may be dropped from the line its neighbours describe.
 *
 * The 1:50m source carries detail no one can see on the whole globe; this keeps about a third of
 * its vertices, which still draws crisp coastlines when a country fills the screen. Outlines that
 * cover less than a pixel, which most small islands do at the widest zoom, are skipped as well.
 */
private const val SIMPLIFY_TOLERANCE_DEGREES = 0.05

/**
 * The state files were already simplified per country by the data preparation tool, so the app
 * only drops vertices that are practically on a straight line.
 */
private const val SUBDIVISION_SIMPLIFY_TOLERANCE_DEGREES = 0.0005

/**
 * Largest share of a ring's own extent its simplification tolerance may reach, so that a country
 * smaller than the usual tolerance, such as Monaco, keeps its shape instead of collapsing.
 */
private const val SMALL_RING_TOLERANCE_SHARE = 0.05

/** Fewest vertices a ring needs before it is worth drawing at all. */
private const val MIN_RING_VERTICES = 3

/** Parses the bundled country and state outlines, and the historical world maps. */
internal class GeoJsonParser(private val json: Json = Json { ignoreUnknownKeys = true }) {
    /**
     * Reads a GeoJSON `FeatureCollection` of countries from [source].
     *
     * Outlines are simplified as they are read, and countries are returned largest first so that
     * the renderer draws big countries beneath their smaller neighbours.
     *
     * @throws kotlinx.serialization.SerializationException if [source] is not the expected GeoJSON.
     */
    fun parseCountries(source: String): List<Country> =
        json.decodeFromString(GeoJsonFeatureCollection.serializer(GeoJsonCountryProperties.serializer()), source)
            .features
            .mapNotNull(::toCountry)
            .sortedByDescending { it.vertexCount }

    /**
     * Reads a GeoJSON `FeatureCollection` of one country's states and provinces from [source],
     * largest first, as [parseCountries] does for countries.
     *
     * @throws kotlinx.serialization.SerializationException if [source] is not the expected GeoJSON.
     */
    fun parseSubdivisions(source: String): List<Subdivision> =
        json.decodeFromString(GeoJsonFeatureCollection.serializer(GeoJsonSubdivisionProperties.serializer()), source)
            .features
            .mapNotNull(::toSubdivision)
            .sortedByDescending { it.vertexCount }

    /**
     * Reads one year's world map from [source], a historical-basemaps `FeatureCollection`.
     *
     * Features that share a name, such as an empire's scattered lands, become one realm; features
     * without a name become unclaimed land.
     *
     * @throws kotlinx.serialization.SerializationException if [source] is not the expected GeoJSON.
     */
    fun parseHistoricalMap(year: Int, source: String): HistoricalMap {
        val features = json.decodeFromString(
            GeoJsonFeatureCollection.serializer(GeoJsonRealmProperties.serializer()),
            source,
        ).features
        val (named, unnamed) = features.partition { !it.properties.name.isNullOrBlank() }
        val realms = named
            .groupBy { requireNotNull(it.properties.name).trim() }
            .mapNotNull { (name, parts) ->
                val outlines = parts.flatMap { it.geometry.toOutlines(SIMPLIFY_TOLERANCE_DEGREES) }
                    .sortedByDescending { it.exterior.area }
                val partOf = parts.firstNotNullOfOrNull { it.properties.partOf?.trim() }?.takeIf { it != name }
                if (outlines.isEmpty()) null else Realm(name, partOf, outlines)
            }
            .sortedByDescending { it.vertexCount }
        return HistoricalMap(
            year = year,
            realms = realms,
            unclaimedLand = unnamed.flatMap { it.geometry.toOutlines(SIMPLIFY_TOLERANCE_DEGREES) },
        )
    }

    private fun toCountry(feature: GeoJsonFeature<GeoJsonCountryProperties>): Country? {
        val outlines = feature.geometry.toOutlines(SIMPLIFY_TOLERANCE_DEGREES)
        if (outlines.isEmpty()) return null
        return Country(
            name = feature.properties.name,
            isoA2 = feature.properties.isoA2,
            isoA3 = feature.properties.isoA3,
            continent = feature.properties.continent,
            outlines = outlines,
            wikidataId = feature.properties.wikidataId,
        )
    }

    private fun toSubdivision(feature: GeoJsonFeature<GeoJsonSubdivisionProperties>): Subdivision? {
        val outlines = feature.geometry.toOutlines(SUBDIVISION_SIMPLIFY_TOLERANCE_DEGREES)
        if (outlines.isEmpty()) return null
        val properties = feature.properties
        return Subdivision(
            name = properties.name,
            kind = properties.kind,
            isoCode = properties.isoCode,
            wikidataId = properties.wikidataId,
            labelPoint = GeoPoint(latitude = properties.labelLatitude, longitude = properties.labelLongitude),
            outlines = outlines,
        )
    }

    /**
     * The landmasses of a geometry, largest first: the source lists them in no particular order,
     * and the camera frames a country by its first, which must be the mainland.
     */
    private fun GeoJsonGeometry.toOutlines(tolerance: Double): List<CountryOutline> = toPolygons()
        .mapNotNull { polygon -> toOutline(polygon, tolerance) }
        .sortedByDescending { it.exterior.area }

    private fun toOutline(polygon: JsonArray, tolerance: Double): CountryOutline? {
        val rings = polygon.mapNotNull { ring -> toRing(ring.jsonArray, tolerance) }
        val exterior = rings.firstOrNull() ?: return null
        return CountryOutline(exterior = exterior, holes = rings.drop(1))
    }

    private fun toRing(ring: JsonArray, tolerance: Double): CountryRing? {
        val vertices = DoubleArray(ring.size * PAIR_STRIDE)
        ring.forEachIndexed { index, position ->
            val pair = position.jsonArray
            vertices[index * PAIR_STRIDE] = pair[0].jsonPrimitive.double
            vertices[index * PAIR_STRIDE + 1] = pair[1].jsonPrimitive.double
        }
        val simplified = simplifyRing(
            vertices,
            tolerance.coerceAtMost(
                vertices.ringExtent() * SMALL_RING_TOLERANCE_SHARE,
            ),
        )
        if (simplified.size / PAIR_STRIDE < MIN_RING_VERTICES) return null
        return CountryRing(simplified)
    }
}

/**
 * Flattens a geometry to a list of polygons, each a list of rings, so that a `Polygon` and a
 * `MultiPolygon` are handled by the same code.
 */
private fun GeoJsonGeometry.toPolygons(): List<JsonArray> = when (type) {
    GEOMETRY_POLYGON -> listOf(coordinates)
    GEOMETRY_MULTI_POLYGON -> coordinates.map { it.jsonArray }
    else -> emptyList()
}
