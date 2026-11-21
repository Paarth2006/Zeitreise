package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint

/**
 * A state, province or similar region of a country, shown when the country is selected.
 *
 * @property name the English name.
 * @property kind what the country calls it, like "State", "Province" or "Union Territory".
 * @property isoCode the ISO 3166-2 code, like "IN-KL", or `null` if it has none.
 * @property wikidataId its Wikidata item, used to find the right Wikipedia article, or `null`.
 * @property labelPoint where its name is drawn.
 * @property outlines its landmasses, largest first.
 */
public class Subdivision internal constructor(
    override val name: String,
    public val kind: String,
    public val isoCode: String?,
    public val wikidataId: String?,
    public val labelPoint: GeoPoint,
    override val outlines: List<CountryOutline>,
) : Territory {
    init {
        require(outlines.isNotEmpty()) { "Subdivision $name has no outlines" }
    }

    override fun toString(): String = "Subdivision($name)"
}
