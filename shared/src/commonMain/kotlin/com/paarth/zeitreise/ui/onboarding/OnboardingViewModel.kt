package com.paarth.zeitreise.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.data.OnboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

/** Moves the onboarding forward and saves when it's done. */
internal class OnboardingViewModel(private val store: OnboardingStore) : ViewModel() {

    private val _step = MutableStateFlow<OnboardingStep>(OnboardingStep.Checking)

    /** The step to show. */
    val step: StateFlow<OnboardingStep> = _step.asStateFlow()

    init {
        viewModelScope.launch {
            val seen = store.isComplete()
            _step.value = if (seen) OnboardingStep.Finished else OnboardingStep.Welcome
        }
    }

    /** Handles [event] and saves the onboarding as done once the user is past the tour. */
    fun onEvent(event: OnboardingEvent) {
        val previous = _step.getAndUpdate { it.after(event) }
        if (!previous.isPastTour && _step.value.isPastTour) {
            viewModelScope.launch { store.markComplete() }
        }
    }
}
