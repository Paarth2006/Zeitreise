package com.paarth.zeitreise.ui.globe

import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.data.FactCursor
import com.paarth.zeitreise.data.GuideRepository
import kotlinx.coroutines.CancellationException

/**
 * Turns a selected place into what the sheet shows about it, and moves through its facts.
 *
 * Every visit to a place, and every tap on "Next fact", moves on to the next fact, so tapping a
 * place again tells something new rather than repeating itself.
 */
internal class StoryTeller(private val guide: GuideRepository, private val factCursor: FactCursor) {

    /** What to show for [place]: its story, or why there is none; [StoryState.Idle] for no place. */
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

    /** The same story showing its next fact, starting over after the last one. */
    fun nextFact(state: StoryState.Ready): StoryState.Ready =
        state.copy(factIndex = factCursor.next(state.answer.article.title, state.answer.facts.size))
}
