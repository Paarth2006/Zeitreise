package com.paarth.zeitreise.domain

import kotlinx.serialization.Serializable

/**
 * The summary of one Wikipedia article: what the guide reads before it says anything, and what
 * the sheet credits.
 *
 * @property title the article's title.
 * @property description Wikipedia's one-line description, such as "Country in South Asia".
 * @property extract the article's opening paragraph in plain text.
 * @property thumbnailUrl the lead image, sized for a thumbnail, if the article has one.
 * @property pageUrl the article on Wikipedia, for the "Read on Wikipedia" link.
 */
@Serializable
public data class WikiArticle(
    val title: String,
    val description: String? = null,
    val extract: String,
    val thumbnailUrl: String? = null,
    val pageUrl: String,
)
