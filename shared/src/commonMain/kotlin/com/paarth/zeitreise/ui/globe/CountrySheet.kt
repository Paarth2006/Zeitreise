package com.paarth.zeitreise.ui.globe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.ui.PreviewSamples
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassSheet
import com.paarth.zeitreise.ui.glass.SHEET_BODY_END_PADDING
import com.paarth.zeitreise.ui.glass.SheetHeader
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.close_country
import zeitreise.shared.generated.resources.country_capitals
import zeitreise.shared.generated.resources.passport_new_stamp

/**
 * The glass sheet for the selected country (or state), with [content] below the header.
 *
 * @param country the selected country, or `null` to hide the sheet.
 * @param subdivision the selected state inside [country], if any.
 * @param newStamp whether [country] was just stamped in the passport for the first time.
 * @param onClose called when the close button is tapped.
 * @param modifier applied to the root element.
 */
@Composable
internal fun CountrySheet(
    country: Country?,
    subdivision: Subdivision?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    newStamp: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Keep showing the last country while the sheet slides away.
    var lastNamed by remember { mutableStateOf<Country?>(null) }
    LaunchedEffect(country) { if (country != null) lastNamed = country }
    val shown = country ?: lastNamed

    GlassSheet(visible = country != null, modifier = modifier) {
        SheetHeader(
            title = subdivision?.name ?: shown?.name.orEmpty(),
            subtitle = subdivision?.subtitleIn(shown) ?: shown?.let { countrySubtitle(it) }.orEmpty(),
            closeLabel = stringResource(Res.string.close_country),
            onClose = onClose,
            leading = shown?.let { country -> { CountryFlag(country) } },
        )
        AnimatedVisibility(visible = newStamp && subdivision == null) {
            Text(
                text = stringResource(Res.string.passport_new_stamp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        content()
    }
}

/** Subtitle like "South America · Capital Lima", or just the continent if there is no capital. */
@Composable
private fun countrySubtitle(country: Country): String {
    val capitals = country.facts.capitals
    if (capitals.isEmpty()) return country.continent
    val capitalText = pluralStringResource(Res.plurals.country_capitals, capitals.size, capitals.joinToString())
    return "${country.continent} · $capitalText"
}

/** Subtitle like "State · India", or just the country name if the kind is unknown. */
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
