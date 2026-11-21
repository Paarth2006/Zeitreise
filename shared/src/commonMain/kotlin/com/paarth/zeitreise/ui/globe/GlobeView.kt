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

/** Offset of the ocean's highlight towards the top left, as a share of the radius. */
private const val OCEAN_LIGHT_OFFSET = 0.35f

/** Size of the ocean gradient, as a share of the radius. */
private const val OCEAN_GRADIENT_SPREAD = 1.45f

/** How far the atmosphere glow extends past the globe, as a share of the radius. */
private const val ATMOSPHERE_SPREAD = 0.1f

/** Width of one atmosphere ring, in pixels. */
private const val ATMOSPHERE_RING_WIDTH = 6f

/** Size of the faintest stars in pixels. Each brighter group is [STAR_SIZE_STEP] bigger. */
private const val STAR_BASE_SIZE = 1.6f

/** Size increase for each brighter group of stars, in pixels. */
private const val STAR_SIZE_STEP = 1.1f

/** Blur of the glow behind state names. */
private const val LABEL_HALO_BLUR = 4f

/** Fade-in time for states, in milliseconds. */
private const val SUBDIVISION_FADE_MILLIS = 600

/** Total time for all pins to drop, in milliseconds. */
private const val PIN_DROP_MILLIS = 1_100

private const val NANOS_PER_SECOND = 1_000_000_000.0

/**
 * The interactive globe.
 *
 * Drag to turn it, pinch or scroll to zoom and tap to select a country. Selecting a country flies
 * the camera to it and shows its states. Outline buffers are allocated once, so drawing a frame
 * doesn't allocate.
 *
 * @param content the countries, the selection and the selected country's states.
 * @param onSelectCountry called with the country that was tapped, or `null` for the ocean.
 * @param onSelectSubdivision called with the tapped state, or `null` if no state was hit.
 * @param modifier applied to the root element.
 * @param pins the pins to show. They drop in one by one when the list changes.
 * @param openPin the pin whose card is open, drawn bigger.
 * @param onSelectPin called with the tapped pin. Pins take priority over the land below them.
 * @param past the historical map to draw in Time Travel.
 * @param onSelectRealm called with the realm of [past] that was tapped, or `null` for the ocean.
 * @param quiz what the quiz highlights, while a quiz is running.
 * @param onTurned called when the user finishes a drag or pinch.
 * @param showcase what the globe acts out for the tour card on screen, if any.
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
    showcase: GlobeShowcase? = null,
) {
    // Facing the home country only matters for the first frame, so later changes don't move the camera.
    val state = remember { GlobeInteractionState(start = content.home?.focus?.center) }
    val geometry = remember(content.countries) { GlobeGeometryCache(content.countries) }
    val subdivisionGeometry = remember(content.subdivisions) { content.subdivisions.map(::TerritoryGeometry) }
    val pastGeometry = remember(past?.map) { past?.map?.let(::PastGeometry) }
    val palette = rememberGlobePalette()
    val starfield = remember { Starfield() }
    val description = stringResource(Res.string.globe_description)
    val labels = rememberSubdivisionLabels(content.subdivisions, palette)
    val subdivisionAlpha by animateSubdivisionAlpha(content.subdivisions)

    val (highlighted, quizShown) = showcase.addedTo(content.selected, quiz)
    val focus = cameraFocus(highlighted, past, quizShown)
    // No idle drift during a quiz, so the globe stays where the user left it.
    CameraDriver(state, focus, drifting = focus == null && quiz == null, showcase = showcase)
    val pinDrop = rememberPinDrop(pins)
    val routeTap = rememberTapRouter(
        TapTargets(
            content = content,
            past = past,
            pins = pins,
            openPin = openPin,
            callbacks = TapCallbacks(onSelectCountry, onSelectSubdivision, onSelectRealm, onSelectPin),
        ),
    )
    // The gesture handler is set up once, so it needs the latest callback through this.
    val currentOnTurned by rememberUpdatedState(onTurned)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            // Set the size here and not while drawing, since writing state while drawing would
            // trigger another redraw every frame.
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
            val highlight = CountryHighlight(highlighted, statesShown = subdivisionAlpha > 0f, quiz = quizShown)
            drawCountries(geometry, projection, highlight, palette)
            drawSubdivisions(subdivisionGeometry, projection, selected, subdivisionAlpha, palette)
            labels.draw(this, projection, subdivisionAlpha, selected)
        }
        drawAtmosphere(projection, palette)
        drawLandmarkPins(pins, projection, openPin, pinDrop.value)
    }
}

/**
 * A historical map to draw instead of today's countries.
 *
 * @property map the map, or `null` while it's downloading.
 * @property selected the selected realm.
 */
internal data class PastGlobe(val map: HistoricalMap?, val selected: Realm?)

/**
 * What the camera should frame: the selected realm, the quiz answer (together with a wrong guess),
 * or the selected country.
 */
private fun cameraFocus(selected: Country?, past: PastGlobe?, quiz: QuizGlobe?): GeoCap? = when {
    past != null -> past.selected?.focus
    quiz != null -> quiz.answer?.focus?.let { answer -> quiz.wrongGuess?.let { answer union it.focus } ?: answer }
    else -> selected?.focus
}

/**
 * The country to highlight as selected and the quiz highlight, with what the tour card adds to the
 * real [selected] country and [quiz].
 */
private fun GlobeShowcase?.addedTo(selected: Country?, quiz: QuizGlobe?): Pair<Country?, QuizGlobe?> {
    val shown = this as? GlobeShowcase.Highlight
    return Pair(
        selected ?: shown?.takeUnless { it.asQuizAnswer }?.country,
        quiz ?: shown?.takeIf { it.asQuizAnswer }?.let { QuizGlobe(answer = it.country) },
    )
}

/** What the globe acts out while a tour card explains a feature. */
internal sealed interface GlobeShowcase {
    /**
     * Flies to [country] and highlights it, as if it was opened.
     *
     * @property asQuizAnswer highlights it as a right quiz answer instead.
     * @property lowered frames it in the lower half of the screen, for a card at the top.
     */
    data class Highlight(val country: Country, val asQuizAnswer: Boolean = false, val lowered: Boolean = false) :
        GlobeShowcase

    /** Spins the globe backwards, for Time Travel. */
    data object Rewind : GlobeShowcase
}

/**
 * What the quiz highlights on the globe.
 *
 * @property answer the correct country (green) once answered, `null` while still guessing.
 * @property wrongGuess the wrongly tapped country (red), if any.
 */
internal data class QuizGlobe(val answer: Country? = null, val wrongGuess: Country? = null)

/**
 * Moves the camera automatically: flies to [focus] when it changes and runs inertia, flights and
 * the idle drift. When nothing moves, the loop waits instead of running every frame, which saves
 * battery. It also acts out the tour's [showcase].
 */
@Composable
private fun CameraDriver(state: GlobeInteractionState, focus: GeoCap?, drifting: Boolean, showcase: GlobeShowcase?) {
    val lowered = (showcase as? GlobeShowcase.Highlight)?.lowered == true
    val rewind = showcase == GlobeShowcase.Rewind
    LaunchedEffect(focus, lowered) {
        if (focus != null) state.flyToFrame(focus, lowered) else state.flyToWholeGlobe()
    }
    // Started after the flight above, which would otherwise cancel the spin.
    LaunchedEffect(rewind) { if (rewind) state.rewind() }

    // Read inside the loop, so changing it doesn't restart the loop.
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

/** Fades the states in when they load and out when they're cleared. */
@Composable
private fun animateSubdivisionAlpha(subdivisions: List<Subdivision>): State<Float> = animateFloatAsState(
    targetValue = if (subdivisions.isEmpty()) 0f else 1f,
    animationSpec = tween(SUBDIVISION_FADE_MILLIS),
)

/** Animates from 0 to 1 each time [pins] changes, to drop the pins in. */
@Composable
private fun rememberPinDrop(pins: List<Pinned>): Animatable<Float, AnimationVector1D> {
    val drop = remember { Animatable(0f) }
    LaunchedEffect(pins) {
        drop.snapTo(0f)
        drop.animateTo(1f, tween(PIN_DROP_MILLIS, easing = LinearEasing))
    }
    return drop
}

/** Measures the state labels once per country. */
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

/** Callbacks for taps on the globe. */
private class TapCallbacks(
    val onSelectCountry: (Country?) -> Unit,
    val onSelectSubdivision: (Subdivision?) -> Unit,
    val onSelectRealm: (Realm?) -> Unit,
    val onSelectPin: (Pinned) -> Unit,
)

/** Everything a tap can select. */
private class TapTargets(
    val content: GlobeUiState.Ready,
    val past: PastGlobe?,
    val pins: List<Pinned>,
    val openPin: Pinned?,
    val callbacks: TapCallbacks,
) {
    /**
     * Decides what a tap at [position] selects. Pins come first. In Time Travel it's a realm,
     * otherwise a state, then the selected country, then any other country. The ocean clears it.
     */
    fun route(position: Offset, projection: GlobeProjection, density: Density) {
        val point = projection.unproject(position.x.toDouble(), position.y.toDouble())
        val pinned = landmarkAt(position, pins, projection, openPin, density)
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

/** The tap handler. It's created once but always uses the latest [targets]. */
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
 * Which countries are highlighted and how.
 *
 * @property selected the selected country, gold until its states have loaded.
 * @property statesShown whether its states are visible.
 * @property quiz the quiz answer (green) and wrong guess (red).
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

/** Draws all countries, with the highlights from [highlight]. */
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

/** Draws the selected country's states with [alpha], and the selected state in gold. */
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
 * Draws the atmosphere glow as fading rings. Rings are used instead of a gradient brush because the
 * brush would have to be rebuilt whenever the radius changes.
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
