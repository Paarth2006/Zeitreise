package com.paarth.zeitreise.data

import com.russhwolf.settings.Settings

/**
 * Keeps responses from the network on the device, so a place opened once opens again without a
 * request, offline and without spending the AI's small free quota twice.
 */
public interface ResponseCache {
    /** The text stored under [key], or `null` if nothing has been stored. */
    public fun get(key: String): String?

    /** Stores [value] under [key], replacing what was there. */
    public fun put(key: String, value: String)
}

/**
 * Keeps the cache in the platform's key-value store: shared preferences on Android, user defaults
 * on iOS and local storage on the web. Each entry is a few kilobytes of JSON, which all three
 * handle comfortably.
 */
internal class SettingsResponseCache(private val settings: Settings) : ResponseCache {
    override fun get(key: String): String? = settings.getStringOrNull("$KEY_PREFIX$key")

    override fun put(key: String, value: String) {
        settings.putString("$KEY_PREFIX$key", value)
    }

    private companion object {
        /** Keeps cached responses apart from the app's other settings. */
        const val KEY_PREFIX = "cache."
    }
}
