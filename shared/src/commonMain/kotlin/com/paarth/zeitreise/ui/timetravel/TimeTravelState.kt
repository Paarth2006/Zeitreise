package com.paarth.zeitreise.ui.timetravel

import com.paarth.zeitreise.domain.HistoricLandmark
import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.domain.MAP_YEARS
import com.paarth.zeitreise.domain.Realm

/** Whether the globe shows today's world or a year from the past. */
public sealed interface TimeTravelState {
    /** The globe shows today's countries. */
    public data object Off : TimeTravelState

    /**
     * The globe shows the world as it was in [year].
     *
     * @property yearIndex which of [MAP_YEARS] is chosen.
     * @property map that year's map, as far as it has loaded.
     * @property selected the realm the user tapped, if any.
     * @property landmarks the famous landmarks that stood that year, pinned on the globe.
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

        /** The year the globe shows; negative before Christ. */
        val year: Int get() = MAP_YEARS[yearIndex]
    }
}

/** How far the chosen year's map has loaded. */
public sealed interface PastMap {
    /** The map is downloading. */
    public data object Loading : PastMap

    /** The map is ready to draw. */
    public data class Ready(val map: HistoricalMap) : PastMap

    /** The map could not be downloaded; the user can try again. */
    public data object Failed : PastMap
}

/** The map to draw, or `null` while there is none for the chosen year. */
internal val TimeTravelState.On.readyMap: HistoricalMap? get() = (map as? PastMap.Ready)?.map
