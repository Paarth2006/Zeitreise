package com.paarth.zeitreise.domain

import kotlinx.serialization.Serializable

/**
 * What the guide says about a place.
 *
 * @property headline a short title for the fact, such as "Wider than the Moon".
 * @property fact one surprising, accurate fact, in a sentence or two.
 * @property knownFor a few things the place is famous for, each a short phrase.
 */
@Serializable
public data class GuideStory(val headline: String, val fact: String, val knownFor: List<String> = emptyList())

/** Where a story came from, which the sheet tells the user. */
public enum class StorySource {
    /** Written by the AI guide from the place's Wikipedia article. */
    Ai,

    /** One of the stories bundled with the app, for when no AI is available. */
    Curated,

    /** Taken straight from the Wikipedia article, for places with no bundled story. */
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
 * A story about a place together with its provenance.
 *
 * @property story what to show.
 * @property source where [story] came from.
 * @property article the Wikipedia article the story is grounded on, credited in the sheet.
 * @property notice why the AI guide did not answer, when it was asked and could not.
 */
public data class GuideAnswer(
    val story: GuideStory,
    val source: StorySource,
    val article: WikiArticle,
    val notice: GuideNotice? = null,
)
