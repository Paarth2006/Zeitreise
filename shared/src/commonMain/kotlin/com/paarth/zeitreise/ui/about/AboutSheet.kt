package com.paarth.zeitreise.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.glass.GlassCircleButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.about_body
import zeitreise.shared.generated.resources.about_close
import zeitreise.shared.generated.resources.about_credit_ai
import zeitreise.shared.generated.resources.about_credit_natural_earth
import zeitreise.shared.generated.resources.about_credit_wikipedia
import zeitreise.shared.generated.resources.about_credits_heading
import zeitreise.shared.generated.resources.about_open
import zeitreise.shared.generated.resources.about_title

/** Size of the drawn "i" inside the button. */
private val GLYPH_SIZE = 18.dp

/** Stroke width of the drawn "i". */
private val GLYPH_STROKE = 2.dp

/** Widest the About card grows. */
private val CARD_MAX_WIDTH = 420.dp

/** Padding inside the About card, the space between its sections, and its corner radius. */
private val CARD_PADDING = 24.dp
private val CARD_SPACING = 12.dp
private val CARD_CORNER = 32.dp

/** How dark the globe dims behind the card. */
private const val SCRIM_ALPHA = 0.35f

/** Scale the card springs up from. */
private const val CARD_START_SCALE = 0.88f

/** A soft spring that lets the card overshoot its size a touch, as iOS alerts do. */
private val CARD_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

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
    GlassCircleButton(onClick = onClick, modifier = modifier.semantics { contentDescription = label }) {
        Canvas(modifier = Modifier.size(GLYPH_SIZE)) { drawInfoGlyph(tint) }
    }
}

/**
 * What the app is, and the credits for the data it draws, which the data licences ask for.
 *
 * A glass card over the dimmed globe and its controls rather than a separate window, so the
 * glass has the whole screen to refract. It springs in from slightly smaller, as iOS presents
 * its alerts.
 *
 * @param visible whether the sheet is showing.
 * @param onDismiss called when the sheet is closed, by its button or a tap outside it.
 * @param modifier applied to the root element.
 */
@Composable
internal fun AboutSheet(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
                .safeDrawingPadding()
                .padding(CARD_PADDING),
        ) {
            GlassPanel(
                shape = RoundedCornerShape(CARD_CORNER),
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(CARD_SPRING, initialScale = CARD_START_SCALE),
                        exit = scaleOut(CARD_SPRING, targetScale = CARD_START_SCALE),
                    )
                    .widthIn(max = CARD_MAX_WIDTH)
                    // Taps on the card itself must not fall through to the scrim and close it.
                    .clickable(interactionSource = null, indication = null, onClick = {}),
            ) {
                AboutContent(onDismiss)
            }
        }
    }
}

@Composable
private fun AboutContent(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
        modifier = modifier.padding(CARD_PADDING),
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
        listOf(
            Res.string.about_credit_wikipedia,
            Res.string.about_credit_natural_earth,
            Res.string.about_credit_ai,
        ).forEach { credit ->
            Text(
                text = stringResource(credit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
            )
        }
        GlassButton(onClick = onDismiss, prominent = true, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(Res.string.about_close), style = MaterialTheme.typography.titleMedium)
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

@Preview
@Composable
private fun InfoButtonPreview() {
    ZeitreiseTheme {
        InfoButton(onClick = {})
    }
}

@Preview
@Composable
private fun AboutSheetPreview() {
    ZeitreiseTheme {
        AboutSheet(visible = true, onDismiss = {})
    }
}
