package com.paarth.zeitreise.ai

import com.paarth.zeitreise.domain.GuideFact
import com.paarth.zeitreise.domain.GuideStory
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/** Maximum "known for" items, enough for one row in the sheet. */
internal const val MAX_KNOWN_FOR = 3

/** Maximum facts kept from one answer. */
internal const val MAX_FACTS = 5

private val LENIENT_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Reads the story from the model's reply, dropping empty facts.
 *
 * @return the story, or `null` if the reply has no usable fact.
 */
internal fun parseGuideStory(reply: String): GuideStory? {
    val story = decodeReply(reply, GuideStory.serializer())
    val facts = story?.facts.orEmpty()
        .filter { it.text.isNotBlank() }
        .map { GuideFact(headline = it.headline?.trim()?.ifBlank { null }, text = it.text.trim()) }
        .take(MAX_FACTS)
    return if (facts.isEmpty()) {
        null
    } else {
        GuideStory(
            facts = facts,
            knownFor = story?.knownFor.orEmpty().map(String::trim).filter(String::isNotEmpty).take(MAX_KNOWN_FOR),
        )
    }
}

/**
 * Decodes the outermost JSON object in [reply].
 *
 * Models sometimes wrap JSON in a code block or add extra text, so anything outside the outer braces
 * is ignored.
 *
 * @return the decoded value, or `null` if there is no matching JSON object.
 */
internal fun <T> decodeReply(reply: String, deserializer: DeserializationStrategy<T>): T? {
    val start = reply.indexOf('{')
    val end = reply.lastIndexOf('}')
    if (start !in 0 until end) return null
    return try {
        LENIENT_JSON.decodeFromString(deserializer, reply.substring(start, end + 1))
    } catch (_: IllegalArgumentException) {
        // SerializationException extends IllegalArgumentException, so this catches both.
        null
    }
}
