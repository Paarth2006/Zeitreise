package com.paarth.zeitreise.domain

import kotlin.random.Random

/** How many countries one round of the quiz asks for. */
public const val QUIZ_LENGTH: Int = 10

/**
 * The smallest a country's largest landmass may be, in square degrees (about 9,000 km², the size
 * of Jamaica), to be asked about. Anything smaller is a speck on the whole globe that no finger can
 * be expected to hit, such as Luxembourg, Singapore or a chain of Pacific islands.
 */
private const val MIN_QUIZ_AREA = 0.75

/**
 * Picks the countries for a round: [count] different ones, in random order, each large enough to
 * tap and each with an ISO code, which leaves out territories such as Kosovo or Somaliland whose
 * status the standard does not settle.
 */
internal fun quizQuestions(countries: List<Country>, random: Random, count: Int = QUIZ_LENGTH): List<Country> =
    countries
        .filter { it.isoA2 != null && it.outlines.first().exterior.area >= MIN_QUIZ_AREA }
        .shuffled(random)
        .take(count)

/**
 * One round of the quiz: a run of countries to find on the globe, and how the user is doing.
 *
 * @property questions the countries to find, in the order they are asked.
 * @property number the index in [questions] of the country being asked about now.
 * @property score how many countries the user has found so far.
 * @property streak how many countries in a row the user has found, ending with the last answer.
 * @property answered whether the current question has been answered, or given up on.
 * @property guess the country the user tapped for the current question; `null` while the question
 *   is open, or when the user gave up and asked to be shown.
 */
public data class QuizRound(
    val questions: List<Country>,
    val number: Int = 0,
    val score: Int = 0,
    val streak: Int = 0,
    val answered: Boolean = false,
    val guess: Country? = null,
) {
    init {
        require(questions.isNotEmpty()) { "A quiz round needs at least one question" }
        require(number in questions.indices) { "Question $number is not in a round of ${questions.size}" }
    }

    /** The country the user is asked to find now. */
    public val country: Country get() = questions[number]

    /** Whether the user found the current country; only meaningful once [answered]. */
    public val isRight: Boolean get() = guess === country

    /** Whether the current question is the last of the round. */
    public val isLast: Boolean get() = number == questions.lastIndex

    /**
     * Answers the current question with [guess], or gives it up when [guess] is `null`, keeping
     * score. A question can be answered only once, so later taps change nothing.
     */
    public fun answer(guess: Country?): QuizRound {
        if (answered) return this
        val right = guess === country
        return copy(
            answered = true,
            guess = guess,
            score = if (right) score + 1 else score,
            streak = if (right) streak + 1 else 0,
        )
    }

    /** Moves on to the next question, once the current one is answered and it is not the last. */
    public fun next(): QuizRound {
        check(answered) { "Question $number has not been answered yet" }
        check(!isLast) { "Question $number is the last of the round" }
        return copy(number = number + 1, answered = false, guess = null)
    }
}
