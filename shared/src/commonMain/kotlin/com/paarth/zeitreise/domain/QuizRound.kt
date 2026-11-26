package com.paarth.zeitreise.domain

import kotlin.random.Random

/** Number of questions in a quiz round. */
public const val QUIZ_LENGTH: Int = 10

/** How many questions of a round are about the past, asked last on an old map. */
public const val PAST_QUESTIONS: Int = 3

/**
 * The years past questions are asked about: turning points with maps that look clearly different
 * from today's, so the realms are worth finding.
 */
public val QUIZ_YEARS: List<Int> = listOf(-323, 1, 500, 1000, 1279, 1492, 1600, 1715, 1815, 1914)

/** Past questions ask for one of this many largest realms of the year, which are the well-known ones. */
private const val PAST_CANDIDATES = 6

/**
 * Words in the names of peoples and loose groups on the old maps, such as "Amazon hunter-gatherers"
 * or "Islamic and Hindu states". They cover huge areas but aren't realms anyone could name.
 */
private val GROUP_WORDS = listOf(
    "hunter", "gatherer", "people", "farmer", "herder", "pastoralist", "nomad", "tribe", "culture",
    "chiefdom", " states", "khanates",
)

/**
 * One quiz question: a place to find on the globe. A country of today is asked by name or by
 * capital; a realm of the past is asked on that year's [map].
 *
 * @property target the country or realm to find.
 * @property capital the capital to show instead of the name, for capital questions.
 * @property map the year's map for a question about the past, or `null` for today's world.
 */
public data class QuizQuestion(val target: Territory, val capital: String? = null, val map: HistoricalMap? = null)

/**
 * Picks [count] different [prominent][isProminent] countries in random order. About half of the
 * ones with a capital are asked by capital.
 */
internal fun quizQuestions(countries: List<Country>, random: Random, count: Int = QUIZ_LENGTH): List<QuizQuestion> =
    countries
        .filter { it.isProminent }
        .shuffled(random)
        .take(count)
        .map { country ->
            QuizQuestion(country, capital = country.facts.capitals.firstOrNull()?.takeIf { random.nextBoolean() })
        }

/**
 * A question about one of the independent realms with the most land on [map], such as the Ottoman
 * Empire in 1600, or `null` if the map has no such realm. Land area, not outline size, so an empire
 * of scattered islands across the Pacific doesn't count as huge.
 */
internal fun pastQuestion(map: HistoricalMap, random: Random): QuizQuestion? = map.realms
    .filter { it.partOf == null && it.isNamedRealm }
    .sortedByDescending { realm -> realm.outlines.sumOf { it.exterior.area } }
    .take(PAST_CANDIDATES)
    .randomOrNull(random)
    ?.let { QuizQuestion(it, map = map) }

/** Whether this has a proper name, like "Ming Chinese Empire", rather than describing a group of peoples. */
private val Realm.isNamedRealm: Boolean
    get() = name.firstOrNull()?.isUpperCase() == true && GROUP_WORDS.none { it in name.lowercase() }

/**
 * One round of the quiz and the player's progress.
 *
 * @property questions the questions in order. More can be added while the round runs, as the
 *   maps for the past questions arrive.
 * @property length how many questions the round will have, to show as the total.
 * @property number index of the current question.
 * @property score number of correct answers so far.
 * @property streak correct answers in a row, up to the last answer.
 * @property answered whether the current question was answered or skipped.
 * @property guess the country or realm tapped for the current question, or `null` if not answered or skipped.
 */
public data class QuizRound(
    val questions: List<QuizQuestion>,
    val length: Int = questions.size,
    val number: Int = 0,
    val score: Int = 0,
    val streak: Int = 0,
    val answered: Boolean = false,
    val guess: Territory? = null,
) {
    init {
        require(questions.isNotEmpty()) { "A quiz round needs at least one question" }
        require(number in questions.indices) { "Question $number is not in a round of ${questions.size}" }
        require(length >= questions.size) { "A round of $length can't hold ${questions.size} questions" }
    }

    /** The question being asked now. */
    public val question: QuizQuestion get() = questions[number]

    /** The country or realm the user is asked to find now. */
    public val target: Territory get() = question.target

    /** Whether the current answer is right. Only meaningful once [answered]. */
    public val isRight: Boolean get() = guess === target

    /** Whether this is the last question. */
    public val isLast: Boolean get() = number == questions.lastIndex

    /**
     * Answers the current question with [guess], or skips it if [guess] is `null`. Each question
     * can only be answered once.
     */
    public fun answer(guess: Territory?): QuizRound {
        if (answered) return this
        val right = guess === target
        return copy(
            answered = true,
            guess = guess,
            score = if (right) score + 1 else score,
            streak = if (right) streak + 1 else 0,
        )
    }

    /** Goes to the next question. The current one must be answered and not be the last. */
    public fun next(): QuizRound {
        check(answered) { "Question $number has not been answered yet" }
        check(!isLast) { "Question $number is the last of the round" }
        return copy(number = number + 1, answered = false, guess = null)
    }
}
