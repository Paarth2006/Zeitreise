package com.paarth.zeitreise.ui.quiz

import com.paarth.zeitreise.domain.QuizRound

/** The state of the quiz. */
public sealed interface QuizState {
    /** No quiz is running. */
    public data object Off : QuizState

    /** A round is in progress. */
    public data class Playing(val round: QuizRound) : QuizState

    /**
     * A round has finished.
     *
     * @property score the number of correct answers.
     * @property total the number of questions.
     * @property best the best score, including this round.
     * @property isNewBest whether this round is a new best.
     */
    public data class Finished(val score: Int, val total: Int, val best: Int, val isNewBest: Boolean) : QuizState
}
