package com.paarth.zeitreise.ui.globe

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.ui.PreviewSamples
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassCircleButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.close_country

/** Widest the sheet grows; past this it floats as a card so lines stay a comfortable length. */
private val SHEET_MAX_WIDTH = 480.dp

/** Gap between a floating sheet and the edges of the safe area. */
private val FLOATING_MARGIN = 16.dp

/** Gap between the sheet and the screen's edges on a phone, as iOS insets its glass sheets. */
private val INSET_MARGIN = 8.dp

/** Corner radius of the sheet, large and round like the corners of the device itself. */
private val SHEET_CORNER = 32.dp

/** Padding inside the sheet, in from the leading, top and bottom edges. */
private val SHEET_PADDING = 20.dp

/** Padding on the trailing side, smaller because the close button carries its own touch area. */
private val SHEET_TRAILING_PADDING = 8.dp

/** Spacing between the country name and its continent. */
private val SHEET_SPACING = 4.dp

/** Size of the cross drawn inside the close button. */
private val CLOSE_GLYPH = 12.dp

/** Stroke width of the cross. */
private val CLOSE_STROKE = 2.dp

/** Damping of the sheet's spring: just under critical, so it lands softly. */
private const val SHEET_DAMPING = 0.86f

/** A gently damped spring for the sheet, which settles without bouncing, as iOS sheets do. */
private val SHEET_SPRING = spring(
    dampingRatio = SHEET_DAMPING,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/** Largest share of the window's height the sheet takes; longer stories scroll inside it. */
private const val MAX_HEIGHT_SHARE = 0.55f

/** Lines the country name may wrap onto before it is ellipsised. */
private const val NAME_MAX_LINES = 2

/**
 * The panel naming the selected country.
 *
 * A pane of Liquid Glass that springs up when a country is selected and away again when the
 * selection is cleared. In a narrow window it sits just inside the screen's bottom and side edges;
 * in a wide one, on the web or a tablet, it floats as a card of readable width.
 *
 * @param country the selected country, or `null` to hide the sheet.
 * @param subdivision the state tapped inside [country], which the sheet names instead, if any.
 * @param story what the guide says about the place the sheet names.
 * @param onClose called when the close button is tapped.
 * @param onRetryStory called when the user asks the guide to try again.
 * @param onNextFact called when the user asks for another fact about the same place.
 * @param modifier applied to the root element.
 */
@Composable
internal fun CountrySheet(
    country: Country?,
    subdivision: Subdivision?,
    story: StoryState,
    onClose: () -> Unit,
    onRetryStory: () -> Unit,
    onNextFact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The sheet has to keep naming a country while it slides away, after the selection is gone.
    var lastNamed by remember { mutableStateOf<Country?>(null) }
    LaunchedEffect(country) { if (country != null) lastNamed = country }
    val shown = country ?: lastNamed

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        val floating = maxWidth >= SHEET_MAX_WIDTH + FLOATING_MARGIN * 2
        val storyMaxHeight = maxHeight * MAX_HEIGHT_SHARE
        AnimatedVisibility(
            visible = country != null,
            enter = slideInVertically(SHEET_SPRING) { height -> height } + fadeIn(),
            exit = slideOutVertically(SHEET_SPRING) { height -> height } + fadeOut(),
        ) {
            // Like iOS sheets in Liquid Glass, the sheet floats inset from the screen's edges
            // instead of reaching them, so the globe shows all round it.
            GlassPanel(
                shape = RoundedCornerShape(SHEET_CORNER),
                modifier = Modifier
                    .windowInsetsPadding(
                        if (floating) {
                            WindowInsets.safeDrawing
                        } else {
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        },
                    )
                    .padding(if (floating) FLOATING_MARGIN else INSET_MARGIN)
                    .widthIn(max = SHEET_MAX_WIDTH)
                    .fillMaxWidth(),
            ) {
                SheetContent(
                    story = story,
                    onRetryStory = onRetryStory,
                    onNextFact = onNextFact,
                    maxHeight = storyMaxHeight,
                    title = subdivision?.name ?: shown?.name.orEmpty(),
                    subtitle = when {
                        subdivision != null -> subdivision.subtitleIn(shown)
                        else -> shown?.continent.orEmpty()
                    },
                    onClose = onClose,
                )
            }
        }
    }
}

@Composable
private fun SheetContent(
    title: String,
    subtitle: String,
    story: StoryState,
    maxHeight: Dp,
    onClose: () -> Unit,
    onRetryStory: () -> Unit,
    onNextFact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SHEET_SPACING * 2),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(start = SHEET_PADDING, top = SHEET_PADDING, end = SHEET_TRAILING_PADDING, bottom = SHEET_PADDING),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                verticalArrangement = Arrangement.spacedBy(SHEET_SPACING),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = NAME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
                )
            }
            CloseButton(onClick = onClose)
        }
        StoryPanel(
            story = story,
            onRetry = onRetryStory,
            onNextFact = onNextFact,
            modifier = Modifier.padding(end = SHEET_PADDING),
        )
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.close_country)
    GlassCircleButton(onClick = onClick, modifier = modifier.semantics { contentDescription = label }) {
        CloseGlyph(MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA))
    }
}

/** A plain cross, drawn rather than taken from an icon set so the app needs no icon dependency. */
@Composable
private fun CloseGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(CLOSE_GLYPH)) {
        val stroke = CLOSE_STROKE.toPx()
        drawLine(tint, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
    }
}

/** "State · India", or just the country's name where the state's kind is unknown. */
private fun Subdivision.subtitleIn(country: Country?): String {
    val countryName = country?.name.orEmpty()
    return if (kind.isBlank()) countryName else "$kind · $countryName"
}

@Preview
@Composable
private fun CountrySheetPreview() {
    ZeitreiseTheme {
        CountrySheet(
            country = PreviewSamples.india,
            subdivision = null,
            story = StoryState.Loading,
            onClose = {},
            onRetryStory = {},
            onNextFact = {},
        )
    }
}

@Preview
@Composable
private fun StateSheetPreview() {
    ZeitreiseTheme {
        CountrySheet(
            country = PreviewSamples.india,
            subdivision = PreviewSamples.kerala,
            story = StoryState.NoArticle,
            onClose = {},
            onRetryStory = {},
            onNextFact = {},
        )
    }
}
