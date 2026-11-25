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
import com.paarth.zeitreise.ui.about.AccountViewModel
import com.paarth.zeitreise.ui.about.actions
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
import com.paarth.zeitreise.ui.onboarding.HomePicker
import com.paarth.zeitreise.ui.onboarding.OnboardingEvent
import com.paarth.zeitreise.ui.onboarding.OnboardingStep
import com.paarth.zeitreise.ui.onboarding.OnboardingViewModel
import com.paarth.zeitreise.ui.onboarding.TourOverlay
import com.paarth.zeitreise.ui.onboarding.TourStop
import com.paarth.zeitreise.ui.onboarding.TourTargets
import com.paarth.zeitreise.ui.onboarding.WelcomeOverlay
import com.paarth.zeitreise.ui.onboarding.tourTarget
import com.paarth.zeitreise.ui.passport.PassportSheet
import com.paarth.zeitreise.ui.passport.PassportViewModel
import com.paarth.zeitreise.ui.quiz.QuizActions
import com.paarth.zeitreise.ui.quiz.QuizControls
import com.paarth.zeitreise.ui.quiz.QuizState
import com.paarth.zeitreise.ui.quiz.QuizViewModel
import com.paarth.zeitreise.ui.timetravel.TimeTravelActions
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

/** Gap between the stacked buttons at the top of the screen. */
private val CONTROL_SPACING = 10.dp

/** Gap between the onboarding hint and the bottom of the safe area. */
private val HINT_BOTTOM_PADDING = 24.dp

/** Starting scale for controls as they appear. */
private const val CONTROL_START_SCALE = 0.9f

/** A slightly bouncy spring for controls appearing and disappearing. */
private val CONTROL_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** Haze layer of the controls, above the globe (0). */
private const val CONTROLS_LAYER = 1f

/** Duration of the globe's intro animation in milliseconds. */
private const val INTRO_MILLIS = 1_200

/** Starting scale of the intro animation. */
private const val INTRO_START_SCALE = 0.82f

/**
 * The main screen: the globe, the country sheet and the onboarding.
 *
 * The globe fills the whole window, while text and controls stay inside the safe area.
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
        account = koinViewModel(),
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

/** All view models used by the globe screen. It's a data class so it can be used as a `remember` key. */
private data class ScreenModels(
    val globe: GlobeViewModel,
    val onboarding: OnboardingViewModel,
    val explore: ExploreViewModel,
    val timeTravel: TimeTravelViewModel,
    val quiz: QuizViewModel,
    val passport: PassportViewModel,
    val account: AccountViewModel,
)

/** The loaded globe with all its controls. */
@Composable
private fun ReadyScreen(current: GlobeUiState.Ready, models: ScreenModels) {
    val travel by models.timeTravel.state.collectAsStateWithLifecycle()
    val quizState by models.quiz.state.collectAsStateWithLifecycle()
    val exploreState by models.explore.state.collectAsStateWithLifecycle()
    val openCard by models.explore.openCard.collectAsStateWithLifecycle()
    val actions = remember(models) { GlobeScreenActions(models.globe, models.onboarding, models.explore) }
    val travelActions = remember(models) { models.timeTravel.controlActions(models.explore) }
    val quizActions = remember(models, current.countries) { models.quiz.controlActions(current.countries) }
    val backdrop = rememberHazeState()
    val places = PlacesOnGlobe.of(exploreState, openCard, current.selected, travel)
    val step by models.onboarding.step.collectAsStateWithLifecycle()
    val demo = rememberTourDemo((step as? OnboardingStep.Tour)?.stop, current)
    ReadyGlobe(
        state = current,
        places = places,
        past = rememberPastGlobe(travel),
        quiz = quizState.globe,
        actions = actions,
        onSelectRealm = travelActions.selectRealm,
        onAnswer = models.quiz::answer,
        showcase = demo.showcase,
        modifier = Modifier.hazeSource(backdrop),
    )
    var showAbout by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        // The controls are a second Haze layer, so cards opened on top blur the controls too.
        Box(modifier = Modifier.fillMaxSize().hazeSource(backdrop, zIndex = CONTROLS_LAYER)) {
            Controls(
                state = current,
                places = places,
                story = models.globe.story.collectAsStateWithLifecycle().value,
                step = step,
                searchDemo = demo.searchText,
                searchResults = models.globe.searchResults.collectAsStateWithLifecycle().value,
                newStamp = models.globe.newStamp.collectAsStateWithLifecycle().value,
                actions = actions,
                mode = globeMode(travel, quizState),
                modeActions = models.modeActions(quizActions) { showAbout = true },
            )
            TimeTravelControls(
                state = travel,
                story = models.timeTravel.story.collectAsStateWithLifecycle().value,
                actions = travelActions,
                cardOpen = places.openCard != null,
            )
            QuizControls(state = quizState, actions = quizActions)
        }
        // Shown above the controls, so their glass also blurs the country sheet.
        PlaceSheet(card = places.openCard, onClose = { actions.showPin(null) })
        CardSheets(models, showAbout = showAbout, onDismissAbout = { showAbout = false })
    }
}

/** The About and Passport cards, over everything else. */
@Composable
private fun CardSheets(models: ScreenModels, showAbout: Boolean, onDismissAbout: () -> Unit) {
    AboutSheet(
        visible = showAbout,
        onDismiss = onDismissAbout,
        onTakeTour = { models.onboarding.onEvent(OnboardingEvent.TourRequested) },
        account = models.account.state.collectAsStateWithLifecycle().value,
        accountActions = remember(models.account) { models.account.actions() },
    )
    PassportSheet(
        book = models.passport.book.collectAsStateWithLifecycle().value,
        onDismiss = models.passport::close,
        onRename = models.passport::rename,
    )
}

/** Toolbar actions. Each one starts a mode or opens a sheet over the globe. */
private fun ScreenModels.modeActions(quiz: QuizActions, showAbout: () -> Unit) = ModeActions(
    startTimeTravel = timeTravel::start,
    startQuiz = quiz.playAgain,
    showPassport = passport::open,
    showAbout = showAbout,
)

/**
 * The historical map to draw, or `null` for today's world. While the next year's map loads, the
 * previous one stays visible so today's map doesn't flash in between.
 */
@Composable
private fun rememberPastGlobe(travel: TimeTravelState): PastGlobe? {
    var lastMap by remember { mutableStateOf<HistoricalMap?>(null) }
    val on = travel as? TimeTravelState.On
    val ready = on?.readyMap
    LaunchedEffect(ready, on == null) { lastMap = if (on == null) null else ready ?: lastMap }
    return on?.let { PastGlobe(map = ready ?: lastMap, selected = it.selected) }
}

/** Time Travel actions. Going back to today also closes any open landmark card. */
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

/** Quiz panel actions. Each round picks from [countries]. */
private fun QuizViewModel.controlActions(countries: List<Country>) = QuizActions(
    reveal = ::reveal,
    next = ::next,
    playAgain = { start(countries) },
    stop = ::stop,
)

/** What the quiz highlights on the globe, or `null` if no quiz is running. */
private val QuizState.globe: QuizGlobe?
    get() = when (this) {
        QuizState.Off -> null
        is QuizState.Finished -> QuizGlobe()
        is QuizState.Playing -> with(round) {
            if (answered) QuizGlobe(answer = country, wrongGuess = guess?.takeIf { !isRight }) else QuizGlobe()
        }
    }

/** The current mode of the globe, which decides which controls are shown. */
private enum class GlobeMode { Explore, TimeTravel, Quiz }

private fun globeMode(travel: TimeTravelState, quiz: QuizState) = when {
    travel is TimeTravelState.On -> GlobeMode.TimeTravel
    quiz != QuizState.Off -> GlobeMode.Quiz
    else -> GlobeMode.Explore
}

/**
 * The pins on the globe and the open card.
 *
 * @property explore the Explore state for the selected country, [ExploreState.Idle] if nothing yet.
 * @property pins the selected country's places, or the landmarks of the year in Time Travel.
 * @property openCard the open card for one of [pins], if any.
 */
private data class PlacesOnGlobe(val explore: ExploreState, val pins: List<Pinned>, val openCard: LandmarkCard?) {
    companion object {
        /** Works out what to show for [selected] from [state], [card] and Time Travel. */
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
 * All actions of the globe screen in one place. This way the onboarding hears about every
 * selection (tap or search), and an open place card closes when another country is picked.
 */
private class GlobeScreenActions(
    private val globe: GlobeViewModel,
    private val onboarding: OnboardingViewModel,
    private val places: ExploreViewModel,
) {
    /** Passes an onboarding event on, such as the globe being turned. */
    val onboardingEvent: (OnboardingEvent) -> Unit = onboarding::onEvent

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

    /** Opens the card for [pin], or closes it if [pin] is `null`. */
    fun showPin(pin: Pinned?) = when (pin) {
        null -> places.close()
        is Landmark -> places.open(pin)
        is HistoricLandmark -> places.openHistoric(pin)
    }

    fun chooseHome(country: Country) {
        globe.chooseHome(country)
        onboarding.onEvent(OnboardingEvent.HomeChosen)
    }

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
    showcase: GlobeShowcase?,
    modifier: Modifier = Modifier,
) {
    val intro = remember { Animatable(0f) }
    LaunchedEffect(intro) { intro.animateTo(1f, tween(INTRO_MILLIS, easing = FastOutSlowInEasing)) }

    GlobeView(
        content = state,
        // During a quiz, tapping a country answers the question.
        onSelectCountry = if (quiz != null) onAnswer else actions::selectCountry,
        onSelectSubdivision = actions::selectSubdivision,
        pins = places.pins,
        openPin = places.openCard?.landmark,
        onSelectPin = actions::showPin,
        past = past,
        onSelectRealm = onSelectRealm,
        quiz = quiz,
        onTurned = { actions.onboardingEvent(OnboardingEvent.GlobeTurned) },
        showcase = showcase,
        modifier = modifier.graphicsLayer {
            val progress = intro.value
            alpha = progress
            scaleX = INTRO_START_SCALE + (1f - INTRO_START_SCALE) * progress
            scaleY = scaleX
        },
    )
}

/**
 * Everything on top of the globe: the top buttons, the search bar or country sheet at the bottom,
 * and the onboarding. The search bar is hidden while a country is open.
 */
@Composable
private fun BoxScope.Controls(
    state: GlobeUiState.Ready,
    places: PlacesOnGlobe,
    story: StoryState,
    step: OnboardingStep,
    searchDemo: String?,
    searchResults: List<SearchResult>,
    newStamp: Country?,
    actions: GlobeScreenActions,
    mode: GlobeMode,
    modeActions: ModeActions,
) {
    // The tour explains the controls, so they stay visible while it runs.
    val exploring = (step == OnboardingStep.Finished || step is OnboardingStep.Tour) && mode == GlobeMode.Explore
    val tourTargets = remember { TourTargets() }
    TodayChip(
        today = state.countryOfTheDay?.takeIf { exploring && state.selected == null },
        onOpenToday = actions::selectCountry,
        tourTargets = tourTargets,
    )
    // The toolbar and search bar only show with no country open, so the modes always start from the whole globe.
    SearchControl(
        visible = exploring && state.selected == null,
        results = searchResults,
        actions = actions,
        modeActions = modeActions,
        tourTargets = tourTargets,
        demoText = searchDemo,
    )
    CountrySheet(
        country = state.selected,
        subdivision = state.selectedSubdivision,
        newStamp = newStamp != null && newStamp == state.selected,
        // Closing a state goes back to its country; closing a country clears the selection.
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
    OnboardingLayers(
        step = step,
        tourTargets = tourTargets,
        countries = state.countries,
        onChooseHome = actions::chooseHome,
        onEvent = actions.onboardingEvent,
    )
}

/** The onboarding on top of everything: the gesture hints, the welcome screen, the home question and the tour. */
@Composable
private fun BoxScope.OnboardingLayers(
    step: OnboardingStep,
    tourTargets: TourTargets,
    countries: List<Country>,
    onChooseHome: (Country) -> Unit,
    onEvent: (OnboardingEvent) -> Unit,
) {
    CoachHint(
        step = step,
        onSkip = { onEvent(OnboardingEvent.Skipped) },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .safeDrawingPadding()
            .padding(bottom = HINT_BOTTOM_PADDING),
    )
    AnimatedVisibility(visible = step is OnboardingStep.Tour, enter = fadeIn(), exit = fadeOut()) {
        // Keep the last card while the overlay fades out.
        var lastStep by remember { mutableStateOf(step as? OnboardingStep.Tour) }
        LaunchedEffect(step) { if (step is OnboardingStep.Tour) lastStep = step }
        lastStep?.let { tour ->
            TourOverlay(
                step = tour,
                targets = tourTargets,
                onNext = { onEvent(OnboardingEvent.TourNext) },
                onSkip = { onEvent(OnboardingEvent.Skipped) },
            )
        }
    }
    AnimatedVisibility(visible = step == OnboardingStep.ChooseHome, enter = fadeIn(), exit = fadeOut()) {
        HomePicker(countries = countries, onChoose = onChooseHome, onSkip = { onEvent(OnboardingEvent.HomeSkipped) })
    }
    AnimatedVisibility(visible = step == OnboardingStep.Welcome, enter = EnterTransition.None, exit = fadeOut()) {
        WelcomeOverlay(onContinue = { onEvent(OnboardingEvent.Started) })
    }
}

/** The country-of-the-day chip at the top, shown while no country is open. */
@Composable
private fun BoxScope.TodayChip(today: Country?, onOpenToday: (Country) -> Unit, tourTargets: TourTargets) {
    // Keep the last country while the chip fades out.
    var lastToday by remember { mutableStateOf(today) }
    LaunchedEffect(today) { if (today != null) lastToday = today }
    AnimatedVisibility(
        visible = today != null,
        enter = fadeIn() + scaleIn(CONTROL_SPRING, initialScale = CONTROL_START_SCALE),
        exit = fadeOut() + scaleOut(CONTROL_SPRING, targetScale = CONTROL_START_SCALE),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .safeDrawingPadding()
            .padding(CONTROL_MARGIN),
    ) {
        lastToday?.let {
            CountryOfTheDayChip(
                country = it,
                onOpen = onOpenToday,
                modifier = Modifier.tourTarget(tourTargets, TourStop.CountryOfTheDay),
            )
        }
    }
}

/**
 * The toolbar and the search bar, shown while no country is open. The search bar keeps its own
 * query text, and the toolbar hides while the user is typing a search. During the tour the search
 * bar shows [demoText] instead.
 */
@Composable
private fun BoxScope.SearchControl(
    visible: Boolean,
    results: List<SearchResult>,
    actions: GlobeScreenActions,
    modeActions: ModeActions,
    tourTargets: TourTargets,
    demoText: String?,
) {
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
        Column(verticalArrangement = Arrangement.spacedBy(CONTROL_SPACING)) {
            AnimatedVisibility(
                visible = query.isEmpty(),
                enter = fadeIn() + scaleIn(CONTROL_SPRING, initialScale = CONTROL_START_SCALE),
                exit = fadeOut() + scaleOut(CONTROL_SPRING, targetScale = CONTROL_START_SCALE),
            ) {
                ModeToolbar(actions = modeActions, enabled = visible && query.isEmpty(), tourTargets = tourTargets)
            }
            SearchBar(
                query = demoText ?: query,
                results = results,
                modifier = Modifier.tourTarget(tourTargets, TourStop.Search),
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
