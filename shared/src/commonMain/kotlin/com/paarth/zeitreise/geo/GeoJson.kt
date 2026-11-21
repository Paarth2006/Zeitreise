package com.paarth.zeitreise.geo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

/** Geometry type name GeoJSON uses for a single ringed shape. */
internal const val GEOMETRY_POLYGON = "Polygon"

/** Geometry type name GeoJSON uses for a group of ringed shapes. */
internal const val GEOMETRY_MULTI_POLYGON = "MultiPolygon"

/**
 * A GeoJSON `FeatureCollection`, as bundled in `countries.json` and in each file under `states/`.
 *
 * Only the fields the app uses are declared; the data preparation tool has already stripped the
 * rest from the Natural Earth source.
 *
 * @param P the properties each feature carries.
 */
@Serializable
internal data class GeoJsonFeatureCollection<P>(val features: List<GeoJsonFeature<P>> = emptyList())

/** One GeoJSON `Feature`: a territory's properties together with its geometry. */
@Serializable
internal data class GeoJsonFeature<P>(val properties: P, val geometry: GeoJsonGeometry)

/** The country properties the data preparation tool keeps. */
@Serializable
internal data class GeoJsonCountryProperties(
    val name: String,
    @SerialName("isoA2") val isoA2: String? = null,
    val continent: String = "",
    val wikidataId: String? = null,
)

/** The state and province properties the data preparation tool keeps. */
@Serializable
internal data class GeoJsonSubdivisionProperties(
    val name: String,
    val kind: String = "",
    val isoCode: String? = null,
    val wikidataId: String? = null,
    val labelLatitude: Double,
    val labelLongitude: Double,
)

/**
 * The properties of a historical-basemaps feature. A feature without a name is land no realm
 * held that year.
 */
@Serializable
internal data class GeoJsonRealmProperties(
    @SerialName("NAME") val name: String? = null,
    @SerialName("PARTOF") val partOf: String? = null,
)

/**
 * A GeoJSON geometry.
 *
 * `coordinates` is kept as a raw [JsonArray] because a `Polygon` nests its numbers one level less
 * deeply than a `MultiPolygon`, and declaring both shapes as one type would need a custom
 * serializer for no gain. [GeoJsonParser] flattens it.
 */
@Serializable
internal data class GeoJsonGeometry(val type: String, val coordinates: JsonArray)
