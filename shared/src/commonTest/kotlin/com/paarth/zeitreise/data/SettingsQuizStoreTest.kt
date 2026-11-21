package com.paarth.zeitreise.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsQuizStoreTest {

    @Test
    fun startsWithNoBestScore() = runTest {
        val store = SettingsQuizStore(MapSettings(), StandardTestDispatcher(testScheduler))

        assertEquals(0, store.bestScore())
    }

    @Test
    fun remembersTheBestScoreAcrossStoresSharingTheSameSettings() = runTest {
        val settings = MapSettings()
        val dispatcher = StandardTestDispatcher(testScheduler)

        SettingsQuizStore(settings, dispatcher).saveBestScore(7)

        assertEquals(7, SettingsQuizStore(settings, dispatcher).bestScore())
    }
}
