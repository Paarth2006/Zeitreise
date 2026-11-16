package com.paarth.zeitreise.ui.globe

import com.paarth.zeitreise.domain.GuideAnswer

/** What the sheet shows below the name of the selected place. */
public sealed interface StoryState {
    /** Nothing is selected, so there is nothing to tell. */
    public data object Idle : StoryState

    /** The guide is reading up on the place. */
    public data object Loading : StoryState

    /** The guide has something to say. */
    public data class Ready(val answer: GuideAnswer) : StoryState

    /** Wikipedia has no article on the place, so the guide has nothing to ground a story on. */
    public data object NoArticle : StoryState

    /** Wikipedia could not be reached; the user can try again. */
    public data object Failed : StoryState
}
