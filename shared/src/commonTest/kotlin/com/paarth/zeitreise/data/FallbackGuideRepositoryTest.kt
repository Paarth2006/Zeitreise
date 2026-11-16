package com.paarth.zeitreise.data

import com.paarth.zeitreise.ai.Guide
import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.ai.GuideUnavailableException
import com.paarth.zeitreise.domain.GuideFact
import com.paarth.zeitreise.domain.GuideNotice
import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.SourcedFact
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

private val AI_STORY = GuideStory(facts = listOf(GuideFact("From the AI", "AI fact.")), knownFor = listOf("AI pick"))
private val CURATED_STORY =
    GuideStory(facts = listOf(GuideFact("Bundled", "Bundled fact.")), knownFor = listOf("Uluru"))
private val NOTABLE = listOf("Australia has the largest ancient reef in the world, which is 2,300 km long.")

/** A Wikipedia that knows only Australia. */
private object FakeWikipedia : WikipediaRepository {
    override suspend fun article(wikidataId: String?, fallbackTitle: String): WikiArticle? =
        ARTICLE.takeIf { wikidataId == PLACE.wikidataId }

    override suspend fun notableFacts(article: WikiArticle): List<String> = NOTABLE
}

/** A guide that answers with [story], or fails the way the AI guide does when [failure] is set. */
private class FakeGuide(private val story: GuideStory?, private val failure: Throwable? = null) : Guide {
    var calls = 0

    var notableSeen: List<String>? = null

    override suspend fun tell(place: GuidePlace, article: WikiArticle, notableFacts: List<String>): GuideStory? {
        notableSeen = notableFacts
        calls++
        failure?.let { throw it }
        return story
    }
}

class FallbackGuideRepositoryTest {

    @Test
    fun prefersTheAiGuidesFactsWhenItAnswers() = runTest {
        val ai = FakeGuide(AI_STORY)

        val answer = FallbackGuideRepository(FakeWikipedia, ai, FakeGuide(CURATED_STORY)).storyFor(PLACE)

        assertEquals(listOf(SourcedFact(AI_STORY.facts.single(), StorySource.Ai)), answer?.facts)
        assertEquals(listOf("AI pick"), answer?.knownFor)
        assertNull(answer?.notice)
        assertEquals(NOTABLE, ai.notableSeen, "the AI is grounded on the article's notable sentences too")
    }

    @Test
    fun followsTheBundledFactWithTheArticlesOwnWhenThereIsNoAiGuide() = runTest {
        val answer = FallbackGuideRepository(FakeWikipedia, aiGuide = null, demoGuide = FakeGuide(CURATED_STORY))
            .storyFor(PLACE)

        assertEquals(listOf(StorySource.Curated, StorySource.Wikipedia), answer?.facts?.map { it.source })
        assertEquals(NOTABLE.single(), answer?.facts?.last()?.fact?.text)
        assertEquals(listOf("Uluru"), answer?.knownFor)
        assertNull(answer?.notice)
    }

    @Test
    fun fallsBackAndSaysSoWhenTheAiIsRateLimited() = runTest {
        val limited = FakeGuide(story = null, failure = GuideUnavailableException(rateLimited = true))

        val answer = FallbackGuideRepository(FakeWikipedia, limited, FakeGuide(CURATED_STORY)).storyFor(PLACE)

        assertEquals(StorySource.Curated, answer?.facts?.first()?.source)
        assertEquals(GuideNotice.RateLimited, answer?.notice)
    }

    @Test
    fun fallsBackToTheArticleWhenNoGuideHasAStory() = runTest {
        val broken = FakeGuide(story = null, failure = GuideUnavailableException(rateLimited = false))

        val answer = FallbackGuideRepository(FakeWikipedia, broken, FakeGuide(story = null)).storyFor(PLACE)

        assertEquals(listOf(StorySource.Wikipedia), answer?.facts?.map { it.source })
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
    fun alwaysCreditsTheArticleTheFactsAreGroundedOn() = runTest {
        val answer = FallbackGuideRepository(FakeWikipedia, FakeGuide(AI_STORY), FakeGuide(null)).storyFor(PLACE)

        assertEquals(ARTICLE, answer?.article)
    }
}
