package com.paarth.zeitreise.ui.passport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.ContinentProgress
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.PassportBook
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassCircleButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import com.paarth.zeitreise.ui.glass.SheetHeader
import com.paarth.zeitreise.ui.globe.CountryFlag
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.passport_close
import zeitreise.shared.generated.resources.passport_continent
import zeitreise.shared.generated.resources.passport_empty
import zeitreise.shared.generated.resources.passport_open
import zeitreise.shared.generated.resources.passport_progress
import zeitreise.shared.generated.resources.passport_title

/** Widest the passport card grows, its padding, the gap between its sections and its corner radius. */
private val CARD_MAX_WIDTH = 460.dp
private val CARD_PADDING = 24.dp
private val CARD_SPACING = 16.dp
private val CARD_CORNER = 32.dp

/** How dark the globe dims behind the card. */
private const val SCRIM_ALPHA = 0.35f

/** Scale the card springs up from. */
private const val CARD_START_SCALE = 0.88f

private val CARD_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** Size of a stamp, the flag inside it, the gap between stamps and the dashes of its rim. */
private val STAMP_WIDTH = 92.dp
private val STAMP_FLAG = DpSize(48.dp, 32.dp)
private val STAMP_GAP = 10.dp
private val STAMP_PADDING = 8.dp
private val STAMP_RIM = 1.5.dp
private val STAMP_DASH = 5.dp
private val STAMP_CORNER = 10.dp

/** Most a stamp is tilted either way, in degrees, as if pressed on by hand. */
private const val STAMP_MAX_TILT = 4

/** The inks stamps are pressed in, one per continent, deep enough to read on the light glass. */
private val STAMP_INKS = listOf(
    Color(0xFF1F5FBF),
    Color(0xFFB5452E),
    Color(0xFF2E7D4F),
    Color(0xFF7A3FA0),
    Color(0xFFA86A12),
    Color(0xFF0F7C86),
    Color(0xFF8A3A5C),
    Color(0xFF4A5A6A),
)

/** Height of a progress bar, and the opacity of its unfilled track. */
private val PROGRESS_HEIGHT = 6.dp
private const val TRACK_ALPHA = 0.12f

/** Size and stroke of the passport drawn in its button. */
private val GLYPH_SIZE = DpSize(15.dp, 19.dp)
private val GLYPH_STROKE = 2.dp

/** Radius of the globe on the passport's cover, as a share of the glyph's width. */
private const val GLYPH_GLOBE = 0.26f

/**
 * The round glass button that opens the passport, showing a passport with a globe on its cover.
 *
 * @param onClick called when the button is tapped.
 * @param modifier applied to the root element.
 */
@Composable
internal fun PassportButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.passport_open)
    val tint = MaterialTheme.colorScheme.primary
    GlassCircleButton(onClick = onClick, modifier = modifier.semantics { contentDescription = label }) {
        Canvas(modifier = Modifier.size(GLYPH_SIZE)) {
            val stroke = Stroke(width = GLYPH_STROKE.toPx())
            val corner = CornerRadius(stroke.width * 2)
            drawRoundRect(tint, cornerRadius = corner, style = stroke)
            drawCircle(tint, radius = size.width * GLYPH_GLOBE, style = stroke)
        }
    }
}

/**
 * The passport: how many countries the user has stamped, how far they have got on each continent,
 * and a stamp for every country they have opened, the latest first.
 *
 * A glass card over the dimmed globe, springing in like the About card.
 *
 * @param book the passport while it is open; `null` while it is closed.
 * @param onDismiss called when the passport is closed, by its button or a tap outside it.
 * @param modifier applied to the root element.
 */
@Composable
internal fun PassportSheet(book: PassportBook?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // The card keeps showing its stamps while it fades away, after the passport is closed.
    var shown by remember { mutableStateOf(book) }
    LaunchedEffect(book) { if (book != null) shown = book }
    AnimatedVisibility(visible = book != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier.fillMaxSize()) {
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
                    // Taps on the card itself must not fall through to the scrim and close it.
                    .clickable(interactionSource = null, indication = null, onClick = {}),
            ) {
                shown?.let { PassportContent(it, onDismiss) }
            }
        }
    }
}

@Composable
private fun PassportContent(book: PassportBook, onDismiss: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(CARD_PADDING),
    ) {
        SheetHeader(
            title = stringResource(Res.string.passport_title),
            subtitle = stringResource(Res.string.passport_progress, book.stamped.size, book.total),
            closeLabel = stringResource(Res.string.passport_close),
            onClose = onDismiss,
        )
        ProgressBar(fraction = book.stamped.size.toFloat() / book.total.coerceAtLeast(1))
        if (book.stamped.isEmpty()) {
            SecondaryText(stringResource(Res.string.passport_empty))
        } else {
            Stamps(book.stamped)
            book.continents.forEach { ContinentRow(it) }
        }
    }
}

/** Every stamp in the passport, in rows that wrap. */
@Composable
private fun Stamps(countries: List<Country>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(STAMP_GAP),
        verticalArrangement = Arrangement.spacedBy(STAMP_GAP),
    ) {
        countries.forEach { Stamp(it) }
    }
}

/**
 * One country's stamp: its flag and name inside a dashed rim, in its continent's ink, tilted a
 * little, always the same way for the same country, as if pressed on by hand.
 */
@Composable
private fun Stamp(country: Country) {
    val ink = STAMP_INKS[country.continent.hashCode().mod(STAMP_INKS.size)]
    val tilt = country.name.hashCode().mod(STAMP_MAX_TILT * 2 + 1) - STAMP_MAX_TILT
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(STAMP_PADDING / 2),
        modifier = Modifier
            .width(STAMP_WIDTH)
            .rotate(tilt.toFloat())
            .drawBehind {
                val dash = STAMP_DASH.toPx()
                drawRoundRect(
                    color = ink,
                    cornerRadius = CornerRadius(STAMP_CORNER.toPx()),
                    style = Stroke(
                        width = STAMP_RIM.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(
                                dash,
                                dash / 2,
                            ),
                        ),
                    ),
                )
            }
            .padding(STAMP_PADDING),
    ) {
        CountryFlag(country, size = STAMP_FLAG)
        Text(
            text = country.name,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How many of a continent's countries are stamped, in words and as a bar. */
@Composable
private fun ContinentRow(progress: ContinentProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(STAMP_PADDING / 2)) {
        Row {
            Text(
                text = progress.continent,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            SecondaryText(stringResource(Res.string.passport_continent, progress.stamped, progress.total))
        }
        ProgressBar(fraction = progress.stamped.toFloat() / progress.total.coerceAtLeast(1))
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA),
        drawStopIndicator = {},
        gapSize = 0.dp,
        modifier = Modifier.fillMaxWidth().height(PROGRESS_HEIGHT),
    )
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
private fun EmptyPassportPreview() {
    ZeitreiseTheme {
        PassportContent(book = PassportBook(stamped = emptyList(), total = 242, continents = emptyList()), onDismiss = {
        })
    }
}
