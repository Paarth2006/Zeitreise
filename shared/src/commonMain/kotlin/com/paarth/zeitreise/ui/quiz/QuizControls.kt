package com.paarth.zeitreise.ui.quiz

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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.QuizRound
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.glass.GlassCircleButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import com.paarth.zeitreise.ui.glass.SHEET_MAX_WIDTH
import com.paarth.zeitreise.ui.glass.SheetHeader
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.quiz_best
import zeitreise.shared.generated.resources.quiz_close
import zeitreise.shared.generated.resources.quiz_done
import zeitreise.shared.generated.resources.quiz_find
import zeitreise.shared.generated.resources.quiz_new_best
import zeitreise.shared.generated.resources.quiz_next
import zeitreise.shared.generated.resources.quiz_open
import zeitreise.shared.generated.resources.quiz_play_again
import zeitreise.shared.generated.resources.quiz_progress
import zeitreise.shared.generated.resources.quiz_rank_beginner
import zeitreise.shared.generated.resources.quiz_rank_explorer
import zeitreise.shared.generated.resources.quiz_rank_perfect
import zeitreise.shared.generated.resources.quiz_rank_traveller
import zeitreise.shared.generated.resources.quiz_result
import zeitreise.shared.generated.resources.quiz_revealed
import zeitreise.shared.generated.resources.quiz_right
import zeitreise.shared.generated.resources.quiz_score
import zeitreise.shared.generated.resources.quiz_see_score
import zeitreise.shared.generated.resources.quiz_show_me
import zeitreise.shared.generated.resources.quiz_streak
import zeitreise.shared.generated.resources.quiz_wrong

/** Gap between the floating panel and the edges of the safe area. */
private val PANEL_MARGIN = 16.dp

/** Padding inside the quiz panel, and between its rows. */
private val PANEL_PADDING = 20.dp
private val PANEL_SPACING = 12.dp

/** Corner radius of the quiz panel, matching the sheets. */
private val PANEL_CORNER = 32.dp

/** Size of the target drawn in the quiz button, and its stroke width. */
private val TARGET_SIZE = 20.dp
private val TARGET_STROKE = 2.dp

/** Radii of the target's rings and its centre, as shares of the glyph's half-width. */
private const val TARGET_INNER_RING = 0.55f
private const val TARGET_CENTRE = 0.18f

/** Fewest answers in a row worth cheering. */
private const val STREAK_TO_CHEER = 2

/** Shares of a round's questions answered right that earn each rank. */
private const val TRAVELLER_SHARE = 0.7
private const val EXPLORER_SHARE = 0.4

/** The verdict's colours, dark enough to read on the light glass. */
private val RIGHT_COLOR = Color(0xFF1B7F3B)
private val WRONG_COLOR = Color(0xFFC4332B)

/** Damping of the panel's spring, settling softly as the sheets do. */
private const val PANEL_DAMPING = 0.86f

private val PANEL_SPRING = spring(
    dampingRatio = PANEL_DAMPING,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/**
 * The round glass button that starts the quiz, showing a target.
 *
 * @param onClick called when the button is tapped.
 * @param modifier applied to the root element.
 */
@Composable
internal fun QuizButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.quiz_open)
    val tint = MaterialTheme.colorScheme.primary
    GlassCircleButton(onClick = onClick, modifier = modifier.semantics { contentDescription = label }) {
        Canvas(modifier = Modifier.size(TARGET_SIZE)) {
            val stroke = Stroke(width = TARGET_STROKE.toPx())
            val radius = size.minDimension / 2 - stroke.width / 2
            drawCircle(tint, radius = radius, style = stroke)
            drawCircle(tint, radius = radius * TARGET_INNER_RING, style = stroke)
            drawCircle(tint, radius = radius * TARGET_CENTRE)
        }
    }
}

/**
 * What the quiz panel can ask for.
 *
 * @property reveal gives the current question up and shows where the country is.
 * @property next asks the next question, or ends the round after the last.
 * @property playAgain starts a new round.
 * @property stop leaves the quiz.
 */
internal class QuizActions(
    val reveal: () -> Unit,
    val next: () -> Unit,
    val playAgain: () -> Unit,
    val stop: () -> Unit,
)

/**
 * The quiz panel at the bottom of the screen: the country to find, then the verdict on the user's
 * tap, and at the end of a round the score.
 *
 * @param state whether the quiz is on, and how far it has got.
 * @param actions what the panel can ask for.
 */
@Composable
internal fun BoxScope.QuizControls(state: QuizState, actions: QuizActions) {
    // The panel keeps showing its last question while it slides away, after the quiz ends.
    var shown by remember { mutableStateOf(state) }
    LaunchedEffect(state) { if (state != QuizState.Off) shown = state }
    AnimatedVisibility(
        visible = state != QuizState.Off,
        enter = slideInVertically(PANEL_SPRING) { height -> height } + fadeIn(),
        exit = slideOutVertically(PANEL_SPRING) { height -> height } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(PANEL_MARGIN),
    ) {
        QuizPanel(state = shown, actions = actions)
    }
}

@Composable
private fun QuizPanel(state: QuizState, actions: QuizActions, modifier: Modifier = Modifier) {
    GlassPanel(
        shape = RoundedCornerShape(PANEL_CORNER),
        modifier = modifier.widthIn(max = SHEET_MAX_WIDTH).fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PANEL_SPACING),
            modifier = Modifier.padding(PANEL_PADDING),
        ) {
            when (state) {
                QuizState.Off -> Unit
                is QuizState.Playing -> Question(round = state.round, actions = actions)
                is QuizState.Finished -> RoundResult(result = state, actions = actions)
            }
        }
    }
}

/** The country to find, and once the user has tapped, whether they found it. */
@Composable
private fun Question(round: QuizRound, actions: QuizActions) {
    SheetHeader(
        title = stringResource(Res.string.quiz_find, round.country.name),
        subtitle = stringResource(
            Res.string.quiz_progress,
            round.number + 1,
            round.questions.size,
            round.country.continent,
        ),
        closeLabel = stringResource(Res.string.quiz_close),
        onClose = actions.stop,
    )
    if (round.answered) Verdict(round)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SecondaryText(stringResource(Res.string.quiz_score, round.score), modifier = Modifier.weight(1f))
        if (round.answered) {
            GlassButton(onClick = actions.next, prominent = true) {
                val label = if (round.isLast) Res.string.quiz_see_score else Res.string.quiz_next
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            GlassButton(onClick = actions.reveal) {
                Text(stringResource(Res.string.quiz_show_me), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Whether the user found the country, or which one they tapped instead; read out as it appears. */
@Composable
private fun Verdict(round: QuizRound) {
    val wrongGuess = round.guess
    val text = when {
        round.isRight -> buildString {
            append(stringResource(Res.string.quiz_right, round.country.name))
            if (round.streak >= STREAK_TO_CHEER) {
                append(' ')
                append(pluralStringResource(Res.plurals.quiz_streak, round.streak, round.streak))
            }
        }
        wrongGuess != null -> stringResource(Res.string.quiz_wrong, wrongGuess.name, round.country.name)
        else -> stringResource(Res.string.quiz_revealed, round.country.name)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (round.isRight) RIGHT_COLOR else WRONG_COLOR,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The round's score, a word on how it went, and the best so far. */
@Composable
private fun RoundResult(result: QuizState.Finished, actions: QuizActions) {
    SheetHeader(
        title = stringResource(Res.string.quiz_result, result.score, result.total),
        subtitle = stringResource(rankOf(result.score, result.total)),
        closeLabel = stringResource(Res.string.quiz_close),
        onClose = actions.stop,
    )
    val record = when {
        result.isNewBest -> stringResource(Res.string.quiz_new_best)
        result.best > 0 -> stringResource(Res.string.quiz_best, result.best, result.total)
        else -> null
    }
    record?.let { SecondaryText(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(PANEL_SPACING, Alignment.End),
        modifier = Modifier.fillMaxWidth(),
    ) {
        GlassButton(onClick = actions.stop) {
            Text(stringResource(Res.string.quiz_done), style = MaterialTheme.typography.titleMedium)
        }
        GlassButton(onClick = actions.playAgain, prominent = true) {
            Text(stringResource(Res.string.quiz_play_again), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A word on how a round went, from a perfect round down to a first try. */
private fun rankOf(score: Int, total: Int) = when {
    score == total -> Res.string.quiz_rank_perfect
    score >= total * TRAVELLER_SHARE -> Res.string.quiz_rank_traveller
    score >= total * EXPLORER_SHARE -> Res.string.quiz_rank_explorer
    else -> Res.string.quiz_rank_beginner
}

@Composable
private fun SecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
        modifier = modifier,
    )
}

@Preview
@Composable
private fun QuizResultPreview() {
    ZeitreiseTheme {
        QuizPanel(
            state = QuizState.Finished(score = 8, total = 10, best = 8, isNewBest = true),
            actions = QuizActions({}, {}, {}, {}),
        )
    }
}
