package com.paarth.zeitreise.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingStepTest {

    @Test
    fun walksThroughEveryHintWhenTheUserFollowsThem() {
        val finalStep = listOf(
            OnboardingEvent.Started,
            OnboardingEvent.GlobeTurned,
            OnboardingEvent.CountrySelected,
        ).fold<OnboardingEvent, OnboardingStep>(OnboardingStep.Welcome) { step, event -> step.after(event) }

        assertEquals(OnboardingStep.Finished, finalStep)
    }

    @Test
    fun continueOnTheWelcomeScreenShowsTheTurningHint() {
        assertEquals(OnboardingStep.TryTurning, OnboardingStep.Welcome.after(OnboardingEvent.Started))
    }

    @Test
    fun turningTheGlobeMovesOnToTheSelectingHint() {
        assertEquals(OnboardingStep.TrySelecting, OnboardingStep.TryTurning.after(OnboardingEvent.GlobeTurned))
    }

    @Test
    fun selectingACountryEarlyFinishesTheOnboarding() {
        assertEquals(OnboardingStep.Finished, OnboardingStep.TryTurning.after(OnboardingEvent.CountrySelected))
    }

    @Test
    fun skipFinishesTheOnboardingFromEveryVisibleStep() {
        listOf(OnboardingStep.Welcome, OnboardingStep.TryTurning, OnboardingStep.TrySelecting).forEach { step ->
            assertEquals(OnboardingStep.Finished, step.after(OnboardingEvent.Skipped), "skipping from $step")
        }
    }

    @Test
    fun theWelcomeScreenIgnoresGesturesBehindIt() {
        assertEquals(OnboardingStep.Welcome, OnboardingStep.Welcome.after(OnboardingEvent.GlobeTurned))
    }

    @Test
    fun turningAgainOnTheSelectingHintKeepsTheHint() {
        assertEquals(OnboardingStep.TrySelecting, OnboardingStep.TrySelecting.after(OnboardingEvent.GlobeTurned))
    }

    @Test
    fun finishedAndCheckingIgnoreEveryEvent() {
        val events = listOf(
            OnboardingEvent.Started,
            OnboardingEvent.GlobeTurned,
            OnboardingEvent.CountrySelected,
            OnboardingEvent.Skipped,
        )
        listOf(OnboardingStep.Checking, OnboardingStep.Finished).forEach { step ->
            events.forEach { event -> assertEquals(step, step.after(event), "$event on $step") }
        }
    }
}
