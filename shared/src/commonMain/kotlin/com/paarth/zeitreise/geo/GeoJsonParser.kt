package com.paarth.zeitreise.geo

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.CountryOutline
import com.paarth.zeitreise.domain.CountryRing
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

/** Fewest vertices a ring needs before it is worth drawing at all. */
private const val MIN_RING_VERTICES = 3

/** Parses the bundled country outlines. */
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
        json.decodeFromString(GeoJsonFeatureCollection.serializer(), source)
            .features
            .mapNotNull(::toCountry)
            .sortedByDescending { country -> country.outlines.sumOf { it.exterior.vertexCount } }

    private fun toCountry(feature: GeoJsonFeature): Country? {
        val outlines = feature.geometry.toPolygons().mapNotNull(::toOutline)
        if (outlines.isEmpty()) return null
        return Country(
            name = feature.properties.name,
            isoA2 = feature.properties.isoA2,
            isoA3 = feature.properties.isoA3,
            continent = feature.properties.continent,
            outlines = outlines,
        )
    }

    private fun toOutline(polygon: JsonArray): CountryOutline? {
        val rings = polygon.mapNotNull { ring -> toRing(ring.jsonArray) }
        val exterior = rings.firstOrNull() ?: return null
        return CountryOutline(exterior = exterior, holes = rings.drop(1))
    }

    private fun toRing(ring: JsonArray): CountryRing? {
        val vertices = DoubleArray(ring.size * PAIR_STRIDE)
        ring.forEachIndexed { index, position ->
            val pair = position.jsonArray
            vertices[index * PAIR_STRIDE] = pair[0].jsonPrimitive.double
            vertices[index * PAIR_STRIDE + 1] = pair[1].jsonPrimitive.double
        }
        val simplified = simplifyRing(vertices, SIMPLIFY_TOLERANCE_DEGREES)
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
