package com.paarth.zeitreise.ui.globe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.GuideAnswer
import com.paarth.zeitreise.domain.GuideFact
import com.paarth.zeitreise.domain.GuideNotice
import com.paarth.zeitreise.domain.SourcedFact
import com.paarth.zeitreise.domain.StorySource
import com.paarth.zeitreise.domain.WikiArticle
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.story_ai_unavailable
import zeitreise.shared.generated.resources.story_did_you_know
import zeitreise.shared.generated.resources.story_failed
import zeitreise.shared.generated.resources.story_known_for
import zeitreise.shared.generated.resources.story_loading
import zeitreise.shared.generated.resources.story_next_fact
import zeitreise.shared.generated.resources.story_no_article
import zeitreise.shared.generated.resources.story_rate_limited
import zeitreise.shared.generated.resources.story_read_more
import zeitreise.shared.generated.resources.story_retry
import zeitreise.shared.generated.resources.story_source_ai
import zeitreise.shared.generated.resources.story_source_curated
import zeitreise.shared.generated.resources.story_source_wikipedia

/** Space between the parts of a story. */
private val STORY_SPACING = 12.dp

/** Space between "known for" chips. */
private val CHIP_SPACING = 8.dp

/** Padding inside a "known for" chip. */
private val CHIP_PADDING_HORIZONTAL = 12.dp
private val CHIP_PADDING_VERTICAL = 6.dp

/** Size of the loading indicator. */
private val LOADING_INDICATOR_SIZE = 16.dp
private val LOADING_INDICATOR_STROKE = 2.dp

/** Opacity of the "known for" chips' background. */
private const val CHIP_ALPHA = 0.07f

/** Total time of the typing animation, in milliseconds. */
private const val TYPE_DURATION_MILLIS = 1_400L

/** Maximum time per letter, so short facts don't type too slowly. */
private const val MAX_MILLIS_PER_LETTER = 28L

/**
 * The guide's story in the sheet: the headline, the fact (typed out), "known for" chips and the source.
 *
 * @param story the current story state.
 * @param onRetry called when the user taps Try again.
 * @param onNextFact called when the user taps Next fact.
 * @param modifier applied to the root element.
 */
@Composable
internal fun StoryPanel(
    story: StoryState,
    onRetry: () -> Unit,
    onNextFact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = story,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = StoryState::animationKey,
        modifier = modifier,
    ) { state ->
        when (state) {
            StoryState.Idle -> Unit
            StoryState.Loading -> LoadingLine()
            is StoryState.Ready -> StoryContent(state, onNextFact)
            StoryState.NoArticle -> SecondaryText(stringResource(Res.string.story_no_article))
            StoryState.Failed -> FailedLine(onRetry)
        }
    }
}

@Composable
private fun StoryContent(state: StoryState.Ready, onNextFact: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val answer = state.answer
    val shown = state.fact
    Column(verticalArrangement = Arrangement.spacedBy(STORY_SPACING), modifier = modifier.fillMaxWidth()) {
        Text(
            text = shown.fact.headline ?: stringResource(Res.string.story_did_you_know),
            style = MaterialTheme.typography.titleMedium,
        )
        TypewriterText(text = shown.fact.text, style = MaterialTheme.typography.bodyLarge)
        if (answer.knownFor.isNotEmpty()) KnownFor(answer.knownFor)
        answer.notice?.let { notice -> SecondaryText(stringResource(notice.message())) }
        SecondaryText(stringResource(shown.source.credit()))
        Row(
            horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING),
            modifier = Modifier.fillMaxWidth().padding(top = CHIP_SPACING),
        ) {
            if (answer.facts.size > 1) {
                GlassButton(onClick = onNextFact, prominent = true) {
                    ButtonLabel(stringResource(Res.string.story_next_fact))
                }
            }
            GlassButton(onClick = { uriHandler.openUri(answer.article.pageUrl) }, modifier = Modifier.weight(1f)) {
                ButtonLabel(stringResource(Res.string.story_read_more))
            }
        }
    }
}

@Composable
private fun KnownFor(items: List<String>, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(CHIP_SPACING), modifier = modifier) {
        SecondaryText(stringResource(Res.string.story_known_for))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CHIP_SPACING),
            verticalArrangement = Arrangement.spacedBy(CHIP_SPACING),
        ) {
            items.forEach { item ->
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = CHIP_ALPHA)) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(
                            horizontal = CHIP_PADDING_HORIZONTAL,
                            vertical = CHIP_PADDING_VERTICAL,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Shows [text] letter by letter. The full text is laid out from the start with the unrevealed part
 * transparent, so the sheet doesn't change height and screen readers read the whole fact.
 */
@Composable
private fun TypewriterText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    var written by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        val pause = (TYPE_DURATION_MILLIS / text.length.coerceAtLeast(1)).coerceAtMost(MAX_MILLIS_PER_LETTER)
        while (written < text.length) {
            delay(pause)
            written++
        }
    }
    Text(
        text = buildAnnotatedString {
            append(text.take(written))
            withStyle(SpanStyle(color = Color.Transparent)) { append(text.drop(written)) }
        },
        style = style,
        modifier = modifier,
    )
}

@Composable
private fun LoadingLine(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(STORY_SPACING),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        CircularProgressIndicator(
            strokeWidth = LOADING_INDICATOR_STROKE,
            modifier = Modifier.size(LOADING_INDICATOR_SIZE),
        )
        SecondaryText(stringResource(Res.string.story_loading))
    }
}

@Composable
private fun FailedLine(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        SecondaryText(stringResource(Res.string.story_failed), modifier = Modifier.weight(1f))
        GlassButton(onClick = onRetry) {
            ButtonLabel(stringResource(Res.string.story_retry))
        }
    }
}

/** A button label in a semi-bold style. */
@Composable
private fun ButtonLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = modifier)
}

@Composable
private fun SecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
        modifier = modifier,
    )
}

/** Animates between different states and places, but not between facts of the same story. */
private fun StoryState.animationKey(): String = when (this) {
    StoryState.Idle -> "idle"
    StoryState.Loading -> "loading"
    is StoryState.Ready -> "ready:${answer.article.pageUrl}"
    StoryState.NoArticle -> "no-article"
    StoryState.Failed -> "failed"
}

private fun StorySource.credit() = when (this) {
    StorySource.Ai -> Res.string.story_source_ai
    StorySource.Curated -> Res.string.story_source_curated
    StorySource.Wikipedia -> Res.string.story_source_wikipedia
}

private fun GuideNotice.message() = when (this) {
    GuideNotice.RateLimited -> Res.string.story_rate_limited
    GuideNotice.Unavailable -> Res.string.story_ai_unavailable
}

@Preview
@Composable
private fun StoryPanelPreview() {
    ZeitreiseTheme {
        Surface {
            StoryPanel(
                story = StoryState.Ready(
                    GuideAnswer(
                        facts = listOf(
                            SourcedFact(
                                GuideFact(
                                    headline = "Money that speaks 17 languages",
                                    text = "Every rupee banknote spells out its value in 17 languages.",
                                ),
                                StorySource.Curated,
                            ),
                        ),
                        knownFor = listOf("The Taj Mahal", "The Himalayas", "Its cuisines"),
                        article = WikiArticle(
                            title = "India",
                            extract = "India is a country in South Asia.",
                            pageUrl = "https://en.wikipedia.org/wiki/India",
                        ),
                    ),
                ),
                onRetry = {},
                onNextFact = {},
                modifier = Modifier.padding(STORY_SPACING),
            )
        }
    }
}
