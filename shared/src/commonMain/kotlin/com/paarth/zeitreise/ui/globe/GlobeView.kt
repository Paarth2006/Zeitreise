package com.paarth.zeitreise.ui.globe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.countryAt
import com.paarth.zeitreise.globe.GlobeProjection

/** Width of a country border, in pixels. */
private const val BORDER_WIDTH = 0.9f

/** Width of the border of the selected country, in pixels. */
private const val SELECTED_BORDER_WIDTH = 1.8f

/** Width of a graticule line, in pixels. */
private const val GRATICULE_WIDTH = 0.6f

/** How far towards the upper left the ocean's lit point sits, as a share of the radius. */
private const val OCEAN_LIGHT_OFFSET = 0.35f

/** How far the ocean gradient reaches, as a share of the radius. */
private const val OCEAN_GRADIENT_SPREAD = 1.45f

/** How far past the limb the outermost atmosphere ring sits, as a share of the radius. */
private const val ATMOSPHERE_SPREAD = 0.1f

/** Width of one atmosphere ring, in pixels. */
private const val ATMOSPHERE_RING_WIDTH = 6f

private const val NANOS_PER_SECOND = 1_000_000_000.0

/**
 * The interactive globe.
 *
 * Drag to turn it, pinch or scroll to zoom, tap a country to select it; left alone it drifts
 * slowly. Every outline is projected into a buffer allocated when the globe loads, so a frame
 * allocates nothing and the draw loop runs no trigonometry.
 *
 * @param countries every country to draw, largest first.
 * @param selected the country drawn highlighted, if any.
 * @param onSelect called with the country that was tapped, or `null` when the ocean was tapped.
 * @param modifier applied to the root element.
 */
@Composable
internal fun GlobeView(
    countries: List<Country>,
    selected: Country?,
    onSelect: (Country?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember { GlobeInteractionState() }
    val geometry = remember(countries) { GlobeGeometryCache(countries) }
    val palette = rememberGlobePalette()

    // Read inside the frame loop rather than captured, so selecting a country stops the drift
    // without restarting the loop.
    val selectedNow by rememberUpdatedState(selected)
    LaunchedEffect(state) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    state.advance(
                        seconds = ((now - previous) / NANOS_PER_SECOND).toFloat(),
                        idle = selectedNow == null,
                    )
                }
                previous = now
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Layout runs before draw, so the viewport is known by the first frame, and the draw
            // loop never writes Compose state - which would invalidate itself on every frame.
            .onSizeChanged { state.viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .globeGestures(
                onPressed = state::onPressed,
                onDragged = state::onDragged,
                onZoomed = state::onZoomed,
                onScrolled = state::onScrolled,
                onReleased = state::onReleased,
                onTapped = { position -> onSelect(state.countryUnder(countries, position)) },
            ),
    ) {
        val projection = state.projection()
        if (projection.scaledRadius <= 0.0) return@Canvas
        drawOcean(projection, palette)
        drawGraticule(geometry.graticule, projection, palette)
        drawCountries(geometry, projection, selected, palette)
        drawAtmosphere(projection, palette)
    }
}

/** The country under [position], or `null` for the ocean and for taps outside the globe. */
private fun GlobeInteractionState.countryUnder(countries: List<Country>, position: Offset): Country? {
    val point = projection().unproject(position.x.toDouble(), position.y.toDouble()) ?: return null
    return countries.countryAt(point)
}

private fun DrawScope.drawOcean(projection: GlobeProjection, palette: GlobePalette) {
    val center = projection.screenCenter()
    val radius = projection.scaledRadius.toFloat()
    drawCircle(
        brush = Brush.radialGradient(
            colors = palette.ocean,
            center = Offset(
                x = center.x - radius * OCEAN_LIGHT_OFFSET,
                y = center.y - radius * OCEAN_LIGHT_OFFSET,
            ),
            radius = radius * OCEAN_GRADIENT_SPREAD,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawGraticule(graticule: GraticuleGeometry, projection: GlobeProjection, palette: GlobePalette) {
    graticule.update(projection)
    drawPath(path = graticule.path, color = palette.graticule, style = Stroke(width = GRATICULE_WIDTH))
}

private fun DrawScope.drawCountries(
    geometry: GlobeGeometryCache,
    projection: GlobeProjection,
    selected: Country?,
    palette: GlobePalette,
) {
    geometry.countries.forEach { country ->
        val isSelected = country.country === selected
        val fill = if (isSelected) palette.landSelected else palette.land
        val border = if (isSelected) palette.borderSelected else palette.border
        val borderWidth = if (isSelected) SELECTED_BORDER_WIDTH else BORDER_WIDTH
        country.update(projection).forEach { path ->
            drawPath(path = path, color = fill)
            drawPath(path = path, color = border, style = Stroke(width = borderWidth))
        }
    }
}

/**
 * Draws the halo outside the limb as a stack of fading rings.
 *
 * Concentric strokes rather than one gradient brush, because a brush with explicit colour stops
 * would have to be rebuilt for every frame's radius.
 */
private fun DrawScope.drawAtmosphere(projection: GlobeProjection, palette: GlobePalette) {
    val center = projection.screenCenter()
    val radius = projection.scaledRadius.toFloat()
    val step = radius * ATMOSPHERE_SPREAD / palette.atmosphere.size
    palette.atmosphere.forEachIndexed { ring, color ->
        drawCircle(
            color = color,
            radius = radius + step * ring,
            center = center,
            style = Stroke(width = ATMOSPHERE_RING_WIDTH),
        )
    }
}

private fun GlobeProjection.screenCenter(): Offset = Offset(centerX.toFloat(), centerY.toFloat())
