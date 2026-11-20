package com.paarth.zeitreise.ui.timetravel

import com.paarth.zeitreise.ai.GuidePlace
import com.paarth.zeitreise.data.GuideRepository
import com.paarth.zeitreise.data.HistoryRepository
import com.paarth.zeitreise.data.SettingsFactCursor
import com.paarth.zeitreise.domain.CountryOutline
import com.paarth.zeitreise.domain.CountryRing
import com.paarth.zeitreise.domain.GuideAnswer
import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.domain.MAP_YEARS
import com.paarth.zeitreise.domain.Realm
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val ROME = Realm(
    name = "Rome",
    partOf = null,
    outlines = listOf(
        CountryOutline(
            CountryRing(doubleArrayOf(10.0, 40.0, 14.0, 40.0, 14.0, 44.0, 10.0, 44.0, 10.0, 40.0)),
            emptyList(),
        ),
    ),
)

/** A history with a map for every year, unless [down], and a record of the years asked for. */
private class FakeHistory(var down: Boolean = false) : HistoryRepository {
    val asked = mutableListOf<Int>()

    override suspend fun mapOf(year: Int): HistoricalMap {
        asked += year
        check(!down) { "No connection" }
        return HistoricalMap(year = year, realms = listOf(ROME), unclaimedLand = emptyList())
    }
}

private object NoGuide : GuideRepository {
    override suspend fun storyFor(place: GuidePlace): GuideAnswer? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimeTravelViewModelTest {

    private val history = FakeHistory()

    /** Runs [test] with the view model's main dispatcher on the test's scheduler. */
    private fun travel(test: suspend TestScope.(TimeTravelViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            test(TimeTravelViewModel(history, NoGuide, SettingsFactCursor(MapSettings())))
            // Let the view model finish what the test set going before its dispatcher is taken away.
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun opensOnTheMapOf1492() = travel { viewModel ->
        viewModel.start()
        advanceUntilIdle()

        val state = assertIs<TimeTravelState.On>(viewModel.state.value)
        assertEquals(1492, state.year)
        assertEquals(1492, assertIs<PastMap.Ready>(state.map).map.year)
    }

    @Test
    fun downloadsOnlyTheYearTheSliderSettlesOn() = travel { viewModel ->
        viewModel.start()
        advanceUntilIdle()
        history.asked.clear()

        (0..5).forEach(viewModel::selectYear)
        advanceUntilIdle()

        assertEquals(listOf(MAP_YEARS[5]), history.asked)
    }

    @Test
    fun saysSoWhenTheMapCannotBeDownloadedAndTriesAgain() = travel { viewModel ->
        history.down = true
        viewModel.start()
        advanceUntilIdle()
        assertEquals(PastMap.Failed, (viewModel.state.value as TimeTravelState.On).map)

        history.down = false
        viewModel.retry()
        advanceUntilIdle()

        assertIs<PastMap.Ready>((viewModel.state.value as TimeTravelState.On).map)
    }

    @Test
    fun clearsTheRealmWhenTheYearChanges() = travel { viewModel ->
        viewModel.start()
        advanceUntilIdle()
        viewModel.selectRealm(ROME)

        viewModel.selectYear(0)

        assertEquals(null, (viewModel.state.value as TimeTravelState.On).selected)
    }

    @Test
    fun comesBackToToday() = travel { viewModel ->
        viewModel.start()
        viewModel.stop()

        assertEquals(TimeTravelState.Off, viewModel.state.value)
    }
}
