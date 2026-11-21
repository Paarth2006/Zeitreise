package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.angleTo

/** How far outside a country's outlines a place may be (about 150 km), so nearby islands still count. */
private const val COAST_MARGIN_DEGREES = 1.5

/**
 * A place worth visiting, pinned on the globe.
 *
 * The guide only gives the name. The location and photo come from Wikipedia, so a pin can't land
 * on a made-up spot.
 *
 * @property name the name the guide gave.
 * @property article the place's Wikipedia article.
 * @property location the pin's position, taken from [article].
 */
public data class Landmark(override val name: String, val article: WikiArticle, override val location: GeoPoint) :
    Pinned

/**
 * Places worth visiting in a country.
 *
 * @property landmarks the places, all checked against Wikipedia. Empty if none were found.
 * @property source who picked them: the AI guide or the bundled list.
 * @property notice why the AI guide didn't pick them, if it failed.
 */
public data class PlacesToVisit(
    val landmarks: List<Landmark>,
    val source: StorySource,
    val notice: GuideNotice? = null,
)

/**
 * Whether [point] is on or near one of this country's landmasses. It is lenient on purpose: it only
 * needs to catch places the guide put in the wrong country.
 */
public fun Country.isNear(point: GeoPoint): Boolean = outlines.any { outline ->
    val bounds = outline.exterior.bounds
    bounds.center.angleTo(point) <= bounds.radiusDegrees + COAST_MARGIN_DEGREES
}
