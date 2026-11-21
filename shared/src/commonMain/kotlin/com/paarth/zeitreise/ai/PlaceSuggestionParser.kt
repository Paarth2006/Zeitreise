package com.paarth.zeitreise.ai

import kotlinx.serialization.Serializable

/** Maximum places suggested for one country. */
internal const val MAX_PLACES = 8

/** The JSON shape of the guide's answer for places to visit. */
@Serializable
internal data class PlaceSuggestions(val places: List<PlaceSuggestion> = emptyList())

/**
 * Reads the suggested places from the model's reply. Trims the text, drops incomplete or repeated
 * entries and keeps at most [MAX_PLACES].
 *
 * @return the suggestions, or `null` when the reply holds none.
 */
internal fun parsePlaceSuggestions(reply: String): List<PlaceSuggestion>? =
    decodeReply(reply, PlaceSuggestions.serializer())?.places
        ?.map { PlaceSuggestion(name = it.name.trim(), wikipediaTitle = it.wikipediaTitle.trim()) }
        ?.filter { it.name.isNotEmpty() && it.wikipediaTitle.isNotEmpty() }
        ?.distinctBy { it.wikipediaTitle.lowercase() }
        ?.take(MAX_PLACES)
        ?.ifEmpty { null }
