package com.paarth.zeitreise.domain

import kotlin.random.Random

/** Number of questions in a quiz round. */
public const val QUIZ_LENGTH: Int = 10

/**
 * One quiz question: a country to find, asked by name or by capital.
 *
 * @property country the country to find.
 * @property capital the capital to show instead of the name, for capital questions.
 */
public data class QuizQuestion(val country: Country, val capital: String? = null)

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
 * One round of the quiz and the player's progress.
 *
 * @property questions the questions in order.
 * @property number index of the current question.
 * @property score number of correct answers so far.
 * @property streak correct answers in a row, up to the last answer.
 * @property answered whether the current question was answered or skipped.
 * @property guess the country tapped for the current question, or `null` if not answered or skipped.
 */
public data class QuizRound(
    val questions: List<QuizQuestion>,
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

    /** The question being asked now. */
    public val question: QuizQuestion get() = questions[number]

    /** The country the user is asked to find now. */
    public val country: Country get() = question.country

    /** Whether the current answer is right. Only meaningful once [answered]. */
    public val isRight: Boolean get() = guess === country

    /** Whether this is the last question. */
    public val isLast: Boolean get() = number == questions.lastIndex

    /**
     * Answers the current question with [guess], or skips it if [guess] is `null`. Each question
     * can only be answered once.
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

    /** Goes to the next question. The current one must be answered and not be the last. */
    public fun next(): QuizRound {
        check(answered) { "Question $number has not been answered yet" }
        check(!isLast) { "Question $number is the last of the round" }
        return copy(number = number + 1, answered = false, guess = null)
    }
}
