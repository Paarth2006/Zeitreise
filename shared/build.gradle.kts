import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

// The Gemini key lives in local.properties, which is never committed, and is written into a
// generated constant at build time, so no source file in the repository ever holds a key. Without
// one the constant is empty and the app runs on its bundled demo guide.
val geminiApiKey: String = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) }.getProperty("GEMINI_API_KEY") }
    .orEmpty()
    .trim()

val generateApiKeys by tasks.registering {
    val key = geminiApiKey
    val outputDir = layout.buildDirectory.dir("generated/apiKeys/commonMain/kotlin")
    inputs.property("geminiApiKey", key)
    outputs.dir(outputDir)
    doLast {
        val escaped = key.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        outputDir.get().file("com/paarth/zeitreise/ai/ApiKeys.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                |package com.paarth.zeitreise.ai
                |
                |/** The Gemini API key from local.properties, or empty when none is set. Generated at build time. */
                |internal const val GEMINI_API_KEY: String = "$escaped"
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
            implementation(libs.navigation.compose)
            implementation(libs.sqldelight.runtime)
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
            implementation(libs.sqldelight.androidDriver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.nativeDriver)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.webWorkerDriver)
        }
    }
}

sqldelight {
    databases {
        create("ZeitreiseDatabase") {
            packageName.set("com.paarth.zeitreise.data.db")
            generateAsync.set(true)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
