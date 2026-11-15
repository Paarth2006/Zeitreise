package com.paarth.zeitreise.dataprep

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Natural Earth 1:110m admin-0 countries, served from the project's own GeoJSON build.
 *
 * Natural Earth is in the public domain; see https://www.naturalearthdata.com/about/terms-of-use/.
 */
private const val SOURCE_URL =
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/" +
        "ne_110m_admin_0_countries.geojson"

/** Name of the generated file inside the compose resources `files` directory. */
private const val OUTPUT_FILE = "countries.json"

/**
 * Decimal places kept for every coordinate. Three places is about 110 m at the equator, far
 * finer than the 1:110m source itself, and it roughly halves the size of the file.
 */
private const val COORDINATE_DECIMALS = 3

/** Natural Earth writes this sentinel instead of leaving a missing code blank. */
private const val MISSING_CODE = "-99"

private val JSON = Json { prettyPrint = false }

/**
 * Downloads the Natural Earth countries, keeps only the name, ISO codes, continent and geometry,
 * rounds every coordinate, and writes the result to the compose resources directory.
 *
 * The output stays a GeoJSON `FeatureCollection`, so the app parses one well-known format and no
 * data model is duplicated between this tool and the shared module.
 */
internal fun generateCountries(resources: Path) {
    println("  Downloading $SOURCE_URL")
    val source = JSON.parseToJsonElement(download(SOURCE_URL)).jsonObject
    val features = source.getValue("features").jsonArray

    val trimmed = features.mapNotNull(::trimFeature)
    val output = buildJsonObject {
        put("type", JsonPrimitive("FeatureCollection"))
        put("features", JsonArray(trimmed))
    }

    val target = resources.resolve(OUTPUT_FILE)
    target.writeText(JSON.encodeToString(JsonObject.serializer(), output))

    val vertices = trimmed.sumOf { countVertices(it.jsonObject.getValue("geometry").jsonObject) }
    println("  Wrote ${trimmed.size} countries and $vertices vertices to $target")
    println("  Size: ${target.toFile().length() / BYTES_PER_KIB} KiB")
}

/**
 * Reduces one source feature to the four properties the app uses, or returns `null` if the
 * feature carries no usable geometry.
 */
private fun trimFeature(feature: JsonElement): JsonElement? {
    val properties = feature.jsonObject.getValue("properties").jsonObject
    val geometry = feature.jsonObject.getValue("geometry")
    if (geometry is JsonNull) return null

    return buildJsonObject {
        put("type", JsonPrimitive("Feature"))
        put(
            "properties",
            buildJsonObject {
                put("name", JsonPrimitive(properties.text("NAME")))
                put("isoA2", code(properties, "ISO_A2", "ISO_A2_EH"))
                put("isoA3", code(properties, "ISO_A3", "ISO_A3_EH"))
                put("continent", JsonPrimitive(properties.text("CONTINENT")))
            },
        )
        put("geometry", roundGeometry(geometry.jsonObject))
    }
}

/**
 * Reads an ISO code, falling back to Natural Earth's `_EH` variant, which fills in codes the
 * primary field leaves as [MISSING_CODE]. Disputed territories have neither and stay null.
 */
private fun code(properties: JsonObject, primary: String, fallback: String): JsonElement {
    val value = listOf(primary, fallback)
        .map { properties.text(it) }
        .firstOrNull { it.isNotBlank() && it != MISSING_CODE }
    return value?.let(::JsonPrimitive) ?: JsonNull
}

/** Rounds every coordinate in a `Polygon` or `MultiPolygon` geometry, keeping its nesting. */
private fun roundGeometry(geometry: JsonObject): JsonObject = buildJsonObject {
    put("type", geometry.getValue("type"))
    put("coordinates", roundCoordinates(geometry.getValue("coordinates")))
}

/**
 * Walks the arbitrarily nested GeoJSON coordinate arrays and rounds the leaf numbers, so the same
 * code handles a `Polygon` and a `MultiPolygon` without knowing which it was given.
 */
private fun roundCoordinates(element: JsonElement): JsonElement = when (element) {
    is JsonArray -> buildJsonArray { element.forEach { add(roundCoordinates(it)) } }
    is JsonPrimitive -> JsonPrimitive(round(element.content.toDouble(), COORDINATE_DECIMALS))
    else -> element
}

private fun countVertices(geometry: JsonObject): Int {
    val coordinates = geometry.getValue("coordinates")
    val polygons = when (geometry.getValue("type").jsonPrimitive.content) {
        "Polygon" -> listOf(coordinates.jsonArray)
        else -> coordinates.jsonArray.map { it.jsonArray }
    }
    return polygons.sumOf { polygon -> polygon.sumOf { ring -> ring.jsonArray.size } }
}
