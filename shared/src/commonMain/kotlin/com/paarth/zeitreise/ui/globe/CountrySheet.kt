package com.paarth.zeitreise.ui.globe

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.ui.PreviewSamples
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassSheet
import com.paarth.zeitreise.ui.glass.SHEET_BODY_END_PADDING
import com.paarth.zeitreise.ui.glass.SheetHeader
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.close_country

/**
 * The glass sheet naming the selected country, or the state tapped inside it, above [content]:
 * what the guide has to say and the places to explore.
 *
 * @param country the selected country, or `null` to hide the sheet.
 * @param subdivision the state tapped inside [country], which the sheet names instead, if any.
 * @param onClose called when the close button is tapped.
 * @param modifier applied to the root element.
 */
@Composable
internal fun CountrySheet(
    country: Country?,
    subdivision: Subdivision?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The sheet has to keep naming a country while it slides away, after the selection is gone.
    var lastNamed by remember { mutableStateOf<Country?>(null) }
    LaunchedEffect(country) { if (country != null) lastNamed = country }
    val shown = country ?: lastNamed

    GlassSheet(visible = country != null, modifier = modifier) {
        SheetHeader(
            title = subdivision?.name ?: shown?.name.orEmpty(),
            subtitle = subdivision?.subtitleIn(shown) ?: shown?.continent.orEmpty(),
            closeLabel = stringResource(Res.string.close_country),
            onClose = onClose,
        )
        content()
    }
}

/** "State · India", or just the country's name where the state's kind is unknown. */
private fun Subdivision.subtitleIn(country: Country?): String {
    val countryName = country?.name.orEmpty()
    return if (kind.isBlank()) countryName else "$kind · $countryName"
}

@Preview
@Composable
private fun CountrySheetPreview() {
    ZeitreiseTheme {
        CountrySheet(country = PreviewSamples.india, subdivision = null, onClose = {}) {
            StoryPanel(
                story = StoryState.Loading,
                onRetry = {},
                onNextFact = {},
                modifier = Modifier.padding(end = SHEET_BODY_END_PADDING),
            )
        }
    }
}

@Preview
@Composable
private fun StateSheetPreview() {
    ZeitreiseTheme {
        CountrySheet(country = PreviewSamples.india, subdivision = PreviewSamples.kerala, onClose = {}) {
            StoryPanel(
                story = StoryState.NoArticle,
                onRetry = {},
                onNextFact = {},
                modifier = Modifier.padding(end = SHEET_BODY_END_PADDING),
            )
        }
    }
}
