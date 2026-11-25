package com.paarth.zeitreise.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.data.ExploreRepository
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.HistoricLandmark
import com.paarth.zeitreise.domain.Landmark
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.domain.Territory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Finds places to visit and holds the open place card. Each state remembers its country or state,
 * so going back to it shows the same pins without another request.
 */
public class ExploreViewModel(private val explore: ExploreRepository) : ViewModel() {

    private val _state = MutableStateFlow<ExploreState>(ExploreState.Idle)

    /** The places for the last explored country or state. */
    public val state: StateFlow<ExploreState> = _state.asStateFlow()

    private val _openCard = MutableStateFlow<LandmarkCard?>(null)

    /** The open place card, or `null`. */
    public val openCard: StateFlow<LandmarkCard?> = _openCard.asStateFlow()

    private var exploreJob: Job? = null
    private var factsJob: Job? = null

    /**
     * Finds places in [subdivision], or in the whole [country] if it's `null`, unless they are
     * already loaded or loading.
     */
    public fun explore(country: Country, subdivision: Subdivision? = null) {
        val area: Territory = subdivision ?: country
        val current = _state.value
        val alreadyHere = (current is ExploreState.Loading && current.area == area) ||
            (current is ExploreState.Ready && current.area == area)
        if (alreadyHere) return

        exploreJob?.cancel()
        _state.value = ExploreState.Loading(area)
        exploreJob = viewModelScope.launch {
            _state.value = runCatching { explore.placesToVisit(country, subdivision) }
                .onFailure { if (it is CancellationException) throw it }
                .fold(
                    onSuccess = { ExploreState.Ready(area, it) },
                    // Wikipedia couldn't be reached, so the sheet shows Try again.
                    onFailure = { ExploreState.Failed(area) },
                )
        }
    }

    /** Opens the card for [landmark] and loads some facts. */
    public fun open(landmark: Landmark) {
        factsJob?.cancel()
        _openCard.value = LandmarkCard(landmark)
        factsJob = viewModelScope.launch {
            val facts = explore.factsAbout(landmark)
            _openCard.update { card -> if (card?.landmark == landmark) card.copy(facts = facts) else card }
        }
    }

    /** Opens the card for a historic landmark once its article is loaded. */
    public fun openHistoric(pin: HistoricLandmark) {
        factsJob?.cancel()
        factsJob = viewModelScope.launch { explore.landmarkFor(pin)?.let(::open) }
    }

    /** Closes the place card. */
    public fun close() {
        factsJob?.cancel()
        _openCard.value = null
    }
}
