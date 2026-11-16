package com.paarth.zeitreise.data

import com.russhwolf.settings.Settings

/** Remembers which fact about each place was shown last, so the next visit shows another. */
public interface FactCursor {
    /**
     * Moves on to the next of a place's [factCount] facts and returns its index, starting over
     * after the last one.
     *
     * @param placeKey identifies the place, such as its Wikidata item.
     */
    public fun next(placeKey: String, factCount: Int): Int
}

/** Keeps the cursor in the platform's key-value store, so it survives the app being closed. */
internal class SettingsFactCursor(private val settings: Settings) : FactCursor {
    override fun next(placeKey: String, factCount: Int): Int {
        require(factCount > 0) { "A place needs at least one fact, $placeKey has none" }
        val key = "$KEY_PREFIX$placeKey"
        val index = (settings.getInt(key, defaultValue = -1) + 1).mod(factCount)
        settings.putInt(key, index)
        return index
    }

    private companion object {
        /** Keeps the cursors apart from the app's other settings. */
        const val KEY_PREFIX = "fact."
    }
}
