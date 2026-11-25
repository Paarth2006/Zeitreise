package com.paarth.zeitreise.sync

import com.paarth.zeitreise.data.OnboardingStore
import com.paarth.zeitreise.data.Passport
import com.paarth.zeitreise.data.QuizStore
import com.paarth.zeitreise.domain.Country

/** A [Passport] that tells [trigger] about every change, so it reaches the user's other devices. */
internal class SyncingPassport(private val local: Passport, private val trigger: SyncTrigger) : Passport by local {
    override suspend fun stamp(country: Country): Boolean = local.stamp(country).also { isNew ->
        if (isNew) trigger.changed()
    }

    override suspend fun setHome(country: Country) {
        local.setHome(country)
        trigger.changed()
    }

    override suspend fun setHolder(name: String) {
        local.setHolder(name)
        trigger.changed()
    }
}

/** A [QuizStore] that tells [trigger] about a new best score. */
internal class SyncingQuizStore(private val local: QuizStore, private val trigger: SyncTrigger) : QuizStore by local {
    override suspend fun saveBestScore(score: Int) {
        local.saveBestScore(score)
        trigger.changed()
    }
}

/** An [OnboardingStore] that tells [trigger] when the onboarding is done, so other devices skip it. */
internal class SyncingOnboardingStore(private val local: OnboardingStore, private val trigger: SyncTrigger) :
    OnboardingStore by local {
    override suspend fun markComplete() {
        local.markComplete()
        trigger.changed()
    }
}
