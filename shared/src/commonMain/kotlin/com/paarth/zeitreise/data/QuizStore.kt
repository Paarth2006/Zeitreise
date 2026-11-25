package com.paarth.zeitreise.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Settings key for the best score. */
internal const val BEST_SCORE_KEY = "quiz_best_score"

/** Remembers the best quiz score. */
public interface QuizStore {
    /** The best score so far, or 0 if no round was played. */
    public suspend fun bestScore(): Int

    /** Saves [score] as the new best. */
    public suspend fun saveBestScore(score: Int)
}

/**
 * Saves the best score in the platform's key-value storage.
 *
 * @param dispatcher where reads and writes run, since the first read may hit the disk.
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
