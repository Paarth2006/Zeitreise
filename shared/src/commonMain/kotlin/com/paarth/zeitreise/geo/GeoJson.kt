package com.paarth.zeitreise.geo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

/** GeoJSON type name for a single polygon. */
internal const val GEOMETRY_POLYGON = "Polygon"

/** GeoJSON type name for a group of polygons. */
internal const val GEOMETRY_MULTI_POLYGON = "MultiPolygon"

/**
 * A GeoJSON `FeatureCollection`, like `countries.json` and the files under `states/`.
 * Only the fields the app needs are declared.
 *
 * @param P the properties each feature carries.
 */
@Serializable
internal data class GeoJsonFeatureCollection<P>(val features: List<GeoJsonFeature<P>> = emptyList())

/** One GeoJSON `Feature`: a territory's properties and its geometry. */
@Serializable
internal data class GeoJsonFeature<P>(val properties: P, val geometry: GeoJsonGeometry)

/** Properties of a country feature. */
@Serializable
internal data class GeoJsonCountryProperties(
    val name: String,
    @SerialName("isoA2") val isoA2: String? = null,
    val continent: String = "",
    val wikidataId: String? = null,
)

/** Properties of a state or province feature. */
@Serializable
internal data class GeoJsonSubdivisionProperties(
    val name: String,
    val kind: String = "",
    val isoCode: String? = null,
    val wikidataId: String? = null,
    val labelLatitude: Double,
    val labelLongitude: Double,
)

/** Properties of a historical map feature. A feature without a name is unclaimed land. */
@Serializable
internal data class GeoJsonRealmProperties(
    @SerialName("NAME") val name: String? = null,
    @SerialName("PARTOF") val partOf: String? = null,
)

/**
 * A GeoJSON geometry.
 *
 * `coordinates` stays a raw [JsonArray] because `Polygon` and `MultiPolygon` nest differently.
 * [GeoJsonParser] flattens it.
 */
@Serializable
internal data class GeoJsonGeometry(val type: String, val coordinates: JsonArray)
