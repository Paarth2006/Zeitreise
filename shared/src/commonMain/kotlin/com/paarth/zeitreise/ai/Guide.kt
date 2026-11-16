package com.paarth.zeitreise.ai

import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.WikiArticle
import kotlinx.serialization.Serializable

/**
 * A place the guide can talk about.
 *
 * @property name the place's English name.
 * @property description what kind of place it is, such as "a country in Asia" or "a state of
 *   India", so the guide knows what it is talking about even when names are shared.
 * @property wikidataId the place's Wikidata item, which keys stories and cached answers.
 */
public data class GuidePlace(val name: String, val description: String, val wikidataId: String?)

/**
 * A place the guide suggests visiting: a name and a Wikipedia title, never a location or a
 * picture, which come from the article once the title is checked.
 *
 * @property name the place's English name.
 * @property wikipediaTitle the title of the place's English Wikipedia article.
 */
@Serializable
public data class PlaceSuggestion(val name: String, val wikipediaTitle: String)

/** Tells a short, accurate story about a place, and suggests places to visit in a country. */
public interface Guide {
    /**
     * Tells a story about [place], grounded on its Wikipedia [article] so that it states only what
     * the article supports.
     *
     * @param notableFacts sentences from further into the article, which give a guide more than
     *   the opening paragraph to choose facts from.
     * @return the story, or `null` when this guide has nothing to say about [place].
     */
    public suspend fun tell(place: GuidePlace, article: WikiArticle, notableFacts: List<String>): GuideStory?

    /**
     * Suggests the places most worth visiting in [country], whose Wikipedia [article] is passed
     * along so the guide knows which country is meant.
     *
     * @return the suggestions, best first, or `null` when this guide has none for [country].
     */
    public suspend fun suggestPlaces(country: GuidePlace, article: WikiArticle): List<PlaceSuggestion>?
}
