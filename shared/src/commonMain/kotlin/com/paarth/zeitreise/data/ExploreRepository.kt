package com.paarth.zeitreise.data

import com.paarth.zeitreise.ai.Guide
import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.ai.GuideUnavailableException
import com.paarth.zeitreise.ai.PlaceSuggestion
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.GuideNotice
import com.paarth.zeitreise.domain.HistoricLandmark
import com.paarth.zeitreise.domain.Landmark
import com.paarth.zeitreise.domain.PlacesToVisit
import com.paarth.zeitreise.domain.StorySource
import com.paarth.zeitreise.domain.WikiArticle
import com.paarth.zeitreise.domain.isNear
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.io.IOException

/** Maximum number of facts on a place's card. */
private const val MAX_LANDMARK_FACTS = 3

/** If this share of a fact's words is already in the summary, the fact is a repeat. */
private const val REPEAT_SHARE = 0.6

/** Short words like "the" and "of" are ignored when comparing. */
private const val MIN_TELLING_WORD_LENGTH = 4

/** Whether [fact] mostly repeats [summary], which is shown just above the facts. */
internal fun repeats(fact: String, summary: String): Boolean {
    val known = tellingWords(summary).toSet()
    val words = tellingWords(fact)
    return words.isNotEmpty() && words.count { it in known } >= words.size * REPEAT_SHARE
}

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

private fun tellingWords(text: String): List<String> =
    text.lowercase().split(NON_WORD).filter { it.length >= MIN_TELLING_WORD_LENGTH }

/** Finds places worth visiting in a country and facts about each. */
public interface ExploreRepository {
    /**
     * The places worth visiting in [country], each checked against Wikipedia.
     *
     * @throws Exception when Wikipedia cannot be reached for the country itself.
     */
    public suspend fun placesToVisit(country: Country): PlacesToVisit

    /** A few facts from [landmark]'s Wikipedia article, or an empty list if it can't be read. */
    public suspend fun factsAbout(landmark: Landmark): List<String>

    /** The landmark to show for the historic [pin], or `null` if its article can't be read. */
    public suspend fun landmarkFor(pin: HistoricLandmark): Landmark?
}

/**
 * Gets places to visit from the AI guide (or the bundled list if the AI can't answer) and checks
 * each one against Wikipedia before it becomes a pin.
 *
 * A place is kept only if its article exists, has coordinates and is in or near the country, so
 * anything the guide got wrong is dropped. The articles are fetched in parallel.
 *
 * @param aiGuide the AI guide, or `null` if no API key is set.
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

    override suspend fun landmarkFor(pin: HistoricLandmark): Landmark? =
        lookUp(pin.wikipediaTitle)?.let { article -> Landmark(pin.name, article, article.location ?: pin.location) }

    /** The suggestions whose articles put them in [country], in the guide's order. */
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

    /** The article titled [title], or `null` if it is missing or can't be loaded. */
    private suspend fun lookUp(title: String): WikiArticle? = try {
        wikipedia.article(wikidataId = null, fallbackTitle = title)
    } catch (_: IllegalStateException) {
        // If one place fails to load, only that pin is skipped.
        null
    } catch (_: IOException) {
        null
    }

    /** The AI's places, or the reason there are none. No notice if there is no AI guide at all. */
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
