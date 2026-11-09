package com.paarth.zeitreise.di

import com.paarth.zeitreise.data.BundledCountryRepository
import com.paarth.zeitreise.data.CountryRepository
import com.paarth.zeitreise.data.OnboardingStore
import com.paarth.zeitreise.data.SettingsOnboardingStore
import com.paarth.zeitreise.ui.globe.GlobeViewModel
import com.paarth.zeitreise.ui.onboarding.OnboardingViewModel
import com.russhwolf.settings.Settings
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
    single<OnboardingStore> { SettingsOnboardingStore(settings = Settings(), dispatcher = get()) }
    viewModelOf(::GlobeViewModel)
    viewModelOf(::OnboardingViewModel)
}
