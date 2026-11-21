package com.paarth.zeitreise.ui

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.CountryOutline
import com.paarth.zeitreise.domain.CountryRing
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.globe.GeoPoint

/** Sample data for `@Preview`s, since the preview renderer can't load the bundled outlines. */
internal object PreviewSamples {
    /** India as a simple square. Previews only need the name and continent. */
    val india: Country = Country(
        name = "India",
        isoA2 = "IN",
        continent = "Asia",
        outlines = listOf(square(west = 68.0, south = 8.0, size = 20.0)),
        wikidataId = "Q668",
    )

    /** Kerala, one of India's states. */
    val kerala: Subdivision = Subdivision(
        name = "Kerala",
        kind = "State",
        isoCode = "IN-KL",
        wikidataId = "Q1186",
        labelPoint = GeoPoint(latitude = 10.4, longitude = 76.4),
        outlines = listOf(square(west = 75.0, south = 8.0, size = 4.0)),
    )

    private fun square(west: Double, south: Double, size: Double): CountryOutline = CountryOutline(
        exterior = CountryRing(
            doubleArrayOf(
                west, south,
                west + size, south,
                west + size, south + size,
                west, south + size,
                west, south,
            ),
        ),
        holes = emptyList(),
    )
}
