package com.paarth.zeitreise.di

import com.paarth.zeitreise.ai.DemoGuide
import com.paarth.zeitreise.ai.GEMINI_API_KEY
import com.paarth.zeitreise.ai.GeminiGuide
import com.paarth.zeitreise.data.BundledCountryRepository
import com.paarth.zeitreise.data.BundledSubdivisionRepository
import com.paarth.zeitreise.data.CountryRepository
import com.paarth.zeitreise.data.FactCursor
import com.paarth.zeitreise.data.FallbackGuideRepository
import com.paarth.zeitreise.data.GuideRepository
import com.paarth.zeitreise.data.KtorWikipediaRepository
import com.paarth.zeitreise.data.OnboardingStore
import com.paarth.zeitreise.data.ResponseCache
import com.paarth.zeitreise.data.SettingsFactCursor
import com.paarth.zeitreise.data.SettingsOnboardingStore
import com.paarth.zeitreise.data.SettingsResponseCache
import com.paarth.zeitreise.data.SubdivisionRepository
import com.paarth.zeitreise.data.WikipediaRepository
import com.paarth.zeitreise.ui.globe.GlobeViewModel
import com.paarth.zeitreise.ui.onboarding.OnboardingViewModel
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import zeitreise.shared.generated.resources.Res

/** Path of the bundled demo stories inside the compose resources. */
private const val DEMO_STORIES = "files/guide/stories.json"

/**
 * Everything the app needs on every platform.
 *
 * Dispatchers are injected rather than referenced directly, so a test can run the repository
 * on its own dispatcher. The AI guide only exists when an API key was set at build time; without
 * one the app runs on the demo guide and Wikipedia alone.
 */
@OptIn(ExperimentalResourceApi::class)
public val sharedModule: Module = module {
    single<CoroutineDispatcher> { Dispatchers.Default }
    single<Settings> { Settings() }
    // Ktor picks each platform's engine from the classpath: OkHttp, Darwin or the browser's fetch.
    single { HttpClient() }
    single<ResponseCache> { SettingsResponseCache(settings = get()) }
    single<FactCursor> { SettingsFactCursor(settings = get()) }
    single<CountryRepository> { BundledCountryRepository(dispatcher = get()) }
    single<SubdivisionRepository> { BundledSubdivisionRepository(dispatcher = get()) }
    single<OnboardingStore> { SettingsOnboardingStore(settings = get(), dispatcher = get()) }
    single<WikipediaRepository> { KtorWikipediaRepository(client = get(), cache = get(), dispatcher = get()) }
    single<GuideRepository> {
        FallbackGuideRepository(
            wikipedia = get(),
            aiGuide = GEMINI_API_KEY.takeIf { it.isNotBlank() }?.let { key ->
                GeminiGuide.create(apiKey = key, httpClient = get(), cache = get(), dispatcher = get())
            },
            demoGuide = DemoGuide(readStories = { Res.readBytes(DEMO_STORIES).decodeToString() }),
        )
    }
    viewModelOf(::GlobeViewModel)
    viewModelOf(::OnboardingViewModel)
}
