package com.paarth.zeitreise.ui.globe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
