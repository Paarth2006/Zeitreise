package com.paarth.zeitreise.domain

/** Results shown for one query; enough to choose from without scrolling. */
public const val MAX_SEARCH_RESULTS: Int = 8

/**
 * The name of a state and the country it belongs to, as listed in the bundled state index, so a
 * state can be found without loading every country's outlines.
 *
 * @property name the state's English name.
 * @property countryCode the ISO 3166-1 alpha-2 code of its country.
 */
public data class SubdivisionName(val name: String, val countryCode: String)

/** A place a search found. */
public sealed interface SearchResult {
    /** The name shown in the result list. */
    public val name: String

    /** A country whose name matches the query. */
    public data class CountryMatch(val country: Country) : SearchResult {
        override val name: String get() = country.name
    }

    /** A state whose name matches the query, with the country it is in. */
    public data class SubdivisionMatch(override val name: String, val country: Country) : SearchResult
}

/** How well a name matches a query; lower is better, and [None] means no match at all. */
private enum class MatchQuality { Exact, Prefix, WordPrefix, Substring, None }

/**
 * Finds the countries and states whose names match [query], best first.
 *
 * Exact names come first, then names that start with the query, then names with a word that
 * starts with it, then names that merely contain it. A country ranks above a state of the same
 * quality. Case and common accents are ignored, so "entre rios" finds "Entre Ríos".
 *
 * @param subdivisions every state's name and country, resolved against [countries] by ISO code.
 * @return at most [limit] results, or none for a blank query.
 */
public fun searchPlaces(
    query: String,
    countries: List<Country>,
    subdivisions: List<SubdivisionName>,
    limit: Int = MAX_SEARCH_RESULTS,
): List<SearchResult> {
    val needle = query.foldForSearch()
    if (needle.isBlank()) return emptyList()

    val byCode = countries.filter { it.isoA2 != null }.associateBy { it.isoA2 }
    val countryMatches = countries.map { SearchResult.CountryMatch(it) to it.name.matchQuality(needle) }
    val subdivisionMatches = subdivisions.mapNotNull { entry ->
        val country = byCode[entry.countryCode] ?: return@mapNotNull null
        SearchResult.SubdivisionMatch(entry.name, country) to entry.name.matchQuality(needle)
    }
    return (countryMatches + subdivisionMatches)
        .filter { (_, quality) -> quality != MatchQuality.None }
        // A stable sort keeps countries ahead of states of the same quality.
        .sortedBy { (_, quality) -> quality }
        .take(limit)
        .map { (result, _) -> result }
}

private fun String.matchQuality(needle: String): MatchQuality {
    val haystack = foldForSearch()
    return when {
        haystack == needle -> MatchQuality.Exact
        haystack.startsWith(needle) -> MatchQuality.Prefix
        haystack.split(' ', '-').any { it.startsWith(needle) } -> MatchQuality.WordPrefix
        needle in haystack -> MatchQuality.Substring
        else -> MatchQuality.None
    }
}

/** Accented letters common in place names, mapped to the plain letters people type instead. */
private val PLAIN_LETTERS: Map<Char, Char> = buildMap {
    "áàâäãåā".forEach { put(it, 'a') }
    "éèêëē".forEach { put(it, 'e') }
    "íìîïī".forEach { put(it, 'i') }
    "óòôöõøō".forEach { put(it, 'o') }
    "úùûüū".forEach { put(it, 'u') }
    "ýÿ".forEach { put(it, 'y') }
    put('ç', 'c')
    put('ñ', 'n')
    put('ş', 's')
    put('ğ', 'g')
}

/** Lower-cases the text, drops common accents and trims it, so matching ignores both. */
internal fun String.foldForSearch(): String = trim().lowercase().map { PLAIN_LETTERS[it] ?: it }.joinToString("")
