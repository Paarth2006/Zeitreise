package com.paarth.zeitreise.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass

/** Minimum touch target the Human Interface Guidelines ask for. */
internal val GLASS_TOUCH_TARGET = 44.dp

/** Padding inside a glass button, either side of its label. */
private val BUTTON_PADDING_HORIZONTAL = 18.dp
private val BUTTON_PADDING_VERTICAL = 10.dp

/** How far a glass button shrinks while pressed, as Apple's controls do. */
private const val PRESSED_SCALE = 0.94f

/** How much a glass button brightens while pressed, as if lit by the finger. */
private const val PRESSED_BRIGHTENING = 0.12f

/**
 * Lifts the glass's white point so it stays light glass even when the system is in dark mode,
 * where the default style would darken it: the labels on it are always black.
 */
private const val LIGHT_WHITE_POINT = 0.2f

/** Opacity of the accent colour tinting a prominent button's glass. */
private const val PROMINENT_TINT_ALPHA = 0.82f

/** A soft, quick spring for press responses, close to how Apple's controls give under a finger. */
private val PRESS_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

/**
 * Milky white over the glass, so black text stays legible over the bright blues and greens of
 * the globe, as Apple's light Liquid Glass does over a map.
 */
private val PANEL_TINT = Color.White.copy(alpha = 0.78f)

/**
 * What shows instead of glass where there is nothing to refract, such as in a preview or on a card
 * over other glass: Apple's opaque grouped background colour.
 */
private val PANEL_FALLBACK = Color(0xFFF2F2F7)

/**
 * The globe as the content behind the glass: every glass control samples it, bends it at its
 * rim and blurs it, so the controls read as floating panes over a living world. `null` outside
 * the globe screen, where the controls fall back to a plain frosted fill.
 */
internal val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** A capsule, the shape Apple gives buttons and search fields in Liquid Glass. */
internal val CapsuleShape = RoundedCornerShape(percent = 50)

/**
 * A pane of Liquid Glass holding [content], with black content colour for legibility.
 *
 * @param shape the pane's outline; panels use large, continuous corners.
 * @param modifier applied to the root element.
 */
@Composable
internal fun GlassPanel(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = remember(shape) {
        GlassStyle.regular.then {
            shape(shape)
            whitePoint(LIGHT_WHITE_POINT)
        }
    }
    // Passing the minimum size on lets content fill a panel given a minimum height, so it can centre itself.
    Box(
        propagateMinConstraints = true,
        modifier = modifier.glass(style, shape, frost = PANEL_TINT, fallback = PANEL_FALLBACK),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            content = { content() },
        )
    }
}

/**
 * A Liquid Glass button: a capsule that squeezes and lights up under the finger.
 *
 * @param onClick called when the button is tapped.
 * @param modifier applied to the root element.
 * @param prominent whether this is the action the user is most likely to want: tinted with the
 *   accent colour and labelled in white, like Apple's prominent glass buttons. Otherwise clear
 *   glass with an accent-coloured label.
 * @param shape the button's outline: a capsule for text, a circle for a single glyph.
 * @param contentPadding space around the label, smaller for a glyph-only button.
 */
@Composable
internal fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    shape: RoundedCornerShape = CapsuleShape,
    contentPadding: Dp = BUTTON_PADDING_HORIZONTAL,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    val style = remember(shape) {
        GlassStyle.regular.then {
            shape(shape)
            whitePoint(LIGHT_WHITE_POINT)
            pressed {
                animate(toSpec = PRESS_SPRING, fromSpec = PRESS_SPRING) {
                    scale(PRESSED_SCALE)
                    whitePointDelta(PRESSED_BRIGHTENING)
                }
            }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = GLASS_TOUCH_TARGET, minHeight = GLASS_TOUCH_TARGET)
            .glass(
                style = style,
                shape = shape,
                frost = if (prominent) accent.copy(alpha = PROMINENT_TINT_ALPHA) else PANEL_TINT,
                fallback = if (prominent) accent else PANEL_FALLBACK,
                interactionSource = interactions,
            )
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = contentPadding, vertical = BUTTON_PADDING_VERTICAL),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (prominent) Color.White else accent,
            content = { content() },
        )
    }
}

/** A round glass button for a single glyph, such as close or info. */
@Composable
internal fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.size(GLASS_TOUCH_TARGET),
        shape = CircleShape,
        contentPadding = 0.dp,
        content = content,
    )
}

/**
 * Draws Liquid Glass sampling the globe behind, or, where there is no globe to sample, a plain
 * frosted [fallback] fill in the same [shape], so previews and dialogs still look right.
 */
@Composable
private fun Modifier.glass(
    style: GlassStyle,
    shape: RoundedCornerShape,
    frost: Color,
    fallback: Color,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    return if (backdrop != null) {
        hazeGlass(
            input = HazeInput.Sources(backdrop),
            style = style,
            interactionSource = interactionSource,
            interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
        )
            // A frosted layer over the refraction keeps labels legible over black space as well
            // as over the bright globe, the job Apple's glass does by adapting to what is behind.
            .background(frost, shape)
    } else {
        clip(shape).background(fallback)
    }
}
