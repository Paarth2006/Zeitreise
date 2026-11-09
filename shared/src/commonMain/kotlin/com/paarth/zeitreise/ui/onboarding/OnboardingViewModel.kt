package com.paarth.zeitreise.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.data.OnboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

/** Moves the onboarding along as the user acts, and remembers once it is over. */
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

    /** Applies [event], saving the onboarding as complete when it finishes. */
    fun onEvent(event: OnboardingEvent) {
        val previous = _step.getAndUpdate { it.after(event) }
        if (previous != OnboardingStep.Finished && _step.value == OnboardingStep.Finished) {
            viewModelScope.launch { store.markComplete() }
        }
    }
}
