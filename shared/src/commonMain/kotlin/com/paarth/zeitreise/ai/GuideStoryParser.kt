package com.paarth.zeitreise.ai

import com.paarth.zeitreise.domain.GuideFact
import com.paarth.zeitreise.domain.GuideStory
import kotlinx.serialization.json.Json

/** Most "known for" items shown; the sheet has room for a single row of them. */
internal const val MAX_KNOWN_FOR = 3

/** Most facts kept from one answer: enough for several visits, few enough to stay surprising. */
internal const val MAX_FACTS = 5

private val LENIENT_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Reads the guide's story out of a model's reply.
 *
 * Models do not always answer with bare JSON: they wrap it in a Markdown code fence or add a line
 * around it. This takes the outermost JSON object in [reply], drops facts with no text, and checks
 * that at least one is left.
 *
 * @return the story, or `null` when the reply holds no readable fact.
 */
internal fun parseGuideStory(reply: String): GuideStory? {
    val start = reply.indexOf('{')
    val end = reply.lastIndexOf('}')
    val story = if (start in 0 until end) decodeOrNull(reply.substring(start, end + 1)) else null
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

/** Decodes a story, or returns `null` for JSON that is malformed or not shaped like a story. */
private fun decodeOrNull(json: String): GuideStory? = try {
    LENIENT_JSON.decodeFromString(GuideStory.serializer(), json)
} catch (_: IllegalArgumentException) {
    // SerializationException is an IllegalArgumentException, so this covers both.
    null
}
