package com.paarth.zeitreise.ai

import com.paarth.zeitreise.domain.GuideFact
import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.WikiArticle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Maximum length of a fact taken from Wikipedia, about two sentences. */
private const val MAX_WIKIPEDIA_FACT_LENGTH = 280

/** Minimum sentence length, so abbreviations like "St." and "U.S." aren't treated as sentence ends. */
private const val MIN_SENTENCE_LENGTH = 40

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * A guide that works without AI. It uses the bundled stories and places for about ten countries,
 * so the app works without an API key.
 *
 * @param readStories reads the bundled stories, a JSON object keyed by Wikidata item.
 * @param readPlaces reads the bundled places to visit, a JSON object keyed by Wikidata item.
 */
internal class DemoGuide(
    private val readStories: suspend () -> String,
    private val readPlaces: suspend () -> String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Guide {

    private val stories = Bundled(MapSerializer(String.serializer(), GuideStory.serializer()), readStories)
    private val places = Bundled(MapSerializer(String.serializer(), PlaceSuggestions.serializer()), readPlaces)

    override suspend fun tell(place: GuidePlace, article: WikiArticle, notableFacts: List<String>): GuideStory? =
        place.wikidataId?.let { stories.load()[it] }

    override suspend fun suggestPlaces(country: GuidePlace, article: WikiArticle): List<PlaceSuggestion>? =
        country.wikidataId?.let { places.load()[it]?.places }

    /** A bundled file, read once when first needed. */
    private inner class Bundled<T>(
        private val deserializer: DeserializationStrategy<T>,
        private val read: suspend () -> String,
    ) {
        private val lock = Mutex()
        private var value: T? = null

        suspend fun load(): T = lock.withLock {
            value ?: json.decodeFromString(deserializer, read()).also { value = it }
        }
    }
}

/**
 * Facts taken directly from a Wikipedia article, for places without an AI or bundled story: its
 * interesting sentences, or else its first lines.
 */
internal fun wikipediaFacts(article: WikiArticle, notableFacts: List<String>): List<GuideFact> =
    notableFacts.map { GuideFact(text = it) }.ifEmpty {
        listOf(
            GuideFact(
                headline = article.description?.replaceFirstChar(Char::uppercaseChar) ?: article.title,
                text = leadSentences(article.extract),
            ),
        )
    }

/** The first sentences of [text] that fit in [maxLength], or the text cut at a word if one is too long. */
internal fun leadSentences(text: String, maxLength: Int = MAX_WIKIPEDIA_FACT_LENGTH): String {
    // Wikipedia leaves double spaces where it removed pronunciations.
    val trimmed = text.trim().replace(WHITESPACE_RUN, " ")
    if (trimmed.length <= maxLength) return trimmed
    val sentenceEnds = generateSequence(trimmed.indexOf(". ")) { previous ->
        trimmed.indexOf(". ", previous + 1).takeIf { it >= 0 }
    }
    val lastStop = sentenceEnds
        .takeWhile { it in 0 until maxLength }
        .lastOrNull { it >= MIN_SENTENCE_LENGTH }
    return if (lastStop != null) {
        trimmed.substring(0, lastStop + 1)
    } else {
        "${trimmed.substring(0, trimmed.lastIndexOf(' ', maxLength).coerceAtLeast(1)).trimEnd()}…"
    }
}
