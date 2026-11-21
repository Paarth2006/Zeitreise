package com.paarth.zeitreise.domain

/**
 * The passport as shown to the user.
 *
 * @property stamped the stamped countries, newest first.
 * @property total the number of countries and territories in total.
 * @property continents progress on each continent, most complete first.
 */
public data class PassportBook(val stamped: List<Country>, val total: Int, val continents: List<ContinentProgress>)

/**
 * Passport progress on one continent.
 *
 * @property continent the continent's name.
 * @property stamped how many of its countries are stamped.
 * @property total how many countries it has.
 */
public data class ContinentProgress(val continent: String, val stamped: Int, val total: Int)

/** The key a country's stamp is saved under: its Wikidata ID (stable across renames), or its name. */
public val Country.passportKey: String get() = wikidataId ?: name

/**
 * Builds the passport from [stamps], the [passportKey]s of stamped countries, oldest first.
 * Stamps for unknown countries are skipped.
 */
internal fun passportBook(countries: List<Country>, stamps: List<String>): PassportBook {
    val byKey = countries.associateBy { it.passportKey }
    val stamped = stamps.mapNotNull(byKey::get)
    val stampedSet = stamped.toSet()
    val continents = countries.groupBy { it.continent }
        .map { (continent, members) -> ContinentProgress(continent, members.count { it in stampedSet }, members.size) }
        .sortedWith(compareByDescending<ContinentProgress> { it.stamped }.thenByDescending { it.total })
    return PassportBook(stamped = stamped.reversed(), total = countries.size, continents = continents)
}
