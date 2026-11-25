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
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.domain.Territory
import com.paarth.zeitreise.domain.WikiArticle
import com.paarth.zeitreise.domain.contains
import com.paarth.zeitreise.domain.isNear
import com.paarth.zeitreise.globe.GeoPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.io.IOException
import kotlin.math.roundToInt

/** How many places Wikipedia's search adds when no guide knows the area. */
private const val MAX_SEARCHED_PLACES = 8

/** Kilometres per degree along a great circle, to turn an area's size into a search radius. */
private const val KM_PER_DEGREE = 111.2

/** The search radius stays between a small state's and what Wikipedia's search handles well. */
private const val MIN_SEARCH_KM = 20
private const val MAX_SEARCH_KM = 1_000

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

/** Finds places worth visiting in a country or one of its states, and facts about each. */
public interface ExploreRepository {
    /**
     * The places worth visiting in [subdivision], or in the whole [country] if it's `null`, each
     * checked against Wikipedia.
     *
     * @throws Exception when Wikipedia cannot be reached for the country or state itself.
     */
    public suspend fun placesToVisit(country: Country, subdivision: Subdivision? = null): PlacesToVisit

    /** A few facts from [landmark]'s Wikipedia article, or an empty list if it can't be read. */
    public suspend fun factsAbout(landmark: Landmark): List<String>

    /** The landmark to show for the historic [pin], or `null` if its article can't be read. */
    public suspend fun landmarkFor(pin: HistoricLandmark): Landmark?
}

/**
 * Gets places to visit from the AI guide, else the bundled list, else Wikipedia's own search, and
 * checks each one against Wikipedia before it becomes a pin.
 *
 * A place is kept only if its article exists, has coordinates and is in or near the country or
 * state, so anything the guide got wrong is dropped. The articles are fetched in parallel.
 *
 * @param aiGuide the AI guide, or `null` if no API key is set.
 */
internal class GuidedExploreRepository(
    private val wikipedia: WikipediaRepository,
    private val aiGuide: Guide?,
    private val demoGuide: Guide,
) : ExploreRepository {

    override suspend fun placesToVisit(country: Country, subdivision: Subdivision?): PlacesToVisit {
        val area: Territory = subdivision ?: country
        val place =
            subdivision?.let { GuidePlace(it.name, "a ${it.kind.lowercase()} of ${country.name}", it.wikidataId) }
                ?: GuidePlace(country.name, "a country in ${country.continent}", country.wikidataId)
        val article = wikipedia.article(place.wikidataId, fallbackTitle = place.name)
            ?: return PlacesToVisit(landmarks = emptyList(), source = StorySource.Wikipedia)

        val (aiPlaces, notice) = askAi(place, article)
        val curated = if (aiPlaces == null) demoGuide.suggestPlaces(place, article) else null
        val suggestions =
            aiPlaces ?: curated ?: searchedPlaces(area, center = subdivision?.labelPoint ?: country.focus.center)
        return PlacesToVisit(
            // A country takes places near its coast too; a state only those inside its borders,
            // or the neighbours' places would slip in.
            landmarks = verify(suggestions, inArea = if (subdivision == null) area::isNear else area::contains),
            source = when {
                aiPlaces != null -> StorySource.Ai
                curated != null -> StorySource.Curated
                else -> StorySource.Wikipedia
            },
            notice = notice,
        )
    }

    override suspend fun factsAbout(landmark: Landmark): List<String> = wikipedia.notableFacts(landmark.article)
        .filterNot { repeats(it, landmark.article.extract) }
        .take(MAX_LANDMARK_FACTS)

    override suspend fun landmarkFor(pin: HistoricLandmark): Landmark? =
        lookUp(pin.wikipediaTitle)?.let { article -> Landmark(pin.name, article, article.location ?: pin.location) }

    /**
     * Sights Wikipedia's search finds around [center], for when no guide knows [area]. Only those
     * inside its outline are kept, since the search looks in a circle that spills over borders.
     */
    private suspend fun searchedPlaces(area: Territory, center: GeoPoint): List<PlaceSuggestion> {
        val size = area.outlines.maxOf { it.exterior.bounds.radiusDegrees }
        val radiusKm = (size * KM_PER_DEGREE).roundToInt().coerceIn(MIN_SEARCH_KM, MAX_SEARCH_KM)
        val sights = try {
            wikipedia.sightsNear(center, radiusKm)
        } catch (_: IllegalStateException) {
            emptyList()
        } catch (_: IOException) {
            emptyList()
        }
        return sights
            .filter { area.contains(it.location) }
            .take(MAX_SEARCHED_PLACES)
            .map { PlaceSuggestion(name = it.title, wikipediaTitle = it.title) }
    }

    /** The suggestions whose articles put them [inArea], in the guide's order. */
    private suspend fun verify(suggestions: List<PlaceSuggestion>, inArea: (GeoPoint) -> Boolean): List<Landmark> =
        coroutineScope {
            suggestions
                .map { suggestion -> async { resolve(suggestion, inArea) } }
                .awaitAll()
                .filterNotNull()
                .distinctBy { it.article.title }
        }

    private suspend fun resolve(suggestion: PlaceSuggestion, inArea: (GeoPoint) -> Boolean): Landmark? {
        val article = lookUp(suggestion.wikipediaTitle)
        val location = article?.location?.takeIf(inArea)
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
