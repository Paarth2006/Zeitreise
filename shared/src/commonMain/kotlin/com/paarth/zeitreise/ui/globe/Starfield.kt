package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.random.Random

/** Stars per brightness group, faintest first: many faint stars and a few bright ones. */
private val STARS_PER_GROUP = listOf(180, 70, 18)

/** Fixed seed, so the same sky appears on every launch and every platform. */
private const val STAR_SEED = 1492

/**
 * The stars behind the globe, scattered once and scaled to whatever size the globe is drawn at.
 *
 * Positions are kept as fractions of the canvas, so a resize only rescales them; the draw loop
 * reads the same lists every frame and allocates nothing.
 */
internal class Starfield {
    private val fractions: List<List<Offset>> = Random(STAR_SEED).let { random ->
        STARS_PER_GROUP.map { count -> List(count) { Offset(random.nextFloat(), random.nextFloat()) } }
    }
    private var laidOutFor = Size.Zero
    private var points: List<List<Offset>> = emptyList()

    /** The stars laid out on a canvas of [size], one list per brightness group, faintest first. */
    fun pointsFor(size: Size): List<List<Offset>> {
        if (size != laidOutFor) {
            laidOutFor = size
            points = fractions.map { group -> group.map { Offset(it.x * size.width, it.y * size.height) } }
        }
        return points
    }
}
