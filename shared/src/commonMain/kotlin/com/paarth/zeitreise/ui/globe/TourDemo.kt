package com.paarth.zeitreise.ui.globe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.ui.onboarding.TourStop
import kotlinx.coroutines.delay

/** The country the search card types in, and the one the quiz card shows as an answer. */
private const val SEARCH_DEMO_ISO = "JP"
private const val QUIZ_DEMO_ISO = "BR"

/** Pause before the search card starts typing, and the time per typed letter. */
private const val TYPING_START_MILLIS = 500L
private const val TYPING_LETTER_MILLIS = 110L

/**
 * What the screen acts out for the tour card on screen.
 *
 * @property showcase what the globe shows.
 * @property searchText the text typed into the search bar, or `null` to show the real query.
 */
internal data class TourDemo(val showcase: GlobeShowcase? = null, val searchText: String? = null)

/**
 * The demo for the tour card at [stop], or no demo if the tour isn't running. The search card types
 * a country's name into the search bar and then flies there, like a real search.
 */
@Composable
internal fun rememberTourDemo(stop: TourStop?, state: GlobeUiState.Ready): TourDemo {
    val searchCountry = remember(state.countries) { state.countries.withCode(SEARCH_DEMO_ISO) }
    val quizCountry = remember(state.countries) { state.countries.withCode(QUIZ_DEMO_ISO) }
    val typed by produceState("", stop, searchCountry) {
        value = ""
        val name = searchCountry?.name
        if (stop != TourStop.Search || name == null) return@produceState
        delay(TYPING_START_MILLIS)
        for (length in 1..name.length) {
            value = name.take(length)
            delay(TYPING_LETTER_MILLIS)
        }
    }
    return when (stop) {
        null, TourStop.Passport, TourStop.About -> TourDemo()
        // The card for the chip sits at the top, so the country is framed below it.
        TourStop.CountryOfTheDay -> TourDemo(
            showcase = state.countryOfTheDay?.let { GlobeShowcase.Highlight(it, lowered = true) },
        )
        TourStop.Search -> TourDemo(
            showcase = searchCountry?.takeIf { typed == it.name }?.let { GlobeShowcase.Highlight(it) },
            searchText = typed,
        )
        TourStop.TimeTravel -> TourDemo(showcase = GlobeShowcase.Rewind)
        TourStop.Quiz -> TourDemo(showcase = quizCountry?.let { GlobeShowcase.Highlight(it, asQuizAnswer = true) })
    }
}

private fun List<Country>.withCode(isoA2: String): Country? = firstOrNull { it.isoA2 == isoA2 }
