package com.paarth.zeitreise.geo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

/** Geometry type name GeoJSON uses for a single ringed shape. */
internal const val GEOMETRY_POLYGON = "Polygon"

/** Geometry type name GeoJSON uses for a group of ringed shapes. */
internal const val GEOMETRY_MULTI_POLYGON = "MultiPolygon"

/**
 * A GeoJSON `FeatureCollection`, as bundled in `countries.json`.
 *
 * Only the fields the app uses are declared; the data preparation tool has already stripped the
 * rest from the Natural Earth source.
 */
@Serializable
internal data class GeoJsonFeatureCollection(val features: List<GeoJsonFeature> = emptyList())

/** One GeoJSON `Feature`: a country's properties together with its geometry. */
@Serializable
internal data class GeoJsonFeature(val properties: GeoJsonCountryProperties, val geometry: GeoJsonGeometry)

/** The four country properties the data preparation tool keeps. */
@Serializable
internal data class GeoJsonCountryProperties(
    val name: String,
    @SerialName("isoA2") val isoA2: String? = null,
    @SerialName("isoA3") val isoA3: String? = null,
    val continent: String = "",
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
