package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.angleTo

/**
 * How far outside a country's outlines, in degrees, a place still counts as in it: about 150 km,
 * enough for a reef or an island just off the coast, not enough for a namesake abroad.
 */
private const val COAST_MARGIN_DEGREES = 1.5

/**
 * A place worth visiting, pinned on the globe.
 *
 * The guide only ever names it; where it is, what it looks like and what it is come from its
 * Wikipedia article, so a pin can never stand on a made-up spot.
 *
 * @property name the name the guide gave the place.
 * @property article the place's Wikipedia article, which has coordinates.
 * @property location where the pin stands, taken from [article].
 */
public data class Landmark(override val name: String, val article: WikiArticle, override val location: GeoPoint) :
    Pinned

/**
 * The places worth visiting in a country.
 *
 * @property landmarks the places, each checked against Wikipedia; empty when none could be found.
 * @property source who chose them: the AI guide or the bundled list.
 * @property notice why the AI guide did not choose them, when it was asked and could not.
 */
public data class PlacesToVisit(
    val landmarks: List<Landmark>,
    val source: StorySource,
    val notice: GuideNotice? = null,
)

/**
 * Whether [point] lies on or just off one of this country's landmasses: inside an outline's
 * bounding circle widened by a coastal margin. Lenient on purpose, since it only has to catch a
 * place the guide put in the wrong country.
 */
public fun Country.isNear(point: GeoPoint): Boolean = outlines.any { outline ->
    val bounds = outline.exterior.bounds
    bounds.center.angleTo(point) <= bounds.radiusDegrees + COAST_MARGIN_DEGREES
}
