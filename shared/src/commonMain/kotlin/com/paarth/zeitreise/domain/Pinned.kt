package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint

/** Something shown as a pin on the globe: a place to visit, or a landmark in Time Travel. */
public sealed interface Pinned {
    /** The name of the pinned place. */
    public val name: String

    /** The pin's position. */
    public val location: GeoPoint
}

/**
 * A historic landmark, shown in Time Travel for the years it existed.
 *
 * @property wikipediaTitle its English Wikipedia article title.
 * @property built the year it was built, negative for BC.
 * @property until the year it was destroyed, or `null` if it still stands.
 */
public data class HistoricLandmark(
    override val name: String,
    val wikipediaTitle: String,
    override val location: GeoPoint,
    val built: Int,
    val until: Int? = null,
) : Pinned {
    /** Whether the landmark existed in [year]. */
    public fun standsIn(year: Int): Boolean = built <= year && (until == null || year < until)
}
