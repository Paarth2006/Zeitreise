package com.paarth.zeitreise.ui.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.QuizRound
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import com.paarth.zeitreise.ui.glass.SHEET_MAX_WIDTH
import com.paarth.zeitreise.ui.glass.SheetHeader
import com.paarth.zeitreise.ui.timetravel.yearLabel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.quiz_best
import zeitreise.shared.generated.resources.quiz_close
import zeitreise.shared.generated.resources.quiz_done
import zeitreise.shared.generated.resources.quiz_find
import zeitreise.shared.generated.resources.quiz_find_capital
import zeitreise.shared.generated.resources.quiz_new_best
import zeitreise.shared.generated.resources.quiz_next
import zeitreise.shared.generated.resources.quiz_play_again
import zeitreise.shared.generated.resources.quiz_progress
import zeitreise.shared.generated.resources.quiz_rank_beginner
import zeitreise.shared.generated.resources.quiz_rank_explorer
import zeitreise.shared.generated.resources.quiz_rank_perfect
import zeitreise.shared.generated.resources.quiz_rank_traveller
import zeitreise.shared.generated.resources.quiz_result
import zeitreise.shared.generated.resources.quiz_revealed
import zeitreise.shared.generated.resources.quiz_revealed_capital
import zeitreise.shared.generated.resources.quiz_right
import zeitreise.shared.generated.resources.quiz_right_capital
import zeitreise.shared.generated.resources.quiz_score
import zeitreise.shared.generated.resources.quiz_see_score
import zeitreise.shared.generated.resources.quiz_show_me
import zeitreise.shared.generated.resources.quiz_streak
import zeitreise.shared.generated.resources.quiz_world_in
import zeitreise.shared.generated.resources.quiz_wrong
import zeitreise.shared.generated.resources.quiz_wrong_capital

/** Margin around the panel. */
private val PANEL_MARGIN = 16.dp

/** Padding and row spacing in the quiz panel. */
private val PANEL_PADDING = 20.dp
private val PANEL_SPACING = 12.dp

/** Corner radius of the quiz panel. */
private val PANEL_CORNER = 32.dp

/** Minimum streak to show a message. */
private const val STREAK_TO_CHEER = 2

/** Score shares needed for each rank. */
private const val TRAVELLER_SHARE = 0.7
private const val EXPLORER_SHARE = 0.4

/** Colours for right and wrong answers, dark enough for the light glass. */
private val RIGHT_COLOR = Color(0xFF1B7F3B)
private val WRONG_COLOR = Color(0xFFC4332B)

/** Spring damping for the panel. */
private const val PANEL_DAMPING = 0.86f

private val PANEL_SPRING = spring(
    dampingRatio = PANEL_DAMPING,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/**
 * Actions for the quiz panel.
 *
 * @property reveal skips the question and shows the answer.
 * @property next goes to the next question, or ends the round.
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
 * The quiz panel at the bottom of the screen. It shows the question, the result of each answer
 * and the final score.
 *
 * @param state the quiz state.
 * @param actions the panel actions.
 */
@Composable
internal fun BoxScope.QuizControls(state: QuizState, actions: QuizActions) {
    // Keep showing the last question while the panel slides away.
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

/** The current question and, once answered, the result. */
@Composable
private fun Question(round: QuizRound, actions: QuizActions) {
    val capital = round.question.capital
    val year = round.question.map?.year
    SheetHeader(
        title = if (capital != null) {
            stringResource(Res.string.quiz_find_capital, capital)
        } else {
            stringResource(Res.string.quiz_find, round.target.name)
        },
        // A country's continent is a hint; for the past, the year the map shows.
        subtitle = stringResource(
            Res.string.quiz_progress,
            round.number + 1,
            round.length,
            (round.target as? Country)?.continent
                ?: year?.let { stringResource(Res.string.quiz_world_in, yearLabel(it)) }.orEmpty(),
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

/** The answer result, announced by screen readers when it appears. */
@Composable
private fun Verdict(round: QuizRound) {
    val cheer = if (round.isRight && round.streak >= STREAK_TO_CHEER) {
        " ${pluralStringResource(Res.plurals.quiz_streak, round.streak, round.streak)}"
    } else {
        ""
    }
    val text = verdictText(round) + cheer
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (round.isRight) RIGHT_COLOR else WRONG_COLOR,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The result text, like "Right! That's Peru." or "Right! Lima is the capital of Peru." */
@Composable
private fun verdictText(round: QuizRound): String {
    val country = round.target.name
    val capital = round.question.capital
    val wrongGuess = round.guess
    return when {
        round.isRight && capital != null -> stringResource(Res.string.quiz_right_capital, capital, country)
        round.isRight -> stringResource(Res.string.quiz_right, country)
        wrongGuess != null && capital != null ->
            stringResource(Res.string.quiz_wrong_capital, wrongGuess.name, capital, country)
        wrongGuess != null -> stringResource(Res.string.quiz_wrong, wrongGuess.name, country)
        capital != null -> stringResource(Res.string.quiz_revealed_capital, capital, country)
        else -> stringResource(Res.string.quiz_revealed, country)
    }
}

/** The end-of-round card with the score, a rank and the best score. */
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

/** The rank message for a score. */
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
