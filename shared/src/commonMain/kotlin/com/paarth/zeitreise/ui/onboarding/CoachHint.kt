package com.paarth.zeitreise.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.hint_select
import zeitreise.shared.generated.resources.hint_skip
import zeitreise.shared.generated.resources.hint_turn

/** Minimum touch target the Human Interface Guidelines ask for, which the capsule never drops below. */
private val TOUCH_TARGET = 44.dp

/** Padding before the hint text; the Skip button carries its own padding on the other side. */
private val HINT_LEADING_PADDING = 20.dp

/** Opacity of the capsule over the globe. */
private const val CAPSULE_ALPHA = 0.94f

/**
 * A short instruction in a capsule, asking the user to try the gesture it names.
 *
 * Teaches in place rather than in a tutorial: the hint stays until the user does what it says,
 * and Skip ends the onboarding at any time. Screen readers announce each new hint.
 *
 * @param step the onboarding step; only [OnboardingStep.TryTurning] and
 *   [OnboardingStep.TrySelecting] show a hint.
 * @param onSkip called when the user taps Skip.
 * @param modifier applied to the root element.
 */
@Composable
internal fun CoachHint(step: OnboardingStep, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = step.hint(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier,
    ) { hint ->
        if (hint != null) HintCapsule(text = stringResource(hint), onSkip = onSkip)
    }
}

@Composable
private fun HintCapsule(text: String, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = CAPSULE_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = TOUCH_TARGET).padding(start = HINT_LEADING_PADDING),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = TOUCH_TARGET)) {
                Text(
                    text = stringResource(Res.string.hint_skip),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
                )
            }
        }
    }
}

/** The instruction this step shows, or `null` for the steps that show no hint. */
private fun OnboardingStep.hint(): StringResource? = when (this) {
    OnboardingStep.TryTurning -> Res.string.hint_turn
    OnboardingStep.TrySelecting -> Res.string.hint_select
    OnboardingStep.Checking, OnboardingStep.Welcome, OnboardingStep.Finished -> null
}
