package com.paarth.zeitreise.ui.onboarding

/** The current onboarding step. After the welcome screen, each hint waits for the user to try it. */
internal sealed interface OnboardingStep {
    /** Still checking if the onboarding was done before. */
    data object Checking : OnboardingStep

    /** The welcome screen, shown on first launch. */
    data object Welcome : OnboardingStep

    /** A hint asking the user to drag the globe. */
    data object TryTurning : OnboardingStep

    /** A hint asking the user to tap a country. */
    data object TrySelecting : OnboardingStep

    /** The onboarding is finished or skipped. */
    data object Finished : OnboardingStep
}

/** A user action that can move the onboarding forward. */
internal sealed interface OnboardingEvent {
    /** The user tapped Continue on the welcome screen. */
    data object Started : OnboardingEvent

    /** The user dragged or pinched the globe. */
    data object GlobeTurned : OnboardingEvent

    /** The user selected a country. */
    data object CountrySelected : OnboardingEvent

    /** The user tapped Skip. */
    data object Skipped : OnboardingEvent
}

/**
 * The next step after [event]. Selecting a country before turning the globe also finishes the
 * onboarding, since the user already found the main gesture.
 */
internal fun OnboardingStep.after(event: OnboardingEvent): OnboardingStep = when (this) {
    OnboardingStep.Checking -> this

    OnboardingStep.Welcome -> when (event) {
        OnboardingEvent.Started -> OnboardingStep.TryTurning
        OnboardingEvent.Skipped -> OnboardingStep.Finished
        OnboardingEvent.GlobeTurned, OnboardingEvent.CountrySelected -> this
    }

    OnboardingStep.TryTurning -> when (event) {
        OnboardingEvent.GlobeTurned -> OnboardingStep.TrySelecting
        OnboardingEvent.CountrySelected, OnboardingEvent.Skipped -> OnboardingStep.Finished
        OnboardingEvent.Started -> this
    }

    OnboardingStep.TrySelecting -> when (event) {
        OnboardingEvent.CountrySelected, OnboardingEvent.Skipped -> OnboardingStep.Finished
        OnboardingEvent.Started, OnboardingEvent.GlobeTurned -> this
    }

    OnboardingStep.Finished -> this
}
