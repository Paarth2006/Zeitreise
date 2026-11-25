package com.paarth.zeitreise.di

import com.paarth.zeitreise.ai.DemoGuide
import com.paarth.zeitreise.ai.GEMINI_API_KEY
import com.paarth.zeitreise.ai.GeminiGuide
import com.paarth.zeitreise.data.BundledCountryRepository
import com.paarth.zeitreise.data.BundledLandmarkHistory
import com.paarth.zeitreise.data.BundledSubdivisionRepository
import com.paarth.zeitreise.data.CountryRepository
import com.paarth.zeitreise.data.DayClock
import com.paarth.zeitreise.data.ExploreRepository
import com.paarth.zeitreise.data.FactCursor
import com.paarth.zeitreise.data.FallbackGuideRepository
import com.paarth.zeitreise.data.GuideRepository
import com.paarth.zeitreise.data.GuidedExploreRepository
import com.paarth.zeitreise.data.HistoryRepository
import com.paarth.zeitreise.data.KtorHistoryRepository
import com.paarth.zeitreise.data.KtorWikipediaRepository
import com.paarth.zeitreise.data.LandmarkHistory
import com.paarth.zeitreise.data.OnboardingStore
import com.paarth.zeitreise.data.Passport
import com.paarth.zeitreise.data.QuizStore
import com.paarth.zeitreise.data.ResponseCache
import com.paarth.zeitreise.data.SettingsFactCursor
import com.paarth.zeitreise.data.SettingsOnboardingStore
import com.paarth.zeitreise.data.SettingsPassport
import com.paarth.zeitreise.data.SettingsQuizStore
import com.paarth.zeitreise.data.SettingsResponseCache
import com.paarth.zeitreise.data.SubdivisionRepository
import com.paarth.zeitreise.data.SystemDayClock
import com.paarth.zeitreise.data.WikipediaRepository
import com.paarth.zeitreise.sync.Account
import com.paarth.zeitreise.sync.CloudSync
import com.paarth.zeitreise.sync.SUPABASE_ANON_KEY
import com.paarth.zeitreise.sync.SUPABASE_URL
import com.paarth.zeitreise.sync.SupabaseApi
import com.paarth.zeitreise.sync.SyncingOnboardingStore
import com.paarth.zeitreise.sync.SyncingPassport
import com.paarth.zeitreise.sync.SyncingQuizStore
import com.paarth.zeitreise.ui.about.AccountViewModel
import com.paarth.zeitreise.ui.explore.ExploreViewModel
import com.paarth.zeitreise.ui.globe.GlobeViewModel
import com.paarth.zeitreise.ui.onboarding.OnboardingViewModel
import com.paarth.zeitreise.ui.passport.PassportViewModel
import com.paarth.zeitreise.ui.quiz.QuizViewModel
import com.paarth.zeitreise.ui.timetravel.TimeTravelViewModel
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import zeitreise.shared.generated.resources.Res

/** Path of the bundled demo stories. */
private const val DEMO_STORIES = "files/guide/stories.json"

/** Path of the bundled places to visit. */
private const val DEMO_PLACES = "files/guide/places.json"

/** Path of the bundled historic landmarks. */
private const val HISTORIC_LANDMARKS = "files/history/landmarks.json"

/**
 * The Koin module shared by all platforms.
 *
 * Dispatchers are injected so tests can replace them. The AI guide is only created when an API key
 * was set at build time; otherwise the app uses the demo guide and Wikipedia.
 */
@OptIn(ExperimentalResourceApi::class)
public val sharedModule: Module = module {
    single<CoroutineDispatcher> { Dispatchers.Default }
    single<Settings> { Settings() }
    // Ktor picks the engine for each platform: OkHttp, Darwin or the browser's fetch.
    single { HttpClient() }
    single<ResponseCache> { SettingsResponseCache(settings = get()) }
    single<FactCursor> { SettingsFactCursor(settings = get()) }
    single<CountryRepository> { BundledCountryRepository(dispatcher = get()) }
    single<SubdivisionRepository> { BundledSubdivisionRepository(dispatcher = get()) }
    single {
        CloudSync(
            api = supabaseApi(client = get()),
            settings = get(),
            // Lives as long as the app, so a sync started by a change finishes after the screen that made it.
            scope = CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>()),
            dispatcher = get(),
        )
    }
    single<Account> { get<CloudSync>() }
    // Each store tells the sync about its changes, so they reach the user's other devices.
    single<OnboardingStore> {
        SyncingOnboardingStore(
            SettingsOnboardingStore(settings = get(), dispatcher = get()),
            trigger = get<CloudSync>(),
        )
    }
    single<QuizStore> {
        SyncingQuizStore(SettingsQuizStore(settings = get(), dispatcher = get()), trigger = get<CloudSync>())
    }
    single<DayClock> { SystemDayClock }
    single<Passport> {
        SyncingPassport(SettingsPassport(settings = get(), dispatcher = get()), trigger = get<CloudSync>())
    }
    single<WikipediaRepository> { KtorWikipediaRepository(client = get(), cache = get(), dispatcher = get()) }
    single<DemoGuide> {
        DemoGuide(
            readStories = { Res.readBytes(DEMO_STORIES).decodeToString() },
            readPlaces = { Res.readBytes(DEMO_PLACES).decodeToString() },
        )
    }
    // Only registered when there is an API key.
    GEMINI_API_KEY.takeIf { it.isNotBlank() }?.let { key ->
        single { GeminiGuide.create(apiKey = key, httpClient = get(), cache = get(), dispatcher = get()) }
    }
    single<GuideRepository> {
        FallbackGuideRepository(wikipedia = get(), aiGuide = getOrNull<GeminiGuide>(), demoGuide = get<DemoGuide>())
    }
    single<HistoryRepository> { KtorHistoryRepository(client = get(), dispatcher = get()) }
    single<LandmarkHistory> {
        BundledLandmarkHistory(readLandmarks = { Res.readBytes(HISTORIC_LANDMARKS).decodeToString() })
    }
    single<ExploreRepository> {
        GuidedExploreRepository(wikipedia = get(), aiGuide = getOrNull<GeminiGuide>(), demoGuide = get<DemoGuide>())
    }
    viewModelOf(::GlobeViewModel)
    viewModelOf(::ExploreViewModel)
    viewModelOf(::TimeTravelViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModel { QuizViewModel(store = get()) }
    viewModelOf(::PassportViewModel)
    viewModelOf(::AccountViewModel)
}

/** The Supabase project set at build time, or `null` when there is none, which hides sync. */
private fun supabaseApi(client: HttpClient): SupabaseApi? = if (SUPABASE_URL.isBlank() || SUPABASE_ANON_KEY.isBlank()) {
    null
} else {
    SupabaseApi(client = client, url = SUPABASE_URL, key = SUPABASE_ANON_KEY)
}
