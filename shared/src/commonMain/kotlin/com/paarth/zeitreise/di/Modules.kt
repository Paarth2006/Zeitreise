package com.paarth.zeitreise.di

import com.paarth.zeitreise.data.BundledCountryRepository
import com.paarth.zeitreise.data.CountryRepository
import com.paarth.zeitreise.ui.globe.GlobeViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Everything the app needs on every platform.
 *
 * Dispatchers are injected rather than referenced directly, so a test can run the repository
 * on its own dispatcher.
 */
public val sharedModule: Module = module {
    single<CoroutineDispatcher> { Dispatchers.Default }
    single<CountryRepository> { BundledCountryRepository(dispatcher = get()) }
    viewModelOf(::GlobeViewModel)
}
