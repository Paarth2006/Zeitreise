package com.paarth.zeitreise.ai

import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.WikiArticle
import kotlinx.serialization.Serializable

/**
 * A place the guide can talk about.
 *
 * @property name the English name.
 * @property description what kind of place it is, like "a country in Asia", so places with the
 *   same name aren't mixed up.
 * @property wikidataId its Wikidata item, used as the cache key.
 * @property year the year being visited in Time Travel (negative for BC), or `null` for today.
 */
public data class GuidePlace(val name: String, val description: String, val wikidataId: String?, val year: Int? = null)

/** Formats a year as "AD 1492" or "500 BC". */
internal fun yearInWords(year: Int): String = if (year < 0) "${-year} BC" else "AD $year"

/**
 * A place suggested by the guide: only a name and a Wikipedia title. The location and photo come
 * from Wikipedia later.
 *
 * @property name the English name.
 * @property wikipediaTitle the title of its English Wikipedia article.
 */
@Serializable
public data class PlaceSuggestion(val name: String, val wikipediaTitle: String)

/** Tells short, accurate stories about places and suggests places to visit. */
public interface Guide {
    /**
     * Tells a story about [place], using only what its Wikipedia [article] says.
     *
     * @param notableFacts interesting sentences from the rest of the article.
     * @return the story, or `null` if this guide has nothing to say.
     */
    public suspend fun tell(place: GuidePlace, article: WikiArticle, notableFacts: List<String>): GuideStory?

    /**
     * Suggests places to visit in [country]. The [article] makes sure the right country is meant.
     *
     * @return the suggestions, best first, or `null` if this guide has none.
     */
    public suspend fun suggestPlaces(country: GuidePlace, article: WikiArticle): List<PlaceSuggestion>?
}
