package com.paarth.zeitreise.ui.explore

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Landmark
import com.paarth.zeitreise.domain.PlacesToVisit

/** The state of finding places to visit. */
public sealed interface ExploreState {
    /** Nothing requested yet. */
    public data object Idle : ExploreState

    /** Places for [country] are loading. */
    public data class Loading(val country: Country) : ExploreState

    /** The places found for [country]. */
    public data class Ready(val country: Country, val places: PlacesToVisit) : ExploreState

    /** Wikipedia couldn't be reached for [country]. */
    public data class Failed(val country: Country) : ExploreState
}

/** The places for [country], or none if the state is about another country. */
internal fun ExploreState.landmarksIn(country: Country?): List<Landmark> =
    if (this is ExploreState.Ready && this.country == country) places.landmarks else emptyList()

/** This state if it's for [country], otherwise [ExploreState.Idle]. */
internal fun ExploreState.about(country: Country?): ExploreState = when (this) {
    ExploreState.Idle -> this
    is ExploreState.Loading -> takeIf { this.country == country } ?: ExploreState.Idle
    is ExploreState.Ready -> takeIf { this.country == country } ?: ExploreState.Idle
    is ExploreState.Failed -> takeIf { this.country == country } ?: ExploreState.Idle
}

/**
 * The open place card.
 *
 * @property landmark the place shown.
 * @property facts facts from its article, or `null` while loading.
 */
public data class LandmarkCard(val landmark: Landmark, val facts: List<String>? = null)
