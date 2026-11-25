package com.paarth.zeitreise.sync

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncedProgressTest {

    private val phone = SyncedProgress(stamps = listOf("Q668"), home = "Q668", bestQuizScore = 7)
    private val tablet = SyncedProgress(stamps = listOf("Q183", "Q668"), holder = "Ada", bestQuizScore = 4)

    @Test
    fun keepsTheStampsOfBothCopiesOnceEach() {
        assertEquals(listOf("Q668", "Q183"), phone.mergedWith(tablet).stamps)
    }

    @Test
    fun keepsItsOwnHomeAndNameAndFillsGapsFromTheOther() {
        val merged = phone.mergedWith(tablet.copy(home = "Q183"))

        assertEquals("Q668", merged.home)
        assertEquals("Ada", merged.holder)
    }

    @Test
    fun keepsTheBestScoreAndAFinishedOnboarding() {
        val merged = tablet.copy(onboardingDone = true).mergedWith(phone)

        assertEquals(7, merged.bestQuizScore)
        assertEquals(true, merged.onboardingDone)
    }

    @Test
    fun readsBackWhatItWroteToTheDevice() {
        val settings = MapSettings()
        val progress = phone.mergedWith(tablet).copy(onboardingDone = true)

        settings.writeProgress(progress)

        assertEquals(progress, settings.readProgress())
    }

    @Test
    fun readsAnEmptyDeviceAsNoProgress() {
        assertEquals(SyncedProgress(), MapSettings().readProgress())
    }
}
