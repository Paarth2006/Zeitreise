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

/** Minimum touch target size. */
internal val GLASS_TOUCH_TARGET = 44.dp

/** Padding inside a glass button. */
private val BUTTON_PADDING_HORIZONTAL = 18.dp
private val BUTTON_PADDING_VERTICAL = 10.dp

/** Scale of a button while pressed. */
private const val PRESSED_SCALE = 0.94f

/** How much a button brightens while pressed. */
private const val PRESSED_BRIGHTENING = 0.12f

/** Keeps the glass light even in dark mode, since the text on it is always black. */
private const val LIGHT_WHITE_POINT = 0.2f

/** Opacity of the accent tint on prominent buttons. */
private const val PROMINENT_TINT_ALPHA = 0.82f

/** Spring used for the press animation. */
private val PRESS_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** White tint over the glass so black text stays readable on top of the globe. */
private val PANEL_TINT = Color.White.copy(alpha = 0.78f)

/** Solid colour used instead of glass when there's nothing behind to blur, like in previews. */
private val PANEL_FALLBACK = Color(0xFFF2F2F7)

/** The Haze state the glass blurs (the globe). `null` outside the globe screen, which uses a solid fill. */
internal val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** A capsule shape for buttons and search fields. */
internal val CapsuleShape = RoundedCornerShape(percent = 50)

/**
 * A glass panel holding [content], with black text.
 *
 * @param shape the panel's shape.
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
    // Pass min constraints on so content can fill a panel with a minimum height.
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
 * A glass button that shrinks and brightens when pressed.
 *
 * @param onClick called when the button is tapped.
 * @param modifier applied to the root element.
 * @param prominent whether it's the main action: tinted with the accent colour with white text.
 *   Otherwise it's clear glass with accent-coloured text.
 * @param shape the button's shape: a capsule for text or a circle for an icon.
 * @param contentPadding padding around the content.
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

/** A round glass button for an icon, like close or info. */
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

/** Draws glass that blurs the globe behind it, or a plain [fallback] fill if there is no globe. */
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
            // A white tint on top keeps the text readable over both space and the globe.
            .background(frost, shape)
    } else {
        clip(shape).background(fallback)
    }
}
