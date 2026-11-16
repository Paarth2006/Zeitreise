package com.paarth.zeitreise.data

import com.paarth.zeitreise.ai.Guide
import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.ai.GuideUnavailableException
import com.paarth.zeitreise.ai.PlaceSuggestion
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.GuideNotice
import com.paarth.zeitreise.domain.Landmark
import com.paarth.zeitreise.domain.PlacesToVisit
import com.paarth.zeitreise.domain.StorySource
import com.paarth.zeitreise.domain.WikiArticle
import com.paarth.zeitreise.domain.isNear
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.io.IOException

/** Most facts shown on a place's card, taken from its Wikipedia article. */
private const val MAX_LANDMARK_FACTS = 3

/** Share of a fact's words already in the summary at which the card would only be repeating itself. */
private const val REPEAT_SHARE = 0.6

/** Words shorter than this, such as "the" and "of", say nothing about whether a fact is new. */
private const val MIN_TELLING_WORD_LENGTH = 4

/**
 * Whether [fact] mostly says what [summary] already says, as the article's first sentence does,
 * which the card shows just above its facts.
 */
internal fun repeats(fact: String, summary: String): Boolean {
    val known = tellingWords(summary).toSet()
    val words = tellingWords(fact)
    return words.isNotEmpty() && words.count { it in known } >= words.size * REPEAT_SHARE
}

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

private fun tellingWords(text: String): List<String> =
    text.lowercase().split(NON_WORD).filter { it.length >= MIN_TELLING_WORD_LENGTH }

/** Finds the places worth visiting in a country, and what to say about each. */
public interface ExploreRepository {
    /**
     * The places worth visiting in [country], each checked against Wikipedia.
     *
     * @throws Exception when Wikipedia cannot be reached for the country itself.
     */
    public suspend fun placesToVisit(country: Country): PlacesToVisit

    /** A few notable facts from [landmark]'s Wikipedia article; empty when it cannot be read. */
    public suspend fun factsAbout(landmark: Landmark): List<String>
}

/**
 * Asks the AI guide for places to visit, or the bundled list when the AI cannot answer, then
 * checks every place against Wikipedia before it becomes a pin.
 *
 * A place is kept only if its article exists, has coordinates, and puts the place in or just off
 * the country; whatever the guide got wrong is dropped, never shown. The articles are looked up
 * at the same time, so eight places take about as long as one.
 *
 * @param aiGuide the AI guide, or `null` when no API key is set.
 */
internal class GuidedExploreRepository(
    private val wikipedia: WikipediaRepository,
    private val aiGuide: Guide?,
    private val demoGuide: Guide,
) : ExploreRepository {

    override suspend fun placesToVisit(country: Country): PlacesToVisit {
        val place = GuidePlace(country.name, "a country in ${country.continent}", country.wikidataId)
        val article = wikipedia.article(country.wikidataId, fallbackTitle = country.name)
            ?: return PlacesToVisit(landmarks = emptyList(), source = StorySource.Wikipedia)

        val (aiPlaces, notice) = askAi(place, article)
        val suggestions = aiPlaces ?: demoGuide.suggestPlaces(place, article).orEmpty()
        return PlacesToVisit(
            landmarks = verify(suggestions, country),
            source = if (aiPlaces != null) StorySource.Ai else StorySource.Curated,
            notice = notice,
        )
    }

    override suspend fun factsAbout(landmark: Landmark): List<String> = wikipedia.notableFacts(landmark.article)
        .filterNot { repeats(it, landmark.article.extract) }
        .take(MAX_LANDMARK_FACTS)

    /** The suggestions whose Wikipedia articles place them in [country], in the guide's order. */
    private suspend fun verify(suggestions: List<PlaceSuggestion>, country: Country): List<Landmark> = coroutineScope {
        suggestions
            .map { suggestion -> async { resolve(suggestion, country) } }
            .awaitAll()
            .filterNotNull()
            .distinctBy { it.article.title }
    }

    private suspend fun resolve(suggestion: PlaceSuggestion, country: Country): Landmark? {
        val article = lookUp(suggestion.wikipediaTitle)
        val location = article?.location?.takeIf(country::isNear)
        return if (article != null && location != null) Landmark(suggestion.name, article, location) else null
    }

    /** The article titled [title], or `null` when it is missing or cannot be read right now. */
    private suspend fun lookUp(title: String): WikiArticle? = try {
        wikipedia.article(wikidataId = null, fallbackTitle = title)
    } catch (_: IllegalStateException) {
        // One place failing to load costs only its own pin.
        null
    } catch (_: IOException) {
        null
    }

    /** The AI's places, or why there are none: no notice when no AI guide is set up at all. */
    private suspend fun askAi(place: GuidePlace, article: WikiArticle): Pair<List<PlaceSuggestion>?, GuideNotice?> =
        if (aiGuide == null) {
            null to null
        } else {
            try {
                aiGuide.suggestPlaces(place, article) to null
            } catch (unavailable: GuideUnavailableException) {
                null to if (unavailable.rateLimited) GuideNotice.RateLimited else GuideNotice.Unavailable
            }
        }
}
