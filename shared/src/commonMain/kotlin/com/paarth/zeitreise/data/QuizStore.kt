package com.paarth.zeitreise.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Key under which the best quiz score is remembered. */
private const val BEST_SCORE_KEY = "quiz_best_score"

/** Remembers the best score the user has reached in a round of the quiz. */
public interface QuizStore {
    /** The best score so far, or 0 before the first round. */
    public suspend fun bestScore(): Int

    /** Records [score] as the best so far. */
    public suspend fun saveBestScore(score: Int)
}

/**
 * Keeps the best score in the platform's key-value store, so it survives the app being closed.
 *
 * @param dispatcher dispatcher the reads and writes run on, since the first read may touch disk.
 */
internal class SettingsQuizStore(private val settings: Settings, private val dispatcher: CoroutineDispatcher) :
    QuizStore {

    override suspend fun bestScore(): Int = withContext(dispatcher) {
        settings.getInt(BEST_SCORE_KEY, defaultValue = 0)
    }

    override suspend fun saveBestScore(score: Int): Unit = withContext(dispatcher) {
        settings.putInt(BEST_SCORE_KEY, score)
    }
}
