package com.paarth.zeitreise.data

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

private fun wikidataAnswer(id: String, title: String?) = """
    { "entities": { "$id": { "sitelinks": { ${title?.let { "\"enwiki\": { \"title\": \"$it\" }" } ?: ""} } } } }
""".trimIndent()

private fun summaryAnswer(title: String, type: String = "standard") = """
    {
      "type": "$type",
      "title": "$title",
      "description": "Country in South Asia",
      "extract": "$title is a country in South Asia.",
      "thumbnail": { "source": "https://upload.wikimedia.org/$title.png" },
      "content_urls": { "desktop": { "page": "https://en.wikipedia.org/wiki/$title" } }
    }
""".trimIndent()

class WikipediaRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun TestScope.repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KtorWikipediaRepository {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return KtorWikipediaRepository(
            client = HttpClient(engine),
            cache = SettingsResponseCache(MapSettings()),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private fun MockRequestHandleScope.answerFor(request: HttpRequestData, title: String?, type: String = "standard") =
        when (request.url.host) {
            "www.wikidata.org" -> respond(wikidataAnswer("Q668", title), headers = JSON_HEADERS)
            else -> respond(summaryAnswer(title ?: "Fallback", type), headers = JSON_HEADERS)
        }

    @Test
    fun findsTheArticleThroughTheWikidataItem() = runTest {
        val article = repository { answerFor(it, title = "India") }.article("Q668", fallbackTitle = "India")

        assertNotNull(article)
        assertEquals("India", article.title)
        assertEquals("Country in South Asia", article.description)
        assertEquals("https://en.wikipedia.org/wiki/India", article.pageUrl)
        assertEquals("https://upload.wikimedia.org/India.png", article.thumbnailUrl)
        assertEquals("/api/rest_v1/page/summary/India", requests.last().url.encodedPath)
    }

    @Test
    fun identifiesTheAppOnEveryRequest() = runTest {
        repository { answerFor(it, title = "India") }.article("Q668", fallbackTitle = "India")

        requests.forEach { assertNotNull(it.headers["Api-User-Agent"], "missing on ${it.url}") }
    }

    @Test
    fun usesTheFallbackTitleWhenTheItemHasNoEnglishArticle() = runTest {
        repository { answerFor(it, title = null) }.article("Q668", fallbackTitle = "Somewhere")

        assertEquals("/api/rest_v1/page/summary/Somewhere", requests.last().url.encodedPath)
    }

    @Test
    fun skipsWikidataForAPlaceWithoutAnItem() = runTest {
        repository { answerFor(it, title = "Somewhere") }.article(wikidataId = null, fallbackTitle = "Somewhere")

        assertEquals(listOf("en.wikipedia.org"), requests.map { it.url.host })
    }

    @Test
    fun treatsADisambiguationPageAsNoArticle() = runTest {
        assertNull(repository { answerFor(it, title = "Georgia", type = "disambiguation") }.article(null, "Georgia"))
    }

    @Test
    fun treatsAMissingPageAsNoArticle() = runTest {
        assertNull(repository { respond("", HttpStatusCode.NotFound) }.article(null, "Atlantis"))
    }

    @Test
    fun answersTheSecondTimeFromTheCache() = runTest {
        val wikipedia = repository { answerFor(it, title = "India") }
        wikipedia.article("Q668", fallbackTitle = "India")
        val firstRequests = requests.size

        val again = wikipedia.article("Q668", fallbackTitle = "India")

        assertEquals("India", again?.title)
        assertEquals(firstRequests, requests.size)
    }

    @Test
    fun failsLoudlyWhenWikipediaIsDown() = runTest {
        val wikipedia = repository { respond("", HttpStatusCode.ServiceUnavailable) }

        assertFailsWith<IllegalStateException> { wikipedia.article(null, "India") }
    }

    @Test
    fun picksNotableSentencesFromTheWholeArticle() = runTest {
        val notable = "India has the largest number of languages of any country in the world, over 400 of them."
        val wikipedia = repository { request ->
            when (request.url.host + request.url.encodedPath) {
                "en.wikipedia.org/w/api.php" -> respond(
                    """{ "query": { "pages": [ { "extract": "History\n$notable\nThe land is wide." } ] } }""",
                    headers = JSON_HEADERS,
                )
                else -> answerFor(request, title = "India")
            }
        }
        val article = wikipedia.article("Q668", fallbackTitle = "India")

        val facts = wikipedia.notableFacts(requireNotNull(article))

        assertEquals(listOf(notable), facts)
        assertEquals("India", requests.last().url.parameters["titles"])
    }

    @Test
    fun goesWithoutNotableSentencesWhenTheFullTextCannotBeRead() = runTest {
        val wikipedia = repository { request ->
            when (request.url.host + request.url.encodedPath) {
                "en.wikipedia.org/w/api.php" -> respond("", HttpStatusCode.ServiceUnavailable)
                else -> answerFor(request, title = "India")
            }
        }
        val article = requireNotNull(wikipedia.article("Q668", fallbackTitle = "India"))

        assertEquals(emptyList(), wikipedia.notableFacts(article))
    }
}
