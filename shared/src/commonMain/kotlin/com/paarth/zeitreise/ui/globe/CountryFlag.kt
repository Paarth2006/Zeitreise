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

/** Default flag size, 3:2 like most flags. */
internal val FLAG_SIZE = DpSize(42.dp, 28.dp)

/** Corner radius and border width. The border keeps white flags visible on the glass. */
private val FLAG_CORNER = RoundedCornerShape(4.dp)
private val FLAG_BORDER = 0.5.dp

/** Opacity of the border. */
private const val FLAG_BORDER_ALPHA = 0.2f

/**
 * Shows the flag of [country], loaded from Wikimedia Commons. Shows nothing if the country has no flag.
 *
 * @param modifier applied to the root element.
 * @param size the size to draw the flag at.
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
