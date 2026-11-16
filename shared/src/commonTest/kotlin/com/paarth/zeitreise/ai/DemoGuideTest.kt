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
            """
            { "Q408": { "facts": [{ "headline": "Wider than the Moon", "fact": "It is." }], "knownFor": ["Uluru"] } }
            """
        },
        readPlaces = {
            """{ "Q408": { "places": [{ "name": "Uluru", "wikipediaTitle": "Uluru" }] } }"""
        },
    )

    @Test
    fun suggestsTheBundledPlacesForACountryItKnows() = runTest {
        assertEquals(
            listOf(PlaceSuggestion("Uluru", "Uluru")),
            guide.suggestPlaces(GuidePlace("Australia", "a country", "Q408"), ARTICLE),
        )
    }

    @Test
    fun suggestsNoPlacesForACountryItDoesNotKnow() = runTest {
        assertNull(guide.suggestPlaces(GuidePlace("Fiji", "a country", "Q712"), ARTICLE))
    }

    @Test
    fun tellsTheBundledStoryForAPlaceItKnows() = runTest {
        assertEquals(
            "Wider than the Moon",
            guide.tell(GuidePlace("Australia", "a country", "Q408"), ARTICLE, emptyList())?.facts?.single()?.headline,
        )
    }

    @Test
    fun staysQuietAboutAPlaceItDoesNotKnow() = runTest {
        assertNull(guide.tell(GuidePlace("Fiji", "a country", "Q712"), ARTICLE, emptyList()))
    }

    @Test
    fun staysQuietAboutAPlaceWithoutAWikidataItem() = runTest {
        assertNull(guide.tell(GuidePlace("Nowhere", "a country", wikidataId = null), ARTICLE, emptyList()))
    }

    @Test
    fun readsTheBundledStoriesOnlyOnce() = runTest {
        guide.tell(GuidePlace("Australia", "a country", "Q408"), ARTICLE, emptyList())
        guide.tell(GuidePlace("Fiji", "a country", "Q712"), ARTICLE, emptyList())

        assertEquals(1, reads)
    }

    @Test
    fun turnsTheArticlesNotableSentencesIntoFactsWithoutHeadlines() {
        val facts = wikipediaFacts(ARTICLE, listOf("First notable sentence.", "Second notable sentence."))

        assertEquals(listOf("First notable sentence.", "Second notable sentence."), facts.map { it.text })
        assertTrue(facts.all { it.headline == null })
    }

    @Test
    fun fallsBackToTheOpeningUnderTheDescriptionWithoutNotableSentences() {
        val fact = wikipediaFacts(ARTICLE, emptyList()).single()

        assertEquals("Country in Oceania", fact.headline)
        assertEquals("Australia is a country.", fact.text)
    }

    @Test
    fun usesTheTitleAsHeadlineWhenThereIsNoDescription() {
        assertEquals("Australia", wikipediaFacts(ARTICLE.copy(description = null), emptyList()).single().headline)
    }

    @Test
    fun closesTheGapsWikipediaLeavesWhereItStripsANativeName() {
        assertEquals("Shiretoko National Park covers it.", leadSentences("Shiretoko National Park  covers it."))
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
