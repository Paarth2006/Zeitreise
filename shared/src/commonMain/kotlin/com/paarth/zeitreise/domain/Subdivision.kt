package com.paarth.zeitreise.domain

import com.paarth.zeitreise.globe.GeoPoint

/**
 * A state, province or other first-level subdivision of a country, shown once the country is
 * selected.
 *
 * @property name the English name, as shown on its label and in the sheet.
 * @property kind what the country calls it, such as "State", "Province" or "Union Territory".
 * @property isoCode the ISO 3166-2 code, such as "IN-KL", or `null` where none is assigned.
 * @property wikidataId the Wikidata item, which leads to the subdivision's exact Wikipedia article,
 *   or `null` where Natural Earth has none.
 * @property labelPoint where its name is drawn on the globe.
 * @property outlines the landmasses it is drawn from, largest first.
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
