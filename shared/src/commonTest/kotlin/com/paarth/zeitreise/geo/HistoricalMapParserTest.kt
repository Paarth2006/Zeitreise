package com.paarth.zeitreise.geo

import com.paarth.zeitreise.domain.contains
import com.paarth.zeitreise.globe.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A historical-basemaps feature: a [size]-degree square at [origin], named [name] or unnamed. */
private fun realmFeature(name: String?, origin: Int, size: Int = 10, partOf: String? = name) = """
    {
      "type": "Feature",
      "properties": {
        "NAME": ${name?.let { "\"$it\"" } ?: "null"},
        "ABBREVN": null,
        "SUBJECTO": null,
        "BORDERPRECISION": 1,
        "PARTOF": ${partOf?.let { "\"$it\"" } ?: "null"}
      },
      "geometry": {
        "type": "MultiPolygon",
        "coordinates": [[[
          [$origin, $origin],
          [${origin + size}, $origin],
          [${origin + size}, ${origin + size}],
          [$origin, ${origin + size}],
          [$origin, $origin]
        ]]]
      }
    }
""".trimIndent()

private fun worldOf(vararg features: String) = """
    { "type": "FeatureCollection", "name": "world_1492",
      "crs": { "type": "name", "properties": { "name": "urn:ogc:def:crs:OGC:1.3:CRS84" } },
      "features": [${features.joinToString(",")}] }
""".trimIndent()

class HistoricalMapParserTest {

    private val parser = GeoJsonParser()

    @Test
    fun readsEachNamedRealmForTheYear() {
        val map = parser.parseHistoricalMap(1492, worldOf(realmFeature("Castille", origin = 0)))

        assertEquals(1492, map.year)
        assertEquals("Castille", map.realms.single().name)
        assertTrue(map.realms.single().contains(GeoPoint(latitude = 5.0, longitude = 5.0)))
    }

    @Test
    fun joinsTheScatteredLandsOfOneRealm() {
        val map = parser.parseHistoricalMap(
            1492,
            worldOf(realmFeature("Aztec Empire", origin = 0), realmFeature("Aztec Empire", origin = 30)),
        )

        assertEquals(2, map.realms.single().outlines.size)
    }

    @Test
    fun keepsUnnamedLandAsUnclaimed() {
        val map = parser.parseHistoricalMap(1492, worldOf(realmFeature(null, origin = 0), realmFeature("Ming", 40)))

        assertEquals(listOf("Ming"), map.realms.map { it.name })
        assertEquals(1, map.unclaimedLand.size)
    }

    @Test
    fun recordsTheRealmARegionBelongedTo() {
        val map = parser.parseHistoricalMap(
            1815,
            worldOf(realmFeature("Bengal", origin = 0, partOf = "British East India Company")),
        )

        assertEquals("British East India Company", map.realms.single().partOf)
    }

    @Test
    fun doesNotCountARealmAsPartOfItself() {
        assertNull(parser.parseHistoricalMap(1492, worldOf(realmFeature("France", origin = 0))).realms.single().partOf)
    }
}
