package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.passportKey
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Settings key for the stamps. */
private const val STAMPS_KEY = "passport_stamps"

/** Separator between stamps in the saved text. Keys never contain a line break. */
private const val STAMP_SEPARATOR = "\n"

/** The countries the user has opened, each stamped once, in the order they were first opened. */
public interface Passport {
    /** The [passportKey]s of stamped countries, oldest first. */
    public suspend fun stamps(): List<String>

    /**
     * Stamps [country] if it isn't stamped yet.
     *
     * @return `true` if this was a new stamp.
     */
    public suspend fun stamp(country: Country): Boolean
}

/**
 * Saves the stamps in the platform's key-value storage.
 *
 * @param dispatcher where reads and writes run, since the first read may hit the disk.
 */
internal class SettingsPassport(private val settings: Settings, private val dispatcher: CoroutineDispatcher) :
    Passport {
    // Prevents two quick stamps from overwriting each other.
    private val lock = Mutex()

    override suspend fun stamps(): List<String> = withContext(dispatcher) { lock.withLock { read() } }

    override suspend fun stamp(country: Country): Boolean = withContext(dispatcher) {
        lock.withLock {
            val stamps = read()
            val key = country.passportKey
            (key !in stamps).also { isNew ->
                if (isNew) settings.putString(STAMPS_KEY, (stamps + key).joinToString(STAMP_SEPARATOR))
            }
        }
    }

    private fun read(): List<String> =
        settings.getString(STAMPS_KEY, defaultValue = "").split(STAMP_SEPARATOR).filter { it.isNotEmpty() }
}
