package com.paarth.zeitreise.ui.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.GuideNotice
import com.paarth.zeitreise.domain.PlacesToVisit
import com.paarth.zeitreise.domain.StorySource
import com.paarth.zeitreise.ui.PreviewSamples
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.globe.PIN_COLOR
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.explore_button
import zeitreise.shared.generated.resources.explore_failed
import zeitreise.shared.generated.resources.explore_found
import zeitreise.shared.generated.resources.explore_loading
import zeitreise.shared.generated.resources.explore_none
import zeitreise.shared.generated.resources.explore_source_ai
import zeitreise.shared.generated.resources.explore_source_curated
import zeitreise.shared.generated.resources.story_ai_unavailable
import zeitreise.shared.generated.resources.story_rate_limited
import zeitreise.shared.generated.resources.story_retry

/** Space between the lines of the panel, and between a glyph and its label. */
private val PANEL_SPACING = 8.dp

/** Size of the pin glyph on the Explore button, and of the loading indicator. */
private val GLYPH_SIZE = 18.dp
private val LOADING_INDICATOR_STROKE = 2.dp

/**
 * The part of the country sheet that finds places to visit: an Explore button, then what the
 * guide is doing, then how many places it pinned and who chose them.
 *
 * @param state what the guide has found for this country; [ExploreState.Idle] for none yet.
 * @param onExplore called when the user asks for places, or to try again after a failure.
 * @param modifier applied to the root element.
 */
@Composable
internal fun ExplorePanel(state: ExploreState, onExplore: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        modifier = modifier.fillMaxWidth(),
    ) { current ->
        when (current) {
            ExploreState.Idle -> ExploreButton(onClick = onExplore)
            is ExploreState.Loading -> LoadingLine()
            is ExploreState.Ready -> FoundPlaces(current.places)
            is ExploreState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(PANEL_SPACING)) {
                SecondaryText(stringResource(Res.string.explore_failed))
                GlassButton(onClick = onExplore) {
                    Text(stringResource(Res.string.story_retry), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ExploreButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        GlyphRow {
            Text(stringResource(Res.string.explore_button), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FoundPlaces(places: PlacesToVisit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PANEL_SPACING / 2),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (places.landmarks.isEmpty()) {
            SecondaryText(stringResource(Res.string.explore_none))
        } else {
            val count = places.landmarks.size
            GlyphRow {
                Text(
                    text = pluralStringResource(Res.plurals.explore_found, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val credit = when (places.source) {
                StorySource.Ai -> Res.string.explore_source_ai
                StorySource.Curated, StorySource.Wikipedia -> Res.string.explore_source_curated
            }
            SecondaryText(stringResource(credit))
        }
        places.notice?.let { notice ->
            SecondaryText(
                stringResource(
                    when (notice) {
                        GuideNotice.RateLimited -> Res.string.story_rate_limited
                        GuideNotice.Unavailable -> Res.string.story_ai_unavailable
                    },
                ),
            )
        }
    }
}

@Composable
private fun LoadingLine(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PANEL_SPACING),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator(strokeWidth = LOADING_INDICATOR_STROKE, modifier = Modifier.size(GLYPH_SIZE))
        SecondaryText(stringResource(Res.string.explore_loading))
    }
}

/** A pin glyph followed by [content], like a label in Apple's apps. */
@Composable
private fun GlyphRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PANEL_SPACING),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        PinGlyph()
        content()
    }
}

/** A small map pin, like the ones the places drop onto the globe as. */
@Composable
private fun PinGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(GLYPH_SIZE)) {
        val radius = size.width * 0.32f
        val head = Offset(size.width / 2, radius + 1f)
        val point = Path().apply {
            moveTo(head.x - radius * 0.7f, head.y + radius * 0.5f)
            lineTo(size.width / 2, size.height)
            lineTo(head.x + radius * 0.7f, head.y + radius * 0.5f)
            close()
        }
        drawPath(point, PIN_COLOR)
        drawCircle(PIN_COLOR, radius = radius, center = head)
        drawCircle(Color.White, radius = radius * 0.4f, center = head)
    }
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

@Preview
@Composable
private fun ExplorePanelPreview() {
    ZeitreiseTheme {
        Column(verticalArrangement = Arrangement.spacedBy(PANEL_SPACING)) {
            ExplorePanel(state = ExploreState.Idle, onExplore = {})
            ExplorePanel(state = ExploreState.Loading(PreviewSamples.india), onExplore = {})
            ExplorePanel(
                state = ExploreState.Ready(
                    PreviewSamples.india,
                    PlacesToVisit(landmarks = emptyList(), source = StorySource.Curated),
                ),
                onExplore = {},
            )
        }
    }
}
