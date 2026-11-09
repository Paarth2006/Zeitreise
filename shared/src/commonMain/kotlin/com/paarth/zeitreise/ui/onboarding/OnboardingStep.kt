package com.paarth.zeitreise.ui.onboarding

/**
 * Where the user is in the onboarding.
 *
 * It teaches by doing rather than by slides: after one welcome screen, each hint waits for the
 * user to try the gesture it describes.
 */
internal sealed interface OnboardingStep {
    /** Not yet known whether the onboarding has been seen; nothing is shown. */
    data object Checking : OnboardingStep

    /** The welcome screen over the globe, shown on the first launch only. */
    data object Welcome : OnboardingStep

    /** A hint asking the user to drag the globe. */
    data object TryTurning : OnboardingStep

    /** A hint asking the user to tap a country. */
    data object TrySelecting : OnboardingStep

    /** The onboarding is over, finished or skipped. */
    data object Finished : OnboardingStep
}

/** Something the user did that may move the onboarding on. */
internal sealed interface OnboardingEvent {
    /** The user tapped Continue on the welcome screen. */
    data object Started : OnboardingEvent

    /** The user dragged or pinched the globe. */
    data object GlobeTurned : OnboardingEvent

    /** The user selected a country. */
    data object CountrySelected : OnboardingEvent

    /** The user asked to skip the rest of the onboarding. */
    data object Skipped : OnboardingEvent
}

/**
 * The step that follows this one after [event].
 *
 * Selecting a country before turning the globe finishes the onboarding too: the user has already
 * found the more important gesture, so there is nothing left worth teaching.
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
