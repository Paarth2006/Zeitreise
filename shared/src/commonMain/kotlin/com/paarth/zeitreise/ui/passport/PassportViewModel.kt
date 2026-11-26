package com.paarth.zeitreise.ui.passport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.data.CountryRepository
import com.paarth.zeitreise.data.Passport
import com.paarth.zeitreise.domain.Country
import com.paarth.zeitreise.domain.PassportBook
import com.paarth.zeitreise.domain.passportBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Longest name that fits on the passport. */
private const val MAX_HOLDER_LENGTH = 30

/** Loads the passport when it's opened, and saves the name the user writes in it. */
public class PassportViewModel(private val passport: Passport, private val countries: CountryRepository) : ViewModel() {

    private val _book = MutableStateFlow<PassportBook?>(null)

    /** The passport while open (reloaded each time), or `null` when closed. */
    public val book: StateFlow<PassportBook?> = _book.asStateFlow()

    /** Opens the passport. */
    public fun open() {
        viewModelScope.launch {
            _book.value = passportBook(
                countries = countries.loadCountries(),
                stamps = passport.stamps(),
                holder = passport.holder(),
                home = passport.home(),
            )
        }
    }

    /** Writes [name] in the passport as the user types it. It's trimmed when saved. */
    public fun rename(name: String) {
        val kept = name.take(MAX_HOLDER_LENGTH)
        // The text shown keeps the user's spaces while they type, so a space before a surname isn't lost.
        _book.update { it?.copy(holder = kept) }
        viewModelScope.launch { passport.setHolder(kept) }
    }

    /** Makes [country] the user's home country, stamps it if needed, and shows the updated passport. */
    public fun changeHome(country: Country) {
        viewModelScope.launch {
            passport.setHome(country)
            if (_book.value != null) open()
        }
    }

    /** Closes the passport. */
    public fun close() {
        _book.value = null
    }
}
