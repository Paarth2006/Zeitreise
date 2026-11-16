package com.paarth.zeitreise.ai

import kotlinx.serialization.Serializable

/** Most places suggested for one country: enough to spread across it, few enough to stay legible. */
internal const val MAX_PLACES = 8

/** The shape of the guide's answer when asked for places to visit. */
@Serializable
internal data class PlaceSuggestions(val places: List<PlaceSuggestion> = emptyList())

/**
 * Reads the guide's suggested places out of a model's reply: trims names and titles, drops any
 * without both, drops repeated titles, and keeps at most [MAX_PLACES].
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
