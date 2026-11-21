package com.paarth.zeitreise.data

import com.russhwolf.settings.Settings

/**
 * Caches network responses on the device, so opening a place again needs no request and doesn't
 * use up the AI quota.
 */
public interface ResponseCache {
    /** The text saved under [key], or `null`. */
    public fun get(key: String): String?

    /** Saves [value] under [key]. */
    public fun put(key: String, value: String)
}

/** Stores the cache in the platform's key-value storage. Each entry is only a few KB of JSON. */
internal class SettingsResponseCache(private val settings: Settings) : ResponseCache {
    override fun get(key: String): String? = settings.getStringOrNull("$KEY_PREFIX$key")

    override fun put(key: String, value: String) {
        settings.putString("$KEY_PREFIX$key", value)
    }

    private companion object {
        /** Prefix that keeps these keys separate from other settings. */
        const val KEY_PREFIX = "cache."
    }
}
