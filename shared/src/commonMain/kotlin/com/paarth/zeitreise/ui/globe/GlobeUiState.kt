package com.paarth.zeitreise.ui.globe

import com.paarth.zeitreise.domain.Country

/** What the globe screen is showing. */
public sealed interface GlobeUiState {
    /** The outlines are still being read. */
    public data object Loading : GlobeUiState

    /** The outlines could not be read, so there is nothing to draw. */
    public data class Failed(val message: String) : GlobeUiState

    /**
     * The globe is ready to draw.
     *
     * @property countries every country, largest first.
     * @property selected the country the user last tapped, or `null` if none is selected.
     */
    public data class Ready(val countries: List<Country>, val selected: Country? = null) : GlobeUiState
}
