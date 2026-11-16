package com.paarth.zeitreise.ai

import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.WikiArticle

/**
 * A place the guide can talk about.
 *
 * @property name the place's English name.
 * @property description what kind of place it is, such as "a country in Asia" or "a state of
 *   India", so the guide knows what it is talking about even when names are shared.
 * @property wikidataId the place's Wikidata item, which keys stories and cached answers.
 */
public data class GuidePlace(val name: String, val description: String, val wikidataId: String?)

/** Tells a short, accurate story about a place. */
public interface Guide {
    /**
     * Tells a story about [place], grounded on its Wikipedia [article] so that it states only what
     * the article supports.
     *
     * @return the story, or `null` when this guide has nothing to say about [place].
     */
    public suspend fun tell(place: GuidePlace, article: WikiArticle): GuideStory?
}
