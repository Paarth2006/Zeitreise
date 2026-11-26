import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// The API keys live in local.properties, which is never committed, and are written into generated
// constants at build time, so no source file in the repository ever holds a key. Without the Gemini
// key the app runs on its bundled demo guide; without the Supabase settings, sync is hidden.
val localProperties: Properties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun localProperty(name: String): String = localProperties.getProperty(name).orEmpty().trim()

// The hosted web build has no local.properties, so its workflow passes the Supabase settings as
// environment variables. They are public by design; the Gemini key is never read from here.
fun publicSetting(name: String): String =
    localProperty(name).ifEmpty { providers.environmentVariable(name).orNull.orEmpty().trim() }

/** Escapes [value] for a Kotlin string literal. */
fun kotlinString(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

val generateApiKeys by tasks.registering {
    // Escaped here, while configuring, because the task action can't call functions of this script.
    val gemini = kotlinString(localProperty("GEMINI_API_KEY"))
    val supabaseUrl = kotlinString(publicSetting("SUPABASE_URL").trimEnd('/'))
    val supabaseKey = kotlinString(publicSetting("SUPABASE_ANON_KEY"))
    val outputDir = layout.buildDirectory.dir("generated/apiKeys/commonMain/kotlin")
    inputs.property("geminiApiKey", gemini)
    inputs.property("supabaseUrl", supabaseUrl)
    inputs.property("supabaseKey", supabaseKey)
    outputs.dir(outputDir)
    doLast {
        outputDir.get().file("com/paarth/zeitreise/ai/ApiKeys.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                |package com.paarth.zeitreise.ai
                |
                |/** The Gemini API key from local.properties, or empty when none is set. Generated at build time. */
                |internal const val GEMINI_API_KEY: String = "$gemini"
                |
                """.trimMargin(),
            )
        }
        outputDir.get().file("com/paarth/zeitreise/sync/SyncKeys.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                |package com.paarth.zeitreise.sync
                |
                |/** The Supabase project address from local.properties, or empty when none is set. Generated at build time. */
                |internal const val SUPABASE_URL: String = "$supabaseUrl"
                |
                |/** The Supabase project's public (anon) key from local.properties, or empty. Generated at build time. */
                |internal const val SUPABASE_ANON_KEY: String = "$supabaseKey"
                |
                """.trimMargin(),
            )
        }
    }
}

kotlin {
    // Every public declaration in the shared module states its visibility and return type.
    explicitApi()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    android {
        namespace = "com.paarth.zeitreise.shared"
        // The SDK ships API 37 as platform "android-37.0"; naming the minor level lets the IDE find it.
        compileSdk {
            version = release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = 0
            }
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateApiKeys)
        }
        commonMain.dependencies {
            implementation(libs.coil.compose)
            implementation(libs.compose.components.resources)
            implementation(libs.coil.network.ktor)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.haze)
            implementation(libs.haze.glass)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core)
            implementation(libs.koog.googleClient)
            implementation(libs.koog.httpKtor)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.lifecycle.runtimeCompose)
            implementation(libs.lifecycle.viewmodelCompose)
            implementation(libs.multiplatformSettings.noArg)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatformSettings.test)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
