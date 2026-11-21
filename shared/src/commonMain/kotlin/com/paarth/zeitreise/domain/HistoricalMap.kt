package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoCap

/** Digits between thousands separators, as in 123,000 BC. */
private const val DIGITS_PER_GROUP = 3

/** Years with at least this many digits get a separator: 10,000 BC does, 1492 doesn't. */
private const val GROUPED_FROM_DIGITS = 5

/**
 * The years that have a world map, oldest first. Years BC are negative, so 500 BC is -500. These
 * match the snapshots in the historical-basemaps project.
 */
public val MAP_YEARS: List<Int> = listOf(
    -123_000, -10_000, -8_000, -5_000, -4_000, -3_000, -2_000, -1_500, -1_000, -700, -500, -400, -323,
    -300, -200, -100, -1, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1279, 1300,
    1400, 1492, 1500, 1530, 1600, 1650, 1700, 1715, 1783, 1800, 1815, 1878, 1880, 1900, 1914, 1920,
    1930, 1938, 1945, 1960, 1994, 2000, 2010,
)

/**
 * A state, empire or people on one year's map.
 *
 * @property name the realm's English name.
 * @property partOf the larger realm it was part of that year, if any.
 */
public class Realm internal constructor(
    override val name: String,
    public val partOf: String?,
    override val outlines: List<CountryOutline>,
) : Territory {
    init {
        require(outlines.isNotEmpty()) { "Realm $name has no outlines" }
    }

    /** The region the camera frames when the realm is selected. */
    public val focus: GeoCap by lazy { outlines.mainlandFocus() }

    override fun toString(): String = "Realm($name)"
}

/**
 * The world map for one year.
 *
 * @property year the year, negative for BC.
 * @property realms the named realms, largest first.
 * @property unclaimedLand land without a named realm. It is drawn but can't be selected.
 */
public class HistoricalMap internal constructor(
    public val year: Int,
    public val realms: List<Realm>,
    public val unclaimedLand: List<CountryOutline>,
)

/** The year's number without BC or AD, with separators for big numbers: 1492, 123,000. */
public fun yearDigits(year: Int): String {
    val digits = kotlin.math.abs(year).toString()
    return if (digits.length <= GROUPED_FROM_DIGITS - 1) {
        digits
    } else {
        digits.reversed().chunked(DIGITS_PER_GROUP).joinToString(",").reversed()
    }
}
