package com.paarth.zeitreise.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.CapsuleShape
import com.paarth.zeitreise.ui.glass.GlassPanel
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

/** Scale a hint grows from as it appears and shrinks to as it leaves. */
private const val HINT_START_SCALE = 0.9f

/** A lively spring for hints coming and going, like iOS's bouncy glass morphs. */
private val HINT_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

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
        transitionSpec = {
            fadeIn() + scaleIn(HINT_SPRING, initialScale = HINT_START_SCALE) togetherWith
                fadeOut() + scaleOut(HINT_SPRING, targetScale = HINT_START_SCALE)
        },
        modifier = modifier,
    ) { hint ->
        if (hint != null) HintCapsule(text = stringResource(hint), onSkip = onSkip)
    }
}

@Composable
private fun HintCapsule(text: String, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(shape = CapsuleShape, modifier = modifier) {
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
                    color = MaterialTheme.colorScheme.primary,
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

@Preview
@Composable
private fun CoachHintPreview() {
    ZeitreiseTheme {
        CoachHint(step = OnboardingStep.TryTurning, onSkip = {})
    }
}
