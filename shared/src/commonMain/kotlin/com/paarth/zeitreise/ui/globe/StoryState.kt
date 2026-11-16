package com.paarth.zeitreise.ui.globe

import com.paarth.zeitreise.domain.GuideAnswer
import com.paarth.zeitreise.domain.SourcedFact

/** What the sheet shows below the name of the selected place. */
public sealed interface StoryState {
    /** Nothing is selected, so there is nothing to tell. */
    public data object Idle : StoryState

    /** The guide is reading up on the place. */
    public data object Loading : StoryState

    /**
     * The guide has something to say.
     *
     * @property answer everything the guide knows about the place.
     * @property factIndex which of [answer]'s facts is showing.
     */
    public data class Ready(val answer: GuideAnswer, val factIndex: Int = 0) : StoryState {
        init {
            require(factIndex in answer.facts.indices) { "No fact $factIndex among ${answer.facts.size}" }
        }

        /** The fact showing now. */
        public val fact: SourcedFact get() = answer.facts[factIndex]
    }

    /** Wikipedia has no article on the place, so the guide has nothing to ground a story on. */
    public data object NoArticle : StoryState

    /** Wikipedia could not be reached; the user can try again. */
    public data object Failed : StoryState
}
