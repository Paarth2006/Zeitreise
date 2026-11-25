package com.paarth.zeitreise.ui.explore

import com.paarth.zeitreise.domain.Landmark
import com.paarth.zeitreise.domain.PlacesToVisit
import com.paarth.zeitreise.domain.Territory

/** The state of finding places to visit. */
public sealed interface ExploreState {
    /** Nothing requested yet. */
    public data object Idle : ExploreState

    /** Places in [area], a country or a state, are loading. */
    public data class Loading(val area: Territory) : ExploreState

    /** The places found in [area]. */
    public data class Ready(val area: Territory, val places: PlacesToVisit) : ExploreState

    /** Wikipedia couldn't be reached for [area]. */
    public data class Failed(val area: Territory) : ExploreState
}

/** The places in [area], or none if this state is about another country or state. */
internal fun ExploreState.landmarksIn(area: Territory?): List<Landmark> =
    if (this is ExploreState.Ready && this.area == area) places.landmarks else emptyList()

/** This state if it's for [area], otherwise [ExploreState.Idle]. */
internal fun ExploreState.about(area: Territory?): ExploreState = when (this) {
    ExploreState.Idle -> this
    is ExploreState.Loading -> takeIf { this.area == area } ?: ExploreState.Idle
    is ExploreState.Ready -> takeIf { this.area == area } ?: ExploreState.Idle
    is ExploreState.Failed -> takeIf { this.area == area } ?: ExploreState.Idle
}

/**
 * The open place card.
 *
 * @property landmark the place shown.
 * @property facts facts from its article, or `null` while loading.
 */
public data class LandmarkCard(val landmark: Landmark, val facts: List<String>? = null)
