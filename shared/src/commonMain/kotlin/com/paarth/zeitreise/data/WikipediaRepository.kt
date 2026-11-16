package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.WikiArticle
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Identifies the app to Wikimedia, as its API policy asks of every client. Browsers forbid setting
 * `User-Agent`, so Wikimedia reads this header instead on every platform.
 */
private const val API_USER_AGENT = "Zeitreise/1.0 (https://github.com/Paarth2006/Zeitreise)"

private const val WIKIDATA_API = "https://www.wikidata.org/w/api.php"
private const val SUMMARY_API = "https://en.wikipedia.org/api/rest_v1/page/summary/"

/** The English Wikipedia, as Wikidata names it among an item's site links. */
private const val ENGLISH_WIKIPEDIA = "enwiki"

/** The summary type of a page that only lists other articles, which has nothing to say itself. */
private const val DISAMBIGUATION = "disambiguation"

/** Finds the Wikipedia article for a place. */
public interface WikipediaRepository {
    /**
     * The summary of the article for the place with the Wikidata item [wikidataId], or of the
     * article titled [fallbackTitle] when the place has no item.
     *
     * @return the summary, or `null` when no such article exists.
     * @throws Exception when Wikipedia cannot be reached; a missing article is not an error.
     */
    public suspend fun article(wikidataId: String?, fallbackTitle: String): WikiArticle?
}

/**
 * Looks articles up over the Wikipedia and Wikidata web APIs, and keeps each one it finds.
 *
 * The Wikidata item gives the article's exact title, so a place is never matched to a namesake by
 * guessing; the title then gives the summary from the Wikipedia REST API.
 *
 * @param dispatcher dispatcher the requests and parsing run on, keeping the main thread free.
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

    /** The English Wikipedia title of the Wikidata item [wikidataId], if it has an article there. */
    private suspend fun englishTitle(wikidataId: String): String? {
        val response = client.get(WIKIDATA_API) {
            header(API_USER_AGENT_HEADER, API_USER_AGENT)
            parameter("action", "wbgetentities")
            parameter("ids", wikidataId)
            parameter("props", "sitelinks")
            parameter("sitefilter", ENGLISH_WIKIPEDIA)
            parameter("format", "json")
            // Lets a browser read the answer: the action API only sends CORS headers when asked.
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

/** The part of a Wikidata `wbgetentities` answer the app reads. */
@Serializable
private data class WikidataEntities(val entities: Map<String, WikidataEntity> = emptyMap())

@Serializable
private data class WikidataEntity(val sitelinks: Map<String, WikidataSitelink>? = null)

@Serializable
private data class WikidataSitelink(val title: String)

/** The part of a Wikipedia REST page summary the app reads. */
@Serializable
private data class WikiSummary(
    val type: String = "",
    val title: String,
    val description: String? = null,
    val extract: String = "",
    val thumbnail: WikiImage? = null,
    @SerialName("content_urls") val contentUrls: WikiContentUrls? = null,
)

@Serializable
private data class WikiImage(val source: String)

@Serializable
private data class WikiContentUrls(val desktop: WikiPageUrl? = null)

@Serializable
private data class WikiPageUrl(val page: String)
