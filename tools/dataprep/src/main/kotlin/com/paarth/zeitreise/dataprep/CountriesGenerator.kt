package com.paarth.zeitreise.dataprep

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Natural Earth 1:50m admin-0 countries, as GeoJSON from Natural Earth's repository on GitHub.
 *
 * It's the 1:50m scale rather than 1:110m, because the coarser map leaves out every country too
 * small to see on a world map, from Monaco and Singapore to Malta and the Pacific island states.
 *
 * Natural Earth is in the public domain; see https://www.naturalearthdata.com/about/terms-of-use/.
 */
private const val SOURCE_URL =
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/" +
        "ne_50m_admin_0_countries.geojson"

/** Name of the generated file inside the compose resources `files` directory. */
private const val OUTPUT_FILE = "countries.json"

/**
 * Decimal places kept for every coordinate. Three places is about 110 m at the equator, finer
 * than the 1:50m source itself, and it roughly halves the size of the file.
 */
private const val COORDINATE_DECIMALS = 3

/**
 * Common English names for the few countries whose Natural Earth long name is a formal title.
 * Every other country takes its long name, which spells out what the short name abbreviates.
 */
private val NAME_OVERRIDES = mapOf(
    "Kingdom of eSwatini" to "Eswatini",
    "Republic of Cabo Verde" to "Cabo Verde",
    "Vatican" to "Vatican City",
)

/**
 * How far, in degrees, a vertex may stray from its neighbours' line and still be dropped: about
 * 2 km, below what the app keeps anyway, so the file shrinks without any visible change.
 */
private const val SIMPLIFY_TOLERANCE_DEGREES = 0.02

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
 * Reduces one source feature to the properties the app uses, or returns `null` if the
 * feature carries no usable geometry.
 */
private fun trimFeature(feature: JsonElement): JsonElement? {
    val properties = feature.jsonObject.getValue("properties").jsonObject
    val geometry = (feature.jsonObject.getValue("geometry") as? JsonObject)
        ?.let { simplifiedGeometry(it, SIMPLIFY_TOLERANCE_DEGREES, COORDINATE_DECIMALS) }
        ?: return null

    return buildJsonObject {
        put("type", JsonPrimitive("Feature"))
        put(
            "properties",
            buildJsonObject {
                val longName = properties.text("NAME_LONG").ifBlank { properties.text("NAME") }
                put("name", JsonPrimitive(NAME_OVERRIDES[longName] ?: longName))
                put("isoA2", code(properties, "ISO_A2", "ISO_A2_EH"))
                put("isoA3", code(properties, "ISO_A3", "ISO_A3_EH"))
                put("continent", JsonPrimitive(properties.text("CONTINENT")))
                put(
                    "wikidataId",
                    properties.text("WIKIDATAID").takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull,
                )
            },
        )
        put("geometry", geometry)
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

private fun countVertices(geometry: JsonObject): Int {
    val coordinates = geometry.getValue("coordinates")
    val polygons = when (geometry.getValue("type").jsonPrimitive.content) {
        "Polygon" -> listOf(coordinates.jsonArray)
        else -> coordinates.jsonArray.map { it.jsonArray }
    }
    return polygons.sumOf { polygon -> polygon.sumOf { ring -> ring.jsonArray.size } }
}
