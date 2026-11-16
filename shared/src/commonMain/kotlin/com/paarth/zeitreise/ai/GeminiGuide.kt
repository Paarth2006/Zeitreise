package com.paarth.zeitreise.ai

import ai.koog.http.client.KoogHttpClientException
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import com.paarth.zeitreise.data.ResponseCache
import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.WikiArticle
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

/** The model the guide talks to: a current Gemini Flash model, fast and within the free tier. */
internal val GUIDE_MODEL: LLModel = GoogleModels.Gemini3_7Flash

/** Low enough that the guide sticks to the article, high enough that it does not sound canned. */
private const val GUIDE_TEMPERATURE = 0.4

/** The HTTP status Gemini answers with when the free quota is used up. */
private const val TOO_MANY_REQUESTS = 429

private val GUIDE_INSTRUCTIONS = """
    You are Zeitreise's travel guide: warm, curious and precise. You are given a place, the opening
    of its Wikipedia article, and further sentences from the same article. Using ONLY facts stated
    in that text, reply with one JSON object and nothing else:
    {"facts": [{"headline": "...", "fact": "..."}], "knownFor": ["...", "...", "..."]}
    - facts: $MAX_FACTS different facts, most surprising first, each about a different topic
      (geography, history, people, culture, nature) where the text allows.
    - headline: at most six words, naming what makes that fact surprising.
    - fact: one or two sentences.
    - knownFor: up to three short phrases (at most four words each) the place is famous for.
    Never invent numbers, dates or names that the text does not contain.
""".trimIndent()

private val PLACES_INSTRUCTIONS = """
    You are Zeitreise's travel guide. Given a country, name the $MAX_PLACES places in it most worth
    visiting: famous landmarks, natural wonders, historic sites and remarkable towns, spread across
    the country rather than bunched in one city. Reply with one JSON object and nothing else:
    {"places": [{"name": "...", "wikipediaTitle": "..."}]}
    - name: the place's usual English name.
    - wikipediaTitle: the exact title of the place's own English Wikipedia article.
    Only name real places that have their own English Wikipedia article and lie inside the country.
    Do not give coordinates, links or descriptions.
""".trimIndent()

/**
 * The AI guide: asks Gemini, through JetBrains' Koog framework, for several facts grounded on the
 * place's Wikipedia article in a single request, and keeps every answer, so a place costs one
 * request at most however often it is opened.
 *
 * [tell] and [suggestPlaces] throw [GuideUnavailableException] when Gemini cannot answer, so the
 * caller can fall back to the bundled stories and places.
 *
 * @param dispatcher dispatcher the request and parsing run on, keeping the main thread free.
 */
internal class GeminiGuide(
    private val client: LLMClient,
    private val cache: ResponseCache,
    private val dispatcher: CoroutineDispatcher,
    private val json: Json = Json,
) : Guide {

    override suspend fun tell(place: GuidePlace, article: WikiArticle, notableFacts: List<String>): GuideStory =
        withContext(dispatcher) {
            val key = "story.${place.wikidataId ?: place.name}"
            cache.get(key)?.let { return@withContext json.decodeFromString(GuideStory.serializer(), it) }

            val story = parseGuideStory(ask(storyPrompt(place, article, notableFacts)))
                ?: throw GuideUnavailableException(rateLimited = false)
            cache.put(key, json.encodeToString(GuideStory.serializer(), story))
            story
        }

    override suspend fun suggestPlaces(country: GuidePlace, article: WikiArticle): List<PlaceSuggestion> =
        withContext(dispatcher) {
            val key = "places.${country.wikidataId ?: country.name}"
            cache.get(key)?.let { return@withContext json.decodeFromString(PlaceSuggestions.serializer(), it).places }

            val places = parsePlaceSuggestions(ask(placesPrompt(country, article)))
                ?: throw GuideUnavailableException(rateLimited = false)
            cache.put(key, json.encodeToString(PlaceSuggestions.serializer(), PlaceSuggestions(places)))
            places
        }

    /** Sends [prompt] to Gemini and returns its text, turning every failure into one exception. */
    private suspend fun ask(prompt: Prompt): String = try {
        client.execute(prompt, GUIDE_MODEL, emptyList()).textContent()
    } catch (cause: KoogHttpClientException) {
        throw GuideUnavailableException(rateLimited = cause.isRateLimit(), cause = cause)
    } catch (cause: LLMClientException) {
        throw GuideUnavailableException(rateLimited = cause.isRateLimit(), cause = cause)
    } catch (cause: IOException) {
        throw GuideUnavailableException(rateLimited = false, cause = cause)
    }

    private fun placesPrompt(country: GuidePlace, article: WikiArticle) = prompt(
        id = "zeitreise-places-to-visit",
        params = LLMParams(temperature = GUIDE_TEMPERATURE),
    ) {
        system(PLACES_INSTRUCTIONS)
        user(
            """
            Country: ${country.name}, ${country.description}
            Wikipedia article "${article.title}":
            ${article.extract}
            """.trimIndent(),
        )
    }

    private fun storyPrompt(place: GuidePlace, article: WikiArticle, notableFacts: List<String>) = prompt(
        id = "zeitreise-place-story",
        params = LLMParams(temperature = GUIDE_TEMPERATURE),
    ) {
        system(GUIDE_INSTRUCTIONS)
        user(
            """
            Place: ${place.name}, ${place.description}
            Wikipedia article "${article.title}":
            ${article.extract}

            Further sentences from the article:
            ${notableFacts.joinToString(separator = "\n") { "- $it" }}
            """.trimIndent(),
        )
    }

    companion object {
        /** Builds the guide on Koog's Gemini client, sending its requests through [httpClient]. */
        fun create(
            apiKey: String,
            httpClient: HttpClient,
            cache: ResponseCache,
            dispatcher: CoroutineDispatcher,
        ): GeminiGuide = GeminiGuide(
            client = GoogleLLMClient(
                apiKey = apiKey,
                httpClientFactory = KtorKoogHttpClient.Factory(baseClient = httpClient, withSse = false),
            ),
            cache = cache,
            dispatcher = dispatcher,
        )
    }
}

/** Whether [this] or anything that caused it is Gemini saying the free quota is used up. */
private fun Throwable.isRateLimit(): Boolean =
    generateSequence(this) { it.cause }.any { (it as? KoogHttpClientException)?.statusCode == TOO_MANY_REQUESTS }

/**
 * The AI guide could not answer.
 *
 * @property rateLimited whether the free quota was used up, which passes within a minute or so.
 */
internal class GuideUnavailableException(val rateLimited: Boolean, cause: Throwable? = null) :
    Exception("The AI guide could not answer", cause)
