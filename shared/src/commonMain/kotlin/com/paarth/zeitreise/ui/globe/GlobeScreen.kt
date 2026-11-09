package com.paarth.zeitreise.ui.globe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paarth.zeitreise.domain.Country
import org.koin.compose.viewmodel.koinViewModel

/** Padding around the failure message. */
private val MESSAGE_PADDING = 32.dp

/**
 * The globe screen: the globe itself, and the sheet naming whatever country is selected.
 *
 * @param modifier applied to the root element.
 */
@Composable
internal fun GlobeScreen(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<GlobeViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            is GlobeUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is GlobeUiState.Failed -> FailureMessage(current.message)
            is GlobeUiState.Ready -> ReadyGlobe(state = current, onSelect = viewModel::select)
        }
    }
}

@Composable
private fun BoxScope.ReadyGlobe(state: GlobeUiState.Ready, onSelect: (Country?) -> Unit) {
    GlobeView(
        countries = state.countries,
        selected = state.selected,
        onSelect = onSelect,
    )
    CountrySheet(
        country = state.selected,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}

@Composable
private fun BoxScope.FailureMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(MESSAGE_PADDING),
    )
}
