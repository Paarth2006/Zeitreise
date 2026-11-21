package com.paarth.zeitreise.ui.timetravel

import com.paarth.zeitreise.domain.HistoricLandmark
import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.domain.MAP_YEARS
import com.paarth.zeitreise.domain.Realm

/** Whether the globe shows today or a past year. */
public sealed interface TimeTravelState {
    /** Today's world. */
    public data object Off : TimeTravelState

    /**
     * The world in [year].
     *
     * @property yearIndex index into [MAP_YEARS].
     * @property map the year's map and its loading state.
     * @property selected the selected realm, if any.
     * @property landmarks the landmarks that existed that year.
     */
    public data class On(
        val yearIndex: Int,
        val map: PastMap = PastMap.Loading,
        val selected: Realm? = null,
        val landmarks: List<HistoricLandmark> = emptyList(),
    ) : TimeTravelState {
        init {
            require(yearIndex in MAP_YEARS.indices) { "No map year at index $yearIndex" }
        }

        /** The selected year, negative for BC. */
        val year: Int get() = MAP_YEARS[yearIndex]
    }
}

/** The loading state of the year's map. */
public sealed interface PastMap {
    /** The map is downloading. */
    public data object Loading : PastMap

    /** The map is loaded. */
    public data class Ready(val map: HistoricalMap) : PastMap

    /** The map couldn't be downloaded. */
    public data object Failed : PastMap
}

/** The loaded map, or `null` if it isn't ready. */
internal val TimeTravelState.On.readyMap: HistoricalMap? get() = (map as? PastMap.Ready)?.map
