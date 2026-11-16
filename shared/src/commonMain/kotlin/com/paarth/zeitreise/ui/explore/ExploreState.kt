package com.paarth.zeitreise.ui.explore

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Landmark
import com.paarth.zeitreise.domain.PlacesToVisit

/** How far the guide has got with finding places to visit. */
public sealed interface ExploreState {
    /** Nobody has asked for places yet. */
    public data object Idle : ExploreState

    /** The guide is choosing places in [country] and checking them against Wikipedia. */
    public data class Loading(val country: Country) : ExploreState

    /** The guide's places in [country], ready to pin on the globe. */
    public data class Ready(val country: Country, val places: PlacesToVisit) : ExploreState

    /** Wikipedia could not be reached for [country]; the user can try again. */
    public data class Failed(val country: Country) : ExploreState
}

/** The places pinned for [country]: none unless the places found are for that country. */
internal fun ExploreState.landmarksIn(country: Country?): List<Landmark> =
    if (this is ExploreState.Ready && this.country == country) places.landmarks else emptyList()

/** This state if it is about [country], or [ExploreState.Idle] for any other country. */
internal fun ExploreState.about(country: Country?): ExploreState = when (this) {
    ExploreState.Idle -> this
    is ExploreState.Loading -> takeIf { this.country == country } ?: ExploreState.Idle
    is ExploreState.Ready -> takeIf { this.country == country } ?: ExploreState.Idle
    is ExploreState.Failed -> takeIf { this.country == country } ?: ExploreState.Idle
}

/**
 * The place card that is open.
 *
 * @property landmark the place the card is about.
 * @property facts notable facts from the place's article, or `null` while they load.
 */
public data class LandmarkCard(val landmark: Landmark, val facts: List<String>? = null)
