package com.paarth.zeitreise.ui.globe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.SearchResult
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.ui.about.AboutSheet
import com.paarth.zeitreise.ui.about.InfoButton
import com.paarth.zeitreise.ui.onboarding.CoachHint
import com.paarth.zeitreise.ui.onboarding.OnboardingEvent
import com.paarth.zeitreise.ui.onboarding.OnboardingStep
import com.paarth.zeitreise.ui.onboarding.OnboardingViewModel
import com.paarth.zeitreise.ui.onboarding.WelcomeOverlay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.loading_world

/** Padding around the failure message. */
private val MESSAGE_PADDING = 32.dp

/** Gap between the floating controls and the edges of the safe area. */
private val CONTROL_MARGIN = 16.dp

/** Gap between the onboarding hint and the bottom of the safe area. */
private val HINT_BOTTOM_PADDING = 24.dp

/** Milliseconds the globe takes to grow and fade in when it first appears. */
private const val INTRO_MILLIS = 1_200

/** Scale the globe grows from as it appears. */
private const val INTRO_START_SCALE = 0.82f

/**
 * The globe screen: the globe itself, the sheet naming whatever country is selected, and the
 * onboarding on first launch.
 *
 * The globe fills the whole window, under the system bars, while text and controls stay inside the
 * safe area, as the Human Interface Guidelines ask of full-bleed content.
 *
 * @param modifier applied to the root element.
 */
@Composable
internal fun GlobeScreen(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<GlobeViewModel>()
    val onboarding = koinViewModel<OnboardingViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val step by onboarding.step.collectAsStateWithLifecycle()
    val loadingLabel = stringResource(Res.string.loading_world)
    val actions = GlobeScreenActions(
        selectCountry = { country ->
            viewModel.select(country)
            if (country != null) onboarding.onEvent(OnboardingEvent.CountrySelected)
        },
        selectSubdivision = viewModel::selectSubdivision,
        search = viewModel::search,
        choose = { result ->
            viewModel.choose(result)
            onboarding.onEvent(OnboardingEvent.CountrySelected)
        },
        submitSearch = { query ->
            viewModel.submitSearch(query)
            onboarding.onEvent(OnboardingEvent.CountrySelected)
        },
        onboarding = onboarding::onEvent,
    )

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            is GlobeUiState.Loading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = loadingLabel },
            )
            is GlobeUiState.Failed -> FailureMessage(current.message)
            is GlobeUiState.Ready -> {
                ReadyGlobe(state = current, actions = actions)
                Controls(state = current, step = step, searchResults = searchResults, actions = actions)
            }
        }
    }
}

/** What the globe screen's parts can ask for, gathered so each takes one parameter rather than six. */
private class GlobeScreenActions(
    val selectCountry: (Country?) -> Unit,
    val selectSubdivision: (Subdivision?) -> Unit,
    val search: (String) -> Unit,
    val choose: (SearchResult) -> Unit,
    val submitSearch: (String) -> Unit,
    val onboarding: (OnboardingEvent) -> Unit,
)

@Composable
private fun ReadyGlobe(state: GlobeUiState.Ready, actions: GlobeScreenActions) {
    val intro = remember { Animatable(0f) }
    LaunchedEffect(intro) { intro.animateTo(1f, tween(INTRO_MILLIS, easing = FastOutSlowInEasing)) }

    GlobeView(
        content = state,
        onSelectCountry = actions.selectCountry,
        onSelectSubdivision = actions.selectSubdivision,
        onTurned = { actions.onboarding(OnboardingEvent.GlobeTurned) },
        modifier = Modifier.graphicsLayer {
            val progress = intro.value
            alpha = progress
            scaleX = INTRO_START_SCALE + (1f - INTRO_START_SCALE) * progress
            scaleY = scaleX
        },
    )
}

/**
 * Everything floating over the globe: the Info button, the search bar or the country sheet at the
 * bottom, and the onboarding on first launch. The search bar gives way to the sheet whenever a
 * country is open, as the search card does in Apple Maps.
 */
@Composable
private fun BoxScope.Controls(
    state: GlobeUiState.Ready,
    step: OnboardingStep,
    searchResults: List<SearchResult>,
    actions: GlobeScreenActions,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    InfoButton(
        onClick = { showAbout = true },
        modifier = Modifier
            .align(Alignment.TopEnd)
            .safeDrawingPadding()
            .padding(CONTROL_MARGIN),
    )
    AnimatedVisibility(
        visible = step == OnboardingStep.Finished && state.selected == null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .safeDrawingPadding()
            .padding(CONTROL_MARGIN),
    ) {
        SearchBar(
            query = query,
            results = searchResults,
            onQueryChange = { text ->
                query = text
                actions.search(text)
            },
            onChoose = { result ->
                query = ""
                actions.choose(result)
            },
            onSubmit = {
                actions.submitSearch(query)
                query = ""
            },
        )
    }
    CountrySheet(
        country = state.selected,
        subdivision = state.selectedSubdivision,
        // Closing a state's sheet steps back to its country; closing the country's leaves it.
        onClose = {
            if (state.selectedSubdivision != null) actions.selectSubdivision(null) else actions.selectCountry(null)
        },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    CoachHint(
        step = step,
        onSkip = { actions.onboarding(OnboardingEvent.Skipped) },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .safeDrawingPadding()
            .padding(bottom = HINT_BOTTOM_PADDING),
    )
    AnimatedVisibility(visible = step == OnboardingStep.Welcome, enter = EnterTransition.None, exit = fadeOut()) {
        WelcomeOverlay(onContinue = { actions.onboarding(OnboardingEvent.Started) })
    }
    if (showAbout) AboutSheet(onDismiss = { showAbout = false })
}

@Composable
private fun BoxScope.FailureMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(Alignment.Center)
            .safeDrawingPadding()
            .padding(MESSAGE_PADDING),
    )
}
