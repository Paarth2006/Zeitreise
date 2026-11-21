package com.paarth.zeitreise.ui.globe

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Subdivision

/** The state of the globe screen. */
public sealed interface GlobeUiState {
    /** The outlines are still loading. */
    public data object Loading : GlobeUiState

    /** The outlines couldn't be loaded. */
    public data class Failed(val message: String) : GlobeUiState

    /**
     * The globe is ready to draw.
     *
     * @property countries every country, largest first.
     * @property selected the selected country, if any.
     * @property subdivisions the states of [selected], largest first. Empty while loading.
     * @property selectedSubdivision the selected state inside [selected], if any.
     * @property countryOfTheDay today's country.
     * @property home the user's home country, if they gave one. The globe starts facing it.
     */
    public data class Ready(
        val countries: List<Country>,
        val selected: Country? = null,
        val subdivisions: List<Subdivision> = emptyList(),
        val selectedSubdivision: Subdivision? = null,
        val countryOfTheDay: Country? = null,
        val home: Country? = null,
    ) : GlobeUiState
}
