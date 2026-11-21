package com.paarth.zeitreise.ui.globe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.ui.PreviewSamples
import com.paarth.zeitreise.ui.ZeitreiseTheme
import com.paarth.zeitreise.ui.glass.GlassButton
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.country_of_the_day
import zeitreise.shared.generated.resources.country_of_the_day_label

/** Size of the flag inside the chip, and the gap between it and the name. */
private val CHIP_FLAG = DpSize(24.dp, 16.dp)
private val CHIP_GAP = 8.dp

/**
 * A glass chip naming today's country, with its flag, which opens it when tapped: a reason to come
 * back to the globe tomorrow.
 *
 * @param country the country of the day.
 * @param onOpen called with [country] when the chip is tapped.
 * @param modifier applied to the root element.
 */
@Composable
internal fun CountryOfTheDayChip(country: Country, onOpen: (Country) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.country_of_the_day_label, country.name)
    GlassButton(
        onClick = { onOpen(country) },
        // Read as one button, "Country of the day: Peru", rather than as a flag and a name.
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
            CountryFlag(country, size = CHIP_FLAG)
            Text(
                text = stringResource(Res.string.country_of_the_day, country.name),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@Composable
private fun CountryOfTheDayChipPreview() {
    ZeitreiseTheme {
        CountryOfTheDayChip(country = PreviewSamples.india, onOpen = {})
    }
}
