package com.paarth.zeitreise.ui.globe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.data.CountryRepository
import com.paarth.zeitreise.domain.Country
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Holds the state of the globe screen and keeps loading and selection out of the composables. */
public class GlobeViewModel(private val countries: CountryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<GlobeUiState>(GlobeUiState.Loading)

    /** The current state of the globe screen. */
    public val uiState: StateFlow<GlobeUiState> = _uiState.asStateFlow()

    init {
        loadCountries()
    }

    /** Selects [country], or clears the selection when it is `null`. */
    public fun select(country: Country?) {
        _uiState.update { state -> if (state is GlobeUiState.Ready) state.copy(selected = country) else state }
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
}
