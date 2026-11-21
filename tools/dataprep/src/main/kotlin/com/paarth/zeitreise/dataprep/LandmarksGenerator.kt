package com.paarth.zeitreise.dataprep

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/** Most titles the Wikidata API resolves in one request. */
private const val TITLES_PER_REQUEST = 50

/** Decimal places kept for a landmark's coordinates: about a hundred metres. */
private const val COORDINATE_DECIMALS = 3

/** Wikidata properties read for each landmark. */
private const val COORDINATES = "P625"
private const val INCEPTION = "P571"
private const val START_TIME = "P580"
private const val OPENING = "P1619"

/**
 * "Dissolved, abolished or demolished": the only end date read. Wikidata's "end time" often marks
 * the end of construction instead, which would have Stonehenge vanish in 1400 BC.
 */
private const val DISSOLVED = "P576"

/**
 * The landmarks Time Travel pins on the globe, by the title of their English Wikipedia article:
 * wonders of every age and continent, including some that no longer stand, so sliding through
 * the years shows them rise and fall.
 */
private val LANDMARK_TITLES = listOf(
    "Göbekli Tepe", "Stonehenge", "Great Pyramid of Giza", "Great Sphinx of Giza", "Ziggurat of Ur",
    "Knossos", "Mohenjo-daro", "Karnak", "Abu Simbel", "Ishtar Gate", "Parthenon", "Colossus of Rhodes",
    "Lighthouse of Alexandria", "Temple of Artemis", "Mausoleum at Halicarnassus", "Statue of Zeus at Olympia",
    "Persepolis", "Great Wall of China", "Terracotta Army", "Petra", "Colosseum", "Pantheon, Rome",
    "Pont du Gard", "Hadrian's Wall", "Teotihuacan", "Sanchi", "Ajanta Caves", "Hagia Sophia", "Borobudur",
    "Tikal", "Chichen Itza", "Angkor Wat", "Buddhas of Bamiyan", "Kinkaku-ji", "Notre-Dame de Paris",
    "Tower of London", "Alhambra", "Great Zimbabwe", "Machu Picchu", "Forbidden City", "Tenochtitlan",
    "St. Peter's Basilica", "Taj Mahal", "Red Fort", "Potala Palace", "Palace of Versailles", "Kaaba",
    "Moscow Kremlin", "Brandenburg Gate", "Statue of Liberty", "Eiffel Tower", "Panama Canal",
    "Empire State Building", "Golden Gate Bridge", "Christ the Redeemer (statue)", "Berlin Wall",
    "Sydney Opera House", "World Trade Center (1973–2001)", "CN Tower", "Burj Khalifa", "Old Summer Palace",
    "The Crystal Palace", "Neuschwanstein Castle", "Great Mosque of Djenné", "Easter Island",
    "Leaning Tower of Pisa", "Sagrada Família", "Golden Temple", "Mont-Saint-Michel", "Shwedagon Pagoda",
)

/**
 * Sites Wikidata gives an end date for that still stand: the date records when they were
 * abandoned, or an earlier building on the site, so they stay on the globe to this day.
 */
private val STILL_STANDING = setOf(
    "Göbekli Tepe",
    "Stonehenge",
    "Persepolis",
    "Teotihuacan",
    "Hagia Sophia",
    "Chichen Itza",
    "Machu Picchu",
)

private val JSON = Json {
    prettyPrint = false
    encodeDefaults = false
}

/**
 * One landmark as the app bundles it.
 *
 * @property built the year it was built or founded; negative before Christ.
 * @property until the year it was destroyed or demolished, if it no longer stands.
 */
@Serializable
private data class BundledLandmark(
    val name: String,
    val wikipediaTitle: String,
    val latitude: Double,
    val longitude: Double,
    val built: Int,
    val until: Int? = null,
)

/**
 * Writes `history/landmarks.json`: each landmark's location, and the years it was built and,
 * where it fell, destroyed, from Wikidata, which is public domain (CC0) and may be bundled.
 * Landmarks Wikidata has no location or founding year for are left out and listed.
 */
internal fun generateLandmarks(resources: Path) {
    val found = LANDMARK_TITLES.chunked(TITLES_PER_REQUEST).flatMap(::resolve)
    val missing = LANDMARK_TITLES - found.map { it.wikipediaTitle }.toSet()
    if (missing.isNotEmpty()) println("Left out, with no location or founding year on Wikidata: $missing")

    val target = resources.resolve("history").also { it.createDirectories() }.resolve("landmarks.json")
    val text = JSON.encodeToString(found.sortedBy { it.built })
    target.writeText(text)
    println("Wrote ${found.size} landmarks to $target (${text.length / BYTES_PER_KIB} KiB)")
}

/** Looks [titles] up on Wikidata, keeping those with a location and a founding year. */
private fun resolve(titles: List<String>): List<BundledLandmark> {
    val query = listOf(
        "action" to "wbgetentities",
        "sites" to "enwiki",
        "titles" to titles.joinToString("|"),
        "props" to "claims|sitelinks",
        "sitefilter" to "enwiki",
        "format" to "json",
    ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val entities = Json.parseToJsonElement(download("$WIKIDATA_API?$query")).jsonObject["entities"]?.jsonObject
    return entities.orEmpty().values.mapNotNull { entity -> toLandmark(entity.jsonObject) }
}

private fun toLandmark(entity: JsonObject): BundledLandmark? {
    val title = entity["sitelinks"]?.jsonObject?.get("enwiki")?.jsonObject?.text("title")
    val claims = entity["claims"]?.jsonObject
    val position = claims?.firstValue(COORDINATES)?.jsonObject
    val latitude = position?.get("latitude")?.jsonPrimitive?.double
    val longitude = position?.get("longitude")?.jsonPrimitive?.double
    val point = if (latitude != null && longitude != null) latitude to longitude else null
    val built = claims?.let { listOf(INCEPTION, START_TIME, OPENING).firstNotNullOfOrNull(it::earliestYear) }
    if (title == null || point == null || built == null) return null
    val until = claims.earliestYear(DISSOLVED)?.takeIf { title !in STILL_STANDING && it > built }
    return BundledLandmark(
        name = title.substringBefore(" (").substringBefore(","),
        wikipediaTitle = title,
        latitude = round(point.first, COORDINATE_DECIMALS),
        longitude = round(point.second, COORDINATE_DECIMALS),
        built = built,
        until = until,
    )
}

/** The value of the first statement of [property], if there is one. */
private fun JsonObject.firstValue(property: String): JsonElement? =
    statements(property).firstNotNullOfOrNull { it.mainValue() }

/** The earliest year among the time statements of [property]. */
private fun JsonObject.earliestYear(property: String): Int? =
    statements(property).mapNotNull { it.mainValue()?.jsonObject?.text("time")?.let(::yearOf) }.minOrNull()

private fun JsonObject.statements(property: String): JsonArray = this[property]?.jsonArray ?: JsonArray(emptyList())

private fun JsonElement.mainValue(): JsonElement? =
    jsonObject["mainsnak"]?.jsonObject?.get("datavalue")?.jsonObject?.get("value")

/** The year of a Wikidata time such as `+1889-03-31T00:00:00Z` or `-2560-00-00T00:00:00Z`. */
internal fun yearOf(time: String): Int? {
    val sign = if (time.startsWith("-")) -1 else 1
    return time.trimStart('+', '-').substringBefore('-').toIntOrNull()?.let { it * sign }
}
