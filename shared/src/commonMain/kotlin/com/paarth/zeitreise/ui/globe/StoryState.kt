package com.paarth.zeitreise.ui.globe

import com.paarth.zeitreise.domain.GuideAnswer
import com.paarth.zeitreise.domain.SourcedFact

/** What the sheet shows below the place's name. */
public sealed interface StoryState {
    /** Nothing is selected. */
    public data object Idle : StoryState

    /** The story is loading. */
    public data object Loading : StoryState

    /**
     * The guide has something to say.
     *
     * @property answer the guide's answer.
     * @property factIndex index of the fact being shown.
     */
    public data class Ready(val answer: GuideAnswer, val factIndex: Int = 0) : StoryState {
        init {
            require(factIndex in answer.facts.indices) { "No fact $factIndex among ${answer.facts.size}" }
        }

        /** The fact being shown. */
        public val fact: SourcedFact get() = answer.facts[factIndex]
    }

    /** There is no Wikipedia article for the place. */
    public data object NoArticle : StoryState

    /** Wikipedia couldn't be reached. */
    public data object Failed : StoryState
}
