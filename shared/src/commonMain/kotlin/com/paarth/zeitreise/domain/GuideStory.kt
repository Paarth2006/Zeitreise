package com.paarth.zeitreise.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One fact the guide can tell about a place.
 *
 * @property headline a short title for the fact, such as "Wider than the Moon", or `null` for a
 *   fact taken straight from Wikipedia, which the sheet introduces with "Did you know?".
 * @property text the fact itself, in a sentence or two.
 */
@Serializable
public data class GuideFact(val headline: String? = null, @SerialName("fact") val text: String)

/**
 * What a guide says about a place: several facts, so each visit can show a different one, and a
 * few things the place is famous for.
 *
 * @property facts the facts, best first.
 * @property knownFor a few things the place is famous for, each a short phrase.
 */
@Serializable
public data class GuideStory(val facts: List<GuideFact>, val knownFor: List<String> = emptyList())

/** Where a fact came from, which the sheet tells the user. */
public enum class StorySource {
    /** Written by the AI guide from the place's Wikipedia article. */
    Ai,

    /** One of the facts bundled with the app, for when no AI is available. */
    Curated,

    /** Taken straight from the Wikipedia article. */
    Wikipedia,
}

/** Why the AI guide could not answer, so the sheet can say so kindly. */
public enum class GuideNotice {
    /** The free AI quota is used up for now; it refills within a minute or so. */
    RateLimited,

    /** The AI could not be reached or gave an answer that could not be read. */
    Unavailable,
}

/**
 * A fact together with where it came from.
 *
 * @property fact what to show.
 * @property source where [fact] came from, credited beneath it.
 */
public data class SourcedFact(val fact: GuideFact, val source: StorySource)

/**
 * Everything the guide has to say about a place.
 *
 * @property facts the facts to show, one per visit, in turn.
 * @property knownFor a few things the place is famous for.
 * @property article the Wikipedia article every fact is grounded on, linked from the sheet.
 * @property notice why the AI guide did not answer, when it was asked and could not.
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
