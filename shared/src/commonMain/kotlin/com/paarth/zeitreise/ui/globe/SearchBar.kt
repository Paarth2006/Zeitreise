package com.paarth.zeitreise.ui.globe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paarth.zeitreise.domain.SearchResult
import com.paarth.zeitreise.ui.SECONDARY_TEXT_ALPHA
import org.jetbrains.compose.resources.stringResource
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.search_cancel
import zeitreise.shared.generated.resources.search_clear
import zeitreise.shared.generated.resources.search_in_country
import zeitreise.shared.generated.resources.search_no_results
import zeitreise.shared.generated.resources.search_placeholder

/** Widest the search bar grows, matching the country sheet on wide screens. */
private val SEARCH_MAX_WIDTH = 480.dp

/** Height of the search field, and of each result row: Apple's minimum touch target and then some. */
private val FIELD_HEIGHT = 48.dp
private val ROW_HEIGHT = 52.dp

/** Padding inside the field and the result rows. */
private val HORIZONTAL_PADDING = 16.dp

/** Gap between the result card and the field below it. */
private val RESULTS_GAP = 8.dp

/** Corner radius of the result card. */
private val RESULTS_CORNER = 16.dp

/** Size of the magnifying glass and of the clear button's cross. */
private val GLYPH_SIZE = 16.dp
private val CLEAR_GLYPH_SIZE = 10.dp

/** Stroke width of the drawn glyphs. */
private val GLYPH_STROKE = 2.dp

/** Minimum touch target for the clear button. */
private val TOUCH_TARGET = 44.dp

/** Opacity of the field and the result card over the globe. */
private const val SURFACE_ALPHA = 0.94f

/** Where the magnifying glass's lens sits and how far its handle reaches, as shares of its size. */
private const val LENS_RADIUS = 0.34f
private const val LENS_CENTER = 0.42f
private const val HANDLE_END = 0.95f

/** Where along the lens's diagonal the handle starts: just outside the rim. */
private const val HANDLE_START_FACTOR = 0.75f

/** Opacity of the hairlines between results. */
private const val DIVIDER_ALPHA = 0.12f

/**
 * The search bar at the bottom of the globe, like Apple Maps: a capsule that searches countries
 * and states as the user types, with the results in a card above it.
 *
 * Stateless: the query and the results are hoisted, so the view model does the searching.
 *
 * @param query the text in the field.
 * @param results the places matching [query], best first.
 * @param onQueryChange called with the new text as the user types.
 * @param onChoose called when the user picks a result.
 * @param onSubmit called when the user presses Search on the keyboard, to go to the best match.
 * @param modifier applied to the root element.
 */
@Composable
internal fun SearchBar(
    query: String,
    results: List<SearchResult>,
    onQueryChange: (String) -> Unit,
    onChoose: (SearchResult) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val choose: (SearchResult) -> Unit = { result ->
        focusManager.clearFocus()
        onChoose(result)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(RESULTS_GAP),
        modifier = modifier.widthIn(max = SEARCH_MAX_WIDTH).fillMaxWidth(),
    ) {
        AnimatedVisibility(visible = focused && query.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
            SearchResults(results = results, onChoose = choose)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {
                    focusManager.clearFocus()
                    onSubmit()
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
            )
            AnimatedVisibility(visible = focused) {
                TextButton(
                    onClick = {
                        onQueryChange("")
                        focusManager.clearFocus()
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.search_cancel),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondary = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA)
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier = modifier.heightIn(min = FIELD_HEIGHT),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HORIZONTAL_PADDING / 2),
            modifier = Modifier.padding(start = HORIZONTAL_PADDING),
        ) {
            MagnifyingGlass(tint = secondary)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                ClearButton(tint = secondary, onClick = { onQueryChange("") })
            } else {
                Box(Modifier.size(HORIZONTAL_PADDING / 2))
            }
        }
    }
}

@Composable
private fun SearchResults(results: List<SearchResult>, onChoose: (SearchResult) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = SURFACE_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(RESULTS_CORNER),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (results.isEmpty()) {
                Text(
                    text = stringResource(Res.string.search_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
                    modifier = Modifier.padding(HORIZONTAL_PADDING),
                )
            }
            val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA)
            results.forEachIndexed { index, result ->
                if (index > 0) HorizontalDivider(color = divider)
                ResultRow(result = result, onClick = { onChoose(result) })
            }
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    val subtitle = when (result) {
        is SearchResult.CountryMatch -> result.country.continent
        is SearchResult.SubdivisionMatch -> stringResource(Res.string.search_in_country, result.country.name)
    }
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = HORIZONTAL_PADDING),
    ) {
        Text(text = result.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_TEXT_ALPHA),
            maxLines = 1,
        )
    }
}

@Composable
private fun MagnifyingGlass(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(GLYPH_SIZE)) {
        val stroke = GLYPH_STROKE.toPx()
        val lens = Offset(size.width * LENS_CENTER, size.height * LENS_CENTER)
        drawCircle(tint, radius = size.minDimension * LENS_RADIUS, center = lens, style = Stroke(stroke))
        val handleStart = lens + Offset(size.width * LENS_RADIUS, size.height * LENS_RADIUS) * HANDLE_START_FACTOR
        drawLine(tint, handleStart, Offset(size.width * HANDLE_END, size.height * HANDLE_END), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ClearButton(tint: Color, onClick: () -> Unit) {
    val label = stringResource(Res.string.search_clear)
    IconButton(onClick = onClick, modifier = Modifier.size(TOUCH_TARGET).semantics { contentDescription = label }) {
        Canvas(modifier = Modifier.size(CLEAR_GLYPH_SIZE)) {
            val stroke = GLYPH_STROKE.toPx()
            drawLine(tint, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(tint, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}
