package com.paarth.zeitreise.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Key under which the finished onboarding is remembered. */
private const val ONBOARDING_COMPLETE_KEY = "onboarding_complete"

/** Remembers whether the user has been through the onboarding, so it is shown only once. */
public interface OnboardingStore {
    /** Whether the onboarding has been finished or skipped before. */
    public suspend fun isComplete(): Boolean

    /** Records that the onboarding is done, so later launches go straight to the globe. */
    public suspend fun markComplete()
}

/**
 * Keeps the flag in the platform's key-value store: shared preferences on Android, user defaults
 * on iOS and local storage on the web.
 *
 * @param dispatcher dispatcher the reads and writes run on, since the first read may touch disk.
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
