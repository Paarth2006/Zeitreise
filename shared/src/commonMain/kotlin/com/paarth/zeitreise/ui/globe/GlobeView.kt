package com.paarth.zeitreise.ui.globe

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.domain.territoryAt
import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.globe.GlobeProjection
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.globe_description

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

/** Diameter of the faintest stars, in pixels; each brighter group is [STAR_SIZE_STEP] larger. */
private const val STAR_BASE_SIZE = 1.6f

/** How much larger each brighter group of stars is drawn, in pixels. */
private const val STAR_SIZE_STEP = 1.1f

/** Blur of the light halo behind state names, which keeps them legible over any fill. */
private const val LABEL_HALO_BLUR = 4f

/** Milliseconds the states take to fade in once they have loaded. */
private const val SUBDIVISION_FADE_MILLIS = 600

private const val NANOS_PER_SECOND = 1_000_000_000.0

/**
 * The interactive globe.
 *
 * Drag to turn it, pinch or scroll to zoom, tap a country to select it; left alone it drifts
 * slowly. Selecting a country flies the camera in to frame it and fades in its states and their
 * names, which can then be tapped in turn; clearing the selection flies back out. Every outline is
 * projected into a buffer allocated when it loads, so a frame allocates nothing for the outlines
 * and the draw loop runs no trigonometry.
 *
 * @param content the countries, the selection and the selected country's states.
 * @param onSelectCountry called with the country that was tapped, or `null` for the ocean.
 * @param onSelectSubdivision called with the state that was tapped inside the selected country,
 *   or `null` when the selected country was tapped outside any of its states.
 * @param modifier applied to the root element.
 * @param onTurned called when the user finishes a drag or pinch.
 */
@Composable
internal fun GlobeView(
    content: GlobeUiState.Ready,
    onSelectCountry: (Country?) -> Unit,
    onSelectSubdivision: (Subdivision?) -> Unit,
    modifier: Modifier = Modifier,
    onTurned: () -> Unit = {},
) {
    val selected = content.selected
    val state = remember { GlobeInteractionState() }
    val geometry = remember(content.countries) { GlobeGeometryCache(content.countries) }
    val subdivisionGeometry = remember(content.subdivisions) { content.subdivisions.map(::TerritoryGeometry) }
    val palette = rememberGlobePalette()
    val starfield = remember { Starfield() }
    val description = stringResource(Res.string.globe_description)
    val labels = rememberSubdivisionLabels(content.subdivisions, palette)
    val subdivisionAlpha by animateFloatAsState(
        targetValue = if (content.subdivisions.isEmpty()) 0f else 1f,
        animationSpec = tween(SUBDIVISION_FADE_MILLIS),
    )

    CameraDriver(state, selected)

    // The gesture handler is installed once, so it reads the latest state and callbacks through
    // these rather than the ones it was created with.
    val currentContent by rememberUpdatedState(content)
    val currentOnSelectCountry by rememberUpdatedState(onSelectCountry)
    val currentOnSelectSubdivision by rememberUpdatedState(onSelectSubdivision)
    val currentOnTurned by rememberUpdatedState(onTurned)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            // Layout runs before draw, so the viewport is known by the first frame, and the draw
            // loop never writes Compose state - which would invalidate itself on every frame.
            .onSizeChanged { state.viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .globeGestures(
                onPressed = state::onPressed,
                onDragged = state::onDragged,
                onZoomed = state::onZoomed,
                onScrolled = state::onScrolled,
                onReleased = { velocity ->
                    state.onReleased(velocity)
                    currentOnTurned()
                },
                onTapped = { position ->
                    val point = state.projection().unproject(position.x.toDouble(), position.y.toDouble())
                    currentContent.routeTap(point, currentOnSelectCountry, currentOnSelectSubdivision)
                },
            ),
    ) {
        drawStars(starfield, palette)
        val projection = state.projection()
        if (projection.scaledRadius <= 0.0) return@Canvas
        drawOcean(projection, palette)
        drawGraticule(geometry.graticule, projection, palette)
        drawCountries(geometry, projection, selected, statesShown = subdivisionAlpha > 0f, palette = palette)
        drawSubdivisions(subdivisionGeometry, projection, content.selectedSubdivision, subdivisionAlpha, palette)
        labels.draw(this, projection, subdivisionAlpha, content.selectedSubdivision)
        drawAtmosphere(projection, palette)
    }
}

/**
 * Moves the camera on its own: flies to [selected] whenever it changes, and advances inertia,
 * drift and flights once per frame.
 */
@Composable
private fun CameraDriver(state: GlobeInteractionState, selected: Country?) {
    LaunchedEffect(selected) {
        if (selected != null) state.flyToFrame(selected.focus) else state.flyToWholeGlobe()
    }

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
}

/** Measures the selected country's state names once per country, in the palette's label style. */
@Composable
private fun rememberSubdivisionLabels(subdivisions: List<Subdivision>, palette: GlobePalette): SubdivisionLabels {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodySmall.copy(
        color = palette.label,
        fontWeight = FontWeight.Medium,
        shadow = Shadow(color = palette.labelHalo, blurRadius = LABEL_HALO_BLUR),
    )
    return remember(subdivisions, style) { SubdivisionLabels(subdivisions, measurer, style) }
}

/**
 * Decides what a tap at [point] selects: a state of the selected country first, then the selected
 * country itself, which steps back from its state, then any other country, or the ocean, which
 * clears the selection.
 */
private fun GlobeUiState.Ready.routeTap(
    point: GeoPoint?,
    onSelectCountry: (Country?) -> Unit,
    onSelectSubdivision: (Subdivision?) -> Unit,
) {
    val subdivision = point?.let { subdivisions.territoryAt(it) }
    val country = point?.let { countries.territoryAt(it) }
    when {
        subdivision != null -> onSelectSubdivision(subdivision)
        country != null && country == selected -> onSelectSubdivision(null)
        else -> onSelectCountry(country)
    }
}

private fun DrawScope.drawStars(starfield: Starfield, palette: GlobePalette) {
    starfield.pointsFor(size).forEachIndexed { group, points ->
        drawPoints(
            points = points,
            pointMode = PointMode.Points,
            color = palette.stars[group],
            strokeWidth = STAR_BASE_SIZE + STAR_SIZE_STEP * group,
            cap = StrokeCap.Round,
        )
    }
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

/**
 * Draws every country. The selected one is filled gold until its states have loaded; once they
 * fade in over it, it keeps its ordinary shade so the states read against it.
 */
private fun DrawScope.drawCountries(
    geometry: GlobeGeometryCache,
    projection: GlobeProjection,
    selected: Country?,
    statesShown: Boolean,
    palette: GlobePalette,
) {
    geometry.countries.forEach { country ->
        val isSelected = country.territory === selected
        val fill = when {
            isSelected && !statesShown -> palette.landSelected
            country.isIce -> palette.ice
            else -> palette.land[country.shade.mod(palette.land.size)]
        }
        val border = if (isSelected) palette.borderSelected else palette.border
        val borderWidth = if (isSelected) SELECTED_BORDER_WIDTH else BORDER_WIDTH
        country.update(projection).forEach { path ->
            drawPath(path = path, color = fill)
            drawPath(path = path, color = border, style = Stroke(width = borderWidth))
        }
    }
}

/** Draws the selected country's states over it, faded in by [alpha], with the tapped one in gold. */
private fun DrawScope.drawSubdivisions(
    subdivisions: List<TerritoryGeometry>,
    projection: GlobeProjection,
    selected: Subdivision?,
    alpha: Float,
    palette: GlobePalette,
) {
    if (alpha <= 0f) return
    subdivisions.forEach { subdivision ->
        val fill = if (subdivision.territory === selected) {
            palette.landSelected
        } else {
            palette.subdivisionLand[subdivision.shade.mod(palette.subdivisionLand.size)]
        }
        subdivision.update(projection).forEach { path ->
            drawPath(path = path, color = fill, alpha = alpha)
            drawPath(path = path, color = palette.border, alpha = alpha, style = Stroke(width = BORDER_WIDTH))
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
