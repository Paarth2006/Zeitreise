package com.paarth.zeitreise.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.SearchResult
import com.paarth.zeitreise.domain.searchPlaces
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import com.paarth.zeitreise.ui.globe.SearchBar
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.home_body
import zeitreise.shared.generated.resources.home_search
import zeitreise.shared.generated.resources.home_skip
import zeitreise.shared.generated.resources.home_title

/** Maximum width of the content, padding around it and the gap between its parts. */
private val CONTENT_MAX_WIDTH = 480.dp
private val CONTENT_PADDING = 24.dp
private val CONTENT_SPACING = 12.dp

/** Opacity of the background over the globe, the same as the welcome screen. */
private const val SCRIM_ALPHA = 0.72f

/** Few enough results that they fit above the search bar while the keyboard is open. */
private const val MAX_RESULTS = 3

/**
 * Asks which country the user is from, right after the welcome screen. It searches the countries
 * as the user types, and can be skipped.
 *
 * @param countries every country to pick from.
 * @param onChoose called with the country the user picked.
 * @param onSkip called when the user taps Skip, to not say.
 * @param modifier applied to the root element.
 * @param skipLabel the label of the button that leaves without choosing: Skip during onboarding,
 *   Cancel when changing the home country later.
 * @param body the line under the title, which says what choosing does.
 */
@Composable
internal fun HomePicker(
    countries: List<Country>,
    onChoose: (Country) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    skipLabel: StringResource = Res.string.home_skip,
    body: StringResource = Res.string.home_body,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // No states here, since the question is about countries.
    val results = remember(query, countries) { searchPlaces(query, countries, emptyList(), limit = MAX_RESULTS) }
    // A Surface sets the text colour and blocks touches from reaching the globe.
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = SCRIM_ALPHA),
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The safe area includes the keyboard, so the search bar and Skip stay above it.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(CONTENT_PADDING)
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxSize(),
            ) {
                // Skip sits in the corner, so it stays out of the way while the keyboard is open.
                GlassButton(onClick = onSkip, modifier = Modifier.align(Alignment.End)) {
                    Text(text = stringResource(skipLabel), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(Res.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(CONTENT_SPACING))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_TEXT_ALPHA),
                )
                Spacer(Modifier.weight(1f))
                SearchBar(
                    query = query,
                    results = results,
                    onQueryChange = { query = it },
                    onChoose = { onChoose(it.country) },
                    onSubmit = { results.firstOrNull()?.let { onChoose(it.country) } },
                    placeholder = Res.string.home_search,
                )
            }
        }
    }
}

/** The country a search result belongs to. */
private val SearchResult.country: Country
    get() = when (this) {
        is SearchResult.CountryMatch -> country
        is SearchResult.SubdivisionMatch -> country
    }

@Preview
@Composable
private fun HomePickerPreview() {
    ZeitreiseTheme {
        HomePicker(countries = emptyList(), onChoose = {}, onSkip = {})
    }
}
