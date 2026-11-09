package com.paarth.zeitreise.ui.globe

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.close_country

/** Widest the sheet grows; past this it floats as a card so lines stay a comfortable length. */
private val SHEET_MAX_WIDTH = 480.dp

/** Gap between a floating sheet and the edges of the safe area. */
private val FLOATING_MARGIN = 16.dp

/** Corner radius of the sheet. */
private val SHEET_CORNER = 24.dp

/** Padding inside the sheet, in from the leading, top and bottom edges. */
private val SHEET_PADDING = 20.dp

/** Padding on the trailing side, smaller because the close button carries its own touch area. */
private val SHEET_TRAILING_PADDING = 8.dp

/** Spacing between the country name and its continent. */
private val SHEET_SPACING = 4.dp

/** Minimum touch target the Human Interface Guidelines ask for. */
private val TOUCH_TARGET = 44.dp

/** Size of the cross drawn inside the close button. */
private val CLOSE_GLYPH = 12.dp

/** Stroke width of the cross. */
private val CLOSE_STROKE = 2.dp

/** Opacity of the sheet over the globe. */
private const val SHEET_ALPHA = 0.94f

/** Lines the country name may wrap onto before it is ellipsised. */
private const val NAME_MAX_LINES = 2

/**
 * The panel naming the selected country.
 *
 * Slides up when a country is selected and away again when the selection is cleared. In a narrow
 * window it is a bottom sheet that reaches the bottom edge, padding its content clear of the home
 * indicator; in a wide one, on the web or a tablet, it floats as a card of readable width.
 *
 * @param country the selected country, or `null` to hide the sheet.
 * @param onClose called when the close button is tapped.
 * @param modifier applied to the root element.
 */
@Composable
internal fun CountrySheet(country: Country?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    // The sheet has to keep naming a country while it slides away, after the selection is gone.
    var lastNamed by remember { mutableStateOf<Country?>(null) }
    LaunchedEffect(country) { if (country != null) lastNamed = country }
    val shown = country ?: lastNamed

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        val floating = maxWidth >= SHEET_MAX_WIDTH + FLOATING_MARGIN * 2
        AnimatedVisibility(
            visible = country != null,
            enter = slideInVertically { height -> height },
            exit = slideOutVertically { height -> height },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = SHEET_ALPHA),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = if (floating) {
                    RoundedCornerShape(SHEET_CORNER)
                } else {
                    RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER)
                },
                modifier = if (floating) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(FLOATING_MARGIN)
                        .widthIn(max = SHEET_MAX_WIDTH)
                        .fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                },
            ) {
                SheetContent(
                    name = shown?.name.orEmpty(),
                    continent = shown?.continent.orEmpty(),
                    onClose = onClose,
                    modifier = if (floating) {
                        Modifier
                    } else {
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetContent(name: String, continent: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SHEET_PADDING, top = SHEET_PADDING, end = SHEET_TRAILING_PADDING, bottom = SHEET_PADDING),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SHEET_SPACING),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = continent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
            )
        }
        CloseButton(onClick = onClose)
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.close_country)
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA)
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(TOUCH_TARGET)
            .semantics { contentDescription = label },
    ) {
        CloseGlyph(tint)
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
