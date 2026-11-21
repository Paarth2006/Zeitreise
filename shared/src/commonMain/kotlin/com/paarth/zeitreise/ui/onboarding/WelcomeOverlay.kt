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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.ic_quiz
import zeitreise.shared.generated.resources.ic_time_travel
import zeitreise.shared.generated.resources.welcome_continue
import zeitreise.shared.generated.resources.welcome_play_body
import zeitreise.shared.generated.resources.welcome_play_title
import zeitreise.shared.generated.resources.welcome_select_body
import zeitreise.shared.generated.resources.welcome_select_title
import zeitreise.shared.generated.resources.welcome_spin_body
import zeitreise.shared.generated.resources.welcome_spin_title
import zeitreise.shared.generated.resources.welcome_tagline
import zeitreise.shared.generated.resources.welcome_time_body
import zeitreise.shared.generated.resources.welcome_time_title
import zeitreise.shared.generated.resources.welcome_title

/** Maximum width of the welcome content. */
private val CONTENT_MAX_WIDTH = 480.dp

/** Padding around the welcome content. */
private val CONTENT_PADDING = 24.dp

/** Spacing between the title and features, between the title and tagline, and between features. */
private val SECTION_SPACING = 32.dp
private val TAGLINE_SPACING = 8.dp
private val FEATURE_SPACING = 24.dp

/** Spacing between a feature's icon and text. */
private val FEATURE_GLYPH_SPACING = 16.dp

/** Size of a feature icon. */
private val GLYPH_SIZE = 36.dp

/** Stroke width of the feature icons. */
private val GLYPH_STROKE = 2.dp

/** Height of the Continue button. */
private val BUTTON_HEIGHT = 50.dp

/** Opacity of the background over the globe. */
private const val SCRIM_ALPHA = 0.72f

/** Fade-in time for each element, in milliseconds. */
private const val REVEAL_MILLIS = 500

/** Delay between elements appearing, in milliseconds. */
private const val REVEAL_STAGGER_MILLIS = 150

/** A feature listed on the welcome screen. */
private enum class WelcomeFeature(val title: StringResource, val body: StringResource) {
    Spin(Res.string.welcome_spin_title, Res.string.welcome_spin_body),
    Select(Res.string.welcome_select_title, Res.string.welcome_select_body),
    TimeTravel(Res.string.welcome_time_title, Res.string.welcome_time_body),
    Play(Res.string.welcome_play_title, Res.string.welcome_play_body),
}

/**
 * The welcome screen shown on first launch, over the globe. It has a title, a tagline, a short
 * feature list and a Continue button, which appear one after another.
 *
 * @param onContinue called when the user taps Continue.
 * @param modifier applied to the root element.
 */
@Composable
internal fun WelcomeOverlay(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    // A Surface sets the text colour and blocks touches from reaching the globe.
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = SCRIM_ALPHA),
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.fillMaxSize(),
    ) {
        // The Box centres the column, since the Surface stretches its child.
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
                Reveal(visible = revealed, order = 0) { WelcomeTitle() }
                Spacer(Modifier.height(SECTION_SPACING))
                Column(verticalArrangement = Arrangement.spacedBy(FEATURE_SPACING)) {
                    WelcomeFeature.entries.forEachIndexed { index, feature ->
                        Reveal(visible = revealed, order = index + 1) { FeatureRow(feature) }
                    }
                }
                Spacer(Modifier.weight(1f))
                Reveal(visible = revealed, order = WelcomeFeature.entries.size + 1) {
                    GlassButton(
                        onClick = onContinue,
                        prominent = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = BUTTON_HEIGHT),
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

/** The app's name and a line on what it's about. */
@Composable
private fun WelcomeTitle() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TAGLINE_SPACING),
    ) {
        Text(
            text = stringResource(Res.string.welcome_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.welcome_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_TEXT_ALPHA),
        )
    }
}

/** Fades [content] in and up, delayed by [order] steps. */
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
        when (feature) {
            WelcomeFeature.Spin -> Canvas(modifier = Modifier.size(GLYPH_SIZE)) { drawGlobeGlyph(tint) }
            WelcomeFeature.Select -> Canvas(modifier = Modifier.size(GLYPH_SIZE)) { drawTapGlyph(tint) }
            // The same icons as the toolbar buttons, so the user recognises them later.
            WelcomeFeature.TimeTravel -> ToolbarGlyph(Res.drawable.ic_time_travel, tint)
            WelcomeFeature.Play -> ToolbarGlyph(Res.drawable.ic_quiz, tint)
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

@Composable
private fun ToolbarGlyph(icon: DrawableResource, tint: Color) {
    Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(GLYPH_SIZE))
}

/** A globe icon: a circle with the equator and one meridian. */
private fun DrawScope.drawGlobeGlyph(tint: Color) {
    val stroke = Stroke(width = GLYPH_STROKE.toPx())
    val radius = size.minDimension / 2 - stroke.width
    drawCircle(tint, radius = radius, style = stroke)
    drawLine(tint, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), stroke.width)
    val meridian = Size(radius, radius * 2)
    drawOval(tint, topLeft = Offset(center.x - meridian.width / 2, center.y - radius), size = meridian, style = stroke)
}

/** A tap icon: a dot inside a ring. */
private fun DrawScope.drawTapGlyph(tint: Color) {
    val stroke = Stroke(width = GLYPH_STROKE.toPx())
    val radius = size.minDimension / 2 - stroke.width
    drawCircle(tint, radius = radius / 3)
    drawCircle(tint, radius = radius, style = stroke)
}

@Preview
@Composable
private fun WelcomeOverlayPreview() {
    ZeitreiseTheme {
        WelcomeOverlay(onContinue = {})
    }
}
