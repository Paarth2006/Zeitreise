package com.paarth.zeitreise.ui.onboarding

/**
 * The current onboarding step: the welcome screen, a short tour of the controls, then two hints
 * that wait for the user to try the main gestures.
 */
internal sealed interface OnboardingStep {
    /** Still checking if the onboarding was done before. */
    data object Checking : OnboardingStep

    /** The welcome screen, shown on first launch. */
    data object Welcome : OnboardingStep

    /**
     * A tour card explaining one control on screen.
     *
     * @property stop the control being explained.
     * @property replay whether the tour was started again from About. A replay ends after the last
     *   card instead of going on to the gesture hints.
     */
    data class Tour(val stop: TourStop, val replay: Boolean = false) : OnboardingStep

    /** A hint asking the user to drag the globe. */
    data object TryTurning : OnboardingStep

    /** A hint asking the user to tap a country. */
    data object TrySelecting : OnboardingStep

    /** The onboarding is finished or skipped. */
    data object Finished : OnboardingStep
}

/** The controls the tour explains, in the order it shows them. */
internal enum class TourStop {
    CountryOfTheDay,
    Search,
    TimeTravel,
    Quiz,
    Passport,
    About,
}

/** A user action that can move the onboarding forward. */
internal sealed interface OnboardingEvent {
    /** The user tapped Continue on the welcome screen. */
    data object Started : OnboardingEvent

    /** The user tapped Next on a tour card. */
    data object TourNext : OnboardingEvent

    /** The user asked to see the tour again from the About card. */
    data object TourRequested : OnboardingEvent

    /** The user dragged or pinched the globe. */
    data object GlobeTurned : OnboardingEvent

    /** The user selected a country. */
    data object CountrySelected : OnboardingEvent

    /** The user tapped Skip. */
    data object Skipped : OnboardingEvent
}

/** The step that comes after [event]. Events that don't apply to the current step are ignored. */
internal fun OnboardingStep.after(event: OnboardingEvent): OnboardingStep = when (this) {
    OnboardingStep.Checking -> this
    OnboardingStep.Welcome -> welcomeAfter(event)
    is OnboardingStep.Tour -> tourAfter(event)
    OnboardingStep.TryTurning -> turningAfter(event)
    OnboardingStep.TrySelecting -> selectingAfter(event)
    OnboardingStep.Finished -> if (event == OnboardingEvent.TourRequested) firstTourStep(replay = true) else this
}

private fun firstTourStep(replay: Boolean = false) = OnboardingStep.Tour(TourStop.entries.first(), replay)

private fun welcomeAfter(event: OnboardingEvent): OnboardingStep = when (event) {
    OnboardingEvent.Started -> firstTourStep()
    OnboardingEvent.Skipped -> OnboardingStep.Finished
    else -> OnboardingStep.Welcome
}

/** Next moves through the stops. After the last one a replay ends, and a first tour goes on to the hints. */
private fun OnboardingStep.Tour.tourAfter(event: OnboardingEvent): OnboardingStep = when (event) {
    OnboardingEvent.TourNext -> {
        val next = TourStop.entries.getOrNull(stop.ordinal + 1)
        when {
            next != null -> copy(stop = next)
            replay -> OnboardingStep.Finished
            else -> OnboardingStep.TryTurning
        }
    }
    OnboardingEvent.Skipped -> OnboardingStep.Finished
    else -> this
}

/** Selecting a country before turning the globe also finishes, since the user found the main gesture. */
private fun turningAfter(event: OnboardingEvent): OnboardingStep = when (event) {
    OnboardingEvent.GlobeTurned -> OnboardingStep.TrySelecting
    OnboardingEvent.CountrySelected, OnboardingEvent.Skipped -> OnboardingStep.Finished
    else -> OnboardingStep.TryTurning
}

private fun selectingAfter(event: OnboardingEvent): OnboardingStep = when (event) {
    OnboardingEvent.CountrySelected, OnboardingEvent.Skipped -> OnboardingStep.Finished
    else -> OnboardingStep.TrySelecting
}
