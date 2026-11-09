package com.paarth.zeitreise.geo

import com.paarth.zeitreise.globe.GeoPoint
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A ten-degree square around the origin, as a GeoJSON Polygon feature. */
private fun squareFeature(
    name: String = "Squareland",
    isoA2: String? = "SQ",
    isoA3: String? = "SQR",
    continent: String = "Atlantis",
    origin: Int = 0,
    size: Int = 10,
) = """
    {
      "type": "Feature",
      "properties": {
        "name": "$name",
        "isoA2": ${isoA2?.let { "\"$it\"" } ?: "null"},
        "isoA3": ${isoA3?.let { "\"$it\"" } ?: "null"},
        "continent": "$continent"
      },
      "geometry": {
        "type": "Polygon",
        "coordinates": [[
          [$origin, $origin],
          [${origin + size}, $origin],
          [${origin + size}, ${origin + size}],
          [$origin, ${origin + size}],
          [$origin, $origin]
        ]]
      }
    }
""".trimIndent()

private fun collectionOf(vararg features: String) =
    """{ "type": "FeatureCollection", "features": [${features.joinToString(",")}] }"""

class GeoJsonParserTest {

    private val parser = GeoJsonParser()

    @Test
    fun readsThePropertiesOfACountry() {
        val country = parser.parseCountries(collectionOf(squareFeature())).single()
        assertEquals("Squareland", country.name)
        assertEquals("SQ", country.isoA2)
        assertEquals("SQR", country.isoA3)
        assertEquals("Atlantis", country.continent)
    }

    @Test
    fun keepsMissingIsoCodesAsNull() {
        val feature = squareFeature(isoA2 = null, isoA3 = null)
        val country = parser.parseCountries(collectionOf(feature)).single()
        assertNull(country.isoA2)
        assertNull(country.isoA3)
    }

    @Test
    fun readsAPolygonAsASingleOutlineWithNoHoles() {
        val country = parser.parseCountries(collectionOf(squareFeature())).single()
        assertEquals(1, country.outlines.size)
        assertTrue(country.outlines.single().holes.isEmpty())
    }

    @Test
    fun readsAMultiPolygonAsOneOutlinePerLandmass() {
        val source = """
            { "type": "FeatureCollection", "features": [{
              "type": "Feature",
              "properties": { "name": "Twoland", "isoA2": "TW", "isoA3": "TWO", "continent": "Atlantis" },
              "geometry": {
                "type": "MultiPolygon",
                "coordinates": [
                  [[[0,0],[10,0],[10,10],[0,10],[0,0]]],
                  [[[20,20],[30,20],[30,30],[20,30],[20,20]]]
                ]
              }
            }] }
        """.trimIndent()
        val country = parser.parseCountries(source).single()
        assertEquals(2, country.outlines.size)
        assertTrue(country.contains(GeoPoint(latitude = 5.0, longitude = 5.0)))
        assertTrue(country.contains(GeoPoint(latitude = 25.0, longitude = 25.0)))
        assertFalse(country.contains(GeoPoint(latitude = 15.0, longitude = 15.0)))
    }

    @Test
    fun readsRingsAfterTheFirstAsHoles() {
        val source = """
            { "type": "FeatureCollection", "features": [{
              "type": "Feature",
              "properties": { "name": "Holeland", "isoA2": "HL", "isoA3": "HOL", "continent": "Atlantis" },
              "geometry": {
                "type": "Polygon",
                "coordinates": [
                  [[0,0],[20,0],[20,20],[0,20],[0,0]],
                  [[8,8],[12,8],[12,12],[8,12],[8,8]]
                ]
              }
            }] }
        """.trimIndent()
        val country = parser.parseCountries(source).single()
        assertEquals(1, country.outlines.single().holes.size)
        assertTrue(country.contains(GeoPoint(latitude = 2.0, longitude = 2.0)))
        assertFalse(country.contains(GeoPoint(latitude = 10.0, longitude = 10.0)), "the hole is not land")
    }

    @Test
    fun ordersCountriesLargestFirst() {
        val small = squareFeature(name = "Small", origin = 40, size = 2)
        val large = """
            {
              "type": "Feature",
              "properties": { "name": "Large", "isoA2": "LG", "isoA3": "LRG", "continent": "Atlantis" },
              "geometry": { "type": "Polygon", "coordinates": [[
                [0,0],[10,0],[20,3],[30,0],[30,10],[20,14],[10,10],[0,10],[0,0]
              ]] }
            }
        """.trimIndent()
        val names = parser.parseCountries(collectionOf(small, large)).map { it.name }
        assertEquals(listOf("Large", "Small"), names)
    }

    @Test
    fun dropsFeaturesWhoseGeometryIsNotAPolygon() {
        val point = """
            {
              "type": "Feature",
              "properties": { "name": "Dot", "isoA2": "DT", "isoA3": "DOT", "continent": "Atlantis" },
              "geometry": { "type": "Point", "coordinates": [1, 2] }
            }
        """.trimIndent()
        assertTrue(parser.parseCountries(collectionOf(point)).isEmpty())
    }

    @Test
    fun dropsRingsTooSmallToDraw() {
        val sliver = """
            {
              "type": "Feature",
              "properties": { "name": "Sliver", "isoA2": "SL", "isoA3": "SLV", "continent": "Atlantis" },
              "geometry": { "type": "Polygon", "coordinates": [[[0,0],[1,0]]] }
            }
        """.trimIndent()
        assertTrue(parser.parseCountries(collectionOf(sliver)).isEmpty())
    }

    @Test
    fun readsAnEmptyCollection() {
        assertTrue(parser.parseCountries(collectionOf()).isEmpty())
    }

    @Test
    fun ignoresPropertiesItDoesNotKnow() {
        val extra = """
            {
              "type": "Feature",
              "properties": {
                "name": "Extraland", "isoA2": "EX", "isoA3": "EXT", "continent": "Atlantis",
                "POP_EST": 1234, "unexpected": { "nested": true }
              },
              "geometry": { "type": "Polygon", "coordinates": [[[0,0],[10,0],[10,10],[0,10],[0,0]]] }
            }
        """.trimIndent()
        assertEquals("Extraland", parser.parseCountries(collectionOf(extra)).single().name)
    }

    @Test
    fun failsOnSomethingThatIsNotGeoJson() {
        assertFailsWith<SerializationException> { parser.parseCountries("""{ "nope": true, "features": 3 }""") }
    }
}
