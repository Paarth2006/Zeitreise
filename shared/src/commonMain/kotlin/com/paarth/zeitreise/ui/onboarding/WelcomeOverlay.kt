package com.paarth.zeitreise.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.welcome_continue
import zeitreise.shared.generated.resources.welcome_select_body
import zeitreise.shared.generated.resources.welcome_select_title
import zeitreise.shared.generated.resources.welcome_spin_body
import zeitreise.shared.generated.resources.welcome_spin_title
import zeitreise.shared.generated.resources.welcome_title

/** Widest the welcome content grows, so it reads as one column on the web and on tablets. */
private val CONTENT_MAX_WIDTH = 480.dp

/** Padding around the welcome content, in from the safe area. */
private val CONTENT_PADDING = 24.dp

/** Space between the title and the first feature, and between features. */
private val SECTION_SPACING = 32.dp

/** Space between a feature's glyph and its text. */
private val FEATURE_GLYPH_SPACING = 16.dp

/** Size of a feature glyph. */
private val GLYPH_SIZE = 36.dp

/** Stroke width of the feature glyphs. */
private val GLYPH_STROKE = 2.dp

/** Height of the Continue button, the size of a prominent button in Apple's apps. */
private val BUTTON_HEIGHT = 50.dp

/** Corner radius of the Continue button. */
private val BUTTON_CORNER = 14.dp

/** How much of the globe shows through the welcome screen. */
private const val SCRIM_ALPHA = 0.72f

/** Milliseconds each element takes to fade in. */
private const val REVEAL_MILLIS = 500

/** Milliseconds between one element starting to appear and the next. */
private const val REVEAL_STAGGER_MILLIS = 150

/** A feature listed on the welcome screen. */
private enum class WelcomeFeature(val title: StringResource, val body: StringResource) {
    Spin(Res.string.welcome_spin_title, Res.string.welcome_spin_body),
    Select(Res.string.welcome_select_title, Res.string.welcome_select_body),
}

/**
 * The first-launch welcome screen, laid over the turning globe.
 *
 * Follows the welcome screens of Apple's own apps: a large title, a short list of what the app
 * does, and a single button that goes straight to the globe. Everything else is taught later, in
 * place, as the user needs it. The title, features and button appear one after another.
 *
 * @param onContinue called when the user taps Continue.
 * @param modifier applied to the root element.
 */
@Composable
internal fun WelcomeOverlay(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    // A surface rather than a plain box: it sets the text colour and keeps touches off the globe.
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = SCRIM_ALPHA),
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.fillMaxSize(),
    ) {
        // The surface stretches its child to fill it, so a box centres the narrower column.
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(CONTENT_PADDING)
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxSize(),
            ) {
                Spacer(Modifier.weight(1f))
                Reveal(visible = revealed, order = 0) {
                    Text(
                        text = stringResource(Res.string.welcome_title),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Spacer(Modifier.height(SECTION_SPACING))
                Column(verticalArrangement = Arrangement.spacedBy(SECTION_SPACING)) {
                    WelcomeFeature.entries.forEachIndexed { index, feature ->
                        Reveal(visible = revealed, order = index + 1) { FeatureRow(feature) }
                    }
                }
                Spacer(Modifier.weight(1f))
                Reveal(visible = revealed, order = WelcomeFeature.entries.size + 1) {
                    Button(
                        onClick = onContinue,
                        shape = RoundedCornerShape(BUTTON_CORNER),
                        modifier = Modifier.fillMaxWidth().height(BUTTON_HEIGHT),
                    ) {
                        Text(
                            text = stringResource(Res.string.welcome_continue),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

/** Fades [content] in and up, [order] steps after the first element. */
@Composable
private fun Reveal(visible: Boolean, order: Int, content: @Composable () -> Unit) {
    val delay = order * REVEAL_STAGGER_MILLIS
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(REVEAL_MILLIS, delayMillis = delay)) +
            slideInVertically(tween(REVEAL_MILLIS, delayMillis = delay)) { height -> height / 2 },
    ) {
        content()
    }
}

@Composable
private fun FeatureRow(feature: WelcomeFeature, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FEATURE_GLYPH_SPACING),
        modifier = modifier.fillMaxWidth(),
    ) {
        Canvas(modifier = Modifier.size(GLYPH_SIZE)) {
            when (feature) {
                WelcomeFeature.Spin -> drawGlobeGlyph(tint)
                WelcomeFeature.Select -> drawTapGlyph(tint)
            }
        }
        Column {
            Text(text = stringResource(feature.title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(feature.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_TEXT_ALPHA),
            )
        }
    }
}

/** A globe: an outline, the equator and one meridian seen at an angle. */
private fun DrawScope.drawGlobeGlyph(tint: Color) {
    val stroke = Stroke(width = GLYPH_STROKE.toPx())
    val radius = size.minDimension / 2 - stroke.width
    drawCircle(tint, radius = radius, style = stroke)
    drawLine(tint, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), stroke.width)
    val meridian = Size(radius, radius * 2)
    drawOval(tint, topLeft = Offset(center.x - meridian.width / 2, center.y - radius), size = meridian, style = stroke)
}

/** A tap: a filled point inside a ring, like the ripple of a finger. */
private fun DrawScope.drawTapGlyph(tint: Color) {
    val stroke = Stroke(width = GLYPH_STROKE.toPx())
    val radius = size.minDimension / 2 - stroke.width
    drawCircle(tint, radius = radius / 3)
    drawCircle(tint, radius = radius, style = stroke)
}
