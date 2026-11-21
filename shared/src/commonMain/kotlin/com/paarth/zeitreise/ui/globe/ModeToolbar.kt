package com.paarth.zeitreise.ui.globe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.CapsuleShape
import com.paarth.zeitreise.ui.glass.GLASS_TOUCH_TARGET
import com.paarth.zeitreise.ui.glass.GlassPanel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.ic_about
import zeitreise.shared.generated.resources.ic_passport
import zeitreise.shared.generated.resources.ic_quiz
import zeitreise.shared.generated.resources.ic_time_travel
import zeitreise.shared.generated.resources.toolbar_about
import zeitreise.shared.generated.resources.toolbar_passport
import zeitreise.shared.generated.resources.toolbar_quiz
import zeitreise.shared.generated.resources.toolbar_time_travel

/** Maximum width of the toolbar, the same as the search bar. */
private val TOOLBAR_MAX_WIDTH = 480.dp

/** Padding inside the toolbar and inside each item. */
private val TOOLBAR_PADDING = 6.dp
private val ITEM_PADDING = 6.dp

/** Size of each icon and the gap between icon and label. */
private val ICON_SIZE = 24.dp
private val ICON_LABEL_GAP = 2.dp

/**
 * Actions for the toolbar.
 *
 * @property startTimeTravel turns the globe into a map of the past.
 * @property startQuiz starts a round of the quiz.
 * @property showPassport opens the passport.
 * @property showAbout opens the About card.
 */
internal class ModeActions(
    val startTimeTravel: () -> Unit,
    val startQuiz: () -> Unit,
    val showPassport: () -> Unit,
    val showAbout: () -> Unit,
)

/**
 * A glass toolbar with labelled buttons for the app's main features, shown above the search bar.
 * Each item has an icon and a short label so it's clear what it does.
 *
 * @param actions what each button does.
 * @param modifier applied to the root element.
 * @param enabled whether the buttons react to taps. It's `false` while the toolbar animates away, so
 *   a quick second tap can't start another feature.
 */
@Composable
internal fun ModeToolbar(actions: ModeActions, modifier: Modifier = Modifier, enabled: Boolean = true) {
    GlassPanel(shape = CapsuleShape, modifier = modifier.widthIn(max = TOOLBAR_MAX_WIDTH).fillMaxWidth()) {
        Row(modifier = Modifier.padding(TOOLBAR_PADDING)) {
            ToolbarItem(Res.drawable.ic_time_travel, Res.string.toolbar_time_travel, enabled, actions.startTimeTravel)
            ToolbarItem(Res.drawable.ic_quiz, Res.string.toolbar_quiz, enabled, actions.startQuiz)
            ToolbarItem(Res.drawable.ic_passport, Res.string.toolbar_passport, enabled, actions.showPassport)
            ToolbarItem(Res.drawable.ic_about, Res.string.toolbar_about, enabled, actions.showAbout)
        }
    }
}

/** One toolbar button: an icon above its label. */
@Composable
private fun RowScope.ToolbarItem(icon: DrawableResource, label: StringResource, enabled: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ICON_LABEL_GAP),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = GLASS_TOUCH_TARGET)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(ITEM_PADDING),
    ) {
        // The label below already describes the button, so the icon itself needs no description.
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(ICON_SIZE))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun ModeToolbarPreview() {
    ZeitreiseTheme {
        ModeToolbar(actions = ModeActions({}, {}, {}, {}))
    }
}
