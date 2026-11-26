package com.paarth.zeitreise.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.data.HistoryRepository
import com.paarth.zeitreise.data.QuizStore
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.domain.PAST_QUESTIONS
import com.paarth.zeitreise.domain.QUIZ_LENGTH
import com.paarth.zeitreise.domain.QUIZ_YEARS
import com.paarth.zeitreise.domain.QuizQuestion
import com.paarth.zeitreise.domain.QuizRound
import com.paarth.zeitreise.domain.Territory
import com.paarth.zeitreise.domain.pastQuestion
import com.paarth.zeitreise.domain.quizQuestions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Runs the quiz: asks the questions, checks the answers and saves the best score. It doesn't use
 * the AI. The last [PAST_QUESTIONS] questions of a round are about the past, on maps downloaded
 * while the first ones are answered; offline, they are about today instead.
 *
 * @param random used to pick the questions. Tests pass a fixed seed.
 */
public class QuizViewModel(
    private val store: QuizStore,
    private val history: HistoryRepository,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val _state = MutableStateFlow<QuizState>(QuizState.Off)

    /** The quiz state. */
    public val state: StateFlow<QuizState> = _state.asStateFlow()

    private var pastJob: Job? = null

    /** Starts a new round with questions from [countries], and fetches the maps for its past questions. */
    public fun start(countries: List<Country>) {
        pastJob?.cancel()
        val today = quizQuestions(countries, random, count = QUIZ_LENGTH - PAST_QUESTIONS)
        if (today.isEmpty()) return
        _state.value = QuizState.Playing(QuizRound(today, length = QUIZ_LENGTH))
        pastJob = viewModelScope.launch {
            val past = pastQuestions()
            // Any map that couldn't be loaded is made up for with a country nobody was asked about yet.
            val asked = today.map { it.target }.toSet()
            val extra = quizQuestions(countries.filterNot { it in asked }, random, count = PAST_QUESTIONS - past.size)
            updateRound { it.copy(questions = it.questions + past + extra) }
        }
    }

    /** Answers with the tapped country or realm. Taps on the ocean (`null`) are ignored. */
    public fun answer(place: Territory?) {
        if (place != null) updateRound { it.answer(place) }
    }

    /** Skips the question and shows the answer. */
    public fun reveal() {
        updateRound { it.answer(null) }
    }

    /**
     * Goes to the next question, or ends the round after the last one. If the past questions are
     * still on their way, it waits for them first.
     */
    public fun next() {
        val round = (_state.value as? QuizState.Playing)?.round ?: return
        if (!round.answered) return
        val waiting = pastJob?.takeIf { it.isActive && round.isLast }
        when {
            waiting != null -> viewModelScope.launch {
                waiting.join()
                next()
            }
            round.isLast -> finish(round)
            else -> _state.value = QuizState.Playing(round.next())
        }
    }

    /** Ends the quiz. */
    public fun stop() {
        pastJob?.cancel()
        _state.value = QuizState.Off
    }

    /** A question for each of a few years whose map can be loaded, fetched in parallel. */
    private suspend fun pastQuestions(): List<QuizQuestion> = coroutineScope {
        QUIZ_YEARS.shuffled(random).take(PAST_QUESTIONS)
            .map { year -> async { mapOrNull(year) } }
            .awaitAll()
            .mapNotNull { map -> map?.let { pastQuestion(it, random) } }
    }

    /** The map of [year], or `null` if it can't be downloaded, so the round goes on without it. */
    private suspend fun mapOrNull(year: Int): HistoricalMap? = runCatching { history.mapOf(year) }
        .onFailure { if (it is CancellationException) throw it }
        .getOrNull()

    private fun finish(round: QuizRound) {
        viewModelScope.launch {
            val previousBest = store.bestScore()
            val isNewBest = round.score > previousBest
            if (isNewBest) store.saveBestScore(round.score)
            _state.value = QuizState.Finished(
                score = round.score,
                total = round.questions.size,
                best = maxOf(previousBest, round.score),
                isNewBest = isNewBest,
            )
        }
    }

    private inline fun updateRound(transform: (QuizRound) -> QuizRound) {
        _state.update { state -> if (state is QuizState.Playing) QuizState.Playing(transform(state.round)) else state }
    }
}
