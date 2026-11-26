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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.paarth.zeitreise.ui.glass.GlassPanel
import com.paarth.zeitreise.ui.glass.SheetHeader
import com.paarth.zeitreise.ui.globe.CountryFlag
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.passport_close
import zeitreise.shared.generated.resources.passport_continent
import zeitreise.shared.generated.resources.passport_empty
import zeitreise.shared.generated.resources.passport_holder
import zeitreise.shared.generated.resources.passport_holder_hint
import zeitreise.shared.generated.resources.passport_home
import zeitreise.shared.generated.resources.passport_home_change
import zeitreise.shared.generated.resources.passport_home_choose
import zeitreise.shared.generated.resources.passport_progress
import zeitreise.shared.generated.resources.passport_title

/** Max width, padding, section spacing and corner radius of the card. */
private val CARD_MAX_WIDTH = 460.dp
private val CARD_PADDING = 24.dp
private val CARD_SPACING = 16.dp
private val CARD_CORNER = 32.dp

/** How much the globe is dimmed behind the card. */
private const val SCRIM_ALPHA = 0.35f

/** Starting scale of the card animation. */
private const val CARD_START_SCALE = 0.88f

private val CARD_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** Stamp size, flag size, spacing and border style. */
private val STAMP_WIDTH = 92.dp
private val STAMP_FLAG = DpSize(48.dp, 32.dp)
private val STAMP_GAP = 10.dp
private val STAMP_PADDING = 8.dp
private val STAMP_RIM = 1.5.dp
private val STAMP_DASH = 5.dp
private val STAMP_CORNER = 10.dp

/** Maximum tilt of a stamp in degrees. */
private const val STAMP_MAX_TILT = 4

/** Stamp colours, one per continent. */
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

/** Size of the home country's flag, and width of the line the name is written on. */
private val HOME_FLAG = DpSize(24.dp, 16.dp)
private val NAME_LINE = 1.dp

/** The arrow next to the home country: its size, line width, and how far its point is from the sides. */
private val CHEVRON_SIZE = 10.dp
private val CHEVRON_STROKE = 1.5.dp
private const val CHEVRON_INSET = 0.25f

/** Progress bar height and track opacity. */
private val PROGRESS_HEIGHT = 6.dp
private const val TRACK_ALPHA = 0.12f

/**
 * The passport card: total progress, a stamp for each opened country (newest first) and progress
 * per continent.
 *
 * @param book the passport, or `null` when it's closed.
 * @param onDismiss called when the card is closed.
 * @param onRename called with the holder's name as the user types it.
 * @param modifier applied to the root element.
 * @param onChangeHome called when the user taps the home country, to pick another one.
 */
@Composable
internal fun PassportSheet(
    book: PassportBook?,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
    onChangeHome: () -> Unit = {},
) {
    // Keep showing the last content while the card fades out.
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
                    // Stop taps on the card from reaching the background and closing it.
                    .clickable(interactionSource = null, indication = null, onClick = {}),
            ) {
                // The book updates as the user types the name, so show it rather than the copy kept for fading out.
                (book ?: shown)?.let { PassportContent(it, onDismiss, onRename, onChangeHome) }
            }
        }
    }
}

@Composable
private fun PassportContent(
    book: PassportBook,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onChangeHome: () -> Unit = {},
) {
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
        HolderRow(holder = book.holder.orEmpty(), home = book.home, onRename = onRename, onChangeHome = onChangeHome)
        ProgressBar(fraction = book.stamped.size.toFloat() / book.total.coerceAtLeast(1))
        if (book.stamped.isEmpty()) {
            SecondaryText(stringResource(Res.string.passport_empty))
        } else {
            Stamps(book.stamped)
            book.continents.forEach { ContinentRow(it) }
        }
    }
}

/**
 * The holder's name, written on a line like in a real passport, and their home country. Tapping
 * the home country picks another one.
 */
@Composable
private fun HolderRow(holder: String, home: Country?, onRename: (String) -> Unit, onChangeHome: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
        Column(modifier = Modifier.weight(1f)) {
            Label(stringResource(Res.string.passport_holder))
            NameField(holder, onRename)
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .clip(RoundedCornerShape(STAMP_CORNER))
                .clickable(role = Role.Button, onClickLabel = stringResource(Res.string.passport_home_change)) {
                    onChangeHome()
                },
        ) {
            Label(stringResource(Res.string.passport_home))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(STAMP_PADDING / 2),
            ) {
                if (home != null) {
                    CountryFlag(home, size = HOME_FLAG)
                    Text(text = home.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                } else {
                    Text(
                        text = stringResource(Res.string.passport_home_choose),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Chevron()
            }
        }
    }
}

/** A small arrow that says the home country can be changed. */
@Composable
private fun Chevron() {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA)
    Canvas(modifier = Modifier.size(CHEVRON_SIZE)) {
        val stroke = CHEVRON_STROKE.toPx()
        drawLine(
            tint,
            Offset(size.width * CHEVRON_INSET, 0f),
            Offset(size.width * (1 - CHEVRON_INSET), size.height / 2),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(size.width * (1 - CHEVRON_INSET), size.height / 2),
            Offset(size.width * CHEVRON_INSET, size.height),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun NameField(holder: String, onRename: (String) -> Unit) {
    // Typed into here first and saved along the way, so fast typing never waits on the saved copy.
    var name by rememberSaveable { mutableStateOf(holder) }
    val focusManager = LocalFocusManager.current
    val secondary = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA)
    val label = stringResource(Res.string.passport_holder)
    Box(
        modifier = Modifier.fillMaxWidth().drawBehind {
            val y = size.height - NAME_LINE.toPx() / 2
            drawLine(secondary, Offset(0f, y), Offset(size.width, y), NAME_LINE.toPx())
        },
    ) {
        if (name.isEmpty()) {
            Text(
                text = stringResource(Res.string.passport_holder_hint),
                style = MaterialTheme.typography.titleMedium,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = name,
            onValueChange = { typed ->
                name = typed.take(MAX_HOLDER_LENGTH)
                onRename(name)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            // No autocorrect: it would turn a name like Paarth into "Party".
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

/** A small caption above a value on the passport. */
@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
    )
}

/** All the stamps, in wrapping rows. */
@Composable
private fun Stamps(countries: List<Country>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(STAMP_GAP),
        verticalArrangement = Arrangement.spacedBy(STAMP_GAP),
    ) {
        countries.forEach { Stamp(it) }
    }
}

/** One stamp: the flag and name in a dashed border. The colour depends on the continent and the tilt on the name. */
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

/** A continent's progress as text and a bar. */
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
        PassportContent(
            book = PassportBook(stamped = emptyList(), total = 242, continents = emptyList(), holder = "Ada Lovelace"),
            onDismiss = {},
            onRename = {},
        )
    }
}
