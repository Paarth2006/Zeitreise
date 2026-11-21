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
 * Runs the quiz: asks for one country after another, scores the user's taps on the globe, and
 * remembers the best round.
 *
 * The quiz needs nothing from the network, so it plays offline and never spends the AI guide's quota.
 *
 * @param random where the questions' order comes from; fixed in tests.
 */
public class QuizViewModel(private val store: QuizStore, private val random: Random = Random.Default) : ViewModel() {

    private val _state = MutableStateFlow<QuizState>(QuizState.Off)

    /** Whether the quiz is on, and if so the round under way or its result. */
    public val state: StateFlow<QuizState> = _state.asStateFlow()

    /** Starts a new round asking for some of [countries]. */
    public fun start(countries: List<Country>) {
        val questions = quizQuestions(countries, random)
        if (questions.isNotEmpty()) _state.value = QuizState.Playing(QuizRound(questions))
    }

    /** Answers the current question with the country the user tapped; taps on the ocean don't count. */
    public fun answer(country: Country?) {
        if (country != null) updateRound { it.answer(country) }
    }

    /** Gives the current question up and shows where the country is. */
    public fun reveal() {
        updateRound { it.answer(null) }
    }

    /** Asks the next question, or ends the round after the last one. */
    public fun next() {
        val round = (_state.value as? QuizState.Playing)?.round ?: return
        if (!round.answered) return
        if (round.isLast) finish(round) else _state.value = QuizState.Playing(round.next())
    }

    /** Leaves the quiz, back to exploring the globe. */
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
