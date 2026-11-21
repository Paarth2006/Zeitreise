package com.paarth.zeitreise.ui.globe

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.paarth.zeitreise.domain.Country
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.country_flag

/** Size a flag is drawn in a sheet: three by two, the shape most flags have. */
internal val FLAG_SIZE = DpSize(42.dp, 28.dp)

/** Rounding of a flag's corners, and the hairline around it that keeps a white flag visible on the glass. */
private val FLAG_CORNER = RoundedCornerShape(4.dp)
private val FLAG_BORDER = 0.5.dp

/** Opacity of the hairline around a flag. */
private const val FLAG_BORDER_ALPHA = 0.2f

/**
 * The flag of [country], downloaded from Wikimedia Commons; nothing at all when the country has
 * no flag, and an empty frame until the picture arrives.
 *
 * @param modifier applied to the root element.
 * @param size how large to draw the flag; a flag of another shape is fitted inside it.
 */
@Composable
internal fun CountryFlag(country: Country, modifier: Modifier = Modifier, size: DpSize = FLAG_SIZE) {
    val url = country.facts.flagUrl ?: return
    AsyncImage(
        model = url,
        contentDescription = stringResource(Res.string.country_flag, country.name),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(FLAG_CORNER)
            .border(FLAG_BORDER, MaterialTheme.colorScheme.onSurface.copy(alpha = FLAG_BORDER_ALPHA), FLAG_CORNER),
    )
}
