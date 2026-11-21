package com.paarth.zeitreise.ui.timetravel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.data.FactCursor
import com.paarth.zeitreise.data.GuideRepository
import com.paarth.zeitreise.data.HistoryRepository
import com.paarth.zeitreise.data.LandmarkHistory
import com.paarth.zeitreise.domain.MAP_YEARS
import com.paarth.zeitreise.domain.Realm
import com.paarth.zeitreise.ui.globe.StoryState
import com.paarth.zeitreise.ui.globe.StoryTeller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long the slider has to rest on a year before its map is downloaded, so a drag fetches one map. */
private const val SETTLE_MILLIS = 250L

/** The year Time Travel opens on: the eve of the Age of Discovery, a map everyone recognises. */
private const val FIRST_YEAR = 1492

/**
 * Holds Time Travel: which year the globe shows, that year's map, and the realm the user tapped
 * and what the guide says about it.
 */
public class TimeTravelViewModel(
    private val history: HistoryRepository,
    private val landmarks: LandmarkHistory,
    guide: GuideRepository,
    factCursor: FactCursor,
) : ViewModel() {

    private val storyTeller = StoryTeller(guide, factCursor)

    private val _state = MutableStateFlow<TimeTravelState>(TimeTravelState.Off)

    /** Whether Time Travel is on, and if so the year, its map and the selected realm. */
    public val state: StateFlow<TimeTravelState> = _state.asStateFlow()

    private val _story = MutableStateFlow<StoryState>(StoryState.Idle)

    /** What the guide says about the selected realm. */
    public val story: StateFlow<StoryState> = _story.asStateFlow()

    private val retries = MutableStateFlow(0)
    private val storyRetries = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val year = _state.map { (it as? TimeTravelState.On)?.yearIndex }.distinctUntilChanged()
            // A new year, or a retry, cancels the download still running for the last one.
            combine(year, retries) { index, _ -> index }.collectLatest { index -> if (index != null) load(index) }
        }
        viewModelScope.launch {
            val realm = _state.map { (it as? TimeTravelState.On)?.let { on -> on.selected to on.year } }
                .distinctUntilChanged()
            combine(realm, storyRetries) { selection, _ -> selection }.collectLatest { selection ->
                val place = selection?.let { (selected, year) -> selected?.guidePlace(year) }
                _story.value = if (place == null) StoryState.Idle else StoryState.Loading
                _story.value = storyTeller.storyFor(place)
            }
        }
    }

    /** Turns Time Travel on, opening on 1492. */
    public fun start() {
        if (_state.value is TimeTravelState.Off) _state.value = TimeTravelState.On(MAP_YEARS.indexOf(FIRST_YEAR))
    }

    /** Comes back to today's world. */
    public fun stop() {
        _state.value = TimeTravelState.Off
    }

    /** Shows the map for the year at [index] of [MAP_YEARS]. */
    public fun selectYear(index: Int) {
        _state.update { state ->
            if (state is TimeTravelState.On && state.yearIndex != index) {
                // The last year's landmarks stay pinned until this year's are read, so pins don't blink.
                TimeTravelState.On(index.coerceIn(MAP_YEARS.indices), PastMap.Loading, landmarks = state.landmarks)
            } else {
                state
            }
        }
    }

    /** Tries the year's map again after it could not be downloaded. */
    public fun retry() {
        retries.update { it + 1 }
    }

    /** Selects [realm], or clears the selection when it is `null`. */
    public fun selectRealm(realm: Realm?) {
        _state.update { state -> if (state is TimeTravelState.On) state.copy(selected = realm) else state }
    }

    /** Asks the guide about the selected realm again after Wikipedia could not be reached. */
    public fun retryStory() {
        storyRetries.update { it + 1 }
    }

    /** Shows the selected realm's next fact. */
    public fun nextFact() {
        _story.update { story -> if (story is StoryState.Ready) storyTeller.nextFact(story) else story }
    }

    private suspend fun load(index: Int) {
        // The landmarks are bundled, so they move with the slider; the map waits for it to settle.
        val standing = landmarks.standingIn(MAP_YEARS[index])
        _state.update { state ->
            if (state is TimeTravelState.On && state.yearIndex == index) state.copy(landmarks = standing) else state
        }
        delay(SETTLE_MILLIS)
        val loaded = runCatching { history.mapOf(MAP_YEARS[index]) }
            .onFailure { if (it is CancellationException) throw it }
            .fold(onSuccess = { PastMap.Ready(it) }, onFailure = { PastMap.Failed })
        _state.update { state ->
            if (state is TimeTravelState.On && state.yearIndex == index) state.copy(map = loaded) else state
        }
    }
}

/** The realm as the guide is asked about it, in the year shown, so it tells of that time. */
private fun Realm.guidePlace(year: Int): GuidePlace {
    val within = partOf?.let { " within $it" }.orEmpty()
    return GuidePlace(name = name, description = "a realm$within", wikidataId = null, year = year)
}
