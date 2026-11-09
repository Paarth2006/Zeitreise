import com.android.build.api.dsl.ApplicationExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    // Loaded here once so each subproject's classloader does not resolve them again.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

val jvmTargetVersion: JvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
val javaCompatibility: JavaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())

// Single home for the configuration every module shares: JVM target, warnings-as-errors and the
// two code-quality plugins. None of this is repeated in a module build script.
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    val generatedRoot = layout.buildDirectory.get().asFile.path
    extensions.configure<KtlintExtension> {
        filter {
            // The compose resource accessors are generated into build/, and their formatting is
            // not ours to fix.
            exclude { element -> element.file.path.startsWith(generatedRoot) }
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        // detekt's default source dirs are src/main/{java,kotlin}, which no multiplatform module
        // has, so point it at every source set the module actually declares.
        source.setFrom(files("src"))
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = javaCompatibility.toString()
    }

    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(true)
            if (this is KotlinJvmCompilerOptions) {
                jvmTarget.set(jvmTargetVersion)
            }
        }
    }

    tasks.withType<Test>().configureEach {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = javaCompatibility
            targetCompatibility = javaCompatibility
        }
    }

    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileOptions {
                sourceCompatibility = javaCompatibility
                targetCompatibility = javaCompatibility
            }
        }
    }
}

// The npm dependencies are resolved with yarn, so package-lock.json is never written. Both tasks
// declare build/wasm/package-lock.json as a mandatory input, which Gradle 9 treats as a hard
// validation failure, and IntelliJ runs the upgrade task after every sync - turning a successful
// sync into a failed one. Yarn's own lock tasks stay enabled and keep kotlin-js-store/ current.
val disabledPackageLockTasks = setOf("kotlinWasmStorePackageLock", "kotlinWasmUpgradePackageLock")
tasks.matching { it.name in disabledPackageLockTasks }.configureEach {
    enabled = false
}
