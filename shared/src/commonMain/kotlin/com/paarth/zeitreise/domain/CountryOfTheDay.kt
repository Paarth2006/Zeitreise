package com.paarth.zeitreise.domain

import kotlin.random.Random

/**
 * Picks the country of the day for [day] (days since 1970). Every device gets the same country on
 * the same day. Only [prominent][Country.isProminent] countries with a capital are picked.
 */
internal fun countryOfTheDay(countries: List<Country>, day: Long): Country? {
    // Sort first so the result only depends on the day, not on the input order.
    val candidates = countries.filter { it.isProminent && it.facts.capitals.isNotEmpty() }.sortedBy { it.passportKey }
    return candidates.takeIf { it.isNotEmpty() }?.get(Random(day).nextInt(candidates.size))
}
