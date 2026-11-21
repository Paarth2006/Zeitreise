package com.paarth.zeitreise.data

import com.russhwolf.settings.Settings

/** Remembers which fact was shown last for each place, so the next visit shows a different one. */
public interface FactCursor {
    /**
     * Moves to the next of [factCount] facts and returns its index, wrapping around after the last.
     *
     * @param placeKey identifies the place, such as its Wikidata item.
     */
    public fun next(placeKey: String, factCount: Int): Int
}

/** Saves the cursor in the platform's key-value storage so it survives restarts. */
internal class SettingsFactCursor(private val settings: Settings) : FactCursor {
    override fun next(placeKey: String, factCount: Int): Int {
        require(factCount > 0) { "A place needs at least one fact, $placeKey has none" }
        val key = "$KEY_PREFIX$placeKey"
        val index = (settings.getInt(key, defaultValue = -1) + 1).mod(factCount)
        settings.putInt(key, index)
        return index
    }

    private companion object {
        /** Prefix that keeps these keys separate from other settings. */
        const val KEY_PREFIX = "fact."
    }
}
