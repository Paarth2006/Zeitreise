package com.paarth.zeitreise.geo

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.CountryOutline
import com.paarth.zeitreise.domain.CountryRing
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.domain.vertexCount
import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.PAIR_STRIDE
import com.paarth.zeitreise.globe.simplifyRing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * How far, in degrees, a vertex may be dropped from the line its neighbours describe.
 *
 * The 1:110m source is already coarse, so this only removes near-collinear vertices: it keeps 91%
 * of them and leaves the silhouettes untouched even at the closest zoom. The draw loop is kept
 * short by back-face culling and by skipping outlines that cover less than a pixel instead.
 */
private const val SIMPLIFY_TOLERANCE_DEGREES = 0.05

/**
 * The state files were already simplified per country by the data preparation tool, so the app
 * only drops vertices that are practically on a straight line.
 */
private const val SUBDIVISION_SIMPLIFY_TOLERANCE_DEGREES = 0.0005

/** Fewest vertices a ring needs before it is worth drawing at all. */
private const val MIN_RING_VERTICES = 3

/** Parses the bundled country and state outlines. */
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

    private fun toCountry(feature: GeoJsonFeature<GeoJsonCountryProperties>): Country? {
        val outlines = feature.geometry.toOutlines(SIMPLIFY_TOLERANCE_DEGREES)
        if (outlines.isEmpty()) return null
        return Country(
            name = feature.properties.name,
            isoA2 = feature.properties.isoA2,
            isoA3 = feature.properties.isoA3,
            continent = feature.properties.continent,
            outlines = outlines,
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

    private fun GeoJsonGeometry.toOutlines(tolerance: Double): List<CountryOutline> =
        toPolygons().mapNotNull { polygon -> toOutline(polygon, tolerance) }

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
        val simplified = simplifyRing(vertices, tolerance)
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
