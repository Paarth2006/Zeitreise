package com.paarth.zeitreise.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.paarth.zeitreise.ai.leadSentences
import com.paarth.zeitreise.domain.Landmark
import com.paarth.zeitreise.domain.WikiArticle
import com.paarth.zeitreise.globe.GeoPoint
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import com.paarth.zeitreise.ui.glass.SheetHeader
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.place_close
import zeitreise.shared.generated.resources.place_facts_loading
import zeitreise.shared.generated.resources.place_photo
import zeitreise.shared.generated.resources.story_read_more
import zeitreise.shared.generated.resources.story_source_wikipedia

/** Gap above the sheet where the globe stays visible. */
private val SHEET_TOP_GAP = 12.dp

/** Corner radius of the modal sheet. */
private val SHEET_CORNER = 32.dp

/** Maximum width. On wider screens it floats in the middle as a card. */
private val SHEET_MAX_WIDTH = 560.dp

/** Padding inside the sheet. */
private val SHEET_PADDING = 20.dp

/** Size of the drag handle and the space around it. */
private val GRABBER_WIDTH = 36.dp
private val GRABBER_HEIGHT = 5.dp
private val GRABBER_SPACING = 8.dp

/** How far the sheet must be pulled down to close it. */
private val DISMISS_DISTANCE = 120.dp

/** A downward flick faster than this (pixels per second) closes the sheet. */
private const val DISMISS_VELOCITY = 1_200f

/** Corner radius of the photo. */
private val PHOTO_CORNER = 18.dp

/** Spacing between parts of the card. */
private val CARD_SPACING = 14.dp
private val FACT_SPACING = 8.dp

/** Aspect ratio of the photo. */
private const val PHOTO_ASPECT_RATIO = 4f / 3f

/** Grey for the photo placeholder and the drag handle. */
private const val PHOTO_PLACEHOLDER_ALPHA = 0.08f
private const val GRABBER_ALPHA = 0.25f

/** How much the globe is dimmed behind the sheet. */
private const val SCRIM_ALPHA = 0.3f

/** Maximum height as a floating card, as a share of the screen. */
private const val FLOATING_HEIGHT_SHARE = 0.86f

/** Spring damping, just under critical so it doesn't bounce. */
private const val SHEET_DAMPING = 0.86f

/** Spring used to show and hide the sheet. */
private val SHEET_SPRING = spring(
    dampingRatio = SHEET_DAMPING,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/**
 * A modal sheet for a pinned place, sliding up over the dimmed globe. It closes by pulling it down,
 * tapping outside it or tapping the close button. On wide screens it's a centred card.
 *
 * @param card the open card, or `null` to hide the sheet.
 * @param onClose called when the sheet is closed.
 * @param modifier applied to the root element.
 */
@Composable
internal fun PlaceSheet(card: LandmarkCard?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    // Keep showing the last place while the sheet slides away.
    var lastShown by remember { mutableStateOf<LandmarkCard?>(null) }
    LaunchedEffect(card) { if (card != null) lastShown = card }
    val shown = card ?: lastShown

    AnimatedVisibility(visible = card != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = null, indication = null, onClick = onClose),
        ) {
            val floating = maxWidth >= SHEET_MAX_WIDTH + SHEET_PADDING * 2
            val sheetModifier = if (floating) {
                Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(SHEET_PADDING)
                    .widthIn(max = SHEET_MAX_WIDTH)
                    .fillMaxHeight(FLOATING_HEIGHT_SHARE)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = SHEET_TOP_GAP)
                    .fillMaxSize()
            }
            if (shown != null) {
                ModalSheet(
                    card = shown,
                    onClose = onClose,
                    floating = floating,
                    modifier = sheetModifier.animateEnterExit(
                        enter = slideInVertically(SHEET_SPRING) { height -> height },
                        exit = slideOutVertically(SHEET_SPRING) { height -> height },
                    ),
                )
            }
        }
    }
}

/** The sheet itself. It follows the finger when pulled down and springs back or closes. */
@Composable
private fun ModalSheet(card: LandmarkCard, onClose: () -> Unit, floating: Boolean, modifier: Modifier = Modifier) {
    val pull = remember { Animatable(0f) }
    LaunchedEffect(card.landmark) { pull.snapTo(0f) }
    val scope = rememberCoroutineScope()
    val dismissDistance = with(LocalDensity.current) { DISMISS_DISTANCE.toPx() }
    val drag = rememberDraggableState { delta -> scope.launch { pull.snapTo((pull.value + delta).coerceAtLeast(0f)) } }

    GlassPanel(
        shape = if (floating) {
            RoundedCornerShape(SHEET_CORNER)
        } else {
            RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER)
        },
        modifier = modifier
            .graphicsLayer { translationY = pull.value }
            // Stops taps on the sheet from reaching the background and closing it.
            .clickable(interactionSource = null, indication = null, onClick = {}),
    ) {
        Column {
            val article = card.landmark.article
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = drag,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (pull.value > dismissDistance || velocity > DISMISS_VELOCITY) {
                                onClose()
                            } else {
                                pull.animateTo(0f, spring(dampingRatio = SHEET_DAMPING))
                            }
                        },
                    )
                    .padding(start = SHEET_PADDING, end = SHEET_PADDING / 2, bottom = CARD_SPACING),
            ) {
                Grabber()
                SheetHeader(
                    title = card.landmark.name,
                    subtitle = article.description?.replaceFirstChar(Char::uppercaseChar).orEmpty(),
                    closeLabel = stringResource(Res.string.place_close),
                    onClose = onClose,
                )
            }
            PlaceBody(
                card = card,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(start = SHEET_PADDING, end = SHEET_PADDING, bottom = SHEET_PADDING),
            )
        }
    }
}

/** The drag handle at the top of the sheet. */
@Composable
private fun Grabber(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(vertical = GRABBER_SPACING)
            .size(width = GRABBER_WIDTH, height = GRABBER_HEIGHT)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = GRABBER_ALPHA)),
    )
}

@Composable
private fun PlaceBody(card: LandmarkCard, modifier: Modifier = Modifier) {
    val article = card.landmark.article
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(CARD_SPACING), modifier = modifier.fillMaxWidth()) {
        article.thumbnailUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = stringResource(Res.string.place_photo, card.landmark.name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PHOTO_ASPECT_RATIO)
                    .clip(RoundedCornerShape(PHOTO_CORNER))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = PHOTO_PLACEHOLDER_ALPHA)),
            )
        }
        Text(text = leadSentences(article.extract), style = MaterialTheme.typography.bodyLarge)
        Facts(card.facts)
        SecondaryLine(stringResource(Res.string.story_source_wikipedia))
        GlassButton(
            onClick = { uriHandler.openUri(article.pageUrl) },
            prominent = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.story_read_more), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

/** The facts as a bullet list, or a loading line. */
@Composable
private fun Facts(facts: List<String>?, modifier: Modifier = Modifier) {
    when {
        facts == null -> SecondaryLine(stringResource(Res.string.place_facts_loading), modifier)
        facts.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(FACT_SPACING), modifier = modifier) {
            facts.forEach { fact ->
                Row(horizontalArrangement = Arrangement.spacedBy(FACT_SPACING)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(fact, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SecondaryLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
        modifier = modifier,
    )
}

@Preview
@Composable
private fun PlaceSheetPreview() {
    val article = WikiArticle(
        title = "Taj Mahal",
        description = "Mausoleum in Agra, India",
        extract = "The Taj Mahal is an ivory-white marble mausoleum on the right bank of the river Yamuna in Agra.",
        pageUrl = "https://en.wikipedia.org/wiki/Taj_Mahal",
        latitude = 27.175,
        longitude = 78.042,
    )
    ZeitreiseTheme {
        PlaceSheet(
            card = LandmarkCard(
                landmark = Landmark("Taj Mahal", article, GeoPoint(27.175, 78.042)),
                facts = listOf("It was commissioned in 1631 by the Mughal emperor Shah Jahan."),
            ),
            onClose = {},
        )
    }
}
