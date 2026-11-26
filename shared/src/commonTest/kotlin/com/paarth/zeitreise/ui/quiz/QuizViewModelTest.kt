package com.paarth.zeitreise.ui.quiz

import com.paarth.zeitreise.data.HistoryRepository
import com.paarth.zeitreise.data.QuizStore
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.CountryOutline
import com.paarth.zeitreise.domain.CountryRing
import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.domain.PAST_QUESTIONS
import com.paarth.zeitreise.domain.QUIZ_LENGTH
import com.paarth.zeitreise.domain.Realm
import com.paarth.zeitreise.geo.GeoJsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [QUIZ_LENGTH] + 2 square countries along the equator, all big enough for the quiz. */
private val WORLD: List<Country> = GeoJsonParser().parseCountries(
    (0 until QUIZ_LENGTH + 2).joinToString(
        prefix = """{ "type": "FeatureCollection", "features": [""",
        postfix = "]}",
    ) {
        val west = it * 10
        val east = west + 4
        """
            {
              "type": "Feature",
              "properties": { "name": "Land $it", "isoA2": "L$it", "isoA3": null, "continent": "Atlantis" },
              "geometry": { "type": "Polygon", "coordinates": [[
                [$west, 0], [$east, 0], [$east, 4], [$west, 4], [$west, 0]
              ]] }
            }
        """.trimIndent()
    },
)

/** An empire on the old maps, from 10° to 14° east and 40° to 44° north. */
private val ROME = Realm(
    name = "Rome",
    partOf = null,
    outlines = listOf(
        CountryOutline(
            CountryRing(doubleArrayOf(10.0, 40.0, 14.0, 40.0, 14.0, 44.0, 10.0, 44.0, 10.0, 40.0)),
            emptyList(),
        ),
    ),
)

/** A history whose maps all show [ROME], or that can't be reached when [down]. */
private class FakeHistory(private val down: Boolean) : HistoryRepository {
    override suspend fun mapOf(year: Int): HistoricalMap {
        check(!down) { "No connection" }
        return HistoricalMap(year = year, realms = listOf(ROME), unclaimedLand = emptyList())
    }
}

/** A store that keeps the best score in memory, starting from [best]. */
private class MemoryQuizStore(var best: Int = 0) : QuizStore {
    override suspend fun bestScore() = best

    override suspend fun saveBestScore(score: Int) {
        best = score
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    private val store = MemoryQuizStore(best = 3)

    /**
     * Runs [test] with the view model's main dispatcher on the test's scheduler. The old maps can't
     * be loaded unless [online], so rounds are about today's countries only.
     */
    private fun quiz(online: Boolean = false, test: suspend TestScope.(QuizViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            test(QuizViewModel(store, FakeHistory(down = !online), Random(seed = 42)))
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private val QuizViewModel.round get() = assertIs<QuizState.Playing>(state.value).round

    @Test
    fun startsARoundOfDifferentCountries() = quiz { viewModel ->
        viewModel.start(WORLD)
        advanceUntilIdle()

        assertEquals(QUIZ_LENGTH, viewModel.round.questions.toSet().size)
        assertEquals(0, viewModel.round.number)
    }

    @Test
    fun aTapOnTheOceanDoesNotAnswer() = quiz { viewModel ->
        viewModel.start(WORLD)
        advanceUntilIdle()

        viewModel.answer(null)

        assertEquals(false, viewModel.round.answered)
    }

    @Test
    fun showsTheCountryWhenTheUserGivesUp() = quiz { viewModel ->
        viewModel.start(WORLD)
        advanceUntilIdle()

        viewModel.reveal()

        assertTrue(viewModel.round.answered)
        assertNull(viewModel.round.guess)
    }

    @Test
    fun waitsForAnAnswerBeforeMovingOn() = quiz { viewModel ->
        viewModel.start(WORLD)
        advanceUntilIdle()

        viewModel.next()

        assertEquals(0, viewModel.round.number)
    }

    @Test
    fun endsTheRoundWithItsScoreAndRemembersANewBest() = quiz { viewModel ->
        viewModel.start(WORLD)
        advanceUntilIdle()
        repeat(QUIZ_LENGTH) {
            viewModel.answer(viewModel.round.target)
            viewModel.next()
        }
        advanceUntilIdle()

        assertEquals(QuizState.Finished(score = 10, total = 10, best = 10, isNewBest = true), viewModel.state.value)
        assertEquals(10, store.best)
    }

    @Test
    fun keepsTheEarlierBestAfterAWeakerRound() = quiz { viewModel ->
        viewModel.start(WORLD)
        advanceUntilIdle()
        repeat(QUIZ_LENGTH) {
            viewModel.reveal()
            viewModel.next()
        }
        advanceUntilIdle()

        assertEquals(QuizState.Finished(score = 0, total = 10, best = 3, isNewBest = false), viewModel.state.value)
        assertEquals(3, store.best)
    }

    @Test
    fun leavesTheQuiz() = quiz { viewModel ->
        viewModel.start(WORLD)
        advanceUntilIdle()

        viewModel.stop()

        assertEquals(QuizState.Off, viewModel.state.value)
    }

    @Test
    fun endsEachRoundWithQuestionsAboutThePast() = quiz(online = true) { viewModel ->
        viewModel.start(WORLD)
        advanceUntilIdle()

        val past = viewModel.round.questions.takeLast(PAST_QUESTIONS)
        assertEquals(QUIZ_LENGTH, viewModel.round.questions.size)
        assertTrue(past.all { it.target === ROME && it.map != null })
        assertTrue(viewModel.round.questions.dropLast(PAST_QUESTIONS).all { it.map == null })
    }

    @Test
    fun tappingTheRealmAnswersAQuestionAboutThePast() = quiz(online = true) { viewModel ->
        viewModel.start(WORLD)
        advanceUntilIdle()
        repeat(QUIZ_LENGTH - PAST_QUESTIONS) {
            viewModel.reveal()
            viewModel.next()
        }

        viewModel.answer(ROME)

        assertTrue(viewModel.round.isRight)
    }

    @Test
    fun waitsForThePastQuestionsInsteadOfEndingEarly() = quiz(online = true) { viewModel ->
        viewModel.start(WORLD)
        repeat(QUIZ_LENGTH - PAST_QUESTIONS) {
            viewModel.reveal()
            viewModel.next()
        }
        advanceUntilIdle()

        assertEquals(QUIZ_LENGTH - PAST_QUESTIONS, viewModel.round.number)
        assertIs<QuizState.Playing>(viewModel.state.value)
    }

    @Test
    fun asksAboutTodayInsteadWhenTheOldMapsCannotBeLoaded() = quiz(online = false) { viewModel ->
        viewModel.start(WORLD)
        advanceUntilIdle()

        assertEquals(QUIZ_LENGTH, viewModel.round.questions.map { it.target }.toSet().size)
        assertTrue(viewModel.round.questions.all { it.map == null })
    }
}
