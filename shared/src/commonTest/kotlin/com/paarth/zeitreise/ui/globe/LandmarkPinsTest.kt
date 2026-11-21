package com.paarth.zeitreise.ui.globe

import com.paarth.zeitreise.domain.HistoricLandmark
import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.GlobeCamera
import com.paarth.zeitreise.globe.GlobeProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The whole globe on a 1,000-pixel screen, centred on Beijing. */
private val WORLD_VIEW = GlobeProjection(
    camera = GlobeCamera().pointingAt(GeoPoint(latitude = 40.0, longitude = 116.0)),
    centerX = 500.0,
    centerY = 500.0,
    baseRadius = 400.0,
)

private fun pin(name: String, latitude: Double, longitude: Double) =
    HistoricLandmark(name, name, GeoPoint(latitude, longitude), built = 0)

private val GREAT_WALL = pin("Great Wall", 40.431, 116.564)
private val FORBIDDEN_CITY = pin("Forbidden City", 39.916, 116.391)
private val TERRACOTTA_ARMY = pin("Terracotta Army", 34.385, 109.273)

class LandmarkPinsTest {

    @Test
    fun aPinHasNotStartedFallingBeforeItsTurn() {
        assertEquals(0f, pinProgress(drop = 0.1f, index = 3, count = 8))
    }

    @Test
    fun everyPinRestsOnceTheDropIsOver() {
        (0 until 8).forEach { index -> assertEquals(1f, pinProgress(drop = 1f, index = index, count = 8), 1e-6f) }
    }

    @Test
    fun aPinOvershootsAsItLands() {
        val peak = (0..100).maxOf { step -> pinProgress(drop = step / 100f, index = 0, count = 1) }

        assertTrue(peak > 1f, "the pin should bounce past its resting place, peaked at $peak")
    }

    @Test
    fun laterPinsLandLater() {
        assertTrue(pinProgress(drop = 0.4f, index = 0, count = 8) > pinProgress(drop = 0.4f, index = 4, count = 8))
    }

    @Test
    fun everyPinLandsEvenWhenThereAreMany() {
        (0 until 60).forEach { index -> assertEquals(1f, pinProgress(drop = 1f, index = index, count = 60), 1e-6f) }
        assertTrue(pinProgress(drop = 0.99f, index = 59, count = 60) > 0f, "the last of 60 pins never started falling")
    }

    @Test
    fun hidesAPinThatWouldCoverAnother() {
        val placed = placePins(listOf(GREAT_WALL, FORBIDDEN_CITY, TERRACOTTA_ARMY), WORLD_VIEW, null, minGap = 20f)

        assertEquals(listOf(GREAT_WALL, TERRACOTTA_ARMY), placed.map { it.pin })
    }

    @Test
    fun alwaysShowsTheOpenPin() {
        val placed = placePins(listOf(GREAT_WALL, FORBIDDEN_CITY), WORLD_VIEW, FORBIDDEN_CITY, minGap = 20f)

        assertEquals(listOf(FORBIDDEN_CITY), placed.map { it.pin })
    }

    @Test
    fun showsBothPinsOnceZoomedInFarEnough() {
        val zoomed = GlobeProjection(
            camera = GlobeCamera().pointingAt(GeoPoint(latitude = 40.2, longitude = 116.5), zoom = 40.0),
            centerX = 500.0,
            centerY = 500.0,
            baseRadius = 400.0,
        )

        assertEquals(2, placePins(listOf(GREAT_WALL, FORBIDDEN_CITY), zoomed, null, minGap = 20f).size)
    }
}
