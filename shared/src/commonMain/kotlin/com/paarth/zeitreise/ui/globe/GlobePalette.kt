package com.paarth.zeitreise.ui.globe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * The colours the globe is drawn with.
 *
 * Resolved once per composition and then only read inside the draw loop, so that no colour is
 * derived per frame.
 *
 * @property ocean radial gradient of the sphere, lit side first.
 * @property land fill of an unselected country.
 * @property landSelected fill of the selected country.
 * @property border outline of an unselected country.
 * @property borderSelected outline of the selected country.
 * @property graticule the meridians and parallels.
 * @property atmosphere the halo outside the limb, innermost ring first.
 */
internal data class GlobePalette(
    val ocean: List<Color>,
    val land: Color,
    val landSelected: Color,
    val border: Color,
    val borderSelected: Color,
    val graticule: Color,
    val atmosphere: List<Color>,
)

/** Number of rings the atmosphere halo is built from. */
private const val ATMOSPHERE_RINGS = 7

/** Alpha of the innermost atmosphere ring, which fades to nothing by the outermost. */
private const val ATMOSPHERE_ALPHA = 0.16f

/** Builds the globe palette, holding it across recompositions. */
@Composable
internal fun rememberGlobePalette(): GlobePalette = remember {
    val glow = Color(0xFF4F7FB5)
    GlobePalette(
        ocean = listOf(Color(0xFF13233D), Color(0xFF0B1527), Color(0xFF060B14)),
        land = Color(0xFF26354A),
        landSelected = Color(0xFF6E5A2E),
        border = Color(0xFF6E86A6),
        borderSelected = Color(0xFFF2D9A0),
        graticule = Color(0x22A8C4E8),
        atmosphere = List(ATMOSPHERE_RINGS) { ring ->
            val fade = 1f - ring.toFloat() / ATMOSPHERE_RINGS
            glow.copy(alpha = ATMOSPHERE_ALPHA * fade * fade)
        },
    )
}
