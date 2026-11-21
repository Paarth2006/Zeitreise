package com.paarth.zeitreise.ui.globe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * The colours used to draw the globe. Created once, so no colours are computed per frame.
 *
 * @property ocean the ocean gradient, lit side first.
 * @property land the country fill shades, so neighbours usually look different.
 * @property subdivisionLand lighter shades for the selected country's states.
 * @property pastLand warmer shades for historical realms.
 * @property unclaimedLand fill for land without a realm.
 * @property ice fill for Antarctica and Greenland.
 * @property landSelected fill of the selected country.
 * @property quizRight fill of the country the quiz asked for, once it is answered.
 * @property quizWrong fill of the country the user tapped instead.
 * @property border outline of an unselected country.
 * @property borderSelected outline of the selected country.
 * @property graticule the meridians and parallels.
 * @property atmosphere the glow around the globe, innermost ring first.
 * @property stars the starfield behind the globe, faintest first.
 * @property label the names of states drawn on the globe.
 * @property labelHalo the glow behind state names that keeps them readable.
 */
internal data class GlobePalette(
    val ocean: List<Color>,
    val land: List<Color>,
    val subdivisionLand: List<Color>,
    val pastLand: List<Color>,
    val unclaimedLand: Color,
    val ice: Color,
    val landSelected: Color,
    val quizRight: Color,
    val quizWrong: Color,
    val border: Color,
    val borderSelected: Color,
    val graticule: Color,
    val atmosphere: List<Color>,
    val stars: List<Color>,
    val label: Color,
    val labelHalo: Color,
)

/** Number of rings in the atmosphere glow. */
private const val ATMOSPHERE_RINGS = 7

/** Alpha of the innermost ring. The outer rings fade to zero. */
private const val ATMOSPHERE_ALPHA = 0.35f

/** Creates the globe palette once and remembers it. */
@Composable
internal fun rememberGlobePalette(): GlobePalette = remember {
    val glow = Color(0xFF8CC4FF)
    GlobePalette(
        ocean = listOf(Color(0xFF6AB2F0), Color(0xFF3483D4), Color(0xFF1A559E)),
        land = listOf(Color(0xFFA9C98A), Color(0xFF9CC07F), Color(0xFFB7CF94), Color(0xFF91B677)),
        subdivisionLand = listOf(Color(0xFFCFE1B6), Color(0xFFC0D8A6), Color(0xFFD9E7C2), Color(0xFFB7D29C)),
        pastLand = listOf(
            Color(0xFFE3B778),
            Color(0xFFC98F6B),
            Color(0xFFB5A26B),
            Color(0xFFD4A0A0),
            Color(0xFF9FB585),
            Color(0xFFC3A9CF),
            Color(0xFFE0C68F),
            Color(0xFF8FB3B0),
        ),
        unclaimedLand = Color(0xFFDCD3B8),
        ice = Color(0xFFEAF1F5),
        landSelected = Color(0xFFF2C96B),
        quizRight = Color(0xFF5BD68A),
        quizWrong = Color(0xFFF2726B),
        border = Color(0x99FFFFFF),
        borderSelected = Color(0xFFFFFFFF),
        graticule = Color(0x1FFFFFFF),
        atmosphere = List(ATMOSPHERE_RINGS) { ring ->
            val fade = 1f - ring.toFloat() / ATMOSPHERE_RINGS
            glow.copy(alpha = ATMOSPHERE_ALPHA * fade * fade)
        },
        stars = listOf(Color(0x40FFFFFF), Color(0x80FFFFFF), Color(0xCCFFFFFF)),
        label = Color(0xFF1F2D3A),
        labelHalo = Color(0xE6FFFFFF),
    )
}
