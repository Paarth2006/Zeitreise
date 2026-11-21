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

        assertSame(eastland, next.country)
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

        assertEquals(listOf("Bigland"), questions.map { it.country.name })
    }

    @Test
    fun asksForDifferentCountriesUpToTheRoundLength() {
        val questions = quizQuestions(ATLANTIS, Random(seed = 7), count = 2)

        assertEquals(2, questions.size)
        assertEquals(2, questions.map { it.country }.toSet().size)
    }

    @Test
    fun asksByCapitalOnlyForCountriesThatHaveOne() {
        val withCapitals = ATLANTIS.take(2).map { it.withFacts(CountryFacts(capitals = listOf("${it.name} City"))) }
        val countries = withCapitals + ATLANTIS[2]

        val questions = (1L..20L).flatMap { seed -> quizQuestions(countries, Random(seed)) }

        assertTrue(questions.none { it.country.name == "Northland" && it.capital != null })
        assertTrue(questions.any { it.capital == "Westland City" })
        assertTrue(questions.any { it.country.name == "Westland" && it.capital == null })
    }
}
