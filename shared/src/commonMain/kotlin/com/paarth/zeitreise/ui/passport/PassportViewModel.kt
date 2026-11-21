package com.paarth.zeitreise.ui.passport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paarth.zeitreise.data.CountryRepository
import com.paarth.zeitreise.data.Passport
import com.paarth.zeitreise.domain.PassportBook
import com.paarth.zeitreise.domain.passportBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Opens the passport: the countries the user has stamped, and how much of the world they cover. */
public class PassportViewModel(private val passport: Passport, private val countries: CountryRepository) : ViewModel() {

    private val _book = MutableStateFlow<PassportBook?>(null)

    /** The passport while it is open, read afresh each time it opens; `null` while it is closed. */
    public val book: StateFlow<PassportBook?> = _book.asStateFlow()

    /** Opens the passport, with every stamp earned so far. */
    public fun open() {
        viewModelScope.launch { _book.value = passportBook(countries.loadCountries(), passport.stamps()) }
    }

    /** Closes the passport. */
    public fun close() {
        _book.value = null
    }
}
