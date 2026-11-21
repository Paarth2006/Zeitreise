package com.paarth.zeitreise.domain

import kotlin.random.Random

/**
 * The country of the day for [day], counted in days since 1970: the same on every device that day,
 * and a different one most days. It is picked among the [prominent][Country.isProminent] countries
 * with a capital, so it can be seen on the globe and there is always a story to read; `null` when
 * there are none.
 */
internal fun countryOfTheDay(countries: List<Country>, day: Long): Country? {
    // Sorted first, so the pick depends on the day alone and not on the order the countries came in.
    val candidates = countries.filter { it.isProminent && it.facts.capitals.isNotEmpty() }.sortedBy { it.passportKey }
    return candidates.takeIf { it.isNotEmpty() }?.get(Random(day).nextInt(candidates.size))
}
