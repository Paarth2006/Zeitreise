package com.paarth.zeitreise.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingStepTest {

    @Test
    fun walksThroughTheTourAndEveryHintWhenTheUserFollowsThem() {
        val events = listOf(OnboardingEvent.Started, OnboardingEvent.HomeChosen) +
            List(TourStop.entries.size) { OnboardingEvent.TourNext } +
            listOf(OnboardingEvent.GlobeTurned, OnboardingEvent.CountrySelected)

        val finalStep = events.fold<OnboardingEvent, OnboardingStep>(OnboardingStep.Welcome) { step, event ->
            step.after(event)
        }

        assertEquals(OnboardingStep.Finished, finalStep)
    }

    @Test
    fun continueOnTheWelcomeScreenAsksWhereTheUserIsFrom() {
        assertEquals(OnboardingStep.ChooseHome, OnboardingStep.Welcome.after(OnboardingEvent.Started))
    }

    @Test
    fun choosingOrSkippingAHomeCountryStartsTheTour() {
        listOf(OnboardingEvent.HomeChosen, OnboardingEvent.HomeSkipped).forEach { event ->
            assertEquals(
                OnboardingStep.Tour(TourStop.CountryOfTheDay),
                OnboardingStep.ChooseHome.after(event),
                "$event",
            )
        }
    }

    @Test
    fun theHomeQuestionIgnoresGesturesBehindIt() {
        assertEquals(OnboardingStep.ChooseHome, OnboardingStep.ChooseHome.after(OnboardingEvent.GlobeTurned))
    }

    @Test
    fun theTourVisitsEveryControlInOrder() {
        val stops = generateSequence<OnboardingStep>(OnboardingStep.Tour(TourStop.entries.first())) {
            it.after(OnboardingEvent.TourNext)
        }.takeWhile { it is OnboardingStep.Tour }.map { (it as OnboardingStep.Tour).stop }.toList()

        assertEquals(TourStop.entries, stops)
    }

    @Test
    fun theLastTourCardLeadsToTheTurningHint() {
        assertEquals(
            OnboardingStep.TryTurning,
            OnboardingStep.Tour(TourStop.entries.last()).after(OnboardingEvent.TourNext),
        )
    }

    @Test
    fun aReplayedTourEndsAfterTheLastCard() {
        val replay = OnboardingStep.Finished.after(OnboardingEvent.TourRequested)
        assertEquals(OnboardingStep.Tour(TourStop.entries.first(), replay = true), replay)

        val last = OnboardingStep.Tour(TourStop.entries.last(), replay = true)
        assertEquals(OnboardingStep.Finished, last.after(OnboardingEvent.TourNext))
    }

    @Test
    fun skippingTheTourFinishesTheOnboarding() {
        assertEquals(OnboardingStep.Finished, OnboardingStep.Tour(TourStop.Quiz).after(OnboardingEvent.Skipped))
    }

    @Test
    fun theTourIgnoresGesturesOnTheGlobe() {
        val tour = OnboardingStep.Tour(TourStop.Search)

        assertEquals(tour, tour.after(OnboardingEvent.GlobeTurned))
        assertEquals(tour, tour.after(OnboardingEvent.CountrySelected))
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
        listOf(
            OnboardingStep.Welcome,
            OnboardingStep.ChooseHome,
            OnboardingStep.TryTurning,
            OnboardingStep.TrySelecting,
        ).forEach { step ->
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
