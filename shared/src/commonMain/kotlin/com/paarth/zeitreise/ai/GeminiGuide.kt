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

/** The Gemini model used. Flash models are fast and in the free tier. */
internal val GUIDE_MODEL: LLModel = GoogleModels.Gemini3_7Flash

/** Low enough to stick to the article, high enough not to sound robotic. */
private const val GUIDE_TEMPERATURE = 0.4

/** HTTP status Gemini returns when the free quota is used up. */
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

/** Extra instructions for Time Travel: talk from that year and don't invent anything. */
private fun timeTravelInstructions(year: String) = """
    You are Zeitreise's time-travelling guide. The traveller has just arrived in $year and is
    looking at the place below. You are given its Wikipedia article and further sentences from it.
    Speak as a guide standing in $year: tell of the place as it was then, in the present tense,
    using ONLY facts the text states about $year or earlier. Never mention anything that happened
    after $year. If the text says nothing about $year itself, use what it says about earlier times,
    and say plainly in the first fact that little is recorded about $year.
    Reply with one JSON object and nothing else:
    {"facts": [{"headline": "...", "fact": "..."}], "knownFor": ["...", "...", "..."]}
    - facts: up to $MAX_FACTS different facts, the most vivid first.
    - headline: at most six words.
    - fact: one or two sentences.
    - knownFor: up to three short phrases (at most four words each) the place was known for in $year.
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
 * The AI guide. It asks Gemini (through Koog) for several facts based on the Wikipedia article in
 * one request, and caches the answer, so each place costs at most one request.
 *
 * [tell] and [suggestPlaces] throw [GuideUnavailableException] if Gemini can't answer, so the caller
 * can fall back to the bundled content.
 *
 * @param dispatcher where requests and parsing run, off the main thread.
 */
internal class GeminiGuide(
    private val client: LLMClient,
    private val cache: ResponseCache,
    private val dispatcher: CoroutineDispatcher,
    private val json: Json = Json,
) : Guide {

    override suspend fun tell(place: GuidePlace, article: WikiArticle, notableFacts: List<String>): GuideStory =
        withContext(dispatcher) {
            // Each year gets its own cache entry, since the story is different.
            val key = "story.${place.wikidataId ?: place.name}${place.year?.let { "@$it" }.orEmpty()}"
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

    /** Sends [prompt] to Gemini and returns the text. Any failure becomes [GuideUnavailableException]. */
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

    /** Builds the prompt for [place], for today or for the year being visited. */
    private fun storyPrompt(place: GuidePlace, article: WikiArticle, notableFacts: List<String>) = prompt(
        id = "zeitreise-place-story",
        params = LLMParams(temperature = GUIDE_TEMPERATURE),
    ) {
        system(place.year?.let { timeTravelInstructions(yearInWords(it)) } ?: GUIDE_INSTRUCTIONS)
        val visited = place.year?.let { " (visited in ${yearInWords(it)})" }.orEmpty()
        user(
            """
            Place: ${place.name}, ${place.description}$visited
            Wikipedia article "${article.title}":
            ${article.extract}

            Further sentences from the article:
            ${notableFacts.joinToString(separator = "\n") { "- $it" }}
            """.trimIndent(),
        )
    }

    companion object {
        /** Creates the guide using Koog's Gemini client and the given [httpClient]. */
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

/** Whether this exception (or its cause) means the free quota is used up. */
private fun Throwable.isRateLimit(): Boolean =
    generateSequence(this) { it.cause }.any { (it as? KoogHttpClientException)?.statusCode == TOO_MANY_REQUESTS }

/**
 * Thrown when the AI guide can't answer.
 *
 * @property rateLimited whether the free quota was used up. It usually resets within a minute.
 */
internal class GuideUnavailableException(val rateLimited: Boolean, cause: Throwable? = null) :
    Exception("The AI guide could not answer", cause)
