package com.paarth.zeitreise.domain

/** How many facts to take from one article. */
public const val MAX_NOTABLE_SENTENCES: Int = 8

/** Shorter sentences rarely make a complete fact. */
private const val MIN_FACT_LENGTH = 60

/** Longest sentence shown, so it fits in the sheet. */
private const val MAX_FACT_LENGTH = 240

/** Words that usually make a sentence interesting. */
private val NOTABLE_WORDS = setOf(
    "largest", "smallest", "highest", "lowest", "longest", "shortest", "oldest", "newest", "deepest",
    "tallest", "biggest", "first", "only", "most", "least", "world", "record", "unique", "famous",
    "ancient", "founded", "discovered", "invented", "named", "known",
)

/** Openings that refer to an earlier sentence, so they don't work alone. */
private val DANGLING_OPENINGS = setOf("it", "this", "these", "they", "he", "she", "his", "her", "its", "their", "such")

/** A sentence ends with `.`, `?` or `!`, then a space and a capital letter. */
private val SENTENCE_BREAK = Regex("""(?<=[.!?])\s+(?=[A-Z"“(])""")

/**
 * Picks the sentences from an article that are most likely to be interesting facts.
 *
 * A sentence counts if it makes sense on its own, isn't too long, and has a number or a word like
 * "largest", "first" or "only". The article is split into [limit] parts and the best sentence from
 * each part is taken, so the facts cover the whole article and not just the start.
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

/** A candidate sentence, its position in the article and its score. */
private data class Scored(val position: Int, val sentence: String, val score: Int)

/** A score for how interesting the sentence is, or `null` if it can't be shown alone. */
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
