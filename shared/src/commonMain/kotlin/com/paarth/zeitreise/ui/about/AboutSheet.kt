package com.paarth.zeitreise.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.about_body
import zeitreise.shared.generated.resources.about_close
import zeitreise.shared.generated.resources.about_credit_ai
import zeitreise.shared.generated.resources.about_credit_flags
import zeitreise.shared.generated.resources.about_credit_history
import zeitreise.shared.generated.resources.about_credit_natural_earth
import zeitreise.shared.generated.resources.about_credit_wikipedia
import zeitreise.shared.generated.resources.about_credits_heading
import zeitreise.shared.generated.resources.about_title
import zeitreise.shared.generated.resources.tour_replay

/** Maximum width of the About card. */
private val CARD_MAX_WIDTH = 420.dp

/** Padding, section spacing and corner radius of the card. */
private val CARD_PADDING = 24.dp
private val CARD_SPACING = 12.dp
private val CARD_CORNER = 32.dp

/** How much the globe is dimmed behind the card. */
private const val SCRIM_ALPHA = 0.35f

/** Starting scale of the card animation. */
private const val CARD_START_SCALE = 0.88f

/** A slightly bouncy spring for the card. */
private val CARD_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

/**
 * The About card: what the app is and the data credits required by the licences.
 *
 * @param visible whether the card is shown.
 * @param onDismiss called when the card is closed.
 * @param onTakeTour called when the user asks to see the tour again. The card closes first.
 * @param modifier applied to the root element.
 */
@Composable
internal fun AboutSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTakeTour: () -> Unit = {},
) {
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
                    // Stop taps on the card from reaching the background and closing it.
                    .clickable(interactionSource = null, indication = null, onClick = {}),
            ) {
                AboutContent(onDismiss = onDismiss, onTakeTour = onTakeTour)
            }
        }
    }
}

@Composable
private fun AboutContent(onDismiss: () -> Unit, onTakeTour: () -> Unit, modifier: Modifier = Modifier) {
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
            Res.string.about_credit_history,
            Res.string.about_credit_flags,
            Res.string.about_credit_ai,
        ).forEach { credit ->
            Text(
                text = stringResource(credit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
            )
        }
        GlassButton(
            onClick = {
                onDismiss()
                onTakeTour()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(Res.string.tour_replay), style = MaterialTheme.typography.titleMedium)
        }
        GlassButton(onClick = onDismiss, prominent = true, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(Res.string.about_close), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview
@Composable
private fun AboutSheetPreview() {
    ZeitreiseTheme {
        AboutSheet(visible = true, onDismiss = {})
    }
}
