package com.paarth.zeitreise.sync

import com.paarth.zeitreise.data.BEST_SCORE_KEY
import com.paarth.zeitreise.data.HOLDER_KEY
import com.paarth.zeitreise.data.HOME_KEY
import com.paarth.zeitreise.data.ONBOARDING_COMPLETE_KEY
import com.paarth.zeitreise.data.STAMPS_KEY
import com.paarth.zeitreise.data.STAMP_SEPARATOR
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable

/**
 * Everything the app keeps for a user, as one document that is saved in the cloud and copied to
 * each of their devices.
 *
 * @property stamps the passport keys of the stamped countries, oldest first.
 * @property home the passport key of the user's home country.
 * @property holder the name the user wrote in the passport.
 * @property bestQuizScore the best quiz score on any device.
 * @property onboardingDone whether the user went through the onboarding on any device.
 */
@Serializable
internal data class SyncedProgress(
    val stamps: List<String> = emptyList(),
    val home: String? = null,
    val holder: String? = null,
    val bestQuizScore: Int = 0,
    val onboardingDone: Boolean = false,
)

/**
 * Combines two copies of the progress. Stamps from both are kept, in this copy's order first, and
 * the better score and the finished onboarding win. For the home country and the name, this copy's
 * value wins and the other one only fills a gap.
 */
internal fun SyncedProgress.mergedWith(other: SyncedProgress): SyncedProgress = SyncedProgress(
    stamps = (stamps + other.stamps).distinct(),
    home = home ?: other.home,
    holder = holder ?: other.holder,
    bestQuizScore = maxOf(bestQuizScore, other.bestQuizScore),
    onboardingDone = onboardingDone || other.onboardingDone,
)

/** Reads the progress saved on this device, from the keys the passport, quiz and onboarding stores use. */
internal fun Settings.readProgress(): SyncedProgress = SyncedProgress(
    stamps = getString(STAMPS_KEY, defaultValue = "").split(STAMP_SEPARATOR).filter { it.isNotEmpty() },
    home = getStringOrNull(HOME_KEY),
    holder = getStringOrNull(HOLDER_KEY),
    bestQuizScore = getInt(BEST_SCORE_KEY, defaultValue = 0),
    onboardingDone = getBoolean(ONBOARDING_COMPLETE_KEY, defaultValue = false),
)

/** Saves [progress] on this device, where the passport, quiz and onboarding stores will find it. */
internal fun Settings.writeProgress(progress: SyncedProgress) {
    putString(STAMPS_KEY, progress.stamps.joinToString(STAMP_SEPARATOR))
    putOrRemove(HOME_KEY, progress.home)
    putOrRemove(HOLDER_KEY, progress.holder)
    putInt(BEST_SCORE_KEY, progress.bestQuizScore)
    putBoolean(ONBOARDING_COMPLETE_KEY, progress.onboardingDone)
}

private fun Settings.putOrRemove(key: String, value: String?) {
    if (value == null) remove(key) else putString(key, value)
}
