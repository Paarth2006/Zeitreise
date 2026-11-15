package com.paarth.zeitreise.dataprep

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText

/**
 * Natural Earth 1:10m admin-1 states and provinces. Public domain, like the countries.
 *
 * The 1:10m scale is the only one that covers every country's states; it is simplified per
 * country below, so the bundled files stay small.
 */
private const val SOURCE_URL =
    "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/" +
        "ne_10m_admin_1_states_provinces.geojson"

/** Directory, inside the compose resources `files` directory, holding one file per country. */
private const val OUTPUT_DIRECTORY = "states"

/**
 * File, inside [OUTPUT_DIRECTORY], listing every state's name and country, so search can find a
 * state without opening every country's file.
 */
private const val INDEX_FILE = "index.json"

/**
 * Share of a country's north-south extent that a dropped vertex may stray from its neighbours'
 * line. Latitude rather than longitude, because a country crossing the antimeridian, such as the
 * United States with the Aleutians, would otherwise look 360 degrees wide.
 */
private const val TOLERANCE_PER_DEGREE_OF_LATITUDE = 0.002

/** Finest and coarsest simplification tolerances, in degrees. */
private const val MIN_TOLERANCE = 0.0005
private const val MAX_TOLERANCE = 0.03

/** Decimal places kept per coordinate: about 110 m, finer than a pixel even when Luxembourg fills the screen. */
private const val COORDINATE_DECIMALS = 3

/** Natural Earth marks a missing country code with a negative number. */
private const val MISSING_CODE_PREFIX = "-"

/** Values per packed `longitude, latitude` pair. */
private const val PAIR = 2

private val JSON = Json { prettyPrint = false }

/**
 * Downloads the Natural Earth states and provinces and writes one GeoJSON file per country, named
 * after its ISO 3166-1 alpha-2 code, so the app loads only the country the user opens.
 *
 * Each state keeps its English name, its type ("State", "Province", ...), its ISO 3166-2 code, its
 * Wikidata ID, which later resolves its exact Wikipedia article, and the point Natural Earth
 * places its label at.
 */
internal fun generateStates(resources: Path) {
    println("  Downloading $SOURCE_URL (about 40 MB)")
    val features = JSON.parseToJsonElement(download(SOURCE_URL)).jsonObject.getValue("features").jsonArray
    val byCountry = features
        .map { it.jsonObject }
        .filter { it["geometry"] !is JsonNull }
        .groupBy { it.getValue("properties").jsonObject.text("iso_a2") }
        .filterKeys { code -> code.length == PAIR && !code.startsWith(MISSING_CODE_PREFIX) }

    val directory = resources.resolve(OUTPUT_DIRECTORY).createDirectories()
    directory.listDirectoryEntries("*.json").forEach { it.deleteIfExists() }

    var totalBytes = 0L
    val index = buildJsonArray {
        byCountry.forEach { (code, states) ->
            states.forEach { state ->
                val properties = state.getValue("properties").jsonObject
                add(
                    buildJsonObject {
                        put("name", JsonPrimitive(properties.text("name_en").ifBlank { properties.text("name") }))
                        put("country", JsonPrimitive(code))
                    },
                )
            }
        }
    }
    directory.resolve(INDEX_FILE).writeText(JSON.encodeToString(JsonArray.serializer(), index))

    byCountry.forEach { (code, states) ->
        val tolerance = toleranceFor(states)
        val output = buildJsonObject {
            put("type", JsonPrimitive("FeatureCollection"))
            put("features", JsonArray(states.mapNotNull { trimState(it, tolerance) }))
        }
        val target = directory.resolve("$code.json")
        target.writeText(JSON.encodeToString(JsonObject.serializer(), output))
        totalBytes += target.toFile().length()
    }
    println("  Wrote ${byCountry.size} countries and ${byCountry.values.sumOf { it.size }} states to $directory")
    println("  Size: ${totalBytes / BYTES_PER_KIB} KiB")
}

/** A tolerance that suits the country: fine for Luxembourg, coarser for Russia. */
private fun toleranceFor(states: List<JsonObject>): Double {
    val latitudes = states.flatMap { state -> positions(state.getValue("geometry").jsonObject).map { it[1] } }
    val span = (latitudes.maxOrNull() ?: 0.0) - (latitudes.minOrNull() ?: 0.0)
    return (span * TOLERANCE_PER_DEGREE_OF_LATITUDE).coerceIn(MIN_TOLERANCE, MAX_TOLERANCE)
}

/** Reduces one state to the properties the app reads, or `null` if nothing of it survives. */
private fun trimState(state: JsonObject, tolerance: Double): JsonElement? {
    val properties = state.getValue("properties").jsonObject
    val geometry = simplifiedGeometry(state.getValue("geometry").jsonObject, tolerance, COORDINATE_DECIMALS)
        ?: return null

    return buildJsonObject {
        put("type", JsonPrimitive("Feature"))
        put(
            "properties",
            buildJsonObject {
                put("name", JsonPrimitive(properties.text("name_en").ifBlank { properties.text("name") }))
                put("kind", JsonPrimitive(properties.text("type_en")))
                put("isoCode", properties.optionalText("iso_3166_2"))
                put("wikidataId", properties.optionalText("wikidataid"))
                put("labelLatitude", JsonPrimitive(properties.getValue("latitude").jsonPrimitive.double))
                put("labelLongitude", JsonPrimitive(properties.getValue("longitude").jsonPrimitive.double))
            },
        )
        put("geometry", geometry)
    }
}

private fun JsonObject.optionalText(key: String): JsonElement =
    text(key).takeIf { it.isNotBlank() && !it.startsWith(MISSING_CODE_PREFIX) }?.let(::JsonPrimitive) ?: JsonNull
