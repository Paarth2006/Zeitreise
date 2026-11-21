package com.paarth.zeitreise.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.tour_about_body
import zeitreise.shared.generated.resources.tour_about_title
import zeitreise.shared.generated.resources.tour_done
import zeitreise.shared.generated.resources.tour_next
import zeitreise.shared.generated.resources.tour_passport_body
import zeitreise.shared.generated.resources.tour_passport_title
import zeitreise.shared.generated.resources.tour_progress
import zeitreise.shared.generated.resources.tour_quiz_body
import zeitreise.shared.generated.resources.tour_quiz_title
import zeitreise.shared.generated.resources.tour_search_body
import zeitreise.shared.generated.resources.tour_search_title
import zeitreise.shared.generated.resources.tour_skip
import zeitreise.shared.generated.resources.tour_start_exploring
import zeitreise.shared.generated.resources.tour_time_travel_body
import zeitreise.shared.generated.resources.tour_time_travel_title
import zeitreise.shared.generated.resources.tour_today_body
import zeitreise.shared.generated.resources.tour_today_title

/** Maximum width, padding, spacing and corner radius of the tour card. */
private val CARD_MAX_WIDTH = 360.dp
private val CARD_PADDING = 20.dp
private val CARD_SPACING = 8.dp
private val CARD_CORNER = 28.dp

/** Width of the ring around the highlighted control, and how long one pulse takes. */
private val HIGHLIGHT_WIDTH = 3.dp
private const val HIGHLIGHT_PULSE_MILLIS = 800

/** The ring fades between these opacities while it pulses. */
private const val HIGHLIGHT_MIN_ALPHA = 0.35f
private const val HIGHLIGHT_MAX_ALPHA = 1f

/**
 * The tour card in the middle of the screen. It explains the control that has the highlight ring,
 * and blocks taps on the globe so nothing opens over the tour.
 *
 * @param step the current tour step.
 * @param onNext called when the user taps Next.
 * @param onSkip called when the user taps Skip tour.
 * @param modifier applied to the root element.
 */
@Composable
internal fun TourOverlay(
    step: OnboardingStep.Tour,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            // Catch taps so the globe and the controls below don't react during the tour.
            .clickable(interactionSource = null, indication = null, onClick = {})
            .safeDrawingPadding()
            .padding(CARD_PADDING),
    ) {
        TourCard(step = step, onNext = onNext, onSkip = onSkip)
    }
}

@Composable
private fun TourCard(step: OnboardingStep.Tour, onNext: () -> Unit, onSkip: () -> Unit) {
    val (title, body) = step.stop.text()
    val isLast = step.stop == TourStop.entries.last()
    GlassPanel(shape = RoundedCornerShape(CARD_CORNER), modifier = Modifier.widthIn(max = CARD_MAX_WIDTH)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
            modifier = Modifier.padding(CARD_PADDING).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = stringResource(Res.string.tour_progress, step.stop.ordinal + 1, TourStop.entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
            )
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(body), style = MaterialTheme.typography.bodyLarge)
            Row(modifier = Modifier.fillMaxWidth().padding(top = CARD_SPACING)) {
                if (!isLast) {
                    GlassButton(onClick = onSkip) {
                        Text(stringResource(Res.string.tour_skip), style = MaterialTheme.typography.titleMedium)
                    }
                }
                // Keeps Next on the right edge, also on the last card where Skip is gone.
                Spacer(Modifier.weight(1f))
                GlassButton(onClick = onNext, prominent = true) {
                    val label = when {
                        !isLast -> Res.string.tour_next
                        step.replay -> Res.string.tour_done
                        else -> Res.string.tour_start_exploring
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** The title and text for each stop of the tour. */
private fun TourStop.text(): Pair<StringResource, StringResource> = when (this) {
    TourStop.CountryOfTheDay -> Res.string.tour_today_title to Res.string.tour_today_body
    TourStop.Search -> Res.string.tour_search_title to Res.string.tour_search_body
    TourStop.TimeTravel -> Res.string.tour_time_travel_title to Res.string.tour_time_travel_body
    TourStop.Quiz -> Res.string.tour_quiz_title to Res.string.tour_quiz_body
    TourStop.Passport -> Res.string.tour_passport_title to Res.string.tour_passport_body
    TourStop.About -> Res.string.tour_about_title to Res.string.tour_about_body
}

/** Draws a pulsing accent ring around the control while the tour is explaining it. */
@Composable
internal fun Modifier.tourHighlight(active: Boolean, shape: Shape): Modifier {
    if (!active) return this
    val pulse = rememberInfiniteTransition()
    val alpha by pulse.animateFloat(
        initialValue = HIGHLIGHT_MIN_ALPHA,
        targetValue = HIGHLIGHT_MAX_ALPHA,
        animationSpec = infiniteRepeatable(tween(HIGHLIGHT_PULSE_MILLIS), RepeatMode.Reverse),
    )
    return border(HIGHLIGHT_WIDTH, MaterialTheme.colorScheme.primary.copy(alpha = alpha), shape)
}

@Preview
@Composable
private fun TourCardPreview() {
    ZeitreiseTheme {
        TourCard(step = OnboardingStep.Tour(TourStop.TimeTravel), onNext = {}, onSkip = {})
    }
}
