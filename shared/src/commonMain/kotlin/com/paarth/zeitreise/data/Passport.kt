package com.paarth.zeitreise.data

import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.passportKey
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Key under which the stamped countries are kept. */
private const val STAMPS_KEY = "passport_stamps"

/** Separates the stamps in the stored text; no Wikidata id or country name holds a line break. */
private const val STAMP_SEPARATOR = "\n"

/** The countries the user has visited on the globe, each stamped once, in the order they were first opened. */
public interface Passport {
    /** The stamps so far, oldest first, as the [passportKey]s of their countries. */
    public suspend fun stamps(): List<String>

    /**
     * Stamps [country], unless it already holds a stamp.
     *
     * @return whether this was the country's first stamp.
     */
    public suspend fun stamp(country: Country): Boolean
}

/**
 * Keeps the stamps in the platform's key-value store, so the passport survives the app being closed.
 *
 * @param dispatcher dispatcher the reads and writes run on, since the first read may touch disk.
 */
internal class SettingsPassport(private val settings: Settings, private val dispatcher: CoroutineDispatcher) :
    Passport {
    // Two countries opened in quick succession must not both read the old list and lose a stamp.
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
