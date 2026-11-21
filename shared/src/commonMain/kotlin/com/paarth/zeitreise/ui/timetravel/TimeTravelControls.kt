package com.paarth.zeitreise.ui.timetravel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import zeitreise.shared.generated.resources.time_travel_realms
import zeitreise.shared.generated.resources.time_travel_today
import zeitreise.shared.generated.resources.time_travel_year
import zeitreise.shared.generated.resources.year_ad
import zeitreise.shared.generated.resources.year_bc

/** Margin around the panel. */
private val PANEL_MARGIN = 16.dp

/** Padding and row spacing in the year panel. */
private val PANEL_PADDING = 20.dp
private val PANEL_SPACING = 6.dp

/** Corner radius of the year panel. */
private val PANEL_CORNER = 32.dp

/** Size of the loading indicator. */
private val LOADING_SIZE = 14.dp
private val LOADING_STROKE = 2.dp

/** Slider thumb size and shadow, and track height. */
private val THUMB_SIZE = 28.dp
private val THUMB_ELEVATION = 4.dp
private val TRACK_HEIGHT = 4.dp

/** Opacity of the slider's unfilled track. */
private const val INACTIVE_TRACK_ALPHA = 0.15f

/** Spring damping for the panel. */
private const val PANEL_DAMPING = 0.86f

private val PANEL_SPRING = spring(
    dampingRatio = PANEL_DAMPING,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/**
 * The Time Travel UI: the year panel with the slider, and a sheet for the selected realm.
 *
 * @param state the Time Travel state.
 * @param story the story for the selected realm.
 * @param actions the control actions.
 * @param cardOpen whether a landmark's card is open on top. The year panel hides behind it.
 */
@Composable
internal fun BoxScope.TimeTravelControls(
    state: TimeTravelState,
    story: StoryState,
    actions: TimeTravelActions,
    cardOpen: Boolean = false,
) {
    // Keep showing the last year while the panel slides away.
    var lastOn by remember { mutableStateOf<TimeTravelState.On?>(null) }
    LaunchedEffect(state) { if (state is TimeTravelState.On) lastOn = state }
    // Placeholder before the first use, so the panel can animate in.
    val on = state as? TimeTravelState.On ?: lastOn ?: TimeTravelState.On(MAP_YEARS.lastIndex)
    AnimatedVisibility(
        visible = state is TimeTravelState.On && on.selected == null && !cardOpen,
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
 * Actions for the Time Travel controls.
 *
 * @property selectYear shows the map for this index of [MAP_YEARS].
 * @property retry retries downloading the map.
 * @property selectRealm selects a realm, or clears it if `null`.
 * @property retryStory retries loading the story.
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

/** The panel with the current year and the year slider. */
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

/** The map's loading status, or the number of realms once loaded. */
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

/** A slider through all the years that have a map. */
@Composable
private fun YearSlider(yearIndex: Int, onSelectYear: (Int) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.time_travel_year)
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_TRACK_ALPHA),
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )
    Slider(
        value = yearIndex.toFloat(),
        onValueChange = { onSelectYear(it.toInt()) },
        valueRange = 0f..MAP_YEARS.lastIndex.toFloat(),
        steps = MAP_YEARS.size - 2,
        colors = colors,
        // iOS-style slider: a round white thumb on a thin track.
        thumb = {
            Box(
                modifier = Modifier
                    .size(THUMB_SIZE)
                    .shadow(THUMB_ELEVATION, CircleShape)
                    .background(Color.White, CircleShape),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 0.dp,
                drawStopIndicator = null,
                modifier = Modifier.height(TRACK_HEIGHT),
            )
        },
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

/** Formats a year as "1492" or "500 BC". */
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
