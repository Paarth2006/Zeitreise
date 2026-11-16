package com.paarth.zeitreise.data

import com.paarth.zeitreise.ai.Guide
import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.ai.GuideUnavailableException
import com.paarth.zeitreise.ai.PlaceSuggestion
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.CountryOutline
import com.paarth.zeitreise.domain.CountryRing
import com.paarth.zeitreise.domain.GuideNotice
import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.StorySource
import com.paarth.zeitreise.domain.WikiArticle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A square country from 10° to 20° north and east. */
private val SQUARELAND = Country(
    name = "Squareland",
    isoA2 = null,
    isoA3 = null,
    continent = "Atlantis",
    outlines = listOf(
        CountryOutline(
            exterior = CountryRing(doubleArrayOf(10.0, 10.0, 20.0, 10.0, 20.0, 20.0, 10.0, 20.0, 10.0, 10.0)),
            holes = emptyList(),
        ),
    ),
    wikidataId = "Q1",
)

private fun article(title: String, latitude: Double? = null, longitude: Double? = null) = WikiArticle(
    title = title,
    extract = "$title is a place.",
    pageUrl = "https://en.wikipedia.org/wiki/$title",
    latitude = latitude,
    longitude = longitude,
)

/** A Wikipedia with an article on Squareland and on a few places in and out of it. */
private class SquarelandWikipedia(private val down: Set<String> = emptySet()) : WikipediaRepository {
    private val articles = listOf(
        article("Squareland"),
        article("Square Tower", latitude = 15.0, longitude = 15.0),
        article("Corner Falls", latitude = 11.0, longitude = 19.0),
        article("Faraway Castle", latitude = -40.0, longitude = 120.0),
        article("Placeless Idea"),
    ).associateBy { it.title }

    override suspend fun article(wikidataId: String?, fallbackTitle: String): WikiArticle? {
        check(fallbackTitle !in down) { "Wikipedia is down for $fallbackTitle" }
        return articles[fallbackTitle]
    }

    override suspend fun notableFacts(article: WikiArticle): List<String> =
        listOf("First fact.", "Second fact.", "Third fact.", "Fourth fact.")
}

/** A guide that suggests [places], or fails the way the AI guide does when [failure] is set. */
private class PlacesGuide(private val places: List<PlaceSuggestion>?, private val failure: Throwable? = null) : Guide {
    override suspend fun tell(place: GuidePlace, article: WikiArticle, notableFacts: List<String>): GuideStory? = null

    override suspend fun suggestPlaces(country: GuidePlace, article: WikiArticle): List<PlaceSuggestion>? {
        failure?.let { throw it }
        return places
    }
}

private fun suggest(vararg titles: String) = titles.map { PlaceSuggestion(name = "The $it", wikipediaTitle = it) }

class ExploreRepositoryTest {

    @Test
    fun pinsTheAiGuidesPlacesWhereWikipediaPutsThem() = runTest {
        val places = GuidedExploreRepository(
            wikipedia = SquarelandWikipedia(),
            aiGuide = PlacesGuide(suggest("Square Tower", "Corner Falls")),
            demoGuide = PlacesGuide(suggest("Corner Falls")),
        ).placesToVisit(SQUARELAND)

        assertEquals(listOf("The Square Tower", "The Corner Falls"), places.landmarks.map { it.name })
        assertEquals(15.0, places.landmarks.first().location.latitude)
        assertEquals(StorySource.Ai, places.source)
        assertNull(places.notice)
    }

    @Test
    fun dropsPlacesWithoutAnArticleCoordinatesOrAPlaceInTheCountry() = runTest {
        val places = GuidedExploreRepository(
            wikipedia = SquarelandWikipedia(),
            aiGuide = PlacesGuide(suggest("Made Up Place", "Placeless Idea", "Faraway Castle", "Square Tower")),
            demoGuide = PlacesGuide(null),
        ).placesToVisit(SQUARELAND)

        assertEquals(listOf("Square Tower"), places.landmarks.map { it.article.title })
    }

    @Test
    fun dropsAPlaceWhoseArticleCannotBeReadButKeepsTheRest() = runTest {
        val places = GuidedExploreRepository(
            wikipedia = SquarelandWikipedia(down = setOf("Corner Falls")),
            aiGuide = PlacesGuide(suggest("Square Tower", "Corner Falls")),
            demoGuide = PlacesGuide(null),
        ).placesToVisit(SQUARELAND)

        assertEquals(listOf("Square Tower"), places.landmarks.map { it.article.title })
    }

    @Test
    fun pinsEachArticleOnce() = runTest {
        val places = GuidedExploreRepository(
            wikipedia = SquarelandWikipedia(),
            aiGuide = PlacesGuide(suggest("Square Tower") + PlaceSuggestion("Tower", "Square Tower")),
            demoGuide = PlacesGuide(null),
        ).placesToVisit(SQUARELAND)

        assertEquals(1, places.landmarks.size)
    }

    @Test
    fun usesTheBundledPlacesWhenThereIsNoAiGuide() = runTest {
        val places = GuidedExploreRepository(
            wikipedia = SquarelandWikipedia(),
            aiGuide = null,
            demoGuide = PlacesGuide(suggest("Corner Falls")),
        ).placesToVisit(SQUARELAND)

        assertEquals(listOf("Corner Falls"), places.landmarks.map { it.article.title })
        assertEquals(StorySource.Curated, places.source)
        assertNull(places.notice)
    }

    @Test
    fun fallsBackAndSaysSoWhenTheAiIsRateLimited() = runTest {
        val places = GuidedExploreRepository(
            wikipedia = SquarelandWikipedia(),
            aiGuide = PlacesGuide(places = null, failure = GuideUnavailableException(rateLimited = true)),
            demoGuide = PlacesGuide(suggest("Corner Falls")),
        ).placesToVisit(SQUARELAND)

        assertEquals(StorySource.Curated, places.source)
        assertEquals(GuideNotice.RateLimited, places.notice)
        assertEquals(1, places.landmarks.size)
    }

    @Test
    fun findsNoPlacesWhenNoGuideKnowsTheCountry() = runTest {
        val places = GuidedExploreRepository(SquarelandWikipedia(), aiGuide = null, demoGuide = PlacesGuide(null))
            .placesToVisit(SQUARELAND)

        assertTrue(places.landmarks.isEmpty())
    }

    @Test
    fun treatsAFactThatRetellsTheSummaryAsARepeat() {
        val summary = "Shiretoko National Park covers most of the Shiretoko Peninsula at the tip of Hokkaido."

        assertTrue(repeats("Shiretoko National Park (Shiretoko Kokuritsu Kōen) covers most of the peninsula.", summary))
        assertFalse(repeats("Brown bears outnumber the people who live on the peninsula in summer.", summary))
    }

    @Test
    fun takesThreeFactsForAPlaceCard() = runTest {
        val repository = GuidedExploreRepository(SquarelandWikipedia(), aiGuide = null, demoGuide = PlacesGuide(null))
        val landmark = GuidedExploreRepository(SquarelandWikipedia(), null, PlacesGuide(suggest("Square Tower")))
            .placesToVisit(SQUARELAND)
            .landmarks
            .single()

        assertEquals(listOf("First fact.", "Second fact.", "Third fact."), repository.factsAbout(landmark))
    }
}
