package com.paarth.zeitreise.ai

import com.paarth.zeitreise.domain.GuideStory
import kotlinx.serialization.json.Json

/** Most "known for" items shown; the sheet has room for a single row of them. */
internal const val MAX_KNOWN_FOR = 3

private val LENIENT_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Reads the guide's story out of a model's reply.
 *
 * Models do not always answer with bare JSON: they wrap it in a Markdown code fence or add a line
 * around it. This takes the outermost JSON object in [reply] and checks that it holds a usable
 * story.
 *
 * @return the story, or `null` when the reply holds no readable story.
 */
internal fun parseGuideStory(reply: String): GuideStory? {
    val start = reply.indexOf('{')
    val end = reply.lastIndexOf('}')
    val story = if (start in 0 until end) decodeOrNull(reply.substring(start, end + 1)) else null
    return story
        ?.takeIf { it.headline.isNotBlank() && it.fact.isNotBlank() }
        ?.let {
            GuideStory(
                headline = it.headline.trim(),
                fact = it.fact.trim(),
                knownFor = it.knownFor.map(String::trim).filter(String::isNotEmpty).take(MAX_KNOWN_FOR),
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
