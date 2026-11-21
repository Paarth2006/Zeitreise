package com.paarth.zeitreise.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One fact about a place.
 *
 * @property headline a short title like "Wider than the Moon", or `null` for a plain Wikipedia fact.
 * @property text the fact itself, a sentence or two.
 */
@Serializable
public data class GuideFact(val headline: String? = null, @SerialName("fact") val text: String)

/**
 * What a guide says about a place.
 *
 * @property facts the facts, best first.
 * @property knownFor a few things the place is famous for, each a short phrase.
 */
@Serializable
public data class GuideStory(val facts: List<GuideFact>, val knownFor: List<String> = emptyList())

/** Where a fact came from, shown under it in the sheet. */
public enum class StorySource {
    /** Written by the AI guide from the Wikipedia article. */
    Ai,

    /** Bundled with the app, used when there is no AI. */
    Curated,

    /** Taken directly from the Wikipedia article. */
    Wikipedia,
}

/** Why the AI guide couldn't answer. */
public enum class GuideNotice {
    /** The free quota is used up for now. It refills within about a minute. */
    RateLimited,

    /** The AI couldn't be reached or its answer couldn't be read. */
    Unavailable,
}

/**
 * A fact and where it came from.
 *
 * @property fact the fact to show.
 * @property source where [fact] came from.
 */
public data class SourcedFact(val fact: GuideFact, val source: StorySource)

/**
 * Everything the guide has to say about a place.
 *
 * @property facts the facts, shown one per visit.
 * @property knownFor a few things the place is famous for.
 * @property article the Wikipedia article the facts are based on.
 * @property notice why the AI guide didn't answer, if it failed.
 */
public data class GuideAnswer(
    val facts: List<SourcedFact>,
    val knownFor: List<String>,
    val article: WikiArticle,
    val notice: GuideNotice? = null,
) {
    init {
        require(facts.isNotEmpty()) { "An answer about ${article.title} needs at least one fact" }
    }
}
