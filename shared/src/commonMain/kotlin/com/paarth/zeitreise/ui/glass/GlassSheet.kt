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

/** Maximum sheet width. Wider screens show it as a floating card. */
internal val SHEET_MAX_WIDTH = 480.dp

/** Margin around a floating sheet. */
private val FLOATING_MARGIN = 16.dp

/** Margin between the sheet and the screen edges on a phone. */
private val INSET_MARGIN = 8.dp

/** Corner radius of the sheet. */
private val SHEET_CORNER = 32.dp

/** Padding inside the sheet. */
private val SHEET_PADDING = 20.dp

/** Smaller padding on the trailing side, since the close button has its own touch area. */
private val SHEET_TRAILING_PADDING = 8.dp

/** Extra trailing padding for the sheet body. */
internal val SHEET_BODY_END_PADDING = SHEET_PADDING

/** Spacing between title and subtitle (double between sections). */
private val SHEET_SPACING = 4.dp

/** Gap between the leading content (like a flag) and the title. */
private val HEADER_LEADING_GAP = 12.dp

/** Size and stroke of the close icon. */
private val CLOSE_GLYPH = 12.dp
private val CLOSE_STROKE = 2.dp

/** Spring damping, just under critical so it doesn't bounce. */
private const val SHEET_DAMPING = 0.86f

/** Spring used to show and hide the sheet. */
private val SHEET_SPRING = spring(
    dampingRatio = SHEET_DAMPING,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/** Maximum share of the screen height. Longer content scrolls. */
private const val MAX_HEIGHT_SHARE = 0.55f

/** Maximum lines for the title. */
private const val TITLE_MAX_LINES = 2

/**
 * A glass sheet over the globe with [content] in a scrolling column.
 *
 * It slides up when [visible]. On phones it sits at the bottom of the screen; on wider screens
 * (web, tablets) it floats as a card.
 *
 * @param visible whether the sheet is shown.
 * @param modifier applied to the root element.
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
 * A sheet header with a title, subtitle and close button.
 *
 * @param closeLabel the screen reader label for the close button.
 * @param leading content shown before the title, like a flag.
 */
@Composable
internal fun SheetHeader(
    title: String,
    subtitle: String,
    closeLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(HEADER_LEADING_GAP),
        modifier = modifier,
    ) {
        leading?.invoke()
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
            // Drawn by hand so the app doesn't need an icon library.
            val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA)
            Canvas(modifier = Modifier.size(CLOSE_GLYPH)) {
                val stroke = CLOSE_STROKE.toPx()
                drawLine(tint, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
            }
        }
    }
}
