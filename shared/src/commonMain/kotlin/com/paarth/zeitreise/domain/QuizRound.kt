package com.paarth.zeitreise.domain

import kotlin.random.Random

/** How many countries one round of the quiz asks for. */
public const val QUIZ_LENGTH: Int = 10

/**
 * One question of the quiz: a country to find on the globe, asked for by its name or by its capital.
 *
 * @property country the country to find.
 * @property capital the capital the question names instead of the country, when it asks by capital.
 */
public data class QuizQuestion(val country: Country, val capital: String? = null)

/**
 * Picks the questions for a round: [count] different [prominent][isProminent] countries, in
 * random order. About half of those with a capital are asked for by it.
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
 * One round of the quiz: a run of countries to find on the globe, and how the user is doing.
 *
 * @property questions the questions, in the order they are asked.
 * @property number the index in [questions] of the question being asked now.
 * @property score how many countries the user has found so far.
 * @property streak how many countries in a row the user has found, ending with the last answer.
 * @property answered whether the current question has been answered, or given up on.
 * @property guess the country the user tapped for the current question; `null` while the question
 *   is open, or when the user gave up and asked to be shown.
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
