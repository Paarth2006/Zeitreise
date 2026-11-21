package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint
import kotlinx.serialization.Serializable

/**
 * The summary of a Wikipedia article.
 *
 * @property title the article's title.
 * @property description Wikipedia's one-line description, such as "Country in South Asia".
 * @property extract the article's opening paragraph in plain text.
 * @property thumbnailUrl the lead image, sized for a thumbnail, if the article has one.
 * @property pageUrl the article on Wikipedia, for the "Read on Wikipedia" link.
 * @property latitude where the article's subject lies, when Wikipedia gives it coordinates.
 * @property longitude where the article's subject lies, when Wikipedia gives it coordinates.
 */
@Serializable
public data class WikiArticle(
    val title: String,
    val description: String? = null,
    val extract: String,
    val thumbnailUrl: String? = null,
    val pageUrl: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** The article's location, or `null` if it has no coordinates. */
    val location: GeoPoint?
        get() = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null
}
