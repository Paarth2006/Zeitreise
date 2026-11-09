package com.paarth.zeitreise.ui.globe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.Country

/** Corner radius applied to the top corners of the sheet. */
private val SHEET_CORNER = 24.dp

/** Padding inside the sheet. */
private val SHEET_PADDING = 20.dp

/** Spacing between the country name and its continent. */
private val SHEET_SPACING = 4.dp

/** Opacity of the sheet over the globe. */
private const val SHEET_ALPHA = 0.94f

/** Opacity of the continent line, which sits below the name in the hierarchy. */
private const val SUBTITLE_ALPHA = 0.7f

/** Lines the country name may wrap onto before it is ellipsised. */
private const val NAME_MAX_LINES = 2

/**
 * The panel naming the selected country.
 *
 * Slides up when a country is selected and away again when the selection is cleared. Phase 2 fills
 * it with the guide's fact and an Explore button.
 *
 * @param country the selected country, or `null` to hide the sheet.
 * @param modifier applied to the root element.
 */
@Composable
internal fun CountrySheet(country: Country?, modifier: Modifier = Modifier) {
    // The sheet has to keep naming a country while it slides away, after the selection is gone.
    var lastNamed by remember { mutableStateOf<Country?>(null) }
    LaunchedEffect(country) { if (country != null) lastNamed = country }
    val shown = country ?: lastNamed

    AnimatedVisibility(
        visible = country != null,
        enter = slideInVertically { height -> height },
        exit = slideOutVertically { height -> height },
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = SHEET_ALPHA),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(SHEET_PADDING).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SHEET_SPACING),
            ) {
                Text(
                    text = shown?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = NAME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shown?.continent.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA),
                )
            }
        }
    }
}
