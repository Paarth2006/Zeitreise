package com.paarth.zeitreise.ui.globe

import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.data.GuideRepository
import com.paarth.zeitreise.data.SettingsFactCursor
import com.paarth.zeitreise.domain.GuideAnswer
import com.paarth.zeitreise.domain.GuideFact
import com.paarth.zeitreise.domain.SourcedFact
import com.paarth.zeitreise.domain.StorySource
import com.paarth.zeitreise.domain.WikiArticle
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val PLACE = GuidePlace(name = "India", description = "a country in Asia", wikidataId = "Q668")

private val ANSWER = GuideAnswer(
    facts = listOf("One", "Two", "Three").map { SourcedFact(GuideFact(text = it), StorySource.Wikipedia) },
    knownFor = emptyList(),
    article = WikiArticle(title = "India", extract = "India.", pageUrl = "https://en.wikipedia.org/wiki/India"),
)

/** A guide that answers [answer], or throws [failure] when it is set. */
private class FakeGuideRepository(private val answer: GuideAnswer?, private val failure: Exception? = null) :
    GuideRepository {
    override suspend fun storyFor(place: GuidePlace): GuideAnswer? {
        failure?.let { throw it }
        return answer
    }
}

class StoryTellerTest {

    private val cursor = SettingsFactCursor(MapSettings())

    @Test
    fun hasNothingToTellWithoutAPlace() = runTest {
        assertEquals(StoryState.Idle, StoryTeller(FakeGuideRepository(ANSWER), cursor).storyFor(null))
    }

    @Test
    fun showsANewFactOnEachVisit() = runTest {
        val teller = StoryTeller(FakeGuideRepository(ANSWER), cursor)

        val shown = List(4) { (teller.storyFor(PLACE) as StoryState.Ready).fact.fact.text }

        assertEquals(listOf("One", "Two", "Three", "One"), shown)
    }

    @Test
    fun movesToTheNextFactOnRequest() = runTest {
        val teller = StoryTeller(FakeGuideRepository(ANSWER), cursor)
        val first = assertIs<StoryState.Ready>(teller.storyFor(PLACE))

        assertEquals("Two", teller.nextFact(first).fact.fact.text)
    }

    @Test
    fun saysSoWhenThereIsNoArticle() = runTest {
        assertEquals(StoryState.NoArticle, StoryTeller(FakeGuideRepository(answer = null), cursor).storyFor(PLACE))
    }

    @Test
    fun reportsAFailureInsteadOfThrowing() = runTest {
        val down = FakeGuideRepository(answer = null, failure = IllegalStateException("Wikipedia is down"))

        assertEquals(StoryState.Failed, StoryTeller(down, cursor).storyFor(PLACE))
    }
}
