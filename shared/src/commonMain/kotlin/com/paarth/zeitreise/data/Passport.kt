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

/** Settings key for the user's home country. */
private const val HOME_KEY = "passport_home"

/** Settings key for the name the user wrote in the passport. */
private const val HOLDER_KEY = "passport_holder"

/** Separator between stamps in the saved text. Keys never contain a line break. */
private const val STAMP_SEPARATOR = "\n"

/**
 * The countries the user has opened, each stamped once, in the order they were first opened, the
 * user's home country and the name they wrote in it.
 */
public interface Passport {
    /** The [passportKey]s of stamped countries, oldest first. */
    public suspend fun stamps(): List<String>

    /**
     * Stamps [country] if it isn't stamped yet.
     *
     * @return `true` if this was a new stamp.
     */
    public suspend fun stamp(country: Country): Boolean

    /** The [passportKey] of the user's home country, or `null` if they didn't say. */
    public suspend fun home(): String?

    /** Saves [country] as the user's home country and stamps it, so it's usually their first stamp. */
    public suspend fun setHome(country: Country)

    /** The name the user wrote in the passport, or `null` if they didn't. */
    public suspend fun holder(): String?

    /** Saves [name] as the passport holder's name. A blank name removes it. */
    public suspend fun setHolder(name: String)
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

    override suspend fun home(): String? = withContext(dispatcher) { settings.getStringOrNull(HOME_KEY) }

    override suspend fun setHome(country: Country) {
        withContext(dispatcher) { settings.putString(HOME_KEY, country.passportKey) }
        stamp(country)
    }

    override suspend fun holder(): String? = withContext(dispatcher) { settings.getStringOrNull(HOLDER_KEY) }

    override suspend fun setHolder(name: String) {
        withContext(dispatcher) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) settings.remove(HOLDER_KEY) else settings.putString(HOLDER_KEY, trimmed)
        }
    }

    private fun read(): List<String> =
        settings.getString(STAMPS_KEY, defaultValue = "").split(STAMP_SEPARATOR).filter { it.isNotEmpty() }
}
