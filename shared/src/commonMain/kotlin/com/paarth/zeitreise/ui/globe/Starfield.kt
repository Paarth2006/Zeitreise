package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.random.Random

/** Number of stars per brightness group, faintest first. */
private val STARS_PER_GROUP = listOf(180, 70, 18)

/** Fixed seed, so the stars look the same every time. */
private const val STAR_SEED = 1492

/**
 * The stars behind the globe. Positions are stored as fractions of the canvas, so a resize only
 * rescales them and drawing allocates nothing.
 */
internal class Starfield {
    private val fractions: List<List<Offset>> = Random(STAR_SEED).let { random ->
        STARS_PER_GROUP.map { count -> List(count) { Offset(random.nextFloat(), random.nextFloat()) } }
    }
    private var laidOutFor = Size.Zero
    private var points: List<List<Offset>> = emptyList()

    /** Star positions for a canvas of [size], one list per brightness group. */
    fun pointsFor(size: Size): List<List<Offset>> {
        if (size != laidOutFor) {
            laidOutFor = size
            points = fractions.map { group -> group.map { Offset(it.x * size.width, it.y * size.height) } }
        }
        return points
    }
}
