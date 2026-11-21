package com.paarth.zeitreise.ui.quiz

import com.paarth.zeitreise.domain.QuizRound

/** Whether the quiz is being played, and if so how far it has got. */
public sealed interface QuizState {
    /** No quiz: the globe is free to explore. */
    public data object Off : QuizState

    /** A round under way. */
    public data class Playing(val round: QuizRound) : QuizState

    /**
     * A round just finished.
     *
     * @property score how many countries the user found.
     * @property total how many the round asked for.
     * @property best the best score so far, counting this round.
     * @property isNewBest whether this round beat every earlier one.
     */
    public data class Finished(val score: Int, val total: Int, val best: Int, val isNewBest: Boolean) : QuizState
}
