package com.paarth.zeitreise.dataprep

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val WIKIDATA_API = "https://www.wikidata.org/w/api.php"

/** Most items the Wikidata API returns in one request. */
private const val IDS_PER_REQUEST = 50

/** Wikidata properties read for each country. */
private const val CAPITAL = "P36"
private const val FLAG_IMAGE = "P41"

/** Qualifier marking a statement that no longer holds, such as Bonn as Germany's capital. */
private const val END_TIME = "P582"

/** Statement ranks, best first; deprecated statements are never read. */
private const val PREFERRED = "preferred"
private const val NORMAL = "normal"

/** Where Wikimedia Commons serves its images, and the width flags are drawn at: one of its standard sizes. */
private const val COMMONS_UPLOADS = "https://upload.wikimedia.org/wikipedia/commons/thumb"
private const val FLAG_WIDTH = 250

/** Languages a name is read in, in order: English, then the label shared by every language. */
private val LABEL_LANGUAGES = listOf("en", "mul")

private val JSON = Json {
    prettyPrint = false
    encodeDefaults = false
}

/**
 * A country's capitals and flag as the app bundles them.
 *
 * @property wikidataId the country's Wikidata item, which ties these facts to its outline.
 * @property capitals the country's capitals today, most often one; Bolivia and South Africa have more.
 * @property flag the address of a picture of the country's flag on Wikimedia Commons.
 */
@Serializable
private data class CountryFacts(
    val wikidataId: String,
    val capitals: List<String> = emptyList(),
    val flag: String? = null,
)

/**
 * Writes `country_facts.json`: each country's capitals and the address of its flag, from Wikidata,
 * which is public domain (CC0) and may be bundled. The flags themselves stay on Wikimedia Commons
 * and are downloaded while the app runs.
 */
internal fun generateCountryFacts(resources: Path) {
    val ids = Json.parseToJsonElement(resources.resolve("countries.json").readText())
        .jsonObject.getValue("features").jsonArray
        .mapNotNull { it.jsonObject["properties"]?.jsonObject?.get("wikidataId")?.jsonPrimitive?.content }
    val entities = ids.chunked(IDS_PER_REQUEST).flatMap { entitiesOf(it, props = "claims").entries }
    val capitalIds = entities.flatMap { (_, claims) -> claims.currentValues(CAPITAL) }.distinct()
    val capitalNames = capitalIds.chunked(IDS_PER_REQUEST).flatMap { labelsOf(it).entries }.associate { it.toPair() }

    val facts = entities.map { (id, claims) ->
        CountryFacts(
            wikidataId = id,
            capitals = claims.currentValues(CAPITAL).mapNotNull(capitalNames::get),
            flag = claims.currentValues(FLAG_IMAGE).firstOrNull()?.let(::flagAddress),
        )
    }
    val target = resources.resolve("country_facts.json")
    val text = JSON.encodeToString(facts.sortedBy { it.wikidataId })
    target.writeText(text)
    println("Wrote facts for ${facts.size} countries to $target (${text.length / BYTES_PER_KIB} KiB)")
    println("No capital: ${facts.filter { it.capitals.isEmpty() }.map { it.wikidataId }}")
    println("No flag: ${facts.filter { it.flag == null }.map { it.wikidataId }}")
}

/** The claims of each of [ids], by item. */
private fun entitiesOf(ids: List<String>, props: String): Map<String, JsonObject> =
    wikidata(ids, props).mapValues { (_, entity) -> entity["claims"]?.jsonObject ?: JsonObject(emptyMap()) }

/**
 * The English name of each of [ids], by item: its English label, or else its label for every
 * language, which is where Wikidata keeps names such as "St. John's" that read the same everywhere.
 */
private fun labelsOf(ids: List<String>): Map<String, String> = wikidata(ids, props = "labels")
    .mapNotNull { (id, entity) ->
        val labels = entity["labels"]?.jsonObject ?: return@mapNotNull null
        LABEL_LANGUAGES.firstNotNullOfOrNull { labels[it]?.jsonObject?.text("value") }?.let { id to it }
    }
    .toMap()

private fun wikidata(ids: List<String>, props: String): Map<String, JsonObject> {
    val query = listOf(
        "action" to "wbgetentities",
        "ids" to ids.joinToString("|"),
        "props" to props,
        "languages" to LABEL_LANGUAGES.joinToString("|"),
        "format" to "json",
    ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8)}" }
    val entities = Json.parseToJsonElement(download("$WIKIDATA_API?$query")).jsonObject["entities"]?.jsonObject
    return entities.orEmpty().mapValues { it.value.jsonObject }
}

/**
 * The values of [property] that hold today: its preferred statements if it has any, else its
 * normal ones, leaving out any that have ended.
 */
private fun JsonObject.currentValues(property: String): List<String> {
    val current = (this[property] as? JsonArray).orEmpty()
        .map { it.jsonObject }
        .filter { END_TIME !in (it["qualifiers"]?.jsonObject ?: JsonObject(emptyMap())) }
    val best = current.filter { it.rank == PREFERRED }.ifEmpty { current.filter { it.rank == NORMAL } }
    return best.mapNotNull { it.value }.distinct()
}

private val JsonObject.rank: String? get() = this["rank"]?.jsonPrimitive?.content

/** A statement's value: an item's id, or a file's name. */
private val JsonObject.value: String?
    get() {
        val value = this["mainsnak"]?.jsonObject?.get("datavalue")?.jsonObject?.get("value") ?: return null
        return (value as? JsonObject)?.text("id") ?: value.jsonPrimitive.content
    }

/**
 * The address of a [FLAG_WIDTH]-pixel picture of the Commons file [name]. Commons files sit in
 * folders named after the start of the MD5 hash of their name, and drawings are served as PNGs.
 */
internal fun flagAddress(name: String): String {
    val file = name.replace(' ', '_')
    val hash = MessageDigest.getInstance("MD5").digest(file.toByteArray()).joinToString("") { "%02x".format(it) }
    val picture = if (file.endsWith(".svg", ignoreCase = true)) "$file.png" else file
    val path = URLEncoder.encode(file, Charsets.UTF_8).replace("+", "%20")
    val thumb = URLEncoder.encode(picture, Charsets.UTF_8).replace("+", "%20")
    return "$COMMONS_UPLOADS/${hash[0]}/${hash.take(2)}/$path/${FLAG_WIDTH}px-$thumb"
}
