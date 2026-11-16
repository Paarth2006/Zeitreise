package com.paarth.zeitreise.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.data.ExploreRepository
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Landmark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Finds places to visit in a country when the user asks, and holds the place card that is open.
 *
 * Every state names its country, so the screen shows pins only while that country is selected,
 * and coming back to it shows the same pins again without asking the guide twice.
 */
public class ExploreViewModel(private val explore: ExploreRepository) : ViewModel() {

    private val _state = MutableStateFlow<ExploreState>(ExploreState.Idle)

    /** The places found, or being found, for the country the user last explored. */
    public val state: StateFlow<ExploreState> = _state.asStateFlow()

    private val _openCard = MutableStateFlow<LandmarkCard?>(null)

    /** The place card that is open, or `null` when none is. */
    public val openCard: StateFlow<LandmarkCard?> = _openCard.asStateFlow()

    private var exploreJob: Job? = null
    private var factsJob: Job? = null

    /**
     * Asks the guide for places to visit in [country], unless they are already found or being
     * found, so one tap costs one request at most.
     */
    public fun explore(country: Country) {
        val current = _state.value
        val alreadyHere = (current is ExploreState.Loading && current.country == country) ||
            (current is ExploreState.Ready && current.country == country)
        if (alreadyHere) return

        exploreJob?.cancel()
        _state.value = ExploreState.Loading(country)
        exploreJob = viewModelScope.launch {
            _state.value = runCatching { explore.placesToVisit(country) }
                .onFailure { if (it is CancellationException) throw it }
                .fold(
                    onSuccess = { ExploreState.Ready(country, it) },
                    // Wikipedia could not be reached; the sheet offers to try again.
                    onFailure = { ExploreState.Failed(country) },
                )
        }
    }

    /** Opens the card for [landmark] and loads a few facts about it. */
    public fun open(landmark: Landmark) {
        factsJob?.cancel()
        _openCard.value = LandmarkCard(landmark)
        factsJob = viewModelScope.launch {
            val facts = explore.factsAbout(landmark)
            _openCard.update { card -> if (card?.landmark == landmark) card.copy(facts = facts) else card }
        }
    }

    /** Closes the place card. */
    public fun close() {
        factsJob?.cancel()
        _openCard.value = null
    }
}
