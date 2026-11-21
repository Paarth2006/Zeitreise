package com.paarth.zeitreise.ui.globe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.data.CountryRepository
import com.paarth.zeitreise.data.DayClock
import com.paarth.zeitreise.data.FactCursor
import com.paarth.zeitreise.data.GuideRepository
import com.paarth.zeitreise.data.Passport
import com.paarth.zeitreise.data.SubdivisionRepository
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.SearchResult
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.domain.countryOfTheDay
import com.paarth.zeitreise.domain.passportKey
import com.paarth.zeitreise.domain.searchPlaces
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

/**
 * The globe screen's view model. It handles loading, selection, search and stories, and stamps
 * every opened country in the [passport], which also keeps the user's home country.
 */
public class GlobeViewModel(
    private val countries: CountryRepository,
    private val subdivisions: SubdivisionRepository,
    private val passport: Passport,
    private val dayClock: DayClock,
    guide: GuideRepository,
    factCursor: FactCursor,
) : ViewModel() {

    private val storyTeller = StoryTeller(guide, factCursor)

    private val _uiState = MutableStateFlow<GlobeUiState>(GlobeUiState.Loading)

    /** The current state of the globe screen. */
    public val uiState: StateFlow<GlobeUiState> = _uiState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())

    /** Results of the latest search, best first. Empty if nothing is being searched. */
    public val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _story = MutableStateFlow<StoryState>(StoryState.Idle)

    /** The story for the selected state, or for the selected country if no state is selected. */
    public val story: StateFlow<StoryState> = _story.asStateFlow()

    private val _newStamp = MutableStateFlow<Country?>(null)

    /** The selected country, if it was just stamped for the first time. */
    public val newStamp: StateFlow<Country?> = _newStamp.asStateFlow()

    private val storyRetries = MutableStateFlow(0)
    private var subdivisionLoad: Job? = null
    private var searchJob: Job? = null

    init {
        loadCountries()
        viewModelScope.launch {
            // A new selection cancels the previous story's loading.
            val focusedPlace = _uiState.map { (it as? GlobeUiState.Ready)?.focusedPlace() }.distinctUntilChanged()
            combine(focusedPlace, storyRetries) { place, _ -> place }
                .collectLatest { place ->
                    _story.value = if (place == null) StoryState.Idle else StoryState.Loading
                    _story.value = storyTeller.storyFor(place)
                }
        }
    }

    /** Shows the next fact, wrapping around after the last. */
    public fun nextFact() {
        _story.update { state ->
            if (state is StoryState.Ready) storyTeller.nextFact(state) else state
        }
    }

    /** Retries loading the story. */
    public fun retryStory() {
        storyRetries.update { it + 1 }
    }

    /** Saves [country] as the user's home country and stamps it in the passport, as their first stamp. */
    public fun chooseHome(country: Country) {
        updateReady { it.copy(home = country) }
        viewModelScope.launch { passport.setHome(country) }
    }

    /** Searches for [query]. A blank query clears the results. */
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

    /** Opens the best match for [query] when the user presses Search instead of picking a result. */
    public fun submitSearch(query: String) {
        val ready = _uiState.value as? GlobeUiState.Ready ?: return
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searchPlaces(query, ready.countries, subdivisions.loadNames()).firstOrNull()?.let(::choose)
        }
    }

    /** Opens the picked search result and clears the results. */
    public fun choose(result: SearchResult) {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        when (result) {
            is SearchResult.CountryMatch -> select(result.country, subdivisionName = null)
            is SearchResult.SubdivisionMatch -> select(result.country, subdivisionName = result.name)
        }
    }

    /** Selects a state of the selected country, or clears it if `null`. */
    public fun selectSubdivision(subdivision: Subdivision?) {
        updateReady { it.copy(selectedSubdivision = subdivision) }
    }

    /**
     * Selects [country] (or clears the selection if `null`) and loads its states. Search passes
     * [subdivisionName] to select one of those states once they're loaded.
     */
    public fun select(country: Country?, subdivisionName: String? = null) {
        subdivisionLoad?.cancel()
        updateReady { it.copy(selected = country, subdivisions = emptyList(), selectedSubdivision = null) }
        _newStamp.value = null
        if (country == null) return
        viewModelScope.launch {
            val firstStamp = passport.stamp(country)
            // Only show "new stamp" if the country is still selected.
            if (firstStamp && (_uiState.value as? GlobeUiState.Ready)?.selected == country) _newStamp.value = country
        }
        subdivisionLoad = viewModelScope.launch {
            val loaded = subdivisions.loadSubdivisions(country)
            val chosen = subdivisionName?.let { name -> loaded.firstOrNull { it.name == name } }
            updateReady { state ->
                val stillSelected = state.selected == country
                if (stillSelected) state.copy(subdivisions = loaded, selectedSubdivision = chosen) else state
            }
        }
    }

    private fun loadCountries() {
        viewModelScope.launch {
            val homeKey = passport.home()
            _uiState.value = runCatching { countries.loadCountries() }
                .fold(
                    onSuccess = { loaded ->
                        GlobeUiState.Ready(
                            countries = loaded,
                            countryOfTheDay = countryOfTheDay(loaded, dayClock.today()),
                            home = homeKey?.let { key -> loaded.firstOrNull { it.passportKey == key } },
                        )
                    },
                    onFailure = { GlobeUiState.Failed(it.message ?: "The country outlines could not be read.") },
                )
        }
    }

    private inline fun updateReady(transform: (GlobeUiState.Ready) -> GlobeUiState.Ready) {
        _uiState.update { state -> if (state is GlobeUiState.Ready) transform(state) else state }
    }
}

/** The selected state, or the selected country if no state is selected. */
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
