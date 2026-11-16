package com.paarth.zeitreise.data

import com.paarth.zeitreise.ai.Guide
import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.ai.GuideUnavailableException
import com.paarth.zeitreise.domain.GuideNotice
import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.StorySource
import com.paarth.zeitreise.domain.WikiArticle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val PLACE = GuidePlace(name = "Australia", description = "a country in Oceania", wikidataId = "Q408")

private val ARTICLE = WikiArticle(
    title = "Australia",
    description = "country in Oceania",
    extract = "Australia is a country.",
    pageUrl = "https://en.wikipedia.org/wiki/Australia",
)

private val AI_STORY = GuideStory(headline = "From the AI", fact = "AI fact.")
private val CURATED_STORY = GuideStory(headline = "Bundled", fact = "Bundled fact.")

/** A Wikipedia that knows only Australia. */
private object FakeWikipedia : WikipediaRepository {
    override suspend fun article(wikidataId: String?, fallbackTitle: String): WikiArticle? =
        ARTICLE.takeIf { wikidataId == PLACE.wikidataId }
}

/** A guide that answers with [story], or fails the way the AI guide does when [failure] is set. */
private class FakeGuide(private val story: GuideStory?, private val failure: Throwable? = null) : Guide {
    var calls = 0

    override suspend fun tell(place: GuidePlace, article: WikiArticle): GuideStory? {
        calls++
        failure?.let { throw it }
        return story
    }
}

class FallbackGuideRepositoryTest {

    @Test
    fun prefersTheAiGuideWhenItAnswers() = runTest {
        val answer = FallbackGuideRepository(FakeWikipedia, FakeGuide(AI_STORY), FakeGuide(CURATED_STORY))
            .storyFor(PLACE)

        assertEquals(AI_STORY, answer?.story)
        assertEquals(StorySource.Ai, answer?.source)
        assertNull(answer?.notice)
    }

    @Test
    fun usesTheBundledStoryWithoutANoticeWhenThereIsNoAiGuide() = runTest {
        val answer = FallbackGuideRepository(FakeWikipedia, aiGuide = null, demoGuide = FakeGuide(CURATED_STORY))
            .storyFor(PLACE)

        assertEquals(StorySource.Curated, answer?.source)
        assertNull(answer?.notice)
    }

    @Test
    fun fallsBackAndSaysSoWhenTheAiIsRateLimited() = runTest {
        val limited = FakeGuide(story = null, failure = GuideUnavailableException(rateLimited = true))

        val answer = FallbackGuideRepository(FakeWikipedia, limited, FakeGuide(CURATED_STORY)).storyFor(PLACE)

        assertEquals(StorySource.Curated, answer?.source)
        assertEquals(GuideNotice.RateLimited, answer?.notice)
    }

    @Test
    fun fallsBackToTheArticleWhenNoGuideHasAStory() = runTest {
        val broken = FakeGuide(story = null, failure = GuideUnavailableException(rateLimited = false))

        val answer = FallbackGuideRepository(FakeWikipedia, broken, FakeGuide(story = null)).storyFor(PLACE)

        assertEquals(StorySource.Wikipedia, answer?.source)
        assertEquals("Australia is a country.", answer?.story?.fact)
        assertEquals(GuideNotice.Unavailable, answer?.notice)
    }

    @Test
    fun hasNothingToSayAboutAPlaceWithoutAnArticle() = runTest {
        val ai = FakeGuide(AI_STORY)

        val answer = FallbackGuideRepository(FakeWikipedia, ai, FakeGuide(CURATED_STORY))
            .storyFor(PLACE.copy(wikidataId = "Q0"))

        assertNull(answer)
        assertEquals(0, ai.calls, "the AI is not asked about a place it cannot be grounded on")
    }

    @Test
    fun alwaysCreditsTheArticleTheStoryIsGroundedOn() = runTest {
        val answer = FallbackGuideRepository(FakeWikipedia, FakeGuide(AI_STORY), FakeGuide(null)).storyFor(PLACE)

        assertEquals(ARTICLE, answer?.article)
    }
}
