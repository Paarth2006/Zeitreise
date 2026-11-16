package com.paarth.zeitreise.data

import com.paarth.zeitreise.ai.Guide
import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.ai.GuideUnavailableException
import com.paarth.zeitreise.ai.wikipediaStory
import com.paarth.zeitreise.domain.GuideAnswer
import com.paarth.zeitreise.domain.GuideNotice
import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.StorySource
import com.paarth.zeitreise.domain.WikiArticle

/** Finds something true and interesting to say about a place. */
public interface GuideRepository {
    /**
     * The story for [place], grounded on its Wikipedia article.
     *
     * @return the story, or `null` when the place has no Wikipedia article to ground one on.
     * @throws Exception when Wikipedia cannot be reached.
     */
    public suspend fun storyFor(place: GuidePlace): GuideAnswer?
}

/**
 * Reads the place's Wikipedia article, then asks the guides in order of how much they can say:
 * the AI guide when there is one, then the bundled demo stories, then the article itself. The
 * AI failing, for instance on the free tier's rate limit, is never an error; the next guide simply
 * answers, and the answer says why the AI did not.
 *
 * @param aiGuide the AI guide, or `null` when no API key is set.
 */
internal class FallbackGuideRepository(
    private val wikipedia: WikipediaRepository,
    private val aiGuide: Guide?,
    private val demoGuide: Guide,
) : GuideRepository {

    override suspend fun storyFor(place: GuidePlace): GuideAnswer? {
        val article = wikipedia.article(place.wikidataId, fallbackTitle = place.name) ?: return null
        val (aiStory, notice) = askAi(place, article)
        return when {
            aiStory != null -> GuideAnswer(aiStory, StorySource.Ai, article)
            else -> demoGuide.tell(place, article)?.let { GuideAnswer(it, StorySource.Curated, article, notice) }
                ?: GuideAnswer(wikipediaStory(article), StorySource.Wikipedia, article, notice)
        }
    }

    /** The AI's story, or why there is none: no notice when no AI guide is set up at all. */
    private suspend fun askAi(place: GuidePlace, article: WikiArticle): Pair<GuideStory?, GuideNotice?> =
        if (aiGuide == null) {
            null to null
        } else {
            try {
                aiGuide.tell(place, article) to null
            } catch (unavailable: GuideUnavailableException) {
                null to if (unavailable.rateLimited) GuideNotice.RateLimited else GuideNotice.Unavailable
            }
        }
}
