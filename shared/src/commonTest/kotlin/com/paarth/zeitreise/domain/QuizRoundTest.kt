package com.paarth.zeitreise.domain

import com.paarth.zeitreise.geo.GeoJsonParser
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A square territory [size] degrees across whose south-west corner is at [origin] degrees. */
private fun quizTerritory(name: String, isoA2: String?, origin: Int, size: Double = 4.0): String {
    val code = isoA2?.let { "\"$it\"" } ?: "null"
    val far = origin + size
    return """
        {
          "type": "Feature",
          "properties": { "name": "$name", "isoA2": $code, "isoA3": null, "continent": "Atlantis" },
          "geometry": { "type": "Polygon", "coordinates": [[
            [$origin, $origin], [$far, $origin], [$far, $far], [$origin, $far], [$origin, $origin]
          ]] }
        }
    """.trimIndent()
}

private fun quizCountries(vararg features: String) =
    GeoJsonParser().parseCountries("""{ "type": "FeatureCollection", "features": [${features.joinToString(",")}] }""")

private val ATLANTIS = quizCountries(
    quizTerritory("Westland", "WL", origin = 0),
    quizTerritory("Eastland", "EL", origin = 10),
    quizTerritory("Northland", "NL", origin = 20),
)

class QuizRoundTest {

    private val westland = ATLANTIS[0]
    private val eastland = ATLANTIS[1]
    private val round = QuizRound(questions = listOf(QuizQuestion(westland), QuizQuestion(eastland)))

    @Test
    fun scoresARightAnswerAndCountsTheStreak() {
        val answered = round.answer(westland)

        assertTrue(answered.isRight)
        assertEquals(1, answered.score)
        assertEquals(1, answered.streak)
    }

    @Test
    fun aWrongAnswerScoresNothingAndBreaksTheStreak() {
        val answered = round.answer(westland).next().answer(westland)

        assertFalse(answered.isRight)
        assertSame(westland, answered.guess)
        assertEquals(1, answered.score)
        assertEquals(0, answered.streak)
    }

    @Test
    fun givingUpShowsTheCountryWithoutAGuess() {
        val answered = round.answer(null)

        assertTrue(answered.answered)
        assertNull(answered.guess)
        assertEquals(0, answered.score)
    }

    @Test
    fun takesOnlyTheFirstAnswerToAQuestion() {
        val answered = round.answer(eastland).answer(westland)

        assertSame(eastland, answered.guess)
        assertEquals(0, answered.score)
    }

    @Test
    fun movesOnToTheNextQuestionWithItsAnswerCleared() {
        val next = round.answer(westland).next()

        assertSame(eastland, next.target)
        assertFalse(next.answered)
        assertNull(next.guess)
        assertTrue(next.isLast)
    }

    @Test
    fun cannotSkipAQuestionWithoutAnsweringIt() {
        assertFailsWith<IllegalStateException> { round.next() }
    }

    @Test
    fun asksOnlyForCountriesBigEnoughToTapAndWithACode() {
        val countries = quizCountries(
            quizTerritory("Bigland", "BL", origin = 0),
            quizTerritory("Speck", "SP", origin = 10, size = 0.5),
            quizTerritory("Nowhere", isoA2 = null, origin = 20),
        )

        val questions = quizQuestions(countries, Random(seed = 1))

        assertEquals(listOf("Bigland"), questions.map { it.target.name })
    }

    @Test
    fun asksForDifferentCountriesUpToTheRoundLength() {
        val questions = quizQuestions(ATLANTIS, Random(seed = 7), count = 2)

        assertEquals(2, questions.size)
        assertEquals(2, questions.map { it.target }.toSet().size)
    }

    @Test
    fun asksByCapitalOnlyForCountriesThatHaveOne() {
        val withCapitals = ATLANTIS.take(2).map { it.withFacts(CountryFacts(capitals = listOf("${it.name} City"))) }
        val countries = withCapitals + ATLANTIS[2]

        val questions = (1L..20L).flatMap { seed -> quizQuestions(countries, Random(seed)) }

        assertTrue(questions.none { it.target.name == "Northland" && it.capital != null })
        assertTrue(questions.any { it.capital == "Westland City" })
        assertTrue(questions.any { it.target.name == "Westland" && it.capital == null })
    }

    @Test
    fun asksAboutTheBigIndependentRealmsOfThePast() {
        fun realm(name: String, partOf: String? = null, size: Double = 1.0) = Realm(
            name = name,
            partOf = partOf,
            outlines = listOf(
                CountryOutline(CountryRing(doubleArrayOf(0.0, 0.0, size, 0.0, size, size, 0.0, 0.0)), emptyList()),
            ),
        )
        // The groups of peoples are bigger, but they aren't realms to find.
        val map = HistoricalMap(
            year = 1600,
            realms = listOf(
                realm("Amazon hunter-gatherers", size = 30.0),
                realm("central Asian khanates", size = 20.0),
                realm("Wallachia", partOf = "Ottoman Empire"),
                realm("Ottoman Empire", size = 10.0),
            ),
            unclaimedLand = emptyList(),
        )

        val questions = List(20) { pastQuestion(map, Random(it)) }

        assertTrue(questions.all { it?.target?.name == "Ottoman Empire" && it.map === map })
    }

    @Test
    fun hasNoPastQuestionWhenTheMapHasNoRealms() {
        assertNull(pastQuestion(HistoricalMap(year = 1, realms = emptyList(), unclaimedLand = emptyList()), Random(1)))
    }
}
