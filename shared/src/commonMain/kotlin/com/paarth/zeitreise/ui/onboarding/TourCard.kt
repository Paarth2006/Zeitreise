package com.paarth.zeitreise.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.ic_passport
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

/** Padding, spacing and corner radius of the tour card. */
private val CARD_PADDING = 20.dp
private val CARD_SPACING = 8.dp
private val CARD_CORNER = 28.dp

/** Size of the passport stamp, and how big it starts before it lands. */
private val STAMP_SIZE = 28.dp
private const val STAMP_START_SCALE = 2.2f

/** The stamp lands slightly crooked, like a real one. */
private const val STAMP_TILT_DEGREES = -12f

/**
 * The tour card: which stop this is, what the control does, and buttons to skip or go on.
 *
 * @param step the current tour step.
 * @param onNext called when the user taps Next.
 * @param onSkip called when the user taps Skip tour.
 * @param modifier applied to the root element.
 */
@Composable
internal fun TourCard(
    step: OnboardingStep.Tour,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, body) = step.stop.text()
    val isLast = step.stop == TourStop.entries.last()
    GlassPanel(shape = RoundedCornerShape(CARD_CORNER), modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
            modifier = Modifier.padding(CARD_PADDING).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.tour_progress, step.stop.ordinal + 1, TourStop.entries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
                    modifier = Modifier.weight(1f),
                )
                if (step.stop == TourStop.Passport) PassportStamp()
            }
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

/** A passport stamp that lands on the card with a bounce. */
@Composable
private fun PassportStamp() {
    val landing = remember { Animatable(STAMP_START_SCALE) }
    LaunchedEffect(landing) {
        landing.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Icon(
        painter = painterResource(Res.drawable.ic_passport),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(STAMP_SIZE)
            .graphicsLayer {
                scaleX = landing.value
                scaleY = landing.value
                // The bounce overshoots below 1, so keep the opacity in range.
                alpha = ((STAMP_START_SCALE - landing.value) / (STAMP_START_SCALE - 1f)).coerceIn(0f, 1f)
                rotationZ = STAMP_TILT_DEGREES
            },
    )
}

@Preview
@Composable
private fun TourCardPreview() {
    ZeitreiseTheme {
        TourCard(step = OnboardingStep.Tour(TourStop.TimeTravel), onNext = {}, onSkip = {})
    }
}
