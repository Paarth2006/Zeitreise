package com.paarth.zeitreise.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Settings key for the onboarding flag. */
private const val ONBOARDING_COMPLETE_KEY = "onboarding_complete"

/** Remembers whether the onboarding was completed, so it is only shown once. */
public interface OnboardingStore {
    /** Whether the onboarding was finished or skipped before. */
    public suspend fun isComplete(): Boolean

    /** Marks the onboarding as done. */
    public suspend fun markComplete()
}

/**
 * Saves the flag in the platform's key-value storage (SharedPreferences, NSUserDefaults or
 * localStorage).
 *
 * @param dispatcher where reads and writes run, since the first read may hit the disk.
 */
internal class SettingsOnboardingStore(private val settings: Settings, private val dispatcher: CoroutineDispatcher) :
    OnboardingStore {

    override suspend fun isComplete(): Boolean = withContext(dispatcher) {
        settings.getBoolean(ONBOARDING_COMPLETE_KEY, defaultValue = false)
    }

    override suspend fun markComplete(): Unit = withContext(dispatcher) {
        settings.putBoolean(ONBOARDING_COMPLETE_KEY, true)
    }
}
