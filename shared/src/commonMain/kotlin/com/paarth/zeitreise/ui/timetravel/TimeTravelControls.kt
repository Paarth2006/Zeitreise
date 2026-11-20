package com.paarth.zeitreise.ui.timetravel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.MAP_YEARS
import com.paarth.zeitreise.domain.Realm
import com.paarth.zeitreise.domain.yearDigits
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.glass.GlassCircleButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import com.paarth.zeitreise.ui.glass.GlassSheet
import com.paarth.zeitreise.ui.glass.SHEET_BODY_END_PADDING
import com.paarth.zeitreise.ui.glass.SHEET_MAX_WIDTH
import com.paarth.zeitreise.ui.glass.SheetHeader
import com.paarth.zeitreise.ui.globe.StoryPanel
import com.paarth.zeitreise.ui.globe.StoryState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.close_realm
import zeitreise.shared.generated.resources.realm_in_year
import zeitreise.shared.generated.resources.realm_part_of
import zeitreise.shared.generated.resources.story_retry
import zeitreise.shared.generated.resources.time_travel_failed
import zeitreise.shared.generated.resources.time_travel_loading
import zeitreise.shared.generated.resources.time_travel_open
import zeitreise.shared.generated.resources.time_travel_realms
import zeitreise.shared.generated.resources.time_travel_today
import zeitreise.shared.generated.resources.time_travel_year
import zeitreise.shared.generated.resources.year_ad
import zeitreise.shared.generated.resources.year_bc

/** Gap between the floating panel and the edges of the safe area. */
private val PANEL_MARGIN = 16.dp

/** Padding inside the year panel, and between its rows. */
private val PANEL_PADDING = 20.dp
private val PANEL_SPACING = 6.dp

/** Corner radius of the year panel, matching the sheets. */
private val PANEL_CORNER = 32.dp

/** Size of the hourglass drawn in the Time Travel button, and its stroke width. */
private val HOURGLASS_SIZE = 18.dp
private val HOURGLASS_STROKE = 2.dp

/** Size of the loading indicator. */
private val LOADING_SIZE = 14.dp
private val LOADING_STROKE = 2.dp

/** Opacity of the slider's unfilled track. */
private const val INACTIVE_TRACK_ALPHA = 0.15f

/** Damping of the panel's spring, settling softly as the sheets do. */
private const val PANEL_DAMPING = 0.86f

private val PANEL_SPRING = spring(
    dampingRatio = PANEL_DAMPING,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/**
 * The round glass button that starts Time Travel, showing an hourglass.
 *
 * @param onClick called when the button is tapped.
 * @param modifier applied to the root element.
 */
@Composable
internal fun TimeTravelButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.time_travel_open)
    val tint = MaterialTheme.colorScheme.primary
    GlassCircleButton(onClick = onClick, modifier = modifier.semantics { contentDescription = label }) {
        Canvas(modifier = Modifier.size(HOURGLASS_SIZE)) {
            val stroke = HOURGLASS_STROKE.toPx()
            val (w, h) = size.width to size.height
            drawLine(tint, Offset(0f, 0f), Offset(w, 0f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(0f, h), Offset(w, h), stroke, StrokeCap.Round)
            val glass = Path().apply {
                moveTo(w * 0.15f, 0f)
                lineTo(w * 0.85f, 0f)
                lineTo(w * 0.15f, h)
                lineTo(w * 0.85f, h)
                close()
            }
            drawPath(glass, tint, style = Stroke(width = stroke))
        }
    }
}

/**
 * Everything Time Travel puts over the globe: the year panel with its slider, and, once a realm
 * is tapped, a sheet telling of it.
 *
 * @param state whether Time Travel is on, and if so the year, its map and the selected realm.
 * @param story what the guide says about the selected realm.
 * @param actions what the controls can ask for.
 */
@Composable
internal fun BoxScope.TimeTravelControls(state: TimeTravelState, story: StoryState, actions: TimeTravelActions) {
    // The controls have to keep showing their year while they slide away, after Time Travel ends.
    var lastOn by remember { mutableStateOf<TimeTravelState.On?>(null) }
    LaunchedEffect(state) { if (state is TimeTravelState.On) lastOn = state }
    // Before the first trip there is nothing to show; a hidden placeholder lets the panel spring in.
    val on = state as? TimeTravelState.On ?: lastOn ?: TimeTravelState.On(MAP_YEARS.lastIndex)
    AnimatedVisibility(
        visible = state is TimeTravelState.On && on.selected == null,
        enter = slideInVertically(PANEL_SPRING) { height -> height } + fadeIn(),
        exit = slideOutVertically(PANEL_SPRING) { height -> height } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(PANEL_MARGIN),
    ) {
        YearPanel(state = on, actions = actions)
    }
    val yearText = yearLabel(on.year)
    GlassSheet(
        visible = state is TimeTravelState.On && state.selected != null,
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val realm = on.selected
        if (realm != null) {
            SheetHeader(
                title = realm.name,
                subtitle = realm.partOf?.let { stringResource(Res.string.realm_part_of, it, yearText) }
                    ?: stringResource(Res.string.realm_in_year, yearText),
                closeLabel = stringResource(Res.string.close_realm),
                onClose = { actions.selectRealm(null) },
            )
        }
        StoryPanel(
            story = story,
            onRetry = actions.retryStory,
            onNextFact = actions.nextFact,
            modifier = Modifier.padding(end = SHEET_BODY_END_PADDING),
        )
    }
}

/**
 * What the Time Travel controls can ask for.
 *
 * @property selectYear shows the map for the year at this index of [MAP_YEARS].
 * @property retry tries the year's map again after it failed to download.
 * @property selectRealm selects a realm, or clears the selection when it is `null`.
 * @property retryStory asks the guide again after Wikipedia could not be reached.
 * @property nextFact shows the selected realm's next fact.
 * @property returnToToday ends Time Travel.
 */
internal class TimeTravelActions(
    val selectYear: (Int) -> Unit,
    val retry: () -> Unit,
    val selectRealm: (Realm?) -> Unit,
    val retryStory: () -> Unit,
    val nextFact: () -> Unit,
    val returnToToday: () -> Unit,
)

/** The panel naming the year shown, with a slider through every year there is a map for. */
@Composable
private fun YearPanel(state: TimeTravelState.On, actions: TimeTravelActions, modifier: Modifier = Modifier) {
    GlassPanel(
        shape = RoundedCornerShape(PANEL_CORNER),
        modifier = modifier.widthIn(max = SHEET_MAX_WIDTH).fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PANEL_SPACING),
            modifier = Modifier.padding(PANEL_PADDING),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = yearLabel(state.year),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(onClick = actions.returnToToday, prominent = true) {
                    Text(stringResource(Res.string.time_travel_today), style = MaterialTheme.typography.titleMedium)
                }
            }
            MapStatus(state = state, onRetry = actions.retry)
            YearSlider(yearIndex = state.yearIndex, onSelectYear = actions.selectYear)
            Row {
                SecondaryText(yearLabel(MAP_YEARS.first()), modifier = Modifier.weight(1f))
                SecondaryText(yearLabel(MAP_YEARS.last()))
            }
        }
    }
}

/** How far the year's map has loaded, and how many realms it holds once it has. */
@Composable
private fun MapStatus(state: TimeTravelState.On, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val yearText = yearLabel(state.year)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PANEL_SPACING),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        when (val map = state.map) {
            PastMap.Loading -> {
                CircularProgressIndicator(strokeWidth = LOADING_STROKE, modifier = Modifier.size(LOADING_SIZE))
                SecondaryText(stringResource(Res.string.time_travel_loading, yearText))
            }
            PastMap.Failed -> {
                SecondaryText(stringResource(Res.string.time_travel_failed, yearText), Modifier.weight(1f))
                GlassButton(onClick = onRetry) {
                    Text(stringResource(Res.string.story_retry), style = MaterialTheme.typography.titleMedium)
                }
            }
            is PastMap.Ready -> {
                val count = map.map.realms.size
                SecondaryText(pluralStringResource(Res.plurals.time_travel_realms, count, count))
            }
        }
    }
}

/** A slider that steps through the years there is a map for, close together where history was busy. */
@Composable
private fun YearSlider(yearIndex: Int, onSelectYear: (Int) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.time_travel_year)
    Slider(
        value = yearIndex.toFloat(),
        onValueChange = { onSelectYear(it.toInt()) },
        valueRange = 0f..MAP_YEARS.lastIndex.toFloat(),
        steps = MAP_YEARS.size - 2,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_TRACK_ALPHA),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
    )
}

@Composable
private fun SecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
        modifier = modifier,
    )
}

/** "1492", or "500 BC" for a year before Christ. */
@Composable
internal fun yearLabel(year: Int): String =
    stringResource(if (year < 0) Res.string.year_bc else Res.string.year_ad, yearDigits(year))

@Preview
@Composable
private fun YearPanelPreview() {
    ZeitreiseTheme {
        YearPanel(
            state = TimeTravelState.On(yearIndex = MAP_YEARS.indexOf(1492)),
            actions = TimeTravelActions({}, {}, {}, {}, {}, {}),
        )
    }
}
