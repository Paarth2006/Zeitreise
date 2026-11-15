package com.paarth.zeitreise.ui.globe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.data.CountryRepository
import com.paarth.zeitreise.data.SubdivisionRepository
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Subdivision
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Holds the state of the globe screen and keeps loading and selection out of the composables. */
public class GlobeViewModel(
    private val countries: CountryRepository,
    private val subdivisions: SubdivisionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GlobeUiState>(GlobeUiState.Loading)

    /** The current state of the globe screen. */
    public val uiState: StateFlow<GlobeUiState> = _uiState.asStateFlow()

    private var subdivisionLoad: Job? = null

    init {
        loadCountries()
    }

    /**
     * Selects [country], or clears the selection when it is `null`, and loads the country's states
     * so they appear once the camera has flown in.
     */
    public fun select(country: Country?) {
        subdivisionLoad?.cancel()
        updateReady { it.copy(selected = country, subdivisions = emptyList(), selectedSubdivision = null) }
        if (country == null) return
        subdivisionLoad = viewModelScope.launch {
            val loaded = subdivisions.loadSubdivisions(country)
            updateReady { state -> if (state.selected == country) state.copy(subdivisions = loaded) else state }
        }
    }

    /** Selects a state of the selected country, or clears it when [subdivision] is `null`. */
    public fun selectSubdivision(subdivision: Subdivision?) {
        updateReady { it.copy(selectedSubdivision = subdivision) }
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
