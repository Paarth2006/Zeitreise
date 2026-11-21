package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint

/** Something the globe marks with a pin: a place to visit today, or a landmark of the year shown. */
public sealed interface Pinned {
    /** The name the pin stands for. */
    public val name: String

    /** Where the pin stands. */
    public val location: GeoPoint
}

/**
 * A landmark of the past, pinned while travelling in time in the years it stood.
 *
 * @property wikipediaTitle the title of its English Wikipedia article, read when its card opens.
 * @property built the year it was built or founded; negative before Christ.
 * @property until the year it was destroyed, or `null` for one that still stands.
 */
public data class HistoricLandmark(
    override val name: String,
    val wikipediaTitle: String,
    override val location: GeoPoint,
    val built: Int,
    val until: Int? = null,
) : Pinned {
    /** Whether the landmark stood in [year]: built by then and not yet destroyed. */
    public fun standsIn(year: Int): Boolean = built <= year && (until == null || year < until)
}
