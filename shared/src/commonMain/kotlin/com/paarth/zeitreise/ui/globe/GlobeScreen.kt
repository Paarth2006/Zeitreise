package com.paarth.zeitreise.ui.globe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.paarth.zeitreise.domain.HistoricLandmark
import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.domain.Landmark
import com.paarth.zeitreise.domain.Pinned
import com.paarth.zeitreise.domain.Realm
import com.paarth.zeitreise.domain.SearchResult
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.ui.about.AboutSheet
import com.paarth.zeitreise.ui.about.InfoButton
import com.paarth.zeitreise.ui.explore.ExplorePanel
import com.paarth.zeitreise.ui.explore.ExploreState
import com.paarth.zeitreise.ui.explore.ExploreViewModel
import com.paarth.zeitreise.ui.explore.LandmarkCard
import com.paarth.zeitreise.ui.explore.PlaceSheet
import com.paarth.zeitreise.ui.explore.about
import com.paarth.zeitreise.ui.explore.landmarksIn
import com.paarth.zeitreise.ui.glass.LocalGlassBackdrop
import com.paarth.zeitreise.ui.glass.SHEET_BODY_END_PADDING
import com.paarth.zeitreise.ui.onboarding.CoachHint
import com.paarth.zeitreise.ui.onboarding.OnboardingEvent
import com.paarth.zeitreise.ui.onboarding.OnboardingStep
import com.paarth.zeitreise.ui.onboarding.OnboardingViewModel
import com.paarth.zeitreise.ui.onboarding.WelcomeOverlay
import com.paarth.zeitreise.ui.passport.PassportButton
import com.paarth.zeitreise.ui.passport.PassportSheet
import com.paarth.zeitreise.ui.passport.PassportViewModel
import com.paarth.zeitreise.ui.quiz.QuizActions
import com.paarth.zeitreise.ui.quiz.QuizButton
import com.paarth.zeitreise.ui.quiz.QuizControls
import com.paarth.zeitreise.ui.quiz.QuizState
import com.paarth.zeitreise.ui.quiz.QuizViewModel
import com.paarth.zeitreise.ui.timetravel.TimeTravelActions
import com.paarth.zeitreise.ui.timetravel.TimeTravelButton
import com.paarth.zeitreise.ui.timetravel.TimeTravelControls
import com.paarth.zeitreise.ui.timetravel.TimeTravelState
import com.paarth.zeitreise.ui.timetravel.TimeTravelViewModel
import com.paarth.zeitreise.ui.timetravel.readyMap
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zeitreise.shared.generated.resources.Res
import zeitreise.shared.generated.resources.loading_world

/** Padding around the failure message. */
private val MESSAGE_PADDING = 32.dp

/** Gap between the floating controls and the edges of the safe area. */
private val CONTROL_MARGIN = 16.dp

/** Room left at the trailing end of the country-of-the-day chip for the buttons beside it. */
private val TOP_CHIP_END_GAP = 76.dp

/** Gap between the stacked buttons at the top of the screen. */
private val CONTROL_SPACING = 10.dp

/** Gap between the onboarding hint and the bottom of the safe area. */
private val HINT_BOTTOM_PADDING = 24.dp

/** Scale a floating control grows from as it appears, so it seems to well up out of the glass. */
private const val CONTROL_START_SCALE = 0.9f

/** A lively spring for controls appearing and disappearing, like iOS's bouncy glass morphs. */
private val CONTROL_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** Backdrop layer of the floating controls, above the globe's (0). */
private const val CONTROLS_LAYER = 1f

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
    val models = ScreenModels(
        globe = koinViewModel(),
        onboarding = koinViewModel(),
        explore = koinViewModel(),
        timeTravel = koinViewModel(),
        quiz = koinViewModel(),
        passport = koinViewModel(),
    )
    val state by models.globe.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            is GlobeUiState.Loading -> LoadingWorld()
            is GlobeUiState.Failed -> FailureMessage(current.message)
            is GlobeUiState.Ready -> ReadyScreen(current, models)
        }
    }
}

/**
 * The view models the globe screen is driven by, one for each thing the globe can be used for.
 * A data class, so the state remembered against it survives recomposition.
 */
private data class ScreenModels(
    val globe: GlobeViewModel,
    val onboarding: OnboardingViewModel,
    val explore: ExploreViewModel,
    val timeTravel: TimeTravelViewModel,
    val quiz: QuizViewModel,
    val passport: PassportViewModel,
)

/** The globe once the world has loaded, with everything that floats over it. */
@Composable
private fun ReadyScreen(current: GlobeUiState.Ready, models: ScreenModels) {
    val travel by models.timeTravel.state.collectAsStateWithLifecycle()
    val quizState by models.quiz.state.collectAsStateWithLifecycle()
    val exploreState by models.explore.state.collectAsStateWithLifecycle()
    val openCard by models.explore.openCard.collectAsStateWithLifecycle()
    val passportBook by models.passport.book.collectAsStateWithLifecycle()
    val actions = remember(models) { GlobeScreenActions(models.globe, models.onboarding, models.explore) }
    val travelActions = remember(models) { models.timeTravel.controlActions(models.explore) }
    val quizActions = remember(models, current.countries) { models.quiz.controlActions(current.countries) }
    val backdrop = rememberHazeState()
    val places = PlacesOnGlobe.of(exploreState, openCard, current.selected, travel)
    ReadyGlobe(
        state = current,
        places = places,
        past = rememberPastGlobe(travel),
        quiz = quizState.globe,
        actions = actions,
        onSelectRealm = travelActions.selectRealm,
        onAnswer = models.quiz::answer,
        modifier = Modifier.hazeSource(backdrop),
    )
    var showAbout by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        // The controls are a second layer of the backdrop, above the globe, so the About
        // card refracts the open sheet as well as the globe. Glass inside this layer
        // only samples what lies behind it: the globe.
        Box(modifier = Modifier.fillMaxSize().hazeSource(backdrop, zIndex = CONTROLS_LAYER)) {
            Controls(
                state = current,
                places = places,
                story = models.globe.story.collectAsStateWithLifecycle().value,
                step = models.onboarding.step.collectAsStateWithLifecycle().value,
                searchResults = models.globe.searchResults.collectAsStateWithLifecycle().value,
                newStamp = models.globe.newStamp.collectAsStateWithLifecycle().value,
                actions = actions,
                mode = globeMode(travel, quizState),
                modeActions = ModeActions(
                    startTimeTravel = models.timeTravel::start,
                    startQuiz = quizActions.playAgain,
                    showPassport = models.passport::open,
                    showAbout = { showAbout = true },
                ),
            )
            TimeTravelControls(travel, models.timeTravel.story.collectAsStateWithLifecycle().value, travelActions)
            QuizControls(state = quizState, actions = quizActions)
        }
        // Presented modally over the controls, so their glass refracts the country's sheet too.
        PlaceSheet(card = places.openCard, onClose = { actions.showPin(null) })
        AboutSheet(visible = showAbout, onDismiss = { showAbout = false })
        PassportSheet(book = passportBook, onDismiss = models.passport::close)
    }
}

/**
 * What the buttons at the top can start.
 *
 * @property startTimeTravel turns the globe into a map of the past.
 * @property startQuiz starts a round of the quiz.
 * @property showPassport opens the passport.
 * @property showAbout opens the About card.
 */
private class ModeActions(
    val startTimeTravel: () -> Unit,
    val startQuiz: () -> Unit,
    val showPassport: () -> Unit,
    val showAbout: () -> Unit,
)

/**
 * The historical map for the globe while travelling in time, or `null` for today's world.
 *
 * While the next year's map downloads, the last one stays on the globe, so sliding through the
 * years changes the borders in place instead of flashing today's map in between.
 */
@Composable
private fun rememberPastGlobe(travel: TimeTravelState): PastGlobe? {
    var lastMap by remember { mutableStateOf<HistoricalMap?>(null) }
    val on = travel as? TimeTravelState.On
    val ready = on?.readyMap
    LaunchedEffect(ready, on == null) { lastMap = if (on == null) null else ready ?: lastMap }
    return on?.let { PastGlobe(map = ready ?: lastMap, selected = it.selected) }
}

/**
 * The Time Travel controls' actions, handed straight to the view model; coming back to today also
 * closes the card of a landmark of the past.
 */
private fun TimeTravelViewModel.controlActions(places: ExploreViewModel) = TimeTravelActions(
    selectYear = ::selectYear,
    retry = ::retry,
    selectRealm = ::selectRealm,
    retryStory = ::retryStory,
    nextFact = ::nextFact,
    returnToToday = {
        places.close()
        stop()
    },
)

/** What the quiz panel can ask for, each round asking for some of [countries]. */
private fun QuizViewModel.controlActions(countries: List<Country>) = QuizActions(
    reveal = ::reveal,
    next = ::next,
    playAgain = { start(countries) },
    stop = ::stop,
)

/** What the quiz marks on the globe, or `null` when no quiz is on. */
private val QuizState.globe: QuizGlobe?
    get() = when (this) {
        QuizState.Off -> null
        is QuizState.Finished -> QuizGlobe()
        is QuizState.Playing -> with(round) {
            if (answered) QuizGlobe(answer = country, wrongGuess = guess?.takeIf { !isRight }) else QuizGlobe()
        }
    }

/** What the globe is being used for, which decides the controls floating over it. */
private enum class GlobeMode { Explore, TimeTravel, Quiz }

private fun globeMode(travel: TimeTravelState, quiz: QuizState) = when {
    travel is TimeTravelState.On -> GlobeMode.TimeTravel
    quiz != QuizState.Off -> GlobeMode.Quiz
    else -> GlobeMode.Explore
}

/**
 * The pins on the globe and the card open over it, as the globe and the sheets show them.
 *
 * @property explore what the guide has found for the selected country; [ExploreState.Idle] when
 *   it has found nothing for it yet, even if it has for another country.
 * @property pins today, the places pinned for the selected country; in the past, the landmarks
 *   that stood in the year shown.
 * @property openCard the card of one of [pins], if one is open.
 */
private data class PlacesOnGlobe(val explore: ExploreState, val pins: List<Pinned>, val openCard: LandmarkCard?) {
    companion object {
        /** What of [state], [card] and the past belongs on screen with [selected]. */
        fun of(state: ExploreState, card: LandmarkCard?, selected: Country?, travel: TimeTravelState): PlacesOnGlobe {
            val landmarks = state.landmarksIn(selected)
            return when (travel) {
                is TimeTravelState.On -> PlacesOnGlobe(ExploreState.Idle, travel.landmarks, card)
                TimeTravelState.Off -> PlacesOnGlobe(
                    explore = state.about(selected),
                    pins = landmarks,
                    openCard = card?.takeIf { it.landmark in landmarks },
                )
            }
        }
    }
}

/**
 * What the globe screen's parts can ask for, in one place, so the onboarding hears about every
 * selection however it was made: by tapping, by search, or by pressing Search; and so a place's
 * card closes whenever another country is chosen.
 */
private class GlobeScreenActions(
    private val globe: GlobeViewModel,
    private val onboarding: OnboardingViewModel,
    private val places: ExploreViewModel,
) {
    fun selectCountry(country: Country?) {
        places.close()
        globe.select(country)
        if (country != null) onboarding.onEvent(OnboardingEvent.CountrySelected)
    }

    fun selectSubdivision(subdivision: Subdivision?) = globe.selectSubdivision(subdivision)

    fun search(query: String) = globe.search(query)

    fun choose(result: SearchResult) {
        places.close()
        globe.choose(result)
        onboarding.onEvent(OnboardingEvent.CountrySelected)
    }

    fun submitSearch(query: String) {
        places.close()
        globe.submitSearch(query)
        onboarding.onEvent(OnboardingEvent.CountrySelected)
    }

    fun explore(country: Country) = places.explore(country)

    /** Opens the card for [pin], or closes the open card when it is `null`. */
    fun showPin(pin: Pinned?) = when (pin) {
        null -> places.close()
        is Landmark -> places.open(pin)
        is HistoricLandmark -> places.openHistoric(pin)
    }

    fun onboardingEvent(event: OnboardingEvent) = onboarding.onEvent(event)

    fun retryStory() = globe.retryStory()

    fun nextFact() = globe.nextFact()
}

@Composable
private fun ReadyGlobe(
    state: GlobeUiState.Ready,
    places: PlacesOnGlobe,
    past: PastGlobe?,
    quiz: QuizGlobe?,
    actions: GlobeScreenActions,
    onSelectRealm: (Realm?) -> Unit,
    onAnswer: (Country?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val intro = remember { Animatable(0f) }
    LaunchedEffect(intro) { intro.animateTo(1f, tween(INTRO_MILLIS, easing = FastOutSlowInEasing)) }

    GlobeView(
        content = state,
        // During a quiz a tapped country is an answer, not a place to open.
        onSelectCountry = if (quiz != null) onAnswer else actions::selectCountry,
        onSelectSubdivision = actions::selectSubdivision,
        pins = places.pins,
        openPin = places.openCard?.landmark,
        onSelectPin = actions::showPin,
        past = past,
        onSelectRealm = onSelectRealm,
        quiz = quiz,
        onTurned = { actions.onboardingEvent(OnboardingEvent.GlobeTurned) },
        modifier = modifier.graphicsLayer {
            val progress = intro.value
            alpha = progress
            scaleX = INTRO_START_SCALE + (1f - INTRO_START_SCALE) * progress
            scaleY = scaleX
        },
    )
}

/**
 * Everything floating over the globe: the buttons at the top, the search bar or the country sheet
 * at the bottom, and the onboarding on first launch. The search bar gives way to the sheet whenever
 * a country is open, as the search card does in Apple Maps. The About card goes above all of these.
 */
@Composable
private fun BoxScope.Controls(
    state: GlobeUiState.Ready,
    places: PlacesOnGlobe,
    story: StoryState,
    step: OnboardingStep,
    searchResults: List<SearchResult>,
    newStamp: Country?,
    actions: GlobeScreenActions,
    mode: GlobeMode,
    modeActions: ModeActions,
) {
    val exploring = step == OnboardingStep.Finished && mode == GlobeMode.Explore
    // Time Travel and the quiz each start from the whole globe, with no country open.
    TopButtons(
        showModes = exploring,
        today = state.countryOfTheDay?.takeIf { exploring && state.selected == null },
        onOpenToday = actions::selectCountry,
        actions = ModeActions(
            startTimeTravel = {
                actions.selectCountry(null)
                modeActions.startTimeTravel()
            },
            startQuiz = {
                actions.selectCountry(null)
                modeActions.startQuiz()
            },
            showPassport = modeActions.showPassport,
            showAbout = modeActions.showAbout,
        ),
    )
    SearchControl(
        visible = exploring && state.selected == null,
        results = searchResults,
        actions = actions,
    )
    CountrySheet(
        country = state.selected,
        subdivision = state.selectedSubdivision,
        newStamp = newStamp != null && newStamp == state.selected,
        // Closing a state's sheet steps back to its country; closing the country's leaves it.
        onClose = {
            if (state.selectedSubdivision != null) actions.selectSubdivision(null) else actions.selectCountry(null)
        },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val bodyModifier = Modifier.padding(end = SHEET_BODY_END_PADDING)
        StoryPanel(
            story = story,
            onRetry = actions::retryStory,
            onNextFact = actions::nextFact,
            modifier = bodyModifier,
        )
        state.selected?.let { country ->
            ExplorePanel(state = places.explore, onExplore = { actions.explore(country) }, modifier = bodyModifier)
        }
    }
    CoachHint(
        step = step,
        onSkip = { actions.onboardingEvent(OnboardingEvent.Skipped) },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .safeDrawingPadding()
            .padding(bottom = HINT_BOTTOM_PADDING),
    )
    AnimatedVisibility(visible = step == OnboardingStep.Welcome, enter = EnterTransition.None, exit = fadeOut()) {
        WelcomeOverlay(onContinue = { actions.onboardingEvent(OnboardingEvent.Started) })
    }
}

/**
 * The controls along the top: at the trailing corner, Info, always, and while the globe is being
 * explored, the buttons that turn it into a time machine or a quiz, and the one that opens the
 * passport; at the leading corner, the country of the day, while no country is open.
 */
@Composable
private fun BoxScope.TopButtons(
    showModes: Boolean,
    today: Country?,
    onOpenToday: (Country) -> Unit,
    actions: ModeActions,
) {
    // The chip keeps naming its country while it fades away.
    var lastToday by remember { mutableStateOf(today) }
    LaunchedEffect(today) { if (today != null) lastToday = today }
    AnimatedVisibility(
        visible = today != null,
        enter = fadeIn() + scaleIn(CONTROL_SPRING, initialScale = CONTROL_START_SCALE),
        exit = fadeOut() + scaleOut(CONTROL_SPRING, targetScale = CONTROL_START_SCALE),
        modifier = Modifier
            .align(Alignment.TopStart)
            .safeDrawingPadding()
            .padding(start = CONTROL_MARGIN, top = CONTROL_MARGIN, end = TOP_CHIP_END_GAP),
    ) {
        lastToday?.let { CountryOfTheDayChip(country = it, onOpen = onOpenToday) }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .safeDrawingPadding()
            .padding(CONTROL_MARGIN),
    ) {
        InfoButton(onClick = actions.showAbout)
        AnimatedVisibility(
            visible = showModes,
            enter = fadeIn() + scaleIn(CONTROL_SPRING, initialScale = CONTROL_START_SCALE),
            exit = fadeOut() + scaleOut(CONTROL_SPRING, targetScale = CONTROL_START_SCALE),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING)) {
                TimeTravelButton(onClick = actions.startTimeTravel)
                QuizButton(onClick = actions.startQuiz)
                PassportButton(onClick = actions.showPassport)
            }
        }
    }
}

/** The search bar at the bottom, shown while no country is open; it holds its own query. */
@Composable
private fun BoxScope.SearchControl(visible: Boolean, results: List<SearchResult>, actions: GlobeScreenActions) {
    var query by rememberSaveable { mutableStateOf("") }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(CONTROL_SPRING, initialScale = CONTROL_START_SCALE),
        exit = fadeOut() + scaleOut(CONTROL_SPRING, targetScale = CONTROL_START_SCALE),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .safeDrawingPadding()
            .padding(CONTROL_MARGIN),
    ) {
        SearchBar(
            query = query,
            results = results,
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
}

@Composable
private fun BoxScope.LoadingWorld() {
    val label = stringResource(Res.string.loading_world)
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).semantics { contentDescription = label })
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
