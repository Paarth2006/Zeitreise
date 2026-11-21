package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.WikiArticle
import com.paarth.zeitreise.domain.notableSentences
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Identifies the app to Wikimedia, as their API policy requires. Browsers don't allow setting
 * `User-Agent`, so this header is used instead.
 */
private const val API_USER_AGENT = "Zeitreise/1.0 (https://github.com/Paarth2006/Zeitreise)"

private const val WIKIDATA_API = "https://www.wikidata.org/w/api.php"
private const val SUMMARY_API = "https://en.wikipedia.org/api/rest_v1/page/summary/"
private const val WIKIPEDIA_API = "https://en.wikipedia.org/w/api.php"

private val STRING_LIST = ListSerializer(String.serializer())

/** Wikidata's site id for English Wikipedia. */
private const val ENGLISH_WIKIPEDIA = "enwiki"

/** Summary type of a disambiguation page, which has no real content. */
private const val DISAMBIGUATION = "disambiguation"

/** Finds the Wikipedia article for a place. */
public interface WikipediaRepository {
    /**
     * The article summary for Wikidata item [wikidataId], or for [fallbackTitle] if there is no item.
     *
     * @return the summary, or `null` if there is no such article.
     * @throws Exception if Wikipedia can't be reached. A missing article is not an error.
     */
    public suspend fun article(wikidataId: String?, fallbackTitle: String): WikiArticle?

    /**
     * Interesting sentences from the full text of [article], or an empty list if the text can't be
     * loaded. The summary alone is still enough, so this never throws.
     */
    public suspend fun notableFacts(article: WikiArticle): List<String>
}

/**
 * Looks up articles through the Wikipedia and Wikidata APIs and caches them.
 *
 * The Wikidata item gives the exact article title, so places are never matched to the wrong
 * article. The title is then used to get the summary from the Wikipedia REST API.
 *
 * @param dispatcher where requests and parsing run, off the main thread.
 */
internal class KtorWikipediaRepository(
    private val client: HttpClient,
    private val cache: ResponseCache,
    private val dispatcher: CoroutineDispatcher,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : WikipediaRepository {

    override suspend fun article(wikidataId: String?, fallbackTitle: String): WikiArticle? = withContext(dispatcher) {
        val key = "wiki.${wikidataId ?: fallbackTitle}"
        cache.get(key)?.let { return@withContext json.decodeFromString(WikiArticle.serializer(), it) }

        val title = wikidataId?.let { englishTitle(it) } ?: fallbackTitle
        summary(title)?.also { cache.put(key, json.encodeToString(WikiArticle.serializer(), it)) }
    }

    override suspend fun notableFacts(article: WikiArticle): List<String> = withContext(dispatcher) {
        val key = "facts.${article.title}"
        cache.get(key)?.let { return@withContext json.decodeFromString(STRING_LIST, it) }

        val text = try {
            fullText(article.title)
        } catch (_: IllegalStateException) {
            // The request failed, but the summary is still enough.
            null
        } catch (_: IOException) {
            null
        }
        text?.let(::notableSentences)?.also { cache.put(key, json.encodeToString(STRING_LIST, it)) }.orEmpty()
    }

    /** The article's full plain text from the action API. */
    private suspend fun fullText(title: String): String? {
        val response = client.get(WIKIPEDIA_API) {
            header(API_USER_AGENT_HEADER, API_USER_AGENT)
            parameter("action", "query")
            parameter("prop", "extracts")
            parameter("explaintext", "1")
            parameter("exsectionformat", "plain")
            parameter("redirects", "1")
            parameter("titles", title)
            parameter("format", "json")
            parameter("formatversion", "2")
            parameter("origin", "*")
        }.requireSuccess()
        return json.decodeFromString(
            WikiExtracts.serializer(),
            response.bodyAsText(),
        ).query?.pages?.firstOrNull()?.extract
    }

    /** The English Wikipedia title for [wikidataId], if it has one. */
    private suspend fun englishTitle(wikidataId: String): String? {
        val response = client.get(WIKIDATA_API) {
            header(API_USER_AGENT_HEADER, API_USER_AGENT)
            parameter("action", "wbgetentities")
            parameter("ids", wikidataId)
            parameter("props", "sitelinks")
            parameter("sitefilter", ENGLISH_WIKIPEDIA)
            parameter("format", "json")
            // Needed on the web: the action API only sends CORS headers when this is set.
            parameter("origin", "*")
        }.requireSuccess()
        val entities = json.decodeFromString(WikidataEntities.serializer(), response.bodyAsText())
        return entities.entities[wikidataId]?.sitelinks?.get(ENGLISH_WIKIPEDIA)?.title
    }

    private suspend fun summary(title: String): WikiArticle? {
        val response = client.get(SUMMARY_API + title.replace(' ', '_').encodeURLPathPart()) {
            header(API_USER_AGENT_HEADER, API_USER_AGENT)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        val summary = json.decodeFromString(WikiSummary.serializer(), response.requireSuccess().bodyAsText())
        return summary.takeIf { it.type != DISAMBIGUATION && it.extract.isNotBlank() }?.let {
            WikiArticle(
                title = it.title,
                description = it.description,
                extract = it.extract,
                thumbnailUrl = it.thumbnail?.source,
                pageUrl = it.contentUrls?.desktop?.page ?: "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}",
                latitude = it.coordinates?.lat,
                longitude = it.coordinates?.lon,
            )
        }
    }

    private fun HttpResponse.requireSuccess(): HttpResponse = also {
        check(status.isSuccess()) { "Wikipedia answered ${status.value} for ${call.request.url}" }
    }

    private companion object {
        const val API_USER_AGENT_HEADER = "Api-User-Agent"
    }
}

/** The fields read from an action API `extracts` response. */
@Serializable
private data class WikiExtracts(val query: WikiQuery? = null)

@Serializable
private data class WikiQuery(val pages: List<WikiPageText> = emptyList())

@Serializable
private data class WikiPageText(val extract: String? = null)

/** The fields read from a Wikidata `wbgetentities` response. */
@Serializable
private data class WikidataEntities(val entities: Map<String, WikidataEntity> = emptyMap())

@Serializable
private data class WikidataEntity(val sitelinks: Map<String, WikidataSitelink>? = null)

@Serializable
private data class WikidataSitelink(val title: String)

/** The fields read from a Wikipedia REST page summary. */
@Serializable
private data class WikiSummary(
    val type: String = "",
    val title: String,
    val description: String? = null,
    val extract: String = "",
    val thumbnail: WikiImage? = null,
    @SerialName("content_urls") val contentUrls: WikiContentUrls? = null,
    val coordinates: WikiCoordinates? = null,
)

@Serializable
private data class WikiCoordinates(val lat: Double, val lon: Double)

@Serializable
private data class WikiImage(val source: String)

@Serializable
private data class WikiContentUrls(val desktop: WikiPageUrl? = null)

@Serializable
private data class WikiPageUrl(val page: String)
