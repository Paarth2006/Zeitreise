package com.paarth.zeitreise.ui.globe

import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.data.FactCursor
import com.paarth.zeitreise.data.GuideRepository
import kotlinx.coroutines.CancellationException

/**
 * Loads the story for a selected place and moves through its facts. Each visit and each tap on
 * "Next fact" shows the next fact, so the same place shows something new next time.
 */
internal class StoryTeller(private val guide: GuideRepository, private val factCursor: FactCursor) {

    /** The story state for [place], or [StoryState.Idle] if it's `null`. */
    suspend fun storyFor(place: GuidePlace?): StoryState {
        if (place == null) return StoryState.Idle
        val answer = runCatching { guide.storyFor(place) }
            .onFailure { if (it is CancellationException) throw it }
        return answer.fold(
            onSuccess = { found ->
                found?.let { StoryState.Ready(it, factCursor.next(it.article.title, it.facts.size)) }
                    ?: StoryState.NoArticle
            },
            onFailure = { StoryState.Failed },
        )
    }

    /** The same story showing its next fact, wrapping around after the last. */
    fun nextFact(state: StoryState.Ready): StoryState.Ready =
        state.copy(factIndex = factCursor.next(state.answer.article.title, state.answer.facts.size))
}
