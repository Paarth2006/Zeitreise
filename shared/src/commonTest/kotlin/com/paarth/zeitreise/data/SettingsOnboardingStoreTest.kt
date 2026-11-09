package com.paarth.zeitreise.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsOnboardingStoreTest {

    @Test
    fun isNotCompleteOnAFreshInstall() = runTest {
        val store = SettingsOnboardingStore(MapSettings(), StandardTestDispatcher(testScheduler))

        assertFalse(store.isComplete())
    }

    @Test
    fun remembersCompletionAcrossStoresSharingTheSameSettings() = runTest {
        val settings = MapSettings()
        val dispatcher = StandardTestDispatcher(testScheduler)

        SettingsOnboardingStore(settings, dispatcher).markComplete()

        assertTrue(SettingsOnboardingStore(settings, dispatcher).isComplete())
    }
}
