package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoCap

/** Digits between the thousands separators of a year such as 123,000 BC. */
private const val DIGITS_PER_GROUP = 3

/** Fewest digits a year needs before it is grouped: 10,000 BC is, 1492 is not. */
private const val GROUPED_FROM_DIGITS = 5

/**
 * The years there is a world map for, oldest first; a year before Christ is negative, so 500 BC
 * is -500. They follow the snapshots of the historical-basemaps project, close together where
 * borders changed quickly and far apart in prehistory.
 */
public val MAP_YEARS: List<Int> = listOf(
    -123_000, -10_000, -8_000, -5_000, -4_000, -3_000, -2_000, -1_500, -1_000, -700, -500, -400, -323,
    -300, -200, -100, -1, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1279, 1300,
    1400, 1492, 1500, 1530, 1600, 1650, 1700, 1715, 1783, 1800, 1815, 1878, 1880, 1900, 1914, 1920,
    1930, 1938, 1945, 1960, 1994, 2000, 2010,
)

/**
 * A state, empire or people as it stood in one year, drawn from that year's map.
 *
 * @property name the realm's English name, as the map names it.
 * @property partOf the larger realm it belonged to that year, such as an empire's province, if any.
 */
public class Realm internal constructor(
    override val name: String,
    public val partOf: String?,
    override val outlines: List<CountryOutline>,
) : Territory {
    init {
        require(outlines.isNotEmpty()) { "Realm $name has no outlines" }
    }

    /** The region the camera frames when the realm is selected: its heartland and nearby lands. */
    public val focus: GeoCap by lazy { outlines.mainlandFocus() }

    override fun toString(): String = "Realm($name)"
}

/**
 * The world as one historical map draws it.
 *
 * @property year the year the map shows; negative before Christ.
 * @property realms the named states, empires and peoples, largest first.
 * @property unclaimedLand land no named realm held that year, drawn but not selectable.
 */
public class HistoricalMap internal constructor(
    public val year: Int,
    public val realms: List<Realm>,
    public val unclaimedLand: List<CountryOutline>,
)

/**
 * The year's number without its era: 1492, or for the deep past grouped by thousands, as
 * historians write it, 123,000 for 123,000 BC.
 */
public fun yearDigits(year: Int): String {
    val digits = kotlin.math.abs(year).toString()
    return if (digits.length <= GROUPED_FROM_DIGITS - 1) {
        digits
    } else {
        digits.reversed().chunked(DIGITS_PER_GROUP).joinToString(",").reversed()
    }
}
