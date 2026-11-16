package com.paarth.zeitreise.domain

/** Facts taken from one article; enough for several visits without repeating. */
public const val MAX_NOTABLE_SENTENCES: Int = 8

/** Shortest sentence worth showing as a fact; anything shorter rarely says something complete. */
private const val MIN_FACT_LENGTH = 60

/** Longest sentence shown as a fact, so it fits the sheet without scrolling. */
private const val MAX_FACT_LENGTH = 240

/** Words that usually mark a sentence as surprising rather than merely descriptive. */
private val NOTABLE_WORDS = setOf(
    "largest", "smallest", "highest", "lowest", "longest", "shortest", "oldest", "newest", "deepest",
    "tallest", "biggest", "first", "only", "most", "least", "world", "record", "unique", "famous",
    "ancient", "founded", "discovered", "invented", "named", "known",
)

/** Openings that refer back to an earlier sentence, which make no sense shown on their own. */
private val DANGLING_OPENINGS = setOf("it", "this", "these", "they", "he", "she", "his", "her", "its", "their", "such")

/** Where a sentence ends: a full stop, question or exclamation mark, then space and a capital. */
private val SENTENCE_BREAK = Regex("""(?<=[.!?])\s+(?=[A-Z"“(])""")

/**
 * Picks the sentences of an article most likely to be surprising facts, in the order they appear.
 *
 * A sentence qualifies when it stands on its own (it does not open with "It" or "They"), fits the
 * sheet, and holds a number or a word such as "largest", "first" or "only". The article is split
 * into [limit] stretches and the best sentence of each is taken, so the facts range over the whole
 * article, from geography to history to culture, instead of clustering in its opening.
 *
 * @param text the article as plain text.
 */
public fun notableSentences(text: String, limit: Int = MAX_NOTABLE_SENTENCES): List<String> {
    val candidates = text.lines()
        .filter { it.isNotBlank() }
        .flatMap { line -> line.split(SENTENCE_BREAK) }
        .map(String::trim)
        .withIndex()
        .mapNotNull { (position, sentence) -> sentence.notability()?.let { Scored(position, sentence, it) } }
    if (candidates.isEmpty() || limit <= 0) return emptyList()

    val last = candidates.last().position + 1
    return candidates
        .groupBy { it.position * limit / last }
        .values
        .map { stretch -> stretch.maxBy(Scored::score) }
        .sortedBy(Scored::position)
        .map(Scored::sentence)
}

/** A sentence that qualified, where it sits in the article, and how notable it looks. */
private data class Scored(val position: Int, val sentence: String, val score: Int)

/** How notable the sentence looks, or `null` when it cannot be shown on its own. */
private fun String.notability(): Int? {
    val words = split(' ', ',', ';', '(', ')').map { it.lowercase() }
    val standsAlone = length in MIN_FACT_LENGTH..MAX_FACT_LENGTH &&
        first().isUpperCase() &&
        last() == '.' &&
        words.first() !in DANGLING_OPENINGS
    val notableWords = words.count { it in NOTABLE_WORDS }
    val hasNumber = any(Char::isDigit)
    return if (standsAlone && (notableWords > 0 || hasNumber)) notableWords * 2 + if (hasNumber) 1 else 0 else null
}
