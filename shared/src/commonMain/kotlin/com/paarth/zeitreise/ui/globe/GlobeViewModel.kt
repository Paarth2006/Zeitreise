package com.paarth.zeitreise.ui.globe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.data.CountryRepository
import com.paarth.zeitreise.data.GuideRepository
import com.paarth.zeitreise.data.SubdivisionRepository
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.SearchResult
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.domain.searchPlaces
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Holds the state of the globe screen and keeps loading and selection out of the composables. */
public class GlobeViewModel(
    private val countries: CountryRepository,
    private val subdivisions: SubdivisionRepository,
    private val guide: GuideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GlobeUiState>(GlobeUiState.Loading)

    /** The current state of the globe screen. */
    public val uiState: StateFlow<GlobeUiState> = _uiState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())

    /** The places matching the latest search, best first; empty when nothing is being searched. */
    public val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    /** What the guide says about the selected place: a state when one is selected, else its country. */
    public val story: StateFlow<StoryState>

    private val storyRetries = MutableStateFlow(0)
    private var subdivisionLoad: Job? = null
    private var searchJob: Job? = null

    init {
        loadCountries()
        val storyState = MutableStateFlow<StoryState>(StoryState.Idle)
        story = storyState.asStateFlow()
        viewModelScope.launch {
            // A new selection cancels the story still loading for the old one.
            val focusedPlace = _uiState.map { (it as? GlobeUiState.Ready)?.focusedPlace() }.distinctUntilChanged()
            combine(focusedPlace, storyRetries) { place, _ -> place }
                .collectLatest { place -> storyState.value = loadStory(place) }
        }
    }

    /** Asks the guide again after Wikipedia could not be reached. */
    public fun retryStory() {
        storyRetries.update { it + 1 }
    }

    /**
     * Selects [country], or clears the selection when it is `null`, and loads the country's states
     * so they appear once the camera has flown in.
     */
    public fun select(country: Country?) {
        select(country, subdivisionName = null)
    }

    /** Shows the places whose names match [query], or clears the results for a blank query. */
    public fun search(query: String) {
        searchJob?.cancel()
        val ready = _uiState.value as? GlobeUiState.Ready
        if (query.isBlank() || ready == null) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            _searchResults.value = searchPlaces(query, ready.countries, subdivisions.loadNames())
        }
    }

    /**
     * Flies to the best match for [query], for when the user presses Search rather than tapping a
     * result. Runs the search itself, so it works even before the results have appeared.
     */
    public fun submitSearch(query: String) {
        val ready = _uiState.value as? GlobeUiState.Ready ?: return
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searchPlaces(query, ready.countries, subdivisions.loadNames()).firstOrNull()?.let(::choose)
        }
    }

    /** Flies to the place the user picked from the search results, and clears them. */
    public fun choose(result: SearchResult) {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        when (result) {
            is SearchResult.CountryMatch -> select(result.country, subdivisionName = null)
            is SearchResult.SubdivisionMatch -> select(result.country, subdivisionName = result.name)
        }
    }

    /** Selects a state of the selected country, or clears it when [subdivision] is `null`. */
    public fun selectSubdivision(subdivision: Subdivision?) {
        updateReady { it.copy(selectedSubdivision = subdivision) }
    }

    /**
     * Selects [country] and loads its states; once they arrive, also selects the one named
     * [subdivisionName], if any, so a state picked from search ends up highlighted.
     */
    private fun select(country: Country?, subdivisionName: String?) {
        subdivisionLoad?.cancel()
        updateReady { it.copy(selected = country, subdivisions = emptyList(), selectedSubdivision = null) }
        if (country == null) return
        subdivisionLoad = viewModelScope.launch {
            val loaded = subdivisions.loadSubdivisions(country)
            val chosen = subdivisionName?.let { name -> loaded.firstOrNull { it.name == name } }
            updateReady { state ->
                val stillSelected = state.selected == country
                if (stillSelected) state.copy(subdivisions = loaded, selectedSubdivision = chosen) else state
            }
        }
    }

    private suspend fun loadStory(place: GuidePlace?): StoryState {
        if (place == null) return StoryState.Idle
        val answer = runCatching { guide.storyFor(place) }
            .onFailure { if (it is CancellationException) throw it }
        return answer.fold(
            onSuccess = { found -> found?.let { StoryState.Ready(it) } ?: StoryState.NoArticle },
            onFailure = { StoryState.Failed },
        )
    }

    private fun loadCountries() {
        viewModelScope.launch {
            _uiState.value = runCatching { countries.loadCountries() }
                .fold(
                    onSuccess = { GlobeUiState.Ready(countries = it) },
                    onFailure = { GlobeUiState.Failed(it.message ?: "The country outlines could not be read.") },
                )
        }
    }

    private inline fun updateReady(transform: (GlobeUiState.Ready) -> GlobeUiState.Ready) {
        _uiState.update { state -> if (state is GlobeUiState.Ready) transform(state) else state }
    }
}

/** The place the sheet is about: the selected state if there is one, else the selected country. */
private fun GlobeUiState.Ready.focusedPlace(): GuidePlace? {
    val country = selected ?: return null
    val subdivision = selectedSubdivision
    return if (subdivision != null) {
        val kind = subdivision.kind.ifBlank { "region" }.lowercase()
        GuidePlace(subdivision.name, "a $kind of ${country.name}", subdivision.wikidataId)
    } else {
        GuidePlace(country.name, "a country in ${country.continent}", country.wikidataId)
    }
}
