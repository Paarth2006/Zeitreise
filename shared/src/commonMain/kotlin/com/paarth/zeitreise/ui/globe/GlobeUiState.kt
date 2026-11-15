package com.paarth.zeitreise.ui.globe

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Subdivision

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
     * @property subdivisions the states and provinces of [selected], largest first; empty while
     *   they load and for a country that has none.
     * @property selectedSubdivision the state the user tapped inside [selected], if any.
     */
    public data class Ready(
        val countries: List<Country>,
        val selected: Country? = null,
        val subdivisions: List<Subdivision> = emptyList(),
        val selectedSubdivision: Subdivision? = null,
    ) : GlobeUiState
}
