package com.paarth.zeitreise.ui.about

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.about_body
import zeitreise.shared.generated.resources.about_close
import zeitreise.shared.generated.resources.about_credit_natural_earth
import zeitreise.shared.generated.resources.about_credits_heading
import zeitreise.shared.generated.resources.about_open
import zeitreise.shared.generated.resources.about_title

/** Size of the Info button: Apple's minimum touch target. */
private val BUTTON_SIZE = 44.dp

/** Size of the drawn "i" inside the button. */
private val GLYPH_SIZE = 18.dp

/** Stroke width of the drawn "i". */
private val GLYPH_STROKE = 2.dp

/** Widest the About card grows. */
private val CARD_MAX_WIDTH = 420.dp

/** Padding inside the About card, the space between its sections, and its corner radius. */
private val CARD_PADDING = 24.dp
private val CARD_SPACING = 12.dp
private val CARD_CORNER = 24.dp

/** Height of the Done button and its corner radius. */
private val BUTTON_HEIGHT = 50.dp
private val BUTTON_CORNER = 14.dp

/** Opacity of the Info button over the globe. */
private const val BUTTON_ALPHA = 0.94f

/** Where the dot and the stem of the "i" sit, as shares of the glyph's height. */
private const val DOT_Y = 0.12f
private const val STEM_TOP = 0.38f
private const val STEM_BOTTOM = 0.92f

/**
 * The round Info button that floats over the globe and opens the About sheet.
 *
 * @param onClick called when the button is tapped.
 * @param modifier applied to the root element.
 */
@Composable
internal fun InfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.about_open)
    val tint = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = BUTTON_ALPHA),
        modifier = modifier.size(BUTTON_SIZE).semantics { contentDescription = label },
    ) {
        Canvas(modifier = Modifier.padding((BUTTON_SIZE - GLYPH_SIZE) / 2).size(GLYPH_SIZE)) {
            drawInfoGlyph(tint)
        }
    }
}

/**
 * What the app is, and the credits for the data it draws, which the data licences ask for.
 *
 * @param onDismiss called when the sheet is closed.
 */
@Composable
internal fun AboutSheet(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(CARD_CORNER),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = CARD_MAX_WIDTH),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
                modifier = Modifier.padding(CARD_PADDING),
            ) {
                Text(
                    text = stringResource(Res.string.about_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = stringResource(Res.string.about_body), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(Res.string.about_credits_heading),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(Res.string.about_credit_natural_earth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
                )
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(BUTTON_CORNER),
                    modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
                ) {
                    Text(text = stringResource(Res.string.about_close), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** An "i": a dot above a stem, drawn so the app needs no icon dependency. */
private fun DrawScope.drawInfoGlyph(tint: Color) {
    val stroke = GLYPH_STROKE.toPx()
    val x = size.width / 2
    drawCircle(tint, radius = stroke, center = Offset(x, size.height * DOT_Y))
    drawLine(tint, Offset(x, size.height * STEM_TOP), Offset(x, size.height * STEM_BOTTOM), stroke, StrokeCap.Round)
}
