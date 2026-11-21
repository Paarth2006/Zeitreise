package com.paarth.zeitreise.ui.globe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * The demo for the tour card at [stop]. The search card types a country's name into the search bar
 * and then flies there, like a real search. Once a tour is over, the globe turns back to the user's
 * home country, since the demos left it somewhere else.
 */
@Composable
internal fun rememberTourDemo(stop: TourStop?, state: GlobeUiState.Ready): TourDemo {
    val homecoming = rememberHomecoming(stop, state.home)
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
        null -> TourDemo(showcase = homecoming)
        TourStop.Passport, TourStop.About -> TourDemo()
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

/** Turns the globe to [home] once a tour has run and is over, or nothing if there's no home country. */
@Composable
private fun rememberHomecoming(stop: TourStop?, home: Country?): GlobeShowcase? {
    var toured by remember { mutableStateOf(false) }
    LaunchedEffect(stop) { if (stop != null) toured = true }
    return home?.takeIf { toured && stop == null }?.let { GlobeShowcase.Homecoming(it) }
}

private fun List<Country>.withCode(isoA2: String): Country? = firstOrNull { it.isoA2 == isoA2 }
