package com.paarth.zeitreise.domain

import com.paarth.zeitreise.geo.GeoJsonParser
import com.paarth.zeitreise.globe.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A square country, with [size] degrees of side, whose south-west corner is at [origin]. */
private fun square(name: String, origin: Int, size: Int) = """
    {
      "type": "Feature",
      "properties": { "name": "$name", "isoA2": null, "isoA3": null, "continent": "Atlantis" },
      "geometry": { "type": "Polygon", "coordinates": [[
        [$origin, $origin],
        [${origin + size}, $origin],
        [${origin + size}, ${origin + size}],
        [$origin, ${origin + size}],
        [$origin, $origin]
      ]] }
    }
""".trimIndent()

/** A ring of [vertices] vertices around [origin], so that one country has more vertices than another. */
private fun blob(name: String, origin: Int, size: Int, vertices: Int): String {
    val points = (0 until vertices).joinToString(",") { step ->
        val fraction = step.toDouble() / vertices
        val angle = fraction * 2.0 * kotlin.math.PI
        val x = origin + size / 2.0 + size / 2.0 * kotlin.math.cos(angle)
        val y = origin + size / 2.0 + size / 2.0 * kotlin.math.sin(angle)
        "[$x,$y]"
    }
    return """
        {
          "type": "Feature",
          "properties": { "name": "$name", "isoA2": null, "isoA3": null, "continent": "Atlantis" },
          "geometry": { "type": "Polygon", "coordinates": [[$points]] }
        }
    """.trimIndent()
}

private fun countriesOf(vararg features: String) =
    GeoJsonParser().parseCountries("""{ "type": "FeatureCollection", "features": [${features.joinToString(",")}] }""")

class CountryTest {

    @Test
    fun findsTheCountryUnderAPoint() {
        val countries = countriesOf(
            square("Westland", origin = 0, size = 10),
            square("Eastland", origin = 20, size = 10),
        )
        assertEquals("Westland", countries.territoryAt(GeoPoint(latitude = 5.0, longitude = 5.0))?.name)
        assertEquals("Eastland", countries.territoryAt(GeoPoint(latitude = 25.0, longitude = 25.0))?.name)
    }

    @Test
    fun findsNoCountryInTheOpenOcean() {
        val countries = countriesOf(square("Westland", origin = 0, size = 10))
        assertNull(countries.territoryAt(GeoPoint(latitude = 50.0, longitude = 50.0)))
    }

    @Test
    fun findsNoCountryInAnEmptyWorld() {
        assertNull(emptyList<Country>().territoryAt(GeoPoint(latitude = 0.0, longitude = 0.0)))
    }

    @Test
    fun prefersTheSmallerOfTwoOverlappingCountries() {
        // A detailed country enclosing a simple one, as South Africa encloses Lesotho.
        val enclosing = blob("Enclosingland", origin = 0, size = 40, vertices = 60)
        val enclosed = square("Enclavia", origin = 18, size = 4)
        val countries = countriesOf(enclosing, enclosed)
        assertEquals("Enclavia", countries.territoryAt(GeoPoint(latitude = 20.0, longitude = 20.0))?.name)
    }

    @Test
    fun namesItselfInItsStringForm() {
        val country = countriesOf(square("Namedland", origin = 0, size = 10)).single()
        assertEquals("Country(Namedland)", country.toString())
    }

    @Test
    fun framesTheMainlandWithItsNearbyIslandsButNotAFarTerritory() {
        val country = GeoJsonParser().parseCountries(
            """
            { "type": "FeatureCollection", "features": [ {
              "type": "Feature",
              "properties": { "name": "Islandia", "isoA2": "IS", "isoA3": "ISL", "continent": "Atlantis" },
              "geometry": { "type": "MultiPolygon", "coordinates": [
                [[[90,0],[91,0],[91,1],[90,1],[90,0]]],
                [[[0,0],[10,0],[10,10],[0,10],[0,0]]],
                [[[14,4],[15,4],[15,5],[14,5],[14,4]]]
              ] }
            } ] }
            """.trimIndent(),
        ).single()

        val focus = country.focus

        assertTrue(focus.radiusDegrees > 8.0, "the nearby island widens the frame: ${focus.radiusDegrees}")
        assertTrue(focus.radiusDegrees < 20.0, "the far territory stays out: ${focus.radiusDegrees}")
        assertTrue(focus.center.longitude in 0.0..15.0, "centred on the mainland: ${focus.center}")
    }
}
