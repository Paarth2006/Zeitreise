package com.paarth.zeitreise.ai

import com.paarth.zeitreise.domain.WikiArticle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ARTICLE = WikiArticle(
    title = "Australia",
    description = "country in Oceania",
    extract = "Australia is a country.",
    pageUrl = "https://en.wikipedia.org/wiki/Australia",
)

class DemoGuideTest {

    private var reads = 0
    private val guide = DemoGuide(
        readStories = {
            reads++
            """{ "Q408": { "headline": "Wider than the Moon", "fact": "It is.", "knownFor": ["Uluru"] } }"""
        },
    )

    @Test
    fun tellsTheBundledStoryForAPlaceItKnows() = runTest {
        assertEquals("Wider than the Moon", guide.tell(GuidePlace("Australia", "a country", "Q408"), ARTICLE)?.headline)
    }

    @Test
    fun staysQuietAboutAPlaceItDoesNotKnow() = runTest {
        assertNull(guide.tell(GuidePlace("Fiji", "a country", "Q712"), ARTICLE))
    }

    @Test
    fun staysQuietAboutAPlaceWithoutAWikidataItem() = runTest {
        assertNull(guide.tell(GuidePlace("Nowhere", "a country", wikidataId = null), ARTICLE))
    }

    @Test
    fun readsTheBundledStoriesOnlyOnce() = runTest {
        guide.tell(GuidePlace("Australia", "a country", "Q408"), ARTICLE)
        guide.tell(GuidePlace("Fiji", "a country", "Q712"), ARTICLE)

        assertEquals(1, reads)
    }

    @Test
    fun makesAWikipediaStoryFromTheDescriptionAndOpening() {
        val story = wikipediaStory(ARTICLE)

        assertEquals("Country in Oceania", story.headline)
        assertEquals("Australia is a country.", story.fact)
        assertTrue(story.knownFor.isEmpty())
    }

    @Test
    fun usesTheTitleAsHeadlineWhenThereIsNoDescription() {
        assertEquals("Australia", wikipediaStory(ARTICLE.copy(description = null)).headline)
    }

    @Test
    fun keepsShortTextAsItIs() {
        assertEquals("Short.", leadSentences("  Short.  ", maxLength = 100))
    }

    @Test
    fun cutsLongTextAtTheLastFullSentenceThatFits() {
        val first = "This opening sentence is comfortably longer than forty characters."
        val text = "$first And a second sentence follows it. And a third that will not fit at all."

        assertEquals(first, leadSentences(text, maxLength = first.length + 10))
    }

    @Test
    fun doesNotEndOnAnAbbreviationNearTheStart() {
        val text = "St. Lucia is an island country in the eastern Caribbean Sea, " + "with a long coast. ".repeat(20)

        val lead = leadSentences(text, maxLength = 120)

        assertTrue(lead.length > "St.".length, "cut at the abbreviation: $lead")
    }

    @Test
    fun cutsAtAWordWithAnEllipsisWhenNoSentenceFits() {
        val lead = leadSentences("word ".repeat(100), maxLength = 30)

        assertTrue(lead.endsWith("…"))
        assertTrue(lead.length <= 31)
    }
}
