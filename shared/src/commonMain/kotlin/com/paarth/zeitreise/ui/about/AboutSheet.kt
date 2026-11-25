package com.paarth.zeitreise.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.drawGlobeGlyph
import com.paarth.zeitreise.ui.glass.GLASS_TOUCH_TARGET
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.glass.GlassPanel
import com.paarth.zeitreise.ui.glass.SheetHeader
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
import zeitreise.shared.generated.resources.welcome_tagline

/** Maximum width of the About card. */
private val CARD_MAX_WIDTH = 420.dp

/** Padding, section spacing and corner radius of the card. */
private val CARD_PADDING = 24.dp
private val CARD_SPACING = 12.dp
private val CARD_CORNER = 32.dp

/** How much the globe is dimmed behind the card. */
private const val SCRIM_ALPHA = 0.35f

/** The globe tile in the header: its size, corner, and the globe drawn on it. */
private val TILE_SIZE = 48.dp
private val TILE_CORNER = 14.dp
private val TILE_GLYPH_SIZE = 30.dp
private val TILE_GLYPH_STROKE = 2.dp

/** The dot in front of each credit, and how far down it sits to line up with the first line. */
private val DOT_SIZE = 6.dp
private val DOT_TOP = 7.dp

/** The chevron that opens the credits. Its arms reach from this share of the height to the rest. */
private val CHEVRON_SIZE = 14.dp
private val CHEVRON_STROKE = 2.dp
private const val CHEVRON_TOP = 0.3f
private const val HALF_TURN_DEGREES = 180f

/** Every credit the licences ask for, in the order shown. */
private val CREDITS = listOf(
    Res.string.about_credit_wikipedia,
    Res.string.about_credit_natural_earth,
    Res.string.about_credit_history,
    Res.string.about_credit_flags,
    Res.string.about_credit_ai,
)

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
 * @param modifier applied to the root element.
 * @param onTakeTour called when the user asks to see the tour again. The card closes first.
 * @param account what the account section shows, if this build has sync.
 * @param accountActions what the account section's buttons and fields do.
 */
@Composable
internal fun AboutSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTakeTour: () -> Unit = {},
    account: AccountUiState = AccountUiState.Hidden,
    accountActions: AccountActions = AccountActions(),
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
                AboutContent(onDismiss = onDismiss, onTakeTour = onTakeTour) {
                    AccountSection(state = account, actions = accountActions)
                }
            }
        }
    }
}

/**
 * The card's content: a header that stays put, and below it what scrolls on short screens: the
 * intro, the account, the tour and the credits.
 */
@Composable
private fun AboutContent(
    onDismiss: () -> Unit,
    onTakeTour: () -> Unit,
    modifier: Modifier = Modifier,
    account: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.padding(CARD_PADDING)) {
        SheetHeader(
            title = stringResource(Res.string.about_title),
            subtitle = stringResource(Res.string.welcome_tagline),
            closeLabel = stringResource(Res.string.about_close),
            onClose = onDismiss,
            leading = { GlobeTile() },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(top = CARD_SPACING),
        ) {
            Text(text = stringResource(Res.string.about_body), style = MaterialTheme.typography.bodyLarge)
            account()
            GlassButton(
                onClick = {
                    onDismiss()
                    onTakeTour()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.tour_replay), style = MaterialTheme.typography.titleMedium)
            }
            Credits()
        }
    }
}

/** The app's mark: a globe on a tile in the accent colour. */
@Composable
private fun GlobeTile() {
    val tint = MaterialTheme.colorScheme.onPrimary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(TILE_SIZE)
            .clip(RoundedCornerShape(TILE_CORNER))
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Canvas(modifier = Modifier.size(TILE_GLYPH_SIZE)) { drawGlobeGlyph(tint, TILE_GLYPH_STROKE) }
    }
}

/** Where the data comes from, as the licences ask. Folded away until the user opens it. */
@Composable
private fun Credits() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) HALF_TURN_DEGREES else 0f)
    Column(verticalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = GLASS_TOUCH_TARGET)
                .clip(RoundedCornerShape(TILE_CORNER))
                .clickable(role = Role.Button) { expanded = !expanded },
        ) {
            Text(
                text = stringResource(Res.string.about_credits_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Chevron(modifier = Modifier.rotate(turn))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
                CREDITS.forEach { CreditRow(stringResource(it)) }
            }
        }
    }
}

/** One credit, with a dot in the accent colour in front of it. */
@Composable
private fun CreditRow(text: String) {
    val dot = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
        Canvas(modifier = Modifier.padding(top = DOT_TOP).size(DOT_SIZE)) { drawCircle(dot) }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
        )
    }
}

/** A downward chevron, drawn by hand like the close button. It's turned upwards when open. */
@Composable
private fun Chevron(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA)
    Canvas(modifier = modifier.size(CHEVRON_SIZE)) {
        val stroke = CHEVRON_STROKE.toPx()
        val top = size.height * CHEVRON_TOP
        val bottom = size.height * (1 - CHEVRON_TOP)
        drawLine(tint, Offset(0f, top), Offset(size.width / 2, bottom), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width / 2, bottom), Offset(size.width, top), stroke, StrokeCap.Round)
    }
}

@Preview
@Composable
private fun AboutSheetPreview() {
    ZeitreiseTheme {
        AboutSheet(visible = true, onDismiss = {})
    }
}
