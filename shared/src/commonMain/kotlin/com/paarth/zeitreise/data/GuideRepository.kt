package com.paarth.zeitreise.data

import com.paarth.zeitreise.ai.Guide
import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.ai.GuideUnavailableException
import com.paarth.zeitreise.ai.wikipediaFacts
import com.paarth.zeitreise.domain.GuideAnswer
import com.paarth.zeitreise.domain.GuideFact
import com.paarth.zeitreise.domain.GuideNotice
import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.SourcedFact
import com.paarth.zeitreise.domain.StorySource
import com.paarth.zeitreise.domain.WikiArticle

/** Finds something true and interesting to say about a place. */
public interface GuideRepository {
    /**
     * The story for [place], grounded on its Wikipedia article.
     *
     * @return the story, or `null` if the place has no Wikipedia article.
     * @throws Exception when Wikipedia cannot be reached.
     */
    public suspend fun storyFor(place: GuidePlace): GuideAnswer?
}

/**
 * Reads the place's Wikipedia article, then asks the AI guide if there is one. Otherwise it uses
 * the bundled facts followed by facts from the article.
 *
 * If the AI fails (for example because of the rate limit), it falls back to the next source and
 * the answer says why.
 *
 * @param aiGuide the AI guide, or `null` if no API key is set.
 */
internal class FallbackGuideRepository(
    private val wikipedia: WikipediaRepository,
    private val aiGuide: Guide?,
    private val demoGuide: Guide,
) : GuideRepository {

    override suspend fun storyFor(place: GuidePlace): GuideAnswer? {
        val article = wikipedia.article(place.wikidataId, fallbackTitle = place.name) ?: return null
        val notable = wikipedia.notableFacts(article)
        val (aiStory, notice) = askAi(place, article, notable)
        val curated = if (aiStory == null) demoGuide.tell(place, article, notable) else null
        val facts = when {
            aiStory != null -> aiStory.facts.sourcedAs(StorySource.Ai)
            // Bundled facts first, then the article's, so later visits still show something new.
            else -> curated?.facts.orEmpty().sourcedAs(StorySource.Curated) +
                wikipediaFacts(article, notable).sourcedAs(StorySource.Wikipedia)
        }
        return GuideAnswer(
            facts = facts,
            knownFor = (aiStory ?: curated)?.knownFor.orEmpty(),
            article = article,
            notice = notice,
        )
    }

    /** The AI's story, or the reason there is none. No notice if there is no AI guide at all. */
    private suspend fun askAi(
        place: GuidePlace,
        article: WikiArticle,
        notable: List<String>,
    ): Pair<GuideStory?, GuideNotice?> = if (aiGuide == null) {
        null to null
    } else {
        try {
            aiGuide.tell(place, article, notable) to null
        } catch (unavailable: GuideUnavailableException) {
            null to if (unavailable.rateLimited) GuideNotice.RateLimited else GuideNotice.Unavailable
        }
    }
}

private fun List<GuideFact>.sourcedAs(source: StorySource): List<SourcedFact> = map { SourcedFact(it, source) }
