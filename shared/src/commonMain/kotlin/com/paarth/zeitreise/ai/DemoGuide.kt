package com.paarth.zeitreise.ai

import com.paarth.zeitreise.domain.GuideFact
import com.paarth.zeitreise.domain.GuideStory
import com.paarth.zeitreise.domain.WikiArticle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Longest fact taken from a Wikipedia article, enough for two plain sentences. */
private const val MAX_WIKIPEDIA_FACT_LENGTH = 280

/** Shortest a first sentence may be before it is taken as the end of one, which skips "St." and "U.S.". */
private const val MIN_SENTENCE_LENGTH = 40

/**
 * The guide that needs no AI: it tells the facts bundled with the app for about ten countries,
 * so the app has something to say with no API key at all, and stays quiet about other places.
 *
 * @param readStories reads the bundled stories, a JSON object keyed by Wikidata item.
 */
internal class DemoGuide(
    private val readStories: suspend () -> String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Guide {

    private val storiesLock = Mutex()
    private var stories: Map<String, GuideStory>? = null

    override suspend fun tell(place: GuidePlace, article: WikiArticle, notableFacts: List<String>): GuideStory? =
        place.wikidataId?.let { loadStories()[it] }

    private suspend fun loadStories(): Map<String, GuideStory> = storiesLock.withLock {
        stories ?: json.decodeFromString(MapSerializer(String.serializer(), GuideStory.serializer()), readStories())
            .also { stories = it }
    }
}

/**
 * Facts taken straight from a Wikipedia article, for places with no AI or bundled story, so every
 * place still has several true things to say: the article's notable sentences, or failing those,
 * its opening under its own description.
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

/**
 * The opening sentences of [text] that fit in [maxLength] characters, ending on a full stop; or
 * the text cut at a word boundary when even the first sentence is longer.
 */
internal fun leadSentences(text: String, maxLength: Int = MAX_WIKIPEDIA_FACT_LENGTH): String {
    val trimmed = text.trim()
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
