package com.paarth.zeitreise.ui.glass

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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA

/** Widest a sheet grows; past this it floats as a card so lines stay a comfortable length. */
internal val SHEET_MAX_WIDTH = 480.dp

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

/** Extra trailing padding for a sheet's body, which has no close button to make room for. */
internal val SHEET_BODY_END_PADDING = SHEET_PADDING

/** Spacing between a sheet's title and its subtitle, and twice this between its sections. */
private val SHEET_SPACING = 4.dp

/** Size of the cross drawn inside the close button, and its stroke width. */
private val CLOSE_GLYPH = 12.dp
private val CLOSE_STROKE = 2.dp

/** Damping of the sheet's spring: just under critical, so it lands softly. */
private const val SHEET_DAMPING = 0.86f

/** A gently damped spring for the sheet, which settles without bouncing, as iOS sheets do. */
private val SHEET_SPRING = spring(
    dampingRatio = SHEET_DAMPING,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/** Largest share of the window's height a sheet takes; longer content scrolls inside it. */
private const val MAX_HEIGHT_SHARE = 0.55f

/** Lines a sheet's title may wrap onto before it is ellipsised. */
private const val TITLE_MAX_LINES = 2

/**
 * A sheet of Liquid Glass over the globe, holding [content] in a scrolling column.
 *
 * Springs up when [visible] and away again when not. In a narrow window it sits just inside the
 * screen's bottom and side edges, as iOS insets its glass sheets so the globe shows all round; in
 * a wide one, on the web or a tablet, it floats as a card of readable width.
 *
 * @param visible whether the sheet is showing.
 * @param modifier applied to the root element, which fills the space the sheet rises in.
 */
@Composable
internal fun GlassSheet(visible: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        val floating = maxWidth >= SHEET_MAX_WIDTH + FLOATING_MARGIN * 2
        val contentMaxHeight = maxHeight * MAX_HEIGHT_SHARE
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(SHEET_SPRING) { height -> height } + fadeIn(),
            exit = slideOutVertically(SHEET_SPRING) { height -> height } + fadeOut(),
        ) {
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(SHEET_SPACING * 2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = contentMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = SHEET_PADDING,
                            top = SHEET_PADDING,
                            end = SHEET_TRAILING_PADDING,
                            bottom = SHEET_PADDING,
                        ),
                    content = content,
                )
            }
        }
    }
}

/**
 * The top of a sheet: its title and subtitle, and a round glass button that closes it.
 *
 * @param closeLabel what a screen reader says for the close button.
 */
@Composable
internal fun SheetHeader(
    title: String,
    subtitle: String,
    closeLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.Top, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(SHEET_SPACING), modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
            )
        }
        GlassCircleButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = closeLabel }) {
            // A plain cross, drawn rather than taken from an icon set so the app needs no icon dependency.
            val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA)
            Canvas(modifier = Modifier.size(CLOSE_GLYPH)) {
                val stroke = CLOSE_STROKE.toPx()
                drawLine(tint, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
            }
        }
    }
}
