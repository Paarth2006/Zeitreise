package com.paarth.zeitreise.ui.globe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.domain.Pinned
import com.paarth.zeitreise.domain.Realm
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.domain.territoryAt
import com.paarth.zeitreise.globe.GeoCap
import com.paarth.zeitreise.globe.GlobeProjection
import com.paarth.zeitreise.globe.union
import kotlinx.coroutines.flow.first
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

/** Milliseconds the pins take to drop onto the globe, one after another. */
private const val PIN_DROP_MILLIS = 1_100

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
 * @param pins the places and landmarks pinned on the globe, which drop in one by one when they change.
 * @param openPin the pin whose card is open, drawn larger.
 * @param onSelectPin called with the pin that was tapped; a pin wins over the land under it.
 * @param past the historical map to draw instead of today's countries, while travelling in time.
 * @param onSelectRealm called with the realm of [past] that was tapped, or `null` for the ocean.
 * @param quiz the countries the quiz marks, while a quiz is on; the globe then holds still between
 *   questions and flies to each answer instead of to the selection.
 * @param onTurned called when the user finishes a drag or pinch.
 */
@Composable
internal fun GlobeView(
    content: GlobeUiState.Ready,
    onSelectCountry: (Country?) -> Unit,
    onSelectSubdivision: (Subdivision?) -> Unit,
    modifier: Modifier = Modifier,
    pins: List<Pinned> = emptyList(),
    openPin: Pinned? = null,
    onSelectPin: (Pinned) -> Unit = {},
    past: PastGlobe? = null,
    onSelectRealm: (Realm?) -> Unit = {},
    quiz: QuizGlobe? = null,
    onTurned: () -> Unit = {},
) {
    val state = remember { GlobeInteractionState() }
    val geometry = remember(content.countries) { GlobeGeometryCache(content.countries) }
    val subdivisionGeometry = remember(content.subdivisions) { content.subdivisions.map(::TerritoryGeometry) }
    val pastGeometry = remember(past?.map) { past?.map?.let(::PastGeometry) }
    val palette = rememberGlobePalette()
    val starfield = remember { Starfield() }
    val description = stringResource(Res.string.globe_description)
    val labels = rememberSubdivisionLabels(content.subdivisions, palette)
    val subdivisionAlpha by animateSubdivisionAlpha(content.subdivisions)

    val focus = cameraFocus(content, past, quiz)
    // While a quiz asks for a country the globe stays where the user turned it, so it can be searched.
    CameraDriver(state, focus = focus, drifting = focus == null && quiz == null)
    val pinDrop = rememberPinDrop(pins)
    val routeTap = rememberTapRouter(
        TapTargets(
            content = content,
            past = past,
            pins = pins,
            callbacks = TapCallbacks(onSelectCountry, onSelectSubdivision, onSelectRealm, onSelectPin),
        ),
    )
    // The gesture handler is installed once, so it reads the latest callback through this.
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
                onTapped = { position -> routeTap(position, state.projection()) },
            ),
    ) {
        drawStars(starfield, palette)
        val projection = state.projection()
        if (projection.scaledRadius <= 0.0) return@Canvas
        drawOcean(projection, palette)
        drawGraticule(geometry.graticule, projection, palette)
        if (pastGeometry != null) {
            drawPast(pastGeometry, projection, past?.selected, palette)
        } else {
            val selected = content.selectedSubdivision
            val highlight = CountryHighlight(content.selected, statesShown = subdivisionAlpha > 0f, quiz = quiz)
            drawCountries(geometry, projection, highlight, palette)
            drawSubdivisions(subdivisionGeometry, projection, selected, subdivisionAlpha, palette)
            labels.draw(this, projection, subdivisionAlpha, selected)
        }
        drawAtmosphere(projection, palette)
        drawLandmarkPins(pins, projection, openPin, pinDrop.value)
    }
}

/**
 * A historical map for the globe to draw in place of today's countries.
 *
 * @property map the map, or `null` while it downloads, when the globe shows bare land and sea.
 * @property selected the realm the user tapped, which the camera frames.
 */
internal data class PastGlobe(val map: HistoricalMap?, val selected: Realm?)

/**
 * What the camera frames: the realm tapped in the past; the quiz's answer, together with the
 * country tapped instead so the user sees how far off they were; or the selected country.
 */
private fun cameraFocus(content: GlobeUiState.Ready, past: PastGlobe?, quiz: QuizGlobe?): GeoCap? = when {
    past != null -> past.selected?.focus
    quiz != null -> quiz.answer?.focus?.let { answer -> quiz.wrongGuess?.let { answer union it.focus } ?: answer }
    else -> content.selected?.focus
}

/**
 * What the quiz marks on the globe.
 *
 * @property answer the country asked for, once the question is answered: filled green and framed
 *   by the camera; `null` while the user is still looking for it.
 * @property wrongGuess the country the user tapped instead, filled red and framed with [answer].
 */
internal data class QuizGlobe(val answer: Country? = null, val wrongGuess: Country? = null)

/**
 * Moves the camera on its own: flies to [focus] whenever it changes, or out to the whole globe
 * when it is cleared, and advances inertia, flights and, while [drifting], the slow idle turn,
 * once per frame. A still globe asks for no frames at all, so it redraws nothing and costs no
 * battery until a drag, a flight or the drift moves it again.
 */
@Composable
private fun CameraDriver(state: GlobeInteractionState, focus: GeoCap?, drifting: Boolean) {
    LaunchedEffect(focus) {
        if (focus != null) state.flyToFrame(focus) else state.flyToWholeGlobe()
    }

    // Read inside the frame loop rather than captured, so selecting a place stops the drift
    // without restarting the loop.
    val driftingNow by rememberUpdatedState(drifting)
    LaunchedEffect(state) {
        while (true) {
            snapshotFlow { state.isMoving || driftingNow }.first { it }
            var previous = 0L
            while (state.isMoving || driftingNow) {
                withFrameNanos { now ->
                    if (previous != 0L) {
                        state.advance(
                            seconds = ((now - previous) / NANOS_PER_SECOND).toFloat(),
                            idle = driftingNow,
                        )
                    }
                    previous = now
                }
            }
        }
    }
}

/** Fades the states in once they have loaded, and out when they are cleared. */
@Composable
private fun animateSubdivisionAlpha(subdivisions: List<Subdivision>): State<Float> = animateFloatAsState(
    targetValue = if (subdivisions.isEmpty()) 0f else 1f,
    animationSpec = tween(SUBDIVISION_FADE_MILLIS),
)

/** Runs from 0 to 1 each time a new set of [pins] is pinned, dropping their pins in turn. */
@Composable
private fun rememberPinDrop(pins: List<Pinned>): Animatable<Float, AnimationVector1D> {
    val drop = remember { Animatable(0f) }
    LaunchedEffect(pins) {
        drop.snapTo(0f)
        drop.animateTo(1f, tween(PIN_DROP_MILLIS, easing = LinearEasing))
    }
    return drop
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

/** Whom to tell when a tap selects something. */
private class TapCallbacks(
    val onSelectCountry: (Country?) -> Unit,
    val onSelectSubdivision: (Subdivision?) -> Unit,
    val onSelectRealm: (Realm?) -> Unit,
    val onSelectPin: (Pinned) -> Unit,
)

/** Everything a tap on the globe can select. */
private class TapTargets(
    val content: GlobeUiState.Ready,
    val past: PastGlobe?,
    val pins: List<Pinned>,
    val callbacks: TapCallbacks,
) {
    /**
     * Decides what a tap at [position] selects: a pin first, wherever it stands. Then, in the past,
     * a realm or the ocean. Today, a state of the selected country, then the selected country
     * itself, which steps back from its state, then any other country, or the ocean, which clears
     * the selection.
     */
    fun route(position: Offset, projection: GlobeProjection, density: Density) {
        val point = projection.unproject(position.x.toDouble(), position.y.toDouble())
        val pinned = landmarkAt(position, pins, projection, density)
        val realm = past?.map?.realms?.let { realms -> point?.let { realms.territoryAt(it) } }
        val subdivision = point?.let { content.subdivisions.territoryAt(it) }
        val country = point?.let { content.countries.territoryAt(it) }
        when {
            pinned != null -> callbacks.onSelectPin(pinned)
            past != null -> callbacks.onSelectRealm(realm)
            subdivision != null -> callbacks.onSelectSubdivision(subdivision)
            country != null && country == content.selected -> callbacks.onSelectSubdivision(null)
            else -> callbacks.onSelectCountry(country)
        }
    }
}

/**
 * The tap handler for the gesture detector, which is installed once: it routes each tap through
 * the latest [targets] rather than the ones it was created with.
 */
@Composable
private fun rememberTapRouter(targets: TapTargets): (Offset, GlobeProjection) -> Unit {
    val density = LocalDensity.current
    val current by rememberUpdatedState(targets)
    return remember(density) { { position, projection -> current.route(position, projection, density) } }
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
 * Which countries stand out from the rest, and how.
 *
 * @property selected the selected country, filled gold until its states have loaded; once they
 *   fade in over it, it keeps its ordinary shade so the states read against it.
 * @property statesShown whether the selected country's states are showing.
 * @property quiz the quiz's right answer and wrong guess, filled green and red.
 */
private class CountryHighlight(val selected: Country?, val statesShown: Boolean, val quiz: QuizGlobe?) {
    fun isMarked(country: TerritoryGeometry): Boolean = country.territory.let {
        it === selected || it === quiz?.answer || it === quiz?.wrongGuess
    }

    fun fillOf(country: TerritoryGeometry, palette: GlobePalette): Color = when (country.territory) {
        quiz?.answer -> palette.quizRight
        quiz?.wrongGuess -> palette.quizWrong
        selected if !statesShown -> palette.landSelected
        else -> if (country.isIce) palette.ice else palette.land[country.shade.mod(palette.land.size)]
    }
}

/** Draws every country, with the selected one and the quiz's answers picked out by [highlight]. */
private fun DrawScope.drawCountries(
    geometry: GlobeGeometryCache,
    projection: GlobeProjection,
    highlight: CountryHighlight,
    palette: GlobePalette,
) {
    geometry.countries.forEach { country ->
        val marked = highlight.isMarked(country)
        val fill = highlight.fillOf(country, palette)
        val border = if (marked) palette.borderSelected else palette.border
        val borderWidth = if (marked) SELECTED_BORDER_WIDTH else BORDER_WIDTH
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
