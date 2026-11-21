package com.paarth.zeitreise.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.data.QuizStore
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.QuizRound
import com.paarth.zeitreise.domain.quizQuestions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Runs the quiz: asks the questions, checks the answers and saves the best score. It works offline
 * and doesn't use the AI.
 *
 * @param random used to pick the questions. Tests pass a fixed seed.
 */
public class QuizViewModel(private val store: QuizStore, private val random: Random = Random.Default) : ViewModel() {

    private val _state = MutableStateFlow<QuizState>(QuizState.Off)

    /** The quiz state. */
    public val state: StateFlow<QuizState> = _state.asStateFlow()

    /** Starts a new round with questions from [countries]. */
    public fun start(countries: List<Country>) {
        val questions = quizQuestions(countries, random)
        if (questions.isNotEmpty()) _state.value = QuizState.Playing(QuizRound(questions))
    }

    /** Answers with the tapped country. Taps on the ocean (`null`) are ignored. */
    public fun answer(country: Country?) {
        if (country != null) updateRound { it.answer(country) }
    }

    /** Skips the question and shows the answer. */
    public fun reveal() {
        updateRound { it.answer(null) }
    }

    /** Goes to the next question, or ends the round after the last one. */
    public fun next() {
        val round = (_state.value as? QuizState.Playing)?.round ?: return
        if (!round.answered) return
        if (round.isLast) finish(round) else _state.value = QuizState.Playing(round.next())
    }

    /** Ends the quiz. */
    public fun stop() {
        _state.value = QuizState.Off
    }

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
