package com.paarth.zeitreise.domain

/**
 * The user's passport as it is shown: the countries stamped, and how much of the world they cover.
 *
 * @property stamped the countries stamped so far, the most recent first.
 * @property total how many countries and territories there are to visit.
 * @property continents how far the user has got on each continent, the best covered first.
 */
public data class PassportBook(val stamped: List<Country>, val total: Int, val continents: List<ContinentProgress>)

/**
 * How many of a continent's countries hold a stamp.
 *
 * @property continent the continent's name.
 * @property stamped how many of its countries are stamped.
 * @property total how many countries it has.
 */
public data class ContinentProgress(val continent: String, val stamped: Int, val total: Int)

/** What a country's stamp is filed under: its Wikidata item, which outlives renames, or else its name. */
public val Country.passportKey: String get() = wikidataId ?: name

/**
 * Opens the passport holding [stamps], the [passportKey]s of the countries stamped, oldest first.
 * Stamps for countries the app no longer knows are left out.
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
